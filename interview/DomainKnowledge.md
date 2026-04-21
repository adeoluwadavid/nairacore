# NairaCore — Domain Knowledge Interview Guide

> This document covers business and domain-level interview questions
> related to NairaCore and the Nigerian fintech/banking industry.

---

## Table of Contents

- [Nigerian Banking Context](#nigerian-banking-context)
- [Account Management](#account-management)
- [KYC and Compliance](#kyc-and-compliance)
- [Transactions and Financial Operations](#transactions-and-financial-operations)
- [Double-Entry Bookkeeping](#double-entry-bookkeeping)
- [Idempotency in Financial Systems](#idempotency-in-financial-systems)
- [Notifications and Communication](#notifications-and-communication)
- [Security and Access Control](#security-and-access-control)
- [Architecture Decisions](#architecture-decisions)

---

## Nigerian Banking Context

---

**Q: What is the CBN and how does it affect how you build a banking system?**

The CBN (Central Bank of Nigeria) is the apex regulatory authority for
financial institutions in Nigeria. It sets the rules that govern how banks
operate, including licensing requirements, data residency, transaction limits,
KYC/AML obligations, and reporting requirements.

In NairaCore, the CBN's influence shows up in several design decisions:
- We store BVN (a CBN-mandated identifier) for every customer
- We maintain immutable audit trails via our ledger — CBN can request this
- We enforce KYC before customers can fully transact
- We keep account records indefinitely — accounts are never deleted, only deactivated
- Our notification system ensures customers are informed of every transaction,
  which is a CBN directive

In production, we would also implement transaction limits, suspicious activity
reporting, and data residency requirements (all customer data stored in Nigeria).

---

**Q: What is NIBSS and what role does it play?**

NIBSS (Nigeria Inter-Bank Settlement System) is the infrastructure that enables
financial transactions between Nigerian banks. It operates:

- **NIP (NIBSS Instant Payment)** — real-time interbank transfers
- **BVN validation** — the authoritative source for verifying BVN numbers
- **NUBAN** — Nigerian Uniform Bank Account Number standard

In NairaCore, when a customer submits their BVN, in production we would call
the NIBSS API to validate it against their database. Currently we mock this
validation — the KYC record is saved but `isVerified` remains `false` until
a real NIBSS integration is added.

For interbank transfers, NairaCore currently only handles transfers between
NairaCore accounts internally. A production system would integrate with NIBSS
NIP for transfers to other banks.

---

**Q: What is a NUBAN account number and how did you implement it?**

NUBAN (Nigerian Uniform Bank Account Number) is a 10-digit account number
standard mandated by the CBN. The format is:

```
Bank Code (3 digits) + Serial Number (7 digits) = 10 digits total
```

Each bank has a CBN-assigned 3-digit bank code. In NairaCore we use `0123`
as our mock bank code. The remaining 6 digits come from a PostgreSQL sequence:

```
0123 + 100000 = 0123100000 (first account)
0123 + 100001 = 0123100001 (second account)
```

We use a PostgreSQL sequence instead of random numbers because sequences are
atomic — even under concurrent load, two accounts can never receive the same
number. This is the same approach real Nigerian banks use internally.

---

**Q: What is the BVN and why is it important in your system?**

BVN (Bank Verification Number) is an 11-digit biometric identifier assigned
to every bank customer in Nigeria by the CBN through NIBSS. It was introduced
in 2014 to combat fraud and enable KYC across the banking system.

Key characteristics:
- One BVN per person — linked to their biometrics (fingerprint and facial)
- A single BVN can be linked to multiple bank accounts across multiple banks
- BVN cannot be transferred or duplicated

In NairaCore:
- We collect BVN during KYC submission — it is mandatory (`NOT NULL`)
- We do not enforce global BVN uniqueness in our database — NIBSS is the
  authority, not us. We verify against NIBSS (mocked in development)
- One KYC record per user covers all their accounts — matching real bank behaviour
- David's two accounts at First Bank both share his BVN — our system mirrors this

---

**Q: How does NairaCore handle the difference between a customer and a teller?**

In a real Nigerian bank:
- **Customer** — opens accounts and transacts through self-service channels
  (mobile app, internet banking)
- **Teller** — bank staff who serve customers at the branch counter. They can
  open accounts on behalf of customers and process transactions for them

NairaCore models this with role-based access:

```
CUSTOMER role:
→ Can create their own accounts
→ Can only transact on their own accounts
→ Can view only their own accounts and notifications

TELLER role:
→ Can create accounts on behalf of a customer (requires targetUserId)
→ Can transact on any account
→ Can view any account and ledger entries

ADMIN role:
→ Can create users of any role
→ Can deactivate accounts
→ Can view all data including ledger entries
```

The `targetUserId` field in account creation is the key distinction —
a teller specifies whose account they are creating, while a customer
always creates for themselves (extracted from JWT).

---

**Q: Why are accounts never deleted in NairaCore?**

This is a regulatory and audit requirement. In banking:

1. **Regulatory compliance** — CBN requires banks to retain financial records
   for a minimum of 5-7 years. Deleting an account would violate this
2. **Audit trail** — transactions linked to a deleted account would have
   orphaned references
3. **Legal protection** — in disputes, the bank needs to prove account history
4. **Fraud investigation** — closed accounts are often needed in fraud
   investigations months or years later

Instead of deletion, NairaCore uses status management:
```
ACTIVE  → normal operations
FROZEN  → temporarily restricted (fraud suspicion, court order)
CLOSED  → permanently deactivated but record preserved
```

A CLOSED account's full history remains queryable. Only the status prevents
new transactions.

---

## Account Management

---

**Q: Why can a customer only have one account per type in NairaCore?**

This mirrors the policy of most Nigerian retail banks. For example:
- GTBank allows one savings and one current account per customer
- Access Bank follows a similar model for standard retail customers

The business reasons are:
1. **Simplicity** — customers understand their accounts clearly
2. **KYC alignment** — one set of KYC documents covers all account types
3. **Fraud prevention** — limiting accounts reduces money laundering risk
4. **Operational efficiency** — bank staff can serve customers more efficiently

In NairaCore we enforce this at the service level:
```java
boolean accountExists = accountRepository
    .existsByUserIdAndAccountType(userId, accountType);
if (accountExists) {
    throw new BadRequestException("You already have a " + accountType + " account");
}
```

Premium customers (private banking) would have different rules, which could
be handled by introducing customer tiers in a future iteration.

---

**Q: What is the difference between SAVINGS, CURRENT and FIXED_DEPOSIT accounts?**

**SAVINGS Account:**
- Designed for personal saving
- Usually earns interest
- May have withdrawal limits per month
- Target customers: individuals saving money

**CURRENT Account:**
- Designed for frequent transactions (business use)
- Usually no interest
- No withdrawal limits
- Supports cheques and higher transaction volumes
- Target customers: businesses, high-volume transactors

**FIXED_DEPOSIT Account:**
- Money locked for a fixed term (e.g. 90 days, 6 months, 1 year)
- Earns higher interest than savings
- Early withdrawal penalties apply
- Target customers: investors seeking guaranteed returns

In NairaCore, all three are modelled with the same `Account` entity.
The distinction is in the `accountType` enum. In production, business
rules specific to each type (interest calculation, withdrawal limits,
maturity dates) would be implemented in dedicated service logic.

---

**Q: How does NairaCore generate account numbers and why is that approach superior?**

NairaCore uses PostgreSQL sequences for account number generation:

```sql
CREATE SEQUENCE accounts.account_number_seq
    START WITH 100000
    INCREMENT BY 1
    NO CYCLE;
```

```java
Long next = accountRepository.getNextAccountSequence();
return BANK_CODE + String.format("%06d", next);
// Result: "0123100000", "0123100001", etc.
```

This is superior to alternatives because:

**vs Random numbers:**
- Random numbers risk collision — two accounts could get the same number
- Collision checking requires a retry loop and extra database queries
- Under high load, retry loops cause performance issues

**vs Auto-increment IDs:**
- Auto-increment exposes business volume (sequential IDs reveal customer count)
- PostgreSQL sequences are more flexible (custom start, increment, format)

**vs UUID-based:**
- UUIDs are 36 characters — too long for a Nigerian bank account number
- NUBAN standard requires exactly 10 digits

**PostgreSQL sequences:**
- Atomic at the database level — concurrent requests always get unique values
- No application-level collision checking needed
- Survives application restarts
- Fast — single SQL call with no retry logic

---

## KYC and Compliance

---

**Q: What is KYC and why is it mandatory in banking?**

KYC (Know Your Customer) is a regulatory requirement that compels financial
institutions to verify the identity of their customers before providing services.
In Nigeria, the CBN mandates KYC for all bank customers.

KYC serves several purposes:
1. **AML (Anti-Money Laundering)** — prevents criminals from using banks
   to launder money
2. **Counter-terrorism financing** — blocks terrorist financing through banking
3. **Fraud prevention** — verified identity reduces fraudulent account opening
4. **Regulatory compliance** — failure to implement KYC results in CBN sanctions

In NairaCore, KYC captures:
- BVN (mandatory) — links to NIBSS biometric database
- NIN (optional) — National Identification Number
- Date of birth
- Address details
- Government ID document

The `isVerified` flag starts as `false`. In production a background job
calls the NIBSS API to verify the BVN and flips it to `true`. Unverified
accounts may have transaction limits enforced.

---

**Q: What is AML and how would NairaCore implement it?**

AML (Anti-Money Laundering) refers to regulations and procedures that
prevent criminals from disguising illegally obtained funds as legitimate income.

In NairaCore's current state, basic AML controls include:
- Mandatory KYC before full account access
- Immutable transaction ledger (audit trail)
- Transaction logging with initiator (initiatedBy field)

In a production system, additional AML controls would include:
1. **Transaction monitoring** — flag transactions above certain thresholds
   (CBN requires reporting transactions above ₦5 million)
2. **Suspicious activity reports (SARs)** — automated filing with NFIU
   (Nigerian Financial Intelligence Unit)
3. **PEP screening** — checking customers against politically exposed
   persons databases
4. **Sanctions screening** — checking against OFAC and UN sanctions lists
5. **Velocity checks** — flagging unusual transaction patterns

These would be implemented as additional rules in the TransactionService,
potentially using a dedicated compliance module with its own RabbitMQ events.

---

**Q: Why does NairaCore not enforce BVN uniqueness at the database level?**

This is a deliberate design decision based on the real-world nature of BVN.

**The argument for uniqueness constraint:**
Only one person should have a BVN, so no two users should share one.

**Why we did not enforce it:**

1. **NIBSS is the authority** — BVN validation is NIBSS's responsibility.
   Our database should not try to be a secondary NIBSS. We verify against
   NIBSS but do not try to replicate their enforcement.

2. **Testing and development** — during development and testing, teams
   often use the same test BVN numbers. A unique constraint would block this.

3. **Data correction** — if NIBSS's database has errors or a customer
   legitimately has multiple registrations, a database constraint would
   prevent legitimate corrections.

4. **Our check is sufficient** — we do check if a BVN already exists in
   our system (`existsByBvn`) and throw an error. This prevents accidental
   duplicates within NairaCore while leaving NIBSS as the authoritative check.

---

## Transactions and Financial Operations

---

**Q: Explain the transaction lifecycle in NairaCore.**

Every transaction in NairaCore goes through a defined state machine:

```
PENDING → PROCESSING → SUCCESS
                    ↘ FAILED
```

**PENDING:**
- Transaction record created in the database
- Reference number and idempotency key stored
- This happens before any balance changes
- If the system crashes here, no money has moved

**PROCESSING:**
- Balance check passed (sufficient funds)
- Balance update in progress
- Ledger entries being written

**SUCCESS:**
- Balance updated on account
- Ledger entries written
- Notification event published to RabbitMQ
- This is the final positive state

**FAILED:**
- Something went wrong during processing
- Balance unchanged (rolled back by @Transactional)
- Failure reason stored for investigation
- Idempotency key preserved — client can check the outcome

**REVERSED (future):**
- A successful transaction that was subsequently reversed
- Requires creating equal and opposite ledger entries

The state machine ensures that at any point, you know exactly what happened
to every transaction — even if the system crashed mid-processing.

---

**Q: What is idempotency and why is it critical in financial systems?**

Idempotency means that performing the same operation multiple times produces
the same result as performing it once.

**The problem it solves:**
```
Customer initiates a ₦50,000 transfer
→ Request reaches server
→ Server processes the transfer successfully
→ Network times out before response reaches client
→ Client does not know if the transfer succeeded
→ Client retries the same request
→ Without idempotency: transfer processed TWICE — ₦100,000 debited
→ With idempotency: second request detected as duplicate, original response returned
```

**How NairaCore implements it:**
1. Client generates a UUID before each transaction attempt
2. Client sends this `idempotencyKey` in the request
3. Server checks if key already exists: `existsByIdempotencyKey()`
4. If exists: return original response without processing
5. If not: process normally and store the key

```java
if (transactionRepository.existsByIdempotencyKey(request.getIdempotencyKey())) {
    return transactionRepository
        .findByIdempotencyKey(request.getIdempotencyKey())
        .map(this::mapToTransactionResponse)
        .orElseThrow();
}
```

**Key properties:**
- The idempotency key is stored with a UNIQUE constraint in the database
- The key must be generated by the CLIENT — the server cannot generate it
  (the server does not know if this is a retry or a new request)
- Keys should be unique per transaction attempt, not per user session

---

**Q: Why does NairaCore use BigDecimal for all monetary amounts?**

Floating point types (float and double) cannot represent decimal values
exactly in binary. This causes rounding errors that are catastrophic in
financial systems.

```java
double a = 0.1 + 0.2;
System.out.println(a); // prints 0.30000000000000004
```

In a banking system with millions of transactions:
```
Balance: ₦10,000.00
After 1000 small operations: ₦9,999.9999999999996  // wrong
```

BigDecimal stores exact decimal values with no rounding errors:
```java
BigDecimal a = new BigDecimal("0.1").add(new BigDecimal("0.2"));
System.out.println(a); // prints 0.3
```

In NairaCore:
- All amounts in Java code: `BigDecimal`
- All amounts in PostgreSQL: `NUMERIC(19, 4)` — exact decimal storage
- Balance comparisons use `compareTo()`, never `equals()`

```java
// WRONG - equals() considers scale
new BigDecimal("10.00").equals(new BigDecimal("10.0")) // false

// CORRECT - compareTo() considers only numeric value
new BigDecimal("10.00").compareTo(new BigDecimal("10.0")) // 0 = equal
```

This is a standard interview question for any fintech role. The answer
should always be: BigDecimal, NUMERIC in PostgreSQL, compareTo() for comparison.

---

**Q: What is optimistic locking and why did you use it in NairaCore?**

Optimistic locking is a concurrency control mechanism that assumes conflicts
are rare and detects them at write time rather than preventing them at read time.

**The problem:**
```
Two simultaneous withdrawal requests on David's account (balance: ₦10,000):
Request A reads balance: ₦10,000
Request B reads balance: ₦10,000

Request A: ₦10,000 - ₦8,000 = ₦2,000 → saves balance
Request B: ₦10,000 - ₦5,000 = ₦5,000 → saves balance (WRONG — balance is now ₦5,000 not -₦3,000)

Result: Balance should be negative but is ₦5,000 — money was created from nothing
```

**The solution — @Version:**
```java
@Version
@Column(name = "version", nullable = false)
private Long version;
```

Hibernate adds a version check to every UPDATE:
```sql
UPDATE accounts.accounts
SET balance = ?, version = version + 1
WHERE id = ? AND version = ?  -- must match current version
```

```
Request A reads balance: ₦10,000, version: 1
Request B reads balance: ₦10,000, version: 1

Request A updates: version 1 → 2, balance ₦2,000 ✅
Request B tries to update: version 1 but DB has version 2 → FAILS
→ OptimisticLockingFailureException thrown
→ Request B returns "Concurrent transaction conflict. Please retry."
→ Client retries → reads version 2, balance ₦2,000 → proceeds correctly
```

**Why optimistic vs pessimistic locking:**
- Pessimistic locking (SELECT FOR UPDATE) locks the row for the entire transaction
- Under high load, this creates bottlenecks and deadlocks
- Optimistic locking has no overhead unless a conflict actually occurs
- Conflicts are rare in practice — most transactions on different accounts

---

**Q: What is double-entry bookkeeping and why did you implement it?**

Double-entry bookkeeping is a 500-year-old accounting principle where every
financial transaction affects at least two accounts — one is debited and one
is credited — and the total always balances to zero.

**Why banks use it:**
1. **Error detection** — if the books do not balance, something is wrong
2. **Complete audit trail** — every balance change has two corresponding entries
3. **Regulatory compliance** — auditors require double-entry records
4. **Fraud detection** — any manipulation of balances leaves inconsistencies

**In NairaCore:**

Every transaction creates ledger entries:

```
DEPOSIT ₦50,000 into David's savings:
Entry 1: David's account   CREDIT  ₦50,000  (balance increases)
Entry 2: External/Cash     DEBIT   ₦50,000  (cash came from somewhere)

TRANSFER ₦10,000 from David to Chidi:
Entry 1: David's account   DEBIT   ₦10,000  (balance decreases)
Entry 2: Chidi's account   CREDIT  ₦10,000  (balance increases)

Net effect: -₦10,000 + ₦10,000 = 0 ✅ always balances
```

**The ledger serves as independent verification:**
```sql
SELECT balance_after
FROM transactions.ledger_entries
WHERE account_number = '0123100000'
ORDER BY created_at DESC
LIMIT 1;
```

This gives the current balance from the ledger — independent of the
balance field on the account. If they ever differ, it signals a bug or fraud.

---

## Double-Entry Bookkeeping

---

**Q: How would you detect if there is a discrepancy between the account balance
and the ledger?**

You would run a reconciliation query:

```sql
SELECT
    a.account_number,
    a.balance AS account_balance,
    le.latest_ledger_balance,
    a.balance - le.latest_ledger_balance AS discrepancy
FROM accounts.accounts a
LEFT JOIN (
    SELECT DISTINCT ON (account_number)
        account_number,
        balance_after AS latest_ledger_balance
    FROM transactions.ledger_entries
    ORDER BY account_number, created_at DESC
) le ON a.account_number = le.account_number
WHERE a.balance != le.latest_ledger_balance;
```

Any rows returned indicate a discrepancy. In production, this reconciliation
would run as a scheduled job (daily or hourly) and alert the operations team.

This is called a **reconciliation process** — all banks run it regularly as
a control mechanism.

---

**Q: What would happen if a transfer succeeds for the source account
but fails for the destination account?**

Without `@Transactional`, this would create a serious problem:
- David's account loses ₦10,000
- Chidi's account never receives it
- ₦10,000 disappears from the system

In NairaCore, `@Transactional` prevents this:

```java
@Transactional
public TransactionResponse transfer(...) {
    // If ANY of these fail, ALL are rolled back:
    sourceAccount.setBalance(sourceBalanceAfter);
    accountRepository.save(sourceAccount);     // step 1
    createLedgerEntry(DEBIT);                  // step 2
    destinationAccount.setBalance(destAfter);
    accountRepository.save(destinationAccount); // step 3
    createLedgerEntry(CREDIT);                  // step 4
    transaction.setStatus(SUCCESS);             // step 5
}
```

If step 3 fails (e.g. destination account locked by another transaction),
the entire database transaction rolls back — step 1 and 2 are undone.
Both accounts return to their original balances.

The transaction record is marked FAILED with the failure reason stored.
No money is lost. The customer can retry.

---

## Idempotency in Financial Systems

---

**Q: Who should generate the idempotency key — client or server?**

The **client** must generate the idempotency key.

Here is why:

The server cannot generate the key because by the time the server receives
the request, it has no way to know if this is a new request or a retry.

The client knows because:
1. Client generates UUID before sending: `idempotencyKey = UUID.randomUUID()`
2. Client sends request with this key
3. Network times out — client does not know if request was received
4. Client retries with the SAME key
5. Server detects the duplicate and returns the original response

If the server generated the key:
1. Client sends request (no key)
2. Server generates key, processes transaction, returns key + response
3. Network times out — response never reaches client
4. Client retries (no key again)
5. Server generates a NEW key — processes as new transaction
6. Result: duplicate transaction ❌

**In practice:** Mobile banking apps generate a UUID when the user taps
"Send Money." If the request fails, the app retries with the same UUID.

---

**Q: What happens if the same idempotency key is used for a different transaction?**

This is a client bug and should be handled carefully.

In NairaCore, when an idempotency key is found:
```java
if (transactionRepository.existsByIdempotencyKey(request.getIdempotencyKey())) {
    return transactionRepository
        .findByIdempotencyKey(request.getIdempotencyKey())
        .map(this::mapToTransactionResponse)
        .orElseThrow();
}
```

We return the ORIGINAL transaction response, regardless of what the new
request contains. This means:

```
Original: deposit ₦10,000 to account A (key: "abc-123")
Retry with same key: deposit ₦50,000 to account B (key: "abc-123")
→ Returns the original ₦10,000 deposit response
→ The ₦50,000 deposit is NOT processed
```

Production systems go further — they also check that the request parameters
match the original (same amount, same account). If they do not match, they
return a 409 Conflict error indicating idempotency key reuse with different
parameters.

---

## Notifications and Communication

---

**Q: Why did you use RabbitMQ for notifications instead of sending emails directly
from the transaction service?**

Direct email sending from TransactionService creates two problems:

**Problem 1 — Coupling:**
```
Transaction processing + email sending = one atomic operation
If email service is slow → transaction response is slow
If email service is down → transaction fails
```

**Problem 2 — Reliability:**
A financial transaction is a database operation.
An email is a network call to an external service.
These have completely different failure modes and should not be coupled.

**With RabbitMQ:**
```
Transaction processing → SUCCESS (fast database operation)
→ Publish event to queue (2ms)
→ Return response to customer immediately

Later (milliseconds or seconds):
→ NotificationConsumer picks up event
→ Sends email via Mailpit/SendGrid
→ Saves notification log
```

The transaction succeeds or fails on its own merits. A failed notification
never causes a transaction rollback. Messages are durably stored — even if
the notification service goes down, messages queue up and are processed when
the service recovers.

---

**Q: What happens to notifications if RabbitMQ goes down?**

This depends on queue durability configuration.

In NairaCore:
```java
new Queue(NOTIFICATION_QUEUE, true); // durable = true
```

With `durable = true`:
- Queue definition survives RabbitMQ restart
- Messages in the queue survive restart (if also marked as persistent)

**Sequence of events:**
1. RabbitMQ goes down
2. TransactionService attempts to publish — connection fails
3. In our implementation, the publisher catches the exception and logs it
4. The notification is lost for that transaction

**Production improvement:**
- Use Spring AMQP's retry mechanism with exponential backoff
- Use RabbitMQ publisher confirms to know if message was received
- Implement a fallback — if RabbitMQ is unavailable, save notification
  to a `pending_notifications` table and process when RabbitMQ recovers

**Dead Letter Queue:**
When a consumer fails to process a message after max retries, it goes to the
DLQ (`nairacore.notifications.dlq`). Operations team can inspect and reprocess
these messages manually.

---

**Q: What is the difference between Mailpit and a production email service?**

| | Mailpit | Production (SendGrid/SES) |
|---|---|---|
| **Purpose** | Local development testing | Real email delivery |
| **Emails sent** | Caught locally, not delivered | Actually delivered to inbox |
| **Setup** | Zero configuration | API key, domain verification, SPF/DKIM |
| **Cost** | Free | Pay per email |
| **Features** | Web UI to inspect emails | Delivery tracking, analytics |
| **Nigerian context** | Development only | Use Africa's Talking for SMS |

In NairaCore, switching from Mailpit to production is a configuration change:
```yaml
# Development (Mailpit)
spring.mail.host: localhost
spring.mail.port: 1025

# Production (SendGrid)
spring.mail.host: smtp.sendgrid.net
spring.mail.port: 587
spring.mail.username: apikey
spring.mail.password: ${SENDGRID_API_KEY}
```

No code changes required — only configuration. This is the benefit of using
Spring's `JavaMailSender` abstraction.

---

## Security and Access Control

---

**Q: Explain how JWT authentication works in NairaCore.**

JWT (JSON Web Token) authentication in NairaCore works as follows:

**Login flow:**
1. Client sends email + password to `/api/v1/auth/login`
2. Server verifies credentials against database
3. Server generates a signed JWT (HS256 algorithm)
4. JWT contains claims: `userId`, `email` (subject), `role`
5. Server returns access token (15 min) + refresh token (30 days)
6. Refresh token saved to `auth.refresh_tokens` table

**Request authentication flow:**
```
Client sends: Authorization: Bearer eyJhbGci...

JwtAuthFilter runs:
1. Extracts token from Authorization header
2. Extracts email from JWT claims
3. Loads UserPrincipal from database
4. Validates token signature and expiry
5. Sets authentication in SecurityContextHolder

Controller runs:
→ @PreAuthorize checks role from SecurityContextHolder
→ extractUserId() gets userId from UserPrincipal
→ No database call needed to identify the user
```

**Why stateless:**
The server stores no session. Every request is independently authenticated
via the JWT signature. This scales horizontally — any server instance can
validate any JWT without shared session storage.

---

**Q: Why did you implement refresh token rotation?**

Refresh token rotation means each refresh operation:
1. Accepts the current refresh token
2. **Deletes** the current refresh token
3. Issues a brand new refresh token
4. Issues a new access token

**Why this matters:**

Without rotation:
```
Attacker steals refresh token
→ Uses it indefinitely to generate access tokens
→ User never knows until they explicitly logout
```

With rotation:
```
Attacker steals refresh token at 10:00
→ User's app refreshes at 10:14 (before access token expires)
→ User's app uses the refresh token first → new tokens issued
→ Old refresh token DELETED from database
→ Attacker tries to use stolen token at 10:15 → 401 Unauthorized
→ Attacker's access window: 15 minutes maximum
```

Additionally, when an already-used refresh token is presented (indicating
potential theft), the system can invalidate ALL sessions for that user as
a security measure.

---

**Q: What is the difference between authentication and authorization?**

**Authentication** — Who are you?
```
"I am David Adewole, here is my JWT token"
→ System verifies the token signature is valid
→ System confirms the user exists and is active
→ Authentication successful
```

**Authorization** — What are you allowed to do?
```
David is authenticated as CUSTOMER
David tries to view ledger entries
→ @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
→ CUSTOMER does not have TELLER or ADMIN role
→ 403 Forbidden
→ Authorization failed
```

In NairaCore:
- **JwtAuthFilter** handles authentication — validates the token
- **@PreAuthorize** handles coarse authorization — checks role
- **Service layer** handles fine authorization — checks resource ownership

```java
// Fine authorization in AccountService
boolean isOwner = account.getUserId().equals(requestingUserId);
boolean isPrivileged = role.equals("ROLE_ADMIN") || role.equals("ROLE_TELLER");
if (!isOwner && !isPrivileged) {
    throw new UnauthorizedException("...");
}
```

---

**Q: Why do you return the same error message for wrong email and wrong password?**

```java
// Wrong email
throw new UnauthorizedException("Invalid email or password");

// Wrong password
throw new UnauthorizedException("Invalid email or password");
```

This is intentional security practice called **error message ambiguity**.

If we returned different messages:
```
"Email not found" → attacker learns valid vs invalid emails
"Wrong password"  → attacker knows the email exists, can brute force password
```

With the same message:
```
"Invalid email or password" → attacker cannot determine which field was wrong
```

This prevents user enumeration attacks — a type of attack where an attacker
systematically tests email addresses to build a list of valid accounts.

Nigerian fintech apps handle millions of users. Knowing which emails are
registered is valuable to attackers for phishing, credential stuffing, etc.

---

## Architecture Decisions

---

**Q: Why did you build NairaCore as a modular monolith instead of microservices?**

Several reasons informed this decision:

**1. Development speed:**
A finished modular monolith beats an incomplete microservices system every time.
Microservices multiply complexity: service discovery, distributed tracing,
inter-service authentication, network latency, eventual consistency.

**2. Team size:**
Microservices make sense when multiple teams own different services.
For a single developer or small team, a monolith is more productive.

**3. Domain clarity:**
We did not yet have enough production experience with NairaCore to know
exactly where the service boundaries should be. Monolith first, extract later
when boundaries are proven under real load.

**4. Nigerian fintech reality:**
Most mid-sized Nigerian fintechs (Moniepoint at early stage, PiggyVest,
Cowrywise) started with modular monoliths. Microservices are more common
at Flutterwave/Paystack scale.

**5. Extractability:**
The modular monolith is designed for future extraction:
- No cross-schema foreign keys — schemas are independent
- No direct repository access across modules
- RabbitMQ already used for async communication
- Switching from in-process calls to HTTP only requires changing the
  communication layer, not the business logic

---

**Q: Why did you choose a single database with multiple schemas instead of
multiple databases?**

**Option A — Single database, multiple schemas (NairaCore's choice):**
```
nairacore (database)
├── auth schema
├── accounts schema
├── transactions schema
└── notifications schema
```

**Option B — Multiple databases:**
```
nairacore_auth
nairacore_accounts
nairacore_transactions
nairacore_notifications
```

**Why we chose Option A:**

1. **Operational simplicity** — one database to manage, backup, monitor
2. **Nigerian fintech standard** — most mid-sized fintechs use this pattern
3. **Portfolio project** — easier to run locally without Docker for the DB layer
4. **Schema isolation** — each module still owns its schema completely
5. **Migration path** — adding schema per service makes future DB separation easy

**The key boundary rule:**
No cross-schema foreign keys. The `accounts.accounts` table has a `user_id` column
referencing the auth module's users, but with no `FOREIGN KEY` constraint.
The relationship is maintained at the application level. This means
extracting to separate databases requires zero SQL changes.

---

**Q: How would you handle a situation where a customer's transaction
is processed but they never receive a notification?**

This is a multi-step investigation:

**Step 1 — Check notification logs:**
```sql
SELECT * FROM notifications.notification_logs
WHERE transaction_reference = 'NRC100000';
```

**Step 2 — Check status:**
- `SENT` → email was delivered to Mailpit/provider. Check customer's spam folder.
- `FAILED` → notification failed with a reason. Check `failure_reason` column.
- No record → event never reached the consumer.

**Step 3 — Check RabbitMQ:**
- Check Dead Letter Queue (`nairacore.notifications.dlq`) for stuck messages
- Check RabbitMQ management UI for any consumer errors

**Step 4 — Manual reprocess:**
If needed, create a new notification log entry and trigger sending manually.

**Preventive measures:**
- Implement retry logic in NotificationConsumer (max 3 retries with backoff)
- Set up DLQ monitoring alerts
- Run daily reconciliation comparing transactions to notification logs

---

**Q: What would change in NairaCore if you needed to support 1 million
transactions per day?**

Current NairaCore handles transactions synchronously with a single app instance.
At 1M transactions/day (~12 transactions/second), changes needed:

**Database:**
- Add read replicas — separate read queries (history, balance) from writes
- Implement connection pooling (HikariCP — already included with Spring Boot)
- Add more indexes on frequently queried columns
- Consider partitioning the ledger_entries table by date

**Application:**
- Deploy multiple instances behind a load balancer
- Optimistic locking already handles concurrent updates correctly
- Add caching (Redis) for frequently read data (account balances)

**Messaging:**
- Increase RabbitMQ consumer concurrency for notifications
- Consider batching notification events

**Architecture:**
- Extract TransactionService to its own microservice if it becomes the bottleneck
- Consider event sourcing for the ledger at very high volumes

At true scale (Flutterwave/Paystack level — 10M+ transactions/day),
the architecture would be significantly more complex with dedicated
fraud detection pipelines, Kafka for event streaming, and multi-region deployment.
