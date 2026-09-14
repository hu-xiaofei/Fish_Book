# 管理员照片管理

2026-09-13 本地验收。范围依据[批准设计](../superpowers/specs/2026-09-12-admin-photo-management-design.md)。本功能尚未云验收、部署或迁移到 RDS；下面的成功结果来自独立、可丢弃的本地 MySQL/MinIO。

## 使用与权限

现有 `ADMIN` 登录后，从“照片管理”进入 `/admin/photos`，可按“所属用户 ID”筛选有照片的记录，分页后通过“查看照片 {记录 ID}”打开详情。列表仅显示记录 ID、所属用户 ID/昵称、鱼类名、钓获日期、照片状态、版本和更新时间；不展示邮箱、详细钓点、笔记、收藏、对象键或存储配置。没有通用管理员钓获编辑接口。

详情展示原照片和近期成功操作。选择“新照片”会生成本地预览；点击“替换照片”，再次确认才提交，“取消”不修改服务器。删除同样需要确认，钓获记录及其字段保留。替换、删除后的旧照片不能恢复，没有批量删除、恢复、审计编辑或导出功能。格式仍为 JPEG、PNG、WebP，最大 10 MiB，服务端同时验证签名和大小。

普通用户只能通过所有者接口处理自己的照片。管理员也不能通过 `/api/v1/catches/{id}/photo` 绕过所有权；跨所有者管理仅走独立接口。匿名读取返回 `401`，普通用户访问管理 API 返回 `403`，所有者 API 对他人照片统一返回 `404`。管理页面隐藏按钮不能替代服务器授权：Web 层检查 ADMIN，应用服务再次从数据库核实当前账号角色。写入还需要有效 Session 和 CSRF。

退出登录、账号切换或授权失败时移除私有页面内容、选择文件与本地预览；本地对象 URL 随选择变化和卸载回收。实际浏览器验收覆盖退出及切换账号后原照片和预览消失；迟到响应与 URL 回收等细节由组件测试覆盖。

## 接口与版本

| 方法 | 管理路径 | 行为 |
| --- | --- | --- |
| GET | `/api/v1/admin/photos?userId=41&page=0&size=20` | 有照片记录列表，userId 可省略 |
| GET | `/api/v1/admin/photos/{id}` | 最小详情，含 `hasPhoto` 和字符串 `revision` |
| GET | `/api/v1/admin/photos/{id}/content` | 经授权的照片字节 |
| PUT | `/api/v1/admin/photos/{id}/content` | multipart 字段 `photo` 替换已有照片 |
| DELETE | `/api/v1/admin/photos/{id}/content` | 删除照片，保留钓获记录 |
| GET | `/api/v1/admin/photos/{id}/operations?page=0&size=20` | 只读操作分页 |

分页 `page` 非负，`size` 默认 20、范围 1–50；ID 为正整数。列表及操作分页返回 `items/page/size/totalItems/totalPages`。管理员替换没有照片的记录返回 `404`；删除后详情仍能显示无照片和历史操作，记录本身不存在才返回 `404`。

每次所有者或管理员照片 PUT/DELETE 都要先读取真实 `revision`，使用单个带双引号的强版本前置条件，例如响应中 `revision: "12"` 对应请求头 `If-Match: "12"`。不可省略引号，不接受 `*`、弱 ETag、多值、负数或超出 Java Long 的数值。保持字符串表示，不转成可能损失精度的 JavaScript Number，不自行增加版本。新建时可选上传使用新建响应的 revision。

| 状态 | 错误码 | 处理 |
| --- | --- | --- |
| 428 | `PHOTO_VERSION_REQUIRED` | 先读取状态并提供版本 |
| 400 | `INVALID_PHOTO_VERSION` | 修正版本头格式 |
| 409 | `CATCH_PHOTO_CONFLICT` | 显示“照片或记录已被修改，请刷新后重新确认操作” |
| 503 | `MEDIA_STORAGE_UNAVAILABLE` | 显示“媒体存储暂时不可用” |

版本覆盖整个钓获记录，因此修改钓获信息也可能使照片确认失效。发生 `409` 后刷新元数据、清空之前的选择与确认，检查当前内容后重新发起操作；禁止自动获取新版本并重试写入。相同版本且已经无照片的删除返回 `204`，不新增成功日志；拿旧版本重复删除可返回冲突，避免删掉之后上传的新照片。

