## 1. 配置与启动校验

- [x] 1.1 新建 `WecomConfigProperties`（`@ConfigurationProperties(prefix="agent.wecom")`），含 corp-id / agent-id / secret / token / encoding-aes-key / callback-base-url / reply.* 嵌套字段
- [x] 1.2 在 `application-web.yml` 追加 `agent.wecom.*` 配置块（默认 `enabled: false`，env 占位 `${WECOM_*:}`）与 `server.ssl.*` 占位
- [x] 1.3 新建 `WecomConfigValidator`：`@PostConstruct public void init()` 校验 enabled=true 时必填字段非空、EncodingAESKey 长度=43、callback-base-url 是 `https://` 前缀，校验失败抛 `IllegalStateException`

## 2. 加密模块

- [x] 2.1 新建 `WecomCrypto`：AES-256-CBC + PKCS#7 padding 加解密；SHA1 签名校验；`verifySignature(token, timestamp, nonce, encrypted, signature)` 方法
- [x] 2.2 新建 `WecomCryptoTest`：加解密 roundtrip；签名正负样例；边界（空消息 / 超长 4096+ 字节 / EncodingAESKey 长度错）

## 3. 企业微信 API 客户端

- [x] 3.1 新建 `WecomClient`：`getAccessToken()` 内存缓存 7000s 过期重取；`sendMarkdown(userId, content)` 调 `/cgi-bin/message/send`；`uploadMedia(...)` 桩（v1 不实现）；用 Spring `WebClient`（连接/读超时配置）
- [x] 3.2 新建 `WecomClientTest`：用 WireMock 模拟 200/45009/网络错；验证 access_token 重取；验证 45009 抛 `RetryableWecomException`

## 4. Session 映射

- [x] 4.1 新建 `WecomSessionMapper`：`findSessionId(userId)` / `getOrCreate(userId)`；持久化到 `~/.agent-demo/wecom/sessions.json`；使用现成 `ObjectMapper`
- [x] 4.2 新建 `WecomSessionMapperTest`：首次创建复用持久化；并发同 userId 仅创建一次

## 5. 流式推送核心

- [x] 5.1 新建 `WecomReplyPusher`：`append(userId, chunk)` 累积 buffer；3s 定时器 或 ≥500 字符 触发 `flush`；`flush` 调 `WecomClient.sendMarkdown`；45009 退避 30s 合并；`max-chars=4000` 截断
- [x] 5.2 新建 `WecomReplyPusherTest`：限频累积；flush 触发条件；45009 退避；超长截断；message_stop 强制 flush

## 6. 派发 + Controller

- [x] 6.1 新建 `WecomEvent`（record，FromUserName / Content / MsgType / CreateTime）与 `WecomXmlParser`（用 Jackson `XmlMapper` 解析企业微信 XML 格式）
- [x] 6.2 新建 `WecomMessageDispatcher`：`dispatch(event)` 流程 = 验消息类型（非 text 拒）→ SessionMapper.getOrCreate → AgentLoopFactory.buildLoop(sid, ...).setPermissionMode(FULL_ACCESS) → 异步订阅 chunks 转发 ReplyPusher；**全部同步步骤 < 100ms** 确保回调在 5s 内返回
- [x] 6.3 新建 `WecomCallbackController`：`@RestController @RequestMapping("/wecom") @ConditionalOnProperty(havingValue="true")`；`GET /callback` 验签 + 解密 echostr 明文返回；`POST /callback` 解密 + 调 Dispatcher → 200 OK 空响应
- [x] 6.4 在 Dispatcher 加 per-userId 串行化锁（`ConcurrentHashMap<String, ReentrantLock>` + `computeIfAbsent`，lock 包住 `getOrCreate + buildLoop + processTurn`）
- [ ] 6.5 新建 `WecomCallbackControllerTest`：MockMvc + 模拟加密 payload；验签通过 / 失败；解密成功 / 失败；非 text 消息拒；并发同 userId 串行

## 7. HTTPS + 集成 + 文档

- [ ] 7.1 启用 Spring Boot HTTPS（`server.ssl.*` 已加 yml 占位）；提供 `WECOM_HTTPS_ENABLED=true` 切换；证书路径 `WECOM_KEYSTORE` env 注入
- [ ] 7.2 在 `docs/test-agent-demo/2026-09-18-wecom-channel-*/` 写四件套：test-design（dev 步骤：ngrok / 企业微信后台 / curl 模拟）/ test-cases（正常 / 验签失败 / 限频 / 超时 / 并发）/ test-report（实测结果）/ test-review（复盘）
- [ ] 7.3 跑 `mvn -o -pl agent-core,agent-web verify -DskipNpm=true -Dsurefire.excludes=**/e2e/**`：单元 + 集成测试全绿；jacoco LINE≥80% / BRANCH≥70% 通过；既有 4 个 web 包级 jacoco 违规未恶化
- [ ] 7.4 走 OpenSpec archive：`openspec archive add-wecom-channel --yes`；delta spec 合并到 `openspec/specs/wecom-channel/spec.md`；分支合 main + 复验 + push
