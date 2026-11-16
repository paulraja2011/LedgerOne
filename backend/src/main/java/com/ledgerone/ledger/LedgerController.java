package com.ledgerone.ledger;

import com.ledgerone.shared.ApiException;
import com.ledgerone.shared.Tenant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class LedgerController {

    private final AccountRepository accounts;
    private final JournalEntryRepository entries;
    private final PostingService posting;
    private final JdbcTemplate jdbc;

    public LedgerController(AccountRepository accounts, JournalEntryRepository entries,
                            PostingService posting, JdbcTemplate jdbc) {
        this.accounts = accounts;
        this.entries = entries;
        this.posting = posting;
        this.jdbc = jdbc;
    }

    // ---------- Chart of accounts ----------

    public record AccountDto(UUID id, String code, String name, AccountType type, String subtype,
                             UUID parentId, String systemKey, boolean active, BigDecimal balance) {}

    public record AccountRequest(@NotBlank String code, @NotBlank String name, @NotNull AccountType type,
                                 @NotBlank String subtype, UUID parentId, Boolean active) {}

    @GetMapping("/accounts")
    public List<AccountDto> listAccounts() {
        UUID cid = Tenant.companyId();
        Map<UUID, BigDecimal[]> totals = new HashMap<>();
        jdbc.query("select account_id, sum(debit), sum(credit) from journal_line where company_id = ? group by account_id",
                rs -> { totals.put(rs.getObject(1, UUID.class), new BigDecimal[]{rs.getBigDecimal(2), rs.getBigDecimal(3)}); },
                cid);
        return accounts.findByCompanyIdOrderByCode(cid).stream().map(a -> {
            BigDecimal[] t = totals.getOrDefault(a.id, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal bal = a.type.debitNormal() ? t[0].subtract(t[1]) : t[1].subtract(t[0]);
            return toDto(a, bal);
        }).toList();
    }

    @PostMapping("/accounts")
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public AccountDto createAccount(@Valid @RequestBody AccountRequest req) {
        UUID cid = Tenant.companyId();
        if (accounts.existsByCompanyIdAndCode(cid, req.code().trim())) {
            throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_CODE_TAKEN", "Account code " + req.code() + " already exists");
        }
        Account a = new Account();
        a.companyId = cid;
        apply(a, req);
        return toDto(accounts.save(a), BigDecimal.ZERO);
    }

    @PutMapping("/accounts/{id}")
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public AccountDto updateAccount(@PathVariable UUID id, @Valid @RequestBody AccountRequest req) {
        Account a = accounts.findByIdAndCompanyId(id, Tenant.companyId()).orElseThrow(() -> ApiException.notFound("Account"));
        if (a.systemKey != null && req.type() != a.type) {
            throw ApiException.badRequest("SYSTEM_ACCOUNT", "The type of a system account cannot change");
        }
        apply(a, req);
        return toDto(accounts.save(a), null);
    }

    public record RegisterLine(UUID entryId, long number, LocalDate date, String memo, String sourceType,
                               String description, BigDecimal debit, BigDecimal credit, BigDecimal balance) {}

    @GetMapping("/accounts/{id}/register")
    public List<RegisterLine> register(@PathVariable UUID id) {
        UUID cid = Tenant.companyId();
        Account a = accounts.findByIdAndCompanyId(id, cid).orElseThrow(() -> ApiException.notFound("Account"));
        List<RegisterLine> rows = new ArrayList<>();
        BigDecimal[] running = {BigDecimal.ZERO};
        jdbc.query("""
                select e.id, e.number, e.entry_date, e.memo, e.source_type, l.description, l.debit, l.credit
                from journal_line l join journal_entry e on e.id = l.entry_id
                where l.company_id = ? and l.account_id = ?
                order by e.entry_date, e.number, l.line_no
                """, rs -> {
            BigDecimal d = rs.getBigDecimal(7), c = rs.getBigDecimal(8);
            running[0] = running[0].add(a.type.debitNormal() ? d.subtract(c) : c.subtract(d));
            rows.add(new RegisterLine(rs.getObject(1, UUID.class), rs.getLong(2), rs.getObject(3, LocalDate.class),
                    rs.getString(4), rs.getString(5), rs.getString(6), d, c, running[0]));
        }, cid, id);
        Collections.reverse(rows);
        return rows;
    }

    // ---------- Journal entries ----------

    public record JournalLineDto(UUID accountId, String accountCode, String accountName,
                                 BigDecimal debit, BigDecimal credit, String description) {}

    public record JournalEntryDto(UUID id, long number, LocalDate date, String memo, String sourceType,
                                  UUID sourceId, boolean reversed, UUID reversalOfId, BigDecimal total,
                                  List<JournalLineDto> lines) {}

    public record ManualLine(@NotNull UUID accountId, BigDecimal debit, BigDecimal credit, String description) {}

    public record ManualEntry(@NotNull LocalDate date, String memo, @NotEmpty List<@Valid ManualLine> lines) {}

    @GetMapping("/journal-entries")
    public Page<JournalEntryDto> listEntries(@RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "50") int size) {
        UUID cid = Tenant.companyId();
        Map<UUID, Account> acc = accountMap(cid);
        return entries.findByCompanyIdOrderByEntryDateDescNumberDesc(cid, PageRequest.of(page, Math.min(size, 200)))
                .map(e -> toDto(e, acc));
    }

    @GetMapping("/journal-entries/{id}")
    public JournalEntryDto getEntry(@PathVariable UUID id) {
        UUID cid = Tenant.companyId();
        return toDto(entries.findByIdAndCompanyId(id, cid).orElseThrow(() -> ApiException.notFound("Journal entry")), accountMap(cid));
    }

    @PostMapping("/journal-entries")
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public JournalEntryDto createEntry(@Valid @RequestBody ManualEntry req) {
        UUID cid = Tenant.companyId();
        List<PostingService.Line> lines = req.lines().stream()
                .map(l -> new PostingService.Line(l.accountId(), nz(l.debit()), nz(l.credit()), l.description(), null, null))
                .toList();
        JournalEntry je = posting.post(new PostingService.Request(cid, Tenant.userId(), req.date(), req.memo(), "MANUAL", null, lines));
        return toDto(je, accountMap(cid));
    }

    public record ReverseRequest(LocalDate date) {}

    @PostMapping("/journal-entries/{id}/reverse")
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public JournalEntryDto reverse(@PathVariable UUID id, @RequestBody(required = false) ReverseRequest req) {
        UUID cid = Tenant.companyId();
        JournalEntry orig = entries.findByIdAndCompanyId(id, cid).orElseThrow(() -> ApiException.notFound("Journal entry"));
        if (!"MANUAL".equals(orig.sourceType)) {
            throw ApiException.badRequest("USE_SOURCE_DOCUMENT", "Void the source " + orig.sourceType.toLowerCase() + " instead");
        }
        LocalDate date = req != null && req.date() != null ? req.date() : LocalDate.now();
        return toDto(posting.reverse(cid, Tenant.userId(), id, date, null), accountMap(cid));
    }

    // ---------- helpers ----------

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private void apply(Account a, AccountRequest req) {
        a.code = req.code().trim();
        a.name = req.name().trim();
        a.type = req.type();
        a.subtype = req.subtype();
        a.parentId = req.parentId();
        if (req.active() != null) a.active = req.active();
    }

    private Map<UUID, Account> accountMap(UUID cid) {
        Map<UUID, Account> m = new HashMap<>();
        accounts.findByCompanyIdOrderByCode(cid).forEach(a -> m.put(a.id, a));
        return m;
    }

    static AccountDto toDto(Account a, BigDecimal bal) {
        return new AccountDto(a.id, a.code, a.name, a.type, a.subtype, a.parentId, a.systemKey, a.active, bal);
    }

    static JournalEntryDto toDto(JournalEntry e, Map<UUID, Account> acc) {
        BigDecimal total = e.lines.stream().map(l -> l.debit).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<JournalLineDto> lines = e.lines.stream().map(l -> {
            Account a = acc.get(l.accountId);
            return new JournalLineDto(l.accountId, a == null ? "?" : a.code, a == null ? "?" : a.name, l.debit, l.credit, l.description);
        }).toList();
        return new JournalEntryDto(e.id, e.number, e.entryDate, e.memo, e.sourceType, e.sourceId, e.reversed, e.reversalOfId, total, lines);
    }
}
