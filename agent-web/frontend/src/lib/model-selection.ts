import type { ModelEntry } from "../api/chat";

/** 模型选择解析结果 */
export interface ResolvedSelection {
  /** 最终生效的 model id；空串表示「未指定」，由服务端回落默认值 */
  model: string;
  /** 最终生效的思考强度 */
  effort: string;
  /** 与 model 对应的目录条目（目录为空时为 null） */
  entry: ModelEntry | null;
}

/** 思考强度兜底值（原 App.tsx 内联逻辑保持不变） */
const DEFAULT_EFFORT = "medium";

/**
 * 由服务端目录 + 本地历史选择解出最终生效的模型与思考强度。
 *
 * fix-stale-model-fallback：兜底值取自服务端下发的 `defaultModel`，不再硬编码模型 id。
 * 此前硬编码的 `deepseek-chat` 已被上游停用且不在服务端目录中，却被前端当作兜底值发出，
 * 服务端又兜回同一个非法值，最终原样发给上游。
 *
 * 不变量：
 *
 * - 目录非空时，返回的 `model` 必定是 `models` 中真实存在的 id；
 * - 目录为空（服务端没配任何模型）时退到服务端声明的 `defaultModel`，再不行才是空串。
 *
 * @param models       服务端目录（`GET /api/chat/models` 的 `models[]`）
 * @param defaultModel 服务端配置的默认模型 id（旧版后端可能不返回）
 * @param savedModel   localStorage 里的历史选择（空串表示无）
 * @param savedEffort  localStorage 里的历史思考强度
 */
export function resolveModelSelection(
  models: ModelEntry[],
  defaultModel: string | undefined,
  savedModel: string,
  savedEffort: string
): ResolvedSelection {
  const inCatalog = (id: string | undefined): id is string =>
    !!id && models.some((m) => m.id === id);

  let model: string;
  if (inCatalog(savedModel)) {
    model = savedModel;
  } else if (inCatalog(defaultModel)) {
    // 服务端默认值可用（正常路径）
    model = defaultModel;
  } else {
    // 两者都不在目录中：目录非空就退到首项；目录为空则只能信服务端声明，否则留空
    model = models[0]?.id ?? defaultModel ?? "";
  }

  const entry = models.find((m) => m.id === model) ?? null;
  const effort =
    entry && entry.reasoningEfforts.includes(savedEffort)
      ? savedEffort
      : entry && entry.reasoningEfforts.length > 0
        ? entry.reasoningEfforts[0]
        : DEFAULT_EFFORT;
  return { model, effort, entry };
}
