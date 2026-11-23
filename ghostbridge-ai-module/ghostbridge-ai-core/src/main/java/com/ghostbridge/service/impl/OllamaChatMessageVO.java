package com.ghostbridge.service.impl;

/** One chat message on the Ollama (OpenAI-compatible) wire format. Provider-internal; never part of a public contract. */
record OllamaChatMessageVO(String role, String content) {
}
