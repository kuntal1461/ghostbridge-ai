import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useNavigate } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { ErrorBoundary } from './ErrorBoundary';

function Boom(): never {
  throw new Error('confidential prompt text leaked in error');
}

describe('ErrorBoundary', () => {
  it('shows a generic fallback without the thrown message', () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    render(
      <ErrorBoundary>
        <Boom />
      </ErrorBoundary>,
    );
    expect(screen.getByRole('alert')).toHaveTextContent('Something went wrong');
    expect(document.body.textContent).not.toContain('confidential prompt text');
    vi.restoreAllMocks();
  });

  it('recovers when the boundary is re-keyed by navigation', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    const { default: userEvent } = await import('@testing-library/user-event');
    function Harness() {
      const navigate = useNavigate();
      return (
        <>
          <button type="button" onClick={() => navigate('/ok')}>go</button>
          <Routes>
            <Route path="/" element={<ErrorBoundary key="/"><Boom /></ErrorBoundary>} />
            <Route path="/ok" element={<ErrorBoundary key="/ok"><p>fine</p></ErrorBoundary>} />
          </Routes>
        </>
      );
    }
    render(<MemoryRouter><Harness /></MemoryRouter>);
    expect(screen.getByRole('alert')).toBeInTheDocument();
    await userEvent.setup().click(screen.getByRole('button', { name: 'go' }));
    expect(screen.getByText('fine')).toBeInTheDocument();
    vi.restoreAllMocks();
  });
});
