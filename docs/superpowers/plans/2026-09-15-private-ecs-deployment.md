# FishBook Private ECS Deployment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deploy FishBook as a private HTTPS learning environment on the existing ECS and RDS, with photos stored in a protected ECS directory.

**Architecture:** Add a `filesystem` implementation behind the existing `MediaStore` interface, then introduce a standalone production Compose stack containing only the backend and TLS Nginx frontend. Bind the HTTPS entry point to ECS loopback, reach it through an SSH tunnel, and keep RDS and photo storage off the public network.

**Tech Stack:** Java 21, Spring Boot 4.1, JUnit 5, AssertJ, React, Nginx unprivileged, Docker Compose 2.26, MySQL 8.4 RDS, Alibaba Cloud Linux 4.

**Spec:** `docs/superpowers/specs/2026-09-15-private-ecs-deployment-design.md`

## Global Constraints

- Do not buy or release resources, open RDS public access, modify security groups, delete server/database data, or expose a public Web port.
- Preserve the existing `minio`, `oss`, and disabled media modes; production must explicitly select `filesystem`.
- Keep photos reachable only through authenticated application APIs; never add an Nginx static route to the photo directory.
- Never put database/admin passwords, SSH keys, TLS private keys, Session values, CSRF tokens, object keys, or absolute photo paths in Git, images, logs, command output, or chat.
- Use the approved learning-only RDS exception: private VPC connection without TLS, ordinary `fishbook_app` account, non-sensitive data only.
- Keep Secure cookies and HTTPS enabled. Before domain/ICP filing, bind Web HTTPS only to `127.0.0.1:8443` and access it through SSH forwarding.
- Do not touch the user's untracked `Fish_Book开发流程/` directory or the long-lived local `fishbook` Compose data.
- Every cloud mutation must be preceded by a read-only target/state check. Operate only on `/opt/fishbook` and the explicitly verified RDS database.

---

### Task 1: Filesystem Provider Configuration

**Files:**
- Modify: `backend/src/main/java/com/fishbook/media/config/MediaProvider.java`
- Modify: `backend/src/main/java/com/fishbook/media/config/MediaProperties.java`
- Create: `backend/src/main/java/com/fishbook/media/config/FilesystemProperties.java`
- Create: `backend/src/main/java/com/fishbook/media/config/FilesystemConfiguration.java`
- Modify: `backend/src/test/java/com/fishbook/media/config/MediaConfigurationTest.java`
- Create: `backend/src/test/java/com/fishbook/media/config/FilesystemPropertiesTest.java`

**Interfaces:**
- Consumes: existing `MediaStore` interface and `MediaProperties.provider()`.
- Produces: `MediaProvider.FILESYSTEM`; validated `FilesystemProperties(Path root)`; exactly one `MediaStore` bean when `fishbook.media.enabled=true` and `provider=filesystem`.

- [ ] **Step 1: Write failing provider-selection tests**

Add `FilesystemConfiguration.class` to `MediaConfigurationTest.contextRunner`. Add a test equivalent to:

```java
@Test
void enabledFilesystemCreatesOnlyFilesystemStore() {
    contextRunner.withPropertyValues(
            "fishbook.media.enabled=true",
            "fishbook.media.provider=filesystem",
            "fishbook.media.filesystem.root=/data/photos")
        .run(context -> {
            assertThat(context).hasSingleBean(MediaStore.class);
            assertThat(context.getBean(MediaStore.class)).isInstanceOf(FilesystemMediaStore.class);
            assertThat(context).doesNotHaveBean(MinioClient.class);
            assertThat(context).doesNotHaveBean(OSS.class);
        });
}
```

Also add `FilesystemPropertiesTest` using `@TempDir` and Jakarta Validation. It must accept an absolute writable directory and reject a relative path, a regular file, a missing/uncreatable root, and a null root. Use literal expected validity results; do not duplicate production validation helpers in the test.

- [ ] **Step 2: Run the focused tests and verify RED**

Run:

```bash
cd backend
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home \
  ./mvnw -B -Dtest=MediaConfigurationTest,FilesystemPropertiesTest test
```

Expected: compilation fails because `FILESYSTEM`, `FilesystemProperties`, `FilesystemConfiguration`, and `FilesystemMediaStore` do not exist.

