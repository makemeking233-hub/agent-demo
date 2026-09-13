/// <reference types="vite/client" />
/// <reference types="vite-plugin-pwa/client" />

/*
 * 本文件补上 Vite 与 vite-plugin-pwa 的 ambient 类型声明（add-rich-markdown-rendering）。
 *
 * 项目此前缺少它，导致两类**假报错**长期占用 tsc 基线（共 17 条）：
 *   - 每个 `import styles from "./X.module.css"` 都报 TS2307（16 条）
 *   - `import.meta.env` 报 TS2339（1 条）
 *
 * 声明前后 tsc 基线：28 -> 11。
 */
