# ECS 私有学习环境

本文件用于已审阅提交的私有部署。它不会自动连接或修改云资源。入口仅为
`SSH 隧道 → ECS 127.0.0.1:8443 HTTPS → backend:8080`。独立使用
`compose.private-ecs.yaml`，不要合并本地 Compose，也不要启动第二个 backend 或 JVM。
不启动 MySQL、MinIO 或 OSS，不迁移本地照片，不修改安全组或开通 RDS 外网地址。

## 部署前核对

核对 ECS `112.124.7.197`、已固定的 SSH ED25519 指纹、工作树提交和 CI 结果。
SSH 必须使用已有可信 known_hosts 和 `StrictHostKeyChecking=yes`；指纹不匹配即停止。
确认 Docker/Compose、jq、OpenSSL、setpriv 可用，服务器磁盘空间、更新及内存满足构建需求。
约 2 GiB 内存的服务器需要约 2 GiB 交换空间；先检查既有配置，不覆盖未知交换文件。
系统更新若要求重启，先报告影响并安排重启，不自动重启服务器。

RDS 目标为 `rm-bp1pgdmw41u3i6r98.mysql.rds.aliyuncs.com:3306/fishbook`，
普通账号为 `fishbook_app`。该账号使用 `caching_sha2_password`；在已接受的非敏感学习环境
非 TLS 例外下，MySQL 客户端需使用 `--get-server-public-key`，Connector/J 需设置
`allowPublicKeyRetrieval=true`，才能通过 RSA 加密交换登录密码。服务器公钥来自当前连接，
连接同时使用 `sessionVariables=explicit_defaults_for_timestamp=ON`，仅为应用自己的数据库会话
启用显式时间戳默认行为，避免受 RDS 兼容参数影响；不修改 RDS 全局参数。
未经过身份验证；仅适用于同一 VPC 的非敏感学习数据。这不会加密后续数据库流量，
也不能防止能够劫持内网连接的攻击者冒充 RDS。正式业务或敏感数据必须启用 TLS 并验证服务端身份。
首次启动前必须完成下文的只读数据库对象与迁移预检。仅允许已审阅的 V1～V10 迁移；
记录当前迁移版本，禁止清库、删表或手工降级。应用启动会运行 Flyway。

## 首次目录与固定提交

以下整块仅用于核实过的首次部署，在 ECS root Bash 终端执行。任何既有部署根目录（包括
符号链接）都停止，需要人工确认现状后选择已有环境流程；不覆盖、不递归改属主。
先创建父目录和数据目录，`app` 留给 clone 创建；按计划核对并准备交换空间后再 clone。

```bash
(
set -euo pipefail
test "$(id -u)" -eq 0
if test -e /opt/fishbook || test -L /opt/fishbook; then
  printf 'STOP: deployment root already exists; inspect it before proceeding\n' >&2
  exit 1
fi
install -d -m 0755 /opt/fishbook /opt/fishbook/releases /opt/fishbook/data
install -d -m 0700 /opt/fishbook/config
# 仅首次创建；之后照片目录由 UID 10001 独占，不再由其他进程写入。
install -d -o 10001 -g 10001 -m 0700 /opt/fishbook/data/photos
)
```

确认已有交换配置，不覆盖任何同名文件；若计划要求创建新交换文件，完成该步骤后，输入已通过
完整 CI 的 40 位提交 SHA。这里必须 clone 到不存在的 `app`，不要预先创建非空目录或换工作树。

```bash
(
set -euo pipefail
if test -e /opt/fishbook/app || test -L /opt/fishbook/app; then
  printf 'STOP: app path already exists\n' >&2
  exit 1
fi
test -d /opt/fishbook/releases
IFS= read -rp 'Exact CI-passing full Git SHA: ' release_sha </dev/tty
[[ "$release_sha" =~ ^[0-9a-f]{40}$ ]]
git clone --no-checkout https://github.com/hu-xiaofei/Fish_Book.git /opt/fishbook/app
cd /opt/fishbook/app
git fetch origin main
git cat-file -e "$release_sha^{commit}"
git merge-base --is-ancestor "$release_sha" origin/main
git switch --detach "$release_sha"
test "$(git rev-parse HEAD)" = "$release_sha"
checkout_status=$(git status --porcelain --untracked-files=all)
test -z "$checkout_status"
git cat-file -e HEAD:deploy/private-ecs/generate-certificate.sh
(set -o noclobber; printf '%s\n' "$release_sha" >/opt/fishbook/releases/prepared-sha)
)
```

