# FishBook Foundation and Identity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first working FishBook vertical slice: a reproducible monorepo with local infrastructure, a React shell, a Spring Boot API, MySQL migrations, secure session-based registration/login/logout, and an authenticated profile page.

**Architecture:** Serve a React SPA and Spring Boot REST API from the same origin in the full-stack environment. Keep identity code inside one bounded backend module with domain, application, web, security, and persistence packages; persist users and HTTP sessions in MySQL via Flyway and Spring Session JDBC. Use a thin typed frontend API layer, TanStack Query for server state, and React Router for public/protected routes.

**Tech Stack:** Java 21, Spring Boot 4.1.x, Maven Wrapper, MySQL 8.4, Flyway, Spring Security, Spring Session JDBC, Testcontainers, React 19.2, TypeScript, Vite 8.1, React Router, TanStack Query, React Hook Form, Zod, Vitest, React Testing Library, Playwright, Docker Compose.

## Global Constraints

- Use Java 21; the current host has no Java runtime and must install a Temurin 21 distribution before implementation.
- Pin Node.js to 24.18.0 LTS in `.nvmrc`; the current host Node 26 installation must not determine the project runtime.
- Install Docker Desktop before implementation; `docker` and `docker compose` are currently absent.
- Use Spring Boot 4.1.x with the exact patch version recorded in `backend/pom.xml`; do not use snapshots or milestones.
- Use MySQL 8.4 with InnoDB, `utf8mb4`, and UTC application timestamps.
- Use server-side Session authentication with `HttpOnly`, `Secure` in production, `SameSite=Lax`, CSRF protection, and session ID rotation on login.
- Use JSON for ordinary API requests; image-bearing APIs in later plans use multipart. This plan has no image endpoint.
- Do not add Lombok, Redux, WebFlux, Redis, JWT, a message queue, or a microservice boundary.
- Keep backend package dependencies pointing inward: web/security/persistence → application/domain. Application code depends on repository and password-hasher interfaces, not Spring Data implementations.
- Work test-first for behavior, run the failing test before implementation, and commit after each task.
- Source specification: `docs/superpowers/specs/2026-08-07-fishbook-mvp-design.md`.

---

## Scope Decomposition

The approved MVP contains five independently reviewable implementation plans. This document is plan 1 and folds repository setup into the first functional identity slice, so setup is proven by working software.

1. Foundation and Identity — this document.
2. Fish Catalog and Administration — write after plan 1 establishes package and test conventions.
3. Favorites — write after catalog interfaces are real.
4. Catch Log and Media — write after identity and catalog IDs are stable.
5. Release Quality and Deployment — write after all feature slices work locally.

## File and Responsibility Map

```text
Fish_Book/
├── .editorconfig                         # Cross-language whitespace rules
├── .env.example                          # Safe local variable names and non-secret examples
├── .github/workflows/ci.yml              # Pull-request and main-branch verification
├── .gitignore                            # Java, Node, IDE, secret, and local tool output
├── .nvmrc                                # Node 24.18.0 LTS pin
├── README.md                             # Setup, run, test, and troubleshooting commands
├── compose.yaml                          # MySQL and MinIO local dependencies
├── compose.full.yaml                     # Same-origin frontend/backend integration stack
├── backend/
│   ├── pom.xml                           # Spring Boot dependency and plugin graph
│   ├── mvnw / mvnw.cmd / .mvn/           # Reproducible Maven wrapper
│   └── src/
│       ├── main/java/com/fishbook/
│       │   ├── FishBookApplication.java
│       │   ├── common/error/              # Stable API error contract
│       │   └── identity/
│       │       ├── domain/                # User, roles, ports, domain rules
│       │       ├── application/           # Registration and profile use cases
│       │       ├── persistence/           # JPA entity, Spring Data repository, adapter
│       │       ├── security/              # Security filter chain and login/session adapter
│       │       └── web/                   # Auth, CSRF, and current-user controllers/DTOs
│       ├── main/resources/
│       │   ├── application.yml
│       │   ├── application-local.yml
│       │   └── db/migration/              # Versioned MySQL migrations
│       └── test/java/com/fishbook/        # Unit, MVC/security, and MySQL integration tests
├── frontend/
│   ├── package.json / package-lock.json
│   ├── vite.config.ts / tsconfig files
│   ├── Dockerfile / nginx.conf
│   └── src/
│       ├── app/                           # Providers, routes, application shell
│       ├── features/auth/                 # Auth types, API, forms, pages, tests
│       ├── shared/api/                    # Fetch wrapper and API error conversion
│       ├── shared/ui/                     # Small reusable form components
│       └── test/                          # Vitest setup and test helpers
└── e2e/                                   # Playwright full-stack auth flow
```

## Shared Interfaces Locked by This Plan

Backend application interfaces:

```java
public record RegisterUserCommand(String email, String password, String nickname) {}

public record UserView(long id, String email, String nickname, String role) {}

public interface UserRepository {
    boolean existsByEmail(String normalizedEmail);
    Optional<User> findByEmail(String normalizedEmail);
    User save(User user);
}

public interface PasswordHasher {
    String hash(String rawPassword);
    boolean matches(String rawPassword, String encodedPassword);
}

public interface AuthApplicationService {
    UserView register(RegisterUserCommand command);
}

public interface ProfileApplicationService {
    UserView currentUser(String normalizedEmail);
    UserView updateNickname(String normalizedEmail, String nickname);
}
```

HTTP contracts:

```text
POST  /api/v1/auth/register  JSON { email, password, nickname } -> 201 UserResponse
POST  /api/v1/auth/login     JSON { email, password }           -> 200 UserResponse
POST  /api/v1/auth/logout                                      -> 204
GET   /api/v1/auth/csrf                                        -> 200 CsrfResponse
GET   /api/v1/me                                                -> 200 UserResponse
PATCH /api/v1/me             JSON { nickname }                  -> 200 UserResponse
GET   /actuator/health                                         -> 200 { status: "UP" }
```

Frontend interfaces:

```ts
export type User = {
  id: number;
  email: string;
  nickname: string;
  role: 'USER' | 'ADMIN';
};

export type RegisterInput = {
  email: string;
  password: string;
  nickname: string;
};

export type LoginInput = {
  email: string;
  password: string;
};

export type ApiErrorBody = {
  code: string;
  message: string;
  fieldErrors: Array<{ field: string; message: string }>;
  requestId: string;
};
```

---

### Task 1: Pin the Toolchain and Establish Repository Policies

**Files:**
- Create: `.nvmrc`
- Create: `.editorconfig`
- Modify: `.gitignore`
- Create: `.env.example`
- Create: `README.md`

**Interfaces:**
- Consumes: approved design specification and the host tool inventory.
- Produces: exact runtime pins and repository-wide commands used by every later task.

- [ ] **Step 1: Install and verify required host tools**

Install a Temurin 21 JDK and Docker Desktop using their official installers. Use a Node version manager to install Node 24.18.0. Do not change project files until all commands succeed.

Run:

```bash
java -version
node --version
npm --version
docker --version
docker compose version
git --version
```

Expected:

```text
java version begins with 21
node prints v24.18.0
npm exits 0
Docker and Docker Compose both exit 0
Git exits 0
```

- [ ] **Step 2: Write the runtime and editor policy files**

Create `.nvmrc`:

```text
24.18.0
```

Create `.editorconfig`:

