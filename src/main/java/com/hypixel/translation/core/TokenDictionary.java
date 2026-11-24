package com.hypixel.translation.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Thread-safe central glossary managing tokens with recursive template resolution,
 * cycle detection, depth bounds, and formatting preservation.
 */
public class TokenDictionary {
    private static final int MAX_RECURSION_DEPTH = 5;
    private static final int MAX_CACHE_SIZE = 4096;
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\{([A-Za-z0-9_]+)\\}");
    private static final String END_ISLAND = "The End";
    private static final Pattern END_OF_SOMETHING = Pattern.compile("(?i)(?:\\s|§[0-9a-fk-or])+of\\b");
    private static final Map<String, TokenDictionary> BUNDLED_CACHE = new ConcurrentHashMap<>();

    private final Map<String, TokenRecord> tokens = new ConcurrentHashMap<>();
    private final Map<String, String> resolutionCache = new ConcurrentHashMap<>();
    private volatile List<TokenRecord> sortedByEnCache = null;
    private volatile TrieNode trieRootCache = null;
    private final AtomicLong version = new AtomicLong(0);
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private static final class TrieNode {
        final Map<Character, TrieNode> children = new HashMap<>(4);
        String resolvedZh;
        boolean startsWithAlnum;
        boolean endsWithAlnum;
        int categoryWeight = -1;
    }

    public TokenDictionary() {}

    /**
     * Registers or updates a token record.
     * Invalidates cached string resolutions.
     */
    public void registerToken(TokenRecord record) {
        if (record != null && record.tokenId() != null && !record.tokenId().isBlank()) {
            tokens.put(record.tokenId(), record);
            clearCache();
        }
    }

    /**
     * Registers multiple token records in batch.
     */
    public void registerTokens(Collection<TokenRecord> records) {
        if (records != null) {
            for (TokenRecord r : records) {
                if (r != null && r.tokenId() != null && !r.tokenId().isBlank()) {
                    tokens.put(r.tokenId(), r);
                }
            }
            clearCache();
        }
    }

    public TokenRecord get(String tokenId) {
        return tokens.get(tokenId);
    }

    public boolean containsToken(String tokenId) {
        return tokens.containsKey(tokenId);
    }

    public TokenRecord removeToken(String tokenId) {
        TokenRecord removed = tokens.remove(tokenId);
        if (removed != null) {
            clearCache();
        }
        return removed;
    }

    public int size() {
        return tokens.size();
    }

    public Map<String, TokenRecord> getTokens() {
        return Collections.unmodifiableMap(tokens);
    }

    /**
     * Fast resolution for a single token ID without regex overhead.
     * Returns Chinese translation or "{TOKEN_ID}" if uncataloged.
     */
    public String resolveToken(String tokenId) {
        if (tokenId == null) return null;
        TokenRecord r = tokens.get(tokenId);
        return r != null ? r.zh_tw() : "{" + tokenId + "}";
    }

    /**
     * Resolves all token placeholders in a template string recursively.
     * Guards against circular loops and caps recursion depth at 5.
     */
    public String resolveString(String template) {
        if (template == null) return null;
        if (template.isEmpty()) return "";

        if (!template.contains("{") || !template.contains("}")) {
            return template;
        }

        String cached = resolutionCache.get(template);
        if (cached != null) {
            return cached;
        }

        String resolved = resolveStringRecursive(template, new HashSet<>(), 0);
        if (resolutionCache.size() < MAX_CACHE_SIZE) {
            resolutionCache.put(template, resolved);
        }
        return resolved;
    }

