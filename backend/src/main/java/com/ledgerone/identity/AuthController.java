package com.ledgerone.identity;

import com.ledgerone.company.Company;
import com.ledgerone.company.CompanyRepository;
import com.ledgerone.shared.ApiException;
import com.ledgerone.shared.Tenant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserRepository users;
    private final MemberRepository members;
    private final CompanyRepository companies;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final long ttlMinutes;

    public AuthController(UserRepository users, MemberRepository members, CompanyRepository companies,
                          PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder,
                          @Value("${ledgerone.jwt.ttl-minutes}") long ttlMinutes) {
        this.users = users;
        this.members = members;
        this.companies = companies;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.ttlMinutes = ttlMinutes;
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}
    public record UserDto(UUID id, String name, String email, String role) {}
    public record CompanyRef(UUID id, String name, String baseCurrency) {}
    public record LoginResponse(String token, UserDto user, CompanyRef company) {}

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        AppUser user = users.findByEmailIgnoreCase(req.email().trim())
                .filter(u -> passwordEncoder.matches(req.password(), u.passwordHash))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Email or password is incorrect"));
        CompanyMember m = members.findByUserId(user.id).stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "NO_COMPANY", "User has no company"));
        Company company = companies.findById(m.companyId).orElseThrow();

        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("ledgerone")
                .subject(user.id.toString())
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(ttlMinutes)))
                .claim("cid", company.id.toString())
                .claim("role", m.role)
                .claim("name", user.name)
                .claim("email", user.email)
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new LoginResponse(token, new UserDto(user.id, user.name, user.email, m.role),
                new CompanyRef(company.id, company.name, company.baseCurrency));
    }

    @GetMapping("/me")
    public LoginResponse me() {
        AppUser user = users.findById(Tenant.userId()).orElseThrow(() -> ApiException.notFound("User"));
        Company company = companies.findById(Tenant.companyId()).orElseThrow();
        String role = members.findById(new CompanyMember.Key(company.id, user.id)).map(x -> x.role).orElse("VIEWER");
        return new LoginResponse(null, new UserDto(user.id, user.name, user.email, role),
                new CompanyRef(company.id, company.name, company.baseCurrency));
    }
}
