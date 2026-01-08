# Product Requirements Document (PRD)

## 1. Executive Summary

### 1.1 Product Overview

xtrmETL is a microservices-based enterprise data integration platform
that provides real-time Change Data Capture (CDC) and
Extract-Transform-Load (ETL) capabilities. The platform enables
organizations to capture database changes in real-time and process data
through configurable transformation pipelines.

### 1.2 Product Vision

To provide a scalable, reliable, and secure platform for real-time
data integration and transformation, enabling organizations to
synchronize data across systems, build real-time analytics pipelines,
and maintain data consistency across distributed architectures.

### 1.3 Target Users

- **Data Engineers**: Configure and manage data pipelines
- **System Administrators**: Monitor and maintain platform infrastructure
- **Application Developers**: Integrate applications with the platform
- **Business Analysts**: Access transformed data for analytics

## 2. Problem Statement

### 2.1 Business Problems

Organizations face several challenges in data integration:

- **Manual Data Synchronization**: Time-consuming and error-prone manual data transfers between systems
- **Batch Processing Delays**: Traditional ETL processes run on schedules, causing data latency
- **Data Consistency**: Difficulty maintaining data consistency across multiple systems
- **Scalability**: Inability to handle growing data volumes efficiently
- **Real-time Requirements**: Modern applications require real-time data updates

### 2.2 Technical Challenges

- Capturing database changes without impacting source system performance
- Processing high-volume data streams reliably
- Handling failures and ensuring data integrity
- Scaling horizontally to meet growing demands
- Providing secure access control for data operations

## 3. Solution Overview

### 3.1 Core Capabilities

#### 3.1.1 Change Data Capture (CDC)

- **Real-time Database Monitoring**: Captures INSERT, UPDATE, DELETE operations from PostgreSQL databases
- **Debezium Integration**: Uses Debezium embedded engine for reliable change data capture
- **Kafka Streaming**: Publishes change events to Kafka topics for downstream processing
- **Minimal Source Impact**: Uses PostgreSQL logical replication (pgoutput) to minimize performance impact

#### 3.1.2 ETL Processing

- **JSON-based Data Processing**: Accepts and processes JSON-formatted data
- **Extract-Transform-Load Pipeline**:
  - Extract: Parse JSON data and extract fields
  - Transform: Apply business rules (uppercase names, lowercase emails, format amounts)
  - Load: Store transformed data in target database
- **Parallel Processing**: Uses CompletableFuture for concurrent record processing
- **Retry Mechanism**: Automatic retry on failures (3 attempts with 1-second backoff)

#### 3.1.3 Security & Authentication

- **JWT-based Authentication**: Secure token-based authentication
- **Role-based Access Control (RBAC)**: Support for USER and ADMIN roles
- **Spring Security Integration**: Industry-standard security framework
- **Password Encryption**: BCrypt password hashing

### 3.2 System Architecture

#### 3.2.1 Microservices Architecture

The platform consists of five independent microservices:

1. **CDC Service** (Port 8001)
   - Purpose: Capture database changes and publish to Kafka
   - Technology: Spring Boot, Debezium, Kafka
   - Database: PostgreSQL (monitored)

2. **ETL Service** (Port 8000)
   - Purpose: Process and transform data
   - Technology: Spring Boot, Jackson, Spring Retry
   - Database: PostgreSQL (target)

3. **Zuul Gateway** (Port 8080)
   - Purpose: API Gateway with routing and authentication
   - Routes:
     - `/etl/**` → ETL Service
     - `/cdc/**` → CDC Service

4. **Eureka Server** (Port 8761)
   - Purpose: Service discovery and registration
   - Enables dynamic service location

5. **Config Server**
   - Purpose: Centralized configuration management
   - Future enhancement for externalized configuration

#### 3.2.2 Technology Stack