    /**
     * Translates plain English text by replacing longest matching English token phrases
     * with their Traditional Chinese translations.
     */
    /**
     * Translates any plain-text occurrences of registered English token terms using a single-pass
     * longest-match Trie with ASCII word-boundary guards (respecting adjacent '§' color codes).
     * Cross-referenced '{TOKEN_ID}' expressions in zh_tw templates are resolved dynamically.
     */
    public String translateText(String text) {
        if (text == null || text.isEmpty()) return text;
        TrieNode root = getOrBuildTrie();
        int len = text.length();
        StringBuilder sb = null;
        int i = 0;

        while (i < len) {
            TrieNode node = root.children.get(text.charAt(i));
            if (node != null) {
                boolean leftOk = isLeftWordBoundary(text, i);
                int bestLen = -1;
                String bestZh = null;
                int j = i;
                while (node != null) {
                    if (node.resolvedZh != null
                            && (!node.startsWithAlnum || leftOk)
                            && (!node.endsWithAlnum || isRightWordBoundary(text, j + 1))
                            && !isEndOfSomething(text, i, j + 1)) {
                        bestLen = j + 1 - i;
                        bestZh = node.resolvedZh;
                    }
                    j++;
                    if (j >= len) break;
                    node = node.children.get(text.charAt(j));
                }
                if (bestLen > 0) {
                    if (sb == null) {
                        sb = new StringBuilder(len + 16);
                        sb.append(text, 0, i);
                    }
                    sb.append(bestZh);
                    i += bestLen;
                    continue;
                }
            }
            if (sb != null) {
                sb.append(text.charAt(i));
            }
            i++;
        }
        return sb != null ? sb.toString() : text;
    }

    private TrieNode getOrBuildTrie() {
        TrieNode root = trieRootCache;
        if (root != null) {
            return root;
        }
        TrieNode built = new TrieNode();
        for (TokenRecord token : tokens.values()) {
            if ("dialogue".equals(token.category())) continue;
            String en = token.en();
            if (en == null || en.isEmpty()) continue;
            // Avoid matching bare 1-character ASCII letters that would corrupt abbreviations or Roman numerals
            if (en.length() == 1 && isAsciiAlnum(en.charAt(0))) continue;

            String resolved = resolveString(token.zh_tw());
            if (resolved == null) resolved = "";
            if (!resolved.isEmpty() && resolved.equals(en)) continue;

            TrieNode cur = built;
            for (int k = 0; k < en.length(); k++) {
                cur = cur.children.computeIfAbsent(en.charAt(k), c -> new TrieNode());
            }
            int weight = categoryPriority(token.category(), resolved);
            if (cur.resolvedZh == null || weight >= cur.categoryWeight) {
                cur.resolvedZh = resolved;
                cur.startsWithAlnum = isAsciiAlnum(en.charAt(0));
                cur.endsWithAlnum = isAsciiAlnum(en.charAt(en.length() - 1));
                cur.categoryWeight = weight;
            }
        }
        trieRootCache = built;
        return built;
    }

    private static int categoryPriority(String category, String resolved) {
        if (resolved == null || resolved.isEmpty()) return 0;
        if (category == null) return 1;
        return switch (category) {
            case "ui", "stat", "ability", "enchant", "skill", "location", "mayor", "npc", "boss" -> 3;
            case "item", "collection", "category", "rarity", "reforge" -> 2;
            default -> 1;
        };
    }

    private static boolean isEndOfSomething(String text, int start, int end) {
        return end - start == END_ISLAND.length()
                && text.regionMatches(start, END_ISLAND, 0, END_ISLAND.length())
                && END_OF_SOMETHING.matcher(text).region(end, text.length()).lookingAt();
    }

    private static boolean isLeftWordBoundary(String text, int idx) {
        if (idx <= 0) return true;
        char prev = text.charAt(idx - 1);
        if (!isAsciiAlnum(prev)) return true;
        return idx >= 2 && text.charAt(idx - 2) == '§' && isFormatCode(prev);
    }

    private static boolean isRightWordBoundary(String text, int endIdx) {
        if (endIdx >= text.length()) return true;
        return !isAsciiAlnum(text.charAt(endIdx));
    }

    private static boolean isAsciiAlnum(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
    }

    private static boolean isFormatCode(char c) {
        return (c >= '0' && c <= '9')
                || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')
                || (c >= 'k' && c <= 'o') || (c >= 'K' && c <= 'O')
                || c == 'r' || c == 'R';
    }

