# mightyETL - Enterprise ETL and CDC Platform

A microservices-based platform for real-time Change Data Capture (CDC) and bounded Extract-Transform-Load (ETL) operations.

> **Formerly xtrmETL.** Product branding is **mightyETL**. Java packages (`com.xtrmetl.*`), Maven coordinates, and some runtime defaults still use the legacy `xtrmetl` identifier for binary compatibility; see [docs/rebrand-name-matrix.md](docs/rebrand-name-matrix.md).

## 🎯 Overview

mightyETL provides enterprise-grade capabilities for:

- **Real-time Change Data Capture**: Monitor PostgreSQL databases and capture all data changes (Postgres → Kafka today; multi-source roadmap in [docs/cdc/any-to-any-cdc.md](docs/cdc/any-to-any-cdc.md))
- **Bounded transactional ETL**: Enforce UTF-8 payload and record limits, prevalidate the complete batch, transform deterministically, and commit accepted records in one transaction
- **Event Streaming**: Publish changes to Kafka for downstream processing
- **Warehouse / BI targets (scaffold)**: Databricks, Snowflake, and Qlik Sense connector contracts — see [docs/connectors/](docs/connectors/)
- **Secure Access**: JWT-based authentication with role-based access control

## ✅ Supported today (honest)

| Capability | Status | Notes |
|:-----------|:-------|:------|
| Product name **mightyETL** | **Docs / APIs / POM name** | Java packages & Maven `artifactId` still `xtrmetl` / `xtrmETL` — [rebrand matrix](docs/rebrand-name-matrix.md) |
| Config prefix | **Dual-read** | Prefer `mightyetl.*`; legacy `xtrmetl.*` still binds |
| CDC capture | **PostgreSQL → Kafka** (Debezium embedded) | Ops: `GET /api/cdc/status`, slot lag, `cdcEngine` health — [ops-and-reliability](docs/cdc/ops-and-reliability.md) |
| CDC replica apply | **Optional** Postgres JDBC | Tables with `(id, data)` shape (`xtrmetl.replica.tables`) |
| Any-to-any CDC | **Scaffold** | Source/target SPI + factory; MySQL/SQL Server **not live** |
| ETL load | **PostgreSQL** via `POST /api/etl/process` | Bounded, fully prevalidated, transaction-scoped request batches — [runbook](docs/etl/bounded-atomic-batches.md) |
| Databricks / Snowflake / Qlik | **Scaffold** (not production) | SPI + YAML binding + required-key validation + catalog; `write()` always refused — [docs/connectors/](docs/connectors/) |
| Progress tracker | [docs/mightyETL-product-upgrade-progress.md](docs/mightyETL-product-upgrade-progress.md) | |

Do **not** market multi-cloud warehouse CDC or BI loaders as production-ready until the matrix rows above say Supported.

## 🏗️ Architecture

```text
┌─────────────────────────────────────────────────────────────┐
│                      API Gateway (Zuul)                      │
│                         Port 8080                            │
└─────────────┬─────────────────────────┬────────────────────┘
              │                         │
              v                         v
    ┌──────────────────┐      ┌──────────────────┐
    │   ETL Service    │      │   CDC Service    │
    │    Port 8000     │      │    Port 8001     │
    └────────┬─────────┘      └────────┬─────────┘
             │                         │
             v                         v
    ┌──────────────────┐      ┌──────────────────┐
    │   PostgreSQL     │      │  Kafka + Debezium│
    │   (Target DB)    │      │  (Event Stream)  │
    └──────────────────┘      └──────────────────┘

┌───────────────────┐         ┌──────────────────┐
│  Eureka Server    │         │   Config Server  │
│   Port 8761       │         │   (Future)       │
└───────────────────┘         └──────────────────┘
```

## 🚀 Quick Start

### Prerequisites

- **Java 25**
- **Maven 3.6+**
- **PostgreSQL 12+** with logical replication enabled
- **Apache Kafka** (optional, for CDC)
- **Docker** (optional, for containerized deployment)

### Docker Compose

Spin up PostgreSQL (primary + replica), Kafka, Zipkin, Eureka, and the application services:

```bash
docker compose up --build
```

Podman을 사용한다면 `docs/podman.md`를 참고하세요.

Note: `docker-compose.yml` includes development defaults for database credentials. Override them via `.env`
(e.g. `POSTGRES_PASSWORD`, `REPLICA_POSTGRES_PASSWORD`) and use secrets for production deployments.

### PostgreSQL Configuration

Enable logical replication in PostgreSQL:

```bash
# In postgresql.conf
wal_level = logical
max_replication_slots = 4
max_wal_senders = 4
```

