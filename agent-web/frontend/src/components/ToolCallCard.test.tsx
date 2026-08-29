import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ToolCallCard } from './ToolCallCard';

describe('ToolCallCard', () => {
  it('renders running state with blue accent and label', () => {
    render(<ToolCallCard name="Shell" status="running" />);
    expect(screen.getByText(/Shell/)).toBeInTheDocument();
    expect(screen.getByText(/执行中/)).toBeInTheDocument();
  });

  it('renders ok state with green label', () => {
    render(<ToolCallCard name="ReadFile" status="ok" text="ok result" durationMs={5} />);
    expect(screen.getByText(/完成 \(5ms\)/)).toBeInTheDocument();
    expect(screen.getByText('ok result')).toBeInTheDocument();
  });

  it('renders fail state with red label', () => {
    render(<ToolCallCard name="EditFile" status="fail" text="error" />);
    expect(screen.getByText(/失败/)).toBeInTheDocument();
    expect(screen.getByText('error')).toBeInTheDocument();
  });

  it('omits text block when text absent', () => {
    render(<ToolCallCard name="Ls" status="ok" />);
    expect(screen.getByText(/Ls/)).toBeInTheDocument();
    // 无 text prop → 不应出现 pre 内容
    expect(screen.queryByRole('presentation')).not.toBeInTheDocument();
  });
});
