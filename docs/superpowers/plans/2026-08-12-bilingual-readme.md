# FishBook Bilingual README Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the root README with an accurate Simplified Chinese-first, English-second guide that introduces, starts, accesses, and verifies the current FishBook application.

**Architecture:** Keep one `README.md` with explicit `#zh-cn` and `#en` navigation anchors. Give both languages the same factual section order and reuse identical command blocks so the two halves do not drift. Validate the document with repository-aware shell assertions, Compose parsing, link-target checks, and a scope-only Git diff.

**Tech Stack:** GitHub-flavored Markdown, POSIX shell assertions, Docker Compose configuration validation, Git.

## Global Constraints

- Modify only the root `README.md`; the already-approved design and this plan are documentation records, not application behavior changes.
- Put complete Simplified Chinese content before complete English content.
- Use top navigation links `[简体中文](#zh-cn) | [English](#en)` and explicit stable anchors.
- Describe only current capabilities: public read-only catalog of exactly 12 fish, search/filter/detail pages, registration, login, JDBC-backed session restoration, profile nickname editing, and logout.
- State that administration, catalog writes, image uploads, favorites, and catch records are not implemented yet.
- Use repository-pinned versions and components: Java 21, Spring Boot 4.1, Node.js 24.18.0, React 19, TypeScript 5.9, MySQL 8.4, Docker Compose, Flyway, Spring Security/Session, Vitest, and Playwright.
- Use `test -f .env || cp .env.example .env`; never tell users to overwrite an existing `.env`.
- Keep `.env` local and do not include real credentials, tokens, or secrets.
- Use the non-destructive stop command without `-v`; do not recommend deleting volumes in the quick-start path.
- Do not claim the repository has an application license. Link fish-image licensing to `docs/data-sources/fish-catalog-attribution.md`.
- Do not add badges, external decorative images, dependencies, application code, migrations, Docker changes, or CI changes.

---

### Task 1: Rewrite and Verify the Bilingual Root README

**Files:**
- Modify: `README.md`
- Reference: `docs/superpowers/specs/2026-08-12-bilingual-readme-design.md`
- Reference: `docs/runbooks/local-development.md`
- Reference: `docs/data-sources/fish-catalog-attribution.md`
- Reference: `docs/superpowers/specs/2026-08-07-fishbook-mvp-design.md`
- Reference: `docs/superpowers/specs/2026-08-11-fish-catalog-core-design.md`

**Interfaces:**
- Consumes: current repository routes, Compose files, package versions, CI commands, and existing documentation paths.
- Produces: one self-contained bilingual entry point for Chinese and English readers; no runtime interface changes.

- [ ] **Step 1: Run the README contract against the current English-only file and verify RED**

Run:

```bash
rg -q '^\[简体中文\]\(#zh-cn\) \| \[English\]\(#en\)$' README.md \
  && rg -q '^<a id="zh-cn"></a>$' README.md \
  && rg -q '^## 简体中文$' README.md \
  && rg -q '^<a id="en"></a>$' README.md \
  && rg -q '^## English$' README.md \
  && rg -q '公开只读鱼类图鉴' README.md \
  && rg -q 'Public read-only fish catalog' README.md
```

Expected: non-zero exit because the current README has no Chinese section, language navigation, or current catalog description.

- [ ] **Step 2: Replace `README.md` with the approved bilingual information architecture**

Write the following content in this exact section order:

```text
# FishBook（鱼类图鉴）
[简体中文](#zh-cn) | [English](#en)

<a id="zh-cn"></a>

## 简体中文
### 项目简介
### 当前功能
### 技术栈
### 系统架构
### 快速开始
### 常用访问地址
### 测试与验证
### 项目结构
### 当前范围与后续方向
### 相关文档

---

<a id="en"></a>

## English
### Overview
### Current Features
### Tech Stack
### Architecture
### Quick Start
### Local URLs
### Tests and Verification
### Project Structure
### Current Scope and Next Steps
### Documentation
```

The Chinese and English halves must contain these matching facts:

- FishBook is a learning-oriented full-stack fish encyclopedia for Chinese freshwater fish.
- The catalog contains exactly 12 curated species and supports public browsing, Chinese/common/scientific-name search, family/habitat filters, URL-restorable state, stable-slug detail pages, loading/empty/error/image fallbacks, and visible image attribution.
- Identity supports registration, login, JDBC session restoration, profile nickname updates, logout, CSRF protection, session fixation protection, HttpOnly cookies, and BCrypt-SHA256 password hashing.
- The catalog remains read-only. Admin management, image uploads, favorites, and catch records are future work.
- The system path is `Browser → Nginx + React SPA → Spring Boot API → MySQL`; Flyway owns schema/data migrations. MinIO is provisioned for future object storage, while current catalog images are audited local assets served by the frontend.
- The stack table names Java 21/Spring Boot 4.1, React 19/TypeScript 5.9/Vite 8, MySQL 8.4/Flyway/JPA, Spring Security/Spring Session JDBC, Docker Compose/Nginx, and JUnit/Testcontainers/Vitest/Testing Library/Playwright.
- Quick start uses:

