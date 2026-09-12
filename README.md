# Enterprise Order Platform

Enterprise multi-tenant order management platform built on Oracle Database. Demonstrates high availability, disaster recovery, replication, and data protection patterns at enterprise scale.

## Architecture

```
                    CLIENTS
                       │
                       ▼
               LOAD BALANCER
                       │
           ┌───────────┴───────────┐
           │                       │
           ▼                       ▼
    APP NODE 01              APP NODE 02
    Spring Boot              Spring Boot
           │                       │
           └───────────┬───────────┘
                       │
                    JDBC/UCP
                       │
                       ▼
           ┌───────────────────────┐
           │    ORACLE CDB          │
           │                        │
           │ ┌────────────────────┐ │
           │ │    PDB_OLTP         │ │
           │ │                     │ │
           │ │ Shared Schema       │ │
           │ │                     │ │
           │ │  Tenant A           │ │
           │ │  Tenant B           │ │
           │ │  Tenant C           │ │
           │ │                     │ │
           │ │ VPD / DBMS_RLS      │ │
           │ │ PL/SQL              │ │
           │ │ Materialized Views  │ │
           │ └────────────────────┘ │
           └───────────┬───────────┘
                       │
                  RAC + ASM
                       │
           ┌───────────┴───────────┐
           │                       │
           ▼                       ▼
      RAC NODE 1              RAC NODE 2
                       │
                   Data Guard
                       │
                       ▼
                 DR DATABASE
                       │
                   GoldenGate
                       │
                       ▼
            REPORTING / ANALYTICS
```

## Local Lab vs Enterprise Target

| Capability         |          Local Lab | Enterprise Target |
| ------------------ | -----------------: | ----------------: |
| Oracle Database    |                  ✓ |                 ✓ |
| Spring Boot        |                  ✓ |                 ✓ |
| JDBC               |                  ✓ |                 ✓ |
| UCP                |                  ✓ |                 ✓ |
| PL/SQL             |                  ✓ |                 ✓ |
| VPD                |                  ✓ |                 ✓ |
| Multi-tenancy      |                  ✓ |                 ✓ |
| Materialized Views |                  ✓ |                 ✓ |
| RMAN               |                  ✓ |                 ✓ |
| Flashback          |                  ✓ |                 ✓ |
| GoldenGate         |           Optional |                 ✓ |
| Data Guard         | Architecture / lab |                 ✓ |
| RAC                |       Architecture |                 ✓ |
| ASM                |       Architecture |                 ✓ |
| Multitenant        |                  ✓ |                 ✓ |

## Quick Start

### Prerequisites

- Podman
- Java 21+
- Maven 3.9+

### Local Lab Setup

```bash
# Clone repository
git clone <repository-url>
cd enterprise-order-platform

# Start Oracle Database
cd infrastructure/podman
podman compose up -d

# Run database migrations
cd ../../backend
./mvnw flyway:migrate

# Start application
./mvnw spring-boot:run
```

### Verify

```bash
# Health check
curl http://localhost:8080/actuator/health

# Create tenant
curl -X POST http://localhost:8080/api/v1/tenants \
  -H "Content-Type: application/json" \
  -d '{"tenantCode":"ACME","name":"Acme Corporation","tier":"STANDARD"}'
```

## Documentation

- [SRS](./docs/SRS.md) - Software Requirements Specification
- [Architecture](./docs/architecture/overview.md) - System architecture
- [ADR](./docs/adr/) - Architecture Decision Records
- [Runbooks](./docs/runbooks/) - Operational procedures

## Demo Flow

1. Create tenant (ACME, GLOBEX, INITECH)
2. Create customer
3. Create product
4. Assign inventory
5. Create order
6. Confirm order (reserves inventory)
7. View Materialized View
8. Verify tenant isolation
9. Demonstrate HA/DR architecture
10. Show recovery scenarios

## Technology Stack

### Application
- Java 21
- Spring Boot
- Spring JDBC (no JPA)
- Oracle JDBC Driver
- Oracle UCP (Universal Connection Pool)
- Flyway
- JUnit 5
- Micrometer + Actuator

### Database
- Oracle Database (Free/Enterprise)
- Oracle Multitenant (CDB/PDB)
- PL/SQL Packages
- Oracle VPD / DBMS_RLS
- Materialized Views
- RMAN + Flashback

### Infrastructure
- Podman
- Docker Compose

## Repository Structure

```
enterprise-order-platform/
├── README.md
├── LICENSE
├── docs/
│   ├── SRS.md
│   ├── architecture/
│   │   ├── overview.md
│   │   ├── database.md
│   │   ├── multitenancy.md
│   │   ├── high-availability.md
│   │   ├── disaster-recovery.md
│   │   ├── replication.md
│   │   └── security.md
│   ├── adr/
│   │   ├── ADR-001-database-first.md
│   │   ├── ADR-002-multitenancy.md
│   │   ├── ADR-003-jdbc-over-jpa.md
│   │   ├── ADR-004-vpd.md
│   │   ├── ADR-005-materialized-views.md
│   │   ├── ADR-006-goldengate.md
│   │   ├── ADR-007-data-guard.md
│   │   ├── ADR-008-rac.md
│   │   ├── ADR-009-rman-flashback.md
│   │   └── ADR-010-multitenant-cdb.md
│   └── runbooks/
│       ├── backup.md
│       ├── restore.md
│       ├── flashback.md
│       ├── switchover.md
│       ├── failover.md
│       └── goldengate-recovery.md
├── backend/
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/
├── database/
│   ├── migrations/
│   ├── packages/
│   ├── security/
│   ├── materialized-views/
│   ├── seed/
│   └── backup/
├── goldengate/
│   ├── extract/
│   ├── replicat/
│   ├── parameter-files/
│   └── docs/
├── infrastructure/
│   ├── podman/
│   ├── networking/
│   └── rac/
└── tests/
    ├── integration/
    ├── concurrency/
    ├── security/
    └── performance/
```

## Failure Domains

| Failure                        | Technology           |
| ------------------------------ | -------------------- |
| Connection failure             | UCP                  |
| Application instance failure   | Load balancer        |
| Database instance/node failure | RAC                  |
| Storage failure                | ASM                  |
| Site failure                   | Data Guard           |
| Logical data error             | Flashback / recovery |
| Physical data loss             | RMAN                 |
| Downstream integration         | GoldenGate           |

## Definition of Done

- [x] Repository reproducible
- [ ] Oracle local funcionando
- [ ] Flyway migrations
- [ ] Complete relational model
- [ ] Constraints
- [ ] Indexes
- [ ] PL/SQL packages
- [ ] Multi-tenancy
- [ ] VPD
- [ ] Spring Boot API
- [ ] JDBC
- [ ] UCP
- [ ] Order workflow
- [ ] Inventory concurrency
- [ ] Idempotency
- [ ] Audit
- [ ] Outbox
- [ ] Materialized Views
- [ ] Reporting
- [ ] Integration tests
- [ ] Security tests
- [ ] Performance tests
- [ ] RMAN documentation
- [ ] Flashback documentation
- [ ] GoldenGate architecture
- [ ] Data Guard architecture
- [ ] RAC architecture
- [ ] ASM architecture
- [ ] Failure scenarios
- [ ] Runbooks
- [ ] Architecture diagrams
- [ ] ADRs
- [ ] Portfolio README

## License

See [LICENSE](LICENSE) file.