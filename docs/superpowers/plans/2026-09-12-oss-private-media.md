# FishBook OSS Private Media Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 增加可测试的 OSS 私有照片存储实现，保持本地 MinIO、既有照片接口与所有权隔离行为。

**Architecture:** 在既有 `MediaStore` 边界增加 OSS 适配器，以配置选择唯一存储实现。显式 ECS RAM 角色提供 IMDSv2 临时凭证，桥接至 OSS V4 签名；本地测试使用替身，不操作真实云资源。

**Tech Stack:** Java 21、Spring Boot 4.1.0、Maven Wrapper、OSS Java SDK V1 3.18.5、credentials-java 1.0.6、JUnit 5、Mockito、ApplicationContextRunner、既有 Testcontainers MinIO。

**Spec:** `docs/superpowers/specs/2026-09-12-oss-private-media-design.md`（先完整阅读已批准设计）。

## 实施勘误（优先于下方原始示例）

实施时已验证并调整以下细节；下方保留的早期示例不能直接作为最终实现复制：

- MinIO/OSS 两个具体配置类同时设置 enabled 和 provider 条件，不使用可能被独立扫描的嵌套配置绕过关闭条件。以 `MinioConfiguration`、`OssConfiguration` 及配置测试为准。
- 生命周期测试使用生产 `OssConfiguration` 的资源所有权和依赖关系；手动注册测试 Bean 的早期示例不能单独证明生产资源会关闭。
- OSS Maven 坐标为 `com.aliyun.oss:aliyun-sdk-oss:3.18.5`；`aliyun-oss-java-sdk` 是源码仓库名，不是 Maven artifactId。
- SDK 会自行输出异常详情，因此 Task 3 还包含 `application.yml` 默认敏感日志抑制、`OssLoggingPrivacyTest` 行为测试，以及 `OssSdkResponseCompatibilityTest` 的离线 XML 解析兼容性检查。保留应用通用错误日志，不输出 SDK 错误正文或请求头。

依赖安全公告核验和真实云签名、权限、IMDSv2 到期刷新仍是未完成的独立验收项；本地测试不能替代这些检查。学习环境数据库 TLS 例外以设计第 8 节为准。

## Global Constraints

- 本文不授权创建 Bucket、变更 RAM 权限、产生新费用或执行线上数据写入。
- 保持 `MediaStore`、`StoredMedia` 和公开 API 不变；不改变数据库表结构。
- 本地 MinIO 不删除、不清空；不迁移既有照片。
- 不使用 `LATEST` 或动态版本；检查依赖树、Java 21 兼容性及依赖安全公告后锁定版本。
- `fishbook.media.provider` 支持 `minio`、`oss`，省略时仍兼容 MinIO；未知提供方启动失败。
- 媒体关闭时，只创建不可用存储，不创建任一云客户端或获取云凭证。
- OSS Endpoint 必须为 HTTPS，SDK 请求采用 V4 签名并启用 HTTPS 证书验证。
- 显式使用 ECS RAM 角色与 IMDSv2；不静默降级到 IMDSv1，不使用默认凭证链。
- 不输出 Token、密钥、SDK 错误正文或请求头；外部错误继续使用中文通用提示。
- 不使用 host 网络、privileged 容器解决 IMDS 连通性问题。
- 正式业务要求 RDS TLS；用户批准的学习环境仅允许同 VPC 私网、ECS 单独白名单、测试/非敏感数据、普通账号强密码及备份条件下明确采用非加密连接。本子项目仍不执行云数据库认证、迁移或连接配置变更；浏览器和 OSS HTTPS 不放宽。
- 不关闭生产 Secure Cookie，不把照片改为公开读；真实云验证单独取得授权。

## 执行约定与文件边界

以下路径均相对仓库根目录；Maven 命令的工作目录均为 `backend/`，Git 命令在仓库根目录执行。执行前使用 using-git-worktrees 技能安排隔离目录，保留用户目录 `Fish_Book开发流程/`。记录基线测试结果；下载依赖可能需要网络许可，不需要云账号。

| 文件 | 职责 |
| --- | --- |
| `backend/src/main/java/com/fishbook/media/config/MediaProvider.java` | 提供方枚举 |
| `backend/src/main/java/com/fishbook/media/config/MediaProperties.java` | 通用 Bucket、MinIO 配置与兼容构造器 |
| `backend/src/main/java/com/fishbook/media/config/MediaConfiguration.java` | 注册通用配置和关闭时的存储 |
| `backend/src/main/java/com/fishbook/media/config/MinioConfiguration.java` | 仅在启用并选择 MinIO 时创建客户端/存储 |
| `backend/src/main/java/com/fishbook/media/config/OssProperties.java` | OSS Endpoint、地域、角色名校验 |
| `backend/src/main/java/com/fishbook/media/config/OssConfiguration.java` | OSS 专属 Bean、签名及生命周期 |
| `backend/src/main/java/com/fishbook/media/persistence/OssRoleCredentialsProvider.java` | 临时凭证桥接与关闭底层提供方 |
| `backend/src/main/java/com/fishbook/media/persistence/OssMediaStore.java` | 对象读写、限量读取、删除错误映射 |
| `backend/pom.xml` | 固定 SDK 依赖与必要的传递依赖锁定 |
| `backend/src/test/java/com/fishbook/media/config/MediaConfigurationTest.java` | 提供方选择与无网络初始化 |
| `backend/src/test/java/com/fishbook/media/config/OssPropertiesTest.java` | OSS 参数校验 |
| `backend/src/test/java/com/fishbook/media/persistence/OssRoleCredentialsProviderTest.java` | Token、刷新、失败与关闭 |
| `backend/src/test/java/com/fishbook/media/persistence/OssMediaStoreTest.java` | SDK 替身的媒体契约 |
| `docs/runbooks/oss-private-media.md` | 配置说明、云验收门槛和回滚限制 |
| `README.md` | 指向新增接入手册，不宣称生产可用 |

