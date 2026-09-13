## Why

`agent-web` 把 Vite 的前端构建产物（`agent-web/src/main/resources/static/`）提交进了 git，但这份快照自 `bff39ad`（add-pwa-support）之后再没更新过。后果有两个：

- **入库前端陈旧**：`static/assets/index-D-C-eWU6.js` 里搜不到此后任何前端改动的痕迹（thinking 折叠、工作区选择器、会话统计条都不在其中）。用 `-DskipNpm` 打包或直接跑已有 jar 时，用户看到的是旧界面。
- **每次构建都弄脏工作树**：Vite 配置 `outDir: ../src/main/resources/static` 且 `emptyOutDir: true`，文件名带内容 hash，因此每次 `npm run build` 都会产生一批 `D`/`??` 的构建产物。这已实际干扰了开发（在 git worktree 里跑一次 `mvn verify` 后 worktree 就变成 dirty，无法直接 `git worktree remove`）。

`.gitignore` 里其实已有同样的先例：`static/vosk-model/` 与 `static/vosk-browser/` 早就按「构建时拷入 static/，勿重复提交」被忽略。本次把该原则贯彻到整个 `static/`。

## What Changes

- `.gitignore` 新增 `agent-web/src/main/resources/static/`。
- `git rm -r --cached agent-web/src/main/resources/static/`，构建产物不再入库。
- `WebIntegrationTest.spaFallbackServesIndexHtml` 加前置假设：前端产物不存在时跳过（而非报错），避免「跳过前端构建」的运行方式产生误导性的红。
- 文档补充：前端产物由 Maven `generate-resources` 阶段构建生成，不入库；产物缺失时用 `mvn package`（不加 `-DskipNpm`）重建。

无破坏性变更：正常的 `mvn test` / `verify` / `package` 生命周期本就会在 `test` 之前跑 `npm ci` + `npm run build`，产物照样存在；改动只是不再把产物纳入版本控制。

## Capabilities

### New Capabilities

- `web-build`：规定前端构建产物的归属与生命周期。

### Modified Capabilities

无。

## Impact

- **配置**：`.gitignore`
- **版本控制**：从索引移除 16 个构建产物文件（工作区文件保留，被忽略）
- **测试**：`WebIntegrationTest`（1 处加跳过假设）
- **文档**：`README.md`、`docs/design/design.md`
- **风险**：首次 clone 后若只跑 `mvn -DskipNpm ... test`，`static/` 为空，SPA 回落用例会被跳过而不是验证通过——需在文档里写明正常构建路径