照片 GET 返回当前 revision 的强 ETag。用户图片、管理图片和管理私有元数据使用 `Cache-Control: private, no-store`；图片保留 MIME、inline 和 nosniff。页面图片地址带 revision 参数，实际字节仍由后端授权读取，不返回公开或预签名 URL。

## 操作证据与清理

V10 给 `catch_records` 增加 `version BIGINT NOT NULL DEFAULT 0`，并创建 `admin_photo_operations`：

| 字段 | 含义 |
| --- | --- |
| `id` | 自增成功操作 ID |
| `actor_user_id` | 实际执行管理操作的 ADMIN 用户 ID |
| `owner_user_id` | 照片所属用户 ID |
| `catch_record_id` | 钓获记录 ID |
| `operation` | `REPLACED` 或 `REMOVED` |
| `previous_version` | 修改前的记录版本 |
| `occurred_at` | 操作时间，TIMESTAMP(6) |

只有管理路径成功提交的真实替换/删除才产生记录。所有者操作、读取、取消、失败及空照片无操作删除不记成功记录。表中不保存图片、原文件名、对象键、邮箱、请求或异常正文；不以级联外键在记录删除时清除证据，但不声称能防止数据库管理员篡改。页面按时间及 ID 倒序分页。

操作 API 的每条 `items` 使用 `id/actorUserId/ownerUserId/recordId/operation/previousRevision/occurredAt`；`previousRevision` 是字符串，与数据库 `previous_version` 对应。

照片引用更新、乐观锁 flush、管理员成功记录和明确旧对象的清理入队在同一数据库事务提交；任一步失败都回滚，不能留下虚假的成功记录。整个记录删除也以记录 ID、用户 ID 和实际版本作为条件。上传新对象后若数据库提交失败，针对这个新对象执行删除补偿；若删除失败可入队 `UPLOAD_ROLLBACK`。如果对象删除和补偿入队所需数据库同时不可用，仍可能残留孤儿对象，需要受控排查，不能宣称绝无孤儿。

