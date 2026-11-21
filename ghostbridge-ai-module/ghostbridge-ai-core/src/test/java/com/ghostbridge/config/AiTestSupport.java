package com.ghostbridge.config;

import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.HashMap;
import java.util.Map;

/** Builds {@link AiProperties} the way Spring does (binding with defaults), without starting a context. */
public final class AiTestSupport {
    private AiTestSupport() {
    }

    public static AiProperties properties(String... keyValues) {
        Map<String, String> source = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            source.put("ghostbridge.ai." + keyValues[i], keyValues[i + 1]);
        }
        return new Binder(new MapConfigurationPropertySource(source))
                .bindOrCreate("ghostbridge.ai", AiProperties.class);
    }
}
