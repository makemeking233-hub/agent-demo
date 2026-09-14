# voice fixtures（improve-voice-accuracy T9.2）

## 当前状态

**前端 voice 测试不依赖真实音频文件**，统一用 mock `Stt` 接口（详见 `src/lib/stt.ts`）：

- `mockStt((onFinal, _onPartial) => { cb = onFinal; })` —— 测试直接驱动 Vosk final / partial 事件
- 不读 .wav / .pcm / 不依赖 `vosk-browser` 实际 WASM

这避免了：

- 测试运行依赖真实麦克风权限
- 跨浏览器音频编解码差异（Safari / Chrome / Firefox）
- 1.5MB+ 模型下载拖慢测试启动

## 何时需要真实音频 fixture

只有以下场景才需要把 .wav 文件加入本目录：

| 场景 | 是否需要真实音频 |
|------|----------------|
| 单元测试（`useVoiceChat.test.ts`、`Composer.test.tsx` 等） | ❌ mock Stt |
| ASR 后处理算法（`voicePostProcess.test.ts`） | ❌ mock Vosk output |
| 浏览器兼容 e2e（playwright-cypress 等） | ✅ 短句 .wav |
| 真实环境演示（手动 smoke test） | ✅ 真人朗读 |

## 命名约定

```text
test/fixtures/voice/
├── README.md                  ← 本文件
├── short-command.wav           ← < 1s 短命令（如「打开文件」）
├── mid-sentence.wav            ← 1-3s 中等长度
└── long-paragraph.wav          ← > 5s 段落（含 partial 触发多次）
```

文件名用 kebab-case；内容标签简短易懂。

## 清理要求

**测试跑完必须清理自己产生的临时文件**（AGENTS.md §10）：

1. 真实录音请写到 `target/test-voice-tmp/`（mvn clean 自动清；不在 git 跟踪）
2. 任何测试产生的调试输出（localStorage 诊断条目、IndexedDB 数据）跑完立即删除
3. 调试用的 .wav 不要 commit 进本目录（用 `target/` 或 `*.tmp.wav`）

## 历史

- 2026-09-13（improve-voice-accuracy T9）：初始化目录 + 本 README