### Environment Variables

Set the following environment variables:

```bash
export PGHOST=localhost
export PGPORT=5432
export PGUSER=your_username
export PGPASSWORD=your_password
export PGDATABASE=your_database
```

Optional ETL admission limits:

```bash
# Defaults: 1 MiB and 1,000 records. See docs/etl/bounded-atomic-batches.md.
export ETL_MAX_PAYLOAD_BYTES=1048576
export ETL_MAX_BATCH_RECORDS=1000
```

The service rejects a request before any JDBC call when either limit is exceeded or any record is invalid. Keep the gateway/ingress body-size limit aligned: the service-level UTF-8 check is defense in depth after the MVC stack has materialized the request body.

Optional (CDC / Debezium):

```bash
# Whether Debezium emits schema change events (`*.schema-changes` topics).
# If you set this to false, no DDL events will be produced/consumed.
export CDC_INCLUDE_SCHEMA_CHANGES=true
```

Optional: enable CDC-based replication to a secondary PostgreSQL (database redundancy/replication):

```bash
export REPLICA_ENABLED=true
export REPLICA_PGHOST=localhost
export REPLICA_PGPORT=5433
export REPLICA_PGUSER=your_username
export REPLICA_PGPASSWORD=your_password
export REPLICA_PGDATABASE=your_database

# Whether the CDC service applies DDL events (`*.schema-changes`) on the replica DB.
# Default: false. Set to true to opt-in to applying DDL on the replica.
export REPLICA_DDL_ENABLED=true
```

If you don’t need replication, set `REPLICA_ENABLED=false` and the CDC service will skip replica applies.

Security note: enabling `REPLICA_DDL_ENABLED` can apply destructive DDL (e.g. `DROP`, `TRUNCATE`) on the replica.
For production, keep the secure default `REPLICA_DDL_VALIDATION_MODE=whitelist` and tighten the allowed prefixes for the deployment.

### Build

```bash
# Build all modules
./mvnw clean install

# Or build individual services
cd etl-service && ../mvnw clean package
cd cdc-service && ../mvnw clean package
```

### Run Services

Start services in the following order:

```bash
# 1. Start Eureka Server (Service Discovery)
cd eureka-server
../mvnw spring-boot:run

# 2. Start CDC Service
cd cdc-service
../mvnw spring-boot:run

# 3. Start ETL Service
cd etl-service
../mvnw spring-boot:run

# 4. Start Zuul Gateway
cd zuul-gateway
../mvnw spring-boot:run
```

Access the gateway at: `http://localhost:8080`

## 📚 Services

### CDC Service (Port 8001)

Captures database changes in real-time using Debezium.

**Key Features:**

- PostgreSQL change data capture
- Kafka event publishing
- Real-time status and replication-slot lag monitoring
- Minimal source database impact

**API Endpoints:**

- `POST /api/cdc/start` - Start CDC process
- `POST /api/cdc/stop` - Stop CDC process
- `GET /api/cdc/status` - Read operator-safe runtime status

### ETL Service (Port 8000)

Processes and transforms bounded JSON batches with configurable business rules.

**Key Features:**

- UTF-8 payload and record-count admission limits
- Full-batch structural validation and transformation before the first database write
- One Spring transaction for every accepted request batch
- Retry limited to transient database failures
- Delimiter-safe, locale-independent transformations with deterministic decimal formatting

**API Endpoints:**

- `POST /api/etl/process` - Process one bounded atomic batch
- `GET /api/etl/connectors` - Inspect target connector capabilities and runtime state

**Transformation Rules:**

- `NAME` fields: locale-independent uppercase
- `EMAIL` fields: locale-independent lowercase
- `AMOUNT` fields: `BigDecimal` with two decimal places and `HALF_UP` rounding
- All other field values are preserved without comma/colon splitting

### Zuul Gateway (Port 8080)

API Gateway with authentication and routing.

**Routes:**

- `/etl/**` → ETL Service
- `/cdc/**` → CDC Service
- `/auth/**` → Authentication endpoints

### Eureka Server (Port 8761)

Service discovery and registration.

**Dashboard:** `http://localhost:8761`

### Config Server (Port 8888)

Centralized configuration service scaffold. The current runtime still relies primarily on local YAML and environment variables.

### Zipkin (Port 9412)

Receives distributed tracing spans from the services.

## 🔐 Authentication

All API endpoints (except `/auth/**`) require JWT authentication.

### 1. Register a User

```bash
curl -X POST http://localhost:8080/auth/signup \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "testpassword"
  }'
```

### 2. Login

```bash
curl -X POST http://localhost:8080/auth/signin \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "testpassword"
  }'
```

Response:

