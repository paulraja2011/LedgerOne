package com.ledgerone.purchases;

import com.ledgerone.ledger.*;
import com.ledgerone.shared.ApiException;
import com.ledgerone.shared.Money;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class BillService {

    public record LineInput(String description, BigDecimal amount, UUID accountId) {}
    public record BillInput(UUID vendorId, String reference, LocalDate billDate, LocalDate dueDate, String memo, List<LineInput> lines) {}
    public record PayInput(LocalDate date, BigDecimal amount, UUID bankAccountId) {}

    private final BillRepository bills;
    private final BillPaymentRepository billPayments;
    private final VendorRepository vendors;
    private final AccountRepository accounts;
    private final PostingService posting;

    BillService(BillRepository bills, BillPaymentRepository billPayments, VendorRepository vendors,
                AccountRepository accounts, PostingService posting) {
        this.bills = bills;
        this.billPayments = billPayments;
        this.vendors = vendors;
        this.accounts = accounts;
        this.posting = posting;
    }

    @Transactional
    public Bill create(UUID companyId, BillInput in) {
        Bill b = new Bill();
        b.companyId = companyId;
        apply(companyId, b, in);
        return bills.save(b);
    }

    @Transactional
    public Bill update(UUID companyId, UUID id, BillInput in) {
        Bill b = get(companyId, id);
        if (b.status != BillStatus.DRAFT) throw new ApiException(HttpStatus.CONFLICT, "NOT_DRAFT", "Only draft bills can be edited");
        apply(companyId, b, in);
        return bills.save(b);
    }

    /** DRAFT -> OPEN. Posts: DR expense/asset per line / CR Accounts Payable. */
    @Transactional
    public Bill approve(UUID companyId, UUID userId, UUID id) {
        Bill b = get(companyId, id);
        if (b.status != BillStatus.DRAFT) throw new ApiException(HttpStatus.CONFLICT, "NOT_DRAFT", "Bill is already approved");
        if (!Money.positive(b.total)) throw ApiException.badRequest("BILL_EMPTY", "Bill total must be greater than zero");
        Account ap = systemAccount(companyId, "AP");
        List<PostingService.Line> lines = new ArrayList<>();
        for (BillLine l : b.lines) {
            lines.add(PostingService.Line.debit(l.accountId, l.amount, l.description).withVendor(b.vendorId));
        }
        lines.add(PostingService.Line.credit(ap.id, b.total, "Bill " + label(b)).withVendor(b.vendorId));
        JournalEntry je = posting.post(new PostingService.Request(companyId, userId, b.billDate, "Bill " + label(b), "BILL", b.id, lines));
        b.journalEntryId = je.id;
        b.status = BillStatus.OPEN;
        return bills.save(b);
    }

    /** Posts: DR Accounts Payable / CR bank. */
    @Transactional
    public Bill pay(UUID companyId, UUID userId, UUID id, PayInput in) {
        Bill b = get(companyId, id);
        if (b.status != BillStatus.OPEN && b.status != BillStatus.PARTIALLY_PAID) {
            throw new ApiException(HttpStatus.CONFLICT, "NOT_PAYABLE", "Bill is " + b.status.name().toLowerCase());
        }
        BigDecimal amount = Money.round(in.amount() != null ? in.amount() : b.balanceDue());
        if (!Money.positive(amount)) throw ApiException.badRequest("AMOUNT_INVALID", "Amount must be greater than zero");
        if (amount.compareTo(b.balanceDue()) > 0) throw ApiException.badRequest("OVERPAYMENT", "Payment exceeds the balance due of " + b.balanceDue());
        Account bank = in.bankAccountId() != null
                ? accounts.findByIdAndCompanyId(in.bankAccountId(), companyId).orElseThrow(() -> ApiException.notFound("Bank account"))
                : systemAccount(companyId, "BANK");
        if (bank.type != AccountType.ASSET && bank.type != AccountType.LIABILITY) {
            throw ApiException.badRequest("PAYMENT_ACCOUNT", "Pay from a bank or credit card account");
        }
        Account ap = systemAccount(companyId, "AP");
        LocalDate date = in.date() != null ? in.date() : LocalDate.now();
        BillPayment p = new BillPayment();
        p.companyId = companyId;
        p.vendorId = b.vendorId;
        p.billId = b.id;
        p.paymentDate = date;
        p.amount = amount;
        p.bankAccountId = bank.id;
        JournalEntry je = posting.post(new PostingService.Request(companyId, userId, date, "Payment of bill " + label(b),
                "BILL_PAYMENT", p.id, List.of(
                PostingService.Line.debit(ap.id, amount, "Bill " + label(b)).withVendor(b.vendorId),
                PostingService.Line.credit(bank.id, amount, "Bill payment").withVendor(b.vendorId))));
        p.journalEntryId = je.id;
        billPayments.save(p);
        b.amountPaid = b.amountPaid.add(amount);
        b.status = b.balanceDue().signum() == 0 ? BillStatus.PAID : BillStatus.PARTIALLY_PAID;
        return bills.save(b);
    }

    @Transactional
    public Bill voidBill(UUID companyId, UUID userId, UUID id) {
        Bill b = get(companyId, id);
        if (b.status == BillStatus.VOID) return b;
        if (b.amountPaid.signum() > 0) throw new ApiException(HttpStatus.CONFLICT, "HAS_PAYMENTS", "Bill has payments; it cannot be voided");
        if (b.journalEntryId != null) posting.reverse(companyId, userId, b.journalEntryId, LocalDate.now(), "Void bill " + label(b));
        b.status = BillStatus.VOID;
        return bills.save(b);
    }

    public Bill get(UUID companyId, UUID id) {
        return bills.findByIdAndCompanyId(id, companyId).orElseThrow(() -> ApiException.notFound("Bill"));
    }

    private static String label(Bill b) {
        return b.reference != null && !b.reference.isBlank() ? b.reference : b.id.toString().substring(0, 8);
    }

    private Account systemAccount(UUID companyId, String key) {
        return accounts.findByCompanyIdAndSystemKey(companyId, key)
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "SETUP", "Missing system account " + key));
    }

    private void apply(UUID companyId, Bill b, BillInput in) {
        if (in.vendorId() == null) throw ApiException.badRequest("VENDOR_REQUIRED", "Choose a vendor");
        vendors.findByIdAndCompanyId(in.vendorId(), companyId).orElseThrow(() -> ApiException.notFound("Vendor"));
        if (in.lines() == null || in.lines().isEmpty()) throw ApiException.badRequest("LINES_REQUIRED", "Add at least one line");
        b.vendorId = in.vendorId();
        b.reference = in.reference();
        b.billDate = in.billDate() != null ? in.billDate() : LocalDate.now();
        b.dueDate = in.dueDate() != null ? in.dueDate() : b.billDate.plusDays(30);
        b.memo = in.memo();
        b.lines.clear();
        BigDecimal total = BigDecimal.ZERO;
        int n = 1;
        for (LineInput li : in.lines()) {
            if (li.accountId() == null) throw ApiException.badRequest("LINE_ACCOUNT", "Each line needs an expense account");
            Account a = accounts.findByIdAndCompanyId(li.accountId(), companyId).orElseThrow(() -> ApiException.notFound("Account"));
            if (a.type != AccountType.EXPENSE && a.type != AccountType.ASSET) {
                throw ApiException.badRequest("LINE_ACCOUNT", "Bill lines must use an expense or asset account");
            }
            BillLine l = new BillLine();
            l.lineNo = n++;
            l.description = li.description() == null || li.description().isBlank() ? a.name : li.description().trim();
            l.amount = Money.round(li.amount());
            if (!Money.positive(l.amount)) throw ApiException.badRequest("LINE_AMOUNT", "Line amounts must be greater than zero");
            l.accountId = a.id;
            b.lines.add(l);
            total = total.add(l.amount);
        }
        b.total = total;
    }
}
