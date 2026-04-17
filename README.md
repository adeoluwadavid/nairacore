# NairaCore Banking API

A comprehensive core banking system built with Java Spring Boot, implementing real-world
financial operations including account management, transactions, double-entry bookkeeping,
role-based access control, and event-driven notifications.

> 📋 **API Payloads & Test Guide** → [PAYLOADS.md](./PAYLOADS.md)

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Technology Stack](#technology-stack)
- [Modules](#modules)
- [Database Design](#database-design)
- [Security](#security)
- [Getting Started](#getting-started)
- [Running With Docker](#running-with-docker)
- [Running Locally](#running-locally)
- [Environment Configuration](#environment-configuration)
- [API Documentation](#api-documentation)
- [API Reference](#api-reference)
- [Developer Tools](#developer-tools)

---

## Overview

NairaCore is a modular monolith core banking system that simulates the backend
infrastructure of a Nigerian fintech or commercial bank. It supports three user
roles — Customer, Teller, and Admin — each with clearly defined permissions and
access boundaries.

Key financial concepts implemented:
- **Double-entry bookkeeping** — every transaction creates two ledger entries
- **Idempotency** — retrying a transaction never results in duplicate processing
- **Optimistic locking** — concurrent balance updates are handled safely
- **Immutable audit trail** — ledger entries are never updated or deleted
- **Event-driven notifications** — decoupled email notifications via RabbitMQ

---

## Architecture

NairaCore is built as a **modular monolith** — a single deployable Spring Boot
application with clearly separated modules. Each module owns its own database
schema, controllers, services, repositories, entities, and DTOs. Modules do not
directly access each other's repositories.

```
nairacore/
├── auth/           → User registration, login, JWT, refresh tokens
├── accounts/       → Account management, KYC
├── transactions/   → Deposits, withdrawals, transfers, ledger
├── notifications/  → Event-driven notifications via RabbitMQ
└── shared/         → JWT utilities, security config, global exceptions
```

### Why Modular Monolith?

The architecture is deliberately designed so any module can be extracted into a
standalone microservice by changing only the communication layer from in-process
calls to HTTP or messaging. This mirrors how companies like Shopify scaled —
monolith first, extract only when a boundary proves it needs independent scaling.

### Notification Architecture

```
TransactionService
→ publishEvent() after SUCCESS
→ RabbitMQ Exchange (nairacore.transactions)
→ Queue (nairacore.notifications)
→ NotificationConsumer
→ JavaMailSender → Mailpit (local) / SendGrid (production)
→ NotificationLog saved to database
```

Failed messages go to the Dead Letter Queue (`nairacore.notifications.dlq`)
for investigation and reprocessing.

---

## Technology Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 4.x |
| Security | Spring Security 7.x + JWT |
| Database | PostgreSQL 14 |
| Migrations | Flyway |
| ORM | Spring Data JPA / Hibernate |
| Messaging | RabbitMQ 4.x |
| Email (local) | Mailpit |
| Documentation | SpringDoc OpenAPI 3.x (Swagger UI) |
| Build Tool | Maven |
| Containerization | Docker + Docker Compose |

---

## Modules

### Auth Module
Handles all authentication and user management.

- User registration (self-service, always CUSTOMER role)
- Login with JWT access token + refresh token
- Refresh token rotation (single-use, 30-day expiry)
- Logout (invalidates refresh token)
- Admin-only user creation (any role)
- Spring Security filter chain with JWT validation

### Accounts Module
Handles bank account lifecycle and KYC.

- Account creation (SAVINGS, CURRENT, FIXED_DEPOSIT)
- Sequence-based 10-digit Nigerian account number generation
- One account per type per customer
- Account status management (ACTIVE, FROZEN, CLOSED)
- KYC submission and retrieval (BVN, NIN, address, ID documents)
- Role-based access — customers see only their own accounts

### Transactions Module
The core financial engine.

- Deposits, withdrawals, and transfers
- Idempotency via client-generated keys
- Transaction state machine: PENDING → PROCESSING → SUCCESS/FAILED
- Double-entry ledger entries for every transaction
- Balance stored as NUMERIC(19,4) — never float or double
- Optimistic locking via @Version to prevent concurrent balance corruption
- Complete transaction history per account
- Ledger entries restricted to TELLER and ADMIN

### Notifications Module
Event-driven notification system.

- RabbitMQ-based event publishing after every successful transaction
- Email notifications for DEPOSIT, WITHDRAWAL, TRANSFER
- Notification log persisted in database with SENT/FAILED status
- Dead Letter Queue for failed notifications
- Mailpit integration for local email testing
- Decoupled from transaction processing — notification failure never affects transactions

---

## Database Design

Single PostgreSQL database with separate schemas per module:

```
nairacore (database)
├── auth schema
│   ├── users
│   └── refresh_tokens
├── accounts schema
│   ├── accounts
│   └── kyc_details
├── transactions schema
│   ├── transactions
│   └── ledger_entries
└── notifications schema
    └── notification_logs
```

### Key Design Decisions

**UUIDs over sequential IDs** — prevents ID enumeration attacks common in financial APIs.

**No cross-schema foreign keys** — schemas are logically independent. Relationships
are maintained at the application level via userId references. This makes future
extraction to microservices straightforward.

**Append-only ledger** — ledger_entries has no updated_at column. Records are
created once and never modified. This provides an immutable audit trail.

**NUMERIC(19,4) for money** — exact decimal storage. Floating point numbers cannot
represent decimal values exactly in binary, making them unsuitable for financial amounts.

**Sequence-based account numbers** — PostgreSQL sequences guarantee uniqueness
atomically. No collision risk, no retry loops. Format: `0123XXXXXX` (10 digits).

---

## Security

### Authentication Flow
```
POST /api/v1/auth/login
→ Returns: accessToken (15 min) + refreshToken (30 days)

Every protected request:
→ Authorization: Bearer {accessToken}
→ JwtAuthFilter validates token
→ UserPrincipal set in SecurityContextHolder

Access token expiry:
→ POST /api/v1/auth/refresh with refreshToken
→ Returns new accessToken + new refreshToken (rotation)
```

### Roles and Permissions

| Action | CUSTOMER | TELLER | ADMIN |
|---|---|---|---|
| Register | ✅ (self) | — | — |
| Create user | ❌ | ❌ | ✅ |
| Create own account | ✅ | — | — |
| Create account for customer | ❌ | ✅ | ✅ |
| View own accounts | ✅ | — | — |
| View any account | ❌ | ✅ | ✅ |
| Deposit/Withdraw/Transfer | ✅ (own) | ✅ (any) | ✅ (any) |
| Deactivate account | ❌ | ✅ | ✅ |
| View ledger entries | ❌ | ✅ | ✅ |
| Submit KYC | ✅ | — | — |
| View own notifications | ✅ | — | — |
| View notifications by reference | ❌ | ✅ | ✅ |

### JWT Security
- HS256 signing algorithm
- Claims include: userId, email (subject), role
- Stateless — no server-side sessions
- Expired token returns clean 401 with descriptive message

---

## Getting Started

### Prerequisites

**For Docker setup (recommended):**
- Docker Desktop

**For local setup:**
- Java 17+
- Maven 3.8+
- PostgreSQL 14+
- RabbitMQ 3.x
- Mailpit

### Clone the Repository
```bash
git clone https://github.com/adewole/nairacore.git
cd nairacore
```

---

## Running With Docker

The easiest way to run NairaCore. One command starts everything.

```bash
docker compose up --build
```

This starts four services:

| Service | Port |
|---|---|
| NairaCore API | 8080 |
| PostgreSQL | 5433 |
| RabbitMQ | 5672 (UI: 15672) |
| Mailpit | 8025 (SMTP: 1025) |

Flyway runs all migrations automatically on startup.
A super admin is seeded automatically via migration V5.

### Stop Services
```bash
docker compose down
```

### Stop and Remove All Data
```bash
docker compose down -v
```

### Default Admin Credentials
```
Email:    admin@nairacore.com
Password: Password123
```
Change this before any real deployment.

---

## Running Locally

### 1. Create the Database
```sql
CREATE DATABASE nairacore;
```

### 2. Start RabbitMQ
```bash
docker run -d --name rabbitmq \
  -p 5672:5672 -p 15672:15672 \
  rabbitmq:3-management
```

### 3. Start Mailpit
```bash
mailpit
```

### 4. Run the Application
```bash
./mvnw spring-boot:run
```

---

## Environment Configuration

Key properties in `application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/nairacore
    username: your_postgres_username
    password: your_postgres_password

  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest

  mail:
    host: localhost
    port: 1025

jwt:
  secret: your_256_bit_hex_secret
  expiration: 900000
```

> **Never commit credentials to version control.**
> Use environment variables or a secrets manager in production.

### Docker Environment Variables

| Variable | Description |
|---|---|
| `JWT_SECRET` | 256-bit hex secret for JWT signing |
| `JWT_EXPIRATION` | Access token expiry in milliseconds |
| `SPRING_PROFILES_ACTIVE` | Active Spring profile (docker) |

---

## API Documentation

Interactive Swagger UI:
```
http://localhost:8080/swagger-ui/index.html
```

OpenAPI JSON spec:
```
http://localhost:8080/api-docs
```

---

## API Reference

### Auth Endpoints

| Method | Endpoint | Access | Description |
|---|---|---|---|
| POST | /api/v1/auth/register | Public | Register new customer |
| POST | /api/v1/auth/login | Public | Login and get tokens |
| POST | /api/v1/auth/refresh | Public | Refresh access token |
| POST | /api/v1/auth/logout | Authenticated | Invalidate refresh token |
| POST | /api/v1/auth/admin/create-user | ADMIN | Create any role user |

### Account Endpoints

| Method | Endpoint | Access | Description |
|---|---|---|---|
| POST | /api/v1/accounts | ALL | Create account |
| GET | /api/v1/accounts/my-accounts | CUSTOMER | Get own accounts |
| GET | /api/v1/accounts/{accountNumber} | ALL | Get account details |
| GET | /api/v1/accounts/{accountNumber}/balance | ALL | Get balance |
| PUT | /api/v1/accounts/{id}/deactivate | TELLER, ADMIN | Deactivate account |
| POST | /api/v1/accounts/kyc | CUSTOMER | Submit KYC |
| GET | /api/v1/accounts/kyc | CUSTOMER | Get KYC details |

### Transaction Endpoints

| Method | Endpoint | Access | Description |
|---|---|---|---|
| POST | /api/v1/transactions/deposit | ALL | Deposit funds |
| POST | /api/v1/transactions/withdraw | ALL | Withdraw funds |
| POST | /api/v1/transactions/transfer | ALL | Transfer between accounts |
| GET | /api/v1/transactions/{reference} | ALL | Get transaction by reference |
| GET | /api/v1/transactions/account/{accountNumber} | ALL | Transaction history |
| GET | /api/v1/transactions/ledger/{accountNumber} | TELLER, ADMIN | Ledger entries |

### Notification Endpoints

| Method | Endpoint | Access | Description |
|---|---|---|---|
| GET | /api/v1/notifications | CUSTOMER | Get own notifications |
| GET | /api/v1/notifications/transaction/{reference} | TELLER, ADMIN | Get by reference |

> 📋 For full request/response payloads and test sequences → [PAYLOADS.md](./PAYLOADS.md)

---

## Developer Tools

| Tool | URL | Credentials |
|---|---|---|
| Swagger UI | http://localhost:8080/swagger-ui/index.html | JWT token |
| RabbitMQ UI | http://localhost:15672 | guest / guest |
| Mailpit UI | http://localhost:8025 | none required |
| pgAdmin (local) | localhost:5432 | your credentials |
| pgAdmin (Docker) | localhost:5433 | nairacore / nairacore |

---

## Author

**David Adewole**
Senior Software Engineer
Ibadan, Nigeria
adeoluwadavid@gmail.com
