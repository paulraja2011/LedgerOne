package com.ledgerone.identity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MemberRepository extends JpaRepository<CompanyMember, CompanyMember.Key> {
    List<CompanyMember> findByUserId(UUID userId);
}
