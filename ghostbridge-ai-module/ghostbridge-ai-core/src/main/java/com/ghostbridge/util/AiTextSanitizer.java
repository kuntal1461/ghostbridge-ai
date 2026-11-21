package com.ghostbridge.util;

import com.ghostbridge.constants.GhostBridgeAiLiterals;

import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Prepares untrusted text for prompts: strips control characters, neutralizes look-alike forms of the
 * untrusted-content delimiter so input cannot easily close its own data block, and applies operator-configured
 * redaction patterns.
 *
 * <p><strong>This is a defence-in-depth layer, not a prompt-injection fix.</strong> Models can still be influenced by
 * the content itself, and look-alikes outside Unicode compatibility normalization (for example Cyrillic homoglyphs)
 * are not detected. The controls that matter are treating all external content as data, giving the model no tools or
 * capabilities, validating output against a schema and never executing or applying generated output.
 * Redaction is best-effort: it runs on the text as submitted (or on the canonical copy when a delimiter look-alike was
 * found), so look-alike characters such as fullwidth digits can evade a pattern. It is never a substitute for not
 * sending sensitive data.
 */
public final class AiTextSanitizer {
    private final List<Pattern> redactions;

    public AiTextSanitizer(List<String> redactionPatterns) {
        this.redactions = redactionPatterns.stream().map(Pattern::compile).toList();
    }

    /** Returns a prompt-safe copy of {@code text}; null becomes the empty string. */
    public String clean(String text) {
        if (text == null) {
            return "";
        }
        String cleaned = GhostBridgeAiLiterals.CONTROL_CHARS.matcher(text).replaceAll("");
        cleaned = neutralizeDelimiters(cleaned);
        for (Pattern redaction : redactions) {
            cleaned = redaction.matcher(cleaned).replaceAll(GhostBridgeAiLiterals.REDACTED_MARKER);
        }
        return cleaned;
    }

    /** Wraps sanitized text in the untrusted-content delimiters. */
    public String wrapUntrusted(String text) {
        return GhostBridgeAiLiterals.UNTRUSTED_OPEN + "\n" + clean(text) + "\n" + GhostBridgeAiLiterals.UNTRUSTED_CLOSE;
    }

    /**
     * Detects delimiter look-alikes on a canonical copy (NFKC, invisible format characters removed). Only when one is
     * found is the canonical, neutralized copy used, so ordinary text keeps its original characters.
     */
    private static String neutralizeDelimiters(String text) {
        String canonical = GhostBridgeAiLiterals.FORMAT_CHARS.matcher(Normalizer.normalize(text, Normalizer.Form.NFKC)).replaceAll("");
        if (!GhostBridgeAiLiterals.UNTRUSTED_DELIMITER.matcher(canonical).find()) {
            return text;
        }
        String neutralized = GhostBridgeAiLiterals.UNTRUSTED_DELIMITER.matcher(canonical)
                .replaceAll(GhostBridgeAiLiterals.UNTRUSTED_ESCAPE_REPLACEMENT);
        // NFKC can expand text (one character may become 18), which would defeat the input-length limit.
        int limit = GhostBridgeAiLiterals.MAX_CANONICAL_EXPANSION_FACTOR * text.length();
        return neutralized.length() > limit ? neutralized.substring(0, limit) : neutralized;
    }
}
