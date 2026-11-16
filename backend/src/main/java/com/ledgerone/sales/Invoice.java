package com.ledgerone.sales;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "invoice")
public class Invoice {
    @Id public UUID id = UUID.randomUUID();
    @Column(name = "company_id") public UUID companyId;
    public long number;
    @Column(name = "customer_id") public UUID customerId;
    @Column(name = "invoice_date") public LocalDate invoiceDate;
    @Column(name = "due_date") public LocalDate dueDate;
    @Enumerated(EnumType.STRING) public InvoiceStatus status = InvoiceStatus.DRAFT;
    public BigDecimal total = BigDecimal.ZERO;
    @Column(name = "amount_paid") public BigDecimal amountPaid = BigDecimal.ZERO;
    public String memo;
    @Column(name = "journal_entry_id") public UUID journalEntryId;
    @Version public Long version;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "invoice_id", nullable = false)
    @OrderBy("lineNo")
    public List<InvoiceLine> lines = new ArrayList<>();

    public BigDecimal balanceDue() { return total.subtract(amountPaid); }
}