```json
{
  "token": "EXAMPLE_JWT_TOKEN_TRUNCATED"
}
```

### 3. Use Token in Requests

```bash
curl -X POST http://localhost:8080/api/etl/process \
  -H "Authorization: Bearer YOUR_TOKEN_HERE" \
  -H "Content-Type: application/json" \
  -d '[
    {
      "id": "record_alpha",
      "name": "john doe",
      "email": "JOHN@EXAMPLE.COM",
      "amount": "1234.567"
    }
  ]'
```

## 📖 API Examples

### Start CDC Capture

```bash
curl -X POST http://localhost:8080/api/cdc/start \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### Process ETL Data

```bash
curl -X POST http://localhost:8080/api/etl/process \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '[
    {
      "id": "record_alpha",
      "name": "jane smith",
      "email": "JANE@COMPANY.COM",
      "amount": "999.99"
    },
    {
      "id": "record_beta",
      "name": "bob jones",
      "email": "BOB@COMPANY.COM",
      "amount": "1500"
    }
  ]'
```

Expected transformations:

- Names: `JANE SMITH`, `BOB JONES`
- Emails: `jane@company.com`, `bob@company.com`
- Amounts: `999.99`, `1500.00`
- Response: `Processed: record_alpha` and `Processed: record_beta`, in input order

## 🗄️ Database Setup

### Create Security Tables

The authentication schema below reflects the legacy application contract and is retained for compatibility:

```sql
CREATE TABLE roles (
    id SERIAL PRIMARY KEY,
    name VARCHAR(20) UNIQUE NOT NULL
);

CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(100) NOT NULL
);

CREATE TABLE user_roles (
    user_id INTEGER NOT NULL,
    role_id INTEGER NOT NULL,
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (role_id) REFERENCES roles(id)
);