```bash
git clone https://github.com/hu-xiaofei/Fish_Book.git
cd Fish_Book
nvm install 24.18.0
nvm use 24.18.0
test -f .env || cp .env.example .env
docker compose -f compose.yaml -f compose.full.yaml up -d --build
docker compose -f compose.yaml -f compose.full.yaml ps
```

- Readers wait for MySQL, MinIO, and backend health before opening `http://localhost:8080/`.
- The URL table includes `/`, `/fish/channa-argus`, `/register`, `/login`, `/profile`, and `/actuator/health` with equivalent descriptions in both languages.
- Non-destructive stop uses:

```bash
docker compose -f compose.yaml -f compose.full.yaml down
```

- Test commands provide a local equivalent of CI coverage and explain that Docker is required for backend Testcontainers and the full stack must be running for Playwright:

```bash
cd backend && ./mvnw test
cd ../frontend && npm ci && npm run lint && npm test && npm run build
cd ../e2e && npm ci && npx playwright install chromium && npm test
cd .. && docker compose --env-file .env.example -f compose.yaml -f compose.full.yaml config --quiet
```

- Both language sections state that CI runs for pushes to `main` and for pull requests. They also distinguish the Linux CI details from the local sequence: Maven batch mode, Playwright `--with-deps`, and Compose startup/health waiting before end-to-end tests.

- The project tree contains `backend/`, `frontend/`, `e2e/`, `docs/`, `compose.yaml`, and `compose.full.yaml`, each with a short purpose.
- Documentation links point to:
  - `docs/runbooks/local-development.md`
  - `docs/data-sources/fish-catalog-attribution.md`
  - `docs/superpowers/specs/2026-08-07-fishbook-mvp-design.md`
  - `docs/superpowers/specs/2026-08-11-fish-catalog-core-design.md`
- The final scope note says there is no repository-level application license file yet and directs image-license readers to the attribution document without implying the application is open-source licensed.

- [ ] **Step 3: Run the bilingual content contract and verify GREEN**

Run:

```bash
rg -q '^\[简体中文\]\(#zh-cn\) \| \[English\]\(#en\)$' README.md \
  && rg -Uq '<a id="zh-cn"></a>\n\n## 简体中文' README.md \
  && rg -q '^## 简体中文$' README.md \
  && rg -Uq '<a id="en"></a>\n\n## English' README.md \
  && rg -q '^## English$' README.md \
  && rg -q '公开只读鱼类图鉴' README.md \
  && rg -q 'Public read-only fish catalog' README.md \
  && rg -q '管理员后台.*尚未实现' README.md \
  && rg -q 'admin.*not.*implemented' README.md \
  && test "$(rg -c 'test -f \.env \|\| cp \.env\.example \.env' README.md)" -eq 2 \
  && ! rg -n 'cp \.env\.example \.env' README.md | rg -v 'test -f \.env \|\|' \
  && ! rg -n 'docker compose .*down -v|MIT License|Apache License' README.md
```

Expected: exit `0` with both language sections, safe environment setup, explicit scope boundaries, and no false license/destructive reset statement.

- [ ] **Step 4: Validate every repository-relative Markdown link target**

Run:

```bash
for target in \
  docs/runbooks/local-development.md \
  docs/data-sources/fish-catalog-attribution.md \
  docs/superpowers/specs/2026-08-07-fishbook-mvp-design.md \
  docs/superpowers/specs/2026-08-11-fish-catalog-core-design.md; do
  test -f "$target" || exit 1
done
```

Expected: exit `0`; every README documentation target exists.

- [ ] **Step 5: Validate operational commands and Markdown hygiene**

Run:

```bash
docker compose --env-file .env.example -f compose.yaml -f compose.full.yaml config --quiet
test "$(rg -c '^```' README.md)" -gt 0
test "$(( $(rg -c '^```' README.md) % 2 ))" -eq 0
! rg -n 'TBD|TODO|FIXME|localhost:3000|localhost:5173' README.md
git diff --check
```

Expected: Compose parses, code fences are balanced, no placeholders or wrong development URLs appear, and Git reports no whitespace errors.

- [ ] **Step 6: Review the final diff for scope and bilingual parity**

Run:

```bash
git status --short
git diff --stat
git diff -- README.md
```

Expected: implementation diff modifies only `README.md`; Chinese and English sections describe the same features, limits, versions, commands, URLs, and documentation.

- [ ] **Step 7: Commit the README implementation**

Run:

```bash
git add README.md
git diff --cached --check
git commit -m "docs: add bilingual project readme"
```

Expected: one implementation commit containing only `README.md`.

- [ ] **Step 8: Verify the committed branch before integration**

Run:

```bash
git status --short --branch
git show --stat --oneline HEAD
git diff main...HEAD --check
```

Expected: clean `codex/bilingual-readme` worktree with the design, plan, and README commits; no diff errors.