    private String resolveStringRecursive(String template, Set<String> visited, int depth) {
        if (template == null) return null;
        if (template.isEmpty()) return "";
        if (depth > MAX_RECURSION_DEPTH) return template;

        Matcher matcher = TOKEN_PATTERN.matcher(template);
        if (!matcher.find()) {
            return template;
        }

        matcher.reset();
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = null;

            if (key.endsWith("_GLYPH")) {
                String base = key.substring(0, key.length() - 6);
                TokenRecord r = tokens.get(base);
                replacement = (r != null) ? r.glyph() : matcher.group(0);
            } else if (key.endsWith("_COLOR")) {
                String base = key.substring(0, key.length() - 6);
                TokenRecord r = tokens.get(base);
                replacement = (r != null) ? r.color() : matcher.group(0);
            } else if (key.endsWith("_EN")) {
                String base = key.substring(0, key.length() - 3);
                TokenRecord r = tokens.get(base);
                replacement = (r != null) ? r.en() : matcher.group(0);
            } else {
                TokenRecord r = tokens.get(key);
                if (r != null) {
                    if (visited.contains(key)) {
                        replacement = r.zh_tw(); // Break cycle, don't recurse
                    } else {
                        visited.add(key);
                        replacement = resolveStringRecursive(r.zh_tw(), visited, depth + 1);
                        visited.remove(key);
                    }
                } else {
                    replacement = matcher.group(0);
                }
            }

            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement != null ? replacement : ""));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public void clearCache() {
        resolutionCache.clear();
        sortedByEnCache = null;
        trieRootCache = null;
        version.incrementAndGet();
    }

    public long getVersion() {
        return version.get();
    }

    public void clear() {
        tokens.clear();
        clearCache();
    }

    public void loadFromResource(String resourcePath) throws IOException {
        if (BundledResourceCache.isBundledPath(resourcePath)) {
            TokenDictionary snapshot = BundledResourceCache.getOrLoad(BUNDLED_CACHE, resourcePath, () -> {
                TokenDictionary loaded = new TokenDictionary();
                loaded.loadFromJson(BundledResourceCache.readResourceUtf8(TokenDictionary.class, resourcePath));
                return loaded;
            });
            copyFrom(snapshot);
            return;
        }
        loadFromJson(BundledResourceCache.readResourceUtf8(getClass(), resourcePath));
    }

    private void copyFrom(TokenDictionary src) {
        if (src == null || src.tokens.isEmpty()) return;
        tokens.putAll(src.tokens);
        clearCache();
    }

    public void loadFromFile(Path path) throws IOException {
        loadFromJson(Files.readString(path, StandardCharsets.UTF_8));
    }

    public void loadFromJson(String json) {
        if (json == null || json.isBlank()) return;
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonObject tokObj = root.has("tokens") ? root.getAsJsonObject("tokens") : root;
        for (Map.Entry<String, JsonElement> e : tokObj.entrySet()) {
            if (!e.getValue().isJsonObject()) continue;
            JsonObject o = e.getValue().getAsJsonObject();
            String id = e.getKey();
            String en = o.has("en") ? o.get("en").getAsString() : "";
            String zh = o.has("zh_tw") ? o.get("zh_tw").getAsString() : "";
            String glyph = o.has("glyph") ? o.get("glyph").getAsString() : "";
            String color = o.has("color") ? o.get("color").getAsString() : "";
            String category = o.has("category") ? o.get("category").getAsString() : "general";
            registerToken(new TokenRecord(id, en, zh, glyph, color, category));
        }
        clearCache();
    }

    public void exportToFile(Path path) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, exportToJson(), StandardCharsets.UTF_8);
    }

    public String exportToJson() {
        JsonObject root = new JsonObject();
        JsonObject tokObj = new JsonObject();
        for (Map.Entry<String, TokenRecord> e : tokens.entrySet()) {
            JsonObject o = new JsonObject();
            TokenRecord r = e.getValue();
            o.addProperty("tokenId", r.tokenId());
            o.addProperty("en", r.en());
            o.addProperty("zh_tw", r.zh_tw());
            o.addProperty("glyph", r.glyph());
            o.addProperty("color", r.color());
            o.addProperty("category", r.category());
            tokObj.add(e.getKey(), o);
        }
        root.add("tokens", tokObj);
        return gson.toJson(root);
    }
}
