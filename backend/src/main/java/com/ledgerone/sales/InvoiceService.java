package com.ledgerone.sales;

import com.ledgerone.company.CompanyRepository;
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
public class InvoiceService {

    public record LineInput(String description, BigDecimal quantity, BigDecimal rate, UUID accountId) {}
    public record InvoiceInput(UUID customerId, LocalDate invoiceDate, LocalDate dueDate, String memo, List<LineInput> lines) {}
    public record PaymentInput(LocalDate date, BigDecimal amount, UUID depositAccountId, String method, String reference) {}

    private final InvoiceRepository invoices;
    private final PaymentRepository payments;
    private final CustomerRepository customers;
    private final AccountRepository accounts;
    private final PostingService posting;
    private final CompanyRepository companies;

    public InvoiceService(InvoiceRepository invoices, PaymentRepository payments, CustomerRepository customers,
                          AccountRepository accounts, PostingService posting, CompanyRepository companies) {
        this.invoices = invoices;
        this.payments = payments;
        this.customers = customers;
        this.accounts = accounts;
        this.posting = posting;
        this.companies = companies;
    }

    @Transactional
    public Invoice create(UUID companyId, InvoiceInput in) {
        companies.lockById(companyId); // serialize invoice numbering
        Invoice inv = new Invoice();
        inv.companyId = companyId;
        inv.number = invoices.maxNumber(companyId) + 1;
        apply(companyId, inv, in);
        return invoices.save(inv);
    }

    @Transactional
    public Invoice update(UUID companyId, UUID id, InvoiceInput in) {
        Invoice inv = get(companyId, id);
        if (inv.status != InvoiceStatus.DRAFT) {
            throw new ApiException(HttpStatus.CONFLICT, "NOT_DRAFT", "Only draft invoices can be edited; void and re-issue instead");
        }
        apply(companyId, inv, in);
        return invoices.save(inv);
    }

    /** DRAFT -> OPEN. Posts: DR Accounts Receivable / CR income account per line. */
    @Transactional
    public Invoice approve(UUID companyId, UUID userId, UUID id) {
        Invoice inv = get(companyId, id);
        if (inv.status != InvoiceStatus.DRAFT) throw new ApiException(HttpStatus.CONFLICT, "NOT_DRAFT", "Invoice is already approved");
        if (!Money.positive(inv.total)) throw ApiException.badRequest("INVOICE_EMPTY", "Invoice total must be greater than zero");
        Account ar = systemAccount(companyId, "AR");
        List<PostingService.Line> lines = new ArrayList<>();
        lines.add(PostingService.Line.debit(ar.id, inv.total, "Invoice #" + inv.number).withCustomer(inv.customerId));
        for (InvoiceLine l : inv.lines) {
            lines.add(PostingService.Line.credit(l.accountId, l.amount, l.description).withCustomer(inv.customerId));
        }
        JournalEntry je = posting.post(new PostingService.Request(companyId, userId, inv.invoiceDate,
                "Invoice #" + inv.number, "INVOICE", inv.id, lines));
        inv.journalEntryId = je.id;
        inv.status = InvoiceStatus.OPEN;
        return invoices.save(inv);
    }

    /** Posts: DR deposit (bank) account / CR Accounts Receivable. */
    @Transactional
    public Payment receivePayment(UUID companyId, UUID userId, UUID invoiceId, PaymentInput in) {
        Invoice inv = get(companyId, invoiceId);
        if (inv.status != InvoiceStatus.OPEN && inv.status != InvoiceStatus.PARTIALLY_PAID) {
            throw new ApiException(HttpStatus.CONFLICT, "NOT_PAYABLE", "Invoice is " + inv.status.name().toLowerCase());
        }
        BigDecimal amount = Money.round(in.amount());
        if (!Money.positive(amount)) throw ApiException.badRequest("AMOUNT_INVALID", "Payment amount must be greater than zero");
        if (amount.compareTo(inv.balanceDue()) > 0) {
            throw ApiException.badRequest("OVERPAYMENT", "Payment exceeds the balance due of " + inv.balanceDue());
        }
        Account deposit = in.depositAccountId() != null
                ? accounts.findByIdAndCompanyId(in.depositAccountId(), companyId).orElseThrow(() -> ApiException.notFound("Deposit account"))
                : systemAccount(companyId, "BANK");
        if (deposit.type != AccountType.ASSET) throw ApiException.badRequest("DEPOSIT_ACCOUNT", "Deposit to an asset account");
        Account ar = systemAccount(companyId, "AR");
        LocalDate date = in.date() != null ? in.date() : LocalDate.now();

        Payment p = new Payment();
        p.companyId = companyId;
        p.customerId = inv.customerId;
        p.invoiceId = inv.id;
        p.paymentDate = date;
        p.amount = amount;
        p.depositAccountId = deposit.id;
        p.method = in.method();
        p.reference = in.reference();
        JournalEntry je = posting.post(new PostingService.Request(companyId, userId, date,
                "Payment for invoice #" + inv.number, "PAYMENT", p.id, List.of(
                PostingService.Line.debit(deposit.id, amount, "Payment received").withCustomer(inv.customerId),
                PostingService.Line.credit(ar.id, amount, "Invoice #" + inv.number).withCustomer(inv.customerId))));
        p.journalEntryId = je.id;

        inv.amountPaid = inv.amountPaid.add(amount);
        inv.status = inv.balanceDue().signum() == 0 ? InvoiceStatus.PAID : InvoiceStatus.PARTIALLY_PAID;
        invoices.save(inv);
        return payments.save(p);
    }

