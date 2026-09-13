## 1. 配置与版本控制

- [x] 1.1 `.gitignore` 新增 `agent-web/src/main/resources/static/`（并入既有 vosk 条目处统一说明）
- [x] 1.2 `git rm -r --cached agent-web/src/main/resources/static/`（保留工作区文件，16 个文件出索引）
- [x] 1.3 确认该目录转为被忽略：`git check-ignore -v` 命中 `.gitignore:47`

## 2. 测试适配

- [x] 2.1 `WebIntegrationTest.spaFallbackServesIndexHtml` 增加 `assumeTrue(frontendBuilt(), ...)` 前置假设
- [x] 2.2 产物存在时正常断言：`WebIntegrationTest` 14 用例 0 失败 0 跳过（`spaFallbackServesIndexHtml` 走真断言）
- [x] 2.3 产物缺失时跳过：临时移除 `index.html` 后 `clean test` 得到 `Skipped: 1` 而非失败

## 3. 文档

- [x] 3.1 `README.md` §11 说明前端产物不入库、由 `generate-resources` 生成、`-DskipNpm` 产出不含界面
- [x] 3.2 `docs/design/design.md` §13.2 补同一说明与陈旧快照的实例

## 4. 收尾

- [x] 4.1 `mvn -o -pl agent-core,agent-web verify` 通过 + `npx vitest run` 全绿
- [x] 4.2 核心验收：一次前端构建写出全新 hash 文件（`index-DB_xNXs_.js` 等）后，`git status` 不含任何 `static/` 下的 dirty 条目
- [x] 4.3 `openspec validate stop-tracking-web-build-output --strict` 通过 + archive + commit + push

## 5. 验收证据

| 项 | 证据 |
|----|------|
| 产物不再入库 | `git check-ignore -v agent-web/src/main/resources/static/index.html` → `.gitignore:47:agent-web/src/main/resources/static/` |
| 构建不再弄脏工作树 | `npm run build` 产出 `index-DB_xNXs_.js` / `index-4Us534Of.css`（与旧 `index-D-C-eWU6.js` 完全不同名）后，`git status --short` 无 `static/` 条目 |
| 旧快照确已陈旧 | 新 bundle 含 `cache_hit_rate` / `tok/s`（add-session-stats-bar 的 StatsBar），旧入库 bundle 用 `git show` 取出后 grep 无匹配 |
| 有产物时真断言 | `mvn -o -pl agent-web test -DskipNpm=true -Dtest=WebIntegrationTest` → `Tests run: 14, Failures: 0, Errors: 0, Skipped: 0` |
| 无产物时跳过 | 移除 `index.html` 后 `mvn -o -pl agent-web clean test -DskipNpm=true -Dtest=WebIntegrationTest#spaFallbackServesIndexHtml` → `Skipped: 1` |

## 6. 操作注意（非本 change 引入，但会撞上）

`mvn verify` 不加 `-DskipNpm` 时会先跑 `npm ci`，而 `npm ci` 会**清空 `node_modules` 后重装**。若此时本机有 `npm run dev`（Vite dev server）在跑，它持有的 `node_modules/@esbuild/win32-x64/esbuild.exe` 被 Windows 锁定，`npm ci` 会以 `EPERM ... unlink` 失败（exit -4048），并且**失败前已经清空了 `node_modules`**，需要 `npm install` 才能恢复。

规避：跑带前端构建的 Maven 生命周期前先停掉 dev server。此为既有行为（`npm ci` 一直在），本次未改。
