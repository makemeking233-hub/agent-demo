import { describe, expect, it } from "vitest";
import type { ModelEntry, ModelSelection, ProviderGroup, ReasoningEffort } from "../api/chat";
import { resolveModelSelection } from "./model-selection";

/** 把档位 id 列表转成 ReasoningEffort[]（v0.2 起 reasoningEfforts 是对象数组）。 */
function efforts(ids: string[]): ReasoningEffort[] {
  return ids.map((id) => ({ id, name: id }));
}

function entry(id: string, effortIds: string[] = []): ModelEntry {
  return { id, name: id, supportsReasoning: effortIds.length > 0, reasoningEfforts: efforts(effortIds) };
}

/**
 * 后端实际下发的 provider 目录（deepseek-chat 已被上游停用，不在此列）。
 * add-provider-catalog-abstract：目录从平铺 models[] 升级为嵌套 providers[]。
 */
const PROVIDERS: ProviderGroup[] = [
  {
    id: "deepseek",
    name: "DeepSeek",
    models: [
      entry("deepseek-v4-flash"),
      entry("deepseek-reasoner", ["low", "medium", "high"]),
      entry("deepseek-v4-pro", ["low", "medium", "high"]),
    ],
  },
  {
    id: "anthropic",
    name: "Anthropic",
    models: [entry("claude-opus-4-20250514", ["medium"])],
  },
];

const DEFAULT_PROVIDER = "deepseek";
const DEFAULT_MODEL = "deepseek-v4-flash";

function sel(model: string, effort?: string, provider = ""): ModelSelection {
  return { provider, model, reasoningEffort: effort };
}

