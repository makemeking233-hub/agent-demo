import { useEffect, useState } from "react";
import type { ModelEntry, ModelsResponse } from "../api/chat";
import { Dropdown } from "./Dropdown";
import styles from "./ModelSelect.module.css";

/**
 * 模型下拉框（add-models-dropdown-v0）。
 *
 * <p>从 {@code GET /api/chat/models} 拉取模型列表，渲染当前 model。
 * 支持切换:不接 {@code onChange} 时仅展示。
 *
 * <p>后续 {@code add-provider-catalog-abstract} 升级为两层菜单（外层 provider / 内层 model）。
 */
export interface ModelSelectProps {
  api: { listModels(): Promise<ModelsResponse> };
  value: string;
  onChange?: (modelId: string) => void;
}

export function ModelSelect({ api, value, onChange }: ModelSelectProps) {
  const [models, setModels] = useState<ModelEntry[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    api
      .listModels()
      .then((resp) => {
        if (cancelled) return;
        setModels(resp.models);
      })
      .catch((e) => {
        if (cancelled) return;
        setError(String(e));
      });
    return () => {
      cancelled = true;
    };
  }, [api]);

  const options = models.map((m) => ({
    label: `${m.name}${m.supportsReasoning ? " · 💭" : ""}`,
    value: m.id,
  }));

  if (error) {
    return <span className={styles.error} title={error}>模型加载失败</span>;
  }

  return (
    <Dropdown
      options={options}
      value={value}
      onChange={(v) => onChange?.(v)}
      placeholder="加载模型中…"
      ariaLabel="选择模型"
      align="right"
    />
  );
}