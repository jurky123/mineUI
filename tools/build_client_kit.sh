#!/usr/bin/env bash
# 构建 MineUI 客户端安装包（mod + Fabric API + Fabric 安装器 + 中文说明）
# 用法: tools/build_client_kit.sh [输出目录]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT_DIR="${1:-$ROOT/tools/out}"
CACHE="$ROOT/tools/cache"
VERSION="$(grep -E '^mineui_version=' "$ROOT/gradle.properties" | cut -d= -f2)"
FABRIC_API_VERSION="$(grep -E '^fabric_api_version=' "$ROOT/gradle.properties" | cut -d= -f2)"
LOADER_VERSION="$(grep -E '^loader_version=' "$ROOT/gradle.properties" | cut -d= -f2)"
FABRIC_INSTALLER_VERSION="1.1.2"

CLIENT_JAR="$ROOT/mineui-client/build/libs/mineui-client-$VERSION.jar"
CLIENT_API_JAR="$ROOT/mineui-client-api/build/libs/mineui-client-api-$VERSION.jar"
FABRIC_API_JAR="$CACHE/fabric-api-$FABRIC_API_VERSION.jar"
INSTALLER_JAR="$CACHE/fabric-installer-$FABRIC_INSTALLER_VERSION.jar"

if [ ! -f "$CLIENT_JAR" ] || [ ! -f "$CLIENT_API_JAR" ]; then
    echo "缺少客户端 jar，请先运行: ./gradlew :mineui-client:build :mineui-client-api:build" >&2
    exit 1
fi

mkdir -p "$CACHE" "$OUT_DIR"
STAGE="$(mktemp -d /tmp/opencode/mineui-kit.XXXXXX)"
trap 'rm -rf "$STAGE"' EXIT

if [ ! -f "$FABRIC_API_JAR" ]; then
    echo "下载 Fabric API $FABRIC_API_VERSION ..."
    curl -fsSL -o "$FABRIC_API_JAR" \
        "https://cdn.modrinth.com/data/P7dR8mSH/versions/UWwhUX3k/fabric-api-0.160.0%2B26.2.jar"
fi

if [ ! -f "$INSTALLER_JAR" ]; then
    echo "下载 Fabric 安装器 $FABRIC_INSTALLER_VERSION ..."
    curl -fsSL -o "$INSTALLER_JAR" \
        "https://maven.fabricmc.net/net/fabricmc/fabric-installer/$FABRIC_INSTALLER_VERSION/fabric-installer-$FABRIC_INSTALLER_VERSION.jar"
fi

mkdir -p "$STAGE/mods"
cp "$CLIENT_JAR" "$STAGE/mods/"
cp "$CLIENT_API_JAR" "$STAGE/mods/"
cp "$FABRIC_API_JAR" "$STAGE/mods/"
cp "$INSTALLER_JAR" "$STAGE/"

cat > "$STAGE/安装说明.txt" <<EOF
MineUI 客户端安装说明（v$VERSION）
==========================================
用于连接服务器 43.160.211.42:25565 的 MineUI 测试。

包内容：
- mods/mineui-client-$VERSION.jar      MineUI 客户端 mod
- mods/mineui-client-api-$VERSION.jar  MineUI 本地扩展点 API（业务客户端 mod 依赖它）
- mods/fabric-api-$FABRIC_API_VERSION.jar  Fabric API（必需）
- fabric-installer-$FABRIC_INSTALLER_VERSION.jar  Fabric 安装器（未装 Fabric 时才需要）
- 安装说明.txt

前提：已安装 Minecraft Java 版 26.2，且至少启动过一次。

第 1 步：安装 Fabric（已装过可跳过）
1) 双击 fabric-installer-$FABRIC_INSTALLER_VERSION.jar
   （双击无效时用命令行：java -jar fabric-installer-$FABRIC_INSTALLER_VERSION.jar）
2) 选择 Client 页签，Minecraft Version 选 26.2，点击 Install
3) 安装完成后启动器里会出现 fabric-loader-$LOADER_VERSION-26.2 版本

第 2 步：放入 mod（两个 jar 都要）
Windows: Win+R 输入 %appdata%\\.minecraft\\mods 回车（没有 mods 文件夹就新建）
macOS:   ~/Library/Application Support/minecraft/mods
Linux:   ~/.minecraft/mods

第 3 步：启动与验证
1) 启动器选择 fabric-loader-$LOADER_VERSION-26.2 启动游戏
2) 进入服务器 43.160.211.42:25565
3) 聊天栏出现  [MineUI] 已连接服务端 v$VERSION（protocol 1）  即成功
4) 输入 /mineui status 应显示：你是 MineUI 客户端：mod $VERSION ...

排查：
- 没有出现提示：确认 mods 里两个 jar 都在、启动的是 Fabric 版本
- 崩溃/进不去：把 .minecraft/logs/latest.log 发给管理员
EOF

KIT_ZIP="$OUT_DIR/mineui-kit-$VERSION.zip"
rm -f "$KIT_ZIP"
python3 - "$STAGE" "$KIT_ZIP" <<'PY'
import os, sys, zipfile

stage, out = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as zf:
    for base, _dirs, files in os.walk(stage):
        for name in sorted(files):
            full = os.path.join(base, name)
            zf.write(full, os.path.relpath(full, stage))
PY
echo "安装包: $KIT_ZIP"
unzip -l "$KIT_ZIP"
