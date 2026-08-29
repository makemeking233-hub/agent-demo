import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MessageBubble } from './MessageBubble';

describe('MessageBubble', () => {
  it('renders assistant markdown text', () => {
    render(<MessageBubble role="assistant" text="**你好**" />);
    // 粗体渲染成 <strong> 或 <p>
    expect(screen.getByText('你好')).toBeInTheDocument();
    const strong = screen.getByText('你好');
    expect(strong.tagName).toBe('STRONG');
  });

  it('renders inline code from markdown', () => {
    render(<MessageBubble role="assistant" text={'```js\nconst x=1;\n```'} />);
    expect(screen.getByText(/const x=1/)).toBeInTheDocument();
  });

  it('shows placeholder ellipsis when text empty', () => {
    render(<MessageBubble role="assistant" text="" />);
    expect(screen.getByText('…')).toBeInTheDocument();
  });

  it('aligns user bubble right', () => {
    const { container } = render(<MessageBubble role="user" text="hi" />);
    const flex = container.querySelector('div');
    expect(flex?.getAttribute('style')).toContain('flex-end');
  });
});
