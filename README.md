# NLB Payment Transfer Service

![Java](https://img.shields.io/badge/Java-21-blue.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)
![Maven](https://img.shields.io/badge/Build-Maven-red.svg)
![Database](https://img.shields.io/badge/Database-PostgreSQL-blue.svg)

This is a REST API service for a digital banking platform, built with Spring Boot. It implements core functionalities for user registration, account management, and executing idempotent batch money transfers between accounts on the platform.

## Table of Contents

- [Features](#features)
- [Technologies and Architecture](#technologies-and-architecture)
- [Running the Project](#running-the-project)
- [Testing](#testing)
- [API Usage](#api-usage)
- [Key Design Decisions](#key-design-decisions)
- [Next Steps (Future Improvements)](#next-steps-future-improvements)

## Features

-   **Registration and Authentication**: User creation and "lightweight" JWT token issuing.
-   **Account Management**:
    -   Creating multiple accounts per user (currently EUR only).
    -   Deposit (`POST /api/v1/accounts/{id}/deposits`) and withdrawal (`POST /api/v1/accounts/{id}/withdrawals`) of funds.
    -   Deactivation of empty accounts (`PATCH /api/v1/accounts/{id}/status`).
    -   Closing accounts (`DELETE /api/v1/accounts/{id}`).
    -   View all owned accounts.
-   **Batch Transfers**:
    -   Sending money from a single source account to multiple destination accounts in one transaction.
    -   **Idempotency**: Guarantees that repeated requests (with the same `Idempotency-Key`) will not be processed twice.
    -   **Transactional Safety**: Uses pessimistic locking (`SELECT ... FOR UPDATE`) to prevent race conditions.
    -   **Validation**: Checks account ownership, account status, and sufficient funds.
-   **Reporting and Audit**:
    -   **Ledger**: Every successful transfer is recorded in the `transactions` table as an immutable record.
    -   **Request Status**: The `payment_orders` table tracks the status of the original request (e.g., `FAILED` due to insufficient funds).
    -   **Reporting API**: Endpoints for retrieving transfer history and account statements.
-   **Migrations and Data**:
    -   **Liquibase**: Manages the database schema (e.g., `V1_Schema`).
    -   **Seed Data**: Uses a Liquibase `context="dev"` to automatically populate the database with development data. This includes 2 pre-registered users, 3 accounts (with funds), and 1 sample transaction.

## Technologies and Architecture

### Technologies Used

-   **Java 21**
-   **Spring Boot 3.x**
    -   `Spring Web`: For the REST API.
    -   `Spring Data JPA (Hibernate)`: For database access.
    -   `Spring Security (OAuth2 Resource Server)`: For JWT authentication.
-   **PostgreSQL**: As the relational database.
-   **Liquibase**: For database schema management and migration.
-   **Lombok**: To reduce boilerplate code.
-   **Testcontainers**: For integration tests against a real database instance.
-   **Mockito & JUnit 5**: For unit tests.

### Architecture

The project is divided into a multi-module Maven structure for a clear Separation of Concerns.

-   `nlb-payment/` (Root POM)
    -   `shared/`: Contains shared classes (e.g., `BaseEntity`, `BusinessValidationException`, `Currency` enum).
    -   `user/`: User-related domain (`User`, `Account`), repositories, and services (`AuthService`, `AccountService`).
    -   `transactions/`: Transfer-related domain (`PaymentOrder`, `Transaction`), repositories, and services (`TransferBatchService`, `ReportService`).
    -   `security-devjwt/`: Configuration for Spring Security and JWT (e.g., `SecurityConfig`, `DevJwtConfig`).
    -   `web/`: Contains the entire HTTP layer (Controllers, DTOs, `GlobalExceptionHandler`).
    -   `infrastructure/`: The main runnable class (`@SpringBootApplication`), the `pom.xml` that assembles all modules, and resources (e.g., `application.properties`, Liquibase changelogs).

## Running the Project

### Prerequisites

-   Java 21 (or newer)
-   Maven 3.8 (or newer)
-   Docker Desktop (or Docker Engine)

### Local Quickstart (Development)

1.  **Start the database**:
    From the project's root folder, run PostgreSQL in Docker:
    ```sh
    docker-compose up -d
    ```

2.  **Build the project**:
    It is necessary to run `mvn clean install` from the root folder to build all modules.
    ```sh
    mvn clean install
    ```

3.  **Run the application**:
    The application is run from the `infrastructure` module:
    ```sh
    mvn -pl infrastructure spring-boot:run
    ```

The application will be available at `http://localhost:8080`.

The database will be automatically created (`V1-schema.xml`) and seeded with development data (`V2-dev-seed.xml`) thanks to the Liquibase `dev` context being enabled in `application.properties`.

## Testing

The project includes both unit and integration tests. The integration tests use Testcontainers and will automatically spin up their own temporary PostgreSQL container.

To run all tests, from the root folder:
```sh
mvn test
```

## API Usage

For a complete list of all endpoints, example requests, and responses, please see the file:
`Postman_Tests.md`

## Key Design Decisions

-   **Idempotency (`Idempotency-Key`)**: Instead of relying on the client, we use a `UNIQUE` constraint in the database on `payment_orders(idempotency_key)`. This provides atomic protection against duplicate transfers, even in race condition scenarios.

-   **Pessimistic Locking (`SELECT ... FOR UPDATE`)**: During a transfer, all involved accounts (source and destinations) are locked. This was chosen over optimistic locking (`@Version`) because, in financial transactions, integrity and safety are more critical than throughput. Deadlocks are avoided by sorting account IDs before locking.

-   **Ledger vs. Intent (`Transaction` vs `PaymentOrder`)**: The application clearly separates the user's intent (`PaymentOrder`) from the actual execution (`Transaction`). A `PaymentOrder` can have a `FAILED` status (e.g., insufficient funds), but the `Transaction` table only records successful transfers, serving as an immutable ledger.

-   **Liquibase Contexts**: We use Liquibase contexts to separate schema creation (`V1-schema.xml`) from data seeding (`V2-dev-seed.xml`). This allows the `dev` environment to start with data, while integration tests run against a perfectly clean schema.

## Next Steps (Future Improvements)

A list of potential enhancements and future features:

-   [ ] **Multi-Currency**: Expand the system to support transfers between different currencies, integrating with an external exchange rate API.
-   [ ] **Asynchronous Retry**: Implement a retry mechanism (e.g., using Spring `@Retryable` or a message queue like RabbitMQ) for system errors logged in the `integration_failures` table.
-   [ ] **Admin Role**: Secure administrative endpoints (like `GET /api/v1/reports/failures`) so they are only accessible to users with an `ADMIN` role.
-   [ ] **Notifications**: Send email notifications to users after a successful transfer or registration.
-   [ ] **Fees**: Introduce logic for charging service fees on transfers.

