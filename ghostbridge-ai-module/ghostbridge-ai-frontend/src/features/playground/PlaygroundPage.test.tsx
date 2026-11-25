import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { GENERATE_OK, jsonResponse, stubFetch } from '../../test/http';
import { renderPage } from '../../test/render';
import { PlaygroundPage } from './PlaygroundPage';

afterEach(() => vi.unstubAllGlobals());

async function fillAndSubmit(text = 'hello world') {
  const user = userEvent.setup();
  renderPage(<PlaygroundPage />);
  await user.type(screen.getByLabelText('Variable 1 value'), text);
  await user.click(screen.getByRole('button', { name: 'Generate' }));
  return user;
}

describe('PlaygroundPage input validation', () => {
  it('blocks an invalid template name and sends nothing', async () => {
    const stub = stubFetch(() => jsonResponse(200, GENERATE_OK));
    const user = userEvent.setup();
    renderPage(<PlaygroundPage />);
    const name = screen.getByLabelText(/Prompt template name/);
    await user.clear(name);
    await user.type(name, 'bad name!');
    await user.click(screen.getByRole('button', { name: 'Generate' }));
    expect(screen.getByText(/Enter a template name/)).toBeInTheDocument();
    expect(name).toHaveAttribute('aria-invalid', 'true');
    expect(stub).not.toHaveBeenCalled();
  });

  it.each([
    ['Variable 1 name', '1bad', /start with a letter/],
    ['Temperature 0 to 1 (optional)', '1.5', /from 0 to 1/],
    ['Max output tokens (optional)', '0', /whole number from 1/],
    ['Template version (optional)', '5000', /whole number from 1/],
  ])('rejects %s = %s', async (label, value, message) => {
    const stub = stubFetch(() => jsonResponse(200, GENERATE_OK));
    const user = userEvent.setup();
    renderPage(<PlaygroundPage />);
    const field = screen.getByLabelText(label);
    await user.clear(field);
    await user.type(field, value);
    await user.click(screen.getByRole('button', { name: 'Generate' }));
    expect(screen.getByText(message)).toBeInTheDocument();
    expect(stub).not.toHaveBeenCalled();
  });

  it('rejects a variable value over the byte limit', async () => {
    const stub = stubFetch(() => jsonResponse(200, GENERATE_OK));
    const user = userEvent.setup();
    renderPage(<PlaygroundPage />);
    await user.click(screen.getByLabelText('Variable 1 value'));
    await user.paste('é'.repeat(8200)); // 16400 UTF-8 bytes
    await user.click(screen.getByRole('button', { name: 'Generate' }));
    expect(screen.getByText(/larger than 16384 bytes/)).toBeInTheDocument();
    expect(stub).not.toHaveBeenCalled();
  });
});

