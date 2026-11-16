package com.ledgerone.purchases;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "bill_payment")
public class BillPayment {
    @Id public UUID id = UUID.randomUUID();
    @Column(name = "company_id") public UUID companyId;
    @Column(name = "vendor_id") public UUID vendorId;
    @Column(name = "bill_id") public UUID billId;
    @Column(name = "payment_date") public LocalDate paymentDate;
    public BigDecimal amount;
    @Column(name = "bank_account_id") public UUID bankAccountId;
    @Column(name = "journal_entry_id") public UUID journalEntryId;
}
