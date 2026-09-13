# FishBook（鱼类图鉴）

[简体中文](#zh-cn) | [English](#en)

<a id="zh-cn"></a>

## 简体中文

### 项目简介

FishBook 是一个面向中国淡水鱼知识学习的全栈鱼类图鉴项目，也是一套用于练习真实软件工程流程的学习型应用。项目目前提供公开鱼类图鉴、完整的用户身份闭环、登录用户私有收藏、带可选私有照片的钓获记录，以及受角色保护的图鉴管理闭环，并通过同源部署将 React 前端与 Spring Boot API 统一运行在一个地址下。

当前版本收录 12 种经过整理的常见淡水鱼：鲫、鲤、草鱼、青鱼、鲢、鳙、乌鳢、鳜、黄颡鱼、团头鲂、翘嘴鲌和泥鳅。鱼类图片均保存在项目中，并记录来源、作者和许可证信息。

### 项目状态

当前产品闭环已交付身份、公开图鉴、按账号隔离的私有收藏、带可选私有照片的钓获记录，以及管理员图鉴维护和照片管理。照片由私有 MinIO 存储；用户接口校验记录所有权，独立管理员接口允许查看、替换和删除所有用户照片；图鉴写入只允许管理员执行。

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

**钓获记录与私有照片**

- 登录用户可以创建、查看、编辑和删除自己的钓获记录，并关联既有鱼种、日期、地点、长度、重量、钓法和备注。
- “钓获记录”列表和详情均按账号隔离；访问其他用户的记录会得到统一的未找到结果。
- 每条记录可选上传一张不超过 10 MiB 的 JPEG、PNG 或 WebP 照片，并可在详情页替换或移除。
- 照片通过需要登录的后端接口读取：用户接口校验所有权，其他用户的照片统一返回未找到；管理员通过独立管理接口查看、替换和删除照片。照片响应禁止存储缓存。
- 新建时照片上传失败不会撤销已经保存的记录，用户可在详情页重试。

**管理员图鉴管理**

- 可选的首次启动引导会创建本地管理员；管理列表、新建和编辑路由分别为 `/admin/fishes`、`/admin/fishes/new` 和 `/admin/fishes/{id}/edit`。
- 管理员可以新建草稿、编辑内容、发布和下架鱼类；草稿与已下架条目不会出现在公开搜索或公开详情中。
- `/api/v1/admin/**` 同时执行登录、管理员角色和 CSRF 校验；普通用户在本地管理页面看到“没有管理员权限”，API 请求得到 `403`。
- 管理员还可通过 `/admin/photos` 按所属用户 ID 筛选、预览、确认替换和删除照片，并查看成功操作记录。钓获详细字段和收藏仍私有，不提供任意钓获记录修改；照片操作使用真实版本，冲突后须重新确认，旧照片不可恢复。

图鉴封面上传、鱼类条目的物理删除和复杂 RBAC 不在当前范围内；当前图鉴仍使用仓库内经过来源审计的公开图片。

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
  → Spring Boot API（identity、catalog、administration、favorites、catchlog）
      → MySQL（业务数据、会话、媒体清理任务）
      → MinIO（私有钓获照片）
```

- Nginx 在 `http://localhost:8080` 提供前端，并将 `/api` 和 `/actuator` 转发到内部后端服务。
- Spring Boot 按领域、应用、持久化和 Web 边界组织 identity、catalog、administration、favorites 与 catchlog 功能。
- Flyway 管理数据库表结构和首批鱼类数据迁移。
- Spring Session 将登录会话保存到 MySQL。
- MinIO 保存按用户和记录隔离的私有钓获照片；浏览器通过后端的所有者接口或独立管理员接口读取，不接收公开或预签名对象地址。
- 图鉴图片仍是经过授权核验、由前端同源提供的公开本地静态资源。

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

首次本地启动时，`.env.example` 包含以下仅供开发使用的管理员引导样例：

```dotenv
FISHBOOK_ADMIN_BOOTSTRAP_ENABLED=true
FISHBOOK_ADMIN_EMAIL=admin@fishbook.local
FISHBOOK_ADMIN_PASSWORD=fishbook_admin_local_only
FISHBOOK_ADMIN_NICKNAME=本地管理员
```

应用配置默认关闭管理员引导；完整 Compose 栈会把上述 `.env` 值传给后端。首次启动完成后，在 `/login` 使用样例账号登录，并从导航进入 `/admin/fishes`。确认管理员已创建后，建议把 `FISHBOOK_ADMIN_BOOTSTRAP_ENABLED` 改为 `false` 并重启。若邮箱已属于管理员，后续启动是安全的无操作；若同一邮箱已属于普通用户，后端会拒绝启动，且不会提升该账号权限。

这些值不是生产凭据。生产环境必须从部署平台的密钥存储注入独立强密码，绝不能把生产秘密写入或提交 `.env` 文件。

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
| 钓获记录 | [http://localhost:8080/catches](http://localhost:8080/catches) |
| 新建钓获记录 | [http://localhost:8080/catches/new](http://localhost:8080/catches/new) |
| 图鉴管理 | [http://localhost:8080/admin/fishes](http://localhost:8080/admin/fishes) |
| 健康检查 | [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health) |

### 测试与验证

在仓库根目录按顺序执行：

```bash
cd backend && ./mvnw test
cd ../frontend && npm ci && npm run lint && npm test && npm run build
cd ../e2e && npm ci && npx playwright install chromium
npm run test:preflight && npm run test:isolated
```

- 后端测试使用 Testcontainers 启动真实 MySQL，因此需要 Docker 正在运行。
- `test:isolated` 使用 `.env.example` 创建全新专用 Compose 项目，前端绑定动态 IPv4 loopback 端口，数据库/MinIO 不发布主机端口；等待健康后运行所有浏览器流程和管理员审计断言，完成或失败后精确删除该项目的测试数据。需要 Docker Compose 2.24.4+，不能使用已有用户数据栈。
- CI 与本地共用这个隔离入口；Linux CI 额外安装 Playwright 系统依赖。直接 `npm test` 仅允许显式指定已经验证的可丢弃项目，缺少环境会在任何浏览器测试前拒绝执行。详见[管理员照片验收](docs/runbooks/admin-photo-management.md#独立本地验收)。本地等效入口已验证，远程 GitHub Actions 本轮未运行。

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

当前交付已包含稳定的身份系统、公开图鉴、管理员初始化与角色授权、管理员鱼类新增/编辑/发布/下架、登录用户私有收藏，以及带可选私有照片的钓获记录 CRUD。下一阶段可以继续开发：

- 管理员图鉴封面上传和鱼类条目物理删除；
- 更复杂的 RBAC 与生产部署、安全密钥轮换和备份方案；
- 私有媒体备份、容量监控和运维告警。

仓库目前没有项目级应用许可证文件，因此不要据此推断应用代码的开源授权。鱼类图片使用各自的开放许可证，详情见图片来源记录。

### 相关文档

- [本地开发与故障排查手册](docs/runbooks/local-development.md)
- [OSS 私有照片接入](docs/runbooks/oss-private-media.md)：实现与云验收前置条件。
- [管理员照片管理](docs/runbooks/admin-photo-management.md)：权限、版本协议、操作记录及独立本地验收证据。
- [鱼类资料与图片来源记录](docs/data-sources/fish-catalog-attribution.md)
- [FishBook MVP 设计规格](docs/superpowers/specs/2026-08-07-fishbook-mvp-design.md)
- [鱼类图鉴核心设计规格](docs/superpowers/specs/2026-08-11-fish-catalog-core-design.md)
- [个人产品闭环设计规格](docs/superpowers/specs/2026-08-14-personal-product-loop-design.md)
- [个人收藏实施计划](docs/superpowers/plans/2026-08-14-personal-favorites.md)
- [私有钓获照片实施计划](docs/superpowers/plans/2026-08-14-catch-photo-media.md)

---

<a id="en"></a>

## English

### Overview

FishBook is a learning-oriented full-stack fish encyclopedia focused on Chinese freshwater fish and on practicing a realistic software engineering workflow. The current application provides a public fish catalog, a complete identity flow, private favorites, catch records with optional private photos, and a role-protected catalog-management loop, with the React frontend and Spring Boot API served from the same origin.

The catalog currently contains 12 curated freshwater species: crucian carp, common carp, grass carp, black carp, silver carp, bighead carp, northern snakehead, mandarin fish, yellow catfish, Wuchang bream, topmouth culter, and weather loach. Every catalog image is stored locally with recorded source, author, and license metadata.

### Project Status

The current product loop delivers identity, a public catalog, account-isolated private favorites, catch records with optional private photos, and administrator catalog and photo management. Photos stay in a private MinIO bucket. Owner endpoints enforce record ownership; separate administrator endpoints allow viewing, replacing, and deleting user photos. Catalog writes are restricted to administrators.

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

**Catch records and private photos**

- Authenticated users can create, view, edit, and delete their own catch records, linked to an existing fish species with date, location, length, weight, method, and notes.
- Catch lists and details are account-isolated; another user's record produces the same not-found state as a missing record.
- Each record can optionally hold one JPEG, PNG, or WebP photo up to 10 MiB, which can be replaced or removed from the detail page.
- Authenticated owner endpoints return the same not-found result for missing and foreign-owned photos. Separate administrator endpoints allow photo management. Private photo responses use `Cache-Control: private, no-store`.
- A photo upload failure during creation does not roll back the saved record, so the user can retry from its detail page.

**Administrator catalog management**

- An optional first-start bootstrap creates the local administrator; list, create, and edit routes are `/admin/fishes`, `/admin/fishes/new`, and `/admin/fishes/{id}/edit`.
- Administrators can create drafts, edit content, publish, and unpublish fish. Draft and unpublished entries stay absent from public search and public detail pages.
- `/api/v1/admin/**` enforces authentication, the administrator role, and CSRF. An ordinary user sees the local “没有管理员权限” page and receives `403` from the API.
- Administrators can also filter photos by owner ID, preview, confirm replacement/deletion, and read successful operation history at `/admin/photos`. Detailed catch fields and favorites remain private; no generic catch editing is granted. Photo writes require the observed revision and renewed confirmation after conflicts; old photos cannot be restored.

Catalog cover upload, physical fish deletion, and complex RBAC remain out of scope. The catalog continues to use audited public images stored in the repository.

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
  → Spring Boot API (identity, catalog, administration, favorites, catchlog)
      → MySQL (business data, sessions, and media-cleanup jobs)
      → MinIO (private catch photos)
```

- Nginx serves the frontend at `http://localhost:8080` and proxies `/api` and `/actuator` to the internal backend service.
- Spring Boot separates the identity, catalog, administration, favorites, and catchlog features across domain, application, persistence, and Web boundaries.
- Flyway owns database schema and initial catalog-data migrations.
- Spring Session stores authenticated sessions in MySQL.
- MinIO stores private catch photos under user- and record-scoped keys. Browsers retrieve photos through owner-authorized or separate administrator endpoints and receive no public or presigned object URLs.
- Catalog images remain audited public static assets served locally by the frontend from the same origin.

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

For the first local start, `.env.example` contains these development-only administrator bootstrap samples:

```dotenv
FISHBOOK_ADMIN_BOOTSTRAP_ENABLED=true
FISHBOOK_ADMIN_EMAIL=admin@fishbook.local
FISHBOOK_ADMIN_PASSWORD=fishbook_admin_local_only
FISHBOOK_ADMIN_NICKNAME=本地管理员
```

Administrator bootstrap is disabled by default in application configuration; the full Compose stack passes the `.env` values into the backend. After the first start, sign in at `/login` with the sample account and open `/admin/fishes` from the navigation. Once the administrator exists, set `FISHBOOK_ADMIN_BOOTSTRAP_ENABLED=false` and restart. A matching existing administrator is a safe no-op; if the email already belongs to an ordinary user, startup fails rather than promoting that account.

These values are not production credentials. Production deployments must inject a separate strong password from deployment secret storage and must never store or commit production secrets in `.env` files.

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
| Catch records | [http://localhost:8080/catches](http://localhost:8080/catches) |
| New catch record | [http://localhost:8080/catches/new](http://localhost:8080/catches/new) |
| Catalog management | [http://localhost:8080/admin/fishes](http://localhost:8080/admin/fishes) |
| Health endpoint | [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health) |

### Tests and Verification

Run these commands in order from the repository root:

```bash
cd backend && ./mvnw test
cd ../frontend && npm ci && npm run lint && npm test && npm run build
cd ../e2e && npm ci && npx playwright install chromium
npm run test:preflight && npm run test:isolated
```

- Backend tests use Testcontainers with a real MySQL instance, so Docker must be running.
- `test:isolated` creates a fresh dedicated Compose project using `.env.example`, a dynamic IPv4 loopback frontend port, and no published database/MinIO ports. It waits for health, runs every browser flow including the administrator database audit, then deletes only that project's disposable data on success or failure. Docker Compose 2.24.4+ is required; never use an existing user-data stack.
- CI shares this isolated entry; Linux additionally installs Playwright system dependencies. Plain `npm test` requires an explicit verified disposable project and fails before any browser test when it is missing. See [administrator photo acceptance](docs/runbooks/admin-photo-management.md#独立本地验收). The equivalent local entry was verified; remote GitHub Actions was not run in this change.

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

The current delivery includes a stable identity system, a public catalog, administrator bootstrap and role authorization, administrator create/edit/publish/unpublish workflows, private favorites, and catch-record CRUD with optional private photos. Natural next steps include:

- administrator catalog-cover upload and physical fish deletion;
- more complex RBAC plus production deployment, secret rotation, and backup procedures;
- private-media backup, capacity monitoring, and operational alerts.

The repository does not currently contain a project-level application license file, so no open-source license should be inferred for the application code. Fish images retain their individual open licenses; see the attribution record for details.

### Documentation

- [Local development and troubleshooting runbook](docs/runbooks/local-development.md)
- [Fish data and image attribution record](docs/data-sources/fish-catalog-attribution.md)
- [FishBook MVP design specification](docs/superpowers/specs/2026-08-07-fishbook-mvp-design.md)
- [Fish catalog core design specification](docs/superpowers/specs/2026-08-11-fish-catalog-core-design.md)
- [Personal product-loop design specification](docs/superpowers/specs/2026-08-14-personal-product-loop-design.md)
- [Personal favorites implementation plan](docs/superpowers/plans/2026-08-14-personal-favorites.md)
- [Private catch-photo implementation plan](docs/superpowers/plans/2026-08-14-catch-photo-media.md)
