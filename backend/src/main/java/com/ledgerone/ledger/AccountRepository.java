package com.ledgerone.ledger;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    List<Account> findByCompanyIdOrderByCode(UUID companyId);
    Optional<Account> findByIdAndCompanyId(UUID id, UUID companyId);
    Optional<Account> findByCompanyIdAndSystemKey(UUID companyId, String systemKey);
    boolean existsByCompanyIdAndCode(UUID companyId, String code);
}
