# 部署文档（add-pwa-support）

## 1. 三种部署模式

| 模式 | 启动参数 | 端口 | 用途 |
|---|---|---|---|
| **HTTP（默认）** | `--spring.profiles.active=web` | 18080 | 本地开发、内网非 PWA 场景 |
| **HTTPS 自签证书（推荐 PWA）** | `--spring.profiles.active=web,https` | 18080 + 8443 | 本地安装 PWA、内网测试 |
| **HTTPS 受信证书（生产）** | 同上 + 替换 keystore | 8443 | 生产部署，需替换为受信 CA 签发证书 |

## 2. HTTPS 自签证书模式（最简 PWA 安装路径）

### 2.1 启动

```bash
# 默认 keystore 密码 changeit（自签证书场景）
mvn -pl agent-web spring-boot:run \
  -Dspring-boot.run.profiles=web,https

# 或自定义密码
mvn -pl agent-web spring-boot:run \
  -Dspring-boot.run.profiles=web,https \
  -Dspring-boot.run.arguments=--agent.web.https.keystore-password=YOUR_PASS
```

启动时 `SslCertificateGenerator`（@Profile("https")）自动：
1. 检测 `agent.web.https.enabled=true` + profile 含 `https`
2. 检查 `${java.io.tmpdir}/agent-demo-cert/keystore.p12` 是否存在
3. 不存在则调用 `keytool -genkeypair` 生成 RSA 2048 / SHA256withRSA / 365 天有效期的自签证书
4. 写出 PKCS12 keystore + PEM cert

启动日志示例：
```
[pwa-https] generating self-signed keystore (this may take ~5 seconds)...
[pwa-https] generated keystore at C:\Users\...\Temp\agent-demo-cert\keystore.p12 (4321 ms)
[pwa-https] exported PEM cert to C:\Users\...\Temp\agent-demo-cert\cert.pem
```

### 2.2 浏览器访问 + 安装

```
https://localhost:8443/
```

浏览器弹"您的连接不是私密连接"警告（自签证书），点：
- Chrome/Edge：`高级` → `继续前往 localhost（不安全）`
- Firefox：`高级` → `接受风险并继续`

随后地址栏出现 📥 安装图标，点击 → "安装 Agent-Demo" → 桌面/开始菜单出现图标。

### 2.3 重启保留

证书生成在 `${java.io.tmpdir}/agent-demo-cert/keystore.p12`，重启不重新生成。

清空：`rm -rf /tmp/agent-demo-cert/` → 下次启动重新生成。

## 3. HTTPS 受信证书模式（生产部署）

### 3.1 准备 .p12 文件

```bash
# 用受信 CA 签发的证书（Let's Encrypt / 企业 CA）
openssl pkcs12 -export \
  -in cert.pem -inkey key.pem \
  -out keystore.p12 \
  -name agent-demo \
  -password pass:YOUR_PROD_PASSWORD
```

### 3.2 替换 keystore + 配置

```bash
# 复制到 cert-dir
cp keystore.p12 /etc/agent-demo/agent-demo-cert/

# 启动时指定路径 + 密码
java -jar agent-web.jar \
  --spring.profiles.active=web,https \
  --agent.web.https.cert-dir=/etc/agent-demo/agent-demo-cert \
  --agent.web.https.keystore-password=YOUR_PROD_PASSWORD \
  --agent.web.https.keystore-alias=agent-demo
```

## 4. PWA 验证清单

部署完成后在 Chrome / Edge 浏览器验证：

- [ ] 访问 `https://localhost:8443/` → SPA 正常加载
- [ ] 地址栏右侧出现 📥 安装按钮
- [ ] 点击安装 → 桌面/开始菜单出现 "Agent-Demo" 图标
- [ ] 启动桌面图标 → 独立窗口（无地址栏）+ 应用名 "Agent-Demo"
- [ ] 断网测试：Chrome DevTools → Network → Offline → 刷新 → SPA 仍加载（静态资源命中）
- [ ] DevTools → Application → Service Workers → "sw.js" activated
- [ ] DevTools → Application → Manifest → 字段全绿
- [ ] DevTools → Lighthouse → PWA 评分 ≥ 90

## 5. 常见问题

### 5.1 keytool 命令找不到
- Windows: JDK 17 自带 keytool，应在 PATH 中
- Linux/macOS: `which keytool` 应输出 `${JAVA_HOME}/bin/keytool`

### 5.2 HTTPS 启动后 HTTP 端口 18080 还开着吗？
- 是的，Spring Boot WebFlux 双协议监听，HTTP 18080 + HTTPS 8443 同时可用
- 如需禁用 HTTP：去掉 `spring.profiles.active=web`，只留 `https`

### 5.3 自签证书怎么批量分发给客户端？
- 内网测试：直接告诉用户"高级 → 继续前往"
- 生产：替换为受信 CA 签发证书（见 §3）
- 永久信任自签：客户端执行 `keytool -importcert -alias agent-demo -file cert.pem -keystore $JAVA_HOME/lib/security/cacerts`（需 root）

## 6. 相关文档

- `docs/pwa-architecture.md` —— PWA 架构 + 缓存策略详解
- `docs/test-agent-demo/2026-09-04-pwa/` —— PWA 四件套
- `openspec/changes/archive/2026-09-04-add-pwa-support/` —— change artifacts