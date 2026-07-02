---
name: spring-boot
description: Guide for Spring Boot backend development. Use when setting up the project, creating REST controllers, services, configuration, security, exception handling, or API design for the Petty Cash Manager backend.
---

# spring-boot

Best practices for building and maintaining the Spring Boot backend for the Petty Cash Manager application.

## When to run

Invoke this skill whenever:
- Setting up or modifying the Spring Boot project structure.
- Creating or modifying REST controllers in `backend/src/main/java/**/controller/`.
- Adding or updating service classes in `backend/src/main/java/**/service/`.
- Configuring application properties, profiles, or security.
- Implementing exception handling or validation.

## Workflow

### Step 1 — Project Structure Conventions

Follow this standard package layout:
```
backend/src/main/java/com/pettycash/
├── PettyCashApplication.java        # @SpringBootApplication entry point
├── config/                           # Security, CORS, bean configs
│   ├── SecurityConfig.java
│   └── CorsConfig.java
├── controller/                       # REST controllers
│   ├── TransactionController.java
│   └── CategoryController.java
├── service/                          # Business logic
│   ├── TransactionService.java
│   └── ReportService.java
├── repository/                       # JPA repositories
│   └── TransactionRepository.java
├── model/                            # JPA entities
│   ├── Transaction.java
│   └── Category.java
├── dto/                              # Request/Response DTOs
│   ├── TransactionRequest.java
│   └── TransactionResponse.java
├── exception/                        # Custom exceptions + global handler
│   ├── ResourceNotFoundException.java
│   └── GlobalExceptionHandler.java
└── mapper/                           # Entity ↔ DTO mappers
    └── TransactionMapper.java
```

### Step 2 — Controller Design

Controllers should be thin — delegate all business logic to services.

```java
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    public ResponseEntity<TransactionResponse> create(
            @Valid @RequestBody TransactionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transactionService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<TransactionResponse>> list(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String type) {
        return ResponseEntity.ok(transactionService.list(month, type));
    }
}
```

**Rules:**
1. Use `@Valid` on all `@RequestBody` parameters.
2. Return proper HTTP status codes (201 for creation, 204 for delete, 200 for reads).
3. Version all API paths under `/api/v1/`.
4. Use `ResponseEntity` wrappers for explicit status codes.

### Step 3 — Service Layer

Services contain all business logic and transaction boundaries.

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final TransactionMapper mapper;

    @Transactional
    public TransactionResponse create(TransactionRequest request) {
        Transaction entity = mapper.toEntity(request);
        return mapper.toResponse(transactionRepository.save(entity));
    }

    public BigDecimal calculateBalance() {
        return transactionRepository.findAll().stream()
            .map(t -> t.getType() == TransactionType.TOPUP
                ? t.getAmount()
                : t.getAmount().negate())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
```

**Rules:**
1. Mark class-level `@Transactional(readOnly = true)`, override with `@Transactional` on write methods.
2. Never expose JPA entities directly — always map to DTOs.
3. Use `BigDecimal` for all monetary amounts, never `double` or `float`.

### Step 4 — Configuration and Profiles

Use Spring profiles to manage environment-specific config:

```yaml
# application.yml (shared defaults)
spring:
  application:
    name: petty-cash-manager

# application-dev.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/pettycash
  jpa:
    show-sql: true
    hibernate:
      ddl-auto: validate

# application-prod.yml
spring:
  jpa:
    show-sql: false
    hibernate:
      ddl-auto: none
```

**Rules:**
1. Never use `ddl-auto: create` or `update` in production — use Flyway or Liquibase migrations.
2. Externalize secrets via environment variables, never hardcode.
3. Configure CORS explicitly for the React frontend origin.

### Step 5 — Exception Handling

Use a `@RestControllerAdvice` for consistent error responses:

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest()
            .body(new ErrorResponse("VALIDATION_ERROR", msg));
    }
}
```

### Step 6 — Security Basics

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable()) // Disable for REST API
            .cors(Customizer.withDefaults())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/**").authenticated()
                .anyRequest().permitAll()
            )
            .build();
    }
}
```

### Step 7 — Verify

After any changes:
```bash
# Compile and run tests
./mvnw clean test -pl backend

# Start the application
./mvnw spring-boot:run -pl backend -Dspring-boot.run.profiles=dev
```
