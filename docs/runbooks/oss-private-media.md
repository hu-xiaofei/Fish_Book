# OSS 私有照片接入

日期：2026-09-12。本文交付 OSS 适配实现、本地验证和后续验收前置条件，不是生产上线或云端验收完成声明。批准的范围与学习环境例外见 [设计规格](../superpowers/specs/2026-09-12-oss-private-media-design.md)。

## 实现与配置

以下为 2026-09-12 OSS 接入阶段的历史范围：当时仅由原后端 API 校验登录和记录所有权，不改变前端 API 或数据库表结构。后续已批准的[管理员照片管理设计](../superpowers/specs/2026-09-12-admin-photo-management-design.md)增加独立管理员查看、替换、删除接口，照片写入版本协议和 V10 数据库变更；当前行为及本地证据见[管理员照片管理手册](admin-photo-management.md)。此后续功能仍未云验收，不能把下文旧测试计数当作它的验证结果。

两阶段都不提供公开或预签名对象 URL，保持 JPEG、PNG、WebP 和 10 MiB 限制、私有 Bucket 及 HTTPS 要求。服务生成 `catches/` 下的不透明对象键，替换和移除通过明确旧对象的清理补偿完成。

默认 `fishbook.media.enabled: false`，关闭时不创建云客户端或获取云凭证。省略 `provider` 仍使用 MinIO；显式选择 `minio` 时原 `endpoint`、`access-key`、`secret-key` 与 `bucket` 配置保持有效。未知提供方拒绝启动；选择 OSS 时使用共享 Bucket 与专属 Endpoint、Region、角色名称，缺失或无效配置拒绝启动。

以下仅是 Spring 属性映射示例，不会自动创建生产 profile，也没有启用任何实际环境。所有值须来自后续核对的目标资源，不得猜测地域、Endpoint 或角色：

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

OSS Endpoint 必须为 HTTPS，客户端启用 V4 签名与 HTTPS 证书验证。目标是杭州地域内网访问，但实际地址和地域须按所选 Bucket 核对。凭证提供方显式使用 ECS RAM 角色和 IMDSv2，不使用默认凭证链、不静默降级 IMDSv1。不得设置用于 OSS 的长期 AccessKey 环境变量，也不得把密钥、私钥或临时凭证复制进仓库或聊天。客户端初始化不创建 Bucket、不改权限、不写对象；应用关闭时释放 OSS 与凭证客户端资源。

## 错误与日志隐私

存储异常对外继续返回 `503`、`MEDIA_STORAGE_UNAVAILABLE` 和中文通用提示“媒体存储暂时不可用”，不泄露 Endpoint、对象键或 SDK 原始错误。适配器不将权限错误、超时或其他失败伪装为删除成功；仅对象已不存在视为成功。读取保留 MIME，最多向应用缓冲区读取 10 MiB + 1 字节以检测超限；恰好 10 MiB 可成功。超限或读取异常时先通过 SDK `forcedClose()` 中止未读完的 HTTP 响应，再正常关闭对象流，避免普通 HTTP 流关闭时继续下载剩余对象。传输层可能预读少量缓冲数据，因此该界限不是整个网络链路的精确字节计量。读取完成时正常关闭并保留连接复用机会；清理失败仍映射中文通用错误。

默认关闭以下 SDK、凭证及传输诊断分类：`com.aliyun.oss`、`com.aliyun.credentials`、`org.apache.http`（包括 `headers` / `wire`）、`okhttp3`、`jdk.httpclient.HttpClient`。实际 SDK 错误日志可能直接输出异常正文、请求头、对象键或凭证，因此默认以隐私优先，牺牲底层排障细节；普通应用通用错误日志仍启用。不得在真实环境开启原始 SDK/传输诊断，也不得记录 exception cause、属性对象、credential、IMDS 响应或 Token。测试只使用人工哨兵文本，没有使用真实秘密。

## 本地交付证据

固定运行时依赖为 `com.aliyun.oss:aliyun-sdk-oss:3.18.5`、`com.aliyun:credentials-java:1.0.6`，`com.aliyun:tea:1.4.2` 由依赖管理固定；没有动态版本。源码仓库名 `aliyun-oss-java-sdk` 不是 Maven artifactId。

本轮 `./mvnw -B dependency:tree -Dscope=runtime` 已成功，实际解析到上述坐标，并包含 HttpClient `4.5.13`、OkHttp JVM `5.3.2`、`javax.xml.bind:jaxb-api:2.3.1` 与 JAXB runtime `4.0.9`。这份实际解析结果用于明确安全评估对象，不代表安全审查通过。

