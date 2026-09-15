/**
 * settings REST 客户端 (add-settings-foundation M1).
 *
 * - GET /api/settings 返回 SettingsView
 * - PATCH /api/settings/{path}（dot notation 路径）写入单字段
 * - GET /api/settings/file-path 返回 settings.yaml 绝对路径（M2 reveal 用）
 */

export interface SettingsView {
  version: number;
  general: Record<string, unknown>;
  revision: number;
}

export interface SettingsError {
  error: string;
  path?: string;
  revision?: number;
  status: number;
}

export class SettingsApi {
  constructor(private base: string = '') {}

  async getSettings(): Promise<SettingsView> {
    const r = await fetch(this.base + '/api/settings');
    if (!r.ok) {
      throw await this.toError(r, 'getSettings');
    }
    return (await r.json()) as SettingsView;
  }

  async patch(path: string, value: unknown, revision?: number): Promise<SettingsView> {
    const body: Record<string, unknown> = { value };
    if (revision !== undefined) body.revision = revision;
    const r = await fetch(this.base + '/api/settings/' + path, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
    if (!r.ok) {
      throw await this.toError(r, 'patch');
    }
    return (await r.json()) as SettingsView;
  }

  async getFilePath(): Promise<string> {
    const r = await fetch(this.base + '/api/settings/file-path');
    if (!r.ok) throw new Error(`getFilePath ${r.status}`);
    const body = (await r.json()) as { path: string };
    return body.path;
  }

  /** GET /api/settings/events SSE URL；浏览器原生 EventSource 用 */
  eventsUrl(): string {
    return this.base + '/api/settings/events';
  }

  private async toError(r: Response, op: string): Promise<SettingsError> {
    let body: Record<string, unknown> = {};
    try {
      body = await r.json();
    } catch {
      /* ignore */
    }
    return {
      error: (body.error as string) ?? 'unknown_error',
      path: body.path as string | undefined,
      revision: body.revision as number | undefined,
      status: r.status,
    };
  }
}
