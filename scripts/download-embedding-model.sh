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
# 模型来源说明（2026-09-23 实测探测）：
#   BAAI/bge-small-zh-v1.5 官方仓库只提供 PyTorch 权重，**没有** onnx/ 子目录
#   （直接取 .../resolve/main/onnx/model.onnx 会 404）
#   onnx-community/bge-small-zh-v1.5-ONNX 提供 ONNX 导出，且使用 **external data** 模式：
#     onnx/model.onnx       ~41KB   仅模型结构 + 外部权重引用
#     onnx/model.onnx_data  ~95MB   实际权重（必须与 model.onnx 同目录同名，否则加载失败）
#   词表取自 BAAI 官方仓库（onnx-community 仓库不含 vocab.txt）
ONNX_REPO="onnx-community/${MODEL_NAME}-ONNX"
ONNX_STRUCTURE_PATH="${ONNX_REPO}/resolve/main/onnx/model.onnx"
ONNX_DATA_PATH="${ONNX_REPO}/resolve/main/onnx/model.onnx_data"
VOCAB_REPO_PATH="BAAI/${MODEL_NAME}/resolve/main/vocab.txt"

if [ "${1:-}" != "" ]; then
  TARGET_DIR="$1"
else
  TARGET_DIR="${HOME}/.agent-demo/models/${MODEL_NAME}"
fi
TARGET_FILE="${TARGET_DIR}/model.onnx"
DATA_FILE="${TARGET_DIR}/model.onnx_data"
VOCAB_FILE="${TARGET_DIR}/vocab.txt"

echo "=== agent-demo embedding 模型下载 ==="
echo "模型: ${MODEL_NAME}"
echo "目标: ${TARGET_DIR}"

# 通用下载函数：依次尝试各镜像；成功返回 0
download_with_mirrors() {
  local repo_path="$1"
  local dest="$2"
  local label="$3"
  if [ -f "${dest}" ] && [ -s "${dest}" ]; then
    echo "  ${label} 已存在，跳过。"
    return 0
  fi
  for host in "${HF_HOSTS[@]}"; do
    url="${host}/${repo_path}"
    echo "  尝试: ${url}"
    if curl -fL --connect-timeout 15 --retry 2 -o "${dest}.part" "${url}"; then
      mv "${dest}.part" "${dest}"
      echo "  成功: ${host}"
      return 0
    fi
    echo "  失败: ${host}"
    rm -f "${dest}.part"
  done
  return 1
}

mkdir -p "${TARGET_DIR}"

echo "[1/3] ONNX 模型结构（约 41KB）"
if ! download_with_mirrors "${ONNX_STRUCTURE_PATH}" "${TARGET_FILE}" "model.onnx"; then
  echo "" >&2
  echo "错误：ONNX 模型结构所有镜像均下载失败。" >&2
  echo "请手动下载并放置到：${TARGET_FILE}" >&2
  echo "参考地址：https://huggingface.co/${ONNX_REPO}/tree/main/onnx" >&2
  exit 1
fi

echo "[2/3] ONNX 外部权重 model.onnx_data（约 95MB，必须与 model.onnx 同目录）"
if ! download_with_mirrors "${ONNX_DATA_PATH}" "${DATA_FILE}" "model.onnx_data"; then
  echo "" >&2
  echo "错误：ONNX 外部权重所有镜像均下载失败。" >&2
  echo "请手动下载并放置到：${DATA_FILE}" >&2
  echo "注意：缺少该文件时 ONNX 加载会失败（模型采用 external data 模式）。" >&2
  exit 1
fi

echo "[3/3] BERT 词表 vocab.txt（约 110KB，tokenizer 必需）"
if ! download_with_mirrors "${VOCAB_REPO_PATH}" "${VOCAB_FILE}" "vocab.txt"; then
  echo "" >&2
  echo "错误：vocab.txt 所有镜像均下载失败。" >&2
  echo "请手动下载并放置到：${VOCAB_FILE}" >&2
  echo "缺少词表时 embedding 层会被自动跳过（召回退化为字面 + sideQuery）。" >&2
  exit 1
fi

# 收紧权限（与项目 memory 目录 0700 的风格一致）
chmod 0600 "${TARGET_FILE}" 2>/dev/null || true
chmod 0600 "${DATA_FILE}" 2>/dev/null || true
chmod 0600 "${VOCAB_FILE}" 2>/dev/null || true

MODEL_SIZE=$(wc -c < "${TARGET_FILE}" | tr -d ' ')
DATA_SIZE=$(wc -c < "${DATA_FILE}" | tr -d ' ')
VOCAB_SIZE=$(wc -c < "${VOCAB_FILE}" | tr -d ' ')
echo ""
echo "完成："
echo "  结构: ${TARGET_FILE}（${MODEL_SIZE} 字节）"
echo "  权重: ${DATA_FILE}（${DATA_SIZE} 字节）"
echo "  词表: ${VOCAB_FILE}（${VOCAB_SIZE} 字节）"
echo "下次启动 agent 时将自动加载并启用 embedding 召回。"
