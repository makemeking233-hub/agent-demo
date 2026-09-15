/**
 * LanguageSelect 测试 (add-settings-general-items M2).
 */

import { fireEvent, render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { LanguageSelect } from "./LanguageSelect";

describe("LanguageSelect", () => {
  it("renders 2 options", () => {
    const { getAllByRole, getByTestId } = render(<LanguageSelect />);
    expect(getAllByRole("option").length).toBe(2);
    expect((getByTestId("language-select") as HTMLSelectElement).value).toBe("zh");
  });

  it("changes selection and persists to localStorage", () => {
    const { getByTestId } = render(<LanguageSelect />);
    const select = getByTestId("language-select") as HTMLSelectElement;
    fireEvent.change(select, { target: { value: "en" } });
    expect(window.localStorage.getItem("agent-demo:language-preference")).toBe("en");
  });

  it("reads initial value from localStorage", () => {
    window.localStorage.setItem("agent-demo:language-preference", "en");
    const { getByTestId } = render(<LanguageSelect />);
    expect((getByTestId("language-select") as HTMLSelectElement).value).toBe("en");
  });
});
