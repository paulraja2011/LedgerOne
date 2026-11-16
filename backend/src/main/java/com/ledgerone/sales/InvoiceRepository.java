package com.ledgerone.sales;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    List<Invoice> findByCompanyIdOrderByInvoiceDateDescNumberDesc(UUID companyId);
    List<Invoice> findByCompanyIdAndCustomerIdOrderByInvoiceDateDesc(UUID companyId, UUID customerId);
    Optional<Invoice> findByIdAndCompanyId(UUID id, UUID companyId);

    @Query("select coalesce(max(i.number), 1000) from Invoice i where i.companyId = :companyId")
    long maxNumber(UUID companyId);
}
