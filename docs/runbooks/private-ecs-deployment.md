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
普通账号为 `fishbook_app`。当前 `useSSL=false&allowPublicKeyRetrieval=false` 是已接受的
非敏感学习数据例外：VPC 内网连接没有传输加密。正式业务或敏感数据必须启用 TLS 并验证服务端身份。
首次启动前使用受信任终端的 MySQL 客户端 `--password` 隐藏提示进行只读认证，确认
`DATABASE()`、当前账号和 `flyway_schema_history`。仅允许已审阅的 V1～V10 迁移；
记录当前迁移版本，禁止清库、删表或手工降级。应用启动会运行 Flyway。

## 首次目录和证书准备

以下操作在 ECS 的 root 终端执行。先逐项确认目录不存在，若已存在则检查归属和内容，
不要递归改属主或覆盖文件。`/opt/fishbook/app` 为已核对提交的 Git 工作树。

```bash
install -d -m 0755 /opt/fishbook /opt/fishbook/releases /opt/fishbook/data
install -d -m 0700 /opt/fishbook/config
# 仅首次创建；之后照片目录由 UID 10001 独占，不再由其他进程写入。
test ! -e /opt/fishbook/data/photos && install -d -o 10001 -g 10001 -m 0700 /opt/fishbook/data/photos
test ! -e /opt/fishbook/tls && install -d -o 101 -g 101 -m 0700 /opt/fishbook/tls
setpriv --reuid=101 --regid=101 --clear-groups \
  env FISHBOOK_TLS_DIR=/opt/fishbook/tls \
  bash /opt/fishbook/app/deploy/private-ecs/generate-certificate.sh
chown root:root /opt/fishbook/tls
chmod 0755 /opt/fishbook/tls
stat -c '%u:%g %a' /opt/fishbook/tls /opt/fishbook/tls/server.crt /opt/fishbook/tls/server.key
```

预期目录 `0:0 755`、证书 `101:101 644`、私钥 `101:101 600`。前端 UID/GID 101
通过只读挂载读取密钥，不能替换目录项。固定镜像
`nginxinc/nginx-unprivileged:1.29.1-alpine` 使用 UID 101，支持非特权端口 8443。
证书为 RSA 3072/SHA-256、397 天，SAN 为 localhost 与 127.0.0.1。
helper 仅接受显式现有可写目录、拒绝覆盖，仅输出 SHA-256 指纹及到期时间。
证书更新应另行准备并核对，不要删除现有密钥后盲目重跑。

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

## 构建、启动和状态

保持一个 root Bash 终端，定义固定项目命令，避免环境变量覆盖文件中的值：

```bash
cd /opt/fishbook/app
dc() {
  env -u MYSQL_PASSWORD -u FISHBOOK_ADMIN_BOOTSTRAP_ENABLED -u FISHBOOK_ADMIN_EMAIL \
    -u FISHBOOK_ADMIN_PASSWORD -u FISHBOOK_ADMIN_NICKNAME \
    docker compose --env-file /opt/fishbook/config/fishbook.env \
    -f /opt/fishbook/app/compose.private-ecs.yaml "$@"
}
bash deploy/private-ecs/verify-compose.sh /opt/fishbook/config/fishbook.env
dc build
# Only after successful builds and the database preflight above:
dc up -d --wait --wait-timeout 180
dc ps
ss -lnt
```

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

首次管理员确认登录后立即关闭 bootstrap，并仅重建一个后端，重新核对健康：

```bash
sed -i 's/^FISHBOOK_ADMIN_BOOTSTRAP_ENABLED=true$/FISHBOOK_ADMIN_BOOTSTRAP_ENABLED=false/' /opt/fishbook/config/fishbook.env
bash deploy/private-ecs/verify-compose.sh /opt/fishbook/config/fishbook.env
dc up -d --no-deps --force-recreate --wait --wait-timeout 180 backend
dc ps
```

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

部署前在 `/opt/fishbook/releases` 记录已验证 Git SHA 和两个镜像 ID（仅记录标识，不保存配置或秘密），
给上一版镜像打唯一保留标签。构建成功并不代表部署验收通过。新版本失败时停止新容器，
保留照片与 env；确认数据库迁移兼容后，将两个已记录的旧镜像恢复到该项目构建标签，
在旧的已验证提交工作树用同一 Compose 和 `up -d --no-build --wait` 启动，再做验收。
不要自动降级数据库、清理照片目录或删除旧镜像。旧版本不兼容新数据时保持停止并报告。

少量学习照片仅存在 ECS 系统盘，磁盘损坏或释放可能导致永久丢失。公开域名/备案、公共可信
HTTPS、正式备份恢复、SSH 来源限制与加固仍属后续工作。云端健康、迁移、权限及照片验收
全部完成前，不能把本地配置测试称为已部署成功。
