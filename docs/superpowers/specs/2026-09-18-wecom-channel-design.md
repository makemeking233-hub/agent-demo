# 企业微信（WeCom）通道 — 设计

> 状态：brainstorming 阶段，**未实施**。spec 经用户 review 后才进 writing-plans。
>
> 关联：brainstorming session 2026-09-18；OpenSpec change 待创建（id 待定）。

## 1. 目标

让 agent-demo 通过**企业微信 bot** 接收用户文本消息、调用 AgentLoop 处理、流式多次推送 markdown 回复。用户在自己微信里直接给 agent 下指令。

非目标：

- 微信公众号 / 个人微信 / 微信小程序
- 群消息（@bot）、语音、图片、文件、位置、链接卡片
- 多 agent 实例共享 session（单机 JSON 持久化）
- 主动发消息给用户（仅被动响应 48h 交互窗口内）

## 2. 关键决策

| # | 决策点 | 选择 | 理由 |
|---|--------|------|------|
| 1 | 平台 | 企业微信自建应用 bot | 个人/小团队场景，免证书鉴权，官方 API 稳定 |
| 2 | 会话模型 | per-WeChat-user 独立 session | 多轮上下文保留，每个微信用户拥有独立 AgentLoop 与历史 |
| 3 | 回复模式 | 流式多次推送 markdown | 贴近 web UI 流式体验；限频每 3s/500 字符；最终 message_stop 强制 flush |
| 4 | 权限模型 | 微信通道默认 `FULL_ACCESS` | 微信场景交互受限，频繁弹按钮卡顿体验；与 add-permission-mode-dropdown 三档独立（仅作用于该通道） |
| 5 | 网络暴露 | 自起 HTTPS | 已知限制：企业微信拒自签证书，dev 环境需 ngrok/cloudflared 或自备公网域名 |
| 6 | 架构 | Spring `@RestController` in agent-web，`wecom.enabled` 开关 | 与现有 chat / session 共享 SessionStore + AgentLoop 工厂，零额外进程 |

## 3. 架构

### 3.1 进程边界

WeChat 模块作为 agent-web 内一组 Spring `@RestController`，与现有 `/api/chat/*`、`/api/session/*` **共进程、共 SessionStore、共 AgentLoop 工厂**。配置开关 `agent.wecom.enabled=true` 才注册（默认 `false`，启动零开销）。

### 3.2 模块位置

新增包：`agent-web/src/main/java/com/example/agent/web/wecom/`

```
wecom/
├── WecomCallbackController   # POST /wecom/callback   GET 验签
├── WecomClient               # 封装企业微信 send/gettoken API
├── WecomCrypto               # 回调消息加解密 (AES-256-CBC + PKCS#7)
├── WecomSessionMapper        # wechatUserId → agent SessionStore sessionId
├── WecomMessageDispatcher    # 入口：解析消息 → 拉/建 session → 启动 AgentLoop
└── WecomReplyPusher          # 出口：订阅 AgentLoop chunks → 限频推送 markdown
```

### 3.3 数据流

```
企业微信 ──POST──► CallbackController
                       │
                       ▼
                  Crypto 验签 + 解密
                       │
                       ▼
             SessionMapper.getOrCreate(userId)
                       │   (按 userId 找/建 web session)
                       ▼
             AgentLoopFactory.buildLoop(...)
                       │   (perms=FULL_ACCESS, model=default)
                       ▼
             loop.processTurn(userMessage)
                       │
                       ▼ chunks (text_delta / message_stop)
                       │
                       ▼
             ReplyPusher.append(chunk)
                       │   (限频 3s/条 or 累计 ≥ 500 字符)
                       ▼
             WecomClient.sendMarkdown(userId, content)
                       │
                       ▼
                  企业微信 ──push──► 用户微信
```

### 3.4 完整时序

**Step 1：回调到达（企业微信 5s 超时窗口）**

```
T=0    WeCom ──POST /wecom/callback──► Controller
T=0+ε  Crypto.verify(msg_signature) → 解密 → 解析 XML
T=10ms  Dispatcher.dispatch(event):
        - 解析 FromUserName=userId, Content="帮我查今天天气"
        - SessionMapper.getOrCreate(userId) → sessionId="wecom:user123"
        - AgentLoopFactory.buildLoop(sid, ...).setPermissionMode(FULL_ACCESS)
        - loop.processTurn(userMsg) 异步订阅
T=20ms  Controller 返回 200 OK("") 给企业微信 ✓
```

