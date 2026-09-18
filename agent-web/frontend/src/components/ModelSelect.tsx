import { useEffect, useMemo, useRef, useState } from "react";
import type { ModelEntry, ModelsResponse, ModelSelection, ProviderGroup } from "../api/chat";
import styles from "./ModelSelect.module.css";

/**
 * 模型两层选择菜单（add-models-dropdown-v0 → add-provider-catalog-abstract task 9 升级）。
 *
 * <p>v0.1 是单层平铺 model 下拉；v0.2 升级为**两层菜单**（对齐 dsh web ModelSelect）：
 *
 * <ul>
 *   <li>外层：provider 列表（左栏），如 DeepSeek / OpenAI / Anthropic
 *   <li>内层：当前 provider 下的 model 列表（右栏）
 *   <li>选中 model 后若 `supportsReasoning`，在该 model 行下方内联 effort 档位 chip
 * </ul>
 *
 * <p>trigger 显示完整 {@link ModelSelection}（provider 名 / model 名 / effort）。
 */
export interface ModelSelectProps {
  api: { listModels(): Promise<ModelsResponse> };
  value: ModelSelection;
  onChange?: (next: ModelSelection) => void;
}

/** 在 providers 里找 model 所属的 provider id（找不到返回 null）。 */
function providerIdOf(providers: ProviderGroup[], modelId: string): string | null {
  for (const p of providers) {
    if (p.models.some((m) => m.id === modelId)) return p.id;
  }
  return null;
}

export function ModelSelect({ api, value, onChange }: ModelSelectProps) {
  const [providers, setProviders] = useState<ProviderGroup[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState(false);
  // 左栏当前高亮的 provider（打开时初始化到 value.provider）
  const [activeProvider, setActiveProvider] = useState<string>(value.provider);
  const wrapRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    let cancelled = false;
    api
      .listModels()
      .then((resp) => {
        if (cancelled) return;
        setProviders(resp.providers ?? []);
      })
      .catch((e) => {
        if (cancelled) return;
        setError(String(e));
      });
    return () => {
      cancelled = true;
    };
  }, [api]);

  // 打开面板时把左栏高亮同步到当前 selection（含外部改值）
  useEffect(() => {
    if (!open) return;
    const own = providerIdOf(providers, value.model);
    setActiveProvider(own ?? value.provider ?? providers[0]?.id ?? "");
  }, [open, providers, value.model, value.provider]);

  // 点击外部关闭
  useEffect(() => {
    if (!open) return;
    function onDocClick(ev: MouseEvent) {
      if (wrapRef.current && !wrapRef.current.contains(ev.target as Node)) setOpen(false);
    }
    document.addEventListener("mousedown", onDocClick);
    return () => document.removeEventListener("mousedown", onDocClick);
  }, [open]);

  useEffect(() => {
    if (!open) return;
    function onKey(ev: KeyboardEvent) {
      if (ev.key === "Escape") setOpen(false);
    }
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [open]);

  const activeGroup = useMemo(
    () => providers.find((p) => p.id === activeProvider) ?? providers[0] ?? null,
    [providers, activeProvider]
  );

  const currentProviderName = useMemo(() => {
    const own = providerIdOf(providers, value.model);
    return providers.find((p) => p.id === (own ?? value.provider))?.name ?? value.provider ?? "";
  }, [providers, value]);

  const currentModel: ModelEntry | null = useMemo(
    () => activeGroup?.models.find((m) => m.id === value.model) ?? null,
    [activeGroup, value.model]
  );

  // effort 显示名（从当前 provider 的所有 model 里找 id → name 映射）
  const effortLabel = useMemo(() => {
    if (!value.reasoningEffort) return null;
    for (const p of providers) {
      for (const m of p.models) {
        const hit = m.reasoningEfforts.find((e) => e.id === value.reasoningEffort);
        if (hit) return hit.name;
      }
    }
    return value.reasoningEffort;
  }, [providers, value.reasoningEffort]);

  if (error) {
    return (
      <span className={styles.error} title={error}>
        模型加载失败
      </span>
    );
  }

  const triggerLabel = currentProviderName
    ? `${currentProviderName} · ${currentModel?.name ?? value.model}`
    : value.model || "加载模型中…";

  function pickModel(providerId: string, model: ModelEntry) {
    const prevEffort = value.reasoningEffort;
    const keepEffort =
      model.supportsReasoning && model.reasoningEfforts.some((e) => e.id === prevEffort);
    const nextEffort = keepEffort
      ? prevEffort
      : model.supportsReasoning && model.reasoningEfforts.length > 0
        ? model.reasoningEfforts[0].id
        : undefined;
    onChange?.({ provider: providerId, model: model.id, reasoningEffort: nextEffort });
    // 选了不支持 reasoning 的模型 / 已选 effort 时关闭；支持 reasoning 且需选档位时保持打开
    if (!model.supportsReasoning || nextEffort !== undefined) {
      setOpen(false);
    }
  }

  function pickEffort(effortId: string) {
    onChange?.({
      provider: providerIdOf(providers, value.model) ?? activeProvider,
      model: value.model,
      reasoningEffort: effortId,
    });
    setOpen(false);
  }

  return (
    <div className={styles.wrap} ref={wrapRef}>
      <button
        type="button"
        className={styles.trigger}
        onClick={() => setOpen((v) => !v)}
        aria-label="选择模型"
        aria-expanded={open}
        disabled={providers.length === 0}
      >
        <span className={styles.triggerLabel}>{triggerLabel}</span>
        {effortLabel && <span className={styles.effortBadge}>{effortLabel}</span>}
      </button>

      {open && (
        <div className={styles.twoPanel} role="menu" aria-label="模型两层菜单">
          <div className={styles.providerList}>
            {providers.map((p) => (
              <button
                key={p.id}
                type="button"
                className={`${styles.providerItem} ${p.id === activeGroup?.id ? styles.active : ""}`}
                onClick={() => setActiveProvider(p.id)}
              >
                {p.name}
              </button>
            ))}
          </div>
          <div className={styles.modelList}>
            {activeGroup?.models.length ? (
              activeGroup.models.map((m) => (
                <div key={m.id}>
                  <button
                    type="button"
                    className={`${styles.modelItem} ${m.id === value.model ? styles.active : ""}`}
                    onClick={() => pickModel(activeGroup.id, m)}
                  >
                    <span className={styles.modelLabel}>{m.name}</span>
                    {m.supportsReasoning && (
                      <span className={styles.reasoningMark} title="支持思考强度">
                        💭
                      </span>
                    )}
                  </button>
                  {m.id === value.model && m.supportsReasoning && m.reasoningEfforts.length > 0 && (
                    <div className={styles.effortInline}>
                      {m.reasoningEfforts.map((e) => (
                        <button
                          key={e.id}
                          type="button"
                          className={`${styles.effortChip} ${
                            e.id === value.reasoningEffort ? styles.effortChipActive : ""
                          }`}
                          onClick={() => pickEffort(e.id)}
                        >
                          {e.name}
                        </button>
                      ))}
                    </div>
                  )}
                </div>
              ))
            ) : (
              <div className={styles.empty}>该 provider 下暂无模型</div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
