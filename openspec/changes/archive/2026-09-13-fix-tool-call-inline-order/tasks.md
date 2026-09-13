## 1. 时间线排序（前端）

- [x] 1.1 抽出模块级纯函数 `appendTextToTimeline` / `appendThinkingToTimeline` / `appendToolToTimeline`（便于单测）
- [x] 1.2 文本/思考判据：仅当**最后一条** assistant 文本项尚未挂工具时合并，否则新建
- [x] 1.3 工具判据：仅当最后一条 assisting 文本项为空或已有工具**全部仍在 running**（同一批公告）时追加，否则新建
- [x] 1.4 组件内三个函数改为薄包装调用纯函数

## 2. 历史重建去重

- [x] 2.1 `mapHistoryToItems` 按 `toolCallId` 把 `role: "tool"` 结果回填到对应 assistant item 的内联工具
- [x] 2.2 匹配不到时（孤儿结果）才退化为独立卡
- [x] 2.3 导出 `mapHistoryToItems` 与 `Item` 供测试

## 3. 测试

- [x] 3.1 `ChatPanel.timeline.test.ts` 新增 10 例：先工具后文本 / 文本后工具 / 三次迭代交错 / 同批公告合并 / 上一批有结果则另起 / 思考与文本同 item / 思考不串到已挂工具的 item / 历史合并成一张卡 / 失败标记 / 孤儿结果保留
- [x] 3.2 `npx vitest run` 全绿

## 4. 收尾

- [x] 4.1 文档：`README.md` §10 补工具卡内联说明
- [x] 4.2 `npx tsc --noEmit` 错误数与改动前一致（27，均为既有）
- [x] 4.3 `openspec validate fix-tool-call-inline-order --strict` + archive + commit + push

## 5. 验收证据

| 项 | 证据 |
|----|------|
| 先工具后文本 | `[a(帮我看看)[], a()[c1,c2], a(我看完了。)[]]` —— 工具卡在最终文本之前 |
| 每次迭代文本后工具 | `[a(帮我看看)[], a(我先读文件。)[c1]]` —— 工具紧随本迭代文本 |
| 三次迭代交错 | `[a(帮我看看)[], a()[c1], a(再看看这个。)[c2], a(结论如下。)[]]` |
| 同批公告合并 | `[a(帮我看看)[], a(同时查两个。)[c1,c2]]` |
| 上一批有结果则另起 | `[a(帮我看看)[], a()[c1], a()[c2]]` |
| 历史不重复渲染 | 带 `toolCalls` 的助手消息 + 随后的 `role:"tool"` 结果 → 仅 2 条 item，1 张卡，名称为真实 `ReadFile`，结果回填其上 |
| 孤儿结果不丢 | `[a(hi)[], standalone:gone]` |
| 全量回归 | 前端 130 用例全绿（原 107，新增 23）；tsc 错误数不变 27 |
