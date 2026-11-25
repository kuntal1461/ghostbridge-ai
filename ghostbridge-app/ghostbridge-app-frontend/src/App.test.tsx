import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { setAiAuthStrategy } from 'ghostbridge-ai-frontend';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetAuthApiForTests } from './api/authApi';
import { App } from './App';
import { AuthProvider } from './auth/AuthProvider';
import { PASSWORD, installFakeBackend } from './test/fakeBackend';

function renderApp(route: string | { pathname: string; state?: unknown } = '/') {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  );
}

async function signInAs(user: ReturnType<typeof userEvent.setup>, password = PASSWORD, name = 'alice') {
  await user.type(await screen.findByLabelText('User name'), name);
  await user.type(screen.getByLabelText('Password'), password);
  await user.click(screen.getByRole('button', { name: 'Sign in' }));
}

beforeEach(() => {
  resetAuthApiForTests();
  setAiAuthStrategy(null);
});
afterEach(() => vi.unstubAllGlobals());

describe('sign-in gate', () => {
  it('shows the sign-in page to an anonymous visitor and calls no AI endpoint', async () => {
    const backend = installFakeBackend();
    renderApp();
    expect(await screen.findByRole('heading', { name: 'GhostBridge' })).toBeInTheDocument();
    expect(screen.getByLabelText('Password')).toHaveAttribute('type', 'password');
    expect(backend.calls.filter((call) => call.path.startsWith('/api/v1/ai'))).toEqual([]);
  });

  it('keeps a valid session across reloads (no sign-in page) and shows the AI console with the user menu', async () => {
    installFakeBackend({ signedIn: true });
    renderApp();
    expect(await screen.findByText('Signed in as alice')).toBeInTheDocument();
    expect(await screen.findByText('phi3:3.8b')).toBeInTheDocument();
    expect(screen.getAllByRole('link').map((link) => link.textContent)).toEqual(
      expect.arrayContaining(['Dashboard', 'AI Playground', 'Structured Output', 'Provider Status', 'Usage & Limits']));
  });

  it('signs in with a form post that carries the CSRF token, refreshes the token, then loads the console', async () => {
    const backend = installFakeBackend();
    const user = userEvent.setup();
    renderApp();
    await signInAs(user);
    expect(await screen.findByText('Signed in as alice')).toBeInTheDocument();
    const login = backend.calls.find((call) => call.path === '/api/v1/auth/login');
    expect(login?.method).toBe('POST');
    expect(login?.headers).toMatchObject({ 'Content-Type': 'application/x-www-form-urlencoded', 'X-CSRF-TOKEN': 'csrf-3' }); // a fresh token is fetched right before the login
    expect(login?.body).toBe('username=alice&password=test-pass-12');
    expect(backend.calls.filter((call) => call.path === '/api/v1/auth/csrf')).toHaveLength(3);
  });

  it('returns to the page that was requested before sign-in', async () => {
    installFakeBackend();
    const user = userEvent.setup();
    renderApp('/playground');
    await signInAs(user);
    expect(await screen.findByRole('heading', { level: 1, name: 'AI Playground' })).toBeInTheDocument();
  });
});

describe('sign-in failures', () => {
  it('shows one generic message for a wrong password and clears the password field', async () => {
    installFakeBackend();
    const user = userEvent.setup();
    renderApp();
    await signInAs(user, 'wrong-password');
    expect(await screen.findByRole('alert')).toHaveTextContent('Check your user name and password');
    expect(screen.getByLabelText('Password')).toHaveValue('');
    expect(screen.queryByText('Signed in as alice')).not.toBeInTheDocument();
  });

  it('shows the Retry-After value of a lockout and never invents one', async () => {
    installFakeBackend({ failureStatus: 429, failureHeaders: { 'Retry-After': '42' } });
    const user = userEvent.setup();
    renderApp();
    await signInAs(user, 'wrong-password');
    expect(await screen.findByRole('alert')).toHaveTextContent('Try again in 42 seconds');
  });

  it('does not send a second sign-in while one is in flight', async () => {
    const backend = installFakeBackend();
    let release: (response: Response) => void = () => undefined;
    const original = backend.stub.getMockImplementation();
    backend.stub.mockImplementation((input: string, init?: RequestInit) =>
      input === '/api/v1/auth/login' ? new Promise<Response>((resolve) => { release = resolve; }) : (original as (i: string, n?: RequestInit) => Promise<Response>)(input, init));
    const user = userEvent.setup();
    renderApp();
    await signInAs(user);
    const button = await screen.findByRole('button', { name: 'Signing in…' });
    expect(button).toBeDisabled();
    await user.click(button);
    release(new Response(JSON.stringify({ username: 'alice', roles: ['AI_USER'] }), { status: 200, headers: { 'Content-Type': 'application/json' } }));
    await screen.findByText('Signed in as alice');
    expect(backend.stub.mock.calls.filter(([path]) => path === '/api/v1/auth/login')).toHaveLength(1);
  });

  it('reports an unreachable backend and offers a retry instead of a blank page', async () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new TypeError('Failed to fetch'))));
    renderApp();
    expect(await screen.findByRole('alert')).toHaveTextContent('Backend unreachable');
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument();
  });
});

describe('session lifecycle', () => {
  it('returns to the sign-in page when a request finds the session expired', async () => {
    const backend = installFakeBackend({ signedIn: true });
    const user = userEvent.setup();
    renderApp();
    await screen.findByText('phi3:3.8b');
    backend.expireSession();
    await user.click(screen.getByRole('link', { name: 'Usage & Limits' }));
    expect(await screen.findByLabelText('User name')).toBeInTheDocument();
    expect(screen.queryByText('Signed in as alice')).not.toBeInTheDocument();
  });

  it('signs out with the CSRF token and shows the sign-in page', async () => {
    const backend = installFakeBackend({ signedIn: true });
    const user = userEvent.setup();
    renderApp();
    await user.click(await screen.findByRole('button', { name: 'Sign out' }));
    expect(await screen.findByLabelText('User name')).toBeInTheDocument();
    const logout = backend.calls.find((call) => call.path === '/api/v1/auth/logout');
    expect(logout?.method).toBe('POST');
    expect(logout?.headers['X-CSRF-TOKEN']).toMatch(/^csrf-/);
  });
});

describe('boundaries', () => {
  it('talks only to GhostBridge App on the same origin and never to a provider', async () => {
    const backend = installFakeBackend();
    const user = userEvent.setup();
    renderApp();
    await signInAs(user);
    await screen.findByText('phi3:3.8b');
    for (const call of backend.calls) {
      expect(call.path).toMatch(/^\/api\/v1\/(auth|ai)\//);
    }
  });

  it('keeps nothing in web storage', async () => {
    const setItem = vi.spyOn(Storage.prototype, 'setItem');
    installFakeBackend();
    const user = userEvent.setup();
    renderApp();
    await signInAs(user);
    await screen.findByText('Signed in as alice');
    await waitFor(() => expect(setItem).not.toHaveBeenCalled());
    expect(document.cookie).toBe('');
    setItem.mockRestore();
  });
});

describe('post-login redirect', () => {
  it.each(['//evil.example', '/\\evil.example', 'https://evil.example'])('ignores the unsafe target %s', async (target) => {
    installFakeBackend();
    const user = userEvent.setup();
    renderApp({ pathname: '/login', state: { from: target } });
    await signInAs(user);
    expect(await screen.findByText('Signed in as alice')).toBeInTheDocument();
  });
});