- [ ] **Step 3: Add the provider and validated properties**

Add `FILESYSTEM` to `MediaProvider`. Change `MediaProperties.isValidWhenEnabled()` so `bucket` is required only for `MINIO` and `OSS`; `FILESYSTEM` is validated by its dedicated properties.

Implement this public shape:

```java
@Validated
@ConfigurationProperties("fishbook.media.filesystem")
public record FilesystemProperties(@NotNull Path root) {
    @AssertTrue(message = "filesystem media root must be an absolute writable directory")
    public boolean isUsableRoot() {
        return root != null && root.isAbsolute() && Files.isDirectory(root)
                && Files.isReadable(root) && Files.isWritable(root);
    }

    @Override public String toString() {
        return "FilesystemProperties[configuration=redacted]";
    }
}
```

Implement `FilesystemConfiguration` with both conditional annotations, `@EnableConfigurationProperties(FilesystemProperties.class)`, and one package-private bean method returning `new FilesystemMediaStore(properties.root())`.

- [ ] **Step 4: Run focused configuration tests and verify GREEN**

Run the command from Step 2. Expected: all selected tests pass with zero failures/errors.

- [ ] **Step 5: Commit Task 1**

```bash
git add backend/src/main/java/com/fishbook/media/config \
  backend/src/test/java/com/fishbook/media/config
git commit -m "feat: configure filesystem photo storage"
```

---

### Task 2: Atomic Private Filesystem Store

**Files:**
- Create: `backend/src/main/java/com/fishbook/media/persistence/FilesystemMediaStore.java`
- Create: `backend/src/test/java/com/fishbook/media/persistence/FilesystemMediaStoreTest.java`

**Interfaces:**
- Consumes: `MediaStore.put(String, byte[], String)`, `get(String)`, and `delete(String)`.
- Produces: the same interface backed by `<root>/<objectKey>/content` and `<root>/<objectKey>/content-type` with atomic directory publication.

- [ ] **Step 1: Write failing storage behavior tests**

Create a real-filesystem test with `@TempDir Path root`. Include independent tests for:

```java
@Test
void storesReadsAndDeletesOneObjectIdempotently() {
    var store = new FilesystemMediaStore(root);
    var bytes = new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x01};
    store.put("catches/42/7/550e8400-e29b-41d4-a716-446655440000", bytes, "image/jpeg");
    assertThat(store.get("catches/42/7/550e8400-e29b-41d4-a716-446655440000").content())
            .containsExactly(bytes);
    assertThat(store.get("catches/42/7/550e8400-e29b-41d4-a716-446655440000").contentType())
            .isEqualTo("image/jpeg");
    store.delete("catches/42/7/550e8400-e29b-41d4-a716-446655440000");
    store.delete("catches/42/7/550e8400-e29b-41d4-a716-446655440000");
}
```

Add parameterized rejection tests for `""`, `"/tmp/x"`, `"../x"`, `"catches/../x"`, `"./x"`, backslash path components, and keys whose existing parent is a symbolic link outside `root`. Assert `MediaStorageUnavailableException` and assert its message contains none of `root.toString()`, the key, or a sentinel filename.

Add tests that reject a second `put` to the same final key, invalid MIME (`text/plain`), missing/malformed MIME metadata, a content file larger than `10 * 1024 * 1024`, a symbolic-link content file, and a symbolic-link object directory. Confirm failed writes leave neither the final directory nor a `.upload-` temporary directory.

- [ ] **Step 2: Run the store test and verify RED**

```bash
cd backend
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home \
  ./mvnw -B -Dtest=FilesystemMediaStoreTest test
```

Expected: compilation fails because `FilesystemMediaStore` is missing.

- [ ] **Step 3: Implement safe resolution and atomic publication**

Implement these constants and method boundaries:

