package com.ledgerone.ledger;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A posted, immutable, balanced journal entry. Corrections are made by reversal. */
@Entity
@Table(name = "journal_entry")
public class JournalEntry {
    @Id public UUID id = UUID.randomUUID();
    @Column(name = "company_id") public UUID companyId;
    public long number;
    @Column(name = "entry_date") public LocalDate entryDate;
    public String memo;
    @Column(name = "source_type") public String sourceType;
    @Column(name = "source_id") public UUID sourceId;
    @Column(name = "reversal_of_id") public UUID reversalOfId;
    public boolean reversed;
    @Column(name = "created_by") public UUID createdBy;
    @Column(name = "created_at", insertable = false, updatable = false) public Instant createdAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "entry_id", nullable = false)
    @OrderBy("lineNo")
    public List<JournalLine> lines = new ArrayList<>();
}
