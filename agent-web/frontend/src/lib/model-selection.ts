import { inferProvider, type ModelEntry, type ModelSelection, type ProviderGroup } from "../api/chat";

/** 模型选择解析结果（add-provider-catalog-abstract：升级为 ModelSelection + entry） */
export interface ResolvedSelection {
  /** 最终生效的选择（provider / model / reasoningEffort） */
  selection: ModelSelection;
  /** 与 model 对应的目录条目（目录为空 / model 为空时为 null） */
  entry: ModelEntry | null;
}

/** 思考强度兜底值（目录未提供档位时用） */
const DEFAULT_EFFORT = "medium";

/** 在嵌套 providers 里找 model（返回 provider id + entry）。 */
function findModel(
  providers: ProviderGroup[],
  modelId: string
): { providerId: string; entry: ModelEntry } | null {
  if (!modelId) return null;
  for (const p of providers) {
    const hit = p.models.find((m) => m.id === modelId);
    if (hit) return { providerId: p.id, entry: hit };
  }
  return null;
}

/**
 * 由服务端 provider 目录 + 本地历史选择解出最终生效的 {@link ModelSelection}。
 *
 * <p>fix-stale-model-fallback（main）：兜底值取自服务端下发的 `defaultProvider` /
 * `defaultModel`，不再硬编码模型 id。此前硬编码的 `deepseek-chat` 已被上游停用且不在
 * 服务端目录中，却被前端当作兜底值发出，服务端又兜回同一个非法值，最终原样发给上游。
 *
 * <p>add-provider-catalog-abstract（本 change）：目录从平铺 `models[]` 升级为嵌套
 * `providers[]`；选择从 `{model, reasoningEffort}` 升级为
 * {@link ModelSelection}（含 provider）。
 *
 * <p>不变量：
 *
 * - 目录非空时，返回的 `selection.model` 必定是某个 provider 下真实存在的 id；
 * - 目录为空时退到服务端声明的 `defaultModel`，再不行才是空串（「未指定」，由服务端回落）；
 * - `selection.provider` 优先取历史值；为空（旧格式 localStorage）时按 model 前缀推断，
 *   推断失败用 model 实际所属的 provider；
 * - `reasoningEffort` 只在 model 支持 reasoning 时有值，否则为 `undefined`。
 *
 * @param providers       服务端目录（`GET /api/chat/models` 的 `providers[]`）
 * @param defaultProvider 服务端配置的默认 provider id（旧版后端可能不返回）
 * @param defaultModel    服务端配置的默认模型 id（旧版后端可能不返回）
 * @param saved           localStorage 里的历史选择
 */
export function resolveModelSelection(
  providers: ProviderGroup[],
  defaultProvider: string | undefined,
  defaultModel: string | undefined,
  saved: ModelSelection
): ResolvedSelection {
  const savedHit = findModel(providers, saved.model);
  const defaultHit = findModel(providers, defaultModel ?? "");
  const firstHit = providers[0]?.models[0]
    ? { providerId: providers[0].id, entry: providers[0].models[0] }
    : null;
  const hit = savedHit ?? defaultHit ?? firstHit;

  if (!hit) {
    // 目录为空：只能信服务端声明，再不行留空（服务端回落）
    const model = defaultModel ?? "";
    return {
      selection: {
        provider: saved.provider || defaultProvider || inferProvider(model) || "",
        model,
        reasoningEffort: model ? saved.reasoningEffort : undefined,
      },
      entry: null,
    };
  }

  const { providerId, entry } = hit;
  // provider 优先历史值；空（旧格式）→ 前缀推断；仍失败 → 用 model 实际所属 provider
  const provider = saved.provider || inferProvider(entry.id) || providerId;

  let effort: string | undefined;
  if (entry.supportsReasoning && entry.reasoningEfforts.length > 0) {
    const keep = entry.reasoningEfforts.some((e) => e.id === saved.reasoningEffort);
    effort = keep ? saved.reasoningEffort : entry.reasoningEfforts[0].id;
  }

  return { selection: { provider, model: entry.id, reasoningEffort: effort }, entry };
}

/** 供测试 / 调用方复用的默认 effort 常量。 */
export { DEFAULT_EFFORT };