describe('PlaygroundPage generation', () => {
  it('sends the AiGenerateRequestVO shape and shows the result', async () => {
    const stub = stubFetch(() => jsonResponse(200, GENERATE_OK));
    await fillAndSubmit();
    expect(await screen.findByText('Hello from the model')).toBeInTheDocument();
    expect(JSON.parse(String(stub.mock.calls[0][1].body))).toEqual({ promptName: 'generic-structured', variables: { text: 'hello world' } });
    expect(screen.getByText('test-model')).toBeInTheDocument();
    expect(screen.getByText('120 ms')).toBeInTheDocument();
    expect(screen.getByText('30')).toBeInTheDocument();
  });

  it('includes optional fields only when provided', async () => {
    const stub = stubFetch(() => jsonResponse(200, GENERATE_OK));
    const user = userEvent.setup();
    renderPage(<PlaygroundPage />);
    await user.type(screen.getByLabelText('Variable 1 value'), 'hi');
    await user.type(screen.getByLabelText('Model id (optional)'), 'm1');
    await user.type(screen.getByLabelText('Temperature 0 to 1 (optional)'), '0.3');
    await user.type(screen.getByLabelText('Max output tokens (optional)'), '256');
    await user.click(screen.getByRole('button', { name: 'Generate' }));
    await screen.findByText('Hello from the model');
    expect(JSON.parse(String(stub.mock.calls[0][1].body))).toMatchObject({ modelId: 'm1', options: { temperature: 0.3, maxOutputTokens: 256 } });
  });

  it('shows a loading state, disables submit and ignores a second submit while active', async () => {
    let release: (response: Response) => void = () => undefined;
    const stub = stubFetch(() => new Promise<Response>((resolve) => { release = resolve; }) as unknown as Response);
    const user = await fillAndSubmit();
    const button = screen.getByRole('button', { name: 'Generating…' });
    expect(button).toBeDisabled();
    expect(screen.getByText('Waiting for the model')).toBeInTheDocument();
    await user.click(button);
    release(jsonResponse(200, GENERATE_OK));
    await screen.findByText('Hello from the model');
    expect(stub).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('button', { name: 'Generate' })).toBeEnabled();
  });

  it('cancels an active request and returns to idle without an error', async () => {
    let signal: AbortSignal | undefined;
    stubFetch((_url, init) => {
      signal = init.signal as AbortSignal;
      return new Promise<Response>((_resolve, reject) => {
        signal?.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')));
      }) as unknown as Response;
    });
    const user = await fillAndSubmit();
    await user.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(signal?.aborted).toBe(true);
    await waitFor(() => expect(screen.getByRole('button', { name: 'Generate' })).toBeEnabled());
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('resets the form and the result', async () => {
    stubFetch(() => jsonResponse(200, GENERATE_OK));
    const user = await fillAndSubmit();
    await screen.findByText('Hello from the model');
    await user.click(screen.getByRole('button', { name: 'Reset' }));
    expect(screen.queryByText('Hello from the model')).not.toBeInTheDocument();
    expect(screen.getByLabelText('Variable 1 value')).toHaveValue('');
  });

  it('copies the response text', async () => {
    stubFetch(() => jsonResponse(200, GENERATE_OK));
    const user = await fillAndSubmit();
    await screen.findByText('Hello from the model');
    const writeText = vi.spyOn(navigator.clipboard, 'writeText').mockResolvedValue();
    await user.click(screen.getByRole('button', { name: 'Copy response' }));
    expect(writeText).toHaveBeenCalledWith('Hello from the model');
    expect(await screen.findByText('Copied to clipboard')).toBeInTheDocument();
  });

  it('renders model-generated HTML as inert text', async () => {
    const hostile = '<img src=x onerror="window.__pwned=1"><script>window.__pwned=1</script><a href="javascript:alert(1)">x</a>';
    stubFetch(() => jsonResponse(200, { ...GENERATE_OK, text: hostile, structured: { note: hostile } }));
    await fillAndSubmit();
    await waitFor(() => expect(screen.getByLabelText('Response (plain text)')).toHaveTextContent(hostile));
    expect(document.querySelector('img')).toBeNull();
    expect(document.querySelector('script')).toBeNull();
    expect(document.querySelector('a[href^="javascript"]')).toBeNull();
    expect((window as unknown as { __pwned?: number }).__pwned).toBeUndefined();
  });

  it('shows an empty state when the model returns no text', async () => {
    stubFetch(() => jsonResponse(200, { ...GENERATE_OK, text: '' }));
    await fillAndSubmit();
    expect(await screen.findByText('The model returned no text')).toBeInTheDocument();
  });
});

describe('PlaygroundPage errors', () => {
  it('shows server validation messages as text (400)', async () => {
    stubFetch(() => jsonResponse(400, { code: 'VALIDATION_ERROR', detail: 'Request validation failed', errors: [{ field: 'promptName', message: '<b>must exist</b>' }] }));
    await fillAndSubmit();
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Request rejected');
    expect(alert).toHaveTextContent('<b>must exist</b>');
    expect(alert.querySelector('b')).toBeNull();
  });

  it.each([
    [401, 'Authentication required'],
    [403, 'Access denied'],
    [413, 'Request too large'],
    [502, 'Provider error'],
    [503, 'Service unavailable'],
    [504, 'Provider timed out'],
  ])('maps HTTP %i to "%s"', async (status, title) => {
    stubFetch(() => jsonResponse(status, { code: 'X', detail: 'secret provider detail' }));
    await fillAndSubmit();
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(title);
    if (status >= 500) expect(alert).not.toHaveTextContent('secret provider detail');
  });

  it('429 shows Retry-After guidance, does not retry, and allows a manual resubmit', async () => {
    const stub = stubFetch(() => jsonResponse(429, { code: 'RATE_LIMITED' }, { 'Retry-After': '30' }));
    await fillAndSubmit();
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Rate limit reached');
    expect(alert).toHaveTextContent('wait 30 seconds');
    expect(stub).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('button', { name: 'Generate' })).toBeEnabled();
  });

  it('429 without Retry-After invents no number', async () => {
    stubFetch(() => jsonResponse(429, { code: 'RATE_LIMITED' }));
    await fillAndSubmit();
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('did not say how long to wait');
    expect(alert.textContent).not.toMatch(/\d+ seconds?/);
  });

  it('network failure shows a generic message', async () => {
    stubFetch(() => {
      throw new TypeError('Failed to fetch http://10.0.0.5/secret');
    });
    await fillAndSubmit();
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Backend unreachable');
    expect(alert).not.toHaveTextContent('10.0.0.5');
  });

  it('never logs prompts or responses', async () => {
    const spies = (['log', 'info', 'warn', 'error', 'debug'] as const).map((level) => vi.spyOn(console, level).mockImplementation(() => undefined));
    stubFetch(() => jsonResponse(200, GENERATE_OK));
    await fillAndSubmit('my confidential prompt');
    await screen.findByText('Hello from the model');
    for (const spy of spies) {
      const logged = JSON.stringify(spy.mock.calls);
      expect(logged).not.toContain('my confidential prompt');
      expect(logged).not.toContain('Hello from the model');
      spy.mockRestore();
    }
  });
});