最终复核以 `dependency:list -DincludeScope=runtime` 补齐简略树遗漏的 6 项，完成全部 **148 项**有效编译/运行依赖的 OSV 查询，HTTP 200 且无分页遗漏。7 个不同公告对应 4 个依赖包；另逐项评估 Tomcat 官方页面中 16 个未出现在本次 OSV 结果中的版本相关公告。其触发条件未在当前代码/配置中启用，保留固定依赖并记录部署前重新核对条件；不能据此声称“无漏洞”。详细来源、版本覆盖、适用性、XML 兼容性限制及后续门槛见 [依赖安全评估](oss-dependency-security-assessment.md) 和其中的原始查询快照。

本次使用 Temurin OpenJDK `21.0.12+8-LTS`（Java 21）与 Maven `3.9.16`。下列命令在 `backend/` 执行，Testcontainers 仅启动本地临时 MySQL/MinIO，不连接云数据库、不迁移或删除本地 Compose 数据：

```sh
./mvnw -B -Dtest=CatchPhotoApiIntegrationTest#storageFailureDoesNotExposeSdkDetails test
./mvnw -B -Dtest=DefaultCatchPhotoApplicationServiceTest,CatchPhotoApiIntegrationTest,CatchRecordAuthorizationTest,MediaCleanupServiceTest,MinioMediaStoreIntegrationTest test
./mvnw -B test
./mvnw -B -DskipTests package
```

本轮本地结果：单个接口脱敏用例 1 个、五类专项回归 24 个、完整后端 342 个，最终各轮均为 0 失败、0 错误、0 跳过并显示 `BUILD SUCCESS`，真实本地 MySQL/MinIO 测试已执行。初次隔离环境下 Mockito 自附加失败（1 个错误），获准在隔离外运行本地验证后消除该环境限制。自动化验证不替代真实云端签名、权限或凭证到期刷新验收。

最终修复波次重新验证：`./mvnw -B -Dtest=OssMediaStoreTest test` 为 **23/0/0/0**；`./mvnw -B test` 为 **344/0/0/0**（测试/失败/错误/跳过，2026-09-12 16:20:49 +08:00）。新增回归使用真实 Apache `ContentLengthInputStream`，修复前确认 20 MiB 对象在清理时被读完，修复后中止下载；覆盖读取异常、清理异常和恰好 10 MiB 成功。最终原命令 `./mvnw -B -DskipTests package` 于 16:21:47 +08:00 成功，耗时 1.071 秒；Java 21.0.12，打包有意跳过测试，完整测试已另行执行。此波次仅更改 OSS 读取及其测试、此手册和依赖评估材料；无依赖升级。

Java 21 打包最终按原命令 `./mvnw -B -DskipTests package` 成功（2026-09-12 15:54:28 +08:00，0.733 秒）；该打包步骤按参数有意不执行测试，不能代替上述完整测试。产物为 `backend/target/backend-0.0.1-SNAPSHOT.jar`（约 86 MiB），已完成 Spring Boot repackage，包含 OSS 适配类及 `aliyun-sdk-oss-3.18.5.jar`、`credentials-java-1.0.6.jar`、`tea-1.4.2.jar`。未构建/推送云镜像或执行部署。首次打包受本地 Maven 缓存写权限限制；隔离外下载曾停滞，仅中止本次进程后使用已核实的 Maven 传输超时做一次执行级有界重试，成功后原命令再次通过；未改依赖、构建文件或删除缓存。

仓库根目录的 `git diff --check` 与显式暂存后的 `git diff --cached --check` 用于空白检查；本轮范围仅 README、此手册、照片接口测试及单独获批的媒体不可用提示字面量。前端、数据库迁移、连接配置和现有 MinIO 数据均未更改。

输出不是无警告：有既有 Mockito/JVM 动态 agent 警告、故意无效配置产生的上下文 WARN、重复键测试 WARN、清理重试/耗尽日志，以及既有非媒体意外错误测试的处理器堆栈。没有在本轮扩展日志/测试清洁重构；后者是范围外观察，不代表本项目做过全局日志隐私审计。

新增接口测试使用既有所有权 fixture 和本地 Testcontainers 数据库，注入含人工秘密/Endpoint/对象键哨兵的存储异常，验证响应正文不泄露这些内容。首个可运行结果暴露原处理器固定英文提示与批准中文要求不一致；经单独范围裁定，仅替换媒体不可用的一个固定字面量，不转发异常消息或 cause，不做其他接口本地化，保持 `503`、code 和响应结构不变。

