package com.ledgerone.ledger;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "journal_line")
public class JournalLine {
    @Id public UUID id = UUID.randomUUID();
    @Column(name = "company_id") public UUID companyId;
    @Column(name = "line_no") public int lineNo;
    @Column(name = "account_id") public UUID accountId;
    public BigDecimal debit = BigDecimal.ZERO;
    public BigDecimal credit = BigDecimal.ZERO;
    public String description;
    @Column(name = "customer_id") public UUID customerId;
    @Column(name = "vendor_id") public UUID vendorId;
}
