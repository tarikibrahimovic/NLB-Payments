# NLB Payment API - Postman & cURL Collection

This guide provides a walkthrough of the main API routes using cURL commands. It is recommended to store values such as tokens and IDs in Postman environment variables for easier testing.

## Table of Contents
1.  [Authentication Flow](#1-authentication-flow)
2.  [Account Management](#2-account-management)
3.  [Transfer Flow](#3-transfer-flow)
4.  [Reporting Flow](#4-reporting-flow)

---

## 1. Authentication Flow
This section covers user registration and login.

### 1.1 Register User A
Register the first user. **Save the `token`, `userId`, and `accountId` from the response.**

**Endpoint:** `POST /auth/register`

**cURL Request:**
```bash
curl --location 'http://localhost:8080/auth/register' \
--header 'Content-Type: application/json' \
--data '{
    "email": "user.a@example.com",
    "fullName": "Korisnik A"
}'
```

**Example Response (`201 Created`):**
```json
{
    "userId": "a1b2c3d4-...",
    "accountId": "acc-a1-...",
    "token": "eyJh...A"
}
```

**Postman Setup:**
-   Save `token` as `{{TOKEN_A}}`
-   Save `accountId` as `{{ACCOUNT_ID_A_MAIN}}`
-   Save `userId` as `{{USER_ID_A}}`

---

### 1.2 Register User B
Register the second user, who will be the recipient in transfers.

**Endpoint:** `POST /auth/register`

**cURL Request:**
```bash
curl --location 'http://localhost:8080/auth/register' \
--header 'Content-Type: application/json' \
--data '{
    "email": "user.b@example.com",
    "fullName": "Korisnik B"
}'
```

**Example Response (`201 Created`):**
```json
{
    "userId": "e5f6g7h8-...",
    "accountId": "acc-b2-...",
    "token": "eyJh...B"
}
```

**Postman Setup:**
-   Save `accountId` as `{{ACCOUNT_ID_B_MAIN}}`
-   Save `token` as `{{TOKEN_B}}`

---

### 1.3 Login
Log in as User A to get a new token and a list of associated accounts.

**Endpoint:** `POST /auth/login`

**cURL Request:**
```bash
curl --location 'http://localhost:8080/auth/login' \
--header 'Content-Type: application/json' \
--data '{
    "email": "user.a@example.com"
}'
```

**Example Response (`200 OK`):**
```json
{
    "token": "eyJh...A_novi_token",
    "accountIds": [
        "{{ACCOUNT_ID_A_MAIN}}"
    ]
}
```
> **Note:** It's good practice to update `{{TOKEN_A}}` with the new token received upon login.

---

## 2. Account Management
All requests in this section should be authenticated using `{{TOKEN_A}}`.

### 2.1 Deposit Funds
Add 100 EUR to User A's main account.

**Endpoint:** `POST /api/v1/accounts/{accountId}/deposit`

**cURL Request:**
```bash
curl --location 'http://localhost:8080/api/v1/accounts/{{ACCOUNT_ID_A_MAIN}}/deposit' \
--header 'Authorization: Bearer {{TOKEN_A}}' \
--header 'Content-Type: application/json' \
--data '{
    "amount": "100.00"
}'
```

**Example Response (`200 OK`):**
```json
{
    "accountId": "{{ACCOUNT_ID_A_MAIN}}",
    "ownerId": "{{USER_ID_A}}",
    "balance": "100.00",
    "currency": "EUR",
    "status": "ACTIVE"
}
```
> The balance of `{{ACCOUNT_ID_A_MAIN}}` is now **100.00 EUR**.

---

### 2.2 Withdraw Funds
Withdraw 20 EUR from User A's main account.

**Endpoint:** `POST /api/v1/accounts/{accountId}/withdraw`

**cURL Request:**
```bash
curl --location 'http://localhost:8080/api/v1/accounts/{{ACCOUNT_ID_A_MAIN}}/withdraw' \
--header 'Authorization: Bearer {{TOKEN_A}}' \
--header 'Content-Type: application/json' \
--data '{
    "amount": "20.00"
}'
```

**Example Response (`200 OK`):**
```json
{
    "accountId": "{{ACCOUNT_ID_A_MAIN}}",
    "ownerId": "{{USER_ID_A}}",
    "balance": "80.00",
    "currency": "EUR",
    "status": "ACTIVE"
}
```
> The balance of `{{ACCOUNT_ID_A_MAIN}}` is now **80.00 EUR**.

---

### 2.3 Create a New Account
User A creates an additional EUR account (e.g., for savings).

**Endpoint:** `POST /api/v1/accounts`

**cURL Request:**
```bash
curl --location --request POST 'http://localhost:8080/api/v1/accounts' \
--header 'Authorization: Bearer {{TOKEN_A}}' \
--header 'Content-Type: application/json'
```

**Example Response (`201 Created`):**
```json
{
    "accountId": "novi-eur-acc-id-...",
    "ownerId": "{{USER_ID_A}}",
    "balance": "0.00",
    "currency": "EUR",
    "status": "ACTIVE"
}
```

**Postman Setup:**
-   Save the new `accountId` as `{{ACCOUNT_ID_A_SAVINGS}}`.

---

### 2.4 Get All User Accounts
Check all accounts owned by User A.

**Endpoint:** `GET /api/v1/accounts`

**cURL Request:**
```bash
curl --location 'http://localhost:8080/api/v1/accounts' \
--header 'Authorization: Bearer {{TOKEN_A}}'
```

**Example Response (`200 OK`):**
```json
[
    {
        "accountId": "{{ACCOUNT_ID_A_MAIN}}",
        "ownerId": "{{USER_ID_A}}",
        "balance": "80.00",
        "currency": "EUR",
        "status": "ACTIVE"
    },
    {
        "accountId": "{{ACCOUNT_ID_A_SAVINGS}}",
        "ownerId": "{{USER_ID_A}}",
        "balance": "0.00",
        "currency": "EUR",
        "status": "ACTIVE"
    }
]
```

---

## 3. Transfer Flow
This section demonstrates money transfers between accounts. We will use `{{ACCOUNT_ID_A_MAIN}}` (with a balance of 80 EUR) as the source.

### 3.1 Successful Transfer (Happy Path)
Send 12.50 EUR from User A's account to User B's account.

> **Important:** Generate a new UUID for the `Idempotency-Key` header for each new transfer request.

**Endpoint:** `POST /api/v1/transfers/batch`

**cURL Request:**
```bash
curl --location 'http://localhost:8080/api/v1/transfers/batch' \
--header 'Idempotency-Key: aaaaaaaa-bbbb-cccc-dddd-123456789012' \
--header 'Authorization: Bearer {{TOKEN_A}}' \
--header 'Content-Type: application/json' \
--data '{
    "sourceAccountId": "{{ACCOUNT_ID_A_MAIN}}",
    "items": [
        {
            "destinationAccountId": "{{ACCOUNT_ID_B_MAIN}}",
            "amount": "12.50"
        }
    ]
}'
```

**Example Response (`200 OK`):**
```json
{
    "paymentOrderId": "order-id-...",
    "status": "COMPLETED",
    "message": "Transfer successful"
}
```

**Postman Setup:**
-   Save the `paymentOrderId` as `{{ORDER_ID_1}}`.
> The balance of `{{ACCOUNT_ID_A_MAIN}}` is now **67.50 EUR**.

---

### 3.2 Failed Transfer (Insufficient Funds)
Attempt to send 100 EUR, which is more than the available balance of 67.50 EUR.

> **Important:** You must use a **new** `Idempotency-Key` for this request.

**Endpoint:** `POST /api/v1/transfers/batch`

**cURL Request:**
```bash
curl --location 'http://localhost:8080/api/v1/transfers/batch' \
--header 'Idempotency-Key: cccccccc-dddd-eeee-ffff-111111111111' \
--header 'Authorization: Bearer {{TOKEN_A}}' \
--header 'Content-Type: application/json' \
--data '{
    "sourceAccountId": "{{ACCOUNT_ID_A_MAIN}}",
    "items": [
        {
            "destinationAccountId": "{{ACCOUNT_ID_B_MAIN}}",
            "amount": "100.00"
        }
    ]
}'
```

**Example Response (`400 Bad Request`):**
```json
{
    "paymentOrderId": "order-id-...",
    "status": "FAILED",
    "message": "Insufficient funds"
}
```

---

## 4. Reporting Flow
This section covers endpoints for retrieving historical data and reports. Use `{{TOKEN_A}}` for authentication.

### 4.1 Get All Payment Orders
Retrieve a list of all payment orders initiated by User A (both successful and failed).

**Endpoint:** `GET /api/v1/reports/orders`

**cURL Request:**
```bash
curl --location 'http://localhost:8080/api/v1/reports/orders' \
--header 'Authorization: Bearer {{TOKEN_A}}'
```

**Example Response (`200 OK`):**
```json
[
    {
        "paymentOrderId": "order-id-...",
        "idempotencyKey": "cccccccc-dddd-eeee-ffff-111111111111",
        "sourceAccountId": "{{ACCOUNT_ID_A_MAIN}}",
        "totalAmount": 100.00,
        "currency": "EUR",
        "status": "FAILED",
        "createdAt": "...",
        "updatedAt": "...",
        "items": null
    },
    {
        "paymentOrderId": "{{ORDER_ID_1}}",
        "idempotencyKey": "aaaaaaaa-bbbb-cccc-dddd-123456789012",
        "sourceAccountId": "{{ACCOUNT_ID_A_MAIN}}",
        "totalAmount": 12.50,
        "currency": "EUR",
        "status": "COMPLETED",
        "createdAt": "...",
        "updatedAt": "...",
        "items": null
    }
]
```

---

### 4.2 Get Payment Order Details
Retrieve the full details for the successful transfer `{{ORDER_ID_1}}`, including its line items.

**Endpoint:** `GET /api/v1/reports/orders/{orderId}`

**cURL Request:**
```bash
curl --location 'http://localhost:8080/api/v1/reports/orders/{{ORDER_ID_1}}' \
--header 'Authorization: Bearer {{TOKEN_A}}'
```

**Example Response (`200 OK`):**
```json
{
    "paymentOrderId": "{{ORDER_ID_1}}",
    "idempotencyKey": "aaaaaaaa-bbbb-cccc-dddd-123456789012",
    "sourceAccountId": "{{ACCOUNT_ID_A_MAIN}}",
    "totalAmount": 12.50,
    "currency": "EUR",
    "status": "COMPLETED",
    "createdAt": "...",
    "updatedAt": "...",
    "items": [
        {
            "itemId": "...",
            "destinationAccountId": "{{ACCOUNT_ID_B_MAIN}}",
            "amount": 12.50,
            "status": "SUCCESS",
            "failureReason": null
        }
    ]
}
```

---

### 4.3 Get Account Transaction History
Retrieve the transaction ledger for `{{ACCOUNT_ID_A_MAIN}}`.

**Endpoint:** `GET /api/v1/reports/accounts/{accountId}/transactions`

**cURL Request:**
```bash
curl --location 'http://localhost:8080/api/v1/reports/accounts/{{ACCOUNT_ID_A_MAIN}}/transactions' \
--header 'Authorization: Bearer {{TOKEN_A}}'
```

**Example Response (`200 OK`):**
```json
[
    {
        "transactionId": "...",
        "sourceAccountId": "{{ACCOUNT_ID_A_MAIN}}",
        "destinationAccountId": "{{ACCOUNT_ID_B_MAIN}}",
        "amount": 12.50,
        "currency": "EUR",
        "createdAt": "...",
        "paymentOrderId": "{{ORDER_ID_1}}"
    }
]
```

---

### 4.4 Get System Failures
Check the Dead-Letter Queue (DLQ) for any unprocessed or failed system events. This should be empty in a healthy system.

**Endpoint:** `GET /api/v1/reports/failures`

**cURL Request:**
```bash
curl --location 'http://localhost:8080/api/v1/reports/failures' \
--header 'Authorization: Bearer {{TOKEN_A}}'
```

**Example Response (`200 OK`):**
```json
[]
```
