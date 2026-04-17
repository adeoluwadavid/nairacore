# NairaCore API Payloads

> 🏠 **Back to project documentation** → [README.md](./README.md)

> Base URL: `http://localhost:8080`
> All protected endpoints require: `Authorization: Bearer {accessToken}`
> Access tokens expire in **15 minutes** — login again if you get a 401.

---

## Table of Contents

- [Auth Endpoints](#auth-endpoints)
- [Account Endpoints](#account-endpoints)
- [Transaction Endpoints](#transaction-endpoints)
- [Notification Endpoints](#notification-endpoints)
- [End-to-End Test Sequence](#end-to-end-test-sequence)
- [Common Error Responses](#common-error-responses)
- [Developer Tools](#developer-tools)

---

## Auth Endpoints

### Register Customer
```
POST /api/v1/auth/register
Access: Public
```
```json
{
  "firstName": "David",
  "lastName": "Adewole",
  "email": "david@nairacore.com",
  "phoneNumber": "+2348102395070",
  "password": "Password123"
}
```
**Response: 201 Created**
```json
{
  "success": true,
  "message": "Registration successful",
  "data": {
    "accessToken": "eyJhbGci...",
    "refreshToken": "76482a6e-...",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "user": {
      "id": "uuid",
      "firstName": "David",
      "lastName": "Adewole",
      "email": "david@nairacore.com",
      "phoneNumber": "+2348102395070",
      "role": "CUSTOMER",
      "active": true
    }
  },
  "timestamp": "2026-04-17T10:00:00"
}
```

---

### Login
```
POST /api/v1/auth/login
Access: Public
```
```json
{
  "email": "david@nairacore.com",
  "password": "Password123"
}
```
**Response: 200 OK** — same structure as register response above.

---

### Login as Admin
```
POST /api/v1/auth/login
Access: Public
```
```json
{
  "email": "admin@nairacore.com",
  "password": "Password123"
}
```

---

### Refresh Token
```
POST /api/v1/auth/refresh
Access: Public
```
```json
{
  "refreshToken": "76482a6e-68c9-4073-9385-b8c221cd9365"
}
```
**Response: 200 OK** — returns new accessToken + new refreshToken.

---

### Logout
```
POST /api/v1/auth/logout
Access: Authenticated
Authorization: Bearer {accessToken}
```
```json
{
  "refreshToken": "76482a6e-68c9-4073-9385-b8c221cd9365"
}
```
**Response: 200 OK**
```json
{
  "success": true,
  "message": "Logged out successfully",
  "data": null
}
```

---

### Admin Create User
```
POST /api/v1/auth/admin/create-user
Access: ADMIN only
Authorization: Bearer {adminToken}
```

**Create Teller:**
```json
{
  "firstName": "Amaka",
  "lastName": "Obi",
  "email": "amaka@nairacore.com",
  "phoneNumber": "+2348100000002",
  "password": "Password123",
  "role": "TELLER"
}
```

**Create Admin:**
```json
{
  "firstName": "Chidi",
  "lastName": "Okeke",
  "email": "chidi@nairacore.com",
  "phoneNumber": "+2348100000003",
  "password": "Password123",
  "role": "ADMIN"
}
```

**Create Customer (VIP onboarding):**
```json
{
  "firstName": "Ngozi",
  "lastName": "Eze",
  "email": "ngozi@nairacore.com",
  "phoneNumber": "+2348100000004",
  "password": "Password123",
  "role": "CUSTOMER"
}
```

**Valid roles:** `CUSTOMER` | `TELLER` | `ADMIN`

**Response: 201 Created**
```json
{
  "success": true,
  "message": "User created successfully",
  "data": {
    "id": "uuid",
    "firstName": "Amaka",
    "lastName": "Obi",
    "email": "amaka@nairacore.com",
    "role": "TELLER",
    "active": true
  }
}
```

---

## Account Endpoints

### Create Account — Customer (Self)
```
POST /api/v1/accounts
Access: CUSTOMER, TELLER, ADMIN
Authorization: Bearer {customerToken}
```
```json
{
  "accountType": "SAVINGS",
  "accountName": "David Adewole",
  "currency": "NGN"
}
```

**Valid account types:** `SAVINGS` | `CURRENT` | `FIXED_DEPOSIT`
**Valid currencies:** `NGN` | `USD` | `GBP`

**Response: 201 Created**
```json
{
  "success": true,
  "message": "Account created successfully",
  "data": {
    "id": "uuid",
    "userId": "uuid",
    "accountNumber": "0123100000",
    "accountName": "David Adewole",
    "accountType": "SAVINGS",
    "status": "ACTIVE",
    "balance": 0.0000,
    "currency": "NGN",
    "createdAt": "2026-04-17T10:00:00"
  }
}
```

---

### Create Account — Teller on Behalf of Customer
```
POST /api/v1/accounts
Access: TELLER, ADMIN
Authorization: Bearer {tellerToken}
```
```json
{
  "accountType": "CURRENT",
  "accountName": "David Adewole",
  "currency": "NGN",
  "targetUserId": "74a3aed3-4ae5-43b7-b050-17af0ae81b73"
}
```
> `targetUserId` is required when TELLER or ADMIN creates an account.
> Must be a valid UUID of an existing user.

---

### Get My Accounts
```
GET /api/v1/accounts/my-accounts
Access: CUSTOMER
Authorization: Bearer {customerToken}
```
No body required.

---

### Get Account By Account Number
```
GET /api/v1/accounts/{accountNumber}
Access: CUSTOMER (own), TELLER, ADMIN
Authorization: Bearer {anyToken}

Example:
GET /api/v1/accounts/0123100000
```
No body required.

---

### Get Balance
```
GET /api/v1/accounts/{accountNumber}/balance
Access: CUSTOMER (own), TELLER, ADMIN
Authorization: Bearer {anyToken}

Example:
GET /api/v1/accounts/0123100000/balance
```
No body required.

---

### Deactivate Account
```
PUT /api/v1/accounts/{accountId}/deactivate
Access: TELLER, ADMIN
Authorization: Bearer {tellerToken or adminToken}

Example:
PUT /api/v1/accounts/74a3aed3-4ae5-43b7-b050-17af0ae81b73/deactivate
```
> Note: `{accountId}` is the UUID of the account, not the account number.

No body required.

---

### Submit KYC
```
POST /api/v1/accounts/kyc
Access: CUSTOMER
Authorization: Bearer {customerToken}
```
```json
{
  "bvn": "12345678901",
  "nin": "98765432101",
  "dateOfBirth": "1990-05-15",
  "address": "12 Awolowo Road",
  "city": "Ibadan",
  "state": "Oyo",
  "idType": "NATIONAL_ID",
  "idNumber": "AB1234567",
  "idExpiryDate": "2028-12-31"
}
```

> **Required:** `bvn`, `address`, `city`, `state`
> **Optional:** `nin`, `dateOfBirth`, `idType`, `idNumber`, `idExpiryDate`
> **BVN format:** exactly 11 digits, numbers only
> **Date format:** `YYYY-MM-DD`

---

### Get KYC
```
GET /api/v1/accounts/kyc
Access: CUSTOMER
Authorization: Bearer {customerToken}
```
No body required.

---

## Transaction Endpoints

> **Important:** Every transaction requires a unique `idempotencyKey`.
> Generate a new UUID for each new transaction.
> Reuse the same UUID to test idempotency (retry simulation).

---

### Deposit
```
POST /api/v1/transactions/deposit
Access: CUSTOMER (own account), TELLER, ADMIN
Authorization: Bearer {anyToken}
```
```json
{
  "accountNumber": "0123100000",
  "amount": 50000.00,
  "description": "Initial deposit",
  "idempotencyKey": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

> **Minimum amount:** ₦1.00
> `description` is optional

**Response: 201 Created**
```json
{
  "success": true,
  "message": "Deposit successful",
  "data": {
    "id": "uuid",
    "reference": "NRC100000",
    "type": "DEPOSIT",
    "status": "SUCCESS",
    "amount": 50000.0000,
    "currency": "NGN",
    "sourceAccountNumber": null,
    "destinationAccountNumber": "0123100000",
    "description": "Initial deposit",
    "failureReason": null,
    "initiatedBy": "uuid",
    "createdAt": "2026-04-17T10:00:00"
  }
}
```

> After a successful deposit an email notification is sent to the account
> owner and visible in Mailpit at `http://localhost:8025`

---

### Withdraw
```
POST /api/v1/transactions/withdraw
Access: CUSTOMER (own account), TELLER, ADMIN
Authorization: Bearer {anyToken}
```
```json
{
  "accountNumber": "0123100000",
  "amount": 5000.00,
  "description": "ATM withdrawal",
  "idempotencyKey": "b2c3d4e5-f6a7-8901-bcde-f12345678901"
}
```

**Insufficient Balance Response: 400 Bad Request**
```json
{
  "success": false,
  "message": "Insufficient balance",
  "data": null
}
```

---

### Transfer
```
POST /api/v1/transactions/transfer
Access: CUSTOMER (own source account), TELLER, ADMIN
Authorization: Bearer {anyToken}
```
```json
{
  "sourceAccountNumber": "0123100000",
  "destinationAccountNumber": "0123100001",
  "amount": 10000.00,
  "description": "Transfer to current account",
  "idempotencyKey": "c3d4e5f6-a7b8-9012-cdef-123456789012"
}
```

**Same Account Error: 400 Bad Request**
```json
{
  "success": false,
  "message": "Source and destination accounts cannot be the same",
  "data": null
}
```

---

### Get Transaction By Reference
```
GET /api/v1/transactions/{reference}
Access: CUSTOMER, TELLER, ADMIN
Authorization: Bearer {anyToken}

Example:
GET /api/v1/transactions/NRC100000
```
No body required.

---

### Get Account Transaction History
```
GET /api/v1/transactions/account/{accountNumber}
Access: CUSTOMER, TELLER, ADMIN
Authorization: Bearer {anyToken}

Example:
GET /api/v1/transactions/account/0123100000
```
No body required.

---

### Get Ledger Entries
```
GET /api/v1/transactions/ledger/{accountNumber}
Access: TELLER, ADMIN only
Authorization: Bearer {tellerToken or adminToken}

Example:
GET /api/v1/transactions/ledger/0123100000
```
No body required.

**Response: 200 OK**
```json
{
  "success": true,
  "message": "Ledger entries retrieved successfully",
  "data": [
    {
      "id": "uuid",
      "transactionReference": "NRC100002",
      "accountNumber": "0123100000",
      "entryType": "DEBIT",
      "amount": 10000.0000,
      "balanceBefore": 45000.0000,
      "balanceAfter": 35000.0000,
      "createdAt": "2026-04-17T10:05:00"
    }
  ]
}
```

---

## Notification Endpoints

### Get My Notifications
```
GET /api/v1/notifications
Access: CUSTOMER only
Authorization: Bearer {customerToken}
```
No body required.

**Response: 200 OK**
```json
{
  "success": true,
  "message": "Notifications retrieved successfully",
  "data": [
    {
      "id": "uuid",
      "userId": "uuid",
      "accountNumber": "0123100000",
      "transactionReference": "NRC100000",
      "type": "DEPOSIT_SUCCESS",
      "channel": "EMAIL",
      "recipient": "david@nairacore.com",
      "subject": "Deposit Successful — NRC100000",
      "message": "Dear David Adewole, your account 0123100000 has been credited with NGN 50,000.00. Available balance: NGN 50,000.00. Reference: NRC100000.",
      "status": "SENT",
      "failureReason": null,
      "createdAt": "2026-04-17T10:00:00"
    }
  ]
}
```

> View actual email content in Mailpit at `http://localhost:8025`

---

### Get Notifications By Transaction Reference
```
GET /api/v1/notifications/transaction/{reference}
Access: TELLER, ADMIN only
Authorization: Bearer {tellerToken or adminToken}

Example:
GET /api/v1/notifications/transaction/NRC100000
```
No body required.

**Notification types:**

| Type | Trigger |
|---|---|
| DEPOSIT_SUCCESS | Successful deposit |
| WITHDRAWAL_SUCCESS | Successful withdrawal |
| TRANSFER_DEBIT | Successful transfer (sender) |
| TRANSFER_CREDIT | Successful transfer (receiver) |
| ACCOUNT_CREATED | New account opened |
| KYC_SUBMITTED | KYC details submitted |

---

## End-to-End Test Sequence

Follow this order for a complete system test:

| Step | Action | Token | Expected |
|---|---|---|---|
| 1 | Register David as customer | None | 201 |
| 2 | Login as admin | None | 200 — copy adminToken |
| 3 | Admin creates teller Amaka | adminToken | 201 |
| 4 | Login as Amaka (teller) | None | 200 — copy tellerToken |
| 5 | Login as David (customer) | None | 200 — copy customerToken |
| 6 | David creates SAVINGS account | customerToken | 201 — copy accountNumber |
| 7 | Teller creates CURRENT for David | tellerToken | 201 — copy accountNumber |
| 8 | David submits KYC | customerToken | 201 |
| 9 | David gets his accounts | customerToken | 200 — list of 2 accounts |
| 10 | Get account by number | customerToken | 200 |
| 11 | Get balance (should be 0) | customerToken | 200 — balance: 0.0000 |
| 12 | Deposit ₦50,000 into savings | customerToken | 201 — check Mailpit for email |
| 13 | Deposit ₦20,000 into current | customerToken | 201 — check Mailpit for email |
| 14 | Retry step 12 (same idempotencyKey) | customerToken | 201 — same reference, balance unchanged |
| 15 | Withdraw ₦5,000 from savings | customerToken | 201 — balance: 45000.0000 |
| 16 | Withdraw ₦999,999 (insufficient) | customerToken | 400 — Insufficient balance |
| 17 | Transfer ₦10,000 savings → current | customerToken | 201 — savings: 35000, current: 30000 |
| 18 | Get transaction history | customerToken | 200 — list of transactions |
| 19 | Get transaction by reference | customerToken | 200 — full details |
| 20 | Get ledger entries | adminToken | 200 — debit/credit entries |
| 21 | Get David's KYC | customerToken | 200 |
| 22 | Get my notifications | customerToken | 200 — list of notification logs |
| 23 | Get notifications by reference | adminToken | 200 — notification for that transaction |
| 24 | View another customer's account | customerToken | 401 — no permission |

---

## Common Error Responses

### 400 Bad Request — Validation Failed
```json
{
  "success": false,
  "message": "Validation failed",
  "data": {
    "email": "Invalid email format",
    "password": "Password must be at least 8 characters"
  }
}
```

### 400 Bad Request — Business Rule
```json
{
  "success": false,
  "message": "Email already in use",
  "data": null,
  "timestamp": "2026-04-17T10:00:00"
}
```

### 401 Unauthorized — Wrong Credentials
```json
{
  "success": false,
  "message": "Invalid email or password",
  "data": null
}
```

### 401 Unauthorized — Token Expired
```json
{
  "success": false,
  "message": "Token expired. Please login again"
}
```

### 403 Forbidden
Returned when accessing a protected endpoint without a token.

### 404 Not Found
```json
{
  "success": false,
  "message": "Account not found: 0123999999",
  "data": null
}
```

### 500 Internal Server Error
```json
{
  "success": false,
  "message": "An unexpected error occurred",
  "data": null
}
```

---

## Quick Reference — idempotencyKey Examples

Use these for testing. Each must be unique per new transaction:

```
a1b2c3d4-e5f6-7890-abcd-ef1234567890  → deposit 1
b2c3d4e5-f6a7-8901-bcde-f12345678901  → deposit 2
c3d4e5f6-a7b8-9012-cdef-123456789012  → deposit 3
d4e5f6a7-b8c9-0123-defa-234567890123  → withdrawal 1
e5f6a7b8-c9d0-1234-efab-345678901234  → withdrawal 2
f6a7b8c9-d0e1-2345-fabc-456789012345  → transfer 1
a7b8c9d0-e1f2-3456-abcd-567890123456  → transfer 2
b8c9d0e1-f2a3-4567-bcde-678901234567  → transfer 3
```

Generate new ones at: https://www.uuidgenerator.net

---

## Developer Tools

| Tool | URL | Purpose |
|---|---|---|
| Swagger UI | http://localhost:8080/swagger-ui/index.html | Test all API endpoints |
| RabbitMQ UI | http://localhost:15672 | Monitor message queues |
| Mailpit UI | http://localhost:8025 | View sent emails |

> 🏠 **Back to project documentation** → [README.md](./README.md)
