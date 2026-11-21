package com.ghostbridge.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostbridge.constants.GhostBridgeAiLiterals;
import com.ghostbridge.dto.AiRenderedPrompt;
import com.ghostbridge.exception.AiErrorReason;
import com.ghostbridge.exception.AiException;
import com.ghostbridge.service.AiPromptService;
import com.ghostbridge.service.AiStructuredOutputService;
import com.ghostbridge.util.AiTextSanitizer;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;

/**
 * Loads prompt templates from {@code classpath*:prompts/*&#47;*.json} once at startup and fails fast on malformed
 * templates. Variables are sanitized and rendered, wrapped in the untrusted-data delimiters, only into the user part;
 * system text is never templated. Input length limits are enforced by the coordinator, not here.
 */
public class ClasspathAiPromptService implements AiPromptService {
    /** Template file model. */
    record PromptDescriptor(String name, int version, String description, String system, String user, List<String> variables, String schema) {
        PromptDescriptor {
            variables = variables == null ? List.of() : List.copyOf(variables);
        }
    }

    private final Map<String, TreeMap<Integer, PromptDescriptor>> prompts;
    private final AiStructuredOutputService structuredOutput;
    private final AiTextSanitizer sanitizer;
    private final Set<String> names;

    public ClasspathAiPromptService(ObjectMapper mapper, AiStructuredOutputService structuredOutput, AiTextSanitizer sanitizer) {
        this(mapper, structuredOutput, sanitizer, GhostBridgeAiLiterals.PROMPT_RESOURCE_PATTERN);
    }

    /** @param locationPattern classpath pattern for prompt {@code *.json} files (injectable for tests) */
    public ClasspathAiPromptService(ObjectMapper mapper, AiStructuredOutputService structuredOutput,
                                    AiTextSanitizer sanitizer, String locationPattern) {
        this.structuredOutput = structuredOutput;
        this.sanitizer = sanitizer;
        this.prompts = load(mapper, locationPattern);
        this.names = Set.copyOf(prompts.keySet());
    }

    @Override
    public Set<String> promptNames() {
        return names;
    }

    @Override
    public AiRenderedPrompt render(String name, Integer version, Map<String, String> variables, String untrustedContent) {
        PromptDescriptor prompt = find(name, version);
        Map<String, String> values = checkedVariables(prompt, variables);
        Matcher matcher = GhostBridgeAiLiterals.PROMPT_PLACEHOLDER.matcher(prompt.user());
        String user = matcher.replaceAll(m -> Matcher.quoteReplacement(sanitizer.wrapUntrusted(values.get(m.group(1)))));
        if (untrustedContent != null && !untrustedContent.isBlank()) {
            user = user + GhostBridgeAiLiterals.BLOCK_SEPARATOR + sanitizer.wrapUntrusted(untrustedContent);
        }
        return new AiRenderedPrompt(prompt.name(), prompt.version(), systemText(prompt), user, prompt.schema());
    }

    private String systemText(PromptDescriptor prompt) {
        String system = prompt.system() + GhostBridgeAiLiterals.TRUST_RULES;
        return prompt.schema() == null ? system : system + GhostBridgeAiLiterals.JSON_OUTPUT_RULES + structuredOutput.schemaText(prompt.schema());
    }

    private PromptDescriptor find(String name, Integer version) {
        TreeMap<Integer, PromptDescriptor> versions = name == null ? null : prompts.get(name);
        if (versions == null) {
            throw new AiException(AiErrorReason.INVALID_INPUT, "Unknown prompt");
        }
        PromptDescriptor prompt = version == null ? versions.lastEntry().getValue() : versions.get(version);
        if (prompt == null) {
            throw new AiException(AiErrorReason.INVALID_INPUT, "Unknown prompt version");
        }
        return prompt;
    }

    private Map<String, String> checkedVariables(PromptDescriptor prompt, Map<String, String> variables) {
        for (String declared : prompt.variables()) {
            String value = variables.get(declared);
            if (value == null) {
                throw new AiException(AiErrorReason.INVALID_INPUT, "Missing template variable: " + declared);
            }
        }
        if (!prompt.variables().containsAll(variables.keySet())) {
            throw new AiException(AiErrorReason.INVALID_INPUT, "Unknown template variable supplied");
        }
        return variables;
    }

    private Map<String, TreeMap<Integer, PromptDescriptor>> load(ObjectMapper mapper, String locationPattern) {
        Map<String, TreeMap<Integer, PromptDescriptor>> loaded = new HashMap<>();
        try {
            for (Resource resource : new PathMatchingResourcePatternResolver().getResources(locationPattern)) {
                PromptDescriptor descriptor = read(mapper, resource);
                verify(descriptor);
                if (loaded.computeIfAbsent(descriptor.name(), k -> new TreeMap<>()).put(descriptor.version(), descriptor) != null) {
                    throw new IllegalStateException("Duplicate prompt " + descriptor.name() + " v" + descriptor.version());
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot scan prompt templates", e);
        }
        return Map.copyOf(loaded);
    }

    private static PromptDescriptor read(ObjectMapper mapper, Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            return mapper.readValue(in, PromptDescriptor.class);
        } catch (IOException e) {
            throw new IllegalStateException("Invalid prompt template " + resource.getFilename(), e);
        }
    }

    private void verify(PromptDescriptor d) {
        String id = "Prompt " + d.name() + " v" + d.version();
        require(d.name() != null && !d.name().isBlank() && d.version() > 0, id + ": name and positive version required");
        require(d.system() != null && !d.system().isBlank() && !GhostBridgeAiLiterals.PROMPT_PLACEHOLDER.matcher(d.system()).find(),
                id + ": system text is required and must not contain placeholders");
        require(d.user() != null && !d.user().isBlank(), id + ": user template is required");
        Set<String> used = new HashSet<>();
        GhostBridgeAiLiterals.PROMPT_PLACEHOLDER.matcher(d.user()).results().forEach(r -> used.add(r.group(1)));
        require(used.equals(Set.copyOf(d.variables())), id + ": declared variables must match template placeholders");
        if (d.schema() != null) {
            structuredOutput.schemaText(d.schema());
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
