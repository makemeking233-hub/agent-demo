## MODIFIED Requirements

### Requirement: 运行时缓存策略

Service Worker SHALL 对以下路径应用对应缓存策略：

| URL 模式 | Handler | 缓存名 | 备注 |
|---|---|---|---|
| `/assets/*` | CacheFirst | `static-assets-v1` | 静态资源永久缓存 |
| `/index.html` 或 `/` | NetworkFirst（3s 超时） | `html-v1` | 保证拿到新版本 |
| `/api/**` | NetworkOnly | — | 永不缓存（避免 stale token / 路径） |
| `https://fonts.*` | CacheFirst | `google-fonts-v1` | Google Fonts 缓存 |

此外，预缓存清单 SHALL 采用**白名单**（`globPatterns`）而非默认的通配 glob，只收录"必须离线可用"的顶层资源。原因是按需库会产出大量命名各异的 chunk（mermaid 12 一次构建产出 63 个，名字含 `chunk` / `diagram` / `elk` / `dagre` / `cytoscape.esm` / `*Diagram` 等，**没有共同前缀**），黑名单模式既长又脆；白名单则让安装体积有上界，与引了多少按需库无关。未被收录的资源仍走 `/assets/*` 的 CacheFirst 运行时缓存，只是不参与"安装即全量下载"。**新增需要离线可用的顶层资源时必须显式加入白名单。**

#### Scenario: 静态资源 CacheFirst

- **WHEN** 用户首次访问 `/assets/index-abc.js`
- **THEN** SW 从网络拉取并缓存到 `static-assets-v1`
- **AND** 后续访问直接返回缓存（无网络）

#### Scenario: HTML NetworkFirst

- **WHEN** 用户访问 `/index.html` 且在线
- **THEN** SW 从网络拉取最新版本
- **AND** 网络失败（>3s 超时）时回退到 `html-v1` 缓存

#### Scenario: API 永不缓存

- **WHEN** 用户调 `/api/chat/send`
- **THEN** SW 不拦截，直接走网络
- **AND** 响应不被任何 Cache Storage 缓存

#### Scenario: 按需 chunk 不进预缓存

- **WHEN** 构建产出 mermaid 的图解 chunk
- **THEN** 生成的 `sw.js` 预缓存清单中不包含这些文件
- **AND** 预缓存条目数与下载总量保持在引入 mermaid 之前的量级

#### Scenario: 首次用到时才取并按运行时规则缓存

- **WHEN** 用户首次打开含 mermaid 围栏的会话
- **THEN** SW 放行该 chunk 的网络请求，并按 `/assets/*` 的 CacheFirst 规则缓存它
- **AND** 之后渲染同图型时命中缓存，不再走网络
