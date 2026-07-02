---
name: docker-compose
description: Guide for Dockerfiles, Docker Compose configuration, multi-stage builds, volumes, networking, and environment management. Use when containerizing the Petty Cash Manager application, setting up dev/prod environments, or troubleshooting container issues.
---

# docker-compose

Best practices for containerizing and orchestrating the Petty Cash Manager application with Docker and Docker Compose.

## When to run

Invoke this skill whenever:
- Creating or modifying Dockerfiles in `backend/` or `frontend/`.
- Updating `docker-compose.yml` or `docker-compose.dev.yml`.
- Adding new services (database, cache, reverse proxy).
- Debugging container startup, networking, or volume issues.
- Setting up CI/CD pipelines that build Docker images.

## Workflow

### Step 1 — Project Docker Structure

```
petty-cash-manager/
├── docker-compose.yml            # Production compose
├── docker-compose.dev.yml        # Dev overrides (hot-reload, debug)
├── .env                          # Shared environment variables
├── .env.example                  # Template for contributors
├── backend/
│   ├── Dockerfile                # Multi-stage Spring Boot build
│   └── .dockerignore
└── frontend/
    ├── Dockerfile                # Multi-stage React build
    └── .dockerignore
```

### Step 2 — Backend Dockerfile (Spring Boot Multi-Stage)

```dockerfile
# backend/Dockerfile

# ---- Build Stage ----
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Cache dependencies
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN ./mvnw dependency:go-offline -B

# Build application
COPY src src
RUN ./mvnw package -DskipTests -B

# ---- Runtime Stage ----
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Rules:**
1. Always use multi-stage builds — separate build and runtime.
2. Copy `pom.xml` first to cache Maven dependency downloads.
3. Use JRE (not JDK) for runtime — smaller image.
4. Run as non-root user.
5. Add `HEALTHCHECK` for orchestration readiness.

### Step 3 — Frontend Dockerfile (React Multi-Stage)

```dockerfile
# frontend/Dockerfile

# ---- Build Stage ----
FROM node:20-alpine AS builder
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --production=false

COPY . .
RUN npm run build

# ---- Runtime Stage ----
FROM nginx:alpine AS runtime

COPY --from=builder /app/build /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD wget -qO- http://localhost/ || exit 1
```

### Step 4 — Docker Compose (Production)

```yaml
# docker-compose.yml
version: '3.9'

services:
  db:
    image: postgres:16-alpine
    restart: unless-stopped
    environment:
      POSTGRES_DB: ${POSTGRES_DB:-pettycash}
      POSTGRES_USER: ${POSTGRES_USER:-pettycash}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?Set POSTGRES_PASSWORD in .env}
    volumes:
      - pgdata:/var/lib/postgresql/data
    ports:
      - "${DB_PORT:-5432}:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER:-pettycash}"]
      interval: 10s
      timeout: 5s
      retries: 5

  backend:
    build:
      context: ./backend
      dockerfile: Dockerfile
    restart: unless-stopped
    depends_on:
      db:
        condition: service_healthy
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/${POSTGRES_DB:-pettycash}
      SPRING_DATASOURCE_USERNAME: ${POSTGRES_USER:-pettycash}
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILE:-prod}
    ports:
      - "${BACKEND_PORT:-8080}:8080"
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 5s
      retries: 3

  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile
    restart: unless-stopped
    depends_on:
      - backend
    ports:
      - "${FRONTEND_PORT:-3000}:80"

volumes:
  pgdata:
    driver: local
```

### Step 5 — Docker Compose (Dev Overrides)

```yaml
# docker-compose.dev.yml
version: '3.9'

services:
  backend:
    build:
      target: builder  # Use build stage for hot-reload
    volumes:
      - ./backend/src:/app/src  # Hot-reload source
    environment:
      SPRING_PROFILES_ACTIVE: dev
      SPRING_DEVTOOLS_RESTART_ENABLED: "true"
    command: ["./mvnw", "spring-boot:run"]

  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile.dev  # Separate dev Dockerfile
    volumes:
      - ./frontend/src:/app/src
      - /app/node_modules         # Anonymous volume to preserve node_modules
    environment:
      - CHOKIDAR_USEPOLLING=true  # File watching in Docker
    command: ["npm", "start"]
    ports:
      - "3000:3000"
```

Start dev environment:
```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up --build
```

### Step 6 — Environment Variables

```bash
# .env.example
POSTGRES_DB=pettycash
POSTGRES_USER=pettycash
POSTGRES_PASSWORD=changeme_in_production
DB_PORT=5432
BACKEND_PORT=8080
FRONTEND_PORT=3000
SPRING_PROFILE=dev
```

**Rules:**
1. Never commit `.env` — only `.env.example`.
2. Use `${VAR:-default}` for optional vars, `${VAR:?error}` for required vars.
3. Prefix all custom env vars with the service context.

### Step 7 — Common Commands

```bash
# Build and start all services
docker compose up --build -d

# View logs
docker compose logs -f backend

# Run backend tests inside container
docker compose exec backend ./mvnw test

# Run database migrations
docker compose exec backend ./mvnw flyway:migrate

# Stop and clean up
docker compose down -v  # -v removes volumes (destructive!)

# Rebuild single service
docker compose up --build -d backend

# Shell into container
docker compose exec backend sh
```

### Step 8 — Verify

After any Docker changes:
```bash
# Clean build
docker compose build --no-cache

# Start and check health
docker compose up -d
docker compose ps   # All services should show "healthy"

# Test connectivity
curl http://localhost:8080/actuator/health
curl http://localhost:3000
```