    @Transactional
    public Invoice voidInvoice(UUID companyId, UUID userId, UUID id) {
        Invoice inv = get(companyId, id);
        if (inv.status == InvoiceStatus.VOID) return inv;
        if (inv.amountPaid.signum() > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "HAS_PAYMENTS", "Invoice has payments; it cannot be voided");
        }
        if (inv.journalEntryId != null) {
            posting.reverse(companyId, userId, inv.journalEntryId, LocalDate.now(), "Void invoice #" + inv.number);
        }
        inv.status = InvoiceStatus.VOID;
        return invoices.save(inv);
    }

    public Invoice get(UUID companyId, UUID id) {
        return invoices.findByIdAndCompanyId(id, companyId).orElseThrow(() -> ApiException.notFound("Invoice"));
    }

    private Account systemAccount(UUID companyId, String key) {
        return accounts.findByCompanyIdAndSystemKey(companyId, key)
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "SETUP", "Missing system account " + key));
    }

    private void apply(UUID companyId, Invoice inv, InvoiceInput in) {
        if (in.customerId() == null) throw ApiException.badRequest("CUSTOMER_REQUIRED", "Choose a customer");
        customers.findByIdAndCompanyId(in.customerId(), companyId).orElseThrow(() -> ApiException.notFound("Customer"));
        if (in.lines() == null || in.lines().isEmpty()) throw ApiException.badRequest("LINES_REQUIRED", "Add at least one line");
        inv.customerId = in.customerId();
        inv.invoiceDate = in.invoiceDate() != null ? in.invoiceDate() : LocalDate.now();
        inv.dueDate = in.dueDate() != null ? in.dueDate() : inv.invoiceDate.plusDays(30);
        if (inv.dueDate.isBefore(inv.invoiceDate)) throw ApiException.badRequest("DUE_DATE", "Due date is before the invoice date");
        inv.memo = in.memo();
        UUID defaultIncome = systemAccount(companyId, "SALES").id;
        inv.lines.clear();
        BigDecimal total = BigDecimal.ZERO;
        int n = 1;
        for (LineInput li : in.lines()) {
            if (li.description() == null || li.description().isBlank()) throw ApiException.badRequest("LINE_DESCRIPTION", "Each line needs a description");
            InvoiceLine l = new InvoiceLine();
            l.lineNo = n++;
            l.description = li.description().trim();
            l.quantity = li.quantity() == null ? BigDecimal.ONE : li.quantity();
            l.rate = li.rate() == null ? BigDecimal.ZERO : li.rate();
            l.amount = Money.round(l.quantity.multiply(l.rate));
            if (l.amount.signum() < 0) throw ApiException.badRequest("LINE_NEGATIVE", "Line amounts cannot be negative");
            l.accountId = li.accountId() != null ? li.accountId() : defaultIncome;
            Account a = accounts.findByIdAndCompanyId(l.accountId, companyId).orElseThrow(() -> ApiException.notFound("Account"));
            if (a.type != AccountType.INCOME) throw ApiException.badRequest("LINE_ACCOUNT", "Invoice lines must use an income account");
            inv.lines.add(l);
            total = total.add(l.amount);
        }
        inv.total = total;
    }
}
