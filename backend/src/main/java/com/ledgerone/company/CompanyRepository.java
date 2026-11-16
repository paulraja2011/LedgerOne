package com.ledgerone.company;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, UUID> {
    /** Row lock used to serialize document numbering within one company. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Company c where c.id = :id")
    Optional<Company> lockById(UUID id);
}