```ini
root = true

[*]
charset = utf-8
end_of_line = lf
insert_final_newline = true
indent_style = space
indent_size = 2
trim_trailing_whitespace = true

[*.java]
indent_size = 4

[*.md]
trim_trailing_whitespace = false
```

Extend `.gitignore` with:

```gitignore
.superpowers/
.DS_Store
.idea/
.vscode/
.env
.env.*
!.env.example
frontend/node_modules/
frontend/dist/
frontend/playwright-report/
frontend/test-results/
backend/target/
*.log
```

- [ ] **Step 3: Define safe local variable names**

Create `.env.example`:

```dotenv
MYSQL_DATABASE=fishbook
MYSQL_USER=fishbook
MYSQL_PASSWORD=fishbook_local_only
MYSQL_ROOT_PASSWORD=root_local_only
MINIO_ROOT_USER=fishbook_minio
MINIO_ROOT_PASSWORD=fishbook_minio_local_only
MINIO_BUCKET=fishbook-local
SPRING_PROFILES_ACTIVE=local
```

- [ ] **Step 4: Write the initial README contract**

Create `README.md` with these headings and commands: `Prerequisites`, `First Run`, `Backend`, `Frontend`, `Full Stack`, `Tests`, `Environment Variables`, and `Troubleshooting`. Under `First Run`, record:

```bash
cp .env.example .env
docker compose up -d
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
cd frontend && npm ci && npm run dev
```

State that `.env` contains local-only values and must never be committed.

- [ ] **Step 5: Verify repository policy files**

Run:

```bash
git diff --check
git status --short
```

Expected: no whitespace errors; only the five intended files are changed.

- [ ] **Step 6: Commit**

```bash
git add .nvmrc .editorconfig .gitignore .env.example README.md
git commit -m "chore: establish FishBook toolchain policy"
```

---

### Task 2: Define Local MySQL and MinIO Infrastructure

**Files:**
- Create: `compose.yaml`
- Modify: `README.md`

**Interfaces:**
- Consumes: variable names from `.env.example`.
- Produces: `mysql:3306`, `minio:9000`, and MinIO console `9001` for later backend adapters.

- [ ] **Step 1: Write the Compose model**

Create `compose.yaml`:

```yaml
name: fishbook

services:
  mysql:
    image: mysql:8.4
    environment:
      MYSQL_DATABASE: ${MYSQL_DATABASE}
      MYSQL_USER: ${MYSQL_USER}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD}
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      TZ: UTC
    command:
      - --character-set-server=utf8mb4
      - --collation-server=utf8mb4_0900_ai_ci
    ports:
      - "3306:3306"
    volumes:
      - mysql-data:/var/lib/mysql
    healthcheck:
      test: ["CMD-SHELL", "mysqladmin ping -h localhost -u root -p$${MYSQL_ROOT_PASSWORD}"]
      interval: 5s
      timeout: 5s
      retries: 20
      start_period: 20s

  minio:
    image: minio/minio:RELEASE.2025-09-07T16-13-09Z-cpuv1
    command: server /data --console-address :9001
    environment:
      MINIO_ROOT_USER: ${MINIO_ROOT_USER}
      MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD}
    ports:
      - "9000:9000"
      - "9001:9001"
    volumes:
      - minio-data:/data
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9000/minio/health/live"]
      interval: 5s
      timeout: 5s
      retries: 20
      start_period: 10s

volumes:
  mysql-data:
  minio-data:
```

- [ ] **Step 2: Validate the resolved configuration before starting containers**

Run:

```bash
cp .env.example .env
docker compose config --quiet
```

Expected: exit 0 and no unresolved-variable warning.

- [ ] **Step 3: Start dependencies and verify health**

Run:

```bash
docker compose up -d
docker compose ps
```

Expected: `mysql` and `minio` both become `healthy`.

- [ ] **Step 4: Verify MySQL encoding and UTC**

Run:

```bash
docker compose exec mysql mysql -ufishbook -pfishbook_local_only fishbook -e "SELECT @@character_set_server, @@collation_server, @@system_time_zone;"
```

Expected output includes `utf8mb4`, `utf8mb4_0900_ai_ci`, and `UTC`.

- [ ] **Step 5: Add reset and troubleshooting commands to README**

Document that `docker compose down` preserves data and `docker compose down -v` permanently deletes local MySQL and MinIO volumes. Mark the latter as destructive.

- [ ] **Step 6: Commit**

```bash
git add compose.yaml README.md
git commit -m "chore: add local MySQL and MinIO services"
```

---

### Task 3: Bootstrap Spring Boot and Prove the Health Boundary

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/mvnw`
- Create: `backend/mvnw.cmd`
- Create: `backend/.mvn/wrapper/maven-wrapper.properties`
- Create: `backend/src/main/java/com/fishbook/FishBookApplication.java`
- Create: `backend/src/main/java/com/fishbook/identity/security/SecurityConfig.java`
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/resources/application-local.yml`
- Test: `backend/src/test/java/com/fishbook/HealthEndpointTest.java`

**Interfaces:**
- Consumes: MySQL variables and Java 21.
- Produces: Spring application entry point and `GET /actuator/health`.

- [ ] **Step 1: Generate the Maven project with an exact Spring Boot release**

From the repository root, run:

```bash
curl -fsSLo /tmp/fishbook-backend.zip "https://start.spring.io/starter.zip?type=maven-project&language=java&bootVersion=4.1.0&groupId=com.fishbook&artifactId=backend&name=FishBook&packageName=com.fishbook&packaging=jar&javaVersion=21&dependencies=web,data-jpa,security,validation,actuator,flyway,mysql,session"
unzip -q /tmp/fishbook-backend.zip -d backend
```

Verify `backend/pom.xml` uses Spring Boot `4.1.0` and Java `21`. Edit the generated POM so each of these test dependencies appears exactly once:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>mysql</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 2: Write the failing health endpoint test**

Create `HealthEndpointTest.java`:

```java
package com.fishbook;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration"
})
@AutoConfigureMockMvc
class HealthEndpointTest {
    @Autowired
    MockMvc mvc;

    @Test
    void exposesHealthWithoutAuthentication() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
```

- [ ] **Step 3: Run the test and verify the intended failure**

Run:

```bash
cd backend && ./mvnw -Dtest=HealthEndpointTest test
```

Expected: FAIL because the application configuration and public health security rule are not complete.

- [ ] **Step 4: Add minimal application configuration**

Set `application.yml`:

```yaml
spring:
  application:
    name: fishbook-backend
  jackson:
    time-zone: UTC
  jpa:
    open-in-view: false
  session:
    jdbc:
      initialize-schema: never

management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      probes:
        enabled: true
```

Set `application-local.yml`:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/${MYSQL_DATABASE:fishbook}?connectionTimeZone=UTC
    username: ${MYSQL_USER:fishbook}
    password: ${MYSQL_PASSWORD:fishbook_local_only}
  flyway:
    enabled: true
