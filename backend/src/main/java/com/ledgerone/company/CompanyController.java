package com.ledgerone.company;

import com.ledgerone.shared.ApiException;
import com.ledgerone.shared.Tenant;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/company")
public class CompanyController {
    private final CompanyRepository companies;

    public CompanyController(CompanyRepository companies) {
        this.companies = companies;
    }

    @GetMapping
    public Company get() {
        return companies.findById(Tenant.companyId()).orElseThrow(() -> ApiException.notFound("Company"));
    }

    public record UpdateCompany(String name, LocalDate lockDate) {}

    @PatchMapping
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public Company update(@RequestBody UpdateCompany req) {
        Company c = get();
        if (req.name() != null && !req.name().isBlank()) c.name = req.name().trim();
        c.lockDate = req.lockDate();
        return companies.save(c);
    }
}
