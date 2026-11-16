package com.ledgerone.purchases;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface VendorRepository extends JpaRepository<Vendor, UUID> {
    List<Vendor> findByCompanyIdOrderByDisplayName(UUID companyId);
    Optional<Vendor> findByIdAndCompanyId(UUID id, UUID companyId);
}

interface BillRepository extends JpaRepository<Bill, UUID> {
    List<Bill> findByCompanyIdOrderByBillDateDesc(UUID companyId);
    Optional<Bill> findByIdAndCompanyId(UUID id, UUID companyId);
}

interface BillPaymentRepository extends JpaRepository<BillPayment, UUID> {
    List<BillPayment> findByBillIdOrderByPaymentDate(UUID billId);
}
