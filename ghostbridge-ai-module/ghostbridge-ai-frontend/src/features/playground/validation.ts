import type { GenerateRequest } from '../../api/types';
import { IDENTIFIER_PATTERN, LIMITS, validateRequestFields, type FieldErrors, type RequestFieldsState } from '../requestFields';

export interface PlaygroundForm {
  promptName: string;
  promptVersion: string;
  modelId: string;
  fields: RequestFieldsState;
}

export function validatePlayground(form: PlaygroundForm): { errors: FieldErrors; request?: GenerateRequest } {
  const { errors, parsed } = validateRequestFields(form.fields);
  const promptName = form.promptName.trim();
  if (!IDENTIFIER_PATTERN.test(promptName)) {
    errors.promptName = 'Enter a template name: letters, digits, dot, underscore or dash (max 128).';
  }

  let promptVersion: number | undefined;
  if (form.promptVersion.trim() !== '') {
    const version = Number(form.promptVersion);
    if (!Number.isInteger(version) || version < 1 || version > LIMITS.promptVersion) {
      errors.promptVersion = `Enter a whole number from 1 to ${LIMITS.promptVersion}.`;
    } else promptVersion = version;
  }

  const modelId = form.modelId.trim();
  if (modelId.length > LIMITS.identifierLength) errors.modelId = `Model id is longer than ${LIMITS.identifierLength} characters.`;

  if (Object.keys(errors).length > 0) return { errors };
  return {
    errors,
    request: {
      promptName,
      promptVersion,
      variables: Object.keys(parsed.variables).length > 0 ? parsed.variables : undefined,
      untrustedContent: parsed.untrustedContent,
      modelId: modelId === '' ? undefined : modelId,
      options: parsed.options,
    },
  };
}
