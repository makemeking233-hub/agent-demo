## Context

`agent-web/frontend/vite.config.ts` 把构建产物直接输出到后端资源目录：

```ts
build: {
  outDir: '../src/main/resources/static',
  emptyOutDir: true,
  sourcemap: true,
}
```

`agent-web/pom.xml` 的 `frontend-maven-plugin` 有三个 execution，均未声明 `<phase>`，因此落在插件默认阶段 `generate-resources`——**早于 `compile` 与 `test`**：

| execution | 目标 | 作用 |
|-----------|------|------|
| `install node and npm` | `install-node-and-npm` | 装 Node v20.11.0 + npm 10.2.4 |
| `npm ci` | `npm` | 按 lockfile 重装依赖 |
| `npm run build` | `npm` | 触发 Vite 构建，写入 `static/` |

于是「产物是否入库」与「测试能否通过」是两件事：产物由 Maven 生命周期即时生成，入库的那份只是某次构建的快照。

## Goals / Non-Goals

**Goals：**

- 构建产物不进版本控制，构建不再弄脏工作树。
- 正常 `mvn test` / `verify` / `package` 行为不变（产物照样生成、SPA 回落照样可用）。
- 产物缺失时测试给出准确信号，不产生误导性的红。

**Non-Goals：**

- 不改 Vite 的 `outDir`（产物仍需落在 Spring Boot 资源目录以便打包）。
- 不改前端构建流程与依赖管理方式。
- 不动 `.gitignore` 里已存在的 `static/vosk-model/`、`static/vosk-browser/` 条目（本次只是把同一原则扩展到整个目录）。

## Decisions

### D1：忽略整个 `static/` 而非只忽略 hash 命名的 `assets/`

**理由**：`emptyOutDir: true` 意味着这个目录**完全由构建拥有**——`index.html`、`sw.js`、`registerSW.js`、`workbox-*.js`、`favicon.svg`、`manifest.webmanifest`、PWA 图标全部由 Vite 从 `frontend/public/` 与插件配置生成。源码都在 `frontend/`（`public/` 下的 `favicon.svg`、`manifest.webmanifest`、PWA 图标、`vosk-model/model.tar.gz` 均在版本控制内），所以整个 `static/` 可以安全忽略。

**考虑过**：只忽略 `static/assets/`（hash 命名，必然每次变化），保留 `index.html` 等。否决：`sw.js` 与 `workbox-*.js` 的 hash 名同样会变，`index.html` 内嵌的资源引用也会变，逐个打补丁既脆弱又与既有 `vosk-model/` 条目的思路不一致。

### D2：用 `Assumptions.assumeTrue` 而非删除或放宽 SPA 回落用例

**理由**：`WebIntegrationTest.spaFallbackServesIndexHtml` 断言 `GET /sessions/some-uuid` 返回 `text/html`，这需要 `static/index.html` 存在。正常情况下 `generate-resources` 已生成它，用例走完整断言；只有在显式跳过前端构建（`-DskipNpm`）或首次 clone 未构建时才会缺产物。

用 `assumeTrue(Files.exists(static/index.html))` 表达「本前置未满足则本用例不适用」，而不是让它失败——否则会产生「代码坏了」的错误信号。**不采用**删除用例：SPA 回落是真实契约，正常构建路径下必须继续被验证。

**考虑过**：把产物目录改为 `target/` 下再由 resource 插件拷入。否决：会改变打包链路与既有 e2e 测试（Selenium 用真实 HTTP 服务，依赖产物位于 classpath 资源中），收益不足以承担风险。

### D3：工作区文件保留，仅从索引移除

**理由**：`git rm --cached` 让本地已有的产物继续留在磁盘上，避免「提交后立刻要重建才能跑测试」。之后 `static/` 作为被忽略目录存在，构建可自由覆盖。

## Risks / Trade-offs

### R1：首次 clone 后跳过前端构建会得到无 UI 的 jar

[Accepted] 正常的 `mvn package` 会构建前端。文档需写明：`-DskipNpm` 只适用于纯后端迭代，产出的 jar 不含界面。

### R2：`npm ci` 依赖网络与 lockfile

[Accepted] 既有行为，本次不改。离线环境下 `npm ci` 需要预热过的 npm 缓存。

### R3：SPA 回落用例在无产物时被跳过，存在「悄悄不验证」的风险

[Mitigation] 跳过原因写在 `assumeTrue` 的消息里，`mvn test` 输出会显示 skip 而非 pass；`test-guide.md` 与本 change 的 tasks 里记录该口径。

## Migration Plan

1. `.gitignore` 加条目。
2. `git rm -r --cached agent-web/src/main/resources/static/`。
3. 加测试前置假设。
4. 补文档。
5. 验证：跑一次 `mvn -pl agent-web verify`（触发前端构建）后 `git status` 必须干净——这是本次的核心验收点；同时确认 `static/` 被重新生成且 `WebIntegrationTest` 通过。

回滚：`git revert` 该提交即可恢复入库状态。

## Open Questions

无。
