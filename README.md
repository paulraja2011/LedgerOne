# LedgerOne

Small-business accounting web app: React + TypeScript frontend, Spring Boot 3 (Java 21) backend,
PostgreSQL, built around a double-entry general ledger.

## What works

| Area | Features |
| --- | --- |
| Sign-in | JWT login, roles (`ADMIN`, `ACCOUNTANT`, ...), company (tenant) carried in the token |
| Chart of accounts | Default chart, create/edit accounts, live balances, account register with running balance |
| Journal | Manual journal entries (must balance), reversal, entry detail |
| Sales | Customers, invoices (draft → approve → partially paid → paid, or void), receive payments |
| Purchases | Vendors, bills (draft → approve → pay, or void), pay from bank or credit card |
| Reports | Dashboard KPIs, Profit and loss, Balance sheet, Trial balance, A/R aging |

Every money document posts through one `PostingService`, which rejects unbalanced entries, inactive or
foreign accounts, and dates on or before the company lock date. Posted entries are never edited; voids
post reversing entries.

## Run locally

Prerequisites: Java 21, Maven 3.9+, Node 20+, PostgreSQL 16 (or `docker compose up -d`).

```bash
# 1. Database (skip if using docker compose)
createuser ledgerone -P            # password: ledgerone
createdb ledgerone -O ledgerone

# 2. Backend on http://localhost:8080  (Flyway creates the schema, demo data is seeded on first start)
cd backend && mvn spring-boot:run

# 3. Frontend on http://localhost:5173  (proxies /api to the backend)
cd frontend && npm install && npm run dev
```

Sign in with **admin@ledgerone.dev / Admin@123**. API docs: http://localhost:8080/swagger-ui.html

Configuration (environment variables): `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`
(always set in real environments), `SEED_DEMO_DATA=false` to skip the demo company.

## Layout

```
backend/src/main/java/com/ledgerone/
  shared/      errors (RFC 9457 problem details), tenant resolution, money helpers
  identity/    users, membership, JWT security, /auth
  company/     company settings, lock date, demo data seeder
  ledger/      accounts, journal entries, PostingService
  sales/       customers, invoices, payments
  purchases/   vendors, bills, bill payments
  reporting/   P&L, balance sheet, trial balance, aging, dashboard
backend/src/main/resources/db/migration/   Flyway SQL
frontend/src/
  api/         fetch client + TypeScript types
  components/  layout shell, modal, badges
  pages/       one file per screen
```

## Not yet built (see the design doc roadmap)

Sales tax, bank feeds and reconciliation, multi-currency, payroll, PDF/email invoices, recurring
transactions, period-close workflow UI, PostgreSQL row-level security, refresh tokens / external IdP,
automated test suite.
