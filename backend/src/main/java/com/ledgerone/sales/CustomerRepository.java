package com.ledgerone.sales;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    List<Customer> findByCompanyIdOrderByDisplayName(UUID companyId);
    Optional<Customer> findByIdAndCompanyId(UUID id, UUID companyId);
}