INSERT INTO roles (name) VALUES ('ROLE_USER'), ('ROLE_ADMIN');
```

### Create ETL Target Table

Use a descriptive nonnumeric primary key while retaining the service’s existing `data` insert contract:

```sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE processed_data (
    processed_data_key UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    data TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## 🧪 Testing

### Run All Tests

```bash
./mvnw test
```

### Billing-Independent Checks (Local)

If GitHub-hosted Actions runners are unavailable, run the local check script:

```bash
./scripts/ci.sh
```

Windows (PowerShell):

```powershell
./scripts/ci.ps1
```

### Run Service-Specific Tests

```bash
./mvnw -pl etl-service test
./mvnw -pl cdc-service test
```

### Test Coverage

Current test coverage includes:

- `EtlServiceTest`
- `EtlServiceBatchSafetyTest`
- `EtlServiceTransactionIntegrationTest`
- `EtlControllerTest`
- `CdcServiceTest`
- `CdcControllerTest`
- `JwtAuthenticationFilterTest`

## 📊 Monitoring

### Zipkin Tracing

Start Zipkin for distributed tracing:

```bash
java -jar zipkin.jar
```

Access Zipkin UI: `http://localhost:9412`

All services are configured to send traces to Zipkin with 100% sampling rate.

### Health Checks

Spring Boot Actuator health endpoints are exposed for each service:

```text
http://localhost:8000/actuator/health  (ETL Service)
http://localhost:8001/actuator/health  (CDC Service)
http://localhost:8080/actuator/health  (Zuul Gateway)
http://localhost:8761/actuator/health  (Eureka Server)
http://localhost:8888/actuator/health  (Config Server)
```

Eureka dashboard: `http://localhost:8761`

## 🔧 Configuration

### Application Properties

Key configuration files:

- `etl-service/src/main/resources/application.yml`
- `cdc-service/src/main/resources/application.yml`
- `zuul-gateway/src/main/resources/application.yml`
- `eureka-server/src/main/resources/application.yml`

### ETL Configuration

Prefer the product namespace in external configuration:

```yaml
mightyetl:
  etl:
    max-payload-bytes: 1048576
    max-batch-records: 1000
```

The compatibility namespace `xtrmetl.etl.*` remains accepted. See [the bounded atomic batch runbook](docs/etl/bounded-atomic-batches.md) for hard ceilings, rollback semantics, and capacity guidance.

### CDC Configuration

Update table monitoring through environment configuration, for example:

```bash
export CDC_TABLE_INCLUDE_LIST=public.processed_data
```

### Kafka Configuration

Configure Kafka brokers in `cdc-service/src/main/resources/application.yml`:

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
```

## 🐳 Docker Deployment (Optional)

Build Docker images:

```bash
docker build -t mightyetl/etl-service:latest ./etl-service
docker build -t mightyetl/cdc-service:latest ./cdc-service
docker build -t mightyetl/zuul-gateway:latest ./zuul-gateway
docker build -t mightyetl/eureka-server:latest ./eureka-server
```

## 📋 Technology Stack

| Component | Technology | Version |
|:----------|:-----------|:--------|
| Runtime | Java | 25 |
| Framework | Spring Boot | 3.5.9 |
| Cloud | Spring Cloud | 2025.0.1 |
| CDC Engine | Debezium | 3.4.0.Final |
| Database | PostgreSQL | 12+ |
| Messaging | Apache Kafka | Current managed version |
| Gateway | Spring Cloud Gateway / legacy Zuul naming | Repository-defined |
| Discovery | Netflix Eureka | Repository-defined |
| Tracing | Zipkin | Repository-defined |
| Security | Spring Security + JWT | Repository-defined |
| Build | Maven Wrapper | 3.9.x |

## 🎓 Key Concepts

### Change Data Capture (CDC)

Monitors database transaction logs to capture INSERT, UPDATE, and DELETE operations in real-time without polling source tables.

### Extract-Transform-Load (ETL)

Traditional data integration pattern:

1. **Extract**: Read data from sources
2. **Transform**: Apply business rules and data cleansing
3. **Load**: Write processed data to targets

### Microservices Architecture

Independent, loosely coupled services that communicate via REST APIs and message queues. Each service can be developed, deployed, and scaled independently.

### Service Discovery

Automatic detection of service instances in the network, eliminating hardcoded service locations.

## 📝 Development

### Project Structure

```text
mightyETL/
├── pom.xml                    # Parent POM
├── README.md                  # This file
├── PRD.md                     # Product Requirements Document
├── docs/                      # Architecture, operations, connectors, roadmaps
├── etl-service/               # Transactional ETL service + target connector SPI
├── cdc-service/               # CDC monitoring service + source SPI scaffold
├── zuul-gateway/              # API Gateway
├── eureka-server/             # Service discovery
├── config-server/             # Configuration management
└── zipkin.jar                 # Distributed tracing
```

### Adding New Transformations

Edit `EtlService.transformValue` and add a locale-safe case to the switch expression:

```java
return switch (key) {
    case "YOUR_FIELD" -> yourTransformation(value);
    default -> value;
};
```

Add focused unit tests for delimiters, locale behavior, null values, size amplification, and transactional failure behavior before exposing a new transformation.

### Adding New Routes

Edit `zuul-gateway/src/main/resources/application.yml`:

```yaml
zuul:
  routes:
    your-service:
      path: /your-path/**
      serviceId: your-service-name
```

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 License

This project is proprietary software. All rights reserved.

## 🆘 Support

For issues, questions, or contributions:

- Create an issue in the repository
- Contact the development team
- Refer to [PRD.md](PRD.md) for detailed requirements
- Use [docs/etl/bounded-atomic-batches.md](docs/etl/bounded-atomic-batches.md) for ETL admission and rollback operations

## 🗺️ Roadmap

### Current (v1.0)

- CDC for PostgreSQL
- Bounded, prevalidated, transaction-scoped PostgreSQL ETL batches
- JWT authentication
- Microservices architecture
- Distributed tracing

### Planned (v2.0)

- Multi-database / any-to-any CDC (source SPI; see `docs/cdc/any-to-any-cdc.md`)
- Databricks / Snowflake / Qlik Sense loaders (target SPI; see `docs/connectors/`)
- Asynchronous ingestion jobs, idempotency keys, and durable retry/DLQ semantics
- Web UI for configuration
- Custom transformation functions
- Data quality validation
- Real-time monitoring dashboard
- Schema registry integration

## 📚 Additional Documentation

- **[PRD.md](PRD.md)** - Complete Product Requirements Document
- **[ARCHITECTURE.md](ARCHITECTURE.md)** - System architecture
- **[docs/etl/bounded-atomic-batches.md](docs/etl/bounded-atomic-batches.md)** - ETL admission, deterministic transformation, and rollback contract
- **[docs/connectors/](docs/connectors/)** - Target connector scaffolds (Qlik, Databricks, Snowflake)
- **[docs/cdc/any-to-any-cdc.md](docs/cdc/any-to-any-cdc.md)** - Any-to-any CDC design and limitations
- **[docs/rebrand-name-matrix.md](docs/rebrand-name-matrix.md)** - mightyETL vs legacy xtrmETL identifiers
- **[xtrmETL-common-initial-design-notes.txt](xtrmETL-common-initial-design-notes.txt)** - Historical design notes (Korean; legacy name)

---

**Version**: 1.0.0  
**Last Updated**: 2026-08-03  
**Status**: Active Development
