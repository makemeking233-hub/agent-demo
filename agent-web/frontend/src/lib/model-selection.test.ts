import { describe, expect, it } from "vitest";
import type { ModelEntry } from "../api/chat";
import { resolveModelSelection } from "./model-selection";

function entry(id: string, efforts: string[] = []): ModelEntry {
  return { id, name: id, supportsReasoning: efforts.length > 0, reasoningEfforts: efforts };
}

/** 后端实际下发的目录（deepseek-chat 已被上游停用，不在此列） */
const CATALOG: ModelEntry[] = [
  entry("deepseek-v4-flash"),
  entry("deepseek-reasoner", ["low", "medium", "high"]),
  entry("deepseek-v4-pro", ["low", "medium", "high"]),
];

const DEFAULT_MODEL = "deepseek-v4-flash";

describe("resolveModelSelection", () => {
  it("保留仍然合法的历史选择", () => {
    const r = resolveModelSelection(CATALOG, DEFAULT_MODEL, "deepseek-reasoner", "high");
    expect(r.model).toBe("deepseek-reasoner");
    expect(r.effort).toBe("high");
  });

  it("历史选择已下线时回落到服务端默认值", () => {
    // 核心回归：deepseek-chat 曾是硬编码兜底值，也是导致非法 id 被透传的起点
    const r = resolveModelSelection(CATALOG, DEFAULT_MODEL, "deepseek-chat", "medium");
    expect(r.model).toBe(DEFAULT_MODEL);
    expect(r.entry?.id).toBe(DEFAULT_MODEL);
  });

  it("没有历史选择时使用服务端默认值", () => {
    const r = resolveModelSelection(CATALOG, DEFAULT_MODEL, "", "medium");
    expect(r.model).toBe(DEFAULT_MODEL);
  });

  it("旧版后端不返回 defaultModel 时退到目录首项", () => {
    const r = resolveModelSelection(CATALOG, undefined, "", "medium");
    expect(r.model).toBe("deepseek-v4-flash");
  });

  it("解析结果永远落在目录内", () => {
    const stale = ["", "deepseek-chat", "gpt-4o", "totally-unknown"];
    for (const saved of stale) {
      const r = resolveModelSelection(CATALOG, DEFAULT_MODEL, saved, "medium");
      expect(CATALOG.map((m) => m.id)).toContain(r.model);
    }
  });

  it("服务端默认值不在目录中时退到目录首项", () => {
    // 服务端启动校验会挡住这种配置，但陈旧缓存响应仍可能造成两者不一致
    const r = resolveModelSelection(CATALOG, "deepseek-chat", "", "medium");
    expect(r.model).toBe("deepseek-v4-flash");
  });

  it("目录为空时沿用服务端声明的默认值", () => {
    // 目录为空说明服务端没配任何模型；此时唯一可信的是它自己声明的默认值
    const r = resolveModelSelection([], DEFAULT_MODEL, "deepseek-chat", "high");
    expect(r.model).toBe(DEFAULT_MODEL);
    expect(r.entry).toBeNull();
  });

  it("目录为空且服务端未声明默认值时返回空串", () => {
    const r = resolveModelSelection([], undefined, "deepseek-chat", "high");
    expect(r.model).toBe("");
    expect(r.entry).toBeNull();
  });

  it("返回的 id 只可能来自目录、服务端默认值或空串", () => {
    // 这正是原始缺陷的要害：旧实现硬编码 deepseek-chat 兜底，
    // 即便服务端既没列出它、也没声明它是默认值，前端照样会把它发出去。
    // 注：目录为空且服务端自己声明 deepseek-chat 为默认值时原样镜像服务端声明是**有意的**
    // （该状态被服务端启动校验排除，健康后端不会出现），故此处只锁「不许凭空发明 id」。
    const allowed = new Set([...CATALOG.map((m) => m.id), DEFAULT_MODEL, ""]);
    for (const models of [CATALOG, []]) {
      for (const saved of ["", "deepseek-chat", "gpt-4o", "totally-unknown"]) {
        const r = resolveModelSelection(models, DEFAULT_MODEL, saved, "medium");
        expect(allowed.has(r.model)).toBe(true);
      }
    }
  });

  it("思考强度不被历史值带走：不支持时用该模型首档", () => {
    const r = resolveModelSelection(CATALOG, DEFAULT_MODEL, "deepseek-reasoner", "ultra");
    expect(r.effort).toBe("low");
  });

  it("思考强度在模型不支持 reasoning 时回落 medium", () => {
    const r = resolveModelSelection(CATALOG, DEFAULT_MODEL, "deepseek-v4-flash", "high");
    expect(r.model).toBe("deepseek-v4-flash");
    expect(r.effort).toBe("medium");
  });
});
