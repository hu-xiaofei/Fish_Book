# Final Fix Report: Minimal Administration Loop

Date: 2026-09-11

Base commit: `45d7177200060f17424ef5ad8a24cba9825704cc`

Branch: `codex/minimal-administration-loop`

Implementation commit: `6cee243` (`fix: close administration review findings`)

## Status and scope

DONE. This wave addresses only the three Important findings from the final review:

1. Favorite removal no longer reveals whether an unpublished or draft slug exists.
2. Fish content saves and publish/unpublish actions share one synchronous operation lock.
3. Alias duplicates that are equivalent under MySQL collation return a safe aliases validation response instead of a 500 or SQL detail.

No dependencies were added. The plan, specification, and review ledger were not modified.

## Fix 1: favorite removal hidden-content oracle

### Root cause

`DefaultFavoriteApplicationService.remove` first performed an unrestricted catalog lookup. Consequently, an authenticated caller received 204 for an existing hidden slug and 404 for a missing slug, even when the hidden fish did not belong to that caller's favorites.

### Change

Removal is now a single ownership-scoped, idempotent repository operation keyed by the current user and fish slug. The native MySQL delete joins `favorites` to `fish_species`, so it can remove an existing unpublished favorite without resolving arbitrary catalog content. A hidden fish that is not favorited by the caller and a missing slug both produce the same no-op result. Add and status still use the public-only catalog lookup.

Files:

- `backend/src/main/java/com/fishbook/favorites/application/DefaultFavoriteApplicationService.java`
- `backend/src/main/java/com/fishbook/favorites/domain/FavoriteRepository.java`
- `backend/src/main/java/com/fishbook/favorites/persistence/JpaFavoriteRepositoryAdapter.java`
- `backend/src/main/java/com/fishbook/favorites/persistence/SpringDataFavoriteJpaRepository.java`
- `backend/src/test/java/com/fishbook/favorites/application/DefaultFavoriteApplicationServiceTest.java`
- `backend/src/test/java/com/fishbook/favorites/persistence/JpaFavoriteRepositoryAdapterTest.java`
- `backend/src/test/java/com/fishbook/favorites/web/FavoriteApiIntegrationTest.java`

### RED

- Command: `cd backend && ./mvnw -Dtest=DefaultFavoriteApplicationServiceTest test`
- Result: the new missing-slug removal test failed because the old implementation called the unrestricted catalog lookup and propagated its missing-reference failure.

### GREEN

- Command: `cd backend && ./mvnw -Dtest=DefaultFavoriteApplicationServiceTest test`
- Result: 12 tests passed.
- Command: `cd backend && ./mvnw -Dtest=FavoriteApiIntegrationTest test`
- Result: 8 tests passed against MySQL, including removal of an owned unpublished favorite and identical 204 behavior for an unowned draft and a missing slug.
- Command: `cd backend && ./mvnw -Dtest=JpaFavoriteRepositoryAdapterTest test`
- Result: 5 tests passed against MySQL, including repeated and concurrent idempotent removal. The first GREEN attempt exposed a stale fixture slug in this test; correcting the test to the seeded fish slug made the focused suite pass without a production change.

## Fix 2: concurrent admin content and publication mutations

### Root cause

The edit page guarded only repeated publication actions. The content form and publication button each disabled only for their own mutation, so a save and publish/unpublish request could overlap and whole-aggregate writes could overwrite each other.

### Change

`AdminFishEditPage` now uses one synchronous ref-based operation lock for both mutation entry points. Both directions check the lock before issuing a request and release it in `finally`. The publication button is disabled while either mutation is pending, and `AdminFishForm` accepts `submitDisabled` so save is disabled while a publication action is pending.

Files:

- `frontend/src/features/administration/components/AdminFishForm.tsx`
- `frontend/src/features/administration/pages/AdminFishEditPage.tsx`
- `frontend/src/features/administration/pages/AdminFishEditPage.test.tsx`

### RED

- Command: `cd frontend && npm test -- --run src/features/administration/pages/AdminFishEditPage.test.tsx src/features/administration/components/AdminFishForm.test.tsx`
- Result: both new deferred-promise interaction tests failed because the opposite control remained enabled during an in-flight mutation.

### GREEN

- Command: `cd frontend && npm test -- --run src/features/administration/pages/AdminFishEditPage.test.tsx src/features/administration/components/AdminFishForm.test.tsx`
- Result: 2 files and 21 tests passed. The tests prove that a pending save blocks publication and a pending publication action blocks save, with exactly one call to the active mutation and zero calls to the blocked mutation.

