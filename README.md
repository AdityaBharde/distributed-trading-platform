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

Services are reached at fixed, configurable addresses (no service registry) and communicate asynchronously via **Apache Kafka** for temporal decoupling and durability.

Each service owns its own **PostgreSQL** database instance — no shared databases between services (strict bounded context).

---

## Services

| Service | Responsibility |
|---|---|
| `api-gateway` | Single entry point. Routes requests, applies cross-cutting concerns (auth, rate limiting) |
| `trading-service` | Order management + matching engine (two modules in one deployable). Persists orders, publishes `OrderCreatedEvent` to Kafka, consumes it and matches buy/sell orders |
| `market-service` | Real-time and historical market data (prices, symbols) |
| `analytics-service` | Indicators, screener, alerts (skeleton for now) |
| `trade-execution-service` | Handles post-match trade settlement and execution lifecycle |
| `user-service` | Manages user accounts, authentication, and portfolios |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.x |
| API Gateway | Spring Cloud Gateway |
| Service addresses | Configuration (environment variables); Docker DNS in Compose |
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

**Why no service registry?**
With one instance of each service, fixed addresses are simpler and do not depend on the machine's network. Docker Compose and Kubernetes provide DNS names; the gateway reads each address from an environment variable.

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
// Published by the order module → consumed by the matching module (both inside trading-service)
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
2. API Gateway routes to trading-service
3. trading-service (order module) validates and persists the order (PostgreSQL)
4. trading-service publishes OrderCreatedEvent → Kafka topic: order-events
5. trading-service (matching module) consumes OrderCreatedEvent
6. Matching engine attempts to pair with opposing order
7. On match: publishes TradeExecutedEvent → Kafka topic: trade-events
8. trade-execution-service consumes TradeExecutedEvent, settles the trade
```

---

## Project Phases

- [x] **Phase 1 — Service Foundation**: All six microservices scaffolded with domain models, JPA entities, repositories, and REST controllers. API Gateway and Eureka registry operational.
- [ ] **Phase 2 — Kafka Integration** *(in progress)*: Async event-driven communication between services. `OrderCreatedEvent` published and consumed inside trading-service (order → matching modules).
- [ ] **Phase 3 — Docker & Kubernetes**: Full containerization with Docker Compose for local dev; Kubernetes manifests for orchestration, scaling, and resilience.
- [ ] **Phase 4 — Load Testing & Observability**: Performance benchmarking under simulated trading load. Observability stack (Prometheus, Grafana, distributed tracing).

---

## Getting Started

### Prerequisites
- Java 17+
- Maven 3.8+
- Docker & Docker Compose

### Running Locally

**1. Set your passwords, then start infrastructure (Kafka (KRaft), PostgreSQL+TimescaleDB+pgvector, Redis)**
```bash
cd backend/infrastructure/docker
cp .env.example .env      # then edit .env and set your own passwords (git-ignored)
docker compose up -d
```
When running a service outside Docker, export `DB_PASSWORD` (same value as `POSTGRES_PASSWORD`) first. Postgres is published on host port **5434** (not 5432, which a native Postgres often occupies); services inside Docker still use `postgres:5432`.

**2. Start each service** (in separate terminals)
```bash
cd services/trading-service && mvn spring-boot:run
cd services/market-service && mvn spring-boot:run
cd services/analytics-service && mvn spring-boot:run
cd services/api-gateway && mvn spring-boot:run
cd services/identity-service && mvn spring-boot:run   # accounts, tokens, profiles (needs Postgres and Redis)
```

**Frontend (Next.js)**
```bash
cd frontend/quantanatara
cp .env.example .env.local   # set BFF_SHARED_SECRET; export the same value before starting the gateway
npm install && npm run dev   # http://localhost:3000  (register, sign in, profile)
```
The browser only talks to the Next.js server, which keeps the tokens in httpOnly cookies and calls the gateway. The gateway listens on port 8090, so set `GATEWAY_URL=http://localhost:8090`. Press Ctrl+K to search stocks: `market-service` (needs Postgres and `DB_PASSWORD`) loads the NSE list on its first start. On a database volume created before F2, create it once with `docker exec postgres psql -U postgres -c "CREATE DATABASE marketdb;"`.

**3. API Gateway entry point**
```
http://localhost:8090
```

---

## Distributed Systems Concepts Demonstrated

- **Event-driven architecture** — services communicate through durable, asynchronous Kafka events rather than blocking HTTP calls
- **Temporal decoupling** — producers and consumers operate independently; a slow consumer doesn't block a producer
- **Bounded contexts** — each service owns its schema; no cross-service database access
- **Configuration over discovery** — service addresses come from environment variables
- **SAGA pattern** — distributed transaction management across services without a global transaction manager *(planned)*
- **Idempotency** — event consumers designed to safely handle duplicate delivery *(planned)*
- **Circuit breaker** — fault isolation using Resilience4j to prevent cascading failures *(planned)*

---

## Repository Structure

```
distributed-trading-platform/
├── infrastructure/             # Docker Compose, database init, Kubernetes manifests
├── services/
│   ├── api-gateway/            # Spring Cloud Gateway
│   ├── trading-service/        # Orders (Kafka producer) + matching engine (Kafka consumer)
│   ├── market-service/         # Market data
│   ├── analytics-service/      # Indicators, screener, alerts (skeleton)
│   ├── trade-execution-service/ # Trade settlement
│   └── identity-service/       # Users, auth (JWT) and profiles
└── docker-compose.yml          # Kafka (KRaft), PostgreSQL+TimescaleDB+pgvector, Redis
```

---

## About

This project is being built as a deep-dive into production-grade backend engineering — not a tutorial follow-along, but a ground-up design exercise in distributed systems, event-driven architecture, and the specific challenges of high-throughput financial platforms (ordering guarantees, consistency, latency).

Built by [Aditya Bharde](https://github.com/AdityaBharde) — CS undergrad interested in backend systems and distributed computing.