清理队列沿用每分钟最多 20 个到期任务、指数退避和八次失败终止。仅删除明确对象，禁止前缀批量删除；失败不恢复 UI 照片。排查方法见[本地清理手册](local-development.md#inspect-media-cleanup-safely)，真实数据处理需另行授权。

## 独立本地验收

不要对已有 `fishbook` 项目运行验收或重新构建：测试会注册用户并写入照片。本地与 CI 统一使用 `e2e/scripts/disposable.cjs` 和版本控制中的 `e2e/compose.disposable.yaml`、`e2e/playwright.config.ts`。Node 24.18.0、Docker Compose 2.24.4+、锁定 Playwright 1.62.1 及匹配浏览器为前置条件，未新增依赖。在 `e2e/` 执行：

```sh
npm ci
npx playwright install chromium
npm run test:preflight
npm run test:config
npm run test:isolated
```

`test:isolated` 自动生成 `fishbook-admin-photo-acceptance-<时间>-<进程>` 项目名，也可通过 `FISHBOOK_E2E_DISPOSABLE_PROJECT` 指定同前缀的新名字。入口仅读取 `.env.example`；在创建任何资源前校验解析后的专用网络/卷、loopback 和数据库端口隔离，并拒绝已存在的同名资源。前端使用动态的 `127.0.0.1` 端口，MySQL/MinIO 不发布主机端口。启动并等待健康后，从标有该项目的前端容器读取实际端口，交给整套测试；完成或失败后 `finally` 仅对此新项目执行 `down --volumes`，删除其测试用户、会话、钓获和照片，保留构建镜像与测试输出。不使用正式 Compose 默认端口、不覆盖真实 `.env`。

样例文件声明的变量会显式覆盖父进程导出的同名变量；`config/up/down` 使用同一个受控环境，保留 PATH 和 Docker 连接设置。仅 `--env-file` 不足以提供这个保证。当前样例使用无引号的字面 `KEY=value`；若将来需要引号或变量展开，入口会拒绝不支持的格式，不会从父环境补出未知凭据。诊断不打印环境内容。

如需复用已经批准保留的独立测试栈，明确指定它再运行；此模式不启动、重建或清理资源：

```sh
FISHBOOK_E2E_DISPOSABLE_PROJECT=fishbook-admin-photo-acceptance-20260913 npm test
```

整个 suite 的前置检查发生在任何浏览器测试之前，`fishbook`、缺失项目、标签不符和非 loopback 映射均拒绝；默认 Playwright 配置也执行同一检查，直接调用不会回落到8080。管理员 fixture 和可信数据库审计的独立检查继续保留。缺少环境或 Docker 权限就是无法验证/失败，不能跳过审计后报告通过。不要通过关闭生产 Secure Cookie 来运行云端测试。

GitHub Actions 的 `docker-and-e2e` 使用同一 `test:isolated`，项目名包含 `github.run_id/run_attempt`，安装浏览器系统依赖后执行，失败时保留 Playwright 输出用于上传。CI 工作流已静态解析，本机实际运行过等效入口；本轮未推送或启动远程 Actions，不能声明远程 CI 已通过。进程被强制终止时 `finally` 可能来不及执行；只按日志中明确项目名核实并清理，不做广泛 prune。

## 2026-09-13 实测结果与保留资源

| 检查 | 实际结果 | 本机日志 |
| --- | --- | --- |
| Java 21 `./mvnw -B test` | 373 测试，0 失败/错误/跳过，18:29:42 +08:00 | `/private/tmp/fishbook-task5-backend-test.log` |
| `./mvnw -B -DskipTests package` | 成功，23:14:52 +08:00；打包本身按参数跳过测试 | `/private/tmp/fishbook-task5-backend-package.log` |
| 前端 `npm test` | 42 文件、340 测试通过 | `/private/tmp/fishbook-task5-frontend-test.log` |
| 前端 `npm run build` / `npm run lint` | 类型/生产构建及 lint 通过 | `/private/tmp/fishbook-task5-frontend-build.log`、`fishbook-task5-frontend-lint.log` |
| 全部实际浏览器流程 | 10/10，通过，0 整用例重试，15.9 秒 | `/private/tmp/fishbook-task5-full-e2e.log` |
| 最终 fixture 安全校验后的管理员专项 | 1/1，通过，4.5 秒 | `/private/tmp/fishbook-task5-admin-e2e-final.log` |
| F5.1 可移植隔离入口 `npm run test:isolated` | 新建专用项目，10/10，通过，16.0 秒，随后精确清理 | `/private/tmp/fishbook-task5-f5-1-isolated-e2e.log` |
| F5.1 最终共享配置 `npm test` | 保留审查栈，10/10，通过，14.9 秒 | `/private/tmp/fishbook-task5-f5-1-final-config-e2e.log` |
| F5.1 入口防护与工作流 | 防护4/4；危险旧项目拒绝；Compose及CI YAML解析通过 | `/private/tmp/fishbook-task5-f5-1-green.log`、`fishbook-task5-f5-1-config.log` |
| F5.2 父环境覆盖防护 | 防护6/6；真实Compose解析断言通过 | `/private/tmp/fishbook-task5-f5-2-green.log`、`fishbook-task5-f5-2-resolved-assertions.log` |
| F5.2 带人工环境冲突的隔离验收 | 10/10，通过，15.9秒，随后精确清理 | `/private/tmp/fishbook-task5-f5-2-isolated-e2e.log` |

首次管理员专项也是 1/1 通过；没有将缺环境、导入或断言失败伪装为测试先行的失败证据。测试先于验收执行编写，未修改产品代码。中间额度暂停后继续同一环境；测试结果的上述时间差不代表重复验证。

独立 owner、ADMIN、陌生人和匿名浏览器上下文实际完成：上传；管理员按可见 owner ID 筛选及预览；替换取消后字节不变；确认替换；所有者刷新获取替换字节；两个已打开页面的同版本竞争由 owner 先提交，admin 收到 `409`，期间只有一次失败写入且未重试，胜者字节保留；管理员重新确认删除，所有者照片消失而其他记录字段保持；陌生人 URL/API、匿名读取被拒绝；退出并切换账号后照片与本地预览消失。图片 GET 的 no-store、强 ETag 和完整字节比较来自浏览器实际响应。

初次交付时受信任的本地数据库证据保存在 `/private/tmp/fishbook-task5-disposable-db-evidence.log`：V10 `success=1`，三个独立验收记录各只有两条管理成功操作。例如当时记录 `6`：actor `1`（数据库角色 ADMIN）、owner `15`，`REPLACED previous_version=1`、`REMOVED previous_version=3`。owner 的竞争胜出和再次上传均未新增管理证据，失败/取消也没有成功记录。具体 ID 仅属于本次可丢弃 fixture，后续复验会增加独立记录。

为最终整体审查暂保留以下资源，尚未清理；协调代理在整体审查结束后负责精确清理：

- 容器：`fishbook-admin-photo-acceptance-20260913-{frontend,backend,mysql,minio}-1`。
- 卷：`fishbook-admin-photo-acceptance-20260913_mysql-data`、`fishbook-admin-photo-acceptance-20260913_minio-data`。
- 网络：`fishbook-admin-photo-acceptance-20260913_default`。
- 本地构建镜像：`fishbook-admin-photo-acceptance-20260913-backend:latest`、`fishbook-admin-photo-acceptance-20260913-frontend:latest`。
- Scratch 配置、解析结果和日志保留于 `/private/tmp/fishbook-admin-photo-acceptance*`、`/private/tmp/fishbook-task5-*`，不是永久归档。

F5.1 可移植入口另建的 `fishbook-admin-photo-acceptance-task5-f51` 已完成自动清理：只删除它的测试容器、两个数据卷和网络，之后逐类查询为空；这些测试数据不可恢复。它的 backend/frontend 本地构建镜像仍保留。上述20260913审查项目未清理，原有 `fishbook` 项目未参与操作。

F5.2 环境修复复验另建的 `fishbook-admin-photo-acceptance-task5-f52` 也已按同样范围完成清理并查询为空。它只使用样例与人工冲突标记，未读取或输出真实父环境凭据；新测试数据不可恢复，构建镜像和日志保留，20260913审查项目继续保留。

只在确认这仍是本次一次性项目后，使用相同 project/env/三个配置执行 `down --volumes`。这会不可恢复地移除该项目的测试用户、记录、会话和 MinIO 图片；保留本地构建镜像和日志。不要省略 project 或执行广泛 prune。既有 `fishbook` 的 8080/3306/9000/9001 容器和卷未被重启、迁移、查询私有数据或复用；最终只读状态记录仍为原有 uptime。

## 尚未完成的边界与风险

此验收不证明 OSS 云角色、签名、临时凭证刷新、生产网络或 RDS 迁移成功；V10 仅在本地可丢弃数据库执行。私有 Bucket、无公开 URL、浏览器 HTTPS、生产 Secure Cookie、OSS HTTPS/V4/证书校验要求没有放宽。已批准的学习型 RDS 内网非加密例外不能扩展为其他传输例外。

测试通过不等于没有已知风险：所有者 `CatchPhotoPanel` 的手动详情读取与后台查询响应先后顺序存在已记录待整体审查项，本专项没有强制复现或证明其安全；管理员同类缓存竞争已在此前任务修复并纳入本次完整组件测试。确认面板的焦点管理仍是已记录的轻微可访问性问题。生产 JS 主包 508.87 kB（gzip 150.76 kB）触发已有 500 kB 警告；后端故意失败场景的 WARN/ERROR（含补偿无法入队、清理耗尽和既有处理器堆栈）与 JVM 动态 agent 警告、Playwright 色彩环境警告仍保留。

依赖没有升级。安装时的两条 moderate 项对应同一 [Vitest 开发服务器公告](https://github.com/vitest-dev/vitest/security/advisories/GHSA-82fw-gwwq-j7x9)，4.1.10 不在修复版本内；仅使用 loopback 不构成已修复声明。另有独立文档发现：[旧 OSS 依赖评估](oss-dependency-security-assessment.md)声称 Jackson `GHSA-mhm7-754m-9p8w` 已由 2.21.5 修复，而[主公告](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-mhm7-754m-9p8w)仍列无修复版本。2026-09-13 已读取两份主公告复核此边界，本功能未解决该旧断言或完成新的全面安全评估。