**Step 2：后台异步回复流（可任意时长）**

```
loop emits text_delta ──► ReplyPusher.append("今")
loop emits text_delta ──► ReplyPusher.append("今天")
                          ... (累积 / 限频)
T=3s    ReplyPusher.flush() → sendMarkdown(userId, "今天...")
       收到 200 + 消息 ID，缓存

loop emits message_stop ──► ReplyPusher.flush(final content)
                          → sendMarkdown(userId, "...(完整回复)")
T=4s    用户微信收到多条 markdown 消息
```

## 4. 组件细节

### 4.1 WecomCallbackController

```java
@RestController
@RequestMapping("/wecom")
@ConditionalOnProperty(name = "agent.wecom.enabled", havingValue = "true")
class WecomCallbackController {
  @GetMapping("/callback")  // 企业微信验证 URL
  String verify(@RequestParam String msg_signature, String timestamp,
                String nonce, String echostr);

  @PostMapping("/callback") // 接收消息事件
  void onMessage(@RequestBody String body, ...);
}
```

### 4.2 WecomCrypto

企业微信回调消息 **AES-256-CBC + PKCS#7 padding**（`EncodingAESKey` Base64 解码后取前 32 字节作 key）。签名校验用 SHA1(token + timestamp + nonce + encrypted) 比对 `msg_signature`。加解密参考官方 `WXBizMsgCrypt.java`（不直接复用，自己实现 + 测试覆盖）。

### 4.3 WecomClient

```java
class WecomClient {
  // access_token 缓存 7000s（企业微信 7200s TTL，留 200s 余量）
  String getAccessToken();  // 内存 ConcurrentHashMap，过期自动重取

  String sendMarkdown(String userId, String content);
  // POST /cgi-bin/message/send
  // 错误码 45009（API 限频）→ RetryableWecomException，由 ReplyPusher 退避

  String uploadMedia(byte[] data, String filename);
  // 用于图片/文件回复（本 PR 不使用，保留接口）
}
```

外部 HTTP 用 Spring `WebClient`（已在 agent-web 用过），配置连接/读超时。

### 4.4 WecomSessionMapper

```java
// userId → sessionId（1:1，首次消息时建空 session）
Optional<String> findSessionId(String userId);
String getOrCreate(String userId);
// 调 SessionStore.create() 或 load；持久化到 agent data dir 下 wecom/sessions.json
```

### 4.5 WecomMessageDispatcher + ReplyPusher

```java
class WecomMessageDispatcher {
  void dispatch(WecomEvent event);
  // 解析 message_type=text → User message
  // → SessionMapper.getOrCreate
  // → AgentLoopFactory.buildLoop(perms=full_access, model=default)
  // → loop.processTurn 订阅 chunks → ReplyPusher
}

class WecomReplyPusher {
  // 累积窗口：3s 或 ≥ 500 字符；message_stop 强制 flush
  // 限频命中 45009 → 退避 30s 重试
  void append(WecomUserId userId, String chunk);
  void flush(WecomUserId userId);
}
```

## 5. 错误处理矩阵

| 场景 | 处理 | 用户感知 |
|------|------|---------|
| 验签失败 | 返回 401 + 日志 WARN | 用户无感知，企业微信重发 |
| 解密失败（EncodingAESKey 错） | 返回 200 OK（避免重试风暴）+ ERROR 日志 | 用户无感知，看日志 |
| 回调超时（> 5s） | 中间步骤必须 < 100ms；LLM 调用异步 | 企业微信内部重试 |
| `getAccessToken` 失败（网络/错 secret） | 重试 3 次后抛异常 → Dispatcher 记录 ERROR | 用户发"处理失败" |
| `sendMarkdown` 限频（45009） | 退避 30s 合并后续 chunks 再发 | 用户晚一点看到合并消息 |
| AgentLoop 异常（LLM 401/超时） | 推一条 markdown 错误提示 | 用户看到错误 |
| 用户发非 text（图/语音/位置） | 当前版本 reject markdown "暂仅支持文本指令" | 用户看到提示 |
| 同一用户多消息并发 | 串行化：每 userId 锁，新消息进队列 | 顺序不乱 |

## 6. 配置

### 6.1 application-web.yml 新增

