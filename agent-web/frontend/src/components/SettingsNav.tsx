/**
 * SettingsNav (add-settings-foundation M1
 * → shadcn-components-p1: 从 CSS Modules 迁到 Tailwind utility).
 *
 * 4 项菜单: 通用设置 / 模型 / 插件 / Agent 预设.
 * 当前项 aria-current="true".
 */

export interface SettingsNavItem {
  id: string;
  label: string;
}

interface SettingsNavProps {
  items: SettingsNavItem[];
  activeId: string;
  onSelect: (id: string) => void;
}

export function SettingsNav({ items, activeId, onSelect }: SettingsNavProps) {
  return (
    <>
      {items.map((item) => {
        const active = item.id === activeId;
        return (
          <button
            key={item.id}
            type="button"
            className={
              active
                ? 'rounded-md bg-accent px-2 py-1.5 text-left text-sm font-medium text-accent-foreground'
                : 'rounded-md px-2 py-1.5 text-left text-sm text-muted-foreground hover:bg-accent/50 hover:text-foreground'
            }
            aria-current={active ? 'true' : undefined}
            onClick={() => onSelect(item.id)}
            data-testid={`settings-nav-${item.id}`}
          >
            {item.label}
          </button>
        );
      })}
    </>
  );
}