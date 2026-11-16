package com.ledgerone.sales;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "customer")
public class Customer {
    @Id public UUID id = UUID.randomUUID();
    @Column(name = "company_id") public UUID companyId;
    @Column(name = "display_name") public String displayName;
    public String email;
    public String phone;
    @Column(name = "billing_address") public String billingAddress;
    @Version public Long version;
}