```yaml
agent:
  wecom:
    enabled: ${WECOM_ENABLED:false}      # 默认关，需显式开启
    corp-id: ${WECOM_CORP_ID:}
    agent-id: ${WECOM_AGENT_ID:}
    secret: ${WECOM_SECRET:}
    token: ${WECOM_TOKEN:}
    encoding-aes-key: ${WECOM_AES_KEY:} # 43 字符 Base64
    callback-base-url: ${WECOM_CALLBACK_BASE_URL:}
    # 流式推送限频
    reply:
      flush-interval-ms: 3000
      flush-min-chars: 500
      max-chars: 4000
      retry-backoff-ms: 30000

server:
  ssl:
    enabled: ${WECOM_HTTPS_ENABLED:false}
    key-store: ${WECOM_KEYSTORE:}
    key-store-password: ${WECOM_KEYSTORE_PASSWORD:}
```

### 6.2 启动校验

`@ConditionalOnProperty` + `@PostConstruct public void init()`：

- 启用时必填字段非空
- `EncodingAESKey` 长度 = 43
- `callback-base-url` 是 https:// 前缀
- 校验失败抛 `IllegalStateException` 阻止应用启动

## 7. 测试策略

### 7.1 Unit（agent-web src/test/java）

- `WecomCryptoTest`：加密/解密 roundtrip；签名校验正负样例；边界（空消息、超长消息）
- `WecomSessionMapperTest`：userId → sessionId 映射；持久化 roundtrip
- `WecomReplyPusherTest`：限频累积；flush 触发条件；45009 退避

### 7.2 Integration（agent-web src/test/java, `@SpringBootTest`）

- `WecomCallbackControllerTest`：MockMvc + 模拟加密 payload；验签 + 解密 + 派发 + 异步回复
- `WecomClientTest`：用 `WireMock` 模拟企业微信 send API（200/45009/网络错误）

### 7.3 E2E（manual，dev 文档）

- spec 文档追加"本地 dev 步骤"：ngrok 启动 → 拿到 URL → 配企业微信后台 → curl 模拟消息 → 看微信收到回复
- `docs/test-agent-demo/<date>-wecom-channel-*/` 四件套（设计/用例/报告/复盘）

### 7.4 Jacoco

同现有 `LINE≥80% / BRANCH≥70%` 门禁。

## 8. 已知限制 / 风险

1. **自签证书**：企业微信拒收，本地 dev 需 ngrok / cloudflared / 自有公网域名。
2. **限频触发**：45009 时会合并多条消息成一长条；用户可能看到"延迟感"。
3. **群消息不支持**：当前只处理个人消息；@bot 的群消息后续 PR 加。
4. **持久化本地**：wecom SessionMapper 用本地 JSON 持久化，多 agent 实例不共享（YAGNI）。
5. **OpenSpec 路径**：本次走 OpenSpec change（待定 id：`add-wecom-channel`）。OpenSpec 全四阶段：explore（已完成本文档）/ propose（基于本文）/ apply / archive。

## 9. 文件改动预估

```
新增：
  agent-web/src/main/java/com/example/agent/web/wecom/
    ├── WecomCallbackController.java
    ├── WecomClient.java
    ├── WecomCrypto.java
    ├── WecomSessionMapper.java
    ├── WecomMessageDispatcher.java
    └── WecomReplyPusher.java
  agent-web/src/main/resources/application-web.yml  (新增 agent.wecom 块 + server.ssl 块)
  agent-web/src/test/java/com/example/agent/web/wecom/
    ├── WecomCryptoTest.java
    ├── WecomClientTest.java
    ├── WecomSessionMapperTest.java
    ├── WecomReplyPusherTest.java
    └── WecomCallbackControllerTest.java
  openspec/changes/add-wecom-channel/
    ├── proposal.md
    ├── design.md   (本文件副本)
    ├── tasks.md
    └── specs/wecom-channel/spec.md

不动：
  - 现有 plugin-system（已确认无"消息通道"扩展点，本 PR 不扩展）
  - SessionStore（复用现有 API）
  - AgentLoopFactory（复用现有 API）
```

预计代码量 ~600-800 行 Java（不含测试），约 30-40 个 task，单 PR 周期 1-2 周。

## 10. 后续 PR 路线（不在本 PR）

1. PR2：群消息支持（@bot 触发）
2. PR3：图片/语音/文件输入（uploadMedia 接口已留）
3. PR4：plugin-system 增加 `ChannelProvider` 扩展点 + PluginContext 暴露 AgentLoop/SessionStore，迁移 wecom 到 plugin
4. PR5：Telegram / Slack / 飞书 通道（PR4 之后成本大降）