`prepared-sha` 仅表示已准备源码，不表示部署验收成功。clone、SHA 核对或清洁检查失败时停止，
保留现场，不运行下一节 helper。

## 首次证书准备

此时 helper 必须已来自上述固定提交。以下整块拒绝已有 TLS 路径，并在 helper 失败或收到
可捕获的终止信号时恢复目录 root:root/0755；SIGKILL 或断电不能由 shell trap 恢复，重试前必须
先人工检查并恢复目录。恢复失败会明确报错，不能继续启动。

```bash
(
set -euo pipefail
test -f /opt/fishbook/app/deploy/private-ecs/generate-certificate.sh
if test -e /opt/fishbook/tls || test -L /opt/fishbook/tls; then
  printf 'STOP: TLS path already exists; inspect it before proceeding\n' >&2
  exit 1
fi
install -d -o root -g root -m 0755 /opt/fishbook/tls
restore_tls_directory() {
  result=$?
  trap - EXIT
  if ! chown root:root /opt/fishbook/tls; then
    printf 'STOP: TLS directory owner restoration failed\n' >&2
    result=1
  fi
  if ! chmod 0755 /opt/fishbook/tls; then
    printf 'STOP: TLS directory mode restoration failed\n' >&2
    result=1
  fi
  exit "$result"
}
trap restore_tls_directory EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
chown 101:101 /opt/fishbook/tls
chmod 0700 /opt/fishbook/tls
setpriv --reuid=101 --regid=101 --clear-groups \
  env FISHBOOK_TLS_DIR=/opt/fishbook/tls \
  bash /opt/fishbook/app/deploy/private-ecs/generate-certificate.sh
chown root:root /opt/fishbook/tls
chmod 0755 /opt/fishbook/tls
stat -c '%u:%g %a' /opt/fishbook/tls /opt/fishbook/tls/server.crt /opt/fishbook/tls/server.key
)
```

预期目录 `0:0 755`、证书 `101:101 644`、私钥 `101:101 600`。前端 UID/GID 101
通过只读挂载读取密钥，不能替换目录项。固定镜像
`nginxinc/nginx-unprivileged:1.29.1-alpine` 使用 UID 101，支持非特权端口 8443。
证书为 RSA 3072/SHA-256、397 天，SAN 为 localhost 与 127.0.0.1。
helper 仅接受显式现有可写目录、拒绝覆盖，仅输出 SHA-256 指纹及到期时间。
证书更新应另行准备并核对，不要删除现有密钥后盲目重跑。

如果 helper 被中断或报告拒绝现有文件，先停止启动流程，并只检查文件存在状态：

```bash
for name in server.crt server.key; do
  if test -e "/opt/fishbook/tls/$name" || test -L "/opt/fishbook/tls/$name"; then
    printf '%s: present\n' "$name"
  else
    printf '%s: missing\n' "$name"
  fi
done
```

只存在其中一个文件即为部分 pair；两者都存在仍须检查它们是预期普通文件、所有权/权限正确，
并在可信终端比较证书与私钥导出的公钥摘要是否一致（不要输出私钥内容）。helper 的无覆盖发布
不是两个文件的联合原子操作，中断可能留下部分 pair。此时不要自动删除任意文件、重跑覆盖、
清理 `.certificate.*` 或复用来源不明的文件。先人工核对本次运行记录、时间及是否有其他生成进程；
无法确定归属即停止并报告。确认它们仅属于失败的本次生成且没有服务使用后，由运维在同一
受保护目录中把已确认的单个文件移到一个明确的新隔离文件名，保留原权限和所有权，禁止覆盖
已有隔离文件。确认 `server.crt` 和 `server.key` 均不存在，再按上面的 UID101 首次生成步骤
临时开放目录写权限（现有目录使用 `chown 101:101 /opt/fishbook/tls` 及
`chmod 0700 /opt/fishbook/tls`，不重复创建目录），重新运行 `setpriv` 生成命令，
无论成功失败都恢复目录 root:root/0755；隔离文件保留等待人工处理。

## 隐藏输入配置