- **Runtime**: Java 17
- **Framework**: Spring Boot 2.7.14, Spring Cloud 2021.0.8
- **Database**: PostgreSQL
- **Messaging**: Apache Kafka
- **Service Discovery**: Netflix Eureka
- **API Gateway**: Netflix Zuul
- **CDC Engine**: Debezium 2.3.x - 2.5.x
- **Monitoring**: Zipkin (distributed tracing), Micrometer
- **Build Tool**: Maven

## 4. Functional Requirements

### 4.1 CDC Service Requirements

#### FR-CDC-1: Database Connection Management

- **Priority**: P0 (Critical)
- **Description**: Connect to PostgreSQL database using environment variables
- **Acceptance Criteria**:
  - Support PGHOST, PGPORT, PGUSER, PGPASSWORD, PGDATABASE environment variables
  - Validate connection on startup
  - Log connection errors clearly

#### FR-CDC-2: Change Event Capture

- **Priority**: P0 (Critical)
- **Description**: Capture all data changes from configured tables
- **Acceptance Criteria**:
  - Capture INSERT, UPDATE, DELETE operations
  - Include before/after values for UPDATE operations
  - Preserve event ordering
  - Handle schema changes gracefully

#### FR-CDC-3: Event Publishing

- **Priority**: P0 (Critical)
- **Description**: Publish change events to Kafka topics
- **Acceptance Criteria**:
  - Topic naming: `xtrmetl-cdc.{schema}.{table}`
  - Include event metadata (timestamp, operation type, source info)
  - Guarantee at-least-once delivery

#### FR-CDC-4: CDC Control API

- **Priority**: P1 (High)
- **Description**: Provide REST API to control CDC process
- **Endpoints**:
  - `POST /api/cdc/start`: Start CDC capture
  - `POST /api/cdc/stop`: Stop CDC capture
- **Acceptance Criteria**:
  - Return appropriate status codes
  - Handle concurrent start/stop requests
  - Graceful shutdown without data loss

### 4.2 ETL Service Requirements

#### FR-ETL-1: Data Processing API

- **Priority**: P0 (Critical)
- **Description**: Accept JSON data for ETL processing
- **Endpoint**: `POST /api/etl/process`
- **Acceptance Criteria**:
  - Accept JSON array of records
  - Each record must have an 'id' field
  - Return processing results
  - Handle malformed JSON gracefully

#### FR-ETL-2: Data Extraction

- **Priority**: P0 (Critical)
- **Description**: Extract fields from JSON records
- **Acceptance Criteria**:
  - Parse all JSON fields
  - Handle nested objects
  - Preserve data types
  - Log extraction errors

#### FR-ETL-3: Data Transformation

- **Priority**: P0 (Critical)
- **Description**: Apply business rules to transform data
- **Transformation Rules**:
  - NAME field: Convert to uppercase
  - EMAIL field: Convert to lowercase
  - AMOUNT field: Format to 2 decimal places, default to "0.00" on error
- **Acceptance Criteria**:
  - Apply rules consistently
  - Handle missing fields gracefully
  - Maintain audit trail of transformations

#### FR-ETL-4: Data Loading

- **Priority**: P0 (Critical)
- **Description**: Load transformed data into target database
- **Acceptance Criteria**:
  - Insert records into `processed_data` table
  - Handle duplicate keys
  - Maintain transaction integrity
  - Rollback on failure

#### FR-ETL-5: Parallel Processing

- **Priority**: P1 (High)
- **Description**: Process multiple records concurrently
- **Acceptance Criteria**:
  - Use thread pool for parallel execution
  - Limit concurrent threads to prevent resource exhaustion
  - Aggregate results from all threads
  - Handle individual record failures without failing entire batch

### 4.3 Authentication & Authorization Requirements

#### FR-AUTH-1: User Registration

- **Priority**: P0 (Critical)
- **Endpoint**: `POST /auth/signup`
- **Acceptance Criteria**:
  - Require unique username
  - Encrypt passwords using BCrypt
  - Assign default USER role
  - Return clear error messages

#### FR-AUTH-2: User Login

