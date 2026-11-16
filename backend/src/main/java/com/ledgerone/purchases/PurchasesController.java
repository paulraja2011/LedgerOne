package com.ledgerone.purchases;

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
public class PurchasesController {

    private final VendorRepository vendors;
    private final BillRepository bills;
    private final BillPaymentRepository billPayments;
    private final BillService service;
    private final JdbcTemplate jdbc;

    PurchasesController(VendorRepository vendors, BillRepository bills, BillPaymentRepository billPayments,
                        BillService service, JdbcTemplate jdbc) {
        this.vendors = vendors;
        this.bills = bills;
        this.billPayments = billPayments;
        this.service = service;
        this.jdbc = jdbc;
    }

    public record VendorRequest(@NotBlank String displayName, String email, String phone, String address) {}
    public record VendorDto(UUID id, String displayName, String email, String phone, String address, BigDecimal openBalance) {}

    @GetMapping("/vendors")
    public List<VendorDto> listVendors() {
        UUID cid = Tenant.companyId();
        Map<UUID, BigDecimal> bal = new HashMap<>();
        jdbc.query("select vendor_id, sum(total - amount_paid) from bill where company_id = ? and status in ('OPEN','PARTIALLY_PAID') group by vendor_id",
                rs -> { bal.put(rs.getObject(1, UUID.class), rs.getBigDecimal(2)); }, cid);
        return vendors.findByCompanyIdOrderByDisplayName(cid).stream()
                .map(v -> new VendorDto(v.id, v.displayName, v.email, v.phone, v.address, bal.getOrDefault(v.id, BigDecimal.ZERO)))
                .toList();
    }

    @PostMapping("/vendors")
    @Transactional
    public VendorDto createVendor(@Valid @RequestBody VendorRequest req) {
        Vendor v = new Vendor();
        v.companyId = Tenant.companyId();
        apply(v, req);
        vendors.save(v);
        return new VendorDto(v.id, v.displayName, v.email, v.phone, v.address, BigDecimal.ZERO);
    }

    @PutMapping("/vendors/{id}")
    @Transactional
    public VendorDto updateVendor(@PathVariable UUID id, @Valid @RequestBody VendorRequest req) {
        Vendor v = vendors.findByIdAndCompanyId(id, Tenant.companyId()).orElseThrow(() -> ApiException.notFound("Vendor"));
        apply(v, req);
        vendors.save(v);
        return new VendorDto(v.id, v.displayName, v.email, v.phone, v.address, null);
    }

    private static void apply(Vendor v, VendorRequest r) {
        v.displayName = r.displayName().trim();
        v.email = r.email();
        v.phone = r.phone();
        v.address = r.address();
    }

    public record BillLineDto(String description, BigDecimal amount, UUID accountId) {}
    public record BillPaymentDto(UUID id, LocalDate date, BigDecimal amount, UUID bankAccountId) {}
    public record BillDto(UUID id, String reference, UUID vendorId, String vendorName, LocalDate billDate, LocalDate dueDate,
                          BillStatus status, boolean overdue, BigDecimal total, BigDecimal amountPaid, BigDecimal balanceDue,
                          String memo, UUID journalEntryId, List<BillLineDto> lines, List<BillPaymentDto> payments) {}

    @GetMapping("/bills")
    public List<BillDto> listBills() {
        UUID cid = Tenant.companyId();
        Map<UUID, String> names = vendorNames(cid);
        return bills.findByCompanyIdOrderByBillDateDesc(cid).stream().map(b -> toDto(b, names, false)).toList();
    }

    @GetMapping("/bills/{id}")
    public BillDto getBill(@PathVariable UUID id) {
        UUID cid = Tenant.companyId();
        return toDto(service.get(cid, id), vendorNames(cid), true);
    }

    @PostMapping("/bills")
    public BillDto createBill(@RequestBody BillService.BillInput in) {
        UUID cid = Tenant.companyId();
        return toDto(service.create(cid, in), vendorNames(cid), true);
    }

    @PutMapping("/bills/{id}")
    public BillDto updateBill(@PathVariable UUID id, @RequestBody BillService.BillInput in) {
        UUID cid = Tenant.companyId();
        return toDto(service.update(cid, id, in), vendorNames(cid), true);
    }

    @PostMapping("/bills/{id}/approve")
    public BillDto approve(@PathVariable UUID id) {
        UUID cid = Tenant.companyId();
        return toDto(service.approve(cid, Tenant.userId(), id), vendorNames(cid), true);
    }

    @PostMapping("/bills/{id}/pay")
    public BillDto pay(@PathVariable UUID id, @RequestBody BillService.PayInput in) {
        UUID cid = Tenant.companyId();
        return toDto(service.pay(cid, Tenant.userId(), id, in), vendorNames(cid), true);
    }

    @PostMapping("/bills/{id}/void")
    public BillDto voidBill(@PathVariable UUID id) {
        UUID cid = Tenant.companyId();
        return toDto(service.voidBill(cid, Tenant.userId(), id), vendorNames(cid), true);
    }

    private Map<UUID, String> vendorNames(UUID cid) {
        Map<UUID, String> m = new HashMap<>();
        vendors.findByCompanyIdOrderByDisplayName(cid).forEach(v -> m.put(v.id, v.displayName));
        return m;
    }

    private BillDto toDto(Bill b, Map<UUID, String> names, boolean withPayments) {
        boolean overdue = (b.status == BillStatus.OPEN || b.status == BillStatus.PARTIALLY_PAID) && b.dueDate.isBefore(LocalDate.now());
        List<BillLineDto> lines = b.lines.stream().map(l -> new BillLineDto(l.description, l.amount, l.accountId)).toList();
        List<BillPaymentDto> pays = withPayments
                ? billPayments.findByBillIdOrderByPaymentDate(b.id).stream()
                    .map(p -> new BillPaymentDto(p.id, p.paymentDate, p.amount, p.bankAccountId)).toList()
                : List.of();
        return new BillDto(b.id, b.reference, b.vendorId, names.get(b.vendorId), b.billDate, b.dueDate, b.status, overdue,
                b.total, b.amountPaid, b.balanceDue(), b.memo, b.journalEntryId, lines, pays);
    }
}
