# OSS integration: effective runtime dependency advisory assessment

Assessed 2026-09-12; final OSV response received **2026-09-12T08:21:04.168Z** (16:21:04 +08:00). Scope: the backend at `a869162` plus the bounded-read cleanup fix; the POM and dependency versions are unchanged. This completes a point-in-time advisory assessment, **not a declaration of vulnerability absence or production approval**.

## Coverage and reproducibility

Commands run successfully from `backend/` with Maven 3.9.16 and Temurin Java 21.0.12:

```sh
./mvnw -B dependency:tree -Dscope=runtime
./mvnw -B dependency:tree -Dscope=runtime -Dverbose
./mvnw -B dependency:list -DincludeScope=runtime
```

The compact tree displayed 142 artifacts. Cross-checking `dependency:list` found **148 effective compile/runtime artifacts**: the compact tree omitted `jakarta.xml.bind-api:4.0.5`, `jakarta.activation-api:2.1.4`, `byte-buddy:1.18.10`, `spring-boot-web-server:4.1.0`, `spring-boot-autoconfigure:4.1.0`, and `slf4j-api:2.0.18`. The final query includes all 148. The root project, test-only dependencies, build plugins, JDK, container/OS packages, frontend, and remotely supplied environment overrides are outside this package-coordinate assessment.