- **Priority**: P0 (Critical)
- **Endpoint**: `POST /auth/signin`
- **Acceptance Criteria**:
  - Validate credentials
  - Generate JWT token (1 hour expiration)
  - Return token in response
  - Log authentication attempts

#### FR-AUTH-3: Protected Endpoints

- **Priority**: P0 (Critical)
- **Description**: Secure all API endpoints except authentication
- **Acceptance Criteria**:
  - Require valid JWT token for protected endpoints
  - Return 401 for missing/invalid tokens
  - Return 403 for insufficient permissions
  - Support role-based access control

### 4.4 Service Discovery & Routing Requirements

#### FR-DISC-1: Service Registration

- **Priority**: P0 (Critical)
- **Description**: All services register with Eureka
- **Acceptance Criteria**:
  - Auto-register on startup
  - Send heartbeats every 30 seconds
  - De-register on graceful shutdown
  - Handle network partitions

#### FR-GATE-1: API Gateway Routing

- **Priority**: P0 (Critical)
- **Description**: Route requests through Zuul Gateway
- **Acceptance Criteria**:
  - Route `/etl/**` to ETL Service
  - Route `/cdc/**` to CDC Service
  - Apply JWT authentication filter
  - Handle service unavailability gracefully

## 5. Non-Functional Requirements

### 5.1 Performance Requirements

#### NFR-PERF-1: CDC Latency

- **Requirement**: Change events published within 1 second of database commit
- **Measurement**: Monitor lag between transaction commit and Kafka publish
- **Priority**: P0

#### NFR-PERF-2: ETL Throughput

- **Requirement**: Process minimum 1000 records per second
- **Measurement**: Monitor processing time and throughput metrics
- **Priority**: P1

#### NFR-PERF-3: API Response Time

- **Requirement**: 95th percentile response time < 500ms
- **Measurement**: Use Micrometer metrics
- **Priority**: P1

### 5.2 Reliability Requirements

#### NFR-REL-1: Service Availability

- **Requirement**: 99.9% uptime for production services
- **Measurement**: Uptime monitoring and alerting
- **Priority**: P0

#### NFR-REL-2: Data Integrity

- **Requirement**: Zero data loss for CDC events
- **Measurement**: Audit logs and reconciliation processes
- **Priority**: P0

#### NFR-REL-3: Fault Tolerance

- **Requirement**: Automatic retry on transient failures
- **Implementation**: Spring Retry with exponential backoff (3 attempts, 1s delay)
- **Priority**: P1

### 5.3 Scalability Requirements

#### NFR-SCALE-1: Horizontal Scaling

- **Requirement**: Support multiple instances of each service
- **Implementation**: Stateless services with externalized session management
- **Priority**: P1

#### NFR-SCALE-2: Data Volume

- **Requirement**: Handle database tables with 100M+ rows
- **Priority**: P1

### 5.4 Security Requirements

#### NFR-SEC-1: Authentication

- **Requirement**: All API access requires valid JWT token
- **Implementation**: Spring Security with JWT filter
- **Priority**: P0

#### NFR-SEC-2: Authorization

- **Requirement**: Role-based access control for sensitive operations
- **Roles**: USER, ADMIN
- **Priority**: P0

#### NFR-SEC-3: Password Security

- **Requirement**: Strong password hashing
- **Implementation**: BCrypt with salt
- **Priority**: P0

#### NFR-SEC-4: Secrets Management

- **Requirement**: No hardcoded credentials
- **Implementation**: Environment variables for database credentials
- **Priority**: P0

### 5.5 Observability Requirements

#### NFR-OBS-1: Distributed Tracing

- **Requirement**: Trace requests across all microservices
- **Implementation**: Spring Cloud Sleuth + Zipkin
- **Priority**: P1

#### NFR-OBS-2: Logging

- **Requirement**: Structured logging with correlation IDs
- **Log Levels**: INFO for operations, DEBUG for troubleshooting
- **Priority**: P1

#### NFR-OBS-3: Metrics