不修改前端、数据库迁移、业务对象键生成、认证、照片服务或清理任务。若回归发现这些既有部分的问题，记录证据并另行定界，不在本计划中重构。

## Task 1: 提供方配置与兼容性

**Files:** 创建 `MediaProvider.java`、`MediaConfiguration.java`、`OssProperties.java`、`OssPropertiesTest.java`；修改 `MediaProperties.java`、`MinioConfiguration.java`、`MediaConfigurationTest.java`（路径见上表）。

**Interfaces:**

- 产出 `MediaProvider { MINIO, OSS }`。
- `MediaProperties(boolean enabled, String endpoint, String accessKey, String secretKey, String bucket, MediaProvider provider)`；保留原五参数构造器，委托至 `MINIO`。
- `OssProperties(String endpoint, String region, String roleName)`，绑定 `fishbook.media.oss`；本任务直接验证属性对象，Task 3 再在 OSS 条件配置内注册。
- `MediaConfiguration` 注册 `MediaProperties`；媒体关闭时创建唯一 `DisabledMediaStore`。
- `MinioConfiguration` 只在 `enabled=true` 且 `provider=minio`（含省略提供方）时生效。

- [ ] **Step 1: 扩展配置 RED 测试（2–5 分钟）**

将现有 ContextRunner 改为 `.withUserConfiguration(MediaConfiguration.class, MinioConfiguration.class)`，保留已有缺失参数测试，增加显式 MinIO 和关闭行为：

```java
@Test
void explicitMinioKeepsOneStore() {
    contextRunner.withPropertyValues(VALID_ENABLED_PROPERTIES.toArray(String[]::new))
            .withPropertyValues("fishbook.media.provider=minio")
            .run(c -> {
                assertThat(c).hasSingleBean(MediaStore.class);
                assertThat(c).hasSingleBean(MinioClient.class);
            });
}

@Test
void disabledOssDoesNotCreateMinio() {
    contextRunner.withPropertyValues("fishbook.media.enabled=false", "fishbook.media.provider=oss")
            .run(c -> {
                assertThat(c).hasSingleBean(MediaStore.class);
                assertThat(c.getBean(MediaStore.class)).isInstanceOf(DisabledMediaStore.class);
                assertThat(c).doesNotHaveBean(MinioClient.class);
            });
}

@Test
void unknownProviderFails() {
    contextRunner.withPropertyValues(VALID_ENABLED_PROPERTIES.toArray(String[]::new))
            .withPropertyValues("fishbook.media.provider=unexpected")
            .run(c -> assertThat(c).hasFailed());
}
```

- [ ] **Step 2: 运行 RED（2–5 分钟）**

```sh
./mvnw -B -Dtest=MediaConfigurationTest test
```

预期新增类型尚不存在导致编译失败；这只证明脚手架缺失。类型添加后必须再次确认未知提供方等行为测试在旧逻辑下失败，不能仅把编译错误当成完整行为 RED。

- [ ] **Step 3: 实现通用属性与条件选择（2–5 分钟）**

在 record 的 `provider` 参数使用 Spring Boot `@DefaultValue("minio")`，显式创建五参数兼容构造器；直接构造时 `provider == null` 归一化为 MINIO。

```java
public MediaProperties(boolean enabled, String endpoint, String accessKey,
                       String secretKey, String bucket) {
    this(enabled, endpoint, accessKey, secretKey, bucket, MediaProvider.MINIO);
}

@AssertTrue(message = "required media configuration is missing")
public boolean isValidWhenEnabled() {
    return !enabled || hasText(bucket) && (provider == MediaProvider.OSS
            || hasText(endpoint) && hasText(accessKey) && hasText(secretKey));
}
```

将 disabled Bean 从 MinioConfiguration 移至 MediaConfiguration，保持原关闭条件。MinioConfiguration 外层配置只承载 enabled 条件；其内部静态配置类承载 provider 条件并包含现有两个 MinIO Bean：

```java
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "fishbook.media.enabled", havingValue = "true")
public class MinioConfiguration {
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "fishbook.media.provider", havingValue = "minio", matchIfMissing = true)
    static class EnabledMinioConfiguration {
        @Bean
        MinioClient minioClient(MediaProperties properties) {
            return MinioClient.builder().endpoint(properties.endpoint())
                    .credentials(properties.accessKey(), properties.secretKey()).build();
        }

        @Bean
        MediaStore minioMediaStore(MinioClient client, MediaProperties properties) {
            return new MinioMediaStore(client, properties);
        }
    }
}
```