```

Add a temporary `SecurityFilterChain` in `com.fishbook.identity.security.SecurityConfig` that permits `/actuator/health` and denies every other request. Task 6 replaces the deny-all rule with the full API policy.

- [ ] **Step 5: Run the test and full backend test suite**

Run:

```bash
cd backend && ./mvnw -Dtest=HealthEndpointTest test
cd backend && ./mvnw test
```

Expected: both commands PASS.

- [ ] **Step 6: Commit**

```bash
git add backend
git commit -m "feat: bootstrap Spring Boot health boundary"
```

---

### Task 4: Create the User and Session Schema with Real MySQL Tests

**Files:**
- Create: `backend/src/main/resources/db/migration/V1__create_users.sql`
- Create: `backend/src/main/resources/db/migration/V2__create_spring_session_tables.sql`
- Create: `backend/src/test/java/com/fishbook/support/MySqlTestConfiguration.java`
- Test: `backend/src/test/java/com/fishbook/DatabaseMigrationTest.java`

**Interfaces:**
- Consumes: Spring Boot datasource and Flyway configuration.
- Produces: `users`, `SPRING_SESSION`, and `SPRING_SESSION_ATTRIBUTES` tables.

- [ ] **Step 1: Write the failing migration integration test**

Create `MySqlTestConfiguration.java`:

```java
package com.fishbook.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MySQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class MySqlTestConfiguration {
    @Bean
    @ServiceConnection
    MySQLContainer<?> mysqlContainer() {
        return new MySQLContainer<>("mysql:8.4");
    }
}
```

Create `DatabaseMigrationTest.java`:

```java
package com.fishbook;

import static org.assertj.core.api.Assertions.assertThat;

import com.fishbook.support.MySqlTestConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(MySqlTestConfiguration.class)
class DatabaseMigrationTest {
    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void flywayCreatesIdentityAndSessionTables() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE()",
                String.class);

        assertThat(tables).contains("users", "SPRING_SESSION", "SPRING_SESSION_ATTRIBUTES");
    }
}
```

- [ ] **Step 2: Run the migration test and verify it fails**

Run:

```bash
cd backend && ./mvnw -Dtest=DatabaseMigrationTest test
```

Expected: FAIL because the tables do not exist.

- [ ] **Step 3: Create the users migration**

Create `V1__create_users.sql`:

```sql
CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    nickname VARCHAR(50) NOT NULL,
    role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

- [ ] **Step 4: Create the Spring Session migration**

Create `V2__create_spring_session_tables.sql`:

```sql
CREATE TABLE SPRING_SESSION (
    PRIMARY_ID CHAR(36) NOT NULL,
    SESSION_ID CHAR(36) NOT NULL,
    CREATION_TIME BIGINT NOT NULL,
    LAST_ACCESS_TIME BIGINT NOT NULL,
    MAX_INACTIVE_INTERVAL INT NOT NULL,
    EXPIRY_TIME BIGINT NOT NULL,
    PRINCIPAL_NAME VARCHAR(320),
    CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC;

CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE SPRING_SESSION_ATTRIBUTES (
    SESSION_PRIMARY_ID CHAR(36) NOT NULL,
    ATTRIBUTE_NAME VARCHAR(200) NOT NULL,
    ATTRIBUTE_BYTES BLOB NOT NULL,
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID)
        REFERENCES SPRING_SESSION(PRIMARY_ID) ON DELETE CASCADE
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC;
```

- [ ] **Step 5: Run migration and regression tests**

Run:

```bash
cd backend && ./mvnw -Dtest=DatabaseMigrationTest test
cd backend && ./mvnw test
```

Expected: PASS; Flyway applies both migrations to a clean MySQL 8.4 container.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/resources/db/migration backend/src/test/java/com/fishbook
git commit -m "feat: add user and session database schema"
```

---

### Task 5: Implement User Registration Behind Domain Ports

**Files:**
- Create: `backend/src/main/java/com/fishbook/identity/domain/User.java`
- Create: `backend/src/main/java/com/fishbook/identity/domain/UserRole.java`
- Create: `backend/src/main/java/com/fishbook/identity/domain/UserStatus.java`
- Create: `backend/src/main/java/com/fishbook/identity/domain/UserRepository.java`
- Create: `backend/src/main/java/com/fishbook/identity/domain/PasswordHasher.java`
- Create: `backend/src/main/java/com/fishbook/identity/domain/DuplicateEmailException.java`
- Create: `backend/src/main/java/com/fishbook/identity/domain/InvalidEmailException.java`
- Create: `backend/src/main/java/com/fishbook/identity/domain/InvalidPasswordException.java`
- Create: `backend/src/main/java/com/fishbook/identity/domain/InvalidNicknameException.java`
- Create: `backend/src/main/java/com/fishbook/identity/domain/UserNotFoundException.java`
- Create: `backend/src/main/java/com/fishbook/identity/application/RegisterUserCommand.java`
- Create: `backend/src/main/java/com/fishbook/identity/application/UserView.java`
- Create: `backend/src/main/java/com/fishbook/identity/application/AuthApplicationService.java`
- Create: `backend/src/main/java/com/fishbook/identity/application/DefaultAuthApplicationService.java`
- Create: `backend/src/main/java/com/fishbook/identity/persistence/UserJpaEntity.java`
- Create: `backend/src/main/java/com/fishbook/identity/persistence/SpringDataUserJpaRepository.java`
- Create: `backend/src/main/java/com/fishbook/identity/persistence/JpaUserRepositoryAdapter.java`
- Create: `backend/src/main/java/com/fishbook/identity/security/BcryptPasswordHasher.java`
- Test: `backend/src/test/java/com/fishbook/identity/application/DefaultAuthApplicationServiceTest.java`
- Test: `backend/src/test/java/com/fishbook/identity/persistence/JpaUserRepositoryAdapterTest.java`

**Interfaces:**
- Consumes: `users` table.
- Produces: the shared backend interfaces declared above and normalized lowercase email identity.

- [ ] **Step 1: Write registration service tests**

Create the test class with a fake `UserRepository`, fixed `Clock`, and fake `PasswordHasher`, then add these methods:

```java
@Test
void registersActiveUserWithNormalizedEmailAndHashedPassword() {
    UserView result = service.register(
            new RegisterUserCommand(" Angler@Example.COM ", "strong-pass", "Wall_E"));

    assertThat(result.email()).isEqualTo("angler@example.com");
    assertThat(result.nickname()).isEqualTo("Wall_E");
    assertThat(result.role()).isEqualTo("USER");
    assertThat(repository.savedUser().passwordHash()).isEqualTo("hashed:strong-pass");
}