The [saved query and response](oss-dependency-osv-snapshot.json) contain the complete ordered inventory and raw OSV results. Queries used exact Maven ecosystem names and resolved versions; the JaCoCo `runtime` classifier was correctly separated from version `0.8.8`. The final public `POST https://api.osv.dev/v1/querybatch` returned HTTP 200, 148 result entries, no errors and no `next_page_token`. Result ordering and pagination were checked against the [OSV API contract](https://google.github.io/osv.dev/post-v1-querybatch/). Only public package coordinates were transmitted; no code, private artifact coordinates, credentials or project data were sent.

OSV returned seven distinct advisory IDs, eight package/advisory matches across four packages. The remaining 144 entries returned no matching IDs. This is database coverage evidence, not proof of safety. Initial sandbox DNS access failed; the authorized public-network retry succeeded with a 25-second single-query and 40-second batch timeout. The earlier failed/filtered GitHub advisory pages are superseded by this successful assessment, not counted as negative findings.

Packaging cross-check: the Java 21 Boot jar contains 135 nested libraries, covering 134 of the resolved artifacts (Boot omits 14 starter metadata jars) plus `spring-boot-jarmode-tools:4.1.0` added by the packaging plugin. A supplemental exact-coordinate query at 2026-09-12T08:25:56Z returned HTTP 200 and `{}`; it is saved in the same snapshot. Thus every nested library has a query result, without claiming a full build-plugin or embedded-loader source audit.

## Resolved OSS dependencies and mediation

| Component | Effective version and disposition |
| --- | --- |
| `com.aliyun.oss:aliyun-sdk-oss` | 3.18.5; no matching OSV IDs |
| `com.aliyun:credentials-java` / `credentials-api` | 1.0.6 / 1.0.0; no matching IDs |
| `com.aliyun:tea` | 1.4.2; explicitly managed, replacing upstream `[1.2.0, 2.0.0)` range; no matching IDs |
| Apache HttpClient / HttpCore | 4.5.13 / 4.4.16; OSS's nearer 4.5.13 wins over core SDK's 4.5.14; Boot manages HttpCore from 4.4.13; no matching IDs |
| Commons Logging / Codec / Gson | 1.3.6 / 1.21.0 / 2.13.2 via dependency management, replacing older upstream requests; no matching IDs |
| OkHttp / OkHttp JVM / Okio JVM / Kotlin | 5.3.2 / 5.3.2 / 3.16.4 / 2.3.21; existing MinIO/direct dependency wins over Tea's 4.12.0 request; no matching IDs |
| JDOM / Jettison / dom4j | 2.0.6.1 / 1.5.4 / 2.1.4; no matching IDs |
| Aliyun SDK core / RAM / trace / java-core | 4.7.8 / 3.1.0 / 0.2.11-beta / 0.2.11-beta; no matching IDs |
| Bouncy Castle / OpenTelemetry API | 1.84 / 1.62.0, superseding SDK requests 1.79 / 1.38.0; no matching IDs |
| JaCoCo agent | 0.8.8, runtime classifier unexpectedly inherited as compile dependency from credentials-java; assessed and no matching IDs; presence alone does not mean the JVM agent is enabled |

The [Apache maintainer's CVE-2020-13956 notice](https://www.openwall.com/lists/oss-security/2020/10/08/4) places the HttpClient URI-authority flaw below 4.5.13; the effective 4.5.13 includes that fix. Fetching the older HttpClient security-page URL failed, so that page is not treated as evidence. No speculative 4.5.14 override was added.

The Java 21 XML mixture remains explicit: SDK core requests `javax.xml.bind:jaxb-api:2.3.1` and JAXB runtime 2.3.2; Boot manages the runtime/core/txw2 to 4.0.9, alongside Jakarta JAXB API 4.0.5, Jakarta activation 2.1.4, javax activation 1.2.0, Angus activation 2.0.3 and istack runtime 4.1.2. Every coordinate was queried with no matching IDs. Jakarta runtime 4 is not a replacement provider for the javax JAXB 2 API. The adapter and credentials bridge do not invoke JAXBContext; the real SDK artificial XML error-parser regression and Java 21 full suite/package pass. This establishes the exercised paths only, not all legacy SDK JAXB features. Adding a javax JAXB operation requires an explicit provider compatibility test and reassessment; no unneeded legacy runtime was introduced.

## OSV matches: applicability and disposition

These are real version matches; none is dismissed just because it predates the OSS work. Applicability below is a code/configuration inference for this checkout, not an exploit test or a statement about unseen deployment settings.

| Advisory | Affected resolved package; upstream fix | Application evidence and disposition |
| --- | --- | --- |
| [GHSA-qv9r-c865-cp47 / CVE-2026-49844](https://github.com/advisories/GHSA-qv9r-c865-cp47) | Log4j API 2.25.4; fixed 2.25.5 / 2.26.1 | Requires attacker-controlled non-finite numbers in MapMessage JSON output. Current tree uses Log4j-to-SLF4J and Logback, no Log4j core/JSON layout; no MapMessage usage/configuration found. Conditional, not exercised; reassess if logging changes. [Apache notice](https://logging.apache.org/security.html#CVE-2026-49844). |
| [GHSA-9xv2-5v5q-p794 / CVE-2026-65905](https://github.com/advisories/GHSA-9xv2-5v5q-p794) | Tomcat core 11.0.22; fixed 11.0.25 | Requires container DIGEST authentication. FishBook uses application login/session and Spring Security, with no container DIGEST configuration. Conditional, not exercised. |
| [GHSA-gcx9-497g-6cp6 / CVE-2026-65182](https://github.com/advisories/GHSA-gcx9-497g-6cp6) | Tomcat core 11.0.22; fixed 11.0.25 | Requires ordered declarative container security constraints. No web.xml, ServletSecurity or container constraint customization; authorization resides in Spring Security and ownership services. Conditional, not exercised. |
| [GHSA-h3x4-894j-xpx5 / CVE-2026-68525](https://github.com/advisories/GHSA-h3x4-894j-xpx5) | Tomcat core 11.0.22; fixed 11.0.25 | Requires container FORM authentication and method-specific constraints; neither configured. Conditional, not exercised. |
| [GHSA-5gvw-p9qm-jgwh / CVE-2026-59889](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-5gvw-p9qm-jgwh) | Jackson databind **both** 2.21.4 and 3.1.4; fixed 2.21.5 / 3.1.5 on these branches | Requires an active JsonView protecting a JsonUnwrapped container property. DTOs do not use either annotation or view-based authorization. Conditional, not exercised. |
| [GHSA-5jmj-h7xm-6q6v / CVE-2026-54515](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-5jmj-h7xm-6q6v) | Jackson databind 2.21.4; fixed 2.21.5; installed 3.1.4 already fixed for this advisory | Requires property-level ignore rules combined with case-insensitive matching. No such annotations/options in application code/configuration. Conditional, not exercised. |
| [GHSA-mhm7-754m-9p8w](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-mhm7-754m-9p8w) | Jackson databind 2.21.4; fixed 2.21.5 | Requires JsonView and external type-id creator-property binding. Neither used. Conditional, not exercised. |

Primary checks: [Apache Tomcat security notices](https://tomcat.apache.org/security-11.html), Apache Log4j's notice, and FasterXML's original maintainer advisories above. GitHub marks the three Tomcat matches Critical; Apache rates the DIGEST/FORM issues Low and constraint bypass Important. Both ratings are recorded rather than silently choosing one. The absence of container authentication is what supports the applicability decision.

## Additional Tomcat vendor findings absent from this OSV response

The vendor page lists 19 findings whose version ranges include 11.0.22: the three above and the 16 below. These were not inferred away from empty OSV entries. All require currently absent features; no dependency update is necessary to repair an exercised path in this OSS change. These conditions must be rechecked before deployment configuration changes.

| CVE(s) | Required feature absent from this checkout; disposition | Fixed in |
| --- | --- | --- |
| 2026-73180, 2026-66299 | WebSocket endpoints / shipped chat example; none shipped | 11.0.25 |
| 2026-68763, 2026-65637 | HTTP/2, with strict SNI relevant to the latter; HTTP/2 is unset and installed Boot `Http2` defaults false | 11.0.25 |
| 2026-68569 | DataSourceRealm authentication; application Spring Security used | 11.0.25 |
| 2026-66422 | Declarative role references; none configured | 11.0.25 |
| 2026-65927 | RewriteValve rules; none configured | 11.0.25 |
| 2026-65183 | Unix domain socket connector; none configured | 11.0.25 |
| 2026-59084, 2026-55955 | Tomcat cluster EncryptInterceptor; no clustering/Tribes artifact | 11.0.24 / 11.0.23 |
| 2026-59083, 2026-53404 | RewriteValve rules; none configured | 11.0.24 / 11.0.23 |
| 2026-55956, 2026-55276 | Declarative servlet constraints / their effective-web.xml log; none configured or relied upon | 11.0.23 |
| 2026-53434 | FFM TLS connector and CRLs; neither configured | 11.0.23 |
| 2026-50229 | Bundled number-guess example; not shipped | 11.0.23 |

All rows derive from the [vendor's versioned security page](https://tomcat.apache.org/security-11.html). Source evidence is `identity/security/SecurityConfig.java`, DTOs under `backend/src/main/java`, `application*.yml`, the runtime dependency inventory, and absence of custom servlet/connector/realm/valve registrations. Search covered the relevant annotations, configuration names and registration APIs. Installed Boot `Http2` bytecode confirmed its boolean default; this does not inspect cloud overrides.

## Decision and remaining gates

Retain fixed OSS 3.18.5, credentials-java 1.0.6 and Tea 1.4.2; do not widen this fix into a speculative framework upgrade. Version-matched conditional findings remain documented technical risk, not "fixed" or "no vulnerabilities." Before deployment, re-run the inventory/query and revalidate logging, Jackson binding, container authentication/constraints, HTTP/2, TLS connector, WebSocket, rewrite and cluster conditions against the **effective deployment configuration**. If any required condition becomes present, update the affected coordinated dependency family to its verified fixed release and run scoped/full/package validation before use.

OSV can lag vendor disclosures, as demonstrated here. This assessment does not claim exhaustive vendor coverage for every transitive package, source-level reachability through all third-party internals, artifact integrity, or unknown vulnerabilities. Real OSS HTTPS/V4/private-access/IMDSv2 expiry-refresh tests remain separately unfulfilled cloud gates. The Java 21 XML caveat remains a compatibility gate for newly exercised SDK paths. Existing generic unexpected-error stack logging is a separate deferred privacy observation, outside this fix.
