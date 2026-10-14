# StoreVN – MVNO Storefront

Customer-facing web app for purchasing mobile data plans and paying via VNPAY.

## Tech Stack

- **Backend:** Spring Boot 3.2, Java 21, PostgreSQL, Flyway
- **Frontend:** React + Vite + TypeScript + Ant Design (planned, in `web/`)
- **Payment:** VNPAY v2.1.0 (redirect flow)

## Architecture

```
Browser ── React ──► Storefront API ──┬──► Admin DB   READ-ONLY (role store_readonly)
                                      └──► Store DB   read/write (orders, payments)
```

- **Admin DB** (port 5432): existing plan catalog – storefront only SELECTs via `JdbcClient`
- **Store DB** (port 5433): orders, payment attempts, payment events – managed by Flyway

## Quick Start

```bash
# 1. Start storefront database
docker compose up -d

# 2. Configure environment
copy .env.example .env
# Fill in CATALOG_DB_PASSWORD and VNPAY credentials

# 3. Run (requires Java 21)
mvnw spring-boot:run
```

**Port:** `8081` · **Context path:** `/api/v1` · **Swagger:** http://localhost:8081/api/v1/swagger-ui.html

## Project Structure

```
StoreVN/
├── pom.xml, docker-compose.yml, .env.example
├── db/admin/            SQL scripts for DBA to run on admin DB
├── docs/                Detailed plan and SQL drafts
├── src/main/java/.../storevn/{controller,dto,entity,exception,repository,service,util,vnpay}
├── src/main/resources/  application.yaml, db/migration/
├── src/test/
└── web/                 React app (planned)
```

## Documentation

See [docs/vnpay-storefront-plan.md](docs/vnpay-storefront-plan.md) for the full implementation plan.
