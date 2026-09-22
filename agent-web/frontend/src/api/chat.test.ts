import { afterEach, describe, expect, it } from "vitest";
import {
  MODEL_SELECTION_KEY,
  inferProvider,
  readModelSelection,
  writeModelSelection,
  type ModelSelection,
} from "./chat";

/**
 * chat.ts 类型工具（add-provider-catalog-abstract task 8.4 + 8.5）。
 *
 * <p>覆盖：localStorage 读写 roundtrip / 旧格式（无 provider）兼容 / 损坏 JSON 容错 /
 * model 前缀推断 provider。
 */
describe("readModelSelection / writeModelSelection", () => {
  afterEach(() => localStorage.clear());

  it("写入后读回完整 ModelSelection", () => {
    const sel: ModelSelection = {
      provider: "deepseek",
      model: "deepseek-reasoner",
      reasoningEffort: "high",
    };
    writeModelSelection(sel);
    expect(readModelSelection()).toEqual(sel);
  });

  it("无记录时返回 null", () => {
    expect(readModelSelection()).toBeNull();
  });

  it("旧格式（无 provider）→ provider 为空串，其余字段保留", () => {
    localStorage.setItem(
      MODEL_SELECTION_KEY,
      JSON.stringify({ model: "deepseek-chat", reasoningEffort: "medium" }),
    );
    expect(readModelSelection()).toEqual({
      provider: "",
      model: "deepseek-chat",
      reasoningEffort: "medium",
    });
  });

  it("旧格式无 reasoningEffort → reasoningEffort 为 undefined", () => {
    localStorage.setItem(MODEL_SELECTION_KEY, JSON.stringify({ model: "deepseek-chat" }));
    const got = readModelSelection();
    expect(got?.model).toBe("deepseek-chat");
    expect(got?.reasoningEffort).toBeUndefined();
  });

  it("损坏 JSON → 返回 null（不抛错）", () => {
    localStorage.setItem(MODEL_SELECTION_KEY, "{not-json");
    expect(readModelSelection()).toBeNull();
  });

  it("缺 model 字段 → 返回 null", () => {
    localStorage.setItem(MODEL_SELECTION_KEY, JSON.stringify({ provider: "deepseek" }));
    expect(readModelSelection()).toBeNull();
  });

  it("model 为空串 → 返回 null", () => {
    localStorage.setItem(MODEL_SELECTION_KEY, JSON.stringify({ model: "" }));
    expect(readModelSelection()).toBeNull();
  });

  it("provider 为非字符串时回落空串", () => {
    localStorage.setItem(
      MODEL_SELECTION_KEY,
      JSON.stringify({ provider: 123, model: "deepseek-chat" }),
    );
    expect(readModelSelection()?.provider).toBe("");
  });
});

describe("inferProvider（对齐后端 ProviderInference）", () => {
  it("o1 / o3 / o4 / gpt- → openai", () => {
    expect(inferProvider("o1-preview")).toBe("openai");
    expect(inferProvider("o3")).toBe("openai");
    expect(inferProvider("o4-mini")).toBe("openai");
    expect(inferProvider("gpt-4o")).toBe("openai");
  });

  it("claude- → anthropic", () => {
    expect(inferProvider("claude-opus-4-20250514")).toBe("anthropic");
  });

  it("deepseek- → deepseek", () => {
    expect(inferProvider("deepseek-chat")).toBe("deepseek");
    expect(inferProvider("deepseek-reasoner")).toBe("deepseek");
  });

  it("大小写不敏感", () => {
    expect(inferProvider("DeepSeek-Chat")).toBe("deepseek");
    expect(inferProvider("GPT-4o")).toBe("openai");
  });

  it("未知前缀 / null / 空串 → null", () => {
    expect(inferProvider("abab6.5s-chat")).toBeNull();
    expect(inferProvider("gemini-pro")).toBeNull();
    expect(inferProvider(null)).toBeNull();
    expect(inferProvider("")).toBeNull();
  });
});
