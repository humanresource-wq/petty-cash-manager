# Petty Cash Manager 💸

Full-stack petty cash management system built with Spring Boot, React (TypeScript), Tailwind CSS, shadcn/ui, PostgreSQL, and Liquibase.

## Project Structure
```
petty-cash-manager/
├── backend/            # Spring Boot 3.5.x REST API (Maven)
├── frontend/           # React 18+ / Vite / TypeScript / Tailwind CSS v4 / shadcn/ui
├── docker-compose.yml  # Orchestrates PostgreSQL, Backend, and Frontend containers
├── .env.example        # Environment variable template
└── README.md           # This documentation
```

## Features

- **Dashboard**: Track cash-in-hand balance with low-balance thresholds, monthly spending trends, category breakups, and recent activities.
- **Transaction Log**: Record cash top-ups and expenses (supporting categories, paid-by, receipt numbers, and invoice uploads).
- **Receipt Auditing**: Track missing receipts and mark them as collected.
- **Reporting**: Export transactions to Excel/CSV and generate PDF/Excel summaries (Phase 5).
- **Templates**: CRUD for recurring expense templates (Phase 5).
- **Security**: Google OAuth2 authentication & JWT session management (Phase 2).

## Development Setup

### Prerequisites
- Java 17+ (JDK 21 recommended)
- Node.js 20+ & npm
- Docker & Docker Compose

### 1. Local Development (Separate Services)

#### Backend:
```bash
cd backend
./mvnw clean spring-boot:run -Dspring-boot.run.profiles=dev
```
- API Endpoint: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- H2 Console: `http://localhost:8080/h2-console` (Credentials: JDBC URL: `jdbc:h2:mem:pettycash_dev`, User: `sa`, Password: empty)

#### Frontend:
```bash
cd frontend
npm install
npm run dev
```
- Dev Server: `http://localhost:5173` (proxies `/api` requests to backend at `:8080`)

### 2. Docker Stack (Production/Full Dev)
```bash
# Setup environment variables
cp .env.example .env

# Run full stack
docker compose up --build
```
- Frontend Access: `http://localhost:3000`
- Backend API Access: `http://localhost:8080`
