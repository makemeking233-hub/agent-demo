#!/usr/bin/env bash
# 下载 bge-small-zh-v1.5 ONNX 模型（add-embedding-rag）
#
# 用途：为 Memory 三层召回的 embedding 粗排层准备本地模型文件。
# 模型缺失时 agent 仍可运行（自动降级为 字面 + sideQuery 两层），只是少了 embedding 召回。
#
# 用法：
#   bash scripts/download-embedding-model.sh              # 下载到默认位置
#   bash scripts/download-embedding-model.sh /custom/dir   # 下载到自定义目录
#
# 默认位置：<user.home>/.agent-demo/models/bge-small-zh-v1.5/model.onnx
#           （与 AgentConfig 缺省 memory.embedding.modelPath 一致）
#
# 模型来源：HuggingFace BAAI/bge-small-zh-v1.5 的 ONNX 导出。
# 若 huggingface.co 访问不稳定，脚本会依次尝试 HF 官方与 hf-mirror 镜像。

set -euo pipefail

MODEL_NAME="bge-small-zh-v1.5"
# HF 官方与国内镜像（后者对国内网络更友好）
HF_HOSTS=(
  "https://huggingface.co"
  "https://hf-mirror.com"
)
ONNX_REPO_PATH="BAAI/${MODEL_NAME}/resolve/main/onnx/model.onnx"

if [ "${1:-}" != "" ]; then
  TARGET_DIR="$1"
else
  TARGET_DIR="${HOME}/.agent-demo/models/${MODEL_NAME}"
fi
TARGET_FILE="${TARGET_DIR}/model.onnx"

echo "=== agent-demo embedding 模型下载 ==="
echo "模型: ${MODEL_NAME}"
echo "目标: ${TARGET_FILE}"

if [ -f "${TARGET_FILE}" ]; then
  SIZE=$(wc -c < "${TARGET_FILE}" | tr -d ' ')
  echo "已存在（${SIZE} 字节），跳过下载。"
  echo "如需重新下载，请先删除该文件。"
  exit 0
fi

mkdir -p "${TARGET_DIR}"

downloaded=0
for host in "${HF_HOSTS[@]}"; do
  url="${host}/${ONNX_REPO_PATH}"
  echo "尝试: ${url}"
  if curl -fL --connect-timeout 15 --retry 2 -o "${TARGET_FILE}.part" "${url}"; then
    mv "${TARGET_FILE}.part" "${TARGET_FILE}"
    downloaded=1
    echo "下载成功: ${host}"
    break
  else
    echo "失败: ${host}"
    rm -f "${TARGET_FILE}.part"
  fi
done

if [ "${downloaded}" -ne 1 ]; then
  echo ""
  echo "错误：所有镜像均下载失败。" >&2
  echo "请手动下载并放置到：${TARGET_FILE}" >&2
  echo "参考地址：https://huggingface.co/BAAI/${MODEL_NAME}/tree/main/onnx" >&2
  exit 1
fi

# 收紧权限（模型不敏感，但与项目 memory 目录 0700 的风格一致）
chmod 0600 "${TARGET_FILE}" 2>/dev/null || true

SIZE=$(wc -c < "${TARGET_FILE}" | tr -d ' ')
echo ""
echo "完成：${TARGET_FILE}（${SIZE} 字节）"
echo "下次启动 agent 时将自动加载该模型并启用 embedding 召回。"