@Test
void rejectsDuplicateNormalizedEmail() {
    repository.addExistingEmail("angler@example.com");

    assertThatThrownBy(() -> service.register(
            new RegisterUserCommand("ANGLER@example.com", "strong-pass", "Wall_E")))
            .isInstanceOf(DuplicateEmailException.class);
}
```

Add the boundary tests with exact values:

```java
@ParameterizedTest
@ValueSource(strings = {"123456789", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
void rejectsPasswordOutsideTenToOneHundredTwentyEightCharacters(String password) {
    assertThatThrownBy(() -> service.register(
            new RegisterUserCommand("angler@example.com", password, "Wall_E")))
            .isInstanceOf(InvalidPasswordException.class);
}

@ParameterizedTest
@ValueSource(strings = {"", "                                                   "})
void rejectsBlankNickname(String nickname) {
    assertThatThrownBy(() -> service.register(
            new RegisterUserCommand("angler@example.com", "strong-pass", nickname)))
            .isInstanceOf(InvalidNicknameException.class);
}

@Test
void rejectsNicknameLongerThanFiftyCharacters() {
    String nickname = "a".repeat(51);
    assertThatThrownBy(() -> service.register(
            new RegisterUserCommand("angler@example.com", "strong-pass", nickname)))
            .isInstanceOf(InvalidNicknameException.class);
}
```

- [ ] **Step 2: Run tests and verify failure**

Run:

```bash
cd backend && ./mvnw -Dtest=DefaultAuthApplicationServiceTest test
```

Expected: FAIL because domain and application types do not exist.

- [ ] **Step 3: Implement the minimal domain and service**

Implement email normalization as `trim().toLowerCase(Locale.ROOT)`. Lock these domain factories so persistence and application code use the same shape:

```java
public static User register(
        String normalizedEmail,
        String passwordHash,
        String nickname,
        Instant now);

public static User reconstitute(
        Long id,
        String normalizedEmail,
        String passwordHash,
        String nickname,
        UserRole role,
        UserStatus status,
        Instant createdAt,
        Instant updatedAt);
```

`User.register` creates an active `USER`; it accepts a pre-hashed password and a supplied `Instant`. `DefaultAuthApplicationService.register` validates boundaries, checks `existsByEmail`, hashes once, saves once, and maps to `UserView`.

Use these exception codes in domain exceptions:

```text
DUPLICATE_EMAIL
INVALID_EMAIL
INVALID_PASSWORD
INVALID_NICKNAME
```

- [ ] **Step 4: Run service tests and verify pass**

Run:

```bash
cd backend && ./mvnw -Dtest=DefaultAuthApplicationServiceTest test
```

Expected: PASS.

- [ ] **Step 5: Write the failing JPA adapter integration test**

Use `@DataJpaTest`, `@Import({MySqlTestConfiguration.class, JpaUserRepositoryAdapter.class})`, and MySQL Testcontainers. Write these two methods:

```java
@Test
void savesAndReconstructsDomainUser() {
    Instant now = Instant.parse("2026-08-07T00:00:00Z");
    User saved = adapter.save(User.register(
            "angler@example.com", "hashed", "Wall_E", now));
    entityManager.flush();
    entityManager.clear();

    User loaded = adapter.findByEmail("angler@example.com").orElseThrow();

    assertThat(loaded.id()).isEqualTo(saved.id());
    assertThat(loaded.email()).isEqualTo("angler@example.com");
    assertThat(loaded.passwordHash()).isEqualTo("hashed");
    assertThat(loaded.nickname()).isEqualTo("Wall_E");
    assertThat(loaded.role()).isEqualTo(UserRole.USER);
    assertThat(loaded.status()).isEqualTo(UserStatus.ACTIVE);
    assertThat(loaded.createdAt()).isEqualTo(now);
    assertThat(loaded.updatedAt()).isEqualTo(now);
}

@Test
void databaseRejectsDuplicateEmail() {
    Instant now = Instant.parse("2026-08-07T00:00:00Z");
    adapter.save(User.register("angler@example.com", "hash-1", "One", now));
    adapter.save(User.register("angler@example.com", "hash-2", "Two", now));

    assertThatThrownBy(entityManager::flush)
            .isInstanceOf(DataIntegrityViolationException.class);
}
```

- [ ] **Step 6: Implement explicit persistence mapping**

`UserJpaEntity` is the only `@Entity`. `User` remains free of JPA annotations. `JpaUserRepositoryAdapter` maps every field explicitly in `toDomain` and `toEntity`; it never returns `UserJpaEntity` outside the persistence package.

- [ ] **Step 7: Run identity and full backend tests**

Run:

```bash
cd backend && ./mvnw -Dtest=DefaultAuthApplicationServiceTest,JpaUserRepositoryAdapterTest test
cd backend && ./mvnw test
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/fishbook/identity backend/src/test/java/com/fishbook/identity
git commit -m "feat: implement identity registration domain"
```

---

### Task 6: Expose Secure Registration, Login, Logout, CSRF, and Profile APIs

**Files:**
- Create: `backend/src/main/java/com/fishbook/common/error/ApiErrorResponse.java`
- Create: `backend/src/main/java/com/fishbook/common/error/GlobalExceptionHandler.java`
- Modify: `backend/src/main/java/com/fishbook/identity/security/SecurityConfig.java`
- Create: `backend/src/main/java/com/fishbook/identity/security/DatabaseUserDetailsService.java`
- Create: `backend/src/main/java/com/fishbook/identity/security/LoginService.java`
- Create: `backend/src/main/java/com/fishbook/identity/application/ProfileApplicationService.java`
- Create: `backend/src/main/java/com/fishbook/identity/application/DefaultProfileApplicationService.java`
- Create: `backend/src/main/java/com/fishbook/identity/web/AuthController.java`
- Create: `backend/src/main/java/com/fishbook/identity/web/CsrfController.java`
- Create: `backend/src/main/java/com/fishbook/identity/web/MeController.java`
- Create: `backend/src/main/java/com/fishbook/identity/web/dto/RegisterRequest.java`
- Create: `backend/src/main/java/com/fishbook/identity/web/dto/LoginRequest.java`
- Create: `backend/src/main/java/com/fishbook/identity/web/dto/UpdateNicknameRequest.java`
- Create: `backend/src/main/java/com/fishbook/identity/web/dto/UserResponse.java`
- Create: `backend/src/main/java/com/fishbook/identity/web/dto/CsrfResponse.java`
- Create: `backend/src/main/java/com/fishbook/common/error/FieldErrorItem.java`
- Test: `backend/src/test/java/com/fishbook/identity/web/AuthFlowIntegrationTest.java`
- Test: `backend/src/test/java/com/fishbook/identity/web/IdentityAuthorizationTest.java`

**Interfaces:**
- Consumes: registration service, `UserRepository`, `PasswordHasher`, and Spring Session tables.
- Produces: all HTTP contracts locked at the top of this plan.

Use these exact web records:

```java
public record RegisterRequest(
        @Email @NotBlank String email,
        @Size(min = 10, max = 128) String password,
        @NotBlank @Size(max = 50) String nickname) {}

public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}

public record UpdateNicknameRequest(@NotBlank @Size(max = 50) String nickname) {}

public record UserResponse(long id, String email, String nickname, String role) {
    public static UserResponse from(UserView view) {
        return new UserResponse(view.id(), view.email(), view.nickname(), view.role());
    }
}

public record CsrfResponse(String token, String headerName) {}

public record FieldErrorItem(String field, String message) {}
```

- [ ] **Step 1: Write the failing registration HTTP test**

Using `@SpringBootTest`, `@AutoConfigureMockMvc`, and `@Import(MySqlTestConfiguration.class)`, write:

```java
@Test
void registersUserAndRejectsDuplicateNormalizedEmail() throws Exception {
    String body = """
            {"email":"Angler@Example.COM","password":"strong-pass","nickname":"Wall_E"}
            """;

    mvc.perform(post("/api/v1/auth/register")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value("angler@example.com"))
            .andExpect(jsonPath("$.role").value("USER"))
            .andExpect(jsonPath("$.password").doesNotExist());

    mvc.perform(post("/api/v1/auth/register")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
}
```

- [ ] **Step 2: Write failing login/session/security tests**

Write one complete session lifecycle test:

```java
@Test
void loginProfileRenameAndLogoutUseServerSessionAndCsrf() throws Exception {
    register("angler@example.com", "strong-pass", "Wall_E");

    MvcResult login = mvc.perform(post("/api/v1/auth/login")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"angler@example.com\",\"password\":\"strong-pass\"}"))
            .andExpect(status().isOk())
            .andExpect(cookie().exists("JSESSIONID"))
            .andReturn();
    MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

    mvc.perform(get("/api/v1/me").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nickname").value("Wall_E"));

    mvc.perform(patch("/api/v1/me")
                    .session(session)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"nickname\":\"River\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("CSRF_INVALID"));

    mvc.perform(patch("/api/v1/me")
                    .session(session)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"nickname\":\"River\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nickname").value("River"));

    mvc.perform(post("/api/v1/auth/logout").session(session).with(csrf()))
            .andExpect(status().isNoContent());

    mvc.perform(get("/api/v1/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
}
```

Add the separate failure and public-boundary methods:

```java
@Test
void invalidCredentialsReturn401AndStableCode() throws Exception {
    register("angler@example.com", "strong-pass", "Wall_E");
    mvc.perform(post("/api/v1/auth/login")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"angler@example.com\",\"password\":\"wrong-pass\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
}

@Test
void currentUserWithoutSessionReturns401() throws Exception {
    mvc.perform(get("/api/v1/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
}

@Test
void healthIsPublic() throws Exception {
    mvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
}

@Test
void csrfEndpointReturnsTokenAndHeaderName() throws Exception {
    mvc.perform(get("/api/v1/auth/csrf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"));
}
```

Use this helper in the same test class:

```java
private void register(String email, String password, String nickname) throws Exception {
    String body = objectMapper.writeValueAsString(
            new RegisterRequest(email, password, nickname));
    mvc.perform(post("/api/v1/auth/register")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated());
}
```

- [ ] **Step 3: Run tests and verify failure**

Run:

```bash
cd backend && ./mvnw -Dtest=AuthFlowIntegrationTest,IdentityAuthorizationTest test
```

Expected: FAIL because the HTTP and security adapters do not exist.

- [ ] **Step 4: Implement the security policy**

Configure `SecurityFilterChain` with these rules:

```java
authorize.requestMatchers("/actuator/health", "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/csrf").permitAll();
authorize.requestMatchers("/api/v1/me/**", "/api/v1/auth/logout").authenticated();
authorize.anyRequest().denyAll();
```

Use `CookieCsrfTokenRepository.withHttpOnlyFalse()` so the SPA can read the CSRF cookie and copy it into `X-XSRF-TOKEN`. Configure logout at `/api/v1/auth/logout`, require POST, invalidate the HTTP session, clear authentication, and delete `JSESSIONID`.

Set local cookie security through profile configuration and production defaults:

```yaml
server:
  servlet:
    session:
      timeout: 24h
      cookie:
        http-only: true
        same-site: lax
        secure: true
```

Override only `secure: false` in `application-local.yml`.

- [ ] **Step 5: Implement JSON login without JWT**

Lock the adapter signature:

```java
public UserView login(
        LoginRequest request,
        HttpServletRequest servletRequest,
        HttpServletResponse servletResponse);
```

`LoginService.login` authenticates with `AuthenticationManager`, invokes `SessionAuthenticationStrategy`, creates a fresh `SecurityContext`, saves it through `HttpSessionSecurityContextRepository`, and returns `UserView` for `AuthController` to map through `UserResponse.from`. Do not store credentials in the session and do not create a token endpoint.

- [ ] **Step 6: Implement controllers and stable error mapping**

Use Bean Validation on request records. `GlobalExceptionHandler` returns:

```java
public record ApiErrorResponse(
        String code,
        String message,
        List<FieldErrorItem> fieldErrors,
        String requestId) {}
```

Map duplicate email to `409`; validation to `400`; bad credentials to `401`; missing authentication to `401`; authorization/CSRF failures to `403`; unexpected exceptions to `500` without a stack trace in the response.

- [ ] **Step 7: Run targeted and full backend verification**

Run:

```bash
cd backend && ./mvnw -Dtest=AuthFlowIntegrationTest,IdentityAuthorizationTest test
cd backend && ./mvnw test
```

Expected: all tests PASS and the session row exists in `SPRING_SESSION` after login.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main backend/src/test
git commit -m "feat: add secure session authentication API"
```

---

### Task 7: Bootstrap the React Application Shell and Typed API Client

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/package-lock.json`
- Create: `frontend/index.html`
- Create: `frontend/tsconfig.json`, `frontend/tsconfig.app.json`, `frontend/tsconfig.node.json`
- Create: `frontend/vite.config.ts`
- Create: `frontend/src/main.tsx`
- Create: `frontend/src/app/App.tsx`
- Create: `frontend/src/app/providers.tsx`
- Create: `frontend/src/app/router.tsx`
- Create: `frontend/src/shared/api/types.ts`
- Create: `frontend/src/shared/api/ApiError.ts`
- Create: `frontend/src/shared/api/httpClient.ts`
- Create: `frontend/src/test/setup.ts`
- Create: `frontend/src/test/renderWithProviders.tsx`
- Test: `frontend/src/app/App.test.tsx`

**Interfaces:**
- Consumes: backend error and CSRF contracts.
- Produces: `apiFetch<T>()`, application providers, and public/protected route skeletons.

Create this exact error boundary in `ApiError.ts`:

```ts
export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly body: ApiErrorBody,
  ) {
    super(body.message);
  }

  static async fromResponse(response: Response): Promise<ApiError> {
    const body = (await response.json()) as ApiErrorBody;
    return new ApiError(response.status, body);
  }
}
```

- [ ] **Step 1: Create an exact frontend manifest and lockfile**

Create `package.json` with Node engine `>=24 <25`, React `19.2.0`, React DOM `19.2.0`, Vite `8.1.0`, and TypeScript strict scripts:

```json
{
  "name": "fishbook-frontend",
  "private": true,
  "version": "0.1.0",
  "type": "module",
  "engines": { "node": ">=24 <25" },
  "scripts": {
    "dev": "vite",
    "build": "tsc -b && vite build",
    "test": "vitest run",
    "test:watch": "vitest",
    "lint": "eslint .",
    "preview": "vite preview"
  },
  "dependencies": {
    "react": "19.2.0",
    "react-dom": "19.2.0"
  },
  "devDependencies": {
    "typescript": "5.9.3",
    "vite": "8.1.0"
  }
}
```

Install the remaining runtime and test dependencies with exact lockfile resolution:

```bash
cd frontend
npm install --save-exact react-router-dom @tanstack/react-query react-hook-form zod @hookform/resolvers
npm install --save-dev --save-exact @vitejs/plugin-react vitest jsdom @testing-library/react @testing-library/jest-dom @testing-library/user-event eslint typescript-eslint eslint-plugin-react-hooks eslint-plugin-react-refresh
```

- [ ] **Step 2: Write the failing application shell test**

```tsx
import { render, screen } from '@testing-library/react';
import { App } from './App';

test('renders the FishBook product identity', () => {
  render(<App />);
  expect(screen.getByRole('heading', { name: 'FishBook' })).toBeInTheDocument();
});
```

- [ ] **Step 3: Run and verify failure**

Run:

```bash
cd frontend && npm test -- App.test.tsx
```

Expected: FAIL because `App` and test setup do not exist.

- [ ] **Step 4: Implement the minimal app shell and providers**

`providers.tsx` creates one `QueryClient` outside render and nests `QueryClientProvider` and `RouterProvider`. `router.tsx` defines `/`, `/register`, `/login`, and `/profile`; auth pages can render headings until Tasks 8–9.

`renderWithProviders.tsx` creates a fresh `QueryClient` with retries disabled, wraps the supplied UI in `QueryClientProvider` and `MemoryRouter`, and returns `{ user, queryClient }` for tests. Never share a query client between test cases.

Also export this deterministic async-test helper from the same file:

```ts
export function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}
```

- [ ] **Step 5: Implement the typed fetch boundary**

`apiFetch<T>` must:

```ts
export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T>;
```

It sends `credentials: 'include'`, sends JSON content type when a body is a string, parses successful JSON, returns `undefined as T` for `204`, and throws `ApiError` containing `status` and `ApiErrorBody` for non-2xx responses.

Before every unsafe method (`POST`, `PUT`, `PATCH`, `DELETE`), call this exact bootstrap path if the `XSRF-TOKEN` cookie is absent:

```ts
async function ensureCsrfCookie(): Promise<void> {
  if (readCookie('XSRF-TOKEN')) return;
  const response = await fetch('/api/v1/auth/csrf', { credentials: 'include' });
  if (!response.ok) throw await ApiError.fromResponse(response);
}
```

After bootstrapping, read `XSRF-TOKEN` again and copy it into `X-XSRF-TOKEN`. Keep `ensureCsrfCookie` internal to `httpClient.ts` so registration, login, profile update, and logout all use the same rule.

- [ ] **Step 6: Run frontend checks**

Run:

```bash
cd frontend && npm test
cd frontend && npm run lint
cd frontend && npm run build
```

Expected: all commands PASS.

- [ ] **Step 7: Commit**

```bash
git add frontend
git commit -m "feat: bootstrap typed React application shell"
```

---

### Task 8: Build the Registration Page as a Tested Vertical UI Slice

**Files:**
- Create: `frontend/src/features/auth/model/types.ts`
- Create: `frontend/src/features/auth/api/authApi.ts`
- Create: `frontend/src/features/auth/pages/RegisterPage.tsx`
- Create: `frontend/src/features/auth/pages/RegisterPage.module.css`
- Create: `frontend/src/shared/ui/FormField.tsx`
- Test: `frontend/src/features/auth/pages/RegisterPage.test.tsx`

**Interfaces:**
- Consumes: `POST /api/v1/auth/register` and `ApiErrorBody`.
- Produces: `register(input): Promise<User>` and a validated registration route.

- [ ] **Step 1: Write the failing registration page tests**

Create a `renderRegisterPage()` helper with `MemoryRouter`, route `/register` to `RegisterPage`, and route `/login` to a small probe that renders `location.state.message`. Mock `authApi.register`, then write:

```tsx
test('shows all required errors without calling the API', async () => {
  const user = userEvent.setup();
  renderRegisterPage();

  await user.click(screen.getByRole('button', { name: '注册' }));

  expect(await screen.findByText('请输入有效邮箱')).toBeInTheDocument();
  expect(screen.getByText('密码至少 10 个字符')).toBeInTheDocument();
  expect(screen.getByText('请输入昵称')).toBeInTheDocument();
  expect(registerMock).not.toHaveBeenCalled();
});

test('submits normalized input and navigates to login', async () => {
  registerMock.mockResolvedValue({
    id: 1,
    email: 'angler@example.com',
    nickname: 'Wall_E',
    role: 'USER',
  });
  const user = userEvent.setup();
  renderRegisterPage();

  await user.type(screen.getByLabelText('邮箱'), ' Angler@Example.COM ');
  await user.type(screen.getByLabelText('密码'), 'strong-pass');
  await user.type(screen.getByLabelText('昵称'), ' Wall_E ');
  await user.click(screen.getByRole('button', { name: '注册' }));

  expect(registerMock).toHaveBeenCalledWith({
    email: 'Angler@Example.COM',
    password: 'strong-pass',
    nickname: 'Wall_E',
  });
  expect(await screen.findByText('注册成功，请登录')).toBeInTheDocument();
});

test('maps duplicate email to the email field', async () => {
  registerMock.mockRejectedValue(new ApiError(409, {
    code: 'DUPLICATE_EMAIL',
    message: '该邮箱已注册',
    fieldErrors: [],
    requestId: 'test-request',
  }));
  const user = userEvent.setup();
  renderRegisterPage();

  await user.type(screen.getByLabelText('邮箱'), 'angler@example.com');
  await user.type(screen.getByLabelText('密码'), 'strong-pass');
  await user.type(screen.getByLabelText('昵称'), 'Wall_E');
  await user.click(screen.getByRole('button', { name: '注册' }));

  expect(await screen.findByText('该邮箱已注册')).toBeInTheDocument();
});
```

- [ ] **Step 2: Run and verify failure**

Run:

```bash
cd frontend && npm test -- RegisterPage.test.tsx
```

Expected: FAIL because the page and API adapter do not exist.

- [ ] **Step 3: Implement exact validation schema**

```ts
export const registerSchema = z.object({
  email: z.string().trim().email('请输入有效邮箱'),
  password: z.string().min(10, '密码至少 10 个字符').max(128, '密码最多 128 个字符'),
  nickname: z.string().trim().min(1, '请输入昵称').max(50, '昵称最多 50 个字符'),
});
```

Use React Hook Form with `zodResolver`. Disable the submit button while pending. Render server errors in an `aria-live="polite"` region.

- [ ] **Step 4: Implement the API adapter**

```ts
export async function register(input: RegisterInput): Promise<User> {
  return apiFetch<User>('/api/v1/auth/register', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}
```

- [ ] **Step 5: Run targeted and full frontend checks**

Run:

```bash
cd frontend && npm test -- RegisterPage.test.tsx
cd frontend && npm test
cd frontend && npm run lint
cd frontend && npm run build
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/features/auth frontend/src/shared/ui frontend/src/app/router.tsx
git commit -m "feat: add tested user registration page"
```

---

### Task 9: Build Login, Session Restoration, Logout, and Profile Editing

**Files:**
- Create: `frontend/src/features/auth/api/currentUser.ts`
- Create: `frontend/src/features/auth/pages/LoginPage.tsx`
- Create: `frontend/src/features/auth/pages/ProfilePage.tsx`
- Create: `frontend/src/features/auth/components/ProtectedRoute.tsx`
- Create: `frontend/src/features/auth/components/SessionNav.tsx`
- Modify: `frontend/src/features/auth/api/authApi.ts`
- Modify: `frontend/src/app/router.tsx`
- Test: `frontend/src/features/auth/pages/LoginPage.test.tsx`
- Test: `frontend/src/features/auth/pages/ProfilePage.test.tsx`
- Test: `frontend/src/features/auth/components/ProtectedRoute.test.tsx`

**Interfaces:**
- Consumes: login, logout, CSRF, current-user, and nickname APIs.
- Produces: query key `['current-user']`, protected-route behavior, and session-aware navigation.

- [ ] **Step 1: Write failing login and protected-route tests**

Mock `authApi.login` and `currentUser.fetchCurrentUser`, then write:

```tsx
test('successful login caches the user without browser storage', async () => {
  loginMock.mockResolvedValue({
    id: 1,
    email: 'angler@example.com',
    nickname: 'Wall_E',
    role: 'USER',
  });
  const localStorageSpy = vi.spyOn(Storage.prototype, 'setItem');
  const { user, queryClient } = renderLoginPage();

  await user.type(screen.getByLabelText('邮箱'), 'angler@example.com');
  await user.type(screen.getByLabelText('密码'), 'strong-pass');
  await user.click(screen.getByRole('button', { name: '登录' }));

  expect(await screen.findByText('个人资料')).toBeInTheDocument();
  expect(queryClient.getQueryData(['current-user'])).toMatchObject({ id: 1 });
  expect(localStorageSpy).not.toHaveBeenCalled();
});

test('invalid credentials show the server message', async () => {
  loginMock.mockRejectedValue(new ApiError(401, {
    code: 'INVALID_CREDENTIALS',
    message: '邮箱或密码错误',
    fieldErrors: [],
    requestId: 'test-request',
  }));
  const { user } = renderLoginPage();

  await user.type(screen.getByLabelText('邮箱'), 'angler@example.com');
  await user.type(screen.getByLabelText('密码'), 'wrong-pass');
  await user.click(screen.getByRole('button', { name: '登录' }));

  expect(await screen.findByText('邮箱或密码错误')).toBeInTheDocument();
});

test('protected route waits for session lookup before redirecting', async () => {
  const session = deferred<User>();
  currentUserMock.mockReturnValue(session.promise);
  renderProtectedProfile();

  expect(screen.getByRole('status', { name: '正在检查登录状态' })).toBeInTheDocument();
  expect(screen.queryByText('登录')).not.toBeInTheDocument();

  session.reject(new ApiError(401, {
    code: 'AUTHENTICATION_REQUIRED',
    message: '请先登录',
    fieldErrors: [],
    requestId: 'test-request',
  }));
  expect(await screen.findByText('登录')).toBeInTheDocument();
});
```

- [ ] **Step 2: Write failing profile and logout tests**

Write profile and logout tests:

```tsx
test('updates nickname and current-user cache', async () => {
  updateNicknameMock.mockResolvedValue({
    id: 1,
    email: 'angler@example.com',
    nickname: 'River',
    role: 'USER',
  });
  const { user, queryClient } = renderAuthenticatedProfile();

  await user.clear(screen.getByLabelText('昵称'));
  await user.type(screen.getByLabelText('昵称'), 'River');
  await user.click(screen.getByRole('button', { name: '保存' }));

  expect(await screen.findByDisplayValue('River')).toBeInTheDocument();
  expect(queryClient.getQueryData<User>(['current-user'])?.nickname).toBe('River');
});

test('logout clears cache and returns to login', async () => {
  logoutMock.mockResolvedValue(undefined);
  const { user, queryClient } = renderAuthenticatedProfile();

  await user.click(screen.getByRole('button', { name: '退出登录' }));

  expect(await screen.findByText('登录')).toBeInTheDocument();
  expect(queryClient.getQueryData(['current-user'])).toBeUndefined();
});
```

Add the validation and server-error methods:

```tsx
test('blocks a blank nickname client-side', async () => {
  const { user } = renderAuthenticatedProfile();
  await user.clear(screen.getByLabelText('昵称'));
  await user.click(screen.getByRole('button', { name: '保存' }));

  expect(await screen.findByText('请输入昵称')).toBeInTheDocument();
  expect(updateNicknameMock).not.toHaveBeenCalled();
});

test('shows backend nickname validation error', async () => {
  updateNicknameMock.mockRejectedValue(new ApiError(400, {
    code: 'INVALID_NICKNAME',
    message: '昵称不可用',
    fieldErrors: [{ field: 'nickname', message: '昵称不可用' }],
    requestId: 'test-request',
  }));
  const { user } = renderAuthenticatedProfile();

  await user.clear(screen.getByLabelText('昵称'));
  await user.type(screen.getByLabelText('昵称'), 'Blocked');
  await user.click(screen.getByRole('button', { name: '保存' }));

  expect(await screen.findByText('昵称不可用')).toBeInTheDocument();
});
```

- [ ] **Step 3: Run and verify failure**

Run:

```bash
cd frontend && npm test -- LoginPage.test.tsx ProfilePage.test.tsx ProtectedRoute.test.tsx
```

Expected: FAIL because session UI modules do not exist.

- [ ] **Step 4: Implement auth API functions**

Expose exact functions:

```ts
export function fetchCsrf(): Promise<{ token: string; headerName: string }>;
export function login(input: LoginInput): Promise<User>;
export function logout(): Promise<void>;
export function fetchCurrentUser(): Promise<User>;
export function updateNickname(nickname: string): Promise<User>;
```

Use `queryKey: ['current-user']`, `retry: false` for `401`, and a five-minute stale time. A `401` means anonymous, not a global error toast.

- [ ] **Step 5: Implement the pages and route guard**

`ProtectedRoute` waits for the current-user query. It renders `<Navigate to="/login" replace />` only after a confirmed `401`. `LoginPage` never stores the session or password in browser storage. `SessionNav` shows profile/logout only when authenticated.

- [ ] **Step 6: Run all frontend checks**

Run:

```bash
cd frontend && npm test
cd frontend && npm run lint
cd frontend && npm run build
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/features/auth frontend/src/app
git commit -m "feat: add session-aware login and profile UI"
```

---

### Task 10: Package the Same-Origin Full Stack with Docker

**Files:**
- Create: `backend/Dockerfile`
- Create: `frontend/Dockerfile`
- Create: `frontend/nginx.conf`
- Create: `compose.full.yaml`
- Modify: `README.md`

**Interfaces:**
- Consumes: backend jar, frontend static build, MySQL, and MinIO.
- Produces: `http://localhost:8080` serving SPA routes and proxying `/api` and `/actuator` to Spring Boot.

- [ ] **Step 1: Write backend multi-stage image**

Use `eclipse-temurin:21-jdk` to run `./mvnw -B package` and `eclipse-temurin:21-jre` for the runtime. Run as a non-root user, expose `8080`, and use `/actuator/health/readiness` for the Compose health check.

Create `backend/Dockerfile`:

```dockerfile
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -DskipTests dependency:go-offline
COPY src src
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 fishbook \
    && useradd --system --uid 10001 --gid fishbook fishbook
WORKDIR /app
COPY --from=build /workspace/target/backend-0.0.1-SNAPSHOT.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

- [ ] **Step 2: Write frontend multi-stage image and same-origin proxy**

Use `node:24.18.0-alpine` to run `npm ci && npm run build`, then serve `dist` with an unprivileged Nginx image. `nginx.conf` must:

Create `frontend/Dockerfile`:

```dockerfile
FROM node:24.18.0-alpine AS build
WORKDIR /workspace
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.29.1-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html
EXPOSE 8080
```

Create `frontend/nginx.conf` with a complete server block:

```nginx
server {
    listen 8080;
    server_name _;
    root /usr/share/nginx/html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Request-ID $request_id;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /actuator/ {
        proxy_pass http://backend:8080;
    }
}
```

- [ ] **Step 3: Define the full-stack Compose overlay**

Create `compose.full.yaml`:

```yaml
services:
  backend:
    build:
      context: ./backend
    environment:
      SPRING_PROFILES_ACTIVE: local
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/${MYSQL_DATABASE}?connectionTimeZone=UTC
      SPRING_DATASOURCE_USERNAME: ${MYSQL_USER}
      SPRING_DATASOURCE_PASSWORD: ${MYSQL_PASSWORD}
    depends_on:
      mysql:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-fsS", "http://localhost:8080/actuator/health/readiness"]
      interval: 5s
      timeout: 5s
      retries: 20
      start_period: 20s

  frontend:
    build:
      context: ./frontend
    depends_on:
      backend:
        condition: service_healthy
    ports:
      - "8080:8080"
```

- [ ] **Step 4: Build and start the integration stack**

Run:

```bash
docker compose -f compose.yaml -f compose.full.yaml config --quiet
docker compose -f compose.yaml -f compose.full.yaml up -d --build
curl -fsS http://localhost:8080/actuator/health
curl -fsS http://localhost:8080/
```

Expected: health returns `UP`; root returns the FishBook HTML shell.

- [ ] **Step 5: Verify no backend port is public**

Run:

```bash
docker compose -f compose.yaml -f compose.full.yaml ps
```

Expected: only frontend `8080`, MySQL `3306`, and MinIO local-development ports are published; backend `8080` is internal.

- [ ] **Step 6: Commit**

```bash
git add backend/Dockerfile frontend/Dockerfile frontend/nginx.conf compose.full.yaml README.md
git commit -m "chore: package FishBook same-origin stack"
```

---

### Task 11: Add End-to-End Auth Coverage, CI, and Operator Documentation

**Files:**
- Create: `e2e/package.json`
- Create: `e2e/package-lock.json`
- Create: `e2e/playwright.config.ts`
- Create: `e2e/tests/auth-flow.spec.ts`
- Create: `.github/workflows/ci.yml`
- Modify: `README.md`
- Create: `docs/runbooks/local-development.md`

**Interfaces:**
- Consumes: full-stack Compose environment and all identity HTTP/UI contracts.
- Produces: repeatable auth acceptance proof and pull-request quality gate.

- [ ] **Step 1: Write the failing Playwright auth flow**

Create `e2e/tests/auth-flow.spec.ts`:

Initialize the package first:

```bash
mkdir -p e2e/tests
cd e2e
npm init -y
npm install --save-dev --save-exact @playwright/test typescript
npm pkg set private=true
npm pkg set scripts.test="playwright test"
```

```ts
import { expect, test } from '@playwright/test';

test('registers, restores the session, edits profile, and logs out', async ({ page }) => {
  const email = `angler-${Date.now()}@example.com`;

  await page.goto('/register');
  await page.getByLabel('邮箱').fill(email);
  await page.getByLabel('密码').fill('strong-pass');
  await page.getByLabel('昵称').fill('Wall_E');
  await page.getByRole('button', { name: '注册' }).click();
  await expect(page.getByText('注册成功，请登录')).toBeVisible();

  await page.getByLabel('邮箱').fill(email);
  await page.getByLabel('密码').fill('strong-pass');
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(/\/profile$/);
  await expect(page.getByText(email)).toBeVisible();

  await page.getByLabel('昵称').fill('River');
  await page.getByRole('button', { name: '保存' }).click();
  await expect(page.getByDisplayValue('River')).toBeVisible();

  await page.reload();
  await expect(page.getByDisplayValue('River')).toBeVisible();

  await page.getByRole('button', { name: '退出登录' }).click();
  await expect(page).toHaveURL(/\/login$/);
  await page.goto('/profile');
  await expect(page).toHaveURL(/\/login$/);
});
```

Do not bypass the UI or seed a user directly. Configure `trace: 'on-first-retry'` and `screenshot: 'only-on-failure'` in `playwright.config.ts`.

- [ ] **Step 2: Run the E2E test against the full stack**

Run:

```bash
docker compose -f compose.yaml -f compose.full.yaml up -d --build
cd e2e && npm ci && npx playwright install chromium
cd e2e && npm test
```

Expected before final wiring: FAIL at the first contract mismatch. Fix only the mismatch; do not weaken assertions.

- [ ] **Step 3: Re-run all verification layers**

Run:

```bash
cd backend && ./mvnw test
cd frontend && npm ci && npm run lint && npm test && npm run build
cd e2e && npm test
docker compose -f compose.yaml -f compose.full.yaml config --quiet
```

Expected: all commands PASS.

- [ ] **Step 4: Create the CI workflow**

Create `.github/workflows/ci.yml`:

```yaml
name: CI

on:
  pull_request:
  push:
    branches: [main]

jobs:
  backend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v5
      - uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: "21"
          cache: maven
      - run: ./mvnw -B test
        working-directory: backend

  frontend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v5
      - uses: actions/setup-node@v5
        with:
          node-version: 24.18.0
          cache: npm
          cache-dependency-path: frontend/package-lock.json
      - run: npm ci
        working-directory: frontend
      - run: npm run lint
        working-directory: frontend
      - run: npm test
        working-directory: frontend
      - run: npm run build
        working-directory: frontend

  docker-and-e2e:
    needs: [backend, frontend]
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v5
      - uses: actions/setup-node@v5
        with:
          node-version: 24.18.0
          cache: npm
          cache-dependency-path: e2e/package-lock.json
      - run: cp .env.example .env
      - run: docker compose -f compose.yaml -f compose.full.yaml up -d --build
      - run: |
          for attempt in $(seq 1 60); do
            curl -fsS http://localhost:8080/actuator/health && exit 0
            sleep 2
          done
          docker compose -f compose.yaml -f compose.full.yaml logs
          exit 1
      - run: npm ci
        working-directory: e2e
      - run: npx playwright install --with-deps chromium
        working-directory: e2e
      - run: npm test
        working-directory: e2e
      - if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: playwright-artifacts
          path: |
            e2e/playwright-report
            e2e/test-results
      - if: always()
        run: docker compose -f compose.yaml -f compose.full.yaml down
```

- [ ] **Step 5: Write the local development runbook**

Document fresh setup, normal start/stop, log inspection, Flyway failure diagnosis, session-table inspection, safe volume preservation, destructive reset warning, and the exact full verification command sequence.

- [ ] **Step 6: Run final plan acceptance checks**

Run:

```bash
git diff --check
git status --short
git log --oneline --decorate -12
```

Expected: clean worktree after the final commit; one focused commit per task.

- [ ] **Step 7: Commit**

```bash
git add e2e .github/workflows/ci.yml README.md docs/runbooks/local-development.md
git commit -m "test: verify identity slice end to end"
```

## Plan 1 Completion Gate

Do not start the Fish Catalog and Administration plan until all of these are true:

- A fresh clone can follow README and start local dependencies.
- Backend unit, security, migration, and MySQL integration tests pass.
- Frontend lint, unit tests, and production build pass.
- Playwright proves registration, login, session restoration, nickname editing, and logout through the UI.
- The full Docker stack serves frontend and backend through one origin.
- Sessions persist in MySQL and contain no raw credentials.
- API error responses match the approved `code/message/fieldErrors/requestId` structure.
- CI reproduces the complete verification on a clean runner.

## Primary References

- Spring Boot 4.1 system requirements: <https://docs.spring.io/spring-boot/system-requirements.html>
- Spring Security authentication persistence: <https://docs.spring.io/spring-security/reference/servlet/authentication/persistence.html>
- Spring Security CSRF: <https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html>
- Node.js release status: <https://nodejs.org/en/about/previous-releases>
- Vite supported releases: <https://vite.dev/releases>
- React 19.2: <https://react.dev/blog/2025/10/01/react-19-2>
