import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { App } from './App';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { STATUS_OK, USAGE_OK, jsonResponse, stubFetch } from './test/http';
import { renderAt } from './test/render';

afterEach(() => vi.unstubAllGlobals());

const okBackend = () => stubFetch((url) => jsonResponse(200, url.endsWith('usage') ? USAGE_OK : STATUS_OK));

describe('navigation', () => {
  it('offers exactly the five working pages', () => {
    okBackend();
    renderAt('/');
    const links = within(screen.getByRole('navigation', { name: 'Primary' })).getAllByRole('link');
    expect(links.map((link) => link.textContent)).toEqual(['Dashboard', 'AI Playground', 'Structured Output', 'Provider Status', 'Usage & Limits']);
  });

  it.each([
    ['AI Playground', 'AI Playground'],
    ['Structured Output', 'Structured Output'],
    ['Provider Status', 'Provider Status'],
    ['Usage & Limits', 'Usage & Limits'],
    ['Dashboard', 'Dashboard'],
  ])('opens %s and marks it current', async (linkName, heading) => {
    okBackend();
    const user = userEvent.setup();
    renderAt('/');
    await user.click(screen.getByRole('link', { name: linkName }));
    expect(await screen.findByRole('heading', { level: 1, name: heading })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: linkName })).toHaveAttribute('aria-current', 'page');
  });

  it('toggles the mobile menu with correct aria state', async () => {
    okBackend();
    const user = userEvent.setup();
    renderAt('/');
    const toggle = screen.getByRole('button', { name: 'Menu' });
    expect(toggle).toHaveAttribute('aria-expanded', 'false');
    await user.click(toggle);
    expect(screen.getByRole('button', { name: 'Close menu' })).toHaveAttribute('aria-expanded', 'true');
  });

  it('has a skip link and a main landmark', () => {
    okBackend();
    renderAt('/');
    expect(screen.getByRole('link', { name: 'Skip to main content' })).toHaveAttribute('href', '#main');
    expect(screen.getByRole('main')).toBeInTheDocument();
  });

  it('shows a not-found state for unknown routes', () => {
    okBackend();
    renderAt('/nope');
    expect(screen.getByText('Page not found')).toBeInTheDocument();
  });

  it('moves focus to the main region and updates the title on route change', async () => {
    okBackend();
    const user = userEvent.setup();
    renderAt('/');
    expect(document.title).toBe('Dashboard · GhostBridge AI');
    await user.click(screen.getByRole('link', { name: 'Usage & Limits' }));
    expect(document.title).toBe('Usage & Limits · GhostBridge AI');
    expect(screen.getByRole('main')).toHaveFocus();
  });

  it('renders host content in the header when given', () => {
    okBackend();
    render(
      <MemoryRouter>
        <App headerActions={<span>signed in as tester</span>} />
      </MemoryRouter>,
    );
    expect(screen.getByText('signed in as tester')).toBeInTheDocument();
  });
});
