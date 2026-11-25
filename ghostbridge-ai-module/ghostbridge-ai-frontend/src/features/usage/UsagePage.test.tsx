import { screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { USAGE_OK, jsonResponse, stubFetch } from '../../test/http';
import { renderPage } from '../../test/render';
import { UsagePage } from './UsagePage';

afterEach(() => vi.unstubAllGlobals());

describe('UsagePage', () => {
  it('shows counts, tokens and the rate limit exactly as returned', async () => {
    stubFetch(() => jsonResponse(200, USAGE_OK));
    renderPage(<UsagePage />);
    expect(await screen.findByText('1,200')).toBeInTheDocument(); // input tokens
    expect(screen.getByText('3,400')).toBeInTheDocument();
    expect(screen.getByText('4,600')).toBeInTheDocument();
    expect(screen.getByText('41 s')).toBeInTheDocument();
    expect(screen.getByText('Remaining').nextSibling).toHaveTextContent('18');
  });

  it('labels bounded history, not lifetime usage', async () => {
    stubFetch(() => jsonResponse(200, USAGE_OK));
    renderPage(<UsagePage />);
    expect(await screen.findByText(/not lifetime usage/i)).toHaveTextContent('500');
    expect(screen.queryByText(/lifetime total/i)).not.toBeInTheDocument();
  });

  it('shows an empty state for zero requests', async () => {
    stubFetch(() => jsonResponse(200, { ...USAGE_OK, requestCount: 0, successCount: 0, failureCount: 0 }));
    renderPage(<UsagePage />);
    expect(await screen.findByText('No requests in the retained history')).toBeInTheDocument();
  });

  it('shows retry guidance with Retry-After on 429', async () => {
    stubFetch(() => jsonResponse(429, { code: 'RATE_LIMITED' }, { 'Retry-After': '9' }));
    renderPage(<UsagePage />);
    expect(await screen.findByRole('alert')).toHaveTextContent('wait 9 seconds');
  });
});
