import type { GenerationOptions } from '../api/types';
import { utf8Bytes } from '../lib/format';

/** Client-side mirror of the backend limits (GhostBridgeAiLiterals). The backend remains the authority. */
export const LIMITS = {
  maxVariables: 32,
  variableValueBytes: 16_384,
  untrustedContentBytes: 32_768,
  maxOutputTokens: 32_768,
  promptVersion: 1_000,
  identifierLength: 128,
} as const;

export const IDENTIFIER_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$/;
export const VARIABLE_NAME_PATTERN = /^[A-Za-z][A-Za-z0-9_]{0,63}$/;

export interface VariableRow {
  id: string;
  name: string;
  value: string;
}

export interface RequestFieldsState {
  variables: VariableRow[];
  untrustedContent: string;
  maxOutputTokens: string;
  temperature: string;
}

export type FieldErrors = Record<string, string>;

let nextId = 0;
export function newVariableRow(name = '', value = ''): VariableRow {
  nextId += 1;
  return { id: `var-${nextId}`, name, value };
}

export function initialRequestFields(): RequestFieldsState {
  return { variables: [newVariableRow('text')], untrustedContent: '', maxOutputTokens: '', temperature: '' };
}

export interface ParsedRequestFields {
  variables: Record<string, string>;
  untrustedContent?: string;
  options?: GenerationOptions;
}

export function validateRequestFields(fields: RequestFieldsState): { errors: FieldErrors; parsed: ParsedRequestFields } {
  const errors: FieldErrors = {};
  const variables: Record<string, string> = {};

  if (fields.variables.length > LIMITS.maxVariables) errors.variables = `At most ${LIMITS.maxVariables} variables are allowed.`;
  const seen = new Set<string>();
  for (const row of fields.variables) {
    if (row.name === '' && row.value === '') continue;
    if (!VARIABLE_NAME_PATTERN.test(row.name)) {
      errors[`${row.id}:name`] = 'Use letters, digits and underscores; start with a letter (max 64).';
    } else if (seen.has(row.name)) {
      errors[`${row.id}:name`] = 'Variable names must be unique.';
    }
    if (utf8Bytes(row.value) > LIMITS.variableValueBytes) {
      errors[`${row.id}:value`] = `Value is larger than ${LIMITS.variableValueBytes} bytes.`;
    }
    seen.add(row.name);
    variables[row.name] = row.value;
  }

  if (utf8Bytes(fields.untrustedContent) > LIMITS.untrustedContentBytes) {
    errors.untrustedContent = `Content is larger than ${LIMITS.untrustedContentBytes} bytes.`;
  }

  const options: GenerationOptions = {};
  if (fields.maxOutputTokens.trim() !== '') {
    const tokens = Number(fields.maxOutputTokens);
    if (!Number.isInteger(tokens) || tokens < 1 || tokens > LIMITS.maxOutputTokens) {
      errors.maxOutputTokens = `Enter a whole number from 1 to ${LIMITS.maxOutputTokens}.`;
    } else options.maxOutputTokens = tokens;
  }
  if (fields.temperature.trim() !== '') {
    const temperature = Number(fields.temperature);
    if (!Number.isFinite(temperature) || temperature < 0 || temperature > 1) errors.temperature = 'Enter a number from 0 to 1.';
    else options.temperature = temperature;
  }

  return {
    errors,
    parsed: {
      variables,
      untrustedContent: fields.untrustedContent === '' ? undefined : fields.untrustedContent,
      options: Object.keys(options).length > 0 ? options : undefined,
    },
  };
}
