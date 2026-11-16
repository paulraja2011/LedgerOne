package com.ledgerone.purchases;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "bill")
public class Bill {
    @Id public UUID id = UUID.randomUUID();
    @Column(name = "company_id") public UUID companyId;
    public String reference;
    @Column(name = "vendor_id") public UUID vendorId;
    @Column(name = "bill_date") public LocalDate billDate;
    @Column(name = "due_date") public LocalDate dueDate;
    @Enumerated(EnumType.STRING) public BillStatus status = BillStatus.DRAFT;
    public BigDecimal total = BigDecimal.ZERO;
    @Column(name = "amount_paid") public BigDecimal amountPaid = BigDecimal.ZERO;
    public String memo;
    @Column(name = "journal_entry_id") public UUID journalEntryId;
    @Version public Long version;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "bill_id", nullable = false)
    @OrderBy("lineNo")
    public List<BillLine> lines = new ArrayList<>();

    public BigDecimal balanceDue() { return total.subtract(amountPaid); }
}