不要把密码发到聊天、放在命令参数或提交到 Git。示例 env 只列变量，不能直接用于启动。
在 ECS root 的 Bash 终端执行以下整块；输入不回显，拒绝覆盖，生成 root:root、0600 文件。
为保持 Compose 单引号字面值语义，此输入流程不接受单引号、反斜杠或回车；`$` 不会展开。

```bash
(
  set +x
  set -euo pipefail
  umask 077
  test "$(id -u)" -eq 0
  target=/opt/fishbook/config/fishbook.env
  test ! -e "$target" && test ! -L "$target"
  names=(MYSQL_PASSWORD FISHBOOK_ADMIN_EMAIL FISHBOOK_ADMIN_PASSWORD FISHBOOK_ADMIN_NICKNAME)
  values=()
  for name in "${names[@]}"; do
    IFS= read -rsp "$name: " value </dev/tty
    printf '\n' >/dev/tty
    if [[ -z "$value" || "$value" == *"'"* || "$value" == *'\'* || "$value" == *$'\r'* ]]; then
      printf 'Invalid input for %s\n' "$name" >&2
      exit 1
    fi
    values+=("$value")
  done
  # Only a confirmed first, empty environment may use bootstrap=true.
  set -o noclobber
  {
    printf 'FISHBOOK_ADMIN_BOOTSTRAP_ENABLED=true\n'
    for i in "${!names[@]}"; do printf "%s='%s'\n" "${names[$i]}" "${values[$i]}"; done
  } >"$target"
  unset value values
  chmod 0600 "$target"
)
# Prints names and presence only, never values.
awk -F= '/^[A-Z_]+=/ {v=substr($0,index($0,"=")+1); print $1 ": " ((v!="" && v!="\047\047") ? "non-empty" : "empty")}' /opt/fishbook/config/fishbook.env
```

不要运行会输出完整秘密的 `docker compose config`、`docker inspect` 环境转储或 `env`。
仓库 verifier 将渲染结果保存在 0600 临时文件，退出时清理，失败只打印规则名。
宿主 root 和 Docker 管理权限仍可读取容器配置中的秘密，必须限制这些权限。

## 只读数据库预检

在受信任终端使用 MySQL 8.4 客户端；`--password` 每次提示均隐藏输入，不从聊天或命令参数
传密码。先用 `SHOW GRANTS FOR CURRENT_USER()` 检查普通账号对整个 `fishbook` schema
的有效权限，确认 tables/views、routines、triggers、events 元数据完整可见；权限不足或无法
确认可见性时停止并核查授权，不能把信息架构的可见子集当成完整空库。不要自行扩大权限。
以下只查询身份、对象计数和迁移元数据，不读取应用行；连接、权限、查询错误与空库严格区分。

```bash
(
set +x
set -euo pipefail
mysql_readonly() {
  mysql --host=rm-bp1pgdmw41u3i6r98.mysql.rds.aliyuncs.com --port=3306 \
    --user=fishbook_app --database=fishbook --ssl-mode=DISABLED --get-server-public-key --connect-timeout=5 \
    --password --batch --skip-column-names --execute="$1"
}
# Complete the effective-grants review above before running this block.
metadata=$(mysql_readonly "SELECT DATABASE(), CURRENT_USER(),
  (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()),
  (SELECT COUNT(*) FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA = DATABASE()),
  (SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA = DATABASE()),
  (SELECT COUNT(*) FROM information_schema.EVENTS WHERE EVENT_SCHEMA = DATABASE()),
  (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'flyway_schema_history' AND TABLE_TYPE = 'BASE TABLE');")
[[ "$metadata" != *$'\n'* ]]
IFS=$'\t' read -r database db_user tables routines triggers events flyway <<<"$metadata"
test "$database" = fishbook
[[ "$db_user" == fishbook_app@* ]]
for count in "$tables" "$routines" "$triggers" "$events" "$flyway"; do
  [[ "$count" =~ ^[0-9]+$ ]]
done
printf 'schema=%s tables_and_views=%s routines=%s triggers=%s events=%s flyway_tables=%s\n' \
  "$database" "$tables" "$routines" "$triggers" "$events" "$flyway"
if test "$flyway" -eq 1; then
  # Preserve trailing newlines so malformed blank rows cannot disappear in command substitution.
  migration_metadata=$(mysql_readonly 'SELECT installed_rank, version, success FROM flyway_schema_history ORDER BY installed_rank;' && printf '.')
  migration_metadata=${migration_metadata%.}
  # The reviewed migration filenames are V1__*.sql through V10__*.sql, each exactly once.
  # A non-empty contiguous prefix permits Flyway to apply the remaining reviewed migrations.
  if ! printf '%s' "$migration_metadata" | awk -F '\t' '
    NF != 3 || $1 !~ /^[1-9][0-9]*$/ || $2 !~ /^[1-9][0-9]*$/ || $3 !~ /^1$/ {bad=1}
    $1 != NR || $2 != NR || NR > 10 {bad=1}
    END {exit (bad || NR < 1)}'; then
    printf 'FAIL: reviewed_flyway_history\n' >&2
    exit 1
  fi
  unset migration_metadata
  printf 'PASS: reviewed_flyway_history\n'
elif test "$flyway" -eq 0 && test "$((tables + routines + triggers + events))" -eq 0; then
  printf 'PASS: confirmed empty schema; no Flyway table yet\n'
else
  printf 'STOP: schema is non-empty without a valid Flyway history table\n' >&2
  exit 1
fi
)
```