删除原有重复 enabled 注解。MediaConfiguration 使用 `@EnableConfigurationProperties(MediaProperties.class)`，定义唯一关闭存储：

```java
@Bean
@ConditionalOnProperty(name = "fishbook.media.enabled", havingValue = "false", matchIfMissing = true)
MediaStore disabledMediaStore() {
    return new DisabledMediaStore();
}
```

不可使用缺少 OSS 时自动兜底的 `@ConditionalOnMissingBean`。MediaProperties 覆盖 toString 为固定脱敏描述，隐藏 accessKey/secretKey，不拼接原始配置值；不给日志传整个属性对象：

```java
@Override
public String toString() {
    return "MediaProperties[configuration=redacted]";
}
```

- [ ] **Step 4: 编写 OSS 校验 RED 测试（2–5 分钟）**

使用 Jakarta Validator 的真实校验器，覆盖 endpoint、region、roleName 为空以及 HTTP/无主机/含查询参数的 Endpoint。测试数据不是真实资源：

```java
@Test
void httpsEndpointNeedsRegionAndRole() {
    try (var factory = Validation.buildDefaultValidatorFactory()) {
        var validator = factory.getValidator();
        assertThat(validator.validate(new OssProperties(
                "https://oss-cn-hangzhou-internal.aliyuncs.com", "cn-hangzhou", "test-role")))
                .isEmpty();
        assertThat(validator.validate(new OssProperties("http://localhost", "cn-hangzhou", "test-role")))
                .isNotEmpty();
        assertThat(validator.validate(new OssProperties("https://", "cn-hangzhou", "test-role")))
                .isNotEmpty();
        assertThat(validator.validate(new OssProperties("https://example.com?token=x", "", "")))
                .isNotEmpty();
    }
}
```

- [ ] **Step 5: 运行校验 RED（2–5 分钟）**

```sh
./mvnw -B -Dtest=OssPropertiesTest test
```

预期未添加约束时非法参数被接受，断言失败。

- [ ] **Step 6: 实现 OSS 参数约束（2–5 分钟）**

OssProperties 标注 `@Validated`、`@ConfigurationProperties("fishbook.media.oss")`，三个字段 `@NotBlank`。使用不回显参数的 AssertTrue 校验：

```java
@AssertTrue(message = "OSS endpoint must be an HTTPS origin")
public boolean isHttpsEndpoint() {
    if (endpoint == null || endpoint.isBlank()) return false;
    try {
        var uri = URI.create(endpoint);
        return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null
                && (uri.getPath().isEmpty() || "/".equals(uri.getPath()));
    } catch (IllegalArgumentException ex) {
        return false;
    }
}
```

- [ ] **Step 7: GREEN 与兼容性回归（2–5 分钟）**

```sh
./mvnw -B -Dtest=MediaConfigurationTest,OssPropertiesTest test
```

预期全部通过；已有五参数 MediaProperties 调用仍能编译，本地配置无需新增 OSS 参数。此时 OSS 还未接线，不声明启用 OSS 可用。

- [ ] **Step 8: 提交这一配置单元（2–5 分钟）**

```sh
git add backend/src/main/java/com/fishbook/media/config backend/src/test/java/com/fishbook/media/config
git commit -m "feat: add compatible media provider configuration"
```

## Task 2: 固定依赖与显式临时凭证桥接

**Files:** 修改 `backend/pom.xml`；创建 `OssRoleCredentialsProvider.java`、`OssRoleCredentialsProviderTest.java`。

**Interfaces:**

- `OssRoleCredentialsProvider implements com.aliyun.oss.common.auth.CredentialsProvider, AutoCloseable`。
- 生产静态工厂 `public static OssRoleCredentialsProvider forRole(String roleName)`。
- 测试注入构造器 `public OssRoleCredentialsProvider(Supplier<CredentialModel> source, Runnable closeAction)`；source 只能在服务端配置中注入，不暴露给前端。
- `public Credentials getCredentials()` 每次委托临时凭证源，传递同一份模型的 ID/Secret/Token；缓存与到期刷新归 credentials SDK。
- `public void setCredentials(Credentials credentials)` 拒绝手工覆盖；`public void close()` 至多关闭底层一次。

- [ ] **Step 1: 固定依赖并核对已发布接口（2–5 分钟）**

以下版本是候选固定版本，实施时先检查 Maven 仓库可解析性；无法解析时停止并记录，不改成范围。credentials 的传递 tea 范围显式收窄为 1.4.2，保留既有 Boot/OkHttp 管理，冲突需依赖树和测试证据处理。

```xml
<!-- dependencies -->
<dependency>
  <groupId>com.aliyun.oss</groupId>
  <artifactId>aliyun-sdk-oss</artifactId>
  <version>3.18.5</version>
</dependency>
<dependency>
  <groupId>com.aliyun</groupId>
  <artifactId>credentials-java</artifactId>
  <version>1.0.6</version>
</dependency>
<!-- dependencyManagement/dependencies: narrow the upstream range -->
<dependency>
  <groupId>com.aliyun</groupId>
  <artifactId>tea</artifactId>
  <version>1.4.2</version>
</dependency>
```