```java
public final class FilesystemMediaStore implements MediaStore {
    static final long MAX_CONTENT_BYTES = 10L * 1024 * 1024;
    private static final Set<String> CONTENT_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp");
    private static final String CONTENT = "content";
    private static final String CONTENT_TYPE = "content-type";
    private final Path root;

    public FilesystemMediaStore(Path root) { /* validate and normalize */ }
    @Override public void put(String objectKey, byte[] content, String contentType) { /* below */ }
    @Override public StoredMedia get(String objectKey) { /* below */ }
    @Override public void delete(String objectKey) { /* below */ }
    private Path resolveObject(String objectKey) { /* below */ }
    private static MediaStorageUnavailableException unavailable(Throwable cause) {
        return new MediaStorageUnavailableException(cause);
    }
}
```

`resolveObject` must split only `/`-separated components, reject blank/`.`/`..`/backslash components, resolve against normalized absolute `root`, verify `candidate.startsWith(root)`, and walk each existing parent with `NOFOLLOW_LINKS`, rejecting symbolic links and non-directories.

`put` must require a non-null body of at most `MAX_CONTENT_BYTES` and an allowed literal MIME. Create missing parent directories one component at a time and recheck them without following links. Create a unique `.upload-<UUID>` sibling directory, write `content` with `CREATE_NEW`, write UTF-8 `content-type` with `CREATE_NEW`, then publish using `Files.move(temp, target, ATOMIC_MOVE)`. Do not use `REPLACE_EXISTING`. On failure, delete only the two fixed files and the exact temporary directory; never recursively walk an object prefix.

`get` must reject symlinks using `NOFOLLOW_LINKS`, require both fixed children to be regular files, use `Files.size` before reading, then read at most `MAX_CONTENT_BYTES + 1` and reject oversize content. Read MIME as bounded UTF-8 text and accept only `CONTENT_TYPES`.

`delete` must resolve one object, reject symlinks, delete only `content`, `content-type`, and then the empty object directory. Treat all three already absent as success; a directory containing any unexpected entry must fail closed instead of recursively deleting it. Wrap I/O errors without logging or exposing paths.

- [ ] **Step 4: Run the focused tests and mutation checks**

Run the command from Step 2. Expected: all pass. Then temporarily change the path containment check, remove `NOFOLLOW_LINKS`, and change `ATOMIC_MOVE` to direct final writes one mutation at a time; each corresponding test must fail. Restore the implementation after each mutation and rerun to green.

- [ ] **Step 5: Run media regression tests**

```bash
cd backend
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home \
  ./mvnw -B -Dtest=FilesystemMediaStoreTest,MediaConfigurationTest,\
DefaultCatchPhotoApplicationServiceTest,CatchPhotoApiIntegrationTest,\
AdminPhotoApiIntegrationTest,MediaCleanupServiceTest test
```

Expected: all selected tests pass; no assertion may expose an absolute storage path.

- [ ] **Step 6: Commit Task 2**

```bash
git add backend/src/main/java/com/fishbook/media/persistence/FilesystemMediaStore.java \
  backend/src/test/java/com/fishbook/media/persistence/FilesystemMediaStoreTest.java
git commit -m "feat: store private photos on filesystem"
```

---

### Task 3: Standalone Production Compose and HTTPS Entry

**Files:**
- Modify: `frontend/Dockerfile`
- Create: `frontend/nginx.private-ecs.conf`
- Create: `compose.private-ecs.yaml`
- Create: `deploy/private-ecs/fishbook.env.example`
- Create: `deploy/private-ecs/generate-certificate.sh`
- Create: `deploy/private-ecs/verify-compose.sh`
- Create: `deploy/private-ecs/verify-image-secrets.sh`
- Create: `docs/runbooks/private-ecs-deployment.md`

**Interfaces:**
- Consumes: `/opt/fishbook/config/fishbook.env`, `/opt/fishbook/tls`, `/opt/fishbook/data/photos`, RDS internal DNS.
- Produces: `frontend` on `127.0.0.1:8443`, internal-only `backend:8080`, repeatable configuration verification, operator runbook.

- [ ] **Step 1: Establish failing production-config checks**

Create `verify-compose.sh` first. It must run `docker compose --env-file "$ENV_FILE" -f compose.private-ecs.yaml config --format json`, inspect the rendered JSON with the available `jq`, and fail unless all are true:

```text
exactly two services named backend and frontend
frontend publishes target 8443 only, host_ip 127.0.0.1
backend publishes no ports
backend has no privileged, network_mode=host, or Docker socket mount
backend mounts /opt/fishbook/data/photos at /data/photos
frontend mounts /opt/fishbook/tls read-only
backend selects filesystem and /data/photos
both services have restart=unless-stopped, healthcheck, and bounded json-file logs
```