有 Flyway 表时自动验证三列迁移元数据：只接受非空的 V1～VN 连续前缀（1 ≤ N ≤ 10），
installed_rank 与 version 均从 1 连续递增、每项仅一次、success 全为 1。允许 V1～V9 等
旧版前缀由启动时 Flyway 继续迁移；拒绝缺中间项、重复、乱序、空/非数字/未知版本、失败行、
空输出及畸形列，不输出原始迁移行，不修表或重置历史。无 Flyway 表仅在四类对象计数总和为零且可见性已确认时
视为首次空库。任何命令非零退出或输出无法解析都不是“空库”，不得继续构建启动。

## 构建、启动和状态

保持一个 root Bash 终端，定义固定项目命令，避免环境变量覆盖文件中的值：

```bash
dc() {
  env -u MYSQL_PASSWORD -u FISHBOOK_ADMIN_BOOTSTRAP_ENABLED -u FISHBOOK_ADMIN_EMAIL \
    -u FISHBOOK_ADMIN_PASSWORD -u FISHBOOK_ADMIN_NICKNAME \
    docker compose --project-name fishbook-private-ecs --env-file /opt/fishbook/config/fishbook.env \
    -f /opt/fishbook/app/compose.private-ecs.yaml "$@"
}
(
set +x
set -euo pipefail
cd /opt/fishbook/app
bash deploy/private-ecs/verify-compose.sh /opt/fishbook/config/fishbook.env
dc build backend frontend
bash deploy/private-ecs/verify-image-secrets.sh /opt/fishbook/config/fishbook.env
# Only after successful builds and the database preflight above:
dc up -d --no-build --no-deps --wait --wait-timeout 180 backend
dc up -d --no-build --no-deps --force-recreate --wait --wait-timeout 180 frontend
curl --fail --silent --show-error --connect-timeout 5 --max-time 15 --cacert /opt/fishbook/tls/server.crt \
  https://localhost:8443/actuator/health/readiness | jq -e '.status == "UP"' >/dev/null
dc ps
ss -lnt
)
```

镜像检查是两个 `up` 之前的硬门槛。helper 检查固定项目刚构建的 backend/frontend 镜像，
从上述单引号格式的受保护 env 文件读取数据库和 bootstrap 密码，不执行文件、不把秘密传为
命令参数。完整 image inspect 和未截断 history 只写入 0600 临时文件，JSON 解码后逐个检查
字符串与字段名；输出仅为 PASS/FAIL 规则名。任一镜像含任一密码、配置不可解析或检查命令失败
都会停止启动；退出/可捕获信号清理本次临时文件。SIGKILL/断电后须由运维检查受保护的遗留
临时目录。该检查针对原值出现在镜像元数据的泄漏，不替代镜像文件层或编码/变形秘密的审计。

