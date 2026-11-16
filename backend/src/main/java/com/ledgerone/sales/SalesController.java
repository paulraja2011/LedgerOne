package com.ledgerone.sales;

import com.ledgerone.shared.ApiException;
import com.ledgerone.shared.Tenant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class SalesController {

    private final CustomerRepository customers;
    private final InvoiceRepository invoices;
    private final PaymentRepository payments;
    private final InvoiceService service;
    private final JdbcTemplate jdbc;

    public SalesController(CustomerRepository customers, InvoiceRepository invoices, PaymentRepository payments,
                           InvoiceService service, JdbcTemplate jdbc) {
        this.customers = customers;
        this.invoices = invoices;
        this.payments = payments;
        this.service = service;
        this.jdbc = jdbc;
    }

    // ---------- Customers ----------

    public record CustomerRequest(@NotBlank String displayName, String email, String phone, String billingAddress) {}
    public record CustomerDto(UUID id, String displayName, String email, String phone, String billingAddress,
                              BigDecimal openBalance, BigDecimal overdueBalance) {}

    @GetMapping("/customers")
    public List<CustomerDto> listCustomers() {
        UUID cid = Tenant.companyId();
        Map<UUID, BigDecimal[]> bal = new HashMap<>();
        jdbc.query("""
                select customer_id, sum(total - amount_paid),
                       sum(case when due_date < current_date then total - amount_paid else 0 end)
                from invoice where company_id = ? and status in ('OPEN','PARTIALLY_PAID') group by customer_id
                """, rs -> { bal.put(rs.getObject(1, UUID.class), new BigDecimal[]{rs.getBigDecimal(2), rs.getBigDecimal(3)}); }, cid);
        return customers.findByCompanyIdOrderByDisplayName(cid).stream().map(c -> {
            BigDecimal[] b = bal.getOrDefault(c.id, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            return new CustomerDto(c.id, c.displayName, c.email, c.phone, c.billingAddress, b[0], b[1]);
        }).toList();
    }

    @PostMapping("/customers")
    @Transactional
    public CustomerDto createCustomer(@Valid @RequestBody CustomerRequest req) {
        Customer c = new Customer();
        c.companyId = Tenant.companyId();
        apply(c, req);
        customers.save(c);
        return new CustomerDto(c.id, c.displayName, c.email, c.phone, c.billingAddress, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @PutMapping("/customers/{id}")
    @Transactional
    public CustomerDto updateCustomer(@PathVariable UUID id, @Valid @RequestBody CustomerRequest req) {
        Customer c = customers.findByIdAndCompanyId(id, Tenant.companyId()).orElseThrow(() -> ApiException.notFound("Customer"));
        apply(c, req);
        customers.save(c);
        return new CustomerDto(c.id, c.displayName, c.email, c.phone, c.billingAddress, null, null);
    }

    private static void apply(Customer c, CustomerRequest r) {
        c.displayName = r.displayName().trim();
        c.email = r.email();
        c.phone = r.phone();
        c.billingAddress = r.billingAddress();
    }

    // ---------- Invoices ----------

    public record InvoiceLineDto(String description, BigDecimal quantity, BigDecimal rate, BigDecimal amount, UUID accountId) {}
    public record PaymentDto(UUID id, UUID invoiceId, long invoiceNumber, UUID customerId, String customerName,
                             LocalDate date, BigDecimal amount, String method, String reference, UUID journalEntryId) {}
    public record InvoiceDto(UUID id, long number, UUID customerId, String customerName, LocalDate invoiceDate,
                             LocalDate dueDate, InvoiceStatus status, boolean overdue, BigDecimal total,
                             BigDecimal amountPaid, BigDecimal balanceDue, String memo, UUID journalEntryId,
                             List<InvoiceLineDto> lines, List<PaymentDto> payments) {}

    @GetMapping("/invoices")
    public List<InvoiceDto> listInvoices(@RequestParam(required = false) UUID customerId) {
        UUID cid = Tenant.companyId();
        Map<UUID, String> names = customerNames(cid);
        List<Invoice> list = customerId == null
                ? invoices.findByCompanyIdOrderByInvoiceDateDescNumberDesc(cid)
                : invoices.findByCompanyIdAndCustomerIdOrderByInvoiceDateDesc(cid, customerId);
        return list.stream().map(i -> toDto(i, names, false)).toList();
    }

    @GetMapping("/invoices/{id}")
    public InvoiceDto getInvoice(@PathVariable UUID id) {
        UUID cid = Tenant.companyId();
        return toDto(service.get(cid, id), customerNames(cid), true);
    }

    @PostMapping("/invoices")
    public InvoiceDto createInvoice(@RequestBody InvoiceService.InvoiceInput in) {
        UUID cid = Tenant.companyId();
        return toDto(service.create(cid, in), customerNames(cid), true);
    }

    @PutMapping("/invoices/{id}")
    public InvoiceDto updateInvoice(@PathVariable UUID id, @RequestBody InvoiceService.InvoiceInput in) {
        UUID cid = Tenant.companyId();
        return toDto(service.update(cid, id, in), customerNames(cid), true);
    }

    @PostMapping("/invoices/{id}/approve")
    public InvoiceDto approve(@PathVariable UUID id) {
        UUID cid = Tenant.companyId();
        return toDto(service.approve(cid, Tenant.userId(), id), customerNames(cid), true);
    }

    @PostMapping("/invoices/{id}/void")
    public InvoiceDto voidInvoice(@PathVariable UUID id) {
        UUID cid = Tenant.companyId();
        return toDto(service.voidInvoice(cid, Tenant.userId(), id), customerNames(cid), true);
    }

    @PostMapping("/invoices/{id}/payments")
    public InvoiceDto receivePayment(@PathVariable UUID id, @RequestBody InvoiceService.PaymentInput in) {
        UUID cid = Tenant.companyId();
        service.receivePayment(cid, Tenant.userId(), id, in);
        return toDto(service.get(cid, id), customerNames(cid), true);
    }

    @GetMapping("/payments")
    public List<PaymentDto> listPayments() {
        UUID cid = Tenant.companyId();
        Map<UUID, String> names = customerNames(cid);
        Map<UUID, Long> numbers = new HashMap<>();
        invoices.findByCompanyIdOrderByInvoiceDateDescNumberDesc(cid).forEach(i -> numbers.put(i.id, i.number));
        return payments.findByCompanyIdOrderByPaymentDateDesc(cid).stream()
                .map(p -> toDto(p, names, numbers.getOrDefault(p.invoiceId, 0L))).toList();
    }

    private Map<UUID, String> customerNames(UUID cid) {
        Map<UUID, String> m = new HashMap<>();
        customers.findByCompanyIdOrderByDisplayName(cid).forEach(c -> m.put(c.id, c.displayName));
        return m;
    }

    private PaymentDto toDto(Payment p, Map<UUID, String> names, long invoiceNumber) {
        return new PaymentDto(p.id, p.invoiceId, invoiceNumber, p.customerId, names.get(p.customerId), p.paymentDate,
                p.amount, p.method, p.reference, p.journalEntryId);
    }

    private InvoiceDto toDto(Invoice i, Map<UUID, String> names, boolean withPayments) {
        boolean overdue = (i.status == InvoiceStatus.OPEN || i.status == InvoiceStatus.PARTIALLY_PAID)
                && i.dueDate.isBefore(LocalDate.now());
        List<InvoiceLineDto> lines = i.lines.stream()
                .map(l -> new InvoiceLineDto(l.description, l.quantity, l.rate, l.amount, l.accountId)).toList();
        List<PaymentDto> pays = withPayments
                ? payments.findByInvoiceIdOrderByPaymentDate(i.id).stream().map(p -> toDto(p, names, i.number)).toList()
                : List.of();
        return new InvoiceDto(i.id, i.number, i.customerId, names.get(i.customerId), i.invoiceDate, i.dueDate, i.status,
                overdue, i.total, i.amountPaid, i.balanceDue(), i.memo, i.journalEntryId, lines, pays);
    }
}
