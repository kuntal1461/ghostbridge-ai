package com.ghostbridge.service.impl;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.ghostbridge.constants.GhostBridgeAiLiterals;
import com.ghostbridge.exception.AiErrorReason;
import com.ghostbridge.exception.AiException;
import com.ghostbridge.service.AiStructuredOutputService;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Parses model output strictly (one JSON value, no trailing content, no duplicate keys) and validates it against JSON
 * Schemas loaded from the classpath. Duplicate schema names fail startup, as do schemas that use a non-standard
 * dialect, any {@code $id} or a non-local {@code $ref}/{@code $dynamicRef} (so validation never fetches remote documents).
 */
public class JsonSchemaAiStructuredOutputService implements AiStructuredOutputService {
    private final ObjectReader strictReader;
    private final ObjectMapper mapper;
    private final Map<String, JsonSchema> schemas = new HashMap<>();
    private final Map<String, String> schemaTexts = new HashMap<>();

    public JsonSchemaAiStructuredOutputService(ObjectMapper mapper) {
        this(mapper, GhostBridgeAiLiterals.SCHEMA_RESOURCE_PATTERN);
    }

    /** @param locationPattern classpath pattern for {@code *.schema.json} files (injectable for tests) */
    public JsonSchemaAiStructuredOutputService(ObjectMapper mapper, String locationPattern) {
        this.mapper = mapper;
        this.strictReader = mapper.copy()
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .readerFor(JsonNode.class)
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        try {
            for (Resource resource : new PathMatchingResourcePatternResolver().getResources(locationPattern)) {
                register(factory, resource);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot scan AI schemas", e);
        }
    }

    private void register(JsonSchemaFactory factory, Resource resource) {
        String file = String.valueOf(resource.getFilename());
        String name = file.substring(0, file.length() - GhostBridgeAiLiterals.SCHEMA_FILE_SUFFIX.length());
        if (schemas.containsKey(name)) {
            throw new IllegalStateException("Duplicate AI schema name: " + name);
        }
        try (InputStream in = resource.getInputStream()) {
            JsonNode node = mapper.readTree(in);
            String text = mapper.writeValueAsString(node);
            requireLocalOnly(node, file);
            schemas.put(name, factory.getSchema(node));
            schemaTexts.put(name, text);
        } catch (IOException e) {
            throw new IllegalStateException("Invalid AI schema " + file, e);
        }
    }

    /** Rejects any schema keyword that could make validation fetch a remote document (remote refs, ids, dialects). */
    private static void requireLocalOnly(JsonNode node, String file) {
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                if (!isLocalKeywordValue(entry.getKey(), entry.getValue())) {
                    throw new IllegalStateException("AI schema must not reference remote documents: " + file);
                }
                requireLocalOnly(entry.getValue(), file);
            });
        } else if (node.isArray()) {
            node.forEach(child -> requireLocalOnly(child, file));
        }
    }

    private static boolean isLocalKeywordValue(String keyword, JsonNode value) {
        if (!value.isTextual()) {
            return true;
        }
        String text = value.asText();
        return switch (keyword) {
            case GhostBridgeAiLiterals.SCHEMA_KEY_REF, GhostBridgeAiLiterals.SCHEMA_KEY_DYNAMIC_REF ->
                    text.startsWith(GhostBridgeAiLiterals.LOCAL_REF_PREFIX);
            case GhostBridgeAiLiterals.SCHEMA_KEY_SCHEMA -> GhostBridgeAiLiterals.SCHEMA_DIALECT_URI.equals(text);
            case GhostBridgeAiLiterals.SCHEMA_KEY_ID -> false; // the library requires an absolute $id, which would define a remote base
            default -> true;
        };
    }

    @Override
    public String schemaText(String schemaName) {
        String text = schemaTexts.get(schemaName);
        if (text == null) {
            throw new AiException(AiErrorReason.MISSING_CONFIGURATION, "Output schema is not available");
        }
        return text;
    }

    @Override
    public JsonNode parseAndValidate(String modelOutput, String schemaName) {
        schemaText(schemaName);
        JsonNode node = parse(modelOutput);
        Set<ValidationMessage> errors = schemas.get(schemaName).validate(node);
        if (!errors.isEmpty()) {
            throw new AiException(AiErrorReason.OUTPUT_VALIDATION_FAILED,
                    "Model output failed schema validation at schema paths: " + describe(errors));
        }
        return node;
    }

    /** The Jackson cause is deliberately not attached: parser messages can quote model output. */
    private JsonNode parse(String modelOutput) {
        try {
            JsonNode node = strictReader.readTree(stripFence(modelOutput));
            if (node == null || node.isMissingNode() || node.isNull()) {
                throw new AiException(AiErrorReason.INVALID_MODEL_RESPONSE, "Model output was empty");
            }
            return node;
        } catch (JsonProcessingException e) {
            throw new AiException(AiErrorReason.INVALID_MODEL_RESPONSE, "Model output was not a single valid JSON value");
        }
    }

    /** Reports schema evaluation paths and keyword types only: never instance values or model-chosen property names. */
    private static String describe(Set<ValidationMessage> errors) {
        return String.join(", ", errors.stream()
                .map(e -> e.getEvaluationPath() + " (" + e.getType() + ")")
                .sorted()
                .limit(GhostBridgeAiLiterals.MAX_REPORTED_SCHEMA_ERRORS)
                .toList());
    }

    /** Unwraps a single markdown code fence that spans the whole output; any other text is left for the strict parser. */
    private static String stripFence(String text) {
        String trimmed = text == null ? "" : text.trim();
        String fence = GhostBridgeAiLiterals.MARKDOWN_FENCE;
        if (trimmed.startsWith(fence) && trimmed.endsWith(fence) && trimmed.length() > 2 * fence.length()) {
            int firstNewline = trimmed.indexOf('\n');
            int closing = trimmed.length() - fence.length();
            if (firstNewline > 0 && firstNewline < closing) {
                return trimmed.substring(firstNewline + 1, closing).trim();
            }
        }
        return trimmed;
    }
}
