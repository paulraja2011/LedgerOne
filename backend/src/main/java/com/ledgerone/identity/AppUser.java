package com.ledgerone.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_user")
public class AppUser {
    @Id public UUID id = UUID.randomUUID();
    public String email;
    public String name;
    @Column(name = "password_hash") public String passwordHash;
    @Column(name = "created_at", insertable = false, updatable = false) public Instant createdAt;
}
