# Distributed Real-Time Trading Platform

A production-grade distributed system built to demonstrate real-world backend engineering — microservices architecture, event-driven communication, and distributed systems design patterns applied to a high-throughput trading domain.

> Built with Java 17 · Spring Boot 3 · Apache Kafka · PostgreSQL · Docker · Kubernetes (planned)

---

## Architecture Overview

```
Client Requests
      │
      ▼
┌─────────────────┐
│   API Gateway   │  ← Spring Cloud Gateway (routing, auth filter)
└────────┬────────┘
         │
   ┌─────┼──────────────────────┐
   ▼     ▼                      ▼
Order   Market Data          User
Service  Service             Service
   │
   ▼
Kafka Event Bus (OrderCreatedEvent)
   │
   ▼
Matching Engine Service
   │
   ▼
Trade Execution Service
   │
   ▼
Kafka Event Bus (TradeExecutedEvent)
```

All services register with **Eureka Service Discovery** and communicate asynchronously via **Apache Kafka** for temporal decoupling and durability.

Each service owns its own **PostgreSQL** database instance — no shared databases between services (strict bounded context).

---

## Services

| Service | Responsibility |
|---|---|
| `api-gateway` | Single entry point. Routes requests, applies cross-cutting concerns (auth, rate limiting) |
| `order-service` | Accepts and persists trade orders. Publishes `OrderCreatedEvent` to Kafka |
| `matching-engine-service` | Consumes order events, matches buy/sell orders, publishes `TradeExecutedEvent` |
| `market-data-service` | Manages real-time and historical market data (prices, symbols) |
| `trade-execution-service` | Handles post-match trade settlement and execution lifecycle |
| `user-service` | Manages user accounts, authentication, and portfolios |
| `infrastructure/service-registry` | Eureka server — all services register and discover each other here |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.x |
| API Gateway | Spring Cloud Gateway |
| Service Discovery | Netflix Eureka (Spring Cloud) |
| Message Broker | Apache Kafka |
| Database | PostgreSQL (per-service instances) |
| Containerization | Docker & Docker Compose |
| Orchestration | Kubernetes *(planned)* |
| Build Tool | Maven |

---

## Key Engineering Decisions

**Why Kafka instead of synchronous REST between services?**
Synchronous REST creates tight coupling — if the matching engine is slow or down, the order service blocks. Kafka provides temporal decoupling: the order service publishes an event and moves on, while the matching engine processes at its own pace. Events are also durable and replayable, which is critical in a financial system.

**Why per-service databases?**
A shared database would mean services can step on each other's data and schema — defeating the purpose of microservices. Each service owns its data and exposes it only through its API.

**Why Eureka for service discovery?**
In a distributed system, services can't have hardcoded addresses. Eureka lets every service register itself and discover others dynamically, which is essential when running multiple instances or doing rolling deploys.

---

## Domain Models

### Order
```java
// Core fields
String orderId       // UUID
String userId
String symbol        // e.g., "AAPL", "BTC-USD"
OrderSide side       // BUY | SELL
OrderType type       // MARKET | LIMIT
BigDecimal price     // null for MARKET orders
Integer quantity
OrderStatus status   // PENDING | MATCHED | EXECUTED | CANCELLED
Instant createdAt
```

### OrderCreatedEvent (Kafka Message)
```java
// Published by order-service → consumed by matching-engine-service
String orderId
String symbol
OrderSide side
OrderType type
BigDecimal price
Integer quantity
Instant createdAt
```

---

## Event Flow

```
1. Client sends POST /api/orders
2. API Gateway routes to order-service
3. order-service validates and persists the order (PostgreSQL)
4. order-service publishes OrderCreatedEvent → Kafka topic: order-events
5. matching-engine-service consumes OrderCreatedEvent
6. Matching engine attempts to pair with opposing order
7. On match: publishes TradeExecutedEvent → Kafka topic: trade-events
8. trade-execution-service consumes TradeExecutedEvent, settles the trade
```

---

## Project Phases

- [x] **Phase 1 — Service Foundation**: All six microservices scaffolded with domain models, JPA entities, repositories, and REST controllers. API Gateway and Eureka registry operational.
- [ ] **Phase 2 — Kafka Integration** *(in progress)*: Async event-driven communication between services. `OrderCreatedEvent` published by order-service, consumed by matching-engine-service.
- [ ] **Phase 3 — Docker & Kubernetes**: Full containerization with Docker Compose for local dev; Kubernetes manifests for orchestration, scaling, and resilience.
- [ ] **Phase 4 — Load Testing & Observability**: Performance benchmarking under simulated trading load. Observability stack (Prometheus, Grafana, distributed tracing).

---

## Getting Started

### Prerequisites
- Java 17+
- Maven 3.8+
- Docker & Docker Compose

### Running Locally

**1. Start infrastructure (Kafka, Zookeeper, PostgreSQL)**
```bash
docker-compose up -d
```

**2. Start the service registry (Eureka)**
```bash
cd infrastructure/service-registry
mvn spring-boot:run
```

**3. Start each service** (in separate terminals)
```bash
cd services/order-service && mvn spring-boot:run
cd services/market-data-service && mvn spring-boot:run
cd services/matching-engine-service && mvn spring-boot:run
cd services/api-gateway && mvn spring-boot:run
```

**4. Eureka Dashboard**
```
http://localhost:8761
```

**5. API Gateway entry point**
```
http://localhost:8080
```

---

## Distributed Systems Concepts Demonstrated

- **Event-driven architecture** — services communicate through durable, asynchronous Kafka events rather than blocking HTTP calls
- **Temporal decoupling** — producers and consumers operate independently; a slow consumer doesn't block a producer
- **Bounded contexts** — each service owns its schema; no cross-service database access
- **Service discovery** — dynamic registration and lookup via Eureka; no hardcoded service URLs
- **SAGA pattern** — distributed transaction management across services without a global transaction manager *(planned)*
- **Idempotency** — event consumers designed to safely handle duplicate delivery *(planned)*
- **Circuit breaker** — fault isolation using Resilience4j to prevent cascading failures *(planned)*

---

## Repository Structure

```
distributed-trading-platform/
├── infrastructure/
│   └── service-registry/       # Eureka discovery server
├── services/
│   ├── api-gateway/            # Spring Cloud Gateway
│   ├── order-service/          # Order management + Kafka producer
│   ├── matching-engine-service/ # Order matching + Kafka consumer
│   ├── market-data-service/    # Market data management
│   ├── trade-execution-service/ # Trade settlement
│   └── user-service/           # User accounts and auth
└── docker-compose.yml          # Kafka, Zookeeper, PostgreSQL
```

---

## About

This project is being built as a deep-dive into production-grade backend engineering — not a tutorial follow-along, but a ground-up design exercise in distributed systems, event-driven architecture, and the specific challenges of high-throughput financial platforms (ordering guarantees, consistency, latency).

Built by [Aditya Bharde](https://github.com/AdityaBharde) — CS undergrad interested in backend systems and distributed computing.
