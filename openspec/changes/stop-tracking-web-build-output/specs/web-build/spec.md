## ADDED Requirements

### Requirement: 前端构建产物不入库

`agent-web/src/main/resources/static/` SHALL 被版本控制忽略，前端构建产物 SHALL NOT 进入 git 索引。

#### Scenario: 构建后工作树保持干净

- **WHEN** 在干净的工作树上执行一次会触发前端构建的 Maven 生命周期（如 `mvn -pl agent-web verify`）
- **THEN** `git status` 不显示 `agent-web/src/main/resources/static/` 下的任何变更
- **AND** 构建产物确实已被写入磁盘

#### Scenario: 产物仍随构建生成

- **WHEN** 执行不加跳过参数的 `mvn -pl agent-web package`
- **THEN** `static/index.html` 与 `static/assets/` 下的带 hash 资源被重新生成
- **AND** 产出的 jar 内含前端界面

### Requirement: 产物缺失时测试给出准确信号

依赖前端产物的测试 SHALL 在产物不存在时跳过并说明原因，SHALL NOT 以失败形式报告。

#### Scenario: 产物存在时正常断言

- **WHEN** `static/index.html` 存在
- **THEN** SPA 回落用例照常断言 `GET /sessions/some-uuid` 返回 `text/html`

#### Scenario: 产物缺失时跳过

- **WHEN** 跳过前端构建导致 `static/index.html` 不存在
- **THEN** SPA 回落用例被标记为跳过（skipped）
- **AND** 跳过原因说明是前端产物缺失

### Requirement: 前端产物源码归属

由 Vite 从 `frontend/public/` 复制或插件生成的资源 SHALL 在 `frontend/` 内保有唯一源码副本，`static/` 下 SHALL NOT 存在需要人工维护的文件。

#### Scenario: PWA 资源有源码

- **WHEN** 检查 `favicon.svg`、`manifest.webmanifest`、PWA 图标
- **THEN** 它们在 `agent-web/frontend/public/` 下存在源码副本
- **AND** `static/` 下的同名文件全部由构建生成
