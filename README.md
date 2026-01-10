# xtrmETL - Enterprise ETL and CDC Platform

A microservices-based platform for real-time Change Data Capture (CDC) and Extract-Transform-Load (ETL) operations.

## 🎯 Overview

xtrmETL provides enterprise-grade capabilities for:

- **Real-time Change Data Capture**: Monitor PostgreSQL databases and capture all data changes
- **Data Transformation**: Apply business rules and transform data at scale
- **Event Streaming**: Publish changes to Kafka for downstream processing
- **Secure Access**: JWT-based authentication with role-based access control

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

- **Java 17** or higher
- **Maven 3.6+**
- **PostgreSQL 12+** with logical replication enabled
- **Apache Kafka** (optional, for CDC)
- **Docker** (optional, for containerized deployment)

### Docker Compose

Spin up PostgreSQL, Kafka, Zipkin, Eureka, and the application services:

```bash
docker compose up --build
```

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

### Build

```bash
# Build all modules
mvn clean install

# Or build individual services
cd etl-service && mvn clean package
cd cdc-service && mvn clean package
```

### Run Services

Start services in the following order:

```bash
# 1. Start Eureka Server (Service Discovery)
cd eureka-server
mvn spring-boot:run

# 2. Start CDC Service
cd cdc-service
mvn spring-boot:run

# 3. Start ETL Service
cd etl-service
mvn spring-boot:run

# 4. Start Zuul Gateway
cd zuul-gateway
mvn spring-boot:run
```

Access the gateway at: `http://localhost:8080`

## 📚 Services

### CDC Service (Port 8001)

Captures database changes in real-time using Debezium.

**Key Features:**

- PostgreSQL change data capture
- Kafka event publishing
- Real-time monitoring
- Minimal source database impact

**API Endpoints:**

- `POST /api/cdc/start` - Start CDC process
- `POST /api/cdc/stop` - Stop CDC process

### ETL Service (Port 8000)

Processes and transforms data with configurable business rules.

**Key Features:**

- JSON data processing
- Parallel record processing
- Automatic retry on failures
- Configurable transformations

**API Endpoints:**

- `POST /api/etl/process` - Process data

**Transformation Rules:**

- NAME fields: Convert to uppercase
- EMAIL fields: Convert to lowercase  
- AMOUNT fields: Format to 2 decimal places

### Zuul Gateway (Port 8080)

API Gateway with authentication and routing.

**Routes:**

- `/etl/**` → ETL Service
- `/cdc/**` → CDC Service
- `/auth/**` → Authentication endpoints

### Eureka Server (Port 8761)

Service discovery and registration.

**Dashboard:** `http://localhost:8761`

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
      "id": "1",
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
      "id": "1",
      "name": "jane smith",
      "email": "JANE@COMPANY.COM", 
      "amount": "999.99"
    },
    {
      "id": "2",
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

## 🗄️ Database Setup

### Create Security Tables

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

-- Insert default roles
INSERT INTO roles (name) VALUES ('ROLE_USER'), ('ROLE_ADMIN');
```

### Create ETL Target Table

```sql
CREATE TABLE processed_data (
    id SERIAL PRIMARY KEY,
    data TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## 🧪 Testing

### Run All Tests

```bash
mvn test
```

### Run Service-Specific Tests

```bash
cd etl-service && mvn test
cd cdc-service && mvn test
```

### Test Coverage

Current test coverage includes:

- ✅ EtlServiceTest
- ✅ EtlControllerTest
- ✅ CdcServiceTest
- ✅ CdcControllerTest
- ✅ JwtAuthenticationFilterTest

## 📊 Monitoring

### Zipkin Tracing

Start Zipkin for distributed tracing:

```bash
java -jar zipkin.jar
```

Access Zipkin UI: `http://localhost:9412`

All services are configured to send traces to Zipkin with 100% sampling rate.

### Health Checks

Check service health via Eureka dashboard:

```text
http://localhost:8761
```

## 🔧 Configuration

### Application Properties

Key configuration files:

- `etl-service/src/main/resources/application.yml`
- `cdc-service/src/main/resources/application.yml`
- `zuul-gateway/src/main/resources/application.yml`
- `eureka-server/src/main/resources/application.yml`

### CDC Configuration

Update table monitoring in `CdcService.java`:

```java
.with("table.include.list", "public.your_table_name")
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
# Build each service
docker build -t xtrmetl/etl-service:latest ./etl-service
docker build -t xtrmetl/cdc-service:latest ./cdc-service
docker build -t xtrmetl/zuul-gateway:latest ./zuul-gateway
docker build -t xtrmetl/eureka-server:latest ./eureka-server
```

## 📋 Technology Stack

| Component | Technology | Version |
| ----------- | ----------- | --------- |
| Runtime | Java | 17 |
| Framework | Spring Boot | 2.7.14 |
| Cloud | Spring Cloud | 2021.0.8 |
| CDC Engine | Debezium | 2.3.x - 2.5.x |
| Database | PostgreSQL | 12+ |
| Messaging | Apache Kafka | Latest |
| Gateway | Netflix Zuul | Latest |
| Discovery | Netflix Eureka | Latest |
| Tracing | Zipkin | Latest |
| Security | Spring Security + JWT | Latest |
| Build | Maven | 3.6+ |

## 🎓 Key Concepts

### Change Data Capture (CDC)

Monitors database transaction logs to capture INSERT, UPDATE, and DELETE operations in real-time without impacting source system performance.

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
xtrmETL/
├── pom.xml                    # Parent POM
├── README.md                  # This file
├── PRD.md                     # Product Requirements Document
├── etl-service/              # ETL processing service
├── cdc-service/              # CDC monitoring service
├── zuul-gateway/             # API Gateway
├── eureka-server/            # Service discovery
├── config-server/            # Configuration management
└── zipkin.jar                # Distributed tracing
```

### Adding New Transformations

Edit `EtlService.java` and add cases to the transform method:

```java
case "YOUR_FIELD":
    value = yourTransformation(value);
    break;
```

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
- Refer to the [PRD.md](PRD.md) for detailed requirements

## 🗺️ Roadmap

### Current (v1.0)

- ✅ CDC for PostgreSQL
- ✅ Basic ETL transformations
- ✅ JWT authentication
- ✅ Microservices architecture
- ✅ Distributed tracing

### Planned (v2.0)

- 🔲 Multi-database CDC support (MySQL, Oracle)
- 🔲 Web UI for configuration
- 🔲 Custom transformation functions
- 🔲 Data quality validation
- 🔲 Real-time monitoring dashboard
- 🔲 Schema registry integration
- 🔲 Enhanced error handling and DLQ

## 📚 Additional Documentation

- **[PRD.md](PRD.md)** - Complete Product Requirements Document
- **[xtrmETL-common-initial-design-notes.txt](xtrmETL-common-initial-design-notes.txt)** - Original design notes (Korean)

---

**Version**: 1.0.0  
**Last Updated**: 2026-01-08  
**Status**: Active Development
