# NairaCore — Project Progress Tracker

> Last Updated: April 16, 2026
> Developer: David Adewole
> Stack: Java 17, Spring Boot 4.x, PostgreSQL, RabbitMQ

---

## Overall Progress

```
Phase 1 — Auth Module           ✅ Complete
Phase 2 — Accounts Module       ✅ Complete
Phase 3 — Transactions Module   ✅ Complete
Phase 4 — Notifications Module  ⬜ Not Started
Phase 5 — Infrastructure        ⬜ Not Started
Phase 6 — Polish                ⬜ Not Started
```

---

## Phase 1 — Auth Module ✅

### Database Migrations
- [x] V1 — Create schemas (auth, accounts, transactions, ledger, notifications)
- [x] V2 — Create auth tables (users, refresh_tokens)
- [x] V5 — Seed super admin

### Entities
- [x] User entity
- [x] RefreshToken entity
- [x] Role enum (CUSTOMER, TELLER, ADMIN)

### Repositories
- [x] UserRepository
- [x] RefreshTokenRepository

### DTOs
- [x] RegisterRequest
- [x] LoginRequest
- [x] AuthResponse
- [x] UserResponse
- [x] RefreshTokenRequest
- [x] CreateUserRequest

### Services
- [x] AuthService (register, login, refresh, logout, createUser)
- [x] UserDetailsServiceImpl
- [x] UserService (validateUserExists)

### Controllers
- [x] AuthController

### Security
- [x] SecurityConfig
- [x] JwtUtil
- [x] JwtAuthFilter
- [x] UserPrincipal

### Shared
- [x] GlobalExceptionHandler
- [x] ApiResponse wrapper
- [x] BadRequestException
- [x] UnauthorizedException
- [x] ResourceNotFoundException

### Endpoints Verified
- [x] POST /api/v1/auth/register
- [x] POST /api/v1/auth/login
- [x] POST /api/v1/auth/refresh
- [x] POST /api/v1/auth/logout
- [x] POST /api/v1/auth/admin/create-user

### Notes
- Refresh token rotation implemented — single use, 30-day expiry
- Access token expiry: 15 minutes
- JWT claims: userId, email, role
- Expired token returns clean 401 response
- Admin seeded via Flyway migration V5

---

## Phase 2 — Accounts Module ✅

### Database Migrations
- [x] V3 — Create accounts tables (accounts, kyc_details)
- [x] V4 — Add account number sequence
- [x] V8 — Add version column to accounts (optimistic locking)

### Entities
- [x] Account entity
- [x] KycDetails entity
- [x] AccountType enum (SAVINGS, CURRENT, FIXED_DEPOSIT)
- [x] AccountStatus enum (ACTIVE, FROZEN, CLOSED)

### Repositories
- [x] AccountRepository
- [x] KycDetailsRepository

### DTOs
- [x] CreateAccountRequest
- [x] KycRequest
- [x] AccountResponse
- [x] AccountSummaryResponse
- [x] KycResponse

### Services
- [x] AccountService
- [x] AccountNumberGenerator (sequence-based, format: BBBBXXXXXX)

### Controllers
- [x] AccountController

### Business Rules Implemented
- [x] One account per type per customer
- [x] Account number format: 0123XXXXXX (10 digits)
- [x] Sequence-based generation — no collision risk
- [x] Accounts never deleted — only deactivated
- [x] Customer can only view own accounts
- [x] Teller/Admin can view any account
- [x] Teller creates account on behalf of customer via targetUserId
- [x] Invalid UUID returns 400
- [x] Non-existent targetUserId returns 404
- [x] BVN is NOT NULL — mandatory for KYC
- [x] One KYC record per user — covers all accounts

### Endpoints Verified
- [x] POST /api/v1/accounts
- [x] GET /api/v1/accounts/my-accounts
- [x] GET /api/v1/accounts/{accountNumber}
- [x] GET /api/v1/accounts/{accountNumber}/balance
- [x] PUT /api/v1/accounts/{id}/deactivate
- [x] POST /api/v1/accounts/kyc
- [x] GET /api/v1/accounts/kyc

### Notes
- No cross-schema foreign keys — module boundary maintained
- KYC isVerified defaults to false — NIBSS integration pending
- Currency defaults to NGN

---

## Phase 3 — Transactions & Ledger Module ✅

### Database Migrations
- [x] V7 — Create transactions tables (transactions, ledger_entries, reference sequence)

### Entities
- [x] Transaction entity
- [x] LedgerEntry entity
- [x] TransactionType enum (DEPOSIT, WITHDRAWAL, TRANSFER)
- [x] TransactionStatus enum (PENDING, PROCESSING, SUCCESS, FAILED, REVERSED)
- [x] EntryType enum (DEBIT, CREDIT)

### Repositories
- [x] TransactionRepository
- [x] LedgerEntryRepository

### DTOs
- [x] DepositRequest
- [x] WithdrawalRequest
- [x] TransferRequest
- [x] TransactionResponse
- [x] LedgerEntryResponse

