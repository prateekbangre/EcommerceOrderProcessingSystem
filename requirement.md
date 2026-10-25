# E-Commerce Order Processing System

A production-ready backend implementation for an **E-Commerce Order Processing System** built using **Java 21**, **Spring Boot 3.x**, and **Spring Batch/Scheduler**. This project fulfills the requirements of the backend system architecture and demonstrates clean code practices, automated background jobs, and robust error handling.

---

## 🏗️ Technical Architecture & Design Decisions

### 1. Technology Stack
*   **Java 21**: Leveraging modern language features like record classes for immutable DTOs and enhanced switch expressions.
*   **Spring Boot 3.x**: Rapid microservice framework setup with embedded Apache Tomcat.
*   **Spring Data JPA / Hibernate**: Clean abstractions over object-relational mapping.
*   **H2 Database**: An in-memory database configuration ensuring zero infrastructure friction during immediate local testing.
*   **Spring Scheduler**: Drives the 5-minute automated processing job without adding infrastructure overhead (e.g., Quartz/RabbitMQ) for this scope.

### 2. Domain & Entity States
The system models a strict, non-reversible lifecycle state machine for orders using an enum:
`PENDING ──> PROCESSING ──> SHIPPED ──> DELIVERED`
*   **Cancellation Limit**: Orders can only be explicitly transitioned to `CANCELLED` if they are explicitly in the `PENDING` state.

---

## 🛠️ AI Assistance Report (Cursor AI & ChatGPT Usage)

As encouraged by the assignment constraints, **Cursor AI** and **ChatGPT (GPT-4o)** were heavily utilized across the development lifecycle. Below is a comprehensive breakdown of the interactions, challenges caught, and remediation strategies deployed.

### 1. Code Generation Framework
*   **Used AI for**: Instantiating boilerplate Spring Boot structures, mapping JPA relationships (`@OneToMany`, `@ManyToOne`) with explicit cascade types, and writing automated Unit/Integration test setups.
*   **Outcome**: Saved significant developer hours on baseline structural boilerplate, allowing focus on concurrency and domain logic boundaries.

### 2. Critical Edge Cases & AI Issues Discovered

| Component / Task | Issue Identified in AI Code | Technical Implication | Resolution / Fix Applied |
| :--- | :--- | :--- | :--- |
| **JPA Entity Mapping** | AI generated standard bi-directional mappings but forgot `cascade = CascadeType.ALL` and `orphanRemoval = true` on the `Order -> OrderItem` relationship. | Saving a new order resulted in `TransientObjectException` because child items were not saved automatically. | Manually updated the mapping to explicitly cascade all entity state operations and added a helper method to sync both sides of the relationship safely. |
| **Background Scheduler** | AI proposed a generic `@Scheduled(cron = "0 */5 * * * *")` updating orders matching a status string via standard repository `saveAll()`. | Potential **Race Conditions**: If a customer cancels a `PENDING` order right when the scheduler fetches it, the scheduler could overwrite it back to `PROCESSING`. | Refactored to an explicit atomic custom JPQL update query (`@Modifying` + `@Query`) updating states directly in the database under transaction blocks. |
| **State Mutability** | AI exposed internal structural domain layers directly to the REST controller payloads. | Violation of encapsulation and data leakage patterns. | Created strict `dto` classes using Java `record` structures for immutability and decoupled them from JPA lifecycle entities. |

---

## 🚀 Getting Started

### Prerequisites
*   Java Development Kit (JDK) 21 or higher
*   Apache Maven 3.9+ or use the wrapper (`./mvnw`)

### Running the Application Locally
1. Clone the repository and navigate to the project directory.
2. Compile and run the application via Maven:
   ```bash
   ./mvnw spring-boot:run
   ```
3. The server starts up locally on `http://localhost:8080`.
4. Access the embedded **H2 Console** for database browsing at `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:orderdb`, Username: `sa`, Password: *leave blank*).

---

## 🛣️ API Endpoints & Verification

### 1. Create an Order
*   **Endpoint**: `POST /api/orders`
*   **Payload Example**:
    ```json
    {
      "customerEmail": "candidate@example.com",
      "items": [
        { "productSku": "PROD-101", "quantity": 2, "unitPrice": 29.99 },
        { "productSku": "PROD-202", "quantity": 1, "unitPrice": 49.99 }
      ]
    }
    ```

### 2. Retrieve Order Details
*   **Endpoint**: `GET /api/orders/{id}`

### 3. List & Filter Orders
*   **Endpoint**: `GET /api/orders?status=PENDING` *(Status parameter is completely optional)*

### 4. Cancel an Order
*   **Endpoint**: `POST /api/orders/{id}/cancel`
*   **Note**: Returns a `400 Bad Request` safety exception if the target order has transitioned past `PENDING`.

---

## 🧪 Automated Verification
To run the automated suite testing state constraints, background transitions, and failure matrices:
```bash
./mvnw test
```
