# FishBook README Release-Readiness Design

**Status:** Approved on 2026-08-17

## Context

FishBook now delivers three user-visible foundations: identity and profile management, a public read-only fish catalog, and private favorites for authenticated users. The bilingual README already documents these capabilities, so this update will strengthen release orientation without rewriting accurate material.

## Goals

- Make the current milestone and the next product milestone immediately visible in both Chinese and English.
- Keep the architecture description aligned with the implemented identity, catalog, and favorites boundaries.
- Reorder the roadmap around the approved personal product loop.
- Link readers to the approved personal-loop design and favorites implementation plan.

## Non-Goals

- Do not add features, screenshots, badges, or deployment claims.
- Do not publish test counts that will become stale as the suite grows.
- Do not claim catch records, private photo uploads, or administration workflows are implemented.
- Do not restructure or rewrite sections that are already accurate.

## README Changes

1. Add compact `项目状态` and `Project Status` sections after the respective overviews. They will state that identity, catalog, and private favorites are complete, and that catch records are the next milestone.
2. Update the architecture bullets so the Spring Boot application explicitly includes identity, catalog, and favorites boundaries.
3. Reorder the next-step lists to reflect the planned sequence: catch records, optional private photos backed by MinIO, then administration and catalog-authoring workflows.
4. Add links to:
   - `docs/superpowers/specs/2026-08-14-personal-product-loop-design.md`
   - `docs/superpowers/plans/2026-08-14-personal-favorites.md`
5. Keep the Chinese and English sections semantically equivalent.

## Verification

- Confirm every new local link resolves to an existing file.
- Compare the Chinese and English sections for equivalent status, roadmap, architecture, and documentation claims.
- Run `git diff --check` and inspect the complete README diff before committing.
- Because the implementation is documentation-only, no application behavior or dependency changes are expected.