```sh
./mvnw -B dependency:tree -Dverbose
javap -classpath /Users/hdc/.m2/repository/com/aliyun/credentials-java/1.0.6/credentials-java-1.0.6.jar 'com.aliyun.credentials.provider.EcsRamRoleCredentialProvider$Builder'
javap -classpath /Users/hdc/.m2/repository/com/aliyun/credentials-java/1.0.6/credentials-java-1.0.6.jar com.aliyun.credentials.models.CredentialModel
```

如执行环境改变，以 Maven 实际 localRepository 路径定位 jar。核对 IMDSv1 禁用、超时、关闭方法和模型 builder；源仓库 master 不是已发布 jar 的替代证据。

- [ ] **Step 2: 写 Token、刷新与关闭 RED 测试（2–5 分钟）**

```java
@Test
void forwardsRotatingSessionCredentialsAndClosesOnce() {
    var next = new AtomicInteger();
    var closes = new AtomicInteger();
    var provider = new OssRoleCredentialsProvider(() -> CredentialModel.builder()
            .accessKeyId("fake-id-" + next.incrementAndGet())
            .accessKeySecret("fake-secret").securityToken("fake-token").build(),
            closes::incrementAndGet);
    assertThat(provider.getCredentials().getAccessKeyId()).isEqualTo("fake-id-1");
    var refreshed = provider.getCredentials();
    assertThat(refreshed.getAccessKeyId()).isEqualTo("fake-id-2");
    assertThat(refreshed.getSecurityToken()).isEqualTo("fake-token");
    provider.close();
    provider.close();
    assertThat(closes.get()).isEqualTo(1);
}

@Test
void missingTokenCannotBecomeLongLivedCredentials() {
    var provider = new OssRoleCredentialsProvider(() -> CredentialModel.builder()
            .accessKeyId("fake-id").accessKeySecret("fake-secret").build(), () -> {});
    assertThatThrownBy(provider::getCredentials)
            .isInstanceOf(MediaStorageUnavailableException.class)
            .hasMessage("媒体存储暂时不可用");
}
```

再增加 source 抛异常、null 模型、缺 ID/Secret、setCredentials 被拒绝、构造时 source 调用次数为零的测试，不调用真实 `forRole().getCredentials()`。

- [ ] **Step 3: 运行 RED（2–5 分钟）**

```sh
./mvnw -B -Dtest=OssRoleCredentialsProviderTest test
```

预期新增类缺失；添加类骨架后，先运行缺 Token/刷新行为确认失败，再实现。

- [ ] **Step 4: 实现桥接与严格 IMDSv2 工厂（2–5 分钟）**

保留 final source/closeAction，AtomicBoolean 控制关闭。getCredentials 捕获底层 RuntimeException，抛既有通用异常，不记录完整异常；无凭证字符串缓存和自定义 toString。

```java
public static OssRoleCredentialsProvider forRole(String roleName) {
    var delegate = EcsRamRoleCredentialProvider.builder()
            .roleName(roleName).disableIMDSv1(true)
            .connectionTimeout(2000).readTimeout(3000)
            .asyncCredentialUpdateEnabled(false).build();
    return new OssRoleCredentialsProvider(delegate::getCredentials, delegate::close);
}

public Credentials getCredentials() {
    try {
        var value = source.get();
        if (value == null || value.getAccessKeyId() == null || value.getAccessKeyId().isBlank()
                || value.getAccessKeySecret() == null || value.getAccessKeySecret().isBlank()
                || value.getSecurityToken() == null || value.getSecurityToken().isBlank()) {
            throw new MediaStorageUnavailableException();
        }
        return new DefaultCredentials(value.getAccessKeyId(), value.getAccessKeySecret(),
                value.getSecurityToken());
    } catch (RuntimeException ex) {
        throw new MediaStorageUnavailableException(ex);
    }
}

public void setCredentials(Credentials credentials) {
    throw new UnsupportedOperationException("Role credentials cannot be replaced");
}

public void close() {
    if (closed.compareAndSet(false, true)) closeAction.run();
}
```

`asyncCredentialUpdateEnabled(false)` 不禁止按请求自动刷新，只避免桥接初始化的异步取证；过期失败策略保留 SDK 严格默认行为。只关闭可关闭的 EcsRamRoleCredentialProvider，不使用没有 close 接口的 Client 包装再假装已释放资源。

- [ ] **Step 5: GREEN、Java 21 与依赖安全门槛（2–5 分钟）**

```sh
./mvnw -B -Dtest=OssRoleCredentialsProviderTest,MediaConfigurationTest,OssPropertiesTest test
./mvnw -B dependency:tree -Dverbose
```

核对 tea 的有效版本无范围，OkHttp/Kotlin 等依赖不产生运行链接错误。查官方 SDK Security/公告及实际依赖的维护者安全公告；发现高风险未缓解问题，不宣布生产可交付。查不到公告或扫描失败应明确记录“未完成安全核验”，不能声称“无漏洞”。不凭猜测添加 JAXB 等运行依赖；仅根据 Java 21 测试失败的证据添加固定版本修复。

- [ ] **Step 6: 提交凭证单元（2–5 分钟）**

```sh
git add backend/pom.xml backend/src/main/java/com/fishbook/media/persistence/OssRoleCredentialsProvider.java backend/src/test/java/com/fishbook/media/persistence/OssRoleCredentialsProviderTest.java
git commit -m "feat: bridge ECS role session credentials for OSS"
```

