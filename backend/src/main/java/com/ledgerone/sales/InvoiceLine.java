package com.ledgerone.sales;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "invoice_line")
public class InvoiceLine {
    @Id public UUID id = UUID.randomUUID();
    @Column(name = "line_no") public int lineNo;
    public String description;
    public BigDecimal quantity;
    public BigDecimal rate;
    public BigDecimal amount;
    @Column(name = "account_id") public UUID accountId;
}
