# README Release-Readiness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refresh the bilingual README so GitHub visitors can immediately see the completed favorites milestone, the next personal-product milestone, and the relevant design documentation, then publish `main` to GitHub.

**Architecture:** Keep the existing bilingual README structure and change only release-orientation copy. The Chinese and English sections will remain semantically equivalent, and the push will occur only after local-link, diff, and repository-boundary checks pass.

**Tech Stack:** Markdown, Git, GitHub

## Global Constraints

- Do not add features, screenshots, badges, deployment claims, or test counts.
- Do not claim catch records, private photo uploads, or administration workflows are implemented.
- Preserve the existing Chinese-first, English-second README structure.
- Keep Chinese and English status, architecture, roadmap, and documentation claims semantically equivalent.
- Push only the local `main` branch to `origin/main`; do not force-push.

---

### Task 1: Refresh the Bilingual README

**Files:**
- Modify: `README.md`

**Interfaces:**
- Consumes: the implemented identity, public catalog, and private favorites milestones documented by the repository.
- Produces: a release-ready bilingual README with current status, aligned architecture, ordered next steps, and links to the personal-loop design and favorites plan.

- [ ] **Step 1: Record the pre-change documentation gaps**

Run:

```bash
rg -n "项目状态|Project Status|personal-product-loop-design|personal-favorites" README.md
```

Expected: no matches for the new status headings or personal-loop documentation links.

- [ ] **Step 2: Add the Chinese project-status section**

After the Chinese overview paragraphs, add:

```markdown
### 项目状态

第一阶段个人产品闭环已经完成：用户可以注册并维护个人资料、浏览公开鱼类图鉴，并管理按账号隔离的私有收藏。下一阶段将围绕钓获记录展开，在不改变公开图鉴只读边界的前提下继续完善个人使用闭环。
```

- [ ] **Step 3: Align the Chinese architecture, roadmap, and documentation links**

Change the Spring Boot architecture bullet so it names `用户、图鉴与收藏功能`.

Replace the next-step list with this order and wording:

```markdown
- 钓获记录的创建、查看、编辑和删除；
- 由 MinIO 支撑的可选私有钓获照片；
- 管理员账号初始化和基于角色的权限控制；
- 鱼类新增、编辑、发布和下架。
```

Add these links to `相关文档`:

```markdown
- [个人产品闭环设计规格](docs/superpowers/specs/2026-08-14-personal-product-loop-design.md)
- [个人收藏实施计划](docs/superpowers/plans/2026-08-14-personal-favorites.md)
```

- [ ] **Step 4: Add the equivalent English project-status section**

After the English overview paragraphs, add:

```markdown
### Project Status

The first personal-product milestone is complete: users can register and maintain a profile, browse the public fish catalog, and manage account-isolated private favorites. The next milestone focuses on catch records while preserving the public catalog's read-only boundary.
```

- [ ] **Step 5: Align the English architecture, roadmap, and documentation links**

Change the Spring Boot architecture bullet so it names the `identity, catalog, and favorites features`.

Replace the next-step list with:

```markdown
- create, view, edit, and delete catch records;
- optional private catch photos backed by MinIO;
- administrator bootstrap and role-based authorization;
- create, edit, publish, and unpublish catalog workflows.
```

Add these links to `Documentation`:

```markdown
- [Personal product-loop design specification](docs/superpowers/specs/2026-08-14-personal-product-loop-design.md)
- [Personal favorites implementation plan](docs/superpowers/plans/2026-08-14-personal-favorites.md)
```

- [ ] **Step 6: Verify links, bilingual parity, and Markdown diff**

Run each command separately:

```bash
test -f docs/superpowers/specs/2026-08-14-personal-product-loop-design.md
test -f docs/superpowers/plans/2026-08-14-personal-favorites.md
git diff --check
git diff -- README.md
```

Expected: both file checks exit 0, `git diff --check` exits 0, and the README diff contains equivalent Chinese and English changes without unrelated rewrites.

- [ ] **Step 7: Commit the README update**

```bash
git add README.md
git commit -m "docs: refresh README project status"
```

### Task 2: Publish `main` to GitHub

**Files:**
- No file changes.

**Interfaces:**
- Consumes: the locally verified `main` history containing the completed favorites milestone and README release update.
- Produces: `origin/main` at the same commit as local `main`.

- [ ] **Step 1: Verify the publication boundary**

Run each command separately:

```bash
git status --short --branch
git log --oneline origin/main..main
```

Expected: the worktree is clean; the outgoing list contains only the intended local design, implementation, verification, and README commits.

- [ ] **Step 2: Push without rewriting remote history**

```bash
git push origin main
```

Expected: the push succeeds without `--force`.

- [ ] **Step 3: Confirm local and remote main match**

Run each command separately:

```bash
git fetch origin main
git rev-parse main
git rev-parse origin/main
git status --short --branch
```

Expected: the two commit IDs are identical and the worktree is clean with no ahead/behind count.