## Task 3: OSS 媒体契约与客户端接线

**Files:** 创建 `OssMediaStore.java`、`OssMediaStoreTest.java`、`OssConfiguration.java`；修改 `MediaConfigurationTest.java`。

**Interfaces:**

- 消费 Task 1 的 MediaProperties/OssProperties 与 Task 2 的 `forRole(String)`。
- `public OssMediaStore(OSS client, String bucket)` 实现原 `void put(String, byte[], String)`、`StoredMedia get(String)`、`void delete(String)`。
- `OssConfiguration` 仅在 enabled true/provider oss 时注册 OssProperties、OssRoleCredentialsProvider、OSS、MediaStore。
- OSS Bean destroyMethod `shutdown`，凭证 Bean destroyMethod `close`；OSS 依赖凭证 Bean，先关闭 OSS 再关闭凭证。

- [ ] **Step 1: 上传和读取 RED 测试（2–5 分钟）**

测试实例 `OSS client = mock(OSS.class)`、`OssMediaStore store = new OssMediaStore(client, "test-bucket")`。只使用替身，不创建 Bucket。

```java
@Test
void uploadsBytesAndContentType() throws Exception {
    byte[] bytes = {1, 2, 3};
    store.put("catches/1/2/test", bytes, "image/png");
    var stream = ArgumentCaptor.forClass(InputStream.class);
    var metadata = ArgumentCaptor.forClass(ObjectMetadata.class);
    verify(client).putObject(eq("test-bucket"), eq("catches/1/2/test"),
            stream.capture(), metadata.capture());
    assertThat(stream.getValue().readAllBytes()).isEqualTo(bytes);
    assertThat(metadata.getValue().getContentLength()).isEqualTo(3);
    assertThat(metadata.getValue().getContentType()).isEqualTo("image/png");
    verifyNoMoreInteractions(client);
}

@Test
void readsContentTypeAndClosesResponse() throws Exception {
    var object = spy(new OSSObject());
    var metadata = new ObjectMetadata();
    metadata.setContentType("image/png");
    object.setObjectMetadata(metadata);
    object.setObjectContent(new ByteArrayInputStream(new byte[]{1, 2, 3}));
    when(client.getObject("test-bucket", "catches/1/2/test")).thenReturn(object);
    var stored = store.get("catches/1/2/test");
    assertThat(stored.content()).isEqualTo(new byte[]{1, 2, 3});
    assertThat(stored.contentType()).isEqualTo("image/png");
    verify(object).close();
}
```

已核对既有 StoredMedia 的 accessor 为 `content()`、`contentType()`；保留这些名称，不扩展接口。

- [ ] **Step 2: 运行 RED（2–5 分钟）**

```sh
./mvnw -B -Dtest=OssMediaStoreTest test
```

预期类缺失；新增空实现后继续运行行为 RED，确认元数据/返回内容断言失败。

- [ ] **Step 3: 实现上传和有界读取（2–5 分钟）**

`MAX_BYTES = 10 * 1024 * 1024` 对齐现有 CatchPhotoValidator；不让适配器另行扩展上传大小。上传仅传对象内容/元数据，不设置公共 ACL、不生成 URL。

```java
public void put(String objectKey, byte[] content, String contentType) {
    try {
        var metadata = new ObjectMetadata();
        metadata.setContentLength(content.length);
        metadata.setContentType(contentType);
        client.putObject(bucket, objectKey, new ByteArrayInputStream(content), metadata);
    } catch (RuntimeException ex) {
        throw new MediaStorageUnavailableException(ex);
    }
}

public StoredMedia get(String objectKey) {
    try (var object = client.getObject(bucket, objectKey)) {
        byte[] bytes = object.getObjectContent().readNBytes(MAX_BYTES + 1);
        String type = object.getObjectMetadata().getContentType();
        if (bytes.length > MAX_BYTES || type == null || type.isBlank()) {
            throw new MediaStorageUnavailableException();
        }
        return new StoredMedia(bytes, type);
    } catch (IOException | RuntimeException ex) {
        throw new MediaStorageUnavailableException(ex);
    }
}
```

已核对既有 StoredMedia 构造顺序为 `(byte[] content, String contentType)`，不改 record。读取结束/失败都关闭 OSSObject，其关闭动作释放响应流。

- [ ] **Step 4: 超限、幂等删除和失败 RED 测试（2–5 分钟）**

```java
@Test
void deletesMissingObjectButDoesNotSwallowDenied() {
    var missing = mock(OSSException.class);
    when(missing.getErrorCode()).thenReturn("NoSuchKey");
    doThrow(missing).when(client).deleteObject("test-bucket", "missing");
    assertThatCode(() -> store.delete("missing")).doesNotThrowAnyException();
    var denied = mock(OSSException.class);
    when(denied.getErrorCode()).thenReturn("AccessDenied");
    doThrow(denied).when(client).deleteObject("test-bucket", "denied");
    assertThatThrownBy(() -> store.delete("denied"))
            .isInstanceOf(MediaStorageUnavailableException.class)
            .hasMessage("媒体存储暂时不可用");
}

@Test
void rejectsOversizeObjectAndClosesResponse() throws Exception {
    var object = spy(new OSSObject());
    var metadata = new ObjectMetadata();
    metadata.setContentType("image/png");
    object.setObjectMetadata(metadata);
    object.setObjectContent(new ByteArrayInputStream(new byte[10 * 1024 * 1024 + 1]));
    when(client.getObject("test-bucket", "too-large")).thenReturn(object);
    assertThatThrownBy(() -> store.get("too-large"))
            .isInstanceOf(MediaStorageUnavailableException.class);
    verify(object).close();
}
```