Java 21 客户端及 XML 兼容性验证的边界：实际 SDK `ErrorResponseParser` 的人工 XML 测试仅刻画这一错误解析路径，不证明所有 JAXB 操作或真实云签名可用；假的凭证供应方轮换也不是云端到期刷新验证。此前公告页面获取失败的缺口已由上述可靠查询及官方公告逐项评估补足；当前评估是时间点和配置限定的结论。部署前必须重新查询并核对有效运行配置，尤其 HTTP/2、容器认证、Jackson 绑定与日志配置是否改变适用性。固定依赖和本地通过不等于“生产安全已批准”。

后续复核单独发现：上述依赖评估中将 Jackson `GHSA-mhm7-754m-9p8w` 称为已由 `2.21.5` 修复的断言，与主公告对 2.x 标为无修复版本不一致。该旧断言不能作为已消除风险的证据；管理员照片功能未调整依赖，也未解决此文档发现。部署前需独立重核适用条件和修复策略。

## 尚未云验收：需要单独批准的步骤

当前没有创建 Bucket、改变 RAM/角色、产生云费用、执行对象写入或云数据库认证。下一项需要用户确认的是目标资源与云操作范围；先单独批准只读核验，写入烟测另行批准。

1. 只读核对目标 Bucket 的准确身份、地域、HTTPS Endpoint、私有属性及阻止公共访问设置；核对 ECS 当前绑定角色及用途，不覆盖未知现有角色。资源不存在时先停止，不在验收中顺手创建。
2. 核对角色最小权限：仅所选 Bucket 的业务 `catches/` 前缀 `GetObject` / `PutObject` / `DeleteObject`。额外动作必须由实测证明需要后再单独授权；不授予 OSS 全管理、Bucket 创建/删除或 ACL 修改权限。
3. 在实际 ECS 应用容器内验证 IMDSv2 Token 获取与临时凭证到期刷新，确认没有 IMDSv1 降级。不记录请求头、响应、Token 或密钥，只记录脱敏结果和时间。不得使用 host 网络或 privileged 容器绕过连通性问题。
4. 写入许可必须列出批准的独立测试前缀、明确且唯一的对象键和小图片；测试前缀需要自身的窄权限，不能为了烟测扩展业务角色的整个 Bucket 权限。上传后读取，比较完整字节与 MIME，并验证匿名读取该对象被拒绝。连续删除同一对象两次确认幂等，仅清理这一个明确键，不进行前缀或 Bucket 批量删除。
5. 保存不含秘密的资源核验、签名/权限、刷新、字节/MIME、匿名拒绝与两次删除结果。任一未做或失败，状态仍为“尚未云验收”，不能宣称“照片生产可用”。

## 数据库与发布边界

用户提供的工单回复（2026-09-12 11:45:58）已确认当前本地盘 MySQL 8.4 暂不支持开启 SSL；这不是等待本任务修复的问题，也没有在本任务重新连接云端核验。

用户批准的学习环境例外只适用于测试及非敏感数据：同一 VPC 私网地址、不开通数据库外网地址、白名单只允许目标 ECS、普通账号、强且不复用的密码及备份，明确使用非加密数据库连接。内网隔离不等于加密；应用与数据库间仍缺少 TLS 传输保密和证书身份校验。转为正式业务或敏感数据前，必须重新评估并恢复 RDS TLS 与证书身份校验门槛。

本子项目不执行云数据库认证、迁移、连接配置修改、购买或释放操作。学习部署的实际连接配置由后续 ECS/RDS 部署子项目单独实施和验收。例外不放宽浏览器 HTTPS、生产 Secure Cookie、OSS HTTPS/V4/证书校验或照片私有权限。生产秘密安全注入、非公开 TLS 入口、镜像交付、域名备案与公开发布均是后续门槛，本地打包成功不等于已经部署。

## 回滚与数据保护

本地可恢复 MinIO 提供方及原有配置，不删除、不清空、不迁移现有 MinIO 数据。真实 OSS 一旦写入业务照片，不能简单切换 MinIO 而丢失读取路径：保留可读 OSS 的上一版镜像和原 Bucket。跨存储迁移必须另行设计对象清单、数据库引用、字节校验及回滚；不在本任务进行迁移或清理。
