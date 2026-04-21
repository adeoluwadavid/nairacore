# NairaCore — System Design Interview Guide

> This document covers system design interview questions related to
> NairaCore's architecture and how to scale it for production.

---

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Database Design](#database-design)
- [Scalability](#scalability)
- [Reliability and Fault Tolerance](#reliability-and-fault-tolerance)
- [Security Architecture](#security-architecture)
- [Microservices Migration](#microservices-migration)
- [Caching Strategy](#caching-strategy)
- [Monitoring and Observability](#monitoring-and-observability)
- [Real-World Scenarios](#real-world-scenarios)

---

## Architecture Overview

---

**Q: Walk me through the high-level architecture of NairaCore.**

NairaCore is a modular monolith — a single deployable Spring Boot application
with clearly defined internal module boundaries.

```
┌─────────────────────────────────────────────────────────┐
│                      NairaCore                          │
│                                                         │
│  ┌──────────┐  ┌──────────┐  ┌─────────────┐           │
│  │   Auth   │  │ Accounts │  │Transactions │           │
│  │  Module  │  │  Module  │  │   Module    │           │
│  └──────────┘  └──────────┘  └─────────────┘           │
│                                     │                   │
│                              ┌──────────────┐           │
│                              │Notifications │           │
│                              │   Module     │           │
│                              └──────────────┘           │
│                                     │                   │
└─────────────────────────────────────┼───────────────────┘
                                      │
              ┌───────────────────────┼───────────────────┐
              │                       │                   │
        ┌─────▼──────┐         ┌──────▼─────┐    ┌───────▼──────┐
        │ PostgreSQL │         │  RabbitMQ  │    │   Mailpit    │
        │  Database  │         │  Broker    │    │  (Email Dev) │
        └────────────┘         └────────────┘    └──────────────┘
```

**Request flow for a deposit:**
```
1. Client sends POST /api/v1/transactions/deposit with JWT
2. JwtAuthFilter validates token, sets UserPrincipal in SecurityContext
3. @PreAuthorize checks role
4. TransactionController extracts userId and role
5. TransactionService processes:
   a. Idempotency check
   b. Account validation
   c. Balance update (with optimistic locking)
   d. Ledger entry creation
   e. Transaction marked SUCCESS
   f. Event published to RabbitMQ
6. NotificationConsumer receives event
7. Email sent via JavaMailSender → Mailpit
8. NotificationLog saved to database
9. Response returned to client
```

---

**Q: Why did you choose a modular monolith over microservices for NairaCore?**

The decision was driven by practical engineering tradeoffs:

**Arguments for modular monolith:**

1. **Development velocity** — no service discovery, no inter-service auth,
   no distributed tracing setup. One codebase, one deployment, one database.

2. **Simplicity under uncertainty** — we did not have enough production data
   to know exactly where service boundaries should be. Extracting too early
   creates microservices with wrong boundaries that are expensive to fix.

3. **Team size** — microservices shine when multiple teams own different services.
   For a single developer or small team, the overhead outweighs the benefits.

4. **Cost** — running 6 separate services with their own databases costs
   significantly more than one monolith. For early-stage fintechs, this matters.

**Microservices when:**
- Different modules have dramatically different scaling needs
- Multiple teams need to deploy independently
- Technology diversity is needed (some modules in Go, some in Java)
- The company is at Flutterwave/Paystack scale

**The modular monolith exit strategy:**
NairaCore is designed for clean extraction:
- No cross-schema foreign keys
- No direct cross-module repository access
- RabbitMQ already used for async communication
- Each module could be extracted to a separate service by:
  1. Creating a new Spring Boot project
  2. Moving the module's code
  3. Replacing in-process calls with HTTP/messaging

---

**Q: How would you design NairaCore to handle 10 million customers?**

At 10 million customers, the current architecture needs several enhancements:

**Database layer:**
```
Write → Primary PostgreSQL instance
Reads → Read replica(s)

Connection pooling:
→ HikariCP (built into Spring Boot) configured for optimal pool size
→ Pool size = (2 × CPU cores) + disk spindles
→ PgBouncer for connection multiplexing at very high concurrency
```

**Application layer:**
```
Multiple NairaCore instances behind a load balancer
→ AWS Application Load Balancer
→ Stateless design (JWT) allows any instance to handle any request
→ Auto-scaling based on CPU/memory metrics
```

**Caching:**
```
Redis for:
→ Account balances (read-heavy, updated on transaction)
→ User profiles (rarely changed)
→ Rate limiting counters

Cache invalidation:
→ Write-through: update DB and cache simultaneously
→ Event-driven: update cache when RabbitMQ event received
```

**Database partitioning:**
```
ledger_entries → partition by created_at (monthly partitions)
transactions → partition by created_at
→ Queries for recent transactions only scan recent partitions
→ Old partitions archived to cold storage
```

**Message queue:**
```
RabbitMQ → multiple notification consumers (concurrent processing)
→ Or migrate to Kafka for higher throughput and replay capability
```

---

## Database Design

---

**Q: Explain the decision to have no cross-schema foreign keys.**

NairaCore has five schemas: auth, accounts, transactions, ledger, notifications.
Despite logical relationships between them, we deliberately avoided foreign
key constraints crossing schema boundaries.

**Example of what we avoided:**
```sql
-- We did NOT do this:
ALTER TABLE accounts.accounts
ADD CONSTRAINT fk_user
FOREIGN KEY (user_id) REFERENCES auth.users(id);
```

**Why:**

1. **Module independence** — the accounts schema should not have a hard
   database dependency on the auth schema. They are separate bounded contexts.

2. **Extraction readiness** — with foreign keys, moving schemas to separate
   databases is impossible without breaking constraints. Without them, each
   schema can be moved independently by just changing connection strings.

3. **Performance** — foreign key checks add overhead to every insert and update.
   At high transaction volumes, this is significant.

4. **Flexibility** — in distributed systems, referential integrity is maintained
   at the application level, not the database level. This is standard practice.

**How we maintain integrity without foreign keys:**
- Application-level validation: `userService.validateUserExists(userId)`
- `@Transactional` ensures atomic operations
- Event-driven eventual consistency for derived data

---

**Q: How would you design the database schema if NairaCore needed to support
multiple currencies?**

Current schema:
```sql
balance NUMERIC(19, 4) NOT NULL DEFAULT 0.0000,
currency VARCHAR(3) NOT NULL DEFAULT 'NGN'
```

Currently, one account has one currency. For multi-currency support:

**Option 1 — Multiple balances per account:**
```sql
CREATE TABLE accounts.account_balances (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL REFERENCES accounts.accounts(id),
    currency VARCHAR(3) NOT NULL,
    balance NUMERIC(19, 4) NOT NULL DEFAULT 0.0000,
    UNIQUE(account_id, currency)
);
```

**Option 2 — Separate account per currency (current approach):**
Customer creates one SAVINGS account for NGN and one for USD.
Simpler but means managing multiple accounts.

**Option 3 — Exchange rate service:**
```
All balances stored in NGN (base currency)
→ When displaying, convert to user's preferred currency
→ Exchange rates fetched from a rates service or API
→ Historical rates stored for accurate ledger replay
```

**Production consideration:**
Multi-currency introduces complexity around exchange rates, conversion fees,
and regulatory requirements (CBN has specific rules for forex accounts).
Most Nigerian fintechs start with NGN only and add USD later when regulatory
approval is obtained.

---

**Q: How would you design a database schema for loan products in NairaCore?**

Loans are a core banking product. A simple loan schema:

```sql
CREATE TABLE loans.loan_products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    interest_rate NUMERIC(5, 2) NOT NULL,   -- e.g. 24.00 (%)
    tenure_months INT NOT NULL,
    min_amount NUMERIC(19, 4) NOT NULL,
    max_amount NUMERIC(19, 4) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE loans.loans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    product_id UUID NOT NULL REFERENCES loans.loan_products(id),
    account_number VARCHAR(10) NOT NULL,     -- disbursement account
    principal NUMERIC(19, 4) NOT NULL,
    interest_rate NUMERIC(5, 2) NOT NULL,    -- rate at time of disbursement
    total_repayable NUMERIC(19, 4) NOT NULL,
    outstanding_balance NUMERIC(19, 4) NOT NULL,
    disbursement_date DATE,
    maturity_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE loans.repayments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_id UUID NOT NULL REFERENCES loans.loans(id),
    amount NUMERIC(19, 4) NOT NULL,
    payment_date TIMESTAMP NOT NULL DEFAULT NOW(),
    transaction_reference VARCHAR(20) NOT NULL,
    type VARCHAR(20) NOT NULL   -- PRINCIPAL, INTEREST, PENALTY
);
```

**Integration with existing system:**
- Loan disbursement creates a DEPOSIT transaction to the customer's account
- Loan repayment creates a WITHDRAWAL transaction
- All movements go through the ledger system for audit trail
- BVN and KYC required before loan approval

---

**Q: How would you handle database migrations in a zero-downtime deployment?**

Zero-downtime deployments require careful migration strategy.

**Problematic migrations:**
```sql
ALTER TABLE accounts.accounts RENAME COLUMN balance TO current_balance;
-- Old code reads 'balance', new code reads 'current_balance'
-- Between deployments, one will fail
```

**Safe migration strategy — expand/contract:**

**Phase 1 — Expand (add new):**
```sql
-- V10__add_new_balance_column.sql
ALTER TABLE accounts.accounts ADD COLUMN current_balance NUMERIC(19, 4);
UPDATE accounts.accounts SET current_balance = balance;
```
Deploy code that reads from BOTH columns.

**Phase 2 — Contract (remove old):**
```sql
-- V11__remove_old_balance_column.sql
ALTER TABLE accounts.accounts DROP COLUMN balance;
```
Deploy code that only reads new column.

**In NairaCore with Flyway:**
- Each migration is atomic
- `ddl-auto: validate` catches mismatches before requests are served
- Health checks prevent traffic until the app is fully started

**Additional techniques:**
- `@Column(name = "current_balance")` — rename in entity without DB change
- Blue-green deployments — switch traffic after full validation
- Feature flags — new code paths enabled after migration completes

---

## Scalability

---

**Q: What are the potential bottlenecks in NairaCore at high load?**

**1. Database — most likely bottleneck:**
```
Single PostgreSQL instance handles all reads and writes
→ Solution: Read replicas for GET endpoints
→ Connection pool tuning
→ Query optimization (indexes, efficient JOINs)
```

**2. Optimistic locking conflicts:**
```
Under high concurrency, the same account may see many conflicts
→ Solution: Implement retry logic with exponential backoff
→ Queue transfers to the same account (rate limiting per account)
```

**3. RabbitMQ consumer throughput:**
```
Single NotificationConsumer processes one message at a time
→ Solution: Increase consumer concurrency:
spring.rabbitmq.listener.simple.concurrency=5
spring.rabbitmq.listener.simple.max-concurrency=10
```

**4. JWT validation per request:**
```
Every request decodes and validates JWT (cryptographic operation)
→ Solution: Cache decoded claims in request context
→ Already lightweight in NairaCore (local validation, no DB call)
```

**5. Single application instance:**
```
All traffic to one JVM
→ Solution: Horizontal scaling behind load balancer
→ Stateless design already supports this
```

---

**Q: How would you implement rate limiting in NairaCore?**

Rate limiting prevents abuse — a single user cannot make unlimited API calls.
In banking, it also prevents brute force attacks on login.

**Strategy 1 — Token bucket at API Gateway level:**
```
AWS API Gateway or Nginx:
→ 10 requests per second per IP for login
→ 100 requests per second per user for authenticated endpoints
→ Configured without touching application code
```

**Strategy 2 — Application-level with Redis:**
```java
@Component
public class RateLimiter {
    private final RedisTemplate<String, Integer> redis;

    public boolean isAllowed(String userId, String endpoint) {
        String key = "rate:" + userId + ":" + endpoint;
        Long count = redis.opsForValue().increment(key);
        if (count == 1) {
            redis.expire(key, Duration.ofMinutes(1));
        }
        return count <= 100; // 100 requests per minute
    }
}
```

**Nigerian fintech specific rates:**
- Login: 5 attempts per 15 minutes (brute force protection)
- Transaction initiation: 10 per minute (fraud prevention)
- OTP requests: 3 per 5 minutes

---

**Q: How would you implement pagination for the transaction history endpoint?**

Currently `getAccountTransactions()` returns all transactions — a problem if
a customer has 10,000 transactions.

**Adding pagination:**

Repository:
```java
Page<Transaction> findAllByAccountNumber(String accountNumber, Pageable pageable);
```

Service:
```java
public Page<TransactionResponse> getAccountTransactions(
        String accountNumber, int page, int size) {
    Pageable pageable = PageRequest.of(page, size,
            Sort.by("createdAt").descending());
    return transactionRepository
            .findAllByAccountNumber(accountNumber, pageable)
            .map(this::mapToTransactionResponse);
}
```

Controller:
```java
@GetMapping("/account/{accountNumber}")
public ResponseEntity<ApiResponse<Page<TransactionResponse>>> getTransactions(
        @PathVariable String accountNumber,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
) { }
```

Response includes pagination metadata:
```json
{
  "data": {
    "content": [...],
    "totalElements": 150,
    "totalPages": 8,
    "number": 0,
    "size": 20
  }
}
```

**Cursor-based pagination (preferred for large datasets):**
Instead of page numbers, use the last transaction's `createdAt` as a cursor.
More efficient for large datasets as it avoids `OFFSET` scans.

---

## Reliability and Fault Tolerance

---

**Q: What happens if the database goes down while NairaCore is processing
a transaction?**

**During transaction processing:**

```java
@Transactional
public TransactionResponse deposit(...) {
    // Database goes down here
    accountRepository.save(account); // throws DataAccessException
}
```

1. `DataAccessException` is caught by our generic exception handler
2. `@Transactional` rolls back — no partial data (the DB was down anyway)
3. Transaction record may or may not have been written (depends on timing)
4. Client receives 500 error
5. Client uses idempotency key to retry when DB recovers
6. If transaction was partially written: retry finds idempotency key → returns
   original response without reprocessing

**Recovery:**
- Database connection pool retries failed connections automatically
- Spring Boot's health check endpoint reports unhealthy
- Load balancer stops sending traffic to the instance
- DBA brings database back up
- Traffic resumes automatically

---

**Q: How would you implement circuit breaker pattern in NairaCore?**

A circuit breaker prevents cascading failures when a downstream service is down.

```
CLOSED state: requests flow normally
↓ (too many failures)
OPEN state: requests immediately fail without calling the service
↓ (after timeout)
HALF-OPEN state: one test request allowed
↓ (success)
CLOSED state: normal operation resumes
```

**Implementation with Resilience4j:**

```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
</dependency>
```

```java
@CircuitBreaker(name = "emailService", fallbackMethod = "emailFallback")
public void sendEmail(String to, String subject, String message) {
    mailSender.send(...);
}

public void emailFallback(String to, String subject, String message, Exception e) {
    // Save to retry queue instead of failing
    pendingNotificationRepository.save(new PendingNotification(to, subject, message));
    log.warn("Email service circuit open. Notification queued for retry.");
}
```

**In NairaCore context:**
The RabbitMQ consumer already provides a degree of resilience — if the email
service is down, messages stay in the queue. The circuit breaker would
additionally prevent hammering a down service and log failures clearly.

---

**Q: How would you ensure data consistency between the account balance
and the ledger?**

**Problem:**
If a bug exists where balance is updated but ledger entry is not created
(or vice versa), the system is inconsistent.

**Solution 1 — Atomic operations in one transaction:**
```java
@Transactional
public void deposit(...) {
    account.setBalance(newBalance);
    accountRepository.save(account);       // balance update
    createLedgerEntry(...);                // ledger entry
    // If either fails, BOTH roll back
}
```

**Solution 2 — Reconciliation job:**
```sql
-- Run daily/hourly
SELECT
    a.account_number,
    a.balance AS account_balance,
    MAX(le.balance_after) AS ledger_balance,
    a.balance - MAX(le.balance_after) AS discrepancy
FROM accounts.accounts a
LEFT JOIN transactions.ledger_entries le ON a.account_number = le.account_number
GROUP BY a.account_number, a.balance
HAVING a.balance != MAX(le.balance_after);
```

**Solution 3 — Event sourcing (advanced):**
The account balance is never stored directly. Instead, the balance is always
calculated by replaying all ledger entries. This makes the ledger the single
source of truth:

```java
public BigDecimal getBalance(String accountNumber) {
    return ledgerEntryRepository
        .findAllByAccountNumber(accountNumber)
        .stream()
        .map(e -> e.getEntryType() == CREDIT ? e.getAmount() : e.getAmount().negate())
        .reduce(BigDecimal.ZERO, BigDecimal::add);
}
```

This is computationally expensive but eliminates any possibility of
balance/ledger inconsistency. Banks like N26 use this approach.

---

## Security Architecture

---

**Q: How would you secure NairaCore for production deployment?**

**Network security:**
```
Internet → WAF (Web Application Firewall) → Load Balancer → NairaCore
         → Blocks SQL injection, XSS, OWASP Top 10 attacks
```

**Infrastructure:**
```
NairaCore runs in private subnet (not internet-accessible)
Only load balancer is in public subnet
Database accessible only from NairaCore's security group
RabbitMQ accessible only from NairaCore's security group
```

**Secrets management:**
```
No secrets in code or Docker images
JWT_SECRET → AWS Secrets Manager
DB password → AWS Secrets Manager
Retrieved at startup via AWS SDK
```

**HTTPS only:**
```
SSL termination at load balancer
All internal traffic over private VPC (no SSL needed internally)
HSTS header set: Strict-Transport-Security
```

**API security:**
```
Rate limiting on all endpoints
CORS configured for specific origins only
JWT with 15-minute expiry (short window)
Refresh token rotation (prevents token theft)
No sensitive data in JWT payload
```

**Database security:**
```
Separate DB user per service (principle of least privilege)
  nairacore_auth → read/write auth schema only
  nairacore_accounts → read/write accounts schema only
Encryption at rest (AWS RDS encryption)
Point-in-time recovery enabled
Automated backups
```

---

**Q: How would you implement two-factor authentication (2FA) for NairaCore?**

2FA requires a second verification step after password login, typically
via SMS (OTP) or authenticator app.

**OTP-based 2FA flow:**
```
1. User logs in with email + password → credentials valid
2. Instead of returning tokens, return: { "requiresOtp": true, "sessionToken": "..." }
3. System generates 6-digit OTP, stores in Redis with 5-minute TTL
4. OTP sent to user's phone via Africa's Talking SMS
5. User submits OTP to POST /api/v1/auth/verify-otp
6. Server validates OTP from Redis
7. If valid: issue access token + refresh token
8. OTP deleted from Redis (single use)
```

**Schema additions:**
```sql
-- No DB needed for OTP if using Redis
-- But for audit trail:
CREATE TABLE auth.otp_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    phone_number VARCHAR(20) NOT NULL,
    sent_at TIMESTAMP NOT NULL DEFAULT NOW(),
    verified_at TIMESTAMP,
    expired BOOLEAN NOT NULL DEFAULT FALSE
);
```

**Redis OTP storage:**
```java
String key = "otp:" + userId;
redisTemplate.opsForValue().set(key, otp, Duration.ofMinutes(5));
```

---

## Microservices Migration

---

**Q: How would you migrate NairaCore from a modular monolith to microservices?**

The key principle: **extract incrementally, never all at once.**

**Step 1 — Identify extraction candidates:**
```
Candidates for extraction (high traffic or independent scaling needs):
→ TransactionService — highest load, needs independent scaling
→ NotificationService — already event-driven via RabbitMQ

Not worth extracting initially:
→ AuthService — tight coupling with security
→ AccountService — depends on UserService validation
```

**Step 2 — Extract Notification Service first (easiest):**
Already communicates via RabbitMQ — no code changes needed.
1. Create new Spring Boot project: `nairacore-notification-service`
2. Move `notifications` module code
3. Point RabbitMQ consumer to new service
4. Remove notifications code from monolith
5. Change only configuration — no business logic changes

**Step 3 — Extract Transaction Service:**
```
Challenge: TransactionService uses AccountRepository directly
Solution 1: HTTP API call to AccountService
Solution 2: Event-driven with Saga pattern

Before extraction:
accountRepository.findByAccountNumber(number);

After extraction:
accountServiceClient.getAccount(number); // HTTP call to AccountService
```

**Step 4 — API Gateway:**
Once multiple services exist, route requests through an API Gateway:
```
Client → API Gateway → auth-service
                    → account-service
                    → transaction-service
                    → notification-service
```

**The strangler fig pattern:**
Keep the monolith running while progressively strangling it by extracting
services one by one. Traffic is gradually redirected from monolith to services.

---

**Q: What is the Saga pattern and when would NairaCore need it?**

The Saga pattern manages distributed transactions across multiple microservices
where a single database ACID transaction is not possible.

**Problem scenario (if NairaCore was microservices):**
```
Transfer ₦10,000 from David (AccountService A) to Chidi (AccountService B):
Step 1: Debit David's account in AccountService A ✅
Step 2: Credit Chidi's account in AccountService B ❌ (service down)

Without Saga:
→ David loses ₦10,000
→ Chidi never receives
→ Money vanishes from system
```

**Choreography-based Saga:**
```
TransactionService publishes TRANSFER_INITIATED event
→ AccountService A subscribes, debits David, publishes DEBIT_COMPLETED
→ AccountService B subscribes, credits Chidi, publishes CREDIT_COMPLETED
→ TransactionService marks SUCCESS

If CREDIT fails:
→ AccountService B publishes CREDIT_FAILED
→ AccountService A subscribes, reverses debit (compensating transaction)
→ TransactionService marks FAILED
```

**In current NairaCore (monolith):**
`@Transactional` handles everything — no Saga needed. The Saga pattern
becomes necessary only when the transaction spans multiple independent services.

---

## Caching Strategy

---

**Q: Where would you add caching to NairaCore and why?**

Caching reduces database load by storing frequently accessed data in memory.

**What to cache:**

**1. Account balances (Redis):**
```java
@Cacheable(value = "accountBalance", key = "#accountNumber")
public BigDecimal getBalance(String accountNumber) {
    return accountRepository.findByAccountNumber(accountNumber)
            .map(Account::getBalance)
            .orElseThrow(...);
}

@CacheEvict(value = "accountBalance", key = "#account.accountNumber")
public Account saveAccount(Account account) {
    return accountRepository.save(account);
}
```
Balance is read on every transaction check but changes only when a transaction
occurs. Cache hit rate: very high.

**2. User profiles (Redis):**
```java
@Cacheable(value = "userProfile", key = "#userId")
public User findById(UUID userId) { }
```
User profiles rarely change. Every authenticated request needs the user.

**3. Exchange rates (Redis with TTL):**
```java
@Cacheable(value = "exchangeRates", key = "#currency")
// TTL: 5 minutes — rates update frequently but not every second
```

**What NOT to cache:**
- Transaction history — frequently changes, must be fresh
- Ledger entries — immutable but queries are complex
- Active session data — JWT is stateless, no session to cache

**Cache invalidation:**
```
Event-driven via RabbitMQ:
→ Transaction completed → BALANCE_UPDATED event
→ Cache invalidation consumer clears account balance cache
→ Next read fetches fresh from database
```

---

## Monitoring and Observability

---

**Q: How would you monitor NairaCore in production?**

Observability has three pillars: metrics, logs, and traces.

**Metrics (Prometheus + Grafana):**

Add Spring Boot Actuator:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

Key metrics to track:
```
Business metrics:
→ transactions_per_minute (by type: DEPOSIT, WITHDRAWAL, TRANSFER)
→ transaction_failure_rate
→ notification_delivery_rate
→ active_accounts_count

Technical metrics:
→ jvm_memory_used
→ http_request_duration_seconds (P50, P95, P99)
→ db_connection_pool_active
→ rabbitmq_queue_messages_ready (queue backlog)
```

**Grafana dashboards:**
```
Dashboard 1: Business Health
→ Transaction volume (real-time)
→ Success/failure ratio
→ Revenue (deposits - withdrawals) over time

Dashboard 2: Technical Health
→ Response times by endpoint
→ Error rates by HTTP status code
→ JVM memory and GC metrics
→ Database query times
```

**Alerting rules:**
```
Alert: transaction failure rate > 1% for 5 minutes
Alert: queue backlog > 1000 messages
Alert: response time P99 > 2 seconds
Alert: JVM heap > 80%
Alert: DB connections > 90% of pool
```

---

**Q: How would you implement distributed tracing for NairaCore?**

Distributed tracing tracks a request as it flows through multiple components,
giving you a complete picture of where time is spent.

**With Spring Boot and Micrometer Tracing:**
```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
```

Each request gets a `traceId` that is propagated through:
```
HTTP Request → traceId: "abc123"
  → JwtAuthFilter (span: jwt-validation, 2ms)
  → TransactionController (span: controller, 1ms)
  → TransactionService (span: service, 5ms)
    → AccountRepository (span: db-query, 3ms)
    → LedgerRepository (span: db-insert, 2ms)
    → RabbitMQ publish (span: mq-publish, 1ms)
```

**In logs:**
```
2026-04-21 10:00:00 [traceId=abc123, spanId=def456] Deposit successful. Reference: NRC100000
```

With `traceId` you can find all logs related to a single request across
multiple services, log files, and time periods.

---

## Real-World Scenarios

---

**Q: A customer complains they were charged twice. How would you investigate?**

**Step 1 — Check idempotency:**
```sql
SELECT * FROM transactions.transactions
WHERE initiated_by = 'customer-uuid'
AND type = 'WITHDRAWAL'
AND created_at >= '2026-04-21 10:00:00'
ORDER BY created_at;
```
If two transactions exist with different idempotency keys → genuine duplicate.
If same idempotency key → idempotency worked, only one charge.

**Step 2 — Check ledger:**
```sql
SELECT * FROM transactions.ledger_entries le
JOIN transactions.transactions t ON le.transaction_id = t.id
WHERE t.initiated_by = 'customer-uuid'
ORDER BY le.created_at;
```
The ledger shows every balance change. Balance_before and balance_after
on each entry prove what actually happened.

**Step 3 — Check account balance:**
```sql
SELECT balance FROM accounts.accounts
WHERE account_number = 'customer-account-number';
```
Compare with sum of ledger entries.

**Step 4 — Check notification logs:**
```sql
SELECT * FROM notifications.notification_logs
WHERE user_id = 'customer-uuid'
ORDER BY created_at DESC;
```
See what notifications were sent — the customer may have received two
SMS notifications triggering the complaint.

**Resolution:**
If genuine duplicate: create a reversal transaction (REVERSED status),
credit the customer, and file incident report.
If idempotency worked: show customer the single transaction record.

---

**Q: How would you handle a scenario where NairaCore needs to process
50,000 salary payments at the same time?**

This is a batch processing scenario — common in Nigerian banking when
companies pay salaries on the same day (typically 25th-27th of the month).

**Current NairaCore limitation:**
50,000 simultaneous API calls would overwhelm the single application instance
and database.

**Solution — Batch processing system:**

```
1. Employer uploads salary file (CSV) via API
   → File stored in S3/object storage

2. Batch job scheduled for processing:
   → Read salary file
   → Create pending transfer records
   → Process in chunks of 100

3. For each chunk:
   → Create all transfers with unique idempotency keys
   → Process asynchronously
   → Retry failed transfers

4. Completion notification:
   → Email to employer with success/failure summary
   → Each employee receives their individual notification
```

**Database consideration:**
50,000 concurrent balance updates would cause massive optimistic locking conflicts.
Solution: process sequentially per account using a queue:
```
Each account gets its own queue
→ No concurrent updates to the same account
→ Eliminates optimistic locking conflicts
→ Throughput limited only by number of parallel queues
```

**RabbitMQ role:**
```
Batch job publishes 50,000 transfer events to queue
→ Multiple consumers process in parallel
→ Each consumer handles one transfer at a time
→ Failed transfers automatically requeued with retry
→ Dead letter queue captures permanently failed transfers
```

---

**Q: How would you design the system to handle a bank-wide outage and recovery?**

**Prevention:**
```
Multi-AZ deployment (AWS):
→ Primary database in us-east-1a
→ Standby database in us-east-1b (synchronous replication)
→ Automatic failover in ~60 seconds

Application:
→ Multiple instances across availability zones
→ Load balancer health checks remove failed instances automatically
```

**During outage:**
```
Database failover:
→ RDS Multi-AZ promotes standby to primary automatically
→ Application reconnects via same endpoint (DNS-based failover)
→ Transactions in-flight during failover: rolled back (no partial state)
→ Clients receive error, use idempotency key to retry

RabbitMQ:
→ Messages durably stored survive restart
→ Consumers reconnect automatically via Spring AMQP retry config

Application instance failure:
→ Load balancer health check fails → instance removed from rotation
→ Auto-scaling group launches replacement instance
→ New instance starts, Flyway validates schema, begins serving traffic
```

**Recovery:**
```
1. Failed database confirmed (3 consecutive health check failures)
2. Standby promoted automatically (Multi-AZ)
3. DNS updated to new primary (60-120 seconds)
4. Application reconnects
5. RabbitMQ processes queued messages
6. Monitor for data consistency issues
7. Post-incident reconciliation run
```

**Communication:**
```
Status page updated (statuspage.io)
Customer SMS/email: "We experienced a brief outage. Your funds are safe."
Incident report filed with CBN (required for outages > 30 minutes)
```

---

**Q: Design the system for NairaCore to support offline transactions.**

This is an advanced scenario — some rural Nigerian areas have poor internet.

**Challenge:**
Transactions require real-time balance checks and database writes.
Without connectivity, we cannot guarantee funds are available.

**Solution — USSD-based offline transactions:**
```
Customer dials *123*1*amount*account# on any phone
→ Works on 2G, no internet required
→ Request goes through telco to USSD gateway
→ USSD gateway calls NairaCore API (server-side, always connected)
→ NairaCore processes transaction normally
→ Result returned to customer via USSD session
```

**Approach for agent banking (more complex):**
```
Bank agent has a POS device with local storage
→ Transactions queued locally when offline
→ Each queued transaction has a local idempotency key
→ When connectivity restored, batch sync to NairaCore
→ Idempotency prevents duplicates
→ Balance checks run at sync time (may reject some transactions)
```

**Risk:**
Offline transactions risk overdrafts — customer may transact beyond their balance.
Mitigation: set offline transaction limit (e.g. ₦5,000) and reserve that amount
when connectivity is lost.
