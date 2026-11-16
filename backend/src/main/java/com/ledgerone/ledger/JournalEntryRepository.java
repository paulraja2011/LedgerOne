package com.ledgerone.ledger;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {
    Page<JournalEntry> findByCompanyIdOrderByEntryDateDescNumberDesc(UUID companyId, Pageable page);
    Optional<JournalEntry> findByIdAndCompanyId(UUID id, UUID companyId);

    @Query("select coalesce(max(e.number), 0) from JournalEntry e where e.companyId = :companyId")
    long maxNumber(UUID companyId);
}
