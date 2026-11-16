package com.ledgerone.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "company_member")
@IdClass(CompanyMember.Key.class)
public class CompanyMember {
    @Id @Column(name = "company_id") public UUID companyId;
    @Id @Column(name = "user_id") public UUID userId;
    public String role;

    public record Key(UUID companyId, UUID userId) implements Serializable {
        public Key() { this(null, null); }
    }
}
