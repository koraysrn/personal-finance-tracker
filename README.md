# Personal Finance Tracker

A self-hosted, single-user web application for tracking personal finances: bank accounts, income and expenses, transfers, budgets, recurring transactions and reports.

## Features

- **Accounts** — checking, savings, credit card, cash, investment and debt accounts with per-type balance rules
- **Transactions** — income/expense tracking with categories, tags and notes; idempotent writes
- **Transfers** — atomic transfers between accounts with multi-currency conversion
- **Recurring transactions** — daily/weekly/monthly/yearly rules generated automatically
- **Budgets** — monthly per-category budgets with 80% / 100% / 110% thresholds
- **Reports** — monthly summaries, category breakdown, account balances and net worth
- **Export** — CSV (formula-injection safe), HTML and full JSON backup with restore
- **Preferences** — user-configurable base currency (USD default), locale and date format

## Tech Stack

| Layer     | Technology                                                        |
|-----------|-------------------------------------------------------------------|
| Backend   | Spring Boot 3.3, Java 21, Spring Security, Spring Data JPA        |
| Database  | PostgreSQL 16 (production), H2 (tests), Flyway migrations         |
| Frontend  | TypeScript, Vite                                                  |
| Build     | Maven (wrapper included, no global install required)              |

## Getting Started

### Prerequisites

- Java 21
- Node.js 20+ (frontend build only)
- Docker (for PostgreSQL)

### Run with Docker

```bash
docker compose up -d db
./mvnw spring-boot:run      # Windows: mvnw.cmd spring-boot:run
```

Open `http://localhost:8080` and create the first user.

### Local development

```bash
# Frontend dev server with API proxy on :5173
cd frontend && npm install && npm run dev

# Backend on :8080
./mvnw spring-boot:run
```

### Build

```bash
cd frontend && npm install && npm run build && cd ..
./mvnw clean verify
```

The frontend build is copied into `src/main/resources/static` and packaged into the executable jar.

## Configuration

| Property                                    | Default    | Description                                        |
|---------------------------------------------|------------|----------------------------------------------------|
| `app.security.max-failed-login-attempts`    | `5`        | Failed logins before account lockout               |
| `app.security.lock-duration-minutes`        | `15`       | Lockout duration in minutes                        |
| `app.transaction.allow-future-dates`        | `false`    | Allow future transaction dates                     |
| `app.backup.dir`                            | `./backups`| Automatic backup directory                         |
| `app.scheduling.enabled`                    | `true`     | Enable scheduled jobs (recurring transactions)     |

## Testing

```bash
./mvnw test
```

Runs 60 tests covering validation, balance rules, transfer atomicity, idempotency, recurring month-end handling, budget thresholds, CSV formula-injection protection, brute-force lockout and layered-architecture checks.