## Fix 3: alias uniqueness under MySQL collation

### Root cause

`FishSpeciesContent` compared normalized aliases using exact Java string equality, while MySQL's `utf8mb4_0900_ai_ci` unique index is case- and accent-insensitive and has additional Unicode equivalences. Collation-equivalent aliases could therefore reach the database and surface as an unhandled constraint violation.

### Change

The domain now validates aliases with a storage-oriented key based on NFKD decomposition, combining-mark removal, and locale-independent lowercasing. This rejects common case/accent-equivalent duplicates before persistence while retaining the original alias text and order.

MySQL remains authoritative for equivalences Java's lightweight normalization does not reproduce. `GlobalExceptionHandler` explicitly recognizes `uk_fish_aliases_species_alias` anywhere in the cause chain and maps it to:

- HTTP 400
- code `VALIDATION_FAILED`
- message `Fish content is invalid`
- field error `aliases: Aliases must be unique`

No SQL message is returned. The update integration test uses `Straße` and `strasse` to exercise the actual database-constraint fallback rather than only domain prevalidation.

Files:

- `backend/src/main/java/com/fishbook/catalog/domain/FishSpeciesContent.java`
- `backend/src/main/java/com/fishbook/catalog/domain/InvalidFishSpeciesException.java`
- `backend/src/main/java/com/fishbook/common/error/GlobalExceptionHandler.java`
- `backend/src/test/java/com/fishbook/catalog/domain/FishSpeciesTest.java`
- `backend/src/test/java/com/fishbook/administration/web/AdminFishApiIntegrationTest.java`

### RED

- Command: `cd backend && ./mvnw -Dtest=FishSpeciesTest test`
- Result: the new `Black Fish` / `black fish` domain test failed because no exception was raised.
- Command: `cd backend && ./mvnw -Dtest=AdminFishApiIntegrationTest test`
- Result: the new MySQL create and update cases returned 500; the server log showed a duplicate entry for `fish_aliases.uk_fish_aliases_species_alias`.

### GREEN

- Command: `cd backend && ./mvnw -Dtest=FishSpeciesTest test`
- Result: 18 tests passed.
- Command: `cd backend && ./mvnw -Dtest=AdminFishApiIntegrationTest#rejectsCollationEquivalentAliasesOnCreateWithASafeFieldError+rejectsCollationEquivalentAliasesOnUpdateWithASafeFieldError test`
- Result: both create and update cases passed with safe 400 field errors.
- Command: `cd backend && ./mvnw -Dtest=AdminFishApiIntegrationTest#rejectsCollationEquivalentAliasesOnUpdateWithASafeFieldError test`
- Result: 1 test passed after selecting the `Straße` / `strasse` pair; the MySQL constraint was exercised and safely mapped.

## Final verification

Focused tests were green before full verification. The first Docker-backed Maven invocation inside the filesystem sandbox could not access the Docker socket; this was an environment permission failure, not a behavioral RED. Docker-backed verification was rerun with the required local Docker permission.

- `cd backend && ./mvnw test` — BUILD SUCCESS; 262 tests passed, 0 failures, 0 errors, 0 skipped.
- `cd frontend && npm test` — 36 files and 257 tests passed.
- `cd frontend && npm run lint` — passed.
- `cd frontend && npm run build` — passed; Vite built 203 modules.
- `docker compose --env-file .env.example -f compose.yaml -f compose.full.yaml up -d --build` — full local stack rebuilt and started successfully; MySQL, MinIO, and backend reported healthy before E2E.
- `cd e2e && npm test` — executed once; 9 Playwright tests passed.
- `git diff --check` — passed before the implementation commit.

## Commit and audit

- `6cee243` — `fix: close administration review findings` (15 production/test files, 235 insertions, 32 deletions).
- This report is committed separately as evidence so it can name the immutable implementation commit.
- The implementation commit was reviewed with `git diff`, `git diff --stat`, `git diff --name-only`, and `git diff --check`; only files listed above changed.

## Concerns

No unresolved functional concern remains within the requested scope. MySQL collation has equivalences broader than a small Java normalization function, so the database constraint intentionally remains the final authority; the safe exception mapping is required and is covered by a real MySQL update test. Existing Mockito dynamic-agent and Node color-environment warnings are non-failing tooling warnings. The local Compose stack was left running and no volumes or user data were removed.
