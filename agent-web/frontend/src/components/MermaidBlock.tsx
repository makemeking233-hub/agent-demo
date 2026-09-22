import { useEffect, useRef, useState } from "react";

type RenderState =
  | { kind: "pending" }
  | { kind: "ready"; svg: string }
  | { kind: "failed"; message: string };

/** mermaid 只需初始化一次；重复 initialize 会覆盖全局配置。 */
let initialized = false;
/** mermaid.render 要求调用方给唯一 id，用它生成。 */
let seq = 0;

/**
 * mermaid 图渲染块（add-mermaid-diagrams）。
 *
 * <p>设计要点（见 change 的 design.md）：
 *
 * <ul>
 *   <li>**运行时全懒加载**：`await import("mermaid")` 只在真的出现图时才发生，页面没有图时零请求；
 *   <li>**恒定深色 + strict 安全级别**：与对话区既有 `github-dark` 代码块观感一致；不关闭 htmlLabels，
 *       否则标签里的换行标记会退化成字面文本（项目图示规范恰恰鼓励用它换行）；
 *   <li>**同一段源码只渲染一次**：流式后续增量会带着相同 source 重渲染本组件，没有这道去重就会把
 *       同一张图反复重画；
 *   <li>**失败兜底**：显示原始源码 + 一行提示，既不白屏也不让用户失去判断依据。
 * </ul>
 *
 * <p>三种状态共用一层带 `data-mermaid-block` 的外壳：既给了统一的样式落点，也让上层测试能同步、
 * 稳定地判定"这个围栏已被图组件接管"——不必去猜异步渲染此刻走到了哪一步。
 *
 * <p>注意：mermaid 在 jsdom 中无法真实渲染（依赖 `getBBox` 等未实现的 SVG 测量 API），
 * 单测只能 mock 它并断言调用契约；"真能出图"必须另在浏览器验证。
 */
export function MermaidBlock(props: { source: string }) {
  const { source } = props;
  const [state, setState] = useState<RenderState>({ kind: "pending" });
  const renderedSource = useRef<string | null>(null);

  useEffect(() => {
    if (renderedSource.current === source) return;
    let cancelled = false;

    void (async () => {
      try {
        const mermaid = (await import("mermaid")).default;
        if (!initialized) {
          mermaid.initialize({
            startOnLoad: false,
            securityLevel: "strict",
            theme: "dark",
          });
          initialized = true;
        }
        const { svg } = await mermaid.render(`mermaid-block-${++seq}`, source);
        if (cancelled) return;
        renderedSource.current = source;
        setState({ kind: "ready", svg });
      } catch (e) {
        if (cancelled) return;
        setState({
          kind: "failed",
          message: e instanceof Error ? e.message : String(e),
        });
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [source]);

  return (
    <div className="my-2" data-mermaid-block="true">
      {state.kind === "ready" ? (
        <div
          className="mx-auto block max-w-full overflow-x-auto text-center [&_svg]:max-w-full [&_svg]:h-auto"
          role="img"
          aria-label="mermaid 图"
          // strict 模式下 mermaid 已用 DOMPurify 消毒过标签，这里插入的是它自己生成的 SVG
          dangerouslySetInnerHTML={{ __html: state.svg }}
        />
      ) : state.kind === "failed" ? (
        <>
          <p className="mb-1 text-[13px] text-muted-foreground">mermaid 图渲染失败：{state.message}</p>
          <pre>
            <code>{source}</code>
          </pre>
        </>
      ) : (
        // 未就绪：先按源码显示（样式由 .markdown pre 提供），既不白屏也不闪空
        <pre>
          <code>{source}</code>
        </pre>
      )}
    </div>
  );
}
