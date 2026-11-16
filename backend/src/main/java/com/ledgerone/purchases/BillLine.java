package com.ledgerone.purchases;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "bill_line")
public class BillLine {
    @Id public UUID id = UUID.randomUUID();
    @Column(name = "line_no") public int lineNo;
    public String description;
    public BigDecimal amount;
    @Column(name = "account_id") public UUID accountId;
}
