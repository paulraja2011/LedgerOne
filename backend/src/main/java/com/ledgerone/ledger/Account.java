package com.ledgerone.ledger;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "account")
public class Account {
    @Id public UUID id = UUID.randomUUID();
    @Column(name = "company_id") public UUID companyId;
    public String code;
    public String name;
    @Enumerated(EnumType.STRING) public AccountType type;
    public String subtype;
    @Column(name = "parent_id") public UUID parentId;
    @Column(name = "system_key") public String systemKey;
    public boolean active = true;
    @Version public Long version;
}
