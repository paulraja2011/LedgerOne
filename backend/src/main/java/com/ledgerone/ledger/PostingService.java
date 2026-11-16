package com.ledgerone.ledger;

import com.ledgerone.company.Company;
import com.ledgerone.company.CompanyRepository;
import com.ledgerone.shared.ApiException;
import com.ledgerone.shared.Money;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The only writer of ledger lines. Validates that every entry is balanced,
 * uses active accounts of the same company, and is dated after the lock date.
 */
@Service
public class PostingService {

    public record Line(UUID accountId, BigDecimal debit, BigDecimal credit, String description,
                       UUID customerId, UUID vendorId) {
        public static Line debit(UUID account, BigDecimal amount, String desc) {
            return new Line(account, amount, BigDecimal.ZERO, desc, null, null);
        }
        public static Line credit(UUID account, BigDecimal amount, String desc) {
            return new Line(account, BigDecimal.ZERO, amount, desc, null, null);
        }
        public Line withCustomer(UUID id) { return new Line(accountId, debit, credit, description, id, vendorId); }
        public Line withVendor(UUID id) { return new Line(accountId, debit, credit, description, customerId, id); }
    }

    public record Request(UUID companyId, UUID userId, LocalDate date, String memo,
                          String sourceType, UUID sourceId, List<Line> lines) {}

    private final JournalEntryRepository entries;
    private final AccountRepository accounts;
    private final CompanyRepository companies;

    public PostingService(JournalEntryRepository entries, AccountRepository accounts, CompanyRepository companies) {
        this.entries = entries;
        this.accounts = accounts;
        this.companies = companies;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public JournalEntry post(Request req) {
        Company company = companies.lockById(req.companyId()).orElseThrow(() -> ApiException.notFound("Company"));
        if (req.date() == null) throw ApiException.badRequest("DATE_REQUIRED", "Entry date is required");
        if (company.lockDate != null && !req.date().isAfter(company.lockDate)) {
            throw new ApiException(HttpStatus.CONFLICT, "PERIOD_LOCKED",
                    "Books are closed through " + company.lockDate + "; choose a later date");
        }
        List<Line> lines = req.lines().stream()
                .filter(l -> Money.positive(l.debit()) || Money.positive(l.credit()))
                .toList();
        if (lines.size() < 2) throw ApiException.badRequest("LEDGER_TOO_FEW_LINES", "A journal entry needs at least two lines");

        BigDecimal debits = BigDecimal.ZERO, credits = BigDecimal.ZERO;
        JournalEntry je = new JournalEntry();
        je.companyId = req.companyId();
        je.entryDate = req.date();
        je.memo = req.memo();
        je.sourceType = req.sourceType();
        je.sourceId = req.sourceId();
        je.createdBy = req.userId();

        List<JournalLine> out = new ArrayList<>();
        int n = 1;
        for (Line l : lines) {
            BigDecimal d = Money.round(l.debit()), c = Money.round(l.credit());
            if (d.signum() < 0 || c.signum() < 0) throw ApiException.badRequest("LEDGER_NEGATIVE", "Amounts must be positive");
            if (d.signum() > 0 && c.signum() > 0) throw ApiException.badRequest("LEDGER_BOTH_SIDES", "A line is either a debit or a credit");
            Account acct = accounts.findByIdAndCompanyId(l.accountId(), req.companyId())
                    .orElseThrow(() -> ApiException.badRequest("ACCOUNT_INVALID", "Unknown account " + l.accountId()));
            if (!acct.active) throw ApiException.badRequest("ACCOUNT_INACTIVE", "Account " + acct.name + " is inactive");
            JournalLine jl = new JournalLine();
            jl.companyId = req.companyId();
            jl.lineNo = n++;
            jl.accountId = acct.id;
            jl.debit = d;
            jl.credit = c;
            jl.description = l.description();
            jl.customerId = l.customerId();
            jl.vendorId = l.vendorId();
            out.add(jl);
            debits = debits.add(d);
            credits = credits.add(c);
        }
        if (debits.compareTo(credits) != 0) {
            throw ApiException.badRequest("LEDGER_UNBALANCED",
                    "Debits (" + debits + ") must equal credits (" + credits + ")");
        }
        je.lines.addAll(out);
        je.number = entries.maxNumber(req.companyId()) + 1;
        return entries.save(je);
    }

    /** Posts a mirror-image entry and marks the original as reversed. */
    @Transactional(propagation = Propagation.MANDATORY)
    public JournalEntry reverse(UUID companyId, UUID userId, UUID entryId, LocalDate date, String memo) {
        JournalEntry orig = entries.findByIdAndCompanyId(entryId, companyId)
                .orElseThrow(() -> ApiException.notFound("Journal entry"));
        if (orig.reversed) throw new ApiException(HttpStatus.CONFLICT, "ALREADY_REVERSED", "Entry is already reversed");
        if (orig.reversalOfId != null) throw new ApiException(HttpStatus.CONFLICT, "IS_REVERSAL", "A reversal cannot be reversed");
        List<Line> lines = orig.lines.stream()
                .map(l -> new Line(l.accountId, l.credit, l.debit, l.description, l.customerId, l.vendorId))
                .toList();
        JournalEntry rev = post(new Request(companyId, userId, date, memo != null ? memo : "Reversal of #" + orig.number,
                orig.sourceType, orig.sourceId, lines));
        rev.reversalOfId = orig.id;
        orig.reversed = true;
        entries.save(orig);
        return entries.save(rev);
    }
}
