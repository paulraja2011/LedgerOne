package com.ledgerone.purchases;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "vendor")
public class Vendor {
    @Id public UUID id = UUID.randomUUID();
    @Column(name = "company_id") public UUID companyId;
    @Column(name = "display_name") public String displayName;
    public String email;
    public String phone;
    public String address;
    @Version public Long version;
}
