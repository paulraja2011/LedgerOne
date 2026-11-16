package com.ledgerone.sales;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findByCompanyIdOrderByPaymentDateDesc(UUID companyId);
    List<Payment> findByInvoiceIdOrderByPaymentDate(UUID invoiceId);
}