使用参数化测试覆盖 `NoSuchObject`、其他 OSSException、ClientException 超时；读取 InputStream 抛 IOException 仍关闭；10MiB 正好接受、10MiB+1 拒绝；put/get/delete 的 sentinel 错误正文均不出现在外部通用消息。不输出异常 cause 或凭证到测试日志。

- [ ] **Step 5: 运行 RED 并实现删除（分别 2–5 分钟）**

```sh
./mvnw -B -Dtest=OssMediaStoreTest test
```

预期未实现 delete 时 AccessDenied 断言失败；实现：

```java
public void delete(String objectKey) {
    try {
        client.deleteObject(bucket, objectKey);
    } catch (OSSException ex) {
        if (!"NoSuchKey".equals(ex.getErrorCode()) && !"NoSuchObject".equals(ex.getErrorCode())) {
            throw new MediaStorageUnavailableException(ex);
        }
    } catch (RuntimeException ex) {
        throw new MediaStorageUnavailableException(ex);
    }
}
```

- [ ] **Step 6: OSS 配置/生命周期 RED 测试（2–5 分钟）**

ContextRunner 同时注册 MediaConfiguration、MinioConfiguration、OssConfiguration，用替身凭证 Bean 替换实际 forRole。启用 OSS 不配置 MinIO 密钥，断言恰好一个 MediaStore、一个 OSS、零 MinioClient。

```java
@Test
void ossDoesNotNeedMinioSecretsOrFetchCredentialsAtStartup() {
    var gets = new AtomicInteger();
    var closes = new AtomicInteger();
    var provider = new OssRoleCredentialsProvider(() -> {
        gets.incrementAndGet();
        throw new IllegalStateException("test must not access IMDS");
    }, closes::incrementAndGet);
    new ApplicationContextRunner()
            .withUserConfiguration(MediaConfiguration.class, MinioConfiguration.class, OssConfiguration.class)
            .withBean(OssRoleCredentialsProvider.class, () -> provider)
            .withPropertyValues("fishbook.media.enabled=true", "fishbook.media.provider=oss",
                    "fishbook.media.bucket=test-bucket",
                    "fishbook.media.oss.endpoint=https://oss-cn-hangzhou-internal.aliyuncs.com",
                    "fishbook.media.oss.region=cn-hangzhou", "fishbook.media.oss.role-name=test-role")
            .run(c -> {
                assertThat(c).hasSingleBean(MediaStore.class);
                assertThat(c.getBean(MediaStore.class)).isInstanceOf(OssMediaStore.class);
                assertThat(c).hasSingleBean(OSS.class);
                assertThat(c).doesNotHaveBean(MinioClient.class);
                assertThat(gets.get()).isZero();
            });
}
```

使用 Task 2 定义的 public 构造器注入测试源，不增加测试工厂文件。补充关闭/未提供 enabled、缺任一 OSS 参数、错误 Endpoint；关闭时不得创建 OSS/凭证 Bean。生命周期测试直接创建 `AnnotationConfigApplicationContext`，注册具有显式关闭方法的替身，再验证次数：

```java
@Test
void contextClosesClientAndProvider() {
    var client = mock(OSS.class);
    var closes = new AtomicInteger();
    var provider = new OssRoleCredentialsProvider(() -> {
        throw new IllegalStateException("credentials must not be fetched");
    }, closes::incrementAndGet);
    try (var context = new AnnotationConfigApplicationContext()) {
        context.registerBean("credentials", OssRoleCredentialsProvider.class, () -> provider,
                bd -> bd.setDestroyMethodName("close"));
        context.registerBean("oss", OSS.class, () -> client, bd -> {
            bd.setDestroyMethodName("shutdown");
            bd.setDependsOn("credentials");
        });
        context.refresh();
    }
    verify(client).shutdown();
    assertThat(closes.get()).isEqualTo(1);
}
```

同时读取生产 Bean 定义，断言 OssConfiguration 的两个 destroyMethod 与测试契约一致；该测试不能替代真实云端凭证刷新验收。

- [ ] **Step 7: 运行接线 RED（2–5 分钟）**

```sh
./mvnw -B -Dtest=MediaConfigurationTest test
```

预期缺少 OSS 配置或存储 Bean。不要为了通过测试执行 getCredentials、BucketExists 或任何真实网络请求。

- [ ] **Step 8: 实现 OSS 条件配置（2–5 分钟）**

采用具体配置类上的双条件：OssConfiguration 同时要求 enabled=true 和 provider=oss，`@EnableConfigurationProperties(OssProperties.class)` 和以下 Bean 放在该配置类，避免独立扫描嵌套类绕过关闭条件，也避免本地被 OSS 缺失参数阻挡。凭证 Bean 用 `@ConditionalOnMissingBean(OssRoleCredentialsProvider.class)` 允许测试替身；只有凭证 Bean 使用该注解，MediaStore 不自动回退。

