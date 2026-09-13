import { describe, expect, it } from "vitest";
import {
  appendTextToTimeline,
  appendThinkingToTimeline,
  appendToolToTimeline,
  mapHistoryToItems,
  type Item,
} from "./ChatPanel";
import type { HistoryMessage } from "../api/chat";

/**
 * 工具调用内联排布（fix-tool-call-inline-order）。
 *
 * <p>这里直接测模块级纯函数——它们是 `ChatPanel` 里三条追加路径的真实实现，
 * 而组件本身难以在不驱动 SSE 的前提下测到这些时序。
 */

const userItem: Item = { kind: "text", id: "u1", role: "user", text: "帮我看看" };

function tool(id: string, status: "running" | "ok" = "running") {
  return { id, name: "ReadFile", status } as const;
}

/** 把 items 压成便于断言的形状：a(文本)[工具id,...]；独立工具项 standalone:id。 */
function shape(items: Item[]): string[] {
  return items.map((it) => {
    if (it.kind === "text") {
      const tools = (it.tools ?? []).map((t) => t.id).join(",");
      return `a(${it.text})[${tools}]`;
    }
    if (it.kind === "tool") return "standalone:" + it.toolCallId;
    return "perm";
  });
}

describe("工具调用按调用顺序内联排布", () => {
  it("先工具后文本：工具卡排在最终文本之前", () => {
    let items: Item[] = [userItem];
    items = appendToolToTimeline(items, tool("c1"), "i1");
    items = appendToolToTimeline(items, tool("c2"), "i2");
    // 同一次迭代的两个工具都还在 running → 落在同一条 item
    items = appendTextToTimeline(items, "我看完了。", "i3");

    expect(shape(items)).toEqual([
      "a(帮我看看)[]",
      "a()[c1,c2]",
      "a(我看完了。)[]",
    ]);
  });

  it("每次迭代「文本后工具」：工具卡紧随该迭代文本", () => {
    let items: Item[] = [userItem];
    items = appendTextToTimeline(items, "我先读文件。", "i1");
    items = appendToolToTimeline(items, tool("c1"), "i2");

    expect(shape(items)).toEqual(["a(帮我看看)[]", "a(我先读文件。)[c1]"]);
  });

  it("多次迭代交错：工具 → 文本+工具 → 文本", () => {
    let items: Item[] = [userItem];
    // 迭代1：只有工具（结果已回）
    items = appendToolToTimeline(items, tool("c1", "ok"), "i1");
    // 迭代2：文本 + 工具
    items = appendTextToTimeline(items, "再看看这个。", "i2");
    items = appendToolToTimeline(items, tool("c2", "running"), "i3");
    // 迭代3：最终文本
    items = appendTextToTimeline(items, "结论如下。", "i4");

    expect(shape(items)).toEqual([
      "a(帮我看看)[]",
      "a()[c1]",
      "a(再看看这个。)[c2]",
      "a(结论如下。)[]",
    ]);
  });

  it("同一批公告的工具合并到同一条 item", () => {
    let items: Item[] = [userItem];
    items = appendTextToTimeline(items, "同时查两个。", "i1");
    items = appendToolToTimeline(items, tool("c1"), "i2");
    items = appendToolToTimeline(items, tool("c2"), "i3");

    // 两个都还 running → 同一批公告，合并到同一条
    expect(shape(items)).toEqual(["a(帮我看看)[]", "a(同时查两个。)[c1,c2]"]);
  });

  it("上一批已有结果时，新一批工具另起一条 item", () => {
    let items: Item[] = [userItem];
    // 上一迭代的工具结果已回（status=ok）
    items = appendToolToTimeline(items, tool("c1", "ok"), "i1");
    // 下一迭代公告新工具 → 必须另起，否则上一迭代的文本会被挤到工具卡之后
    items = appendToolToTimeline(items, tool("c2", "running"), "i2");

    expect(shape(items)).toEqual(["a(帮我看看)[]", "a()[c1]", "a()[c2]"]);
  });

  it("思考与文本同属一次迭代，落在同一条 item", () => {
    let items: Item[] = [userItem];
    items = appendThinkingToTimeline(items, "先想想……", "i1");
    items = appendTextToTimeline(items, "好了。", "i2");

    expect(items).toHaveLength(2);
    const assistant = items[1] as Extract<Item, { kind: "text" }>;
    expect(assistant.thinking).toBe("先想想……");
    expect(assistant.text).toBe("好了。");
  });

  it("思考不会串到上一迭代已挂工具的 item 上", () => {
    let items: Item[] = [userItem];
    items = appendToolToTimeline(items, tool("c1", "ok"), "i1");
    items = appendThinkingToTimeline(items, "第二轮思考", "i2");

    expect(shape(items)).toEqual(["a(帮我看看)[]", "a()[c1]", "a()[]"]);
    const second = items[2] as Extract<Item, { kind: "text" }>;
    expect(second.thinking).toBe("第二轮思考");
  });
});

describe("历史重建不重复渲染工具调用", () => {
  it("assistant 的 toolCalls 与随后的 tool 结果合并成一张卡", () => {
    const history: HistoryMessage[] = [
      { role: "user", content: "读一下" },
      {
        role: "assistant",
        content: "",
        toolCalls: [{ id: "c1", name: "ReadFile", argumentsJson: '{"path":"a.txt"}' }],
      },
      { role: "tool", content: "文件内容", toolCallId: "c1", isError: false },
    ];

    const items = mapHistoryToItems(history);

    // 只有 user + assistant 两条，没有额外的独立工具项
    expect(items).toHaveLength(2);
    const assistant = items[1] as Extract<Item, { kind: "text" }>;
    expect(assistant.tools).toHaveLength(1);
    // 真实工具名（而不是字面量 "tool"）
    expect(assistant.tools![0].name).toBe("ReadFile");
    // 结果回填到同一张卡上
    expect(assistant.tools![0].text).toBe("文件内容");
    expect(assistant.tools![0].status).toBe("ok");
  });

  it("失败结果标记为 fail", () => {
    const items = mapHistoryToItems([
      { role: "assistant", content: "", toolCalls: [{ id: "c1", name: "Bash" }] },
      { role: "tool", content: "boom", toolCallId: "c1", isError: true },
    ]);

    const assistant = items[0] as Extract<Item, { kind: "text" }>;
    expect(assistant.tools![0].status).toBe("fail");
  });

  it("孤儿工具结果仍以独立卡渲染，不丢失", () => {
    const items = mapHistoryToItems([
      { role: "user", content: "hi" },
      { role: "tool", content: "孤立结果", toolCallId: "gone", isError: false },
    ]);

    expect(shape(items)).toEqual(["a(hi)[]", "standalone:gone"]);
  });
});
