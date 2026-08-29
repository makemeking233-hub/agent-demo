/**
 * 后端 HTTP API 客户端 (T7.3).
 * 配合 lib/sse-client.ts 跑流.
 */

export interface SendRequest {
  content: string;
  session_id?: string;
}

export interface SendResponse {
  stream_id: string;
  session_id: string;
  model: string;
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

  async health(): Promise<{ status: string; version: string; uptime_s: number }> {
    const r = await fetch(this.base + '/api/health');
    if (!r.ok) throw new Error(`health ${r.status}`);
    return await r.json();
  }
}