Run it against a temporary environment file containing synthetic values. Expected: FAIL because `compose.private-ecs.yaml` does not exist.

- [ ] **Step 2: Add production Nginx and image selection**

Change `frontend/Dockerfile` to:

```dockerfile
ARG NGINX_CONFIG=nginx.conf
COPY ${NGINX_CONFIG} /etc/nginx/conf.d/default.conf
```

Place the `ARG` immediately before the runtime-stage copy; the default preserves local builds. In `nginx.private-ecs.conf`, listen on `8443 ssl`, load `/etc/fishbook/tls/server.crt` and `server.key`, serve the SPA, proxy `/api/` to `http://backend:8080`, and proxy only exact `/actuator/health/readiness`. Preserve `Host`, `X-Request-ID`, `X-Forwarded-Proto`, `X-Forwarded-For`, timeouts, `nosniff`, and a restrictive server-token setting. Do not add any `/data`, `/photos`, or `/catches` file location.

- [ ] **Step 3: Add the two-service Compose file**

Define builds from `./backend` and `./frontend`, with frontend build arg `NGINX_CONFIG=nginx.private-ecs.conf`. Use these backend settings:

```yaml
SPRING_DATASOURCE_URL: "jdbc:mysql://rm-bp1pgdmw41u3i6r98.mysql.rds.aliyuncs.com:3306/fishbook?connectionTimeZone=UTC&useSSL=false&allowPublicKeyRetrieval=false"
SPRING_DATASOURCE_USERNAME: fishbook_app
SPRING_DATASOURCE_PASSWORD: ${MYSQL_PASSWORD:?MYSQL_PASSWORD is required}
FISHBOOK_MEDIA_ENABLED: "true"
FISHBOOK_MEDIA_PROVIDER: filesystem
FISHBOOK_MEDIA_FILESYSTEM_ROOT: /data/photos
FISHBOOK_ADMIN_BOOTSTRAP_ENABLED: ${FISHBOOK_ADMIN_BOOTSTRAP_ENABLED:?required}
FISHBOOK_ADMIN_EMAIL: ${FISHBOOK_ADMIN_EMAIL:?required}
FISHBOOK_ADMIN_PASSWORD: ${FISHBOOK_ADMIN_PASSWORD:?required}
FISHBOOK_ADMIN_NICKNAME: ${FISHBOOK_ADMIN_NICKNAME:?required}
```

Mount `/opt/fishbook/data/photos:/data/photos`, set backend `user: "10001:10001"`, and keep its healthcheck internal. Mount `/opt/fishbook/tls:/etc/fishbook/tls:ro` into frontend, set `user: "101:101"`, and publish only `127.0.0.1:8443:8443`. Add `restart: unless-stopped` and `json-file` rotation (`10m`, three files) to both.

- [ ] **Step 4: Add certificate and environment helpers**

`generate-certificate.sh` must operate only on an explicit existing writable `FISHBOOK_TLS_DIR`, refuse to overwrite either existing TLS file, use OpenSSL with RSA 3072/SHA-256 and SAN `DNS:localhost,IP:127.0.0.1`, validity 397 days, then set certificate `0644` and key `0600`. It must print only the SHA-256 certificate fingerprint and expiry, never the private key. During cloud preparation, root runs it as UID/GID 101, then changes the TLS directory itself back to `root:root` and `0755`; the non-root frontend can read its `101:101` key but cannot replace directory entries.

`fishbook.env.example` contains variable names and non-secret booleans only; secret values remain empty and it is never passed directly to Compose. The runbook must provide a hidden-input command using `read -rsp` to create `/opt/fishbook/config/fishbook.env` as `0600`, plus checks that output only variable names and whether they are non-empty. The example must not contain `${...}` expansions that could accidentally read ambient secrets.

- [ ] **Step 5: Verify rendered behavior and helper refusal paths**

Run `verify-compose.sh` with a temporary synthetic env file. Expected: PASS. Run the certificate helper as the current user against a newly created temporary `FISHBOOK_TLS_DIR`, verify OpenSSL reports the required SAN/fingerprint and private-key mode `0600`, then run it again and expect refusal without overwriting either file.

