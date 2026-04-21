# NairaCore — Technical Interview Guide

> This document covers core Java and Spring Boot technical interview questions
> directly related to what was built in NairaCore.

---

## Table of Contents

- [Core Java](#core-java)
- [Spring Boot and Spring Framework](#spring-boot-and-spring-framework)
- [Spring Security and JWT](#spring-security-and-jwt)
- [JPA and Hibernate](#jpa-and-hibernate)
- [Database and Flyway](#database-and-flyway)
- [RabbitMQ and Messaging](#rabbitmq-and-messaging)
- [REST API Design](#rest-api-design)
- [Exception Handling](#exception-handling)
- [Testing](#testing)
- [Docker and Infrastructure](#docker-and-infrastructure)

---

## Core Java

---

**Q: Why did you use `Optional` in your repository methods instead of returning
nullable objects?**

`Optional<T>` makes the possible absence of a value explicit at the type level.
This forces the caller to handle the empty case rather than risking a NullPointerException.

```java
// Without Optional — dangerous
User user = userRepository.findByEmail(email); // could be null
String name = user.getFirstName(); // NullPointerException if user is null

// With Optional — safe and explicit
Optional<User> user = userRepository.findByEmail(email);
User u = user.orElseThrow(() -> new UnauthorizedException("User not found"));
```

In a financial system, a NullPointerException on a user lookup during a
transaction is catastrophic. `Optional` forces us to decide what to do
when the user does not exist — throw a specific exception, return a default,
or take another action. This makes the code more robust and more readable.

---

**Q: Explain how you used streams in NairaCore and why.**

Streams provide a declarative, functional way to process collections. In NairaCore
we use them extensively for mapping entities to DTOs:

```java
public List<AccountSummaryResponse> getMyAccounts(UUID userId) {
    return accountRepository.findAllByUserId(userId)
            .stream()
            .map(this::mapToAccountSummaryResponse)
            .collect(Collectors.toList());
}
```

This is equivalent to:
```java
List<AccountSummaryResponse> responses = new ArrayList<>();
for (Account account : accountRepository.findAllByUserId(userId)) {
    responses.add(mapToAccountSummaryResponse(account));
}
return responses;
```

The stream version is more concise, readable, and composable. You can add
filters, sorting, and other operations cleanly:

```java
return accountRepository.findAllByUserId(userId)
        .stream()
        .filter(a -> a.getStatus() == AccountStatus.ACTIVE) // only active
        .sorted(Comparator.comparing(Account::getCreatedAt).reversed()) // newest first
        .map(this::mapToAccountSummaryResponse)
        .collect(Collectors.toList());
```

---

**Q: What is the switch expression you used in NotificationConsumer and how
does it differ from a traditional switch statement?**

Java 14 introduced switch expressions as a more concise and safe alternative
to switch statements.

**Traditional switch statement:**
```java
String subject;
switch (event.getTransactionType()) {
    case DEPOSIT:
        subject = "Deposit Successful";
        break;
    case WITHDRAWAL:
        subject = "Withdrawal Successful";
        break;
    case TRANSFER:
        subject = "Transfer Successful";
        break;
    default:
        throw new IllegalStateException();
}
```

**Switch expression (used in NairaCore):**
```java
String subject = switch (event.getTransactionType()) {
    case DEPOSIT -> "Deposit Successful — " + event.getTransactionReference();
    case WITHDRAWAL -> "Withdrawal Successful — " + event.getTransactionReference();
    case TRANSFER -> "Transfer Successful — " + event.getTransactionReference();
};
```

Key differences:
1. **Expression, not statement** — returns a value directly
2. **No fall-through** — the `->` arrow prevents accidental fall-through bugs
3. **Exhaustiveness** — compiler enforces all enum cases are handled
4. **Concise** — no `break` statements needed

Since NairaCore runs on Java 17, switch expressions are stable and preferred.

---

**Q: Explain the `@RequiredArgsConstructor` annotation and why you used it
instead of `@Autowired`.**

`@RequiredArgsConstructor` is a Lombok annotation that generates a constructor
for all `final` fields in the class.

```java
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    // Lombok generates:
    // public AuthService(UserRepository u, RefreshTokenRepository r,
    //                    JwtUtil j, PasswordEncoder p) { ... }
}
```

**Why constructor injection over `@Autowired` field injection:**

1. **Testability** — constructor injection makes dependencies explicit.
   In unit tests you can create the service with mock dependencies directly:
   ```java
   AuthService service = new AuthService(mockRepo, mockTokenRepo, mockJwt, mockEncoder);
   ```
   With `@Autowired` field injection you need Spring's context or reflection to inject.

2. **Immutability** — `final` fields cannot be changed after construction.
   This prevents accidental reassignment.

3. **Spring best practice** — Spring's own documentation recommends constructor
   injection since Spring 4.3.

4. **Null safety** — if a dependency is missing, the application fails at startup
   with a clear error, not at runtime with a NullPointerException.

---

**Q: What is the `@Builder` annotation and how did you use it?**

`@Builder` is a Lombok annotation that implements the Builder design pattern,
allowing you to construct objects with named parameters in any order.

```java
@Builder
public class User {
    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private Role role;
    // ...
}

// Usage:
User user = User.builder()
        .firstName("David")
        .lastName("Adewole")
        .email("david@nairacore.com")
        .role(Role.CUSTOMER)
        .build();
```

**Why Builder is ideal for financial entities:**

1. **Many optional fields** — entities like `Transaction` have many nullable
   fields (description, failureReason, sourceAccountNumber). Builder makes
   it clear which fields are set.

2. **Readability** — named parameters make intent clear.
   `new Transaction("NRC100", key, DEPOSIT, PENDING, amount...)` — which argument
   is which? Builder eliminates this ambiguity.

3. **Immutability** — you can combine `@Builder` with immutable fields.

4. **Test data** — in tests, Builder makes creating test fixtures easy and readable.

---

**Q: Explain the difference between `@Data`, `@Getter`, `@Setter` in Lombok.**

`@Data` is a composite annotation that includes:
- `@Getter` — generates getters for all fields
- `@Setter` — generates setters for all fields
- `@ToString` — generates toString()
- `@EqualsAndHashCode` — generates equals() and hashCode()
- `@RequiredArgsConstructor` — generates constructor for final fields

In NairaCore:
- Entities use `@Data` — they need getters, setters, equals, and hashCode
- DTOs use `@Data` — same reasons
- Response DTOs also use `@Builder` — for convenient construction

**Warning about `@EqualsAndHashCode` on JPA entities:**
Using `@Data` on JPA entities can cause issues because `@EqualsAndHashCode`
uses all fields by default, including the generated `id`. Two entities with
the same data but different IDs will not be equal. For JPA entities, it is
better to override `equals()` and `hashCode()` manually using only the `id` field,
or use `@EqualsAndHashCode(of = "id")`.

---

**Q: What is the UUID type and why did you use it for IDs instead of Long?**

UUID (Universally Unique Identifier) is a 128-bit number that is virtually
guaranteed to be globally unique. Format: `550e8400-e29b-41d4-a716-446655440000`

**Why UUID over Long (auto-increment):**

1. **Security** — sequential IDs expose business information:
   ```
   GET /api/v1/users/1    → first user
   GET /api/v1/users/5000 → 5000th user
   ```
   This reveals your customer volume to competitors or attackers.
   UUID IDs are opaque: `74a3aed3-4ae5-43b7-b050-17af0ae81b73`

2. **Enumeration prevention** — attackers cannot guess valid IDs by incrementing

3. **Distributed systems** — UUIDs can be generated by any service without
   coordination. Sequential IDs require a central database sequence.

4. **Merging** — when merging data from multiple sources, UUIDs do not conflict.
   Sequential IDs from different sources would collide.

In NairaCore, UUIDs are generated by PostgreSQL:
```sql
id UUID PRIMARY KEY DEFAULT gen_random_uuid()
```

And by JPA:
```java
@GeneratedValue(strategy = GenerationType.UUID)
private UUID id;
```

---

## Spring Boot and Spring Framework

---

**Q: Explain dependency injection and how Spring implements it in NairaCore.**

Dependency Injection (DI) is a design pattern where an object's dependencies
are provided (injected) by an external source rather than the object creating
them itself.

**Without DI:**
```java
public class AuthService {
    private UserRepository userRepository = new UserRepository(); // tightly coupled
}
```

**With DI (Spring):**
```java
@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository; // injected by Spring
}
```

Spring's IoC (Inversion of Control) container manages object creation and wiring:

1. On startup, Spring scans for `@Component`, `@Service`, `@Repository`, `@Controller`
2. Creates instances (beans) of all annotated classes
3. Injects dependencies through constructors
4. Manages bean lifecycle

In NairaCore this creates a clean dependency graph:
```
AuthController → AuthService → UserRepository
                             → RefreshTokenRepository
                             → JwtUtil
                             → PasswordEncoder (from SecurityConfig @Bean)
```

Each layer depends on abstractions (interfaces), not implementations.
This makes testing easy — in unit tests, we replace real beans with mocks.

---

**Q: What is the difference between `@Component`, `@Service`, `@Repository`,
and `@Controller`?**

All four are specializations of `@Component` — they all register a class as
a Spring bean. The difference is semantic and functional:

`@Component` — Generic bean. Use when none of the others fit.

`@Service` — Business logic layer.
```java
@Service
public class AuthService { }  // contains login, register logic
```

`@Repository` — Data access layer. Additionally enables Spring's exception
translation — converts database-specific exceptions (like `JdbcException`)
to Spring's `DataAccessException` hierarchy.
```java
@Repository
public interface UserRepository extends JpaRepository<User, UUID> { }
```

`@Controller` — Web layer. Handles HTTP requests. Returns view names.

`@RestController` — `@Controller` + `@ResponseBody`. Automatically serializes
return values to JSON. Used in all NairaCore controllers.

---

**Q: What does `@Transactional` do and why is it on service methods, not
controllers or repositories?**

`@Transactional` wraps a method in a database transaction. If the method
completes successfully, the transaction commits. If any exception is thrown,
the transaction rolls back — all database changes within the method are undone.

```java
@Transactional
public TransactionResponse transfer(TransferRequest request, ...) {
    // If anything here throws an exception:
    sourceAccount.setBalance(newSourceBalance);
    accountRepository.save(sourceAccount);    // ← rolled back
    createLedgerEntry(DEBIT);                // ← rolled back
    destinationAccount.setBalance(newDestBalance);
    accountRepository.save(destinationAccount); // ← rolled back
    // All changes undone — no partial state
}
```

**Why on services, not controllers:**
- Controllers handle HTTP — they should not know about database transactions
- `@Transactional` on controller methods is an anti-pattern

**Why not on repositories:**
- Repositories have individual method transactions by default
- `@Transactional` on service methods creates a single transaction spanning
  multiple repository calls — this is the point

**Why not on all methods:**
Read-only methods (`getAccountByNumber`) don't need transactions.
Only methods that write to the database benefit from transactional guarantees.
For read-only methods, you can use `@Transactional(readOnly = true)` for
a small performance optimization.

---

**Q: What is `@PreAuthorize` and how does it differ from configuring
security in `SecurityConfig`?**

Both provide authorization but at different levels:

**`SecurityConfig` — URL-level authorization:**
```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/v1/auth/**").permitAll()
    .anyRequest().authenticated()
)
```
This is coarse-grained — "any authenticated user can access this URL."
It does not know which specific user or their role.

**`@PreAuthorize` — Method-level authorization:**
```java
@GetMapping("/ledger/{accountNumber}")
@PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
public ResponseEntity<?> getLedgerEntries(...) { }
```
This is fine-grained — only TELLER or ADMIN can access this specific endpoint.

**In NairaCore we use both:**
- `SecurityConfig` — ensures all endpoints require authentication (except public ones)
- `@PreAuthorize` — enforces role requirements on specific endpoints

`@PreAuthorize` requires `@EnableMethodSecurity` on the security config class:
```java
@EnableMethodSecurity
public class SecurityConfig { }
```

**SpEL expressions in @PreAuthorize:**
```java
@PreAuthorize("hasRole('ADMIN')")              // single role
@PreAuthorize("hasAnyRole('ADMIN', 'TELLER')") // multiple roles
@PreAuthorize("isAuthenticated()")             // any authenticated user
@PreAuthorize("#userId == authentication.principal.userId") // ownership check
```

---

**Q: What is the `UserPrincipal` class you created and why was it necessary?**

Spring Security's default `UserDetails` interface only stores username (email)
and password. It has no field for `userId` (UUID).

Without `UserPrincipal`, every controller method that needs the current user's
UUID would have to make a database call:

```java
// Without UserPrincipal — requires DB call every request
String email = authentication.getName();
User user = userRepository.findByEmail(email); // extra DB query
UUID userId = user.getId();
```

With `UserPrincipal`, the UUID is stored directly in the security context:

```java
public class UserPrincipal implements UserDetails {
    private final UUID userId;
    private final String email;
    private final String password;
    private final Collection<? extends GrantedAuthority> authorities;
}
```

In `UserDetailsServiceImpl`, we populate it:
```java
return new UserPrincipal(
    user.getId(),    // stored in security context
    user.getEmail(),
    user.getPasswordHash(),
    List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
);
```

In controllers, we extract it efficiently:
```java
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
UUID userId = principal.getUserId(); // no DB call needed
```

This saves a database query on every authenticated request — significant
at scale.

---

## Spring Security and JWT

---

**Q: Explain the Spring Security filter chain in NairaCore.**

Spring Security processes every HTTP request through a chain of filters.
Each filter performs a specific security check.

In NairaCore the relevant filters are:

```
HTTP Request
↓
DisableEncodeUrlFilter          — prevents session IDs in URLs
↓
SecurityContextHolderFilter     — manages security context lifecycle
↓
LogoutFilter                    — handles logout requests
↓
JwtAuthFilter (custom)          — validates JWT, sets authentication
↓
UsernamePasswordAuthenticationFilter — handles form login (bypassed in our case)
↓
AuthorizationFilter             — checks @PreAuthorize and URL rules
↓
Your Controller
```

**JwtAuthFilter is the critical custom filter:**
```java
@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(request, response, filterChain) {
        // 1. Extract token from Authorization header
        // 2. Validate token
        // 3. Set authentication in SecurityContextHolder
        // 4. Call filterChain.doFilter() to continue
    }
}
```

`OncePerRequestFilter` guarantees the filter runs exactly once per request,
even if the request is forwarded internally.

---

**Q: How does the JWT token contain user information without a database call?**

A JWT has three parts: `header.payload.signature`

The payload (claims) contains the user's information:
```json
{
  "sub": "david@nairacore.com",
  "role": "CUSTOMER",
  "userId": "74a3aed3-4ae5-43b7-b050-17af0ae81b73",
  "iat": 1776117799,
  "exp": 1776118699
}
```

When the server receives a request:
1. `JwtAuthFilter` extracts the token
2. Verifies the signature using the secret key (cryptographic check — no DB needed)
3. Extracts `email`, `role`, `userId` from the payload
4. Creates `UserPrincipal` with these values
5. Sets it in `SecurityContextHolder`

Controllers then access the userId directly from the security context —
no database call:
```java
UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
UUID userId = principal.getUserId(); // from JWT, not DB
```

**The security guarantee:**
The signature ensures the payload has not been tampered with. If someone
changes `"role": "CUSTOMER"` to `"role": "ADMIN"` in the token, the
signature verification fails and the request is rejected.

---

**Q: What algorithm does NairaCore use to sign JWTs and why?**

NairaCore uses **HS256 (HMAC-SHA256)** — a symmetric signing algorithm.

```java
.signWith(getSigningKey(), SignatureAlgorithm.HS256)
```

**How it works:**
1. Server uses a secret key to sign the JWT: `signature = HMAC-SHA256(header.payload, secret)`
2. Client receives the signed JWT
3. On next request, server re-computes the signature and compares
4. If they match, the token is valid and unmodified

**Why HS256:**
- Simple — only one key needed (symmetric)
- Fast — HMAC computation is lightweight
- Sufficient for most APIs where the same server signs and verifies

**Alternative — RS256 (asymmetric):**
- Uses a private key to sign, public key to verify
- Better for distributed systems where multiple services need to verify tokens
- The signing service keeps the private key; other services only get the public key
- More complex to manage

For NairaCore (single application), HS256 is the correct choice.

**Secret key requirement:**
HS256 requires a key of at least 256 bits (32 bytes). NairaCore uses a
64-character hex string which provides exactly 256 bits.

---

## JPA and Hibernate

---

**Q: What are JPA lifecycle hooks and how did you use them in NairaCore?**

JPA lifecycle hooks are methods annotated with special annotations that
Hibernate calls automatically at specific points in an entity's lifecycle.

In NairaCore, `@PrePersist` and `@PreUpdate` are used:

```java
@PrePersist
protected void onCreate() {
    createdAt = LocalDateTime.now();
    updatedAt = LocalDateTime.now();
    if (role == null) role = Role.CUSTOMER;
    isActive = true;
}

@PreUpdate
protected void onUpdate() {
    updatedAt = LocalDateTime.now();
}
```

**`@PrePersist`** — fires just before the entity is first inserted into the database.
Used to set default values that should not require the caller to set them manually.

**`@PreUpdate`** — fires just before the entity is updated in the database.
Used to automatically update `updatedAt` without requiring service code to set it.

**Other lifecycle hooks:**
- `@PostPersist` — after insert
- `@PreRemove` — before delete
- `@PostRemove` — after delete
- `@PostLoad` — after entity is loaded from database

**Why this matters for financial systems:**
`createdAt` and `updatedAt` are set automatically — no service code can
accidentally omit them. This guarantees every record has accurate timestamps
for audit purposes.

---

**Q: Explain `FetchType.LAZY` vs `FetchType.EAGER` and your choice in NairaCore.**

**EAGER loading:**
When you load an entity, Hibernate immediately loads all related entities too.

```java
@ManyToOne(fetch = FetchType.EAGER)
private User user;
// Loading RefreshToken immediately loads User too
// SELECT * FROM refresh_tokens WHERE id = ?
// SELECT * FROM auth.users WHERE id = ?  ← automatic
```

**LAZY loading:**
When you load an entity, related entities are loaded only when accessed.

```java
@ManyToOne(fetch = FetchType.LAZY)
private User user;
// Loading RefreshToken does NOT load User
// SELECT * FROM refresh_tokens WHERE id = ?
// user.getEmail() → THEN loads user:
// SELECT * FROM auth.users WHERE id = ?
```

**In NairaCore, all associations use LAZY:**
```java
// RefreshToken → User
@ManyToOne(fetch = FetchType.LAZY)
private User user;

// LedgerEntry → Transaction
@ManyToOne(fetch = FetchType.LAZY)
private Transaction transaction;
```

**Why LAZY:**
Most operations do not need the related entity. When validating a refresh token,
we only need its `expiresAt` and `token` fields — not the full User object.
LAZY ensures we do not pay the cost of loading User for every token validation.

**The N+1 problem:**
LAZY loading can cause performance issues if you load a list of entities
and then access the related entity in a loop:
```java
List<RefreshToken> tokens = repo.findAll(); // 1 query
for (RefreshToken t : tokens) {
    t.getUser().getEmail(); // N queries — one per token
}
```
Solution: use JOIN FETCH in JPQL for bulk operations.

---

**Q: What is the `@Version` annotation and how does it implement optimistic locking?**

`@Version` tells Hibernate to use a version column for optimistic locking:

```java
@Version
@Column(name = "version", nullable = false)
private Long version;
```

**How it works:**

1. Every time an Account is read, the `version` value is included
2. When saving, Hibernate adds a version check to the UPDATE:
```sql
UPDATE accounts.accounts
SET balance = 50000, version = 2
WHERE id = 'uuid' AND version = 1  -- must match what we read
```
3. If another transaction updated the row first, `version` is now 2 in the DB
4. Our UPDATE finds 0 rows affected (version mismatch)
5. Hibernate throws `OptimisticLockingFailureException`

**In NairaCore we handle it explicitly:**
```java
} catch (OptimisticLockingFailureException e) {
    transaction.setStatus(TransactionStatus.FAILED);
    transaction.setFailureReason("Concurrent transaction conflict. Please retry.");
    transactionRepository.save(transaction);
    throw new BadRequestException("Concurrent transaction conflict. Please retry.");
}
```

This ensures the client receives a meaningful error message rather than
a generic 500 error.

---

**Q: Why does NairaCore use `ddl-auto: validate` instead of `create` or `update`?**

`ddl-auto` controls what Hibernate does with the database schema on startup:

| Value | Behaviour |
|---|---|
| `create` | Drops and recreates all tables on every startup |
| `create-drop` | Creates on startup, drops on shutdown |
| `update` | Adds missing columns/tables, never deletes |
| `validate` | Only checks schema matches entities — fails if mismatch |
| `none` | Does nothing |

**Why `validate` in NairaCore:**

1. **Flyway owns the schema** — Flyway creates and manages all tables via
   migration scripts. Hibernate's job is only to map entities to existing tables.

2. **Safety** — `update` can silently alter or remove columns in production.
   In a financial system, unexpected schema changes are catastrophic.

3. **Auditability** — every schema change is a versioned SQL file in version control.
   With `update`, changes happen automatically and invisibly.

4. **`validate` as a safety net** — if a developer adds a field to an entity
   but forgets to write a migration, the app fails at startup rather than
   silently operating with a mismatched schema.

---

## Database and Flyway

---

**Q: What is Flyway and why did you use it instead of letting Hibernate
manage the schema?**

Flyway is a database migration tool that manages schema changes through
versioned SQL scripts.

**How it works:**
1. SQL scripts are placed in `src/main/resources/db/migration/`
2. Named with version prefix: `V1__create_schemas.sql`, `V2__create_auth_tables.sql`
3. Flyway tracks which scripts have run in `flyway_schema_history` table
4. On startup, Flyway runs any scripts not yet applied

**Why Flyway over Hibernate DDL:**

1. **Version control** — every schema change is a `.sql` file committed to Git.
   You can see exactly when and why a column was added.

2. **Rollback capability** — you can write down-migration scripts for rollback.

3. **Team coordination** — multiple developers cannot accidentally conflict on
   schema changes. Each writes their own migration file.

4. **Production safety** — Hibernate's `update` can silently drop columns.
   Flyway only runs what you explicitly write.

5. **Compliance** — Nigerian bank regulators may audit schema changes.
   Flyway provides a complete, traceable history.

6. **Immutability** — once a migration runs, it cannot be changed. Flyway
   detects modifications and fails startup. This prevents accidental corruption
   of production databases.

---

**Q: What happens if you try to modify a Flyway migration file that has
already been applied?**

Flyway calculates a checksum of each migration file when it runs. The checksum
is stored in the `flyway_schema_history` table.

If you modify a file after it has run:
```
Flyway detects checksum mismatch for V2__create_auth_tables.sql
Expected checksum: 1234567890
Actual checksum:  9876543210
→ FlywayException: Validate failed
→ Application fails to start
```

**The correct approach:**
Never modify a migration that has been applied. Instead, create a new migration:
```
V2__create_auth_tables.sql     ← never touch this
V10__add_column_to_users.sql   ← add new file for the change
```

In NairaCore we experienced this when we needed to update the KYC table.
We created `V4__update_kyc_details.sql` with an `ALTER TABLE` statement
rather than modifying V3.

---

**Q: Why did you use separate schemas (auth, accounts, transactions) instead
of one flat schema?**

Separate schemas provide module isolation within a single database:

1. **Namespace separation** — `auth.users` and `accounts.accounts` can have
   columns with the same name without conflict.

2. **Access control** — in production, you can grant different database users
   access to different schemas. The accounts service user has no access to
   the auth schema.

3. **Module boundaries** — the schema structure mirrors the application
   module structure. Looking at the database immediately tells you which
   module owns which tables.

4. **No cross-schema foreign keys** — this is a deliberate constraint.
   `accounts.accounts.user_id` references `auth.users.id` conceptually but
   not via a database foreign key. This keeps schemas independent and
   enables future extraction to separate databases with zero SQL changes.

5. **Flyway organisation** — V1 creates all schemas upfront, subsequent
   migrations create tables in specific schemas. Clear ownership is maintained.

---

## RabbitMQ and Messaging

---

**Q: Explain the RabbitMQ components in NairaCore — exchange, queue, binding.**

**Exchange (`nairacore.transactions`):**
The exchange is the entry point for messages. Publishers send messages to
exchanges, not directly to queues. The exchange routes messages based on
routing keys and bindings.

In NairaCore we use a **Direct exchange** — routes messages to queues
where the binding key exactly matches the routing key.

**Queue (`nairacore.notifications`):**
The queue stores messages until a consumer picks them up. Messages are
stored durably (`durable = true`) so they survive RabbitMQ restarts.

**Binding:**
A binding connects an exchange to a queue with a routing key:
```java
BindingBuilder
    .bind(notificationQueue())       // queue
    .to(transactionExchange())       // exchange
    .with(NOTIFICATION_ROUTING_KEY); // key: "transaction.notification"
```

**Message flow:**
```
TransactionService
→ rabbitTemplate.convertAndSend(
    "nairacore.transactions",       // exchange
    "transaction.notification",     // routing key
    event                           // payload
  )

RabbitMQ:
→ Exchange receives message
→ Finds binding where key = "transaction.notification"
→ Routes to "nairacore.notifications" queue

NotificationConsumer:
→ @RabbitListener(queues = "nairacore.notifications")
→ Picks up message from queue
→ Deserializes JSON → TransactionEvent
→ Processes notification
→ Sends ACK to RabbitMQ
→ Message removed from queue
```

---

**Q: What is a Dead Letter Queue and how is it used in NairaCore?**

A Dead Letter Queue (DLQ) is a special queue where messages go when they
cannot be processed successfully after the maximum number of retries.

In NairaCore:
```java
public static final String DEAD_LETTER_QUEUE = "nairacore.notifications.dlq";

@Bean
public Queue deadLetterQueue() {
    return new Queue(DEAD_LETTER_QUEUE, true);
}
```

**When a message goes to DLQ:**
1. Consumer receives message from `nairacore.notifications`
2. Consumer throws an unhandled exception (e.g. Mailpit is down)
3. RabbitMQ requeues the message
4. After max retry attempts, message moved to DLQ

**Why DLQ matters in banking:**
Without DLQ, failed messages are silently lost. A customer might never
receive a transaction notification and you would have no record of the failure.

With DLQ:
- Failed messages are preserved for investigation
- Operations team can inspect DLQ via RabbitMQ UI
- Messages can be manually replayed after fixing the underlying issue
- Full audit trail of notification failures

**Production enhancement:**
Set up monitoring alerts when messages appear in the DLQ, and a scheduled
job to automatically retry DLQ messages after a configurable delay.

---

**Q: What is `JacksonJsonMessageConverter` and what does it do?**

`JacksonJsonMessageConverter` is a Spring AMQP message converter that
automatically serializes Java objects to JSON when publishing and
deserializes JSON back to Java objects when consuming.

```java
@Bean
public MessageConverter messageConverter() {
    return new JacksonJsonMessageConverter();
}

@Bean
public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
    RabbitTemplate template = new RabbitTemplate(connectionFactory);
    template.setMessageConverter(messageConverter());
    return template;
}
```

**Without it:**
```java
// Publishing — manual serialization
byte[] bytes = objectMapper.writeValueAsBytes(event);
rabbitTemplate.send(exchange, routingKey, new Message(bytes));

// Consuming — manual deserialization
TransactionEvent event = objectMapper.readValue(message.getBody(), TransactionEvent.class);
```

**With it:**
```java
// Publishing — automatic
rabbitTemplate.convertAndSend(exchange, routingKey, event); // event serialized automatically

// Consuming — automatic
@RabbitListener(queues = "nairacore.notifications")
public void consume(TransactionEvent event) { } // deserialized automatically
```

**Important:** In Spring Boot 4.x, `Jackson2JsonMessageConverter` was deprecated
in favour of `JacksonJsonMessageConverter` which uses Jackson 3.

---

## REST API Design

---

**Q: Why does NairaCore use `/api/v1/` prefix in all endpoints?**

The `/api/v1/` prefix serves two purposes:

1. **API versioning** — the `v1` indicates this is version 1 of the API.
   When breaking changes are needed, a new version (`v2`) is introduced:
   ```
   /api/v1/accounts  ← existing clients continue using this
   /api/v2/accounts  ← new clients use the updated version
   ```
   Both versions can run simultaneously during a transition period.

2. **Namespace separation** — the `/api/` prefix distinguishes API endpoints
   from other URL patterns (static assets, admin pages, health checks).

In Nigerian fintech, API versioning is critical because:
- Multiple mobile app versions are always in use simultaneously
- You cannot force all customers to update their app immediately
- Breaking changes in the API would crash older app versions

---

**Q: Why does NairaCore use different HTTP status codes for different operations?**

HTTP status codes communicate the result of an operation to clients without
requiring them to parse the response body.

In NairaCore:

```
201 Created    → POST /api/v1/accounts (new account created)
               → POST /api/v1/transactions/deposit (new transaction)
200 OK         → GET /api/v1/accounts/my-accounts
               → POST /api/v1/auth/login (login returns tokens, not creates a resource)
400 Bad Request → Validation failure, business rule violation
401 Unauthorized → Authentication failure, wrong credentials, expired token
403 Forbidden   → Authenticated but no permission
404 Not Found   → Resource does not exist
500 Server Error → Unexpected error
```

**Why `201` for transactions:**
A deposit creates a new transaction record. The correct semantic for
"a new resource was created" is 201, not 200.

**Why `200` for login:**
Login does not create a new resource — it authenticates and returns tokens.
Some APIs use 200 for login; others use 201 treating the session as a resource.
We use 200 because authentication is not resource creation.

**Client benefits:**
- Clients can check the status code without parsing JSON
- Error handling is more straightforward
- Caching rules are based on status codes
- Standard — all HTTP clients understand these codes

---

**Q: What is the `ApiResponse<T>` wrapper you created and why?**

`ApiResponse<T>` is a generic envelope that wraps all API responses in a
consistent format:

```java
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private LocalDateTime timestamp;
}
```

Every response follows this structure:
```json
{
  "success": true,
  "message": "Account created successfully",
  "data": { ... },
  "timestamp": "2026-04-17T10:00:00"
}
```

**Why this pattern:**

1. **Consistency** — clients always know where to find the data and status
2. **Error uniformity** — errors follow the same structure, just `success: false`
3. **Debugging** — timestamp helps correlate client logs with server logs
4. **Extensibility** — can add `requestId`, `version`, `pagination` without
   breaking existing clients

**Fintech standard:**
Paystack, Flutterwave and Moniepoint all use a similar envelope pattern.
Frontend and mobile developers appreciate consistent response shapes.

---

## Exception Handling

---

**Q: How does `@RestControllerAdvice` work in NairaCore?**

`@RestControllerAdvice` is a global exception handler that intercepts exceptions
thrown anywhere in the application and converts them to structured HTTP responses.

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(BadRequestException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }
}
```

**Without it:**
```
BadRequestException thrown in AuthService
→ Propagates to Spring's default error handler
→ Returns Spring's default error JSON (ugly, inconsistent)
→ Or worse, a raw stack trace
```

**With it:**
```
BadRequestException thrown in AuthService
→ GlobalExceptionHandler.handleBadRequest() called automatically
→ Returns clean ApiResponse with 400 status
```

**The generic catch-all:**
```java
@ExceptionHandler(Exception.class)
public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
    return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error("An unexpected error occurred"));
}
```

This ensures stack traces NEVER reach the client — a critical security
requirement. Exposing stack traces reveals internal implementation details
that attackers can use.

---

**Q: Why did you create custom exceptions (`BadRequestException`, `UnauthorizedException`,
`ResourceNotFoundException`) instead of using Java's built-in exceptions?**

Custom exceptions provide several advantages:

1. **Semantic clarity** — `BadRequestException` clearly communicates intent.
   `IllegalArgumentException` is ambiguous — is it a client error or a bug?

2. **HTTP mapping** — each custom exception maps to a specific HTTP status:
   ```java
   BadRequestException       → 400 Bad Request
   UnauthorizedException     → 401 Unauthorized
   ResourceNotFoundException → 404 Not Found
   ```
   Java's built-in exceptions have no HTTP semantic.

3. **Consistent handling** — `GlobalExceptionHandler` handles each custom
   exception type with the appropriate status code. No ambiguity.

4. **Domain language** — `ResourceNotFoundException("Account not found: 0123100000")`
   reads like business language, not technical language.

5. **No checked exceptions** — our custom exceptions extend `RuntimeException`,
   so service methods do not need `throws` declarations. This keeps code clean.

---

## Testing

---

**Q: Explain the difference between unit tests and integration tests
and which you wrote for NairaCore.**

**Unit tests** test a single class in isolation. All dependencies are mocked.
No Spring context, no database, no external services.

```java
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock UserRepository userRepository;
    @InjectMocks AuthService authService;
    // Tests run in milliseconds
}
```

**Integration tests** test multiple components working together. Typically
involve a real (or in-memory) database and a Spring context.

```java
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {
    @Autowired MockMvc mockMvc;
    // Tests the full stack: Controller → Service → Repository → H2 Database
}
```

**In NairaCore, we wrote unit tests** for three service classes:
- `AuthServiceTest` — 12 tests
- `AccountServiceTest` — 25 tests
- `TransactionServiceTest` — 20 tests
- Total: 58 tests, all passing

**Why unit tests first:**
- Faster — 58 tests complete in ~9 seconds
- No infrastructure needed — no database, no RabbitMQ
- Forces better design — if a class is hard to test in isolation,
  it probably has too many dependencies (violates single responsibility)
- Higher coverage — each test targets specific business logic paths

---

**Q: What is Mockito and how did you use it?**

Mockito is a mocking framework for Java that creates fake implementations
of dependencies for unit testing.

**Key annotations:**
```java
@Mock UserRepository userRepository;
// Creates a fake UserRepository that returns null by default

@InjectMocks AuthService authService;
// Creates real AuthService with mock dependencies injected
```

**Stubbing — defining mock behaviour:**
```java
when(userRepository.existsByEmail("david@nairacore.com")).thenReturn(false);
// When this method is called with this argument, return false
```

**Verification — confirming interactions:**
```java
verify(userRepository).save(any(User.class));
// Verify save() was called exactly once with any User argument

verify(userRepository, never()).save(any(User.class));
// Verify save() was NEVER called
```

**ArgumentMatchers:**
```java
any(UUID.class)    // any UUID value
anyString()        // any String value
eq("specific")     // exactly this value
```

**Why strict stubbing:**
Mockito's strict mode (default in JUnit 5) fails tests when:
- A stub was set up but never called (indicates unnecessary stubbing)
- A method was called with different arguments than stubbed

This caught a real bug in our tests where `generateAccessToken` was
receiving `null` for userId because `@PrePersist` was not firing in mocks.

---

**Q: What is AssertJ and why use it over JUnit's built-in assertions?**

AssertJ provides fluent, readable assertions that produce clear failure messages.

**JUnit built-in assertions:**
```java
assertEquals("ACTIVE", response.getStatus());
// Failure message: expected: <ACTIVE> but was: <null>
```

**AssertJ:**
```java
assertThat(response.getStatus()).isEqualTo("ACTIVE");
// Failure message: Expecting actual: <null> to be equal to: <"ACTIVE">
// More readable in test reports
```

**AssertJ advantages:**
```java
// Chaining
assertThat(response)
    .isNotNull()
    .extracting("accountNumber", "status")
    .containsExactly("0123100000", "ACTIVE");

// Collection assertions
assertThat(accounts).hasSize(2);
assertThat(accounts).extracting("accountType").containsExactly("SAVINGS", "CURRENT");

// Exception assertions
assertThatThrownBy(() -> service.method())
    .isInstanceOf(BadRequestException.class)
    .hasMessage("Email already in use");

// BigDecimal comparison
assertThat(balance).isEqualByComparingTo(BigDecimal.valueOf(50000));
```

`isEqualByComparingTo` is critical for BigDecimal — it uses `compareTo()`
rather than `equals()`, correctly treating `50000.0` and `50000.00` as equal.

---

## Docker and Infrastructure

---

**Q: Explain the multi-stage Docker build you implemented.**

A multi-stage build uses multiple `FROM` statements in one Dockerfile,
each creating a separate intermediate image. Only the final image is kept.

```dockerfile
# Stage 1: Build
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B     # download dependencies (cached layer)
COPY src ./src
RUN mvn clean package -DskipTests -B # compile and package

# Stage 2: Run
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Benefits:**

1. **Smaller final image** — Stage 1 uses full Maven + JDK (~700MB).
   Stage 2 uses only JRE (~200MB). The Maven and JDK are discarded.

2. **Security** — Source code and build tools are not in the production image.
   Attackers cannot access the build environment.

3. **Layer caching** — `COPY pom.xml` + `RUN mvn dependency:go-offline` is
   cached. Dependencies only re-download when `pom.xml` changes.
   Subsequent builds (code changes only) are much faster.

---

**Q: What is Docker Compose and how does it coordinate the NairaCore services?**

Docker Compose defines and runs multi-container applications using a YAML file.

NairaCore's `docker-compose.yml` defines four services:
```yaml
services:
  postgres:   # database
  rabbitmq:   # message broker
  mailpit:    # email testing
  nairacore:  # the application
```

**Key features used:**

**Health checks:**
```yaml
healthcheck:
  test: ["CMD-SHELL", "pg_isready -U nairacore"]
  interval: 10s
  timeout: 5s
  retries: 5
```
PostgreSQL and RabbitMQ have health checks. NairaCore waits for them to
be healthy before starting — preventing startup failures due to database
not being ready.

**depends_on:**
```yaml
depends_on:
  postgres:
    condition: service_healthy
  rabbitmq:
    condition: service_healthy
```
NairaCore only starts after its dependencies are healthy.

**Named volumes:**
```yaml
volumes:
  postgres_data:
  rabbitmq_data:
```
Data persists between `docker compose down` and `docker compose up`.
Without this, all data is lost when containers stop.

**Shared network:**
All services share `nairacore-network`. Services reference each other
by service name (`postgres`, `rabbitmq`, `mailpit`) rather than IP addresses.

---

**Q: What is the purpose of `application-docker.yml` and Spring profiles?**

Spring profiles allow different configurations for different environments.

```yaml
# application.yml (default — local development)
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/nairacore

# application-docker.yml (Docker environment)
spring:
  datasource:
    url: jdbc:postgresql://postgres:5432/nairacore
```

In Docker, `postgres` is the service name, not `localhost`.
The profile is activated via environment variable:

```yaml
# docker-compose.yml
environment:
  SPRING_PROFILES_ACTIVE: docker
```

**Profile loading order:**
1. `application.yml` — always loaded first (base config)
2. `application-docker.yml` — loaded if `docker` profile is active
3. `application-docker.yml` values override `application.yml` values

**The `${VAR:default}` syntax:**
```yaml
jwt:
  secret: ${JWT_SECRET:defaultvalue}
```
Uses environment variable `JWT_SECRET` if set, otherwise uses `defaultvalue`.
This allows secrets to be injected at runtime without hardcoding them in files.