前端仅发布 `127.0.0.1:8443`，backend 不发布宿主端口。两容器都有健康检查、
`unless-stopped` 和 `json-file` 日志轮转（10 MiB × 3）。前端检查本容器 HTTPS 页面，
后端检查内部 readiness；通过入口另行验收 readiness。没有 Nginx 照片静态路由或照片挂载，
照片必须走应用授权 API。照片宿主目录 `/opt/fishbook/data/photos` 只挂载到 backend 的
`/data/photos`，提供方固定为 `filesystem`。不要通过 `--scale` 增加后端副本。

在自己的电脑打开隧道，替换私钥路径和已验证的 SSH 登录用户：

```bash
ssh -i /absolute/path/to/existing-key -o StrictHostKeyChecking=yes \
  -o ExitOnForwardFailure=yes -N -L 127.0.0.1:8443:127.0.0.1:8443 SSH_USER@112.124.7.197
```

浏览器打开 `https://localhost:8443`。通过可信 SSH 获取公开证书指纹并核对后处理自签名提示，
不要关闭 Secure Cookie。可复制公开 `server.crt` 到本机后使用
`curl --cacert /path/to/server.crt https://localhost:8443/actuator/health/readiness` 验证证书与健康。
前端容器内的 healthcheck 使用 `--no-check-certificate` 仅检查本地 HTTPS 服务，不是浏览器或外部客户端信任方案。

以本次新建的测试用户和非敏感小图验收注册、登录、退出、Secure/HttpOnly Cookie、
所有者访问、他人拒绝、管理员查看/替换/删除照片但不可读私有钓获字段及收藏。
检查猜测路径无法获取照片、替换/删除旧对象清理和本次操作无孤儿。
核对 V1～V10 各一次且无失败，不修改未知用户数据。

首次管理员确认登录后立即关闭 bootstrap。Nginx 在启动时解析 backend 地址，后端重建可能
改变容器 IP，因此必须先等唯一后端健康，再重建前端刷新地址，最后验证穿过 HTTPS 代理的
精确 readiness 路径。以下整块在 ECS root Bash 执行，任一步失败即停止：

```bash
(
set -euo pipefail
sed -i 's/^FISHBOOK_ADMIN_BOOTSTRAP_ENABLED=true$/FISHBOOK_ADMIN_BOOTSTRAP_ENABLED=false/' /opt/fishbook/config/fishbook.env
bash deploy/private-ecs/verify-compose.sh /opt/fishbook/config/fishbook.env
dc up -d --no-build --no-deps --force-recreate --wait --wait-timeout 180 backend
dc up -d --no-build --no-deps --force-recreate --wait --wait-timeout 180 frontend
curl --fail --silent --show-error --connect-timeout 5 --max-time 15 --cacert /opt/fishbook/tls/server.crt \
  https://localhost:8443/actuator/health/readiness | jq -e '.status == "UP"' >/dev/null
dc ps
)
```

两个命令分别只重建一个指定服务，`--no-deps` 阻止前端命令再次启动或重建 backend；
Compose 固定 backend 副本数为 1，不传入额外的缩放参数。前端重建或 HTTPS readiness
失败时保持 bootstrap=false，保留数据及已健康的后端，停止前端并调查配置/解析错误。
需要回滚应用时按下面的已验证版本流程处理；任何后端地址变更后仍须先等后端健康、
再重建前端及复验精确 HTTPS readiness，不通过恢复 bootstrap=true 或启动第二个后端恢复服务。

日志诊断先使用状态和错误计数，不把原始日志粘贴到聊天；日志可能包含身份信息或路径。
以下只打印错误行数量。若需具体事件，须在受信任终端逐条脱敏，剔除密码、Cookie、Session、
CSRF Token、绝对照片路径和对象键。不要打开 HTTP/SDK wire debug。

```bash
dc logs --no-color --since 10m 2>/dev/null | awk '/ERROR|FATAL/ {n++} END {print "error_lines=" (n+0)}'
dc stop
# Resume the same verified images and preserved mounts:
dc up -d --no-build --wait --wait-timeout 180
```

## 版本与回滚

每次升级前，在 `/opt/fishbook/releases` 保存上一版已完成业务验收的完整 Git SHA、backend
和 frontend 的 `sha256:` 镜像 ID，并给这两个镜像打唯一保留标签，避免构建时丢失回滚来源。
记录仅包含标识，不保存配置或秘密。构建成功不代表部署验收通过；首次部署在尚无已验证、
数据库兼容且包含本部署栈的旧版本时，没有应用回滚目标，失败只能停止应用并保留现场。

