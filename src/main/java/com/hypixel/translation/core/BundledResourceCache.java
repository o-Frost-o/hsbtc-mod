package com.hypixel.translation.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Package-private helper for caching immutable bundled dictionary snapshots keyed by
 * classpath resource path so repeated reloads avoid re-parsing ~11 MB of static JSON
 * and recompiling ~2,800 regexes.
 */
final class BundledResourceCache {
    static final String BUNDLED_PREFIX = "/assets/hsbtr/dictionaries/";

    @FunctionalInterface
    interface IoSupplier<T> {
        T get() throws IOException;
    }

    private BundledResourceCache() {}

    static boolean isBundledPath(String resourcePath) {
        return resourcePath != null && resourcePath.startsWith(BUNDLED_PREFIX);
    }

    static String readResourceUtf8(Class<?> owner, String resourcePath) throws IOException {
        try (InputStream is = owner.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static <T> T getOrLoad(Map<String, T> cache, String resourcePath, IoSupplier<T> loader) throws IOException {
        try {
            return cache.computeIfAbsent(resourcePath, key -> {
                try {
                    return loader.get();
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            });
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        }
    }
}
