/**
 * PermissionModeSelect 测试 (add-settings-general-items M2).
 */

import { fireEvent, render } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { PermissionModeSelect } from "./PermissionModeSelect";

const noopPatch = vi.fn().mockResolvedValue(undefined);

const mockSnapshot = {
  version: 1,
  general: { permission: { mode: "ask" as const } },
  revision: 0,
};

const mockStore = {
  snapshot: mockSnapshot,
  status: "ready" as const,
  error: null,
  patch: noopPatch,
  refresh: vi.fn(),
};

vi.mock("../hooks/useSettingsStore", () => ({
  useSettingsStore: (selector: (s: typeof mockStore) => unknown) => selector(mockStore),
}));

describe("PermissionModeSelect", () => {
  beforeEach(() => {
    noopPatch.mockClear();
  });

  it("renders 4 options", () => {
    const { getByTestId, getAllByRole } = render(<PermissionModeSelect />);
    const select = getByTestId("permission-mode-select") as HTMLSelectElement;
    expect(getAllByRole("option").length).toBe(4);
    expect(select.value).toBe("ask");
  });

  it("shows current value", () => {
    mockStore.snapshot = {
      ...mockSnapshot,
      general: { permission: { mode: "plan" } },
    } as unknown as typeof mockSnapshot;
    const { getByTestId } = render(<PermissionModeSelect />);
    const select = getByTestId("permission-mode-select") as HTMLSelectElement;
    expect(select.value).toBe("plan");
  });

  it("invokes patch on change", () => {
    const { getByTestId } = render(<PermissionModeSelect />);
    const select = getByTestId("permission-mode-select") as HTMLSelectElement;
    fireEvent.change(select, { target: { value: "danger-full" } });
    expect(noopPatch).toHaveBeenCalledWith("general.permission.mode", "danger-full");
  });
});