```java
@Bean(destroyMethod = "close")
@ConditionalOnMissingBean(OssRoleCredentialsProvider.class)
OssRoleCredentialsProvider ossRoleCredentialsProvider(OssProperties properties) {
    return OssRoleCredentialsProvider.forRole(properties.roleName());
}

@Bean(destroyMethod = "shutdown")
OSS ossClient(OssProperties properties, OssRoleCredentialsProvider credentials) {
    var configuration = new ClientBuilderConfiguration();
    configuration.setSignatureVersion(SignVersion.V4);
    configuration.setConnectionTimeout(3000);
    configuration.setSocketTimeout(10000);
    configuration.setMaxErrorRetry(2);
    return OSSClientBuilder.create().endpoint(properties.endpoint()).region(properties.region())
            .credentialsProvider(credentials).clientConfiguration(configuration).build();
}

@Bean
MediaStore ossMediaStore(OSS client, MediaProperties properties) {
    return new OssMediaStore(client, properties.bucket());
}
```

不设置不安全 TLS 信任器或 hostname verifier。Bean 创建不发送对象操作、不修改权限。核对 OSS 3.18.5 实际 builder 接口后编译；测试通过不代表底层签名已经在真实云端验证。

- [ ] **Step 9: GREEN（2–5 分钟）**

```sh
./mvnw -B -Dtest=OssMediaStoreTest,OssRoleCredentialsProviderTest,MediaConfigurationTest,OssPropertiesTest test
```

预期全通过，无真实云访问，客户端销毁检查通过。测试使用捕获的元数据确认内容类型，Mockito verifyNoMoreInteractions 防止上传时增加 ACL/Bucket 操作。

- [ ] **Step 10: 提交 OSS 接线单元（2–5 分钟）**

```sh
git add backend/src/main/java/com/fishbook/media/config/OssConfiguration.java backend/src/main/java/com/fishbook/media/persistence/OssMediaStore.java backend/src/test/java/com/fishbook/media
git commit -m "feat: implement private OSS media storage"
```

## Task 4: 回归、接入手册与交付证据

**Files:** 创建 `docs/runbooks/oss-private-media.md`；修改 `README.md`；仅在缺少脱敏回归覆盖时扩展 `backend/src/test/java/com/fishbook/catchlog/web/CatchPhotoApiIntegrationTest.java`。其他既有测试只运行、不重构。

**Interfaces:** 使用原照片 API 和所有权检查；新增手册说明设计第 8 节学习环境例外，不提供未经授权的云数据库连接、迁移或降低正式业务 RDS TLS 的上线命令。

- [ ] **Step 1: 确认对外错误脱敏覆盖（2–5 分钟）**

在 CatchPhotoApiIntegrationTest 增加完整用例，使用既有 fixture 和所有权设置，仅访问本地 Testcontainers 数据库：

```java
@Test
void storageFailureDoesNotExposeSdkDetails() throws Exception {
    long id = insertCatch(USER_ID, "catches/9601/private");
    var failure = new MediaStorageUnavailableException(new IllegalStateException(
            "sentinel-secret endpoint=https://private.invalid key=catches/private"));
    when(mediaStore.get("catches/9601/private")).thenThrow(failure);
    var result = mvc.perform(get("/api/v1/catches/{id}/photo", id).with(user(USER_EMAIL)))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("MEDIA_STORAGE_UNAVAILABLE"))
            .andReturn();
    assertThat(result.getResponse().getContentAsString())
            .contains("媒体存储暂时不可用")
            .doesNotContain("sentinel-secret", "private.invalid", "catches/private");
}
```

若原测试已有等价正文脱敏断言，则不修改，仅记录用例位置。若失败，先按 systematic-debugging 确认异常处理边界，不在业务层捕获并吞掉异常。

- [ ] **Step 2: 运行照片/清理/MinIO 回归（2–5 分钟启动，实际完成时间据测试而定）**

```sh
./mvnw -B -Dtest=DefaultCatchPhotoApplicationServiceTest,CatchPhotoApiIntegrationTest,CatchRecordAuthorizationTest,MediaCleanupServiceTest,MinioMediaStoreIntegrationTest test
```

预期全部执行并通过，不应因为 Docker 不可用而跳过后宣称通过。长测试使用非阻塞轮询并保持进度更新；记录执行/失败/跳过数量。确保跨用户访问被拒绝，失败删除仍进入补偿重试，本地 MinIO 字节读写不变。

- [ ] **Step 3: 写实际配置和验收边界（2–5 分钟）**

手册加入以下非秘密配置映射，所有值均由后续核对的目标资源提供，不在代码里放真实密钥：

```yaml
fishbook:
  media:
    enabled: true
    provider: oss
    bucket: ${FISHBOOK_MEDIA_BUCKET}
    oss:
      endpoint: ${FISHBOOK_MEDIA_OSS_ENDPOINT}
      region: ${FISHBOOK_MEDIA_OSS_REGION}
      role-name: ${FISHBOOK_MEDIA_OSS_ROLE_NAME}
```