- [ ] **Step 6: Commit Task 3**

```bash
git add frontend/Dockerfile frontend/nginx.private-ecs.conf compose.private-ecs.yaml \
  deploy/private-ecs docs/runbooks/private-ecs-deployment.md
git commit -m "feat: add private ECS deployment stack"
```

---

### Task 4: Full Local and GitHub Verification

**Files:**
- Modify only files needed to repair failures caused by Tasks 1–3; do not broaden scope.

**Interfaces:**
- Consumes: completed filesystem provider and private ECS stack.
- Produces: reviewed commits on `main` with passing local and GitHub verification.

- [ ] **Step 1: Run complete backend verification**

```bash
cd backend
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home ./mvnw -B test
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home ./mvnw -B -DskipTests package
```

Expected: 0 failures/errors/skips, `BUILD SUCCESS` for both; record the exact test count.

- [ ] **Step 2: Run complete frontend verification**

```bash
cd frontend
PATH=/Users/hdc/.nvm/versions/node/v24.18.0/bin:$PATH npm run lint
PATH=/Users/hdc/.nvm/versions/node/v24.18.0/bin:$PATH npm test
PATH=/Users/hdc/.nvm/versions/node/v24.18.0/bin:$PATH npm run build
```

Expected: all commands exit 0; record test-file and test counts plus any non-blocking size advisory.

- [ ] **Step 3: Run isolated browser acceptance**

```bash
cd e2e
PATH=/Users/hdc/.nvm/versions/node/v24.18.0/bin:$PATH npm run test:preflight
PATH=/Users/hdc/.nvm/versions/node/v24.18.0/bin:$PATH npm run test:isolated
```

Expected: six preflight tests and ten browser tests pass; the dedicated containers, network, and volumes are removed. Confirm the long-lived local `fishbook` project remained running and untouched.

- [ ] **Step 4: Request independent code review and resolve findings**

Use `superpowers:requesting-code-review`. Review against the approved spec, with emphasis on traversal/symlink resistance, exact deletion scope, atomic publication, secrets, loopback binding, and rollback. Fix Important findings with a failing regression first; rerun affected and full checks once after the final change.

- [ ] **Step 5: Push and wait for GitHub CI**

Verify origin is `https://github.com/hu-xiaofei/Fish_Book.git`, signed-in GitHub owner is `hu-xiaofei`, remote `main` is an ancestor, and no secret/private-key filenames are staged. Push normally with IPv6 (`git push -6 origin main`), never force. Wait for backend, frontend, and docker-and-e2e jobs to complete successfully before cloud deployment.

---

### Task 5: Prepare ECS and Start the Verified Release

**Files:**
- Remote create: `/opt/fishbook/app`, `/opt/fishbook/config/fishbook.env`, `/opt/fishbook/tls`, `/opt/fishbook/data/photos`, `/opt/fishbook/releases`
- Remote modify: `/etc/fstab` only for the exact new `/opt/fishbook/swapfile` entry after duplicate checks.

**Interfaces:**
- Consumes: successful GitHub commit, user-entered RDS/admin secrets, fixed SSH host key.
- Produces: healthy private ECS containers and applied RDS migrations.

- [ ] **Step 1: Repeat non-mutating target checks**

Strictly verify the saved ED25519 fingerprint, instance hostname, OS, disk/memory, Docker/Compose versions, listeners, containers, absence or identity of `/opt/fishbook`, RDS TCP reachability, and remote `main` SHA. Stop on any drift that changes the target or would overwrite existing resources.

- [ ] **Step 2: Install security updates without rebooting**

Run Alibaba Cloud Linux security-only updates. Record package names/count without dumping environment values. Check whether reboot is required; if yes, report it and obtain explicit reboot approval before interrupting the server.

- [ ] **Step 3: Create scoped directories and swap**

Follow the runbook's fail-fast first-install directory block: refuse an existing or ambiguous deployment root, create only `/opt/fishbook` and its `releases`, `config`, `data`, and `data/photos` directories, and leave `/opt/fishbook/app` absent for clone. Set photos to `10001:10001`, mode `0700`. Do not create TLS files or invoke an uninstalled helper yet. If no swap exists and `/opt/fishbook/swapfile` is absent, allocate 2 GiB, mode `0600`, run `mkswap`/`swapon`, and append exactly one validated fstab entry; stop on any ambiguous existing resource. Do not overwrite or recursively change ownership of existing paths.

