#!/usr/bin/env bash
# ============================================
# agent-demo Unix launcher
# 自动设置 UTF-8 防止中文乱码
# ============================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
JAR="$PROJECT_ROOT/target/agent-cli.jar"

if [[ ! -f "$JAR" ]]; then
    echo "[agent] 未找到 $JAR" >&2
    echo "       请先运行: mvn clean package -DskipTests" >&2
    exit 1
fi

export LC_ALL="${LC_ALL:-C.UTF-8}"
export LANG="${LANG:-C.UTF-8}"
export JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8"

java $JAVA_TOOL_OPTIONS -jar "$JAR" "$@"