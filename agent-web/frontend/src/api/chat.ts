/**
 * 后端 HTTP API 客户端 (T7.3).
 * 配合 lib/sse-client.ts 跑流.
 */

import type { MessageClock } from "../lib/message-clock";

/**
 * 权限模式（rewrite-permission-mode-dsh T10：统一为 dsh 4 档命名）。
 *
 * 后端 `PermissionMode.from()` 接受新旧两套 wire value 并自动 normalize：
 * - 新 4 档（推荐）
 * - 旧 3 档（read_only / workspace_write / full_access）
 */
export type PermissionMode = "plan" | "ask" | "danger-full" | "dontAsk";

/** 旧 3 档 wire value（向后兼容；后端会自动 normalize 为 4 档） */
export type LegacyPermissionMode = "read_only" | "workspace_write" | "full_access";

/** send / permission 请求可接受的 mode 值（新旧兼容） */
export type PermissionModeInput = PermissionMode | LegacyPermissionMode;

export interface SendRequest {
  content: string;
  session_id?: string;
  permission_mode?: PermissionModeInput;
  /**
   * add-provider-catalog-abstract: 可选 provider id（如 `deepseek` / `openai` / `anthropic`）；
   * 缺省由后端 `ProviderInference` 按 model 前缀推断，推断失败回退 `agent.chat.default-provider`。
   */
  provider?: string;
  /**
   * 可选模型名。留空/不传 → 服务端用 `agent.chat.default-model`；
   * 传了但不在服务端目录中 → 400 `invalid_model`（不再静默兜底）。
   * 合法取值见 `listModels()`。
   */
  model?: string;
  /** add-models-dropdown-v0: 可选思考强度（low / medium / high）；缺省由 Provider 内部 fallback */
  reasoning_effort?: string;
}

export interface SendResponse {
  stream_id: string;
  session_id: string;
  model: string;
}

/**
 * 推理强度档位条目（add-provider-catalog-abstract）。
 *
 * <p>对齐 dsh web `ModelReasoningEffort`：每个推理模型可选档位的 `{id, name, description?}`。
 */
export interface ReasoningEffort {
  id: string;
  name: string;
  description?: string;
}

/** add-provider-catalog-abstract: /api/chat/models 嵌套结构里的模型条目 */
export interface ModelEntry {
  id: string;
  name: string;
  supportsReasoning: boolean;
  reasoningEfforts: ReasoningEffort[];
}

/**
 * Provider 分组（add-provider-catalog-abstract）。
 *
 * <p>两层菜单外层：列出 provider（如 "DeepSeek" / "OpenAI" / "Anthropic"），
 * 每个 provider 嵌套其下的所有 {@link ModelEntry}。对齐 dsh web `ModelProviderGroup`。
 */
export interface ProviderGroup {
  id: string;
  name: string;
  models: ModelEntry[];
}

/**
 * `/api/chat/models` 响应（add-provider-catalog-abstract）。
 *
 * <p>**BREAKING**：v0.1（add-models-dropdown-v0）是平铺 `models[]`；
 * v0.2 升级为嵌套 `providers[]`。平铺字段已移除。
 *
 * <p>`defaultProvider` / `defaultModel` 为 fix-stale-model-fallback 新增（merge main 后保留）：
 * 前端据此兜底，不必再硬编码模型 id。可选是因为连到旧版后端时这两个字段不存在。
 */
export interface ModelsResponse {
  providers: ProviderGroup[];
  defaultProvider?: string;
  defaultModel?: string;
}

/**
 * 模型选择三元组（add-provider-catalog-abstract）。
 *
 * <p>对齐 dsh web `ModelSelection`：provider + model + 可选 reasoningEffort。
 */
export interface ModelSelection {
  provider: string;
  model: string;
  reasoningEffort?: string;
}

/** localStorage key（add-models-dropdown-v0 沿用，v0.2 起 value 含 provider）。 */
export const MODEL_SELECTION_KEY = "agent-demo:model-selection";

/**
 * 按 model 名前缀推断 provider（add-provider-catalog-abstract；对齐后端 `ProviderInference`）。
 *
 * <p>用于旧格式 localStorage（无 provider 字段）的 fallback。推断失败返回 `null`。
 */
export function inferProvider(model: string | null | undefined): string | null {
  if (!model) return null;
  const m = model.toLowerCase();
  if (m.startsWith("o1") || m.startsWith("o3") || m.startsWith("o4") || m.startsWith("gpt-")) {
    return "openai";
  }
  if (m.startsWith("claude-")) return "anthropic";
  if (m.startsWith("deepseek-")) return "deepseek";
  return null;
}

/**
 * 读 localStorage 里的模型选择（add-provider-catalog-abstract task 8.4）。
 *
 * <p>兼容旧格式（add-models-dropdown-v0 只有 `{model, reasoningEffort}`）：读到旧格式时
 * `provider` 返回空串，调用方应走 {@link inferProvider} 推断。
 *
 * @returns 解析成功的选择；无记录 / 格式非法（缺 model）/ JSON 损坏时返回 `null`
 */
export function readModelSelection(): ModelSelection | null {
  try {
    const raw = window.localStorage.getItem(MODEL_SELECTION_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Partial<ModelSelection>;
    if (typeof parsed.model !== "string" || parsed.model.length === 0) return null;
    return {
      provider: typeof parsed.provider === "string" ? parsed.provider : "",
      model: parsed.model,
      reasoningEffort:
        typeof parsed.reasoningEffort === "string" && parsed.reasoningEffort.length > 0
          ? parsed.reasoningEffort
          : undefined,
    };
  } catch {
    return null;
  }
}

/** 写 localStorage（add-provider-catalog-abstract task 8.4）；失败静默忽略（隐私模式等）。 */
export function writeModelSelection(selection: ModelSelection): void {
  try {
    window.localStorage.setItem(MODEL_SELECTION_KEY, JSON.stringify(selection));
  } catch {
    /* ignore */
  }
}

export interface SlashResult {
  consumed: boolean;
  command?: string;
  output?: string;
  closeStream: boolean;
}

export interface CurrentSession {
  session_id: string | null;
  started_at?: number;
  turn_count?: number;
  tokens_in?: number;
  tokens_out?: number;
  model?: string;
}

export interface HistoryToolCall {
  id: string;
  name: string;
  argumentsJson?: string;
}

export interface HistoryMessage {
  role: string;
  content: string;
  toolCalls?: HistoryToolCall[];
  toolCallId?: string;
  isError?: boolean;
  /** add-message-actions P2：该条 assistant 在会话存档里的条目 uuid */
  uuid?: string | null;
  /** add-message-actions P2：该条消息的读数（刷新后 clock 仍在） */
  meta?: MessageClock | null;
}

export interface HistoryResponse {
  session_id: string;
  messages: HistoryMessage[];
}

export interface SessionSummary {
  id: string;
  title: string;
  preview: string;
  workspace: string;
  time: number;
  /**
   * 时间分档（auto-archive-stale-sessions）：仅归档列表返回。
   * `recent` | `last_week` | `within_month` | `earlier`（见后端 `SessionAgeBucket`）。
   */
  bucket?: string;
}

/** 工作区（add-workspaces-and-rename）。 */
export interface Workspace {
  name: string;
  // align-dsh-workspace v2: 稳定 id / title / updatedAt / status
  id: string;
  title: string;
  updatedAt: number;
  status: "ok" | "missing_dir";
  // v1 兼容字段（保留）
  dir: string;
  sessionCount: number;
  lastActiveAt: number;
}

/** 会话累计统计（add-session-stats-bar）；派生指标不可用时为 null。 */
export interface SessionStats {
  turns: number;
  steps: number;
  tokens_in: number;
  tokens_out: number;
  llm_ms: number;
  tool_ms: number;
  avg_ttft_ms: number | null;
  tok_per_sec: number | null;
  cache_hit_rate: number | null;
}

export class ChatApi {
  constructor(private base: string = '') {}

  async send(req: SendRequest): Promise<SendResponse> {
    const r = await fetch(this.base + '/api/chat/send', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(req),
    });
    if (!r.ok) {
      const body = await r.text();
      throw new Error(`send ${r.status}: ${body}`);
    }
    return (await r.json()) as SendResponse;
  }

  streamUrl(streamId: string): string {
    return this.base + `/api/chat/stream/${streamId}`;
  }

  async abort(streamId: string): Promise<void> {
    await fetch(this.base + `/api/chat/abort/${streamId}`, { method: 'POST' });
  }

  async submitDecision(streamId: string, permissionId: string, decision: 'yes' | 'no' | 'always'): Promise<boolean> {
    const r = await fetch(this.base + `/api/chat/decision/${streamId}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ permission_id: permissionId, decision }),
    });
    if (r.status === 404) return false;
    if (!r.ok) throw new Error(`submitDecision ${r.status}`);
    const body = (await r.json()) as { ok: boolean };
    return body.ok;
  }

  // 实时切换权限模式（add-permission-mode-dropdown + rewrite-permission-mode-dsh T10）
  //
  // escalate=true 时后端临时升级, turn 结束自动恢复; 响应含 effective_mode 字段。
  async setPermission(
    streamId: string,
    mode: PermissionModeInput,
    escalate: boolean = false,
  ): Promise<{ mode: string; effective_mode: string }> {
    const r = await fetch(this.base + `/api/chat/${encodeURIComponent(streamId)}/permission`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ mode, escalate }),
    });
    if (!r.ok) throw new Error(`setPermission ${r.status}`);
    const body = (await r.json()) as { ok: boolean; mode: string; effective_mode: string };
    return { mode: body.mode, effective_mode: body.effective_mode };
  }

  async slash(streamId: string, content: string): Promise<SlashResult> {
    const r = await fetch(this.base + `/api/chat/slash/${streamId}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ content }),
    });
    if (r.status === 400) {
      return (await r.json()) as SlashResult;
    }
    if (!r.ok) throw new Error(`slash ${r.status}`);
    return (await r.json()) as SlashResult;
  }

  async currentSession(): Promise<CurrentSession> {
    const r = await fetch(this.base + '/api/sessions/current');
    if (!r.ok) throw new Error(`currentSession ${r.status}`);
    return (await r.json()) as CurrentSession;
  }

  /**
   * 拉取某会话的消息历史（v0.3 会话重进恢复）。
   * @param sessionId 会话 id
   * @returns 会话消息；未知会话 / 无存档时 messages 为空或抛错
   */
  async history(sessionId: string): Promise<HistoryResponse> {
    const r = await fetch(this.base + `/api/sessions/${encodeURIComponent(sessionId)}/messages`);
    if (r.status === 404) return { session_id: sessionId, messages: [] };
    if (!r.ok) throw new Error(`history ${r.status}`);
    return (await r.json()) as HistoryResponse;
  }

  // 列出现实会话（add-session-switch）；可选按工作区过滤（add-workspaces-and-rename）
  async listSessions(workspace?: string): Promise<SessionSummary[]> {
    const q = workspace ? `?workspace=${encodeURIComponent(workspace)}` : '';
    const r = await fetch(this.base + `/api/sessions${q}`);
    if (!r.ok) throw new Error(`listSessions ${r.status}`);
    return (await r.json()) as SessionSummary[];
  }

  // 列出归档会话（add-session-management）；可选按工作区过滤
  async listArchived(workspace?: string): Promise<SessionSummary[]> {
    const q = new URLSearchParams({ archived: 'true' });
    if (workspace) q.set('workspace', workspace);
    const r = await fetch(this.base + `/api/sessions?${q}`);
    if (!r.ok) throw new Error(`listArchived ${r.status}`);
    return (await r.json()) as SessionSummary[];
  }

  // 列工作区（add-workspaces-and-rename）
  async listWorkspaces(): Promise<Workspace[]> {
    const r = await fetch(this.base + '/api/workspaces');
    if (!r.ok) throw new Error(`listWorkspaces ${r.status}`);
    return (await r.json()) as Workspace[];
  }

  // 创建工作区（align-dsh-workspace v2: DSH 单 action，只需 path，name + title 后端派生）
  async createWorkspace(path: string): Promise<{ ok: boolean; name: string; id: string; title: string; dir: string }> {
    const r = await fetch(this.base + '/api/workspaces', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ path }),
    });
    if (!r.ok) throw new Error(`createWorkspace ${r.status}: ${await r.text()}`);
    return (await r.json()) as { ok: boolean; name: string; id: string; title: string; dir: string };
  }

  // 会话重命名（add-workspaces-and-rename）
  async renameSession(sessionId: string, title: string): Promise<void> {
    const r = await fetch(this.base + `/api/sessions/${encodeURIComponent(sessionId)}/rename`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ title }),
    });
    if (!r.ok) throw new Error(`renameSession ${r.status}`);
  }

  // 会话累计统计（add-session-stats-bar）
  async sessionStats(sessionId: string, workspace?: string): Promise<SessionStats> {
    const q = workspace ? `?workspace=${encodeURIComponent(workspace)}` : '';
    const r = await fetch(this.base + `/api/sessions/${encodeURIComponent(sessionId)}/stats${q}`);
    if (!r.ok) throw new Error(`sessionStats ${r.status}`);
    return (await r.json()) as SessionStats;
  }

  // 归档（软删除）会话（add-session-management）
  async archiveSession(sessionId: string): Promise<boolean> {
    const r = await fetch(this.base + `/api/sessions/${encodeURIComponent(sessionId)}`, { method: 'DELETE' });
    if (r.status === 404) return false;
    if (!r.ok) throw new Error(`archiveSession ${r.status}`);
    return true;
  }

  // 恢复归档会话（add-session-management）
  async restoreSession(sessionId: string): Promise<boolean> {
    const r = await fetch(this.base + `/api/sessions/${encodeURIComponent(sessionId)}/restore`, { method: 'POST' });
    if (r.status === 404) return false;
    if (!r.ok) throw new Error(`restoreSession ${r.status}`);
    return true;
  }

  async health(): Promise<{ status: string; version: string; uptime_s: number }> {
    const r = await fetch(this.base + '/api/health');
    if (!r.ok) throw new Error(`health ${r.status}`);
    return await r.json();
  }

  /** add-models-dropdown-v0: 拉取 supported-models 列表（含 reasoningEfforts） */
  async listModels(): Promise<ModelsResponse> {
    const r = await fetch(this.base + '/api/chat/models');
    if (!r.ok) throw new Error(`listModels ${r.status}`);
    return (await r.json()) as ModelsResponse;
  }
}