- [ ] **Step 4: Clone and pin the verified release**

Use the runbook's executable clone/pin block only after base-directory/swap preparation. Refuse any existing `/opt/fishbook/app` path, clone the public repository into that absent path, fetch `main`, verify the exact CI-passing SHA belongs to it, detach that same checkout at the exact SHA, and verify HEAD. Assign `git status` output separately so command failure propagates, then refuse dirty state. Record the prepared SHA without overwriting an existing record; this is not a successful-deployment marker. Do not place credentials in Git configuration or create another deployment checkout/project.

Only now invoke the certificate helper from that verified committed checkout. Follow the runbook's separate fail-fast TLS block: refuse any existing TLS path, create the directory under root ownership, install an EXIT/signal cleanup trap before granting UID/GID 101 write access, and run the helper as `101:101`. The trap must restore the directory to `root:root` and `0755` on helper failure as well as success, without changing the key's `101:101`/`0600`; a restore failure stops deployment. SIGKILL/power loss requires manual directory inspection and restoration before retry. Record only certificate fingerprint/expiry.

- [ ] **Step 5: Pause for secure user input**

Ask the user to run the runbook's hidden-input block in Alibaba Workbench to write the RDS password and a new production administrator email/password into `fishbook.env`. Do not request those values in chat. Verify only file owner/mode, required variable names, non-empty status, and absence from Git/Docker build context.

- [ ] **Step 6: Verify RDS identity before migration**

Use the runbook's fail-fast read-only MySQL 8.4 client preflight in a trusted terminal, with hidden `--password` input (never a password argument or a raw env/config dump). Review `SHOW GRANTS FOR CURRENT_USER()` first to confirm metadata visibility over the entire `fishbook` schema, including routines/triggers/events; insufficient or uncertain visibility is a blocker, not proof of an empty schema. Do not broaden grants during this preflight. Query identity and object counts before deciding whether the Flyway query is valid:

```sql
SELECT DATABASE(), CURRENT_USER(),
  (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()),
  (SELECT COUNT(*) FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA = DATABASE()),
  (SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA = DATABASE()),
  (SELECT COUNT(*) FROM information_schema.EVENTS WHERE EVENT_SCHEMA = DATABASE()),
  (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'flyway_schema_history' AND TABLE_TYPE = 'BASE TABLE');
-- Run only when the previous query proves that exactly one Flyway base table exists:
SELECT installed_rank, version, success
FROM flyway_schema_history ORDER BY installed_rank;
```

The first result must identify database `fishbook` and ordinary `fishbook_app`, with well-formed numeric counts. TABLES counts include both tables and views. An absent Flyway table is acceptable only when the sum of tables/views, routines, triggers, and events is zero and visibility is confirmed. Nonzero objects without Flyway, failed connection/permission/query, or malformed output must stop deployment. Never catch a failed query and substitute zero counts. When Flyway exists, capture only the three migration metadata columns and automatically require a non-empty contiguous V1..VN prefix, where 1 <= N <= 10, matching the reviewed `V1__*.sql` through `V10__*.sql` files. Both rank and version must be canonical positive integers equal to the row number and every success must be exactly 1. Reject missing intermediate entries, duplicates, out-of-order rows, blank/non-numeric/unknown versions, failed entries, malformed columns and empty output with nonzero status. A valid older prefix permits startup to apply the remaining reviewed migrations; post-start acceptance still requires V1..V10. Print only the validation rule result, never raw migration rows, application rows or the password.

- [ ] **Step 7: Build and start**

