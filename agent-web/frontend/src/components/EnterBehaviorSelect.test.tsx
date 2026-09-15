/**
 * EnterBehaviorSelect 测试 (add-settings-general-items M2).
 */

import { fireEvent, render } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { EnterBehaviorSelect } from "./EnterBehaviorSelect";

const noopPatch = vi.fn().mockResolvedValue(undefined);

const mockSnapshot = {
  version: 1,
  general: { enterBehavior: { mode: "send" as const } },
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

describe("EnterBehaviorSelect", () => {
  beforeEach(() => {
    noopPatch.mockClear();
  });

  it("renders 3 options", () => {
    const { getAllByRole, getByTestId } = render(<EnterBehaviorSelect />);
    expect(getAllByRole("option").length).toBe(3);
    expect((getByTestId("enter-behavior-select") as HTMLSelectElement).value).toBe("send");
  });

  it("invokes patch on change", () => {
    const { getByTestId } = render(<EnterBehaviorSelect />);
    const select = getByTestId("enter-behavior-select") as HTMLSelectElement;
    fireEvent.change(select, { target: { value: "queue" } });
    expect(noopPatch).toHaveBeenCalledWith("general.enterBehavior.mode", "queue");
  });
});