describe("resolveModelSelection", () => {
  it("保留仍然合法的历史选择", () => {
    const r = resolveModelSelection(
      PROVIDERS,
      DEFAULT_PROVIDER,
      DEFAULT_MODEL,
      sel("deepseek-reasoner", "high")
    );
    expect(r.selection.model).toBe("deepseek-reasoner");
    expect(r.selection.reasoningEffort).toBe("high");
  });

  it("历史选择已下线时回落到服务端默认值", () => {
    // 核心回归：deepseek-chat 曾是硬编码兜底值，也是导致非法 id 被透传的起点
    const r = resolveModelSelection(
      PROVIDERS,
      DEFAULT_PROVIDER,
      DEFAULT_MODEL,
      sel("deepseek-chat", "medium")
    );
    expect(r.selection.model).toBe(DEFAULT_MODEL);
    expect(r.entry?.id).toBe(DEFAULT_MODEL);
  });

  it("没有历史选择时使用服务端默认值", () => {
    const r = resolveModelSelection(PROVIDERS, DEFAULT_PROVIDER, DEFAULT_MODEL, sel("", "medium"));
    expect(r.selection.model).toBe(DEFAULT_MODEL);
  });

  it("旧版后端不返回 defaultModel 时退到目录首项", () => {
    const r = resolveModelSelection(PROVIDERS, DEFAULT_PROVIDER, undefined, sel("", "medium"));
    expect(r.selection.model).toBe("deepseek-v4-flash");
  });

  it("解析结果永远落在目录内", () => {
    const stale = ["", "deepseek-chat", "gpt-4o", "totally-unknown"];
    for (const saved of stale) {
      const r = resolveModelSelection(PROVIDERS, DEFAULT_PROVIDER, DEFAULT_MODEL, sel(saved, "medium"));
      const allIds = PROVIDERS.flatMap((p) => p.models.map((m) => m.id));
      expect(allIds).toContain(r.selection.model);
    }
  });

  it("服务端默认值不在目录中时退到目录首项", () => {
    const r = resolveModelSelection(PROVIDERS, DEFAULT_PROVIDER, "deepseek-chat", sel("", "medium"));
    expect(r.selection.model).toBe("deepseek-v4-flash");
  });

  it("目录为空时沿用服务端声明的默认值", () => {
    const r = resolveModelSelection([], DEFAULT_PROVIDER, DEFAULT_MODEL, sel("deepseek-chat", "high"));
    expect(r.selection.model).toBe(DEFAULT_MODEL);
    expect(r.entry).toBeNull();
  });

  it("目录为空且服务端未声明默认值时返回空串", () => {
    const r = resolveModelSelection([], undefined, undefined, sel("deepseek-chat", "high"));
    expect(r.selection.model).toBe("");
    expect(r.entry).toBeNull();
  });

  it("返回的 id 只可能来自目录、服务端默认值或空串", () => {
    const allIds = PROVIDERS.flatMap((p) => p.models.map((m) => m.id));
    const allowed = new Set([...allIds, DEFAULT_MODEL, ""]);
    for (const providers of [PROVIDERS, []]) {
      for (const saved of ["", "deepseek-chat", "gpt-4o", "totally-unknown"]) {
        const r = resolveModelSelection(providers, DEFAULT_PROVIDER, DEFAULT_MODEL, sel(saved, "medium"));
        expect(allowed.has(r.selection.model)).toBe(true);
      }
    }
  });

  it("思考强度不被历史值带走：非法档位用该模型首档", () => {
    const r = resolveModelSelection(
      PROVIDERS,
      DEFAULT_PROVIDER,
      DEFAULT_MODEL,
      sel("deepseek-reasoner", "ultra")
    );
    expect(r.selection.reasoningEffort).toBe("low");
  });

  it("模型不支持 reasoning 时 effort 为 undefined（不再回落 medium）", () => {
    const r = resolveModelSelection(
      PROVIDERS,
      DEFAULT_PROVIDER,
      DEFAULT_MODEL,
      sel("deepseek-v4-flash", "high")
    );
    expect(r.selection.model).toBe("deepseek-v4-flash");
    expect(r.selection.reasoningEffort).toBeUndefined();
  });

  // ---- add-provider-catalog-abstract：provider 解析 ----

  it("保留历史 provider", () => {
    const r = resolveModelSelection(
      PROVIDERS,
      DEFAULT_PROVIDER,
      DEFAULT_MODEL,
      sel("claude-opus-4-20250514", "medium", "anthropic")
    );
    expect(r.selection.provider).toBe("anthropic");
  });

  it("旧格式（provider 为空）按 model 前缀推断 provider", () => {
    const r = resolveModelSelection(
      PROVIDERS,
      DEFAULT_PROVIDER,
      DEFAULT_MODEL,
      sel("claude-opus-4-20250514", "medium", "")
    );
    expect(r.selection.provider).toBe("anthropic");
  });

  it("前缀无法推断时用 model 实际所属 provider", () => {
    // abab6.5s-chat 前缀不可识别，但它在 minimax 目录下
    const providers: ProviderGroup[] = [
      ...PROVIDERS,
      { id: "minimax", name: "MiniMax", models: [entry("abab6.5s-chat")] },
    ];
    const r = resolveModelSelection(
      providers,
      DEFAULT_PROVIDER,
      DEFAULT_MODEL,
      sel("abab6.5s-chat", undefined, "")
    );
    expect(r.selection.provider).toBe("minimax");
  });

  it("目录为空时 provider 取历史值，历史为空则用服务端默认 provider", () => {
    const withSaved = resolveModelSelection([], "openai", DEFAULT_MODEL, sel("gpt-4o", undefined, "openai"));
    expect(withSaved.selection.provider).toBe("openai");

    const withoutSaved = resolveModelSelection([], "openai", DEFAULT_MODEL, sel("gpt-4o", undefined, ""));
    // 前缀可推断时优先推断（gpt- → openai）
    expect(withoutSaved.selection.provider).toBe("openai");
  });

  it("跨 provider 保留仍有效的 effort", () => {
    const r = resolveModelSelection(
      PROVIDERS,
      DEFAULT_PROVIDER,
      DEFAULT_MODEL,
      sel("claude-opus-4-20250514", "medium", "anthropic")
    );
    expect(r.selection.reasoningEffort).toBe("medium");
  });
});
