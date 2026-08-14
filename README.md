# FishBook（鱼类图鉴）

[简体中文](#zh-cn) | [English](#en)

<a id="zh-cn"></a>

## 简体中文

### 项目简介

FishBook 是一个面向中国淡水鱼知识学习的全栈鱼类图鉴项目，也是一套用于练习真实软件工程流程的学习型应用。项目目前提供公开只读鱼类图鉴、完整的用户身份闭环和登录用户私有收藏，并通过同源部署将 React 前端与 Spring Boot API 统一运行在一个地址下。

当前版本收录 12 种经过整理的常见淡水鱼：鲫、鲤、草鱼、青鱼、鲢、鳙、乌鳢、鳜、黄颡鱼、团头鲂、翘嘴鲌和泥鳅。鱼类图片均保存在项目中，并记录来源、作者和许可证信息。

### 当前功能

**公开只读鱼类图鉴**

- 无需登录即可浏览 12 种鱼类。
- 按中文正式名、别名或科学学名搜索。
- 按中文科名和栖息环境组合筛选。
- 将搜索、筛选和页码保存在 URL 中，支持刷新、前进、后退和分享链接。
- 使用稳定 slug 打开鱼类详情页，展示分类、别名、外形、体型、栖息环境、分布和介绍。
- 显示图片作者、原始来源和许可证。
- 处理加载中、无结果、请求失败、鱼类不存在和图片加载失败等状态。

**用户身份**

- 注册、登录和退出登录。
- 使用 JDBC 持久化会话，刷新页面后可以恢复登录状态。
- 查看个人资料并修改昵称。
- 使用 CSRF 防护、会话固定攻击防护和 HttpOnly 会话 Cookie。
- 使用 BCrypt-SHA256 对密码进行安全哈希。

**个人收藏**

- 登录用户可以从图鉴卡片或鱼类详情收藏、取消收藏鱼类。
- “我的收藏”页面按用户隔离展示私有收藏，并支持分页和持久化取消收藏。
- 重复收藏和重复取消收藏均采用幂等处理，不会产生重复数据。

图鉴内容目前保持公开只读。管理员后台、图鉴新增与编辑、图片上传和钓获记录尚未实现，属于后续开发范围。

### 技术栈

| 层级 | 技术 |
| --- | --- |
| 前端 | React 19、TypeScript 5.9、Vite 8、React Router、TanStack Query、React Hook Form、Zod |
| 后端 | Java 21、Spring Boot 4.1、Spring Web MVC、Spring Data JPA |
| 身份与安全 | Spring Security、Spring Session JDBC、CSRF、BCrypt-SHA256 |
| 数据库 | MySQL 8.4、Flyway |
| 基础设施 | Docker Compose、Nginx、MinIO |
| 测试 | JUnit、Testcontainers、Vitest、Testing Library、Playwright |

Node.js 版本固定为 `24.18.0`。前端和端到端测试依赖均通过各自的 `package-lock.json` 锁定。

### 系统架构

```text
浏览器
  → Nginx + React 单页应用
  → Spring Boot API
  → MySQL
```

- Nginx 在 `http://localhost:8080` 提供前端，并将 `/api` 和 `/actuator` 转发到内部后端服务。
- Spring Boot 按领域、应用、持久化和 Web 边界组织用户与图鉴功能。
- Flyway 管理数据库表结构和首批鱼类数据迁移。
- Spring Session 将登录会话保存到 MySQL。
- MinIO 已作为未来对象存储基础设施运行；当前图鉴图片是经过授权核验、由前端同源提供的本地静态资源。

### 快速开始

#### 环境要求

- Temurin JDK 21
- nvm 与 Node.js 24.18.0
- Docker Desktop（包含 Docker Compose）
- Git

#### 启动完整应用

```bash
git clone https://github.com/hu-xiaofei/Fish_Book.git
cd Fish_Book
nvm install 24.18.0
nvm use 24.18.0
test -f .env || cp .env.example .env
docker compose -f compose.yaml -f compose.full.yaml up -d --build
docker compose -f compose.yaml -f compose.full.yaml ps
```

`.env` 仅用于本地开发，请勿提交到 Git。等待 MySQL、MinIO 和后端显示为健康状态后，打开 [http://localhost:8080/](http://localhost:8080/)。

停止服务但保留 MySQL 和 MinIO 数据卷：

```bash
docker compose -f compose.yaml -f compose.full.yaml down
```

### 常用访问地址

| 功能 | 地址 |
| --- | --- |
| 鱼类图鉴首页 | [http://localhost:8080/](http://localhost:8080/) |
| 乌鳢详情示例 | [http://localhost:8080/fish/channa-argus](http://localhost:8080/fish/channa-argus) |
| 注册 | [http://localhost:8080/register](http://localhost:8080/register) |
| 登录 | [http://localhost:8080/login](http://localhost:8080/login) |
| 个人资料 | [http://localhost:8080/profile](http://localhost:8080/profile) |
| 我的收藏 | [http://localhost:8080/favorites](http://localhost:8080/favorites) |
| 健康检查 | [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health) |

### 测试与验证

在仓库根目录按顺序执行：

```bash
cd backend && ./mvnw test
cd ../frontend && npm ci && npm run lint && npm test && npm run build
cd ../e2e && npm ci && npx playwright install chromium && npm test
cd .. && docker compose --env-file .env.example -f compose.yaml -f compose.full.yaml config --quiet
```

- 后端测试使用 Testcontainers 启动真实 MySQL，因此需要 Docker 正在运行。
- Playwright 测试需要先通过完整 Docker Compose 命令启动应用，并覆盖身份、公开图鉴和私有收藏主流程。
- 以上是与 CI 覆盖范围一致的本地验证流程。GitHub Actions 会在推送到 `main` 和 Pull Request 时执行后端、前端、Docker 与端到端测试；Linux CI 还会使用 Maven 批处理模式、安装 Playwright 系统依赖，并在端到端测试前启动和等待完整服务栈。

### 项目结构

```text
Fish_Book/
├── backend/           # Spring Boot API、领域逻辑、Flyway 迁移和后端测试
├── frontend/          # React 应用、鱼类图片和前端测试
├── e2e/               # Playwright 真实浏览器流程
├── docs/              # 设计规格、实施计划、运行手册和数据来源
├── compose.yaml       # MySQL 与 MinIO 基础服务
└── compose.full.yaml  # 后端与前端完整应用服务
```

### 当前范围与后续方向

当前交付已包含稳定的身份系统、公开只读图鉴和登录用户私有收藏。下一阶段可以继续开发：

- 管理员账号初始化和基于角色的权限控制；
- 鱼类新增、编辑、发布和下架；
- 图片上传与 MinIO 对象存储；
- 钓获记录和更多个人学习功能。

仓库目前没有项目级应用许可证文件，因此不要据此推断应用代码的开源授权。鱼类图片使用各自的开放许可证，详情见图片来源记录。

### 相关文档

- [本地开发与故障排查手册](docs/runbooks/local-development.md)
- [鱼类资料与图片来源记录](docs/data-sources/fish-catalog-attribution.md)
- [FishBook MVP 设计规格](docs/superpowers/specs/2026-08-07-fishbook-mvp-design.md)
- [鱼类图鉴核心设计规格](docs/superpowers/specs/2026-08-11-fish-catalog-core-design.md)

---

<a id="en"></a>

## English

### Overview

FishBook is a learning-oriented full-stack fish encyclopedia focused on Chinese freshwater fish and on practicing a realistic software engineering workflow. The current application provides a public read-only fish catalog, a complete identity flow, and private favorites for authenticated users, with the React frontend and Spring Boot API served from the same origin.

The catalog currently contains 12 curated freshwater species: crucian carp, common carp, grass carp, black carp, silver carp, bighead carp, northern snakehead, mandarin fish, yellow catfish, Wuchang bream, topmouth culter, and weather loach. Every catalog image is stored locally with recorded source, author, and license metadata.

### Current Features

**Public read-only fish catalog**

- Browse all 12 fish species without signing in.
- Search by official Chinese name, alias, or scientific name.
- Combine exact Chinese family and habitat filters.
- Preserve search, filter, and page state in the URL for reloads, browser navigation, and link sharing.
- Open stable-slug detail pages with taxonomy, aliases, appearance, size, habitat, distribution, and descriptive content.
- Display image author, original source, and license information.
- Handle loading, empty, request-error, missing-fish, and image-error states.

**Identity**

- Register, sign in, and sign out.
- Restore authenticated sessions after reload through JDBC-backed session storage.
- View a profile and update its nickname.
- Protect requests with CSRF defense, session-fixation protection, and HttpOnly session cookies.
- Hash passwords with BCrypt-SHA256.

**Personal favorites**

- Authenticated users can add or remove favorites from catalog cards and fish details.
- The “My Favorites” page keeps each user's favorites private and supports pagination and persistent removal.
- Repeated add and remove requests are idempotent and do not create duplicate data.

Catalog content remains read-only. The admin management UI, catalog writes, image uploads, and catch records are not implemented yet and remain future work.

### Tech Stack

| Layer | Technologies |
| --- | --- |
| Frontend | React 19, TypeScript 5.9, Vite 8, React Router, TanStack Query, React Hook Form, Zod |
| Backend | Java 21, Spring Boot 4.1, Spring Web MVC, Spring Data JPA |
| Identity and security | Spring Security, Spring Session JDBC, CSRF, BCrypt-SHA256 |
| Database | MySQL 8.4, Flyway |
| Infrastructure | Docker Compose, Nginx, MinIO |
| Testing | JUnit, Testcontainers, Vitest, Testing Library, Playwright |

Node.js is pinned to `24.18.0`. Frontend and end-to-end dependencies are locked through their respective `package-lock.json` files.

### Architecture

```text
Browser
  → Nginx + React SPA
  → Spring Boot API
  → MySQL
```

- Nginx serves the frontend at `http://localhost:8080` and proxies `/api` and `/actuator` to the internal backend service.
- Spring Boot separates the identity and catalog features across domain, application, persistence, and Web boundaries.
- Flyway owns database schema and initial catalog-data migrations.
- Spring Session stores authenticated sessions in MySQL.
- MinIO is provisioned as infrastructure for future object storage. Current catalog images are audited local static assets served by the frontend from the same origin.

### Quick Start

#### Prerequisites

- Temurin JDK 21
- nvm and Node.js 24.18.0
- Docker Desktop with Docker Compose
- Git

#### Start the full application

```bash
git clone https://github.com/hu-xiaofei/Fish_Book.git
cd Fish_Book
nvm install 24.18.0
nvm use 24.18.0
test -f .env || cp .env.example .env
docker compose -f compose.yaml -f compose.full.yaml up -d --build
docker compose -f compose.yaml -f compose.full.yaml ps
```

Keep `.env` local and never commit it. Wait until MySQL, MinIO, and the backend report healthy status, then open [http://localhost:8080/](http://localhost:8080/).

Stop the services while preserving the MySQL and MinIO data volumes:

```bash
docker compose -f compose.yaml -f compose.full.yaml down
```

### Local URLs

| Feature | URL |
| --- | --- |
| Fish catalog | [http://localhost:8080/](http://localhost:8080/) |
| Northern snakehead example | [http://localhost:8080/fish/channa-argus](http://localhost:8080/fish/channa-argus) |
| Registration | [http://localhost:8080/register](http://localhost:8080/register) |
| Login | [http://localhost:8080/login](http://localhost:8080/login) |
| Profile | [http://localhost:8080/profile](http://localhost:8080/profile) |
| My Favorites | [http://localhost:8080/favorites](http://localhost:8080/favorites) |
| Health endpoint | [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health) |

### Tests and Verification

Run these commands in order from the repository root:

```bash
cd backend && ./mvnw test
cd ../frontend && npm ci && npm run lint && npm test && npm run build
cd ../e2e && npm ci && npx playwright install chromium && npm test
cd .. && docker compose --env-file .env.example -f compose.yaml -f compose.full.yaml config --quiet
```

- Backend tests use Testcontainers with a real MySQL instance, so Docker must be running.
- Playwright requires the full application stack to be running first and covers the identity, public-catalog, and private-favorites flows.
- The commands above are the local equivalent of the CI verification scope. GitHub Actions runs backend, frontend, Docker, and end-to-end checks for pushes to `main` and for pull requests; Linux CI additionally uses Maven batch mode, installs Playwright system dependencies, and starts and waits for the full stack before the end-to-end tests.

### Project Structure

```text
Fish_Book/
├── backend/           # Spring Boot API, domain logic, Flyway migrations, and backend tests
├── frontend/          # React application, fish images, and frontend tests
├── e2e/               # Real-browser Playwright flows
├── docs/              # Design specs, implementation plans, runbooks, and data provenance
├── compose.yaml       # MySQL and MinIO infrastructure services
└── compose.full.yaml  # Full backend and frontend application services
```

### Current Scope and Next Steps

The current delivery includes a stable identity system, a public read-only catalog, and private favorites for authenticated users. Natural next steps include:

- administrator bootstrap and role-based authorization;
- create, edit, publish, and unpublish catalog workflows;
- image uploads backed by MinIO object storage;
- catch records and additional personal learning features.

The repository does not currently contain a project-level application license file, so no open-source license should be inferred for the application code. Fish images retain their individual open licenses; see the attribution record for details.

### Documentation

- [Local development and troubleshooting runbook](docs/runbooks/local-development.md)
- [Fish data and image attribution record](docs/data-sources/fish-catalog-attribution.md)
- [FishBook MVP design specification](docs/superpowers/specs/2026-08-07-fishbook-mvp-design.md)
- [Fish catalog core design specification](docs/superpowers/specs/2026-08-11-fish-catalog-core-design.md)
