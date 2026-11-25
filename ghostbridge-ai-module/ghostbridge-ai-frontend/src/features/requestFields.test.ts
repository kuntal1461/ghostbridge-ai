import { describe, expect, it } from 'vitest';
import { initialRequestFields, newVariableRow, validateRequestFields } from './requestFields';
import { validatePlayground } from './playground/validation';

describe('validateRequestFields', () => {
  it('accepts the default empty form with no variables', () => {
    expect(validateRequestFields(initialRequestFields())).toMatchObject({ errors: {}, parsed: { variables: {} } });
  });

  it('rejects duplicate variable names and more than 32 variables', () => {
    const fields = initialRequestFields();
    fields.variables = [newVariableRow('a', '1'), newVariableRow('a', '2')];
    expect(Object.values(validateRequestFields(fields).errors).join(' ')).toMatch(/unique/);
    fields.variables = Array.from({ length: 33 }, (_, i) => newVariableRow(`v${i}`, 'x'));
    expect(validateRequestFields(fields).errors.variables).toMatch(/At most 32/);
  });

  it('rejects untrusted content over 32768 bytes', () => {
    const fields = { ...initialRequestFields(), untrustedContent: 'a'.repeat(32_769) };
    expect(validateRequestFields(fields).errors.untrustedContent).toBeDefined();
  });
});

describe('validatePlayground', () => {
  const form = { promptName: 'generic-structured', promptVersion: '', modelId: '', fields: initialRequestFields() };

  it('builds a request with only the provided fields', () => {
    form.fields.variables = [newVariableRow('text', 'hi')];
    expect(validatePlayground(form).request).toEqual({
      promptName: 'generic-structured',
      promptVersion: undefined,
      variables: { text: 'hi' },
      untrustedContent: undefined,
      modelId: undefined,
      options: undefined,
    });
  });

  it('rejects an over-long model id', () => {
    expect(validatePlayground({ ...form, modelId: 'm'.repeat(129) }).errors.modelId).toBeDefined();
  });
});
