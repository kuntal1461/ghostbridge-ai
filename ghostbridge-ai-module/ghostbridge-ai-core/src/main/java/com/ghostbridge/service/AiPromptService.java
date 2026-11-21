package com.ghostbridge.service;

import com.ghostbridge.dto.AiRenderedPrompt;

import java.util.Map;
import java.util.Set;

/** Resolves and safely renders named, versioned prompt templates. */
public interface AiPromptService {

    /**
     * Renders a prompt: variables and untrusted content are sanitized and delimited as data in the user part only.
     *
     * @param version null selects the latest version
     * @throws com.ghostbridge.exception.AiException INVALID_INPUT on unknown prompt/version or variable problems
     */
    AiRenderedPrompt render(String name, Integer version, Map<String, String> variables, String untrustedContent);

    /** Names of all registered prompts (an immutable set). */
    Set<String> promptNames();
}
