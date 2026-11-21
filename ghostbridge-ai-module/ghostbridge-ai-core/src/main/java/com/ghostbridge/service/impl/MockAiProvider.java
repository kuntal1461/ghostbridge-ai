package com.ghostbridge.service.impl;

import com.ghostbridge.config.AiProperties;
import com.ghostbridge.constants.GhostBridgeAiLiterals;
import com.ghostbridge.dto.AiProviderCall;
import com.ghostbridge.dto.AiProviderResult;
import com.ghostbridge.dto.AiProviderStatus;
import com.ghostbridge.dto.AiUsage;
import com.ghostbridge.service.AiProvider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Deterministic provider for local development and tests: same call, same output, no network, no credentials.
 * Registered only when {@code ghostbridge.ai.provider=mock}; rejected at startup in {@code prod}.
 * Reported token counts are simulated (about four characters per token) and exist only for this provider.
 */
public class MockAiProvider implements AiProvider {
    private final AiProperties.Mock settings;

    public MockAiProvider(AiProperties.Mock settings) {
        this.settings = settings;
    }

    @Override
    public String name() {
        return GhostBridgeAiLiterals.PROVIDER_MOCK;
    }

    @Override
    public AiProviderResult invoke(AiProviderCall call) {
        String digest = digest(call);
        String text = outputFor(call, digest);
        AiUsage usage = new AiUsage(tokens(call.system().length() + call.user().length()), tokens(text.length()), null);
        return new AiProviderResult(text, usage);
    }

    @Override
    public AiProviderStatus status() {
        return new AiProviderStatus(true, GhostBridgeAiLiterals.MOCK_STATUS_DETAIL);
    }

    private String outputFor(AiProviderCall call, String digest) {
        if (settings.response() != null) {
            return settings.response();
        }
        return call.jsonOutput() ? GhostBridgeAiLiterals.MOCK_JSON_PREFIX + digest + GhostBridgeAiLiterals.MOCK_JSON_SUFFIX : GhostBridgeAiLiterals.MOCK_TEXT_PREFIX + digest;
    }

    private static int tokens(int characters) {
        return (characters + GhostBridgeAiLiterals.MOCK_CHARS_PER_TOKEN - 1) / GhostBridgeAiLiterals.MOCK_CHARS_PER_TOKEN;
    }

    private static String digest(AiProviderCall call) {
        try {
            MessageDigest sha = MessageDigest.getInstance(GhostBridgeAiLiterals.MOCK_DIGEST_ALGORITHM);
            String material = call.model() + '\u0000' + call.system() + '\u0000' + call.user();
            return HexFormat.of().formatHex(sha.digest(material.getBytes(StandardCharsets.UTF_8))).substring(0, GhostBridgeAiLiterals.MOCK_DIGEST_CHARS);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(GhostBridgeAiLiterals.MOCK_DIGEST_ALGORITHM + " is required by the JDK", e);
        }
    }
}
