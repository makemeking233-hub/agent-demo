/**
 * SettingsEmpty 测试 (add-settings-menu-placeholders M3).
 */

import { Box } from "lucide-react";
import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { SettingsEmpty } from "./SettingsEmpty";

describe("SettingsEmpty", () => {
  it("renders title and description", () => {
    const { getByText } = render(
      <SettingsEmpty icon={Box} title="模型设置" description="配置默认模型" />,
    );
    expect(getByText("模型设置")).toBeInTheDocument();
    expect(getByText("配置默认模型")).toBeInTheDocument();
    expect(getByText("将在后续版本接入")).toBeInTheDocument();
  });

  it("renders custom testId", () => {
    const { getByTestId } = render(
      <SettingsEmpty icon={Box} title="t" description="d" testId="my-test-id" />,
    );
    expect(getByTestId("my-test-id")).toBeInTheDocument();
  });
});