解释：这是 Spring 属性示例，不会自动创建生产 profile；选择 MinIO 时原 endpoint/access-key/secret-key 保持有效。OSS 使用角色，不设置长期 AccessKey 环境变量，也不把密钥复制进仓库或聊天。

手册逐项写明：

1. 实际云操作需另行批准，先只读核对 Bucket/地域/私有与阻止公共访问、ECS 当前绑定角色及最小权限；不得覆盖未知现有角色。
2. 业务前缀沿用服务生成的 `catches/`，角色仅获所选 Bucket 前缀的 GetObject/PutObject/DeleteObject，额外动作须实测证明；不授予 OSS 全管理。
3. 容器内 IMDSv2 token 获取及临时凭证到期刷新需云验收，不记录响应/Token；无 host/privileged 绕过。
4. 单独批准后，用批准的独立测试前缀、明确唯一对象键上传小图片；读取并比较完整字节与 MIME，匿名访问必须拒绝；连续删除两次，仅清理该键，不批量删除。
5. 云端验证未做就标“尚未云验收”，不说“照片生产可用”；自动化测试不替代真实签名/权限/刷新验证。
6. 工单已确认当前本地盘 MySQL 8.4 暂不支持 SSL；说明用户批准的学习环境例外及非加密风险，本子项目仍不执行云认证/迁移或数据库连接配置变更。正式业务需要恢复 RDS TLS/证书身份校验；浏览器和 OSS HTTPS 不放宽，非公开 TLS 入口、镜像交付和域名备案属于后续子项目。
7. 本地回滚可用 MinIO，生产业务写入 OSS 后保持可读 OSS 的上一版镜像及原 Bucket，不能简单切 MinIO 丢失照片读取路径；无本地数据迁移删除。

README 仅新增相对链接 `[OSS 私有照片接入](docs/runbooks/oss-private-media.md)`，描述“实现与云验收前置条件”，不改正式上线状态。

- [ ] **Step 4: 完整后端验证及范围检查（2–5 分钟启动）**

```sh
./mvnw -B test
./mvnw -B -DskipTests package
```

仓库根目录检查：

```sh
git diff --check
git diff --name-only
git status --short
```

预期测试通过、Java 21 打包成功、无空白问题；变更限于媒体适配/配置/测试/POM/文档，无前端 API 或数据库迁移。审查新增日志，禁止输出 exception cause、属性对象或 credential。记录完整后端执行与跳过数量；无法运行的验证明确报告，不宣称完成。构建不推送镜像、不登录生产数据库。

- [ ] **Step 5: 提交手册并交付（2–5 分钟）**

```sh
git add README.md docs/runbooks/oss-private-media.md
git diff --cached --check
git commit -m "docs: document OSS validation and rollout gates"
```

若扩展了照片故障回归，单独显式加入该测试文件后提交，不 git add 整仓库。用 verification-before-completion 技能检查证据，再报告：已完成的适配与本地测试、仍未做的真实 OSS 验收、安全核验是否完成、学习环境 RDS TLS 例外及其边界（不等于已部署）、下一项需用户确认的云资源操作。

## 设计覆盖与计划自审

- 设计 1/2/3：只实现 OSS 子项目，不处理购买、云权限、RDS 或公开发布；Task 2 锁依赖并设置证据门槛。
- 设计 4：Task 1/3 提供方选择、参数校验、零初始化云请求和关闭资源；五参数构造器保留。
- 设计 5：Task 2 IMDSv2/显式角色/Token/刷新；Task 3 V4/默认 TLS；Task 4 真实角色权限验收。
- 设计 6：Task 3 保留元数据、有界读取、幂等删除/故障映射；Task 4 既有所有权和对外脱敏回归。
- 设计 7：Task 1–3 无云替身测试；Task 4 MinIO/所有权/清理全量回归及真实云验收清单。
- 设计 8：Task 4 RDS TLS、秘密注入、真实 OSS、非公开入口和回滚限制明确区分。

编写时仅核对代码与官方资料，没有执行本计划测试、安装 SDK、修改业务代码或操作云资源。执行人应以已发布 jar 和实际测试复核 API，不把资料阅读视为验证成功。

## SDK 核对依据

- [OSS Java SDK 官方发布记录](https://github.com/aliyun/aliyun-oss-java-sdk/releases)与 [3.18.5 POM](https://github.com/aliyun/aliyun-oss-java-sdk/blob/3.18.5/pom.xml)：固定版本候选及传递依赖。
- [credentials-java 发布日志](https://github.com/aliyun/credentials-java/blob/master/ChangeLog.txt)、[ECS 提供方源码](https://github.com/aliyun/credentials-java/blob/master/src/main/java/com/aliyun/credentials/provider/EcsRamRoleCredentialProvider.java)：1.0.6 候选版本与 IMDSv2/资源关闭核对。
- [凭证缓存接口](https://github.com/aliyun/credentials-java/blob/master/src/main/java/com/aliyun/credentials/provider/SessionCredentialsProvider.java)：按请求缓存刷新与严格过期默认策略。
- [Tea 官方 POM](https://github.com/aliyun/tea-java/blob/master/pom.xml)：1.4.2 固定候选；实施前仍须核对仓库解析和兼容性。