### Services
- [x] TransactionService

### Controllers
- [x] TransactionController

### Business Rules Implemented
- [x] Idempotency — duplicate idempotencyKey returns original response
- [x] Transaction states: PENDING → PROCESSING → SUCCESS/FAILED
- [x] Double-entry bookkeeping — every transaction creates ledger entries
- [x] Optimistic locking — concurrent updates handled via @Version
- [x] Balance cannot go below zero
- [x] Account must be ACTIVE to transact
- [x] Ownership check — customers can only transact on own accounts
- [x] Reference format: NRC + 6-digit sequence (e.g. NRC100000)
- [x] balanceBefore and balanceAfter stored on every ledger entry
- [x] Ledger entries are immutable — no updated_at column
- [x] Transfer validates both source and destination accounts
- [x] Cannot transfer to same account

### Endpoints Verified
- [x] POST /api/v1/transactions/deposit
- [x] POST /api/v1/transactions/withdraw
- [x] POST /api/v1/transactions/transfer
- [x] GET /api/v1/transactions/{reference}
- [x] GET /api/v1/transactions/account/{accountNumber}
- [x] GET /api/v1/transactions/ledger/{accountNumber}

### Notes
- All amounts stored as NUMERIC(19,4)
- BigDecimal.compareTo() used for balance checks — never equals()
- @Transactional on all write operations — full rollback on failure
- @Slf4j logging on every successful transaction

---

## Phase 4 — Notifications Module ⬜

### Planned Features
- [ ] RabbitMQ event publishing from TransactionService
- [ ] NotificationConsumer listening to transaction events
- [ ] Email notifications on deposit, withdrawal, transfer
- [ ] Notification log stored in notifications schema
- [ ] notification_logs table migration

### Planned Endpoints
- [ ] GET /api/v1/notifications — get notifications for logged in user

---

## Phase 5 — Infrastructure ⬜

### Docker
- [ ] Dockerfile for NairaCore application
- [ ] Docker Compose (app + PostgreSQL + RabbitMQ)
- [ ] Multi-stage build for smaller image size
- [ ] Health checks

### CI/CD
- [ ] GitHub Actions workflow
- [ ] Build and test on pull request
- [ ] Docker image build and push on merge to main

### Monitoring
- [ ] Spring Boot Actuator
- [ ] Prometheus metrics endpoint
- [ ] Grafana dashboard
- [ ] Key metrics: transaction throughput, error rates, response times

### Deployment
- [ ] AWS deployment (ECS or EC2)
- [ ] Environment-specific application.yml profiles
- [ ] Secrets management

---

## Phase 6 — Polish ⬜

### Testing
- [ ] Unit tests for AuthService
- [ ] Unit tests for AccountService
- [ ] Unit tests for TransactionService
- [ ] Integration tests for auth endpoints
- [ ] Integration tests for transaction endpoints
- [ ] 80%+ code coverage target

### Documentation
- [x] Swagger UI configured
- [x] README.md
- [x] PROJECT.md
- [ ] @Operation annotations on all endpoints
- [ ] Architecture diagram
- [ ] Postman collection export

### Code Quality
- [ ] Remove all unused imports
- [ ] Review and clean up TODOs
- [ ] Consistent error messages across all modules
- [ ] Review all @PreAuthorize annotations

---

## Database Migration History

| Version | Description | Status |
|---|---|---|
| V1 | Create schemas | ✅ Applied |
| V2 | Create auth tables | ✅ Applied |
| V3 | Create accounts tables | ✅ Applied |
| V4 | Add account number sequence | ✅ Applied |
| V5 | Seed super admin | ✅ Applied |
| V6 | Fix admin password hash | ✅ Applied |
| V7 | Create transactions tables | ✅ Applied |
| V8 | Add version column to accounts | ✅ Applied |

---

## Known Issues / Technical Debt

| Issue | Priority | Status |
|---|---|---|
| KYC isVerified always false — no NIBSS integration | Medium | Open |
| No pagination on transaction history | Medium | Open |
| No rate limiting on auth endpoints | High | Open |
| No password reset flow | Medium | Open |
| RabbitMQ not yet connected for notifications | Low | Open |

---

## Decisions Log

| Date | Decision | Reason |
|---|---|---|
| Apr 2026 | Modular monolith over microservices | Faster to build, easier to maintain, extractable later |
| Apr 2026 | Single DB with separate schemas | Module isolation without operational complexity |
| Apr 2026 | Flyway over Hibernate DDL | Schema changes must be versioned and auditable |
| Apr 2026 | Sequence-based account numbers | Zero collision risk, atomic, no retry loop needed |
| Apr 2026 | Refresh token rotation | Single-use tokens reduce risk of stolen token reuse |
| Apr 2026 | No cross-schema foreign keys | Maintains module independence for future extraction |
| Apr 2026 | BigDecimal for all amounts | Exact decimal representation — float/double unsuitable |
| Apr 2026 | BVN not globally unique in DB | NIBSS is the authority, not NairaCore |