Use the runbook's fixed-project `dc` function and fail-fast `set -euo pipefail` block with xtrace disabled. Run the committed Compose verifier, then `dc build backend frontend`, then the committed `verify-image-secrets.sh /opt/fishbook/config/fishbook.env` in the same subshell; any failure must prevent both startup commands. The image helper reads the literal database/bootstrap passwords from the protected single-quoted env format without sourcing it or placing secrets in arguments. It captures full inspect JSON and untruncated history for both fixed-project build tags in 0600 temporary files, decodes JSON strings/keys to check both secret values, prints only PASS/FAIL rule names, and traps cleanup. Failed metadata commands, malformed/empty metadata or a matched password must fail closed. This checks literal secrets in metadata, not image file layers or encoded/transformed secrets. Only after verification, both builds, the image gate and database preflight succeed, start backend with `--no-build --no-deps --wait`, then force-recreate frontend with `--no-build --no-deps --wait` to refresh the backend address. Validate exact HTTPS `/actuator/health/readiness` using the public certificate and require status UP. Keep the single `fishbook-private-ecs` project and one backend/JVM. Confirm only `127.0.0.1:8443` is newly listening and no backend/database/storage port is published.

- [ ] **Step 8: Verify migrations and logs**

Check readiness through local HTTPS, then query only Flyway metadata and confirm V1–V10 exactly once with `success=1`. Review bounded startup logs for errors and secret/path leakage. If startup or migration fails, stop new containers, preserve RDS and photos, capture sanitized diagnostics, and do not clear or retry destructive operations.

---

### Task 6: Private Cloud Acceptance and Handoff

**Files:**
- Modify: `docs/runbooks/private-ecs-deployment.md` only to record sanitized actual versions, fingerprints, commands, results, and known gaps.
- Remote modify: `/opt/fishbook/config/fishbook.env` only to switch bootstrap from `true` to `false` after successful administrator creation.

**Interfaces:**
- Consumes: healthy private deployment and user-controlled SSH tunnel/browser.
- Produces: verified learning environment, disabled bootstrap, operator handoff, or an explicit incomplete status.

- [ ] **Step 1: Open the private tunnel**

Give the user this command with the already verified key and host:

```bash
ssh -N -L 8443:127.0.0.1:8443 \
  -i ~/.ssh/fishbook-prod-hz-20260911.pem \
  -o IdentitiesOnly=yes -o StrictHostKeyChecking=yes \
  root@112.124.7.197
```

Have the user open `https://localhost:8443`, inspect the certificate SHA-256 fingerprint against the recorded value, and accept only that self-signed certificate for this learning environment.

- [ ] **Step 2: Verify identity and Session flow**

Create one non-sensitive learning user, log in/out, and confirm the Session cookie is `Secure`, `HttpOnly`, and `SameSite=Lax`. Confirm direct public requests to `112.124.7.197:8443` fail while tunneled HTTPS succeeds.

- [ ] **Step 3: Verify photo owner and administrator behavior**

With a tiny generated non-sensitive JPEG/PNG fixture, create a test catch and upload a photo as the learning user. Confirm a second user cannot read it. Confirm the existing administrator can see, replace, and delete it through `/admin/photos`, while detailed catch fields and favorites remain unavailable. Confirm stale revisions produce 409 and are not retried automatically.

- [ ] **Step 4: Verify exact storage cleanup**

Inspect only metadata for the newly created test record and exact object directory. Confirm replacement/removal queues and removes only the old object, repeated deletion is harmless, no `.upload-` directory remains, and logs/responses do not reveal paths or keys. Do not scan, print, or delete unrelated object directories.

- [ ] **Step 5: Disable bootstrap and reverify**

Change only `FISHBOOK_ADMIN_BOOTSTRAP_ENABLED=true` to `false` in the protected env file, recreate backend, and verify readiness plus administrator login. Confirm the admin record was not duplicated and password values were not printed.

- [ ] **Step 6: Record outcome and operating commands**

Update the runbook with the verified commit, certificate fingerprint/expiry, migrations, test counts, container names, loopback listener, and sanitized acceptance outcome. Include exact status/start/stop/log/rollback/tunnel commands and state these unresolved gates: domain/ICP filing, public CA HTTPS, SSH source restriction/non-root account, paid photo backup/snapshot, RDS TLS, and recovery drill.

- [ ] **Step 7: Final completion gate**

Use `superpowers:verification-before-completion`. Re-run health/listener/container/Flyway/bootstrap checks and read the outputs before claiming success. If any success criterion from the spec is missing, report “cloud deployment incomplete” with the exact blocker rather than calling the site online.