- **Requirement**: Expose metrics for monitoring
- **Implementation**: Micrometer with custom business metrics
- **Priority**: P1

### 5.6 Maintainability Requirements

#### NFR-MAINT-1: Code Quality

- **Requirement**: Unit test coverage > 80%
- **Current Status**: Tests exist for controllers and services
- **Priority**: P1

#### NFR-MAINT-2: Documentation

- **Requirement**: API documentation and deployment guides
- **Priority**: P1

## 6. Data Model

### 6.1 Security Schema

#### Users Table

```sql
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(100) NOT NULL
);
```

#### Roles Table

```sql
CREATE TABLE roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(20) UNIQUE NOT NULL
);
```

#### User-Roles Association

```sql
CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (role_id) REFERENCES roles(id)
);
```

### 6.2 ETL Schema

#### Processed Data Table

```sql
CREATE TABLE processed_data (
    id BIGSERIAL PRIMARY KEY,
    data TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## 7. API Specifications

### 7.1 Authentication APIs

#### POST /auth/signup

**Request**:

```json
{
  "username": "string",
  "password": "string"
}
```

**Response** (200):

```json
{
  "message": "User registered successfully"
}
```

#### POST /auth/signin

**Request**:

```json
{
  "username": "string",
  "password": "string"
}
```

**Response** (200):

```json
{
  "token": "EXAMPLE_JWT_TOKEN_TRUNCATED"  // Example token for illustration
}
```

### 7.2 CDC APIs

#### POST /api/cdc/start

**Headers**: `Authorization: Bearer {token}`  
**Response** (200):

```json
{
  "message": "CDC process started"
}
```

#### POST /api/cdc/stop

**Headers**: `Authorization: Bearer {token}`  
**Response** (200):

```json
{
  "message": "CDC process stopped"
}
```

### 7.3 ETL APIs

#### POST /api/etl/process

**Headers**: `Authorization: Bearer {token}`  
**Request**:

```json
[
  {
    "id": "1",
    "name": "John Doe",
    "email": "JOHN@EXAMPLE.COM",
    "amount": "1234.5"
  }
]
```

**Response** (200):

```text
Processed: 1
Processed: 2
...
```

## 8. Deployment Architecture

### 8.1 Service Ports

- **Zuul Gateway**: 8080 (public-facing)
- **ETL Service**: 8000 (internal)
- **CDC Service**: 8001 (internal)
- **Eureka Server**: 8761 (internal)
- **Config Server**: 8888 (internal)
- **Zipkin**: 9412 (internal)

### 8.2 External Dependencies

- **PostgreSQL**: Source and target databases
- **Apache Kafka**: Message streaming (port 9092)
- **Zipkin**: Distributed tracing (port 9412)

### 8.3 Environment Variables

#### Required for CDC Service

- `PGHOST`: PostgreSQL host
- `PGPORT`: PostgreSQL port (default: 5432)
- `PGUSER`: PostgreSQL username
- `PGPASSWORD`: PostgreSQL password
- `PGDATABASE`: PostgreSQL database name

#### Required for ETL Service

- `PGHOST`: PostgreSQL host
- `PGPORT`: PostgreSQL port
- `PGUSER`: PostgreSQL username
- `PGPASSWORD`: PostgreSQL password
- `PGDATABASE`: PostgreSQL database name

## 9. Use Cases

### 9.1 Real-time Data Synchronization

**Actors**: Data Engineer, Source System, Target System  
**Goal**: Synchronize data changes from source to target in real-time

**Flow**:

1. Source application updates record in PostgreSQL
2. CDC Service detects change via Debezium
3. Change event published to Kafka topic
4. Downstream consumer processes change
5. Target system updated within 1 second

### 9.2 Batch Data Transformation

**Actors**: Data Engineer, External System  
**Goal**: Transform and load batch data

**Flow**:

1. External system authenticates via JWT
2. POST JSON array to `/api/etl/process`
3. ETL Service validates and parses data
4. Applies transformation rules in parallel
5. Loads transformed data to database
6. Returns processing results

### 9.3 User Access Management

**Actors**: Administrator, End User  
**Goal**: Control access to platform APIs

**Flow**:

1. Administrator creates user account
2. User authenticates with credentials
3. System validates and issues JWT token
4. User includes token in subsequent API calls
5. System validates token and permissions
6. Grants or denies access based on role

## 10. Future Enhancements

### 10.1 Planned Features (v2.0)

- **Multi-database Support**: MySQL, Oracle, SQL Server CDC
- **Custom Transformations**: User-defined transformation functions
- **Data Quality Rules**: Validation and data quality checks
- **Web UI**: Configuration and monitoring dashboard
- **Schema Registry**: Centralized schema management
- **Dead Letter Queue**: Failed message handling
- **Metrics Dashboard**: Real-time monitoring UI

### 10.2 Technical Debt

- **Common Module**: Referenced in documentation but not implemented
- **MyBatis Integration**: Mentioned but not used
- **Redis Integration**: Dependency present but not utilized
- **Config Server**: Implemented but not actively used
- **Health Checks**: Add Spring Boot Actuator endpoints

## 11. Success Metrics

### 11.1 Technical Metrics

- **CDC Lag**: < 1 second average
- **ETL Throughput**: > 1000 records/second
- **API Response Time**: < 500ms (p95)
- **Error Rate**: < 0.1%
- **Test Coverage**: > 80%

### 11.2 Business Metrics

- **Data Accuracy**: 100% (zero data loss)
- **System Uptime**: 99.9%
- **Processing Cost**: Measured per million records
- **Time to Sync**: < 5 seconds for critical data

## 12. Risks and Mitigations

### 12.1 Technical Risks

| Risk | Impact | Probability | Mitigation |
| ------ | -------- | ------------- | ------------ |
| Kafka message loss | High | Low | Enable acknowledgments, configure retention |
| Database connection pool exhaustion | High | Medium | Configure connection limits, implement circuit breaker |
| Memory leaks in long-running processes | Medium | Medium | Regular monitoring, automated restarts |
| Debezium version compatibility | Medium | Low | Pin versions, test upgrades thoroughly |
| JWT token compromise | High | Low | Short expiration, token rotation, HTTPS only |

### 12.2 Operational Risks

| Risk | Impact | Probability | Mitigation |
| ------ | -------- | ------------- | ------------ |
| Service discovery failure | High | Low | Eureka clustering, health checks |
| Configuration drift | Medium | Medium | Infrastructure as Code, Config Server |
| Insufficient monitoring | Medium | High | Implement comprehensive observability |
| Data volume growth | High | High | Capacity planning, horizontal scaling |

## 13. Glossary

- **CDC**: Change Data Capture - Technology to capture database changes
- **ETL**: Extract, Transform, Load - Data processing pattern
- **Debezium**: Open-source CDC platform
- **JWT**: JSON Web Token - Token-based authentication standard
- **Eureka**: Netflix service discovery server
- **Zuul**: Netflix API Gateway
- **Kafka**: Distributed streaming platform
- **RBAC**: Role-Based Access Control
- **pgoutput**: PostgreSQL logical replication output plugin

## 14. References

### 14.1 Technology Documentation

- [Debezium Documentation](https://debezium.io/documentation/)
- [Spring Cloud Documentation](https://spring.io/projects/spring-cloud)
- [Apache Kafka Documentation](https://kafka.apache.org/documentation/)
- [PostgreSQL Logical Replication](https://www.postgresql.org/docs/current/logical-replication.html)

### 14.2 Internal Documentation

- See `Pasted--xtrmETL-xtrmETL-common--1728658367670.txt` for initial design notes (Korean)
- Service-specific READMEs (to be created)

---

**Document Version**: 1.0  
**Last Updated**: 2026-01-08  
**Status**: Draft for Review  
**Author**: Product Engineering Team  
**Approvers**: TBD