`/opt/fishbook/app` 是唯一部署 checkout，项目名固定为 `fishbook-private-ecs`。`dc` 使用该
目录的绝对 Compose 路径，切换当前目录不会切换配置；禁止创建第二个工作树或项目来回滚。
旧版本必须已经确认兼容当前数据库及照片数据，否则先停止应用并报告，不降级数据库。
在已定义上述 `dc` 函数的 ECS root Bash 中，从已有发布记录输入三个标识后执行：

```bash
(
set -euo pipefail
cd /opt/fishbook/app
test "$(git rev-parse --show-toplevel)" = /opt/fishbook/app
checkout_status=$(git status --porcelain --untracked-files=all)
if test -n "$checkout_status"; then
  printf 'STOP: deployment checkout is dirty; preserve and review changes\n' >&2
  exit 1
fi
failed_sha=$(git rev-parse HEAD)
# No overwrite; record the failed source revision before switching it.
(set -o noclobber; printf '%s\n' "$failed_sha" >"/opt/fishbook/releases/failed-$failed_sha-$(date -u +%Y%m%dT%H%M%SZ).txt")
IFS= read -rp 'Previously verified full Git SHA: ' rollback_sha </dev/tty
IFS= read -rp 'Recorded backend sha256 image ID: ' rollback_backend_id </dev/tty
IFS= read -rp 'Recorded frontend sha256 image ID: ' rollback_frontend_id </dev/tty
[[ "$rollback_sha" =~ ^[0-9a-f]{40}$ ]]
[[ "$rollback_backend_id" =~ ^sha256:[0-9a-f]{64}$ ]]
[[ "$rollback_frontend_id" =~ ^sha256:[0-9a-f]{64}$ ]]
git cat-file -e "$rollback_sha^{commit}"
git cat-file -e "$rollback_sha:compose.private-ecs.yaml"
git cat-file -e "$rollback_sha:deploy/private-ecs/verify-compose.sh"
test "$(docker image inspect --format '{{.Id}}' "$rollback_backend_id")" = "$rollback_backend_id"
test "$(docker image inspect --format '{{.Id}}' "$rollback_frontend_id")" = "$rollback_frontend_id"
dc stop
# Changes only the same clean checkout, with no force/reset/discard operation.
git switch --detach "$rollback_sha"
test "$(git rev-parse HEAD)" = "$rollback_sha"
bash /opt/fishbook/app/deploy/private-ecs/verify-compose.sh /opt/fishbook/config/fishbook.env
docker image tag "$rollback_backend_id" fishbook-private-ecs-backend:latest
docker image tag "$rollback_frontend_id" fishbook-private-ecs-frontend:latest
test "$(docker image inspect --format '{{.Id}}' fishbook-private-ecs-backend:latest)" = "$rollback_backend_id"
test "$(docker image inspect --format '{{.Id}}' fishbook-private-ecs-frontend:latest)" = "$rollback_frontend_id"
dc up -d --no-build --no-deps --force-recreate --wait --wait-timeout 180 backend
dc up -d --no-build --no-deps --force-recreate --wait --wait-timeout 180 frontend
curl --fail --silent --show-error --connect-timeout 5 --max-time 15 --cacert /opt/fishbook/tls/server.crt \
  https://localhost:8443/actuator/health/readiness | jq -e '.status == "UP"' >/dev/null
dc ps
)
```

标识必须来自此前已验证发布记录，不能猜测 SHA、把镜像标签当成身份或选取尚不含本部署栈的
旧提交。上述镜像标签对应固定项目的两个默认构建标签；如果某个旧版本变更过服务名或镜像
命名，先停止并核对其已记录配置，不直接套用命令。任何一步失败即停止后续步骤、保留现场，
不得丢弃脏树、自动降级数据库、清理照片目录或删除旧镜像。HTTPS readiness 通过后仍须完成
业务和照片权限验收，才记录为回滚成功；bootstrap 始终保持 false。

少量学习照片仅存在 ECS 系统盘，磁盘损坏或释放可能导致永久丢失。公开域名/备案、公共可信
HTTPS、正式备份恢复、SSH 来源限制与加固仍属后续工作。云端健康、迁移、权限及照片验收
全部完成前，不能把本地配置测试称为已部署成功。
