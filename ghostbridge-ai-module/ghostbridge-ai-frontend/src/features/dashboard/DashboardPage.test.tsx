import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { STATUS_OK, USAGE_OK, jsonResponse, stubFetch } from '../../test/http';
import { renderPage } from '../../test/render';
import { DashboardPage } from './DashboardPage';

afterEach(() => vi.unstubAllGlobals());

const route = (status: () => Response, usage: () => Response) => (url: string) => (url.endsWith('usage') ? usage() : status());

describe('DashboardPage', () => {
  it('shows a loading skeleton first, then the returned values', async () => {
    stubFetch(route(() => jsonResponse(200, STATUS_OK), () => jsonResponse(200, USAGE_OK)));
    renderPage(<DashboardPage />);
    expect(screen.getAllByRole('status', { name: '' }).length).toBeGreaterThan(0);
    expect(await screen.findByText('test-model')).toBeInTheDocument();
    expect(screen.getByText('AVAILABLE')).toBeInTheDocument();
    expect(screen.getByText('TEXT_GENERATION')).toBeInTheDocument();
    expect(screen.getByText('12')).toBeInTheDocument(); // requestCount
    expect(screen.getByText('4,600')).toBeInTheDocument();
    expect(screen.getByText('Limit per minute')).toBeInTheDocument();
  });

  it('does not claim the status is a live availability check', async () => {
    stubFetch(route(() => jsonResponse(200, STATUS_OK), () => jsonResponse(200, USAGE_OK)));
    renderPage(<DashboardPage />);
    expect(await screen.findByText(/configuration status only/i)).toBeInTheDocument();
  });

  it('shows empty states for missing capabilities, zero requests and absent rate limit', async () => {
    stubFetch(
      route(
        () => jsonResponse(200, { configured: false, status: 'NOT_CONFIGURED' }),
        () => jsonResponse(200, { ...USAGE_OK, requestCount: 0, rateLimit: undefined }),
      ),
    );
    renderPage(<DashboardPage />);
    expect(await screen.findByText('No capabilities reported')).toBeInTheDocument();
    expect(screen.getByText('No requests in the retained history')).toBeInTheDocument();
    expect(screen.getByText('No rate-limit information')).toBeInTheDocument();
    expect(screen.getAllByText('Not reported')).toHaveLength(2);
  });

  it('shows the authentication-required state on 401 and does not retry', async () => {
    const stub = stubFetch(() => jsonResponse(401, { code: 'AUTHENTICATION_REQUIRED' }));
    renderPage(<DashboardPage />);
    expect(await screen.findByRole('alert')).toHaveTextContent('Authentication required');
    expect(screen.queryByRole('button', { name: 'Try again' })).not.toBeInTheDocument();
    expect(stub).toHaveBeenCalledTimes(2); // status + usage, once each
  });

  it('shows the access-denied state on 403', async () => {
    stubFetch(() => jsonResponse(403, { code: 'ACCESS_DENIED' }));
    renderPage(<DashboardPage />);
    expect(await screen.findByRole('alert')).toHaveTextContent('Access denied');
  });

  it('shows an unavailable state on 503 and reloads only on an explicit click', async () => {
    const stub = stubFetch(() => jsonResponse(503, { code: 'PROVIDER_UNAVAILABLE' }));
    const user = userEvent.setup();
    renderPage(<DashboardPage />);
    const alerts = await screen.findAllByRole('alert');
    expect(alerts[0]).toHaveTextContent('Service unavailable');
    expect(stub).toHaveBeenCalledTimes(2);
    stub.mockImplementation(() => Promise.resolve(jsonResponse(200, STATUS_OK)));
    await user.click(screen.getAllByRole('button', { name: 'Try again' })[0]);
    expect(stub).toHaveBeenCalledTimes(3);
  });

  it('reports an unreachable backend', async () => {
    stubFetch(() => {
      throw new TypeError('Failed to fetch');
    });
    renderPage(<DashboardPage />);
    expect((await screen.findAllByRole('alert'))[0]).toHaveTextContent('Backend unreachable');
  });

  it('refreshes both resources on demand', async () => {
    const stub = stubFetch(route(() => jsonResponse(200, STATUS_OK), () => jsonResponse(200, USAGE_OK)));
    const user = userEvent.setup();
    renderPage(<DashboardPage />);
    await screen.findByText('test-model');
    await user.click(screen.getByRole('button', { name: 'Refresh' }));
    await screen.findByText('test-model');
    expect(stub).toHaveBeenCalledTimes(4);
  });

  it('cancels in-flight requests when unmounted', async () => {
    const signals: AbortSignal[] = [];
    stubFetch((_url, init) => {
      signals.push(init.signal as AbortSignal);
      return new Promise<Response>(() => undefined) as unknown as Response;
    });
    const view = renderPage(<DashboardPage />);
    await Promise.resolve();
    view.unmount();
    expect(signals.length).toBeGreaterThan(0);
    expect(signals.every((signal) => signal.aborted)).toBe(true);
  });

  it('flags a malformed response as unexpected rather than crashing', async () => {
    stubFetch(route(() => jsonResponse(200, { nope: true }), () => jsonResponse(200, USAGE_OK)));
    renderPage(<DashboardPage />);
    expect(await screen.findByText('Unexpected response')).toBeInTheDocument();
  });
});
