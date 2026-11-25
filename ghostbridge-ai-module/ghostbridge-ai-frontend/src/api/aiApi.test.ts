import { afterEach, describe, expect, it, vi } from 'vitest';
import { generate, generateStructured, getProviderStatus, getUsage } from './aiApi';
import { ApiError } from './errors';
import { GENERATE_OK, STATUS_OK, STRUCTURED_OK, USAGE_OK, jsonResponse, stubFetch } from '../test/http';

afterEach(() => vi.unstubAllGlobals());

async function kindOf(promise: Promise<unknown>): Promise<string> {
  try {
    await promise;
  } catch (error) {
    return (error as ApiError).kind;
  }
  return 'no-error';
}

describe('request contracts', () => {
  it('POSTs AiGenerateRequestVO fields to /api/v1/ai/generate', async () => {
    const stub = stubFetch(() => jsonResponse(200, GENERATE_OK));
    const request = { promptName: 'generic-structured', variables: { text: 'hi' }, options: { temperature: 0.2 } };
    const response = await generate(request);
    expect(stub.mock.calls[0][0]).toBe('/api/v1/ai/generate');
    expect(JSON.parse(String(stub.mock.calls[0][1].body))).toEqual(request);
    expect(response.text).toBe('Hello from the model');
    expect(response.usage?.totalTokens).toBe(30);
  });

  it('POSTs AiStructuredGenerateRequestVO to /api/v1/ai/generate/structured', async () => {
    const stub = stubFetch(() => jsonResponse(200, STRUCTURED_OK));
    const response = await generateStructured({ schemaName: 'generic-summary', variables: { text: 'hi' } });
    expect(stub.mock.calls[0][0]).toBe('/api/v1/ai/generate/structured');
    expect(response.structured).toEqual(STRUCTURED_OK.structured);
  });

  it('GETs status and usage', async () => {
    const stub = stubFetch((url) => jsonResponse(200, url.endsWith('usage') ? USAGE_OK : STATUS_OK));
    expect((await getProviderStatus()).capabilities).toHaveLength(2);
    expect((await getUsage()).rateLimit?.remaining).toBe(18);
    expect(stub.mock.calls.map((c) => c[1].method)).toEqual(['GET', 'GET']);
  });
});

describe('response guards (missing or ill-typed fields)', () => {
  it.each([
    ['generate without text', () => generate({ promptName: 'p' }), { ...GENERATE_OK, text: undefined }],
    ['generate with numeric model', () => generate({ promptName: 'p' }), { ...GENERATE_OK, model: 5 }],
    ['generate without durationMs', () => generate({ promptName: 'p' }), { ...GENERATE_OK, durationMs: undefined }],
    ['structured without structured', () => generateStructured({ schemaName: 's' }), { ...STRUCTURED_OK, structured: undefined }],
    ['status without configured', () => getProviderStatus(), { ...STATUS_OK, configured: undefined }],
    ['status with bad capabilities', () => getProviderStatus(), { ...STATUS_OK, capabilities: [1] }],
    ['usage without counts', () => getUsage(), { ...USAGE_OK, requestCount: undefined }],
    ['usage with string tokens', () => getUsage(), { ...USAGE_OK, totalTokens: '4600' }],
    ['array body', () => getUsage(), []],
    ['null body', () => getUsage(), null],
  ])('%s is invalid-response', async (_name, run, body) => {
    stubFetch(() => jsonResponse(200, body));
    expect(await kindOf(run())).toBe('invalid-response');
  });

  it('tolerates omitted optional fields (provider, model, capabilities, usage, rateLimit)', async () => {
    stubFetch((url) => jsonResponse(200, url.endsWith('usage') ? { ...USAGE_OK, rateLimit: undefined } : { configured: false, status: 'NOT_CONFIGURED' }));
    const status = await getProviderStatus();
    expect(status).toMatchObject({ configured: false, capabilities: [] });
    expect(status.provider).toBeUndefined();
    expect((await getUsage()).rateLimit).toBeUndefined();
  });
});
