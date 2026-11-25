import { screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { STATUS_OK, jsonResponse, stubFetch } from '../../test/http';
import { renderPage } from '../../test/render';
import { ProviderStatusPage } from './ProviderStatusPage';

afterEach(() => vi.unstubAllGlobals());

describe('ProviderStatusPage', () => {
  it('shows only the approved metadata fields', async () => {
    stubFetch(() => jsonResponse(200, { ...STATUS_OK, apiKey: 'must-not-appear', endpoint: 'https://internal' }));
    renderPage(<ProviderStatusPage />);
    expect(await screen.findByText('ollama')).toBeInTheDocument();
    expect(screen.getByText('test-model')).toBeInTheDocument();
    expect(screen.getByText('Yes')).toBeInTheDocument();
    expect(screen.getByText('STRUCTURED_OUTPUT')).toBeInTheDocument();
    expect(document.body.textContent).not.toContain('must-not-appear');
    expect(document.body.textContent).not.toContain('https://internal');
  });

  it('labels configuration status accurately', async () => {
    stubFetch(() => jsonResponse(200, STATUS_OK));
    renderPage(<ProviderStatusPage />);
    expect(await screen.findByText(/does not prove the provider is reachable/i)).toBeInTheDocument();
  });

  it('warns visibly when not configured', async () => {
    stubFetch(() => jsonResponse(200, { configured: false, status: 'NOT_CONFIGURED', capabilities: [] }));
    renderPage(<ProviderStatusPage />);
    expect(await screen.findByText('NOT_CONFIGURED')).toBeInTheDocument();
    expect(screen.getByText('No')).toBeInTheDocument();
  });
});
