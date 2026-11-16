package com.ledgerone.company;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "company")
public class Company {
    @Id public UUID id = UUID.randomUUID();
    public String name;
    @Column(name = "base_currency") public String baseCurrency = "USD";
    @Column(name = "fiscal_year_start_month") public int fiscalYearStartMonth = 1;
    @Column(name = "lock_date") public LocalDate lockDate;
}
