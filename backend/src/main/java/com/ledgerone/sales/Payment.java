package com.ledgerone.sales;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "payment")
public class Payment {
    @Id public UUID id = UUID.randomUUID();
    @Column(name = "company_id") public UUID companyId;
    @Column(name = "customer_id") public UUID customerId;
    @Column(name = "invoice_id") public UUID invoiceId;
    @Column(name = "payment_date") public LocalDate paymentDate;
    public BigDecimal amount;
    @Column(name = "deposit_account_id") public UUID depositAccountId;
    public String method;
    public String reference;
    @Column(name = "journal_entry_id") public UUID journalEntryId;
}
