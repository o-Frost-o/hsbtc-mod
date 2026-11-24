package com.hypixel.translation.core;

import com.google.gson.JsonArray;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Thread-safe dictionary for Hypixel SkyBlock NPC dialogues, interactive chat options,
 * quest lines, and system announcements.
 * Preserves Minecraft formatting codes, interactive option brackets, [NPC] prefixes,
 * and resolves {TOKEN_ID} placeholders through TokenDictionary.
 */
public class DialogueDictionary {
    private static final Pattern COLOR_STRIP_PATTERN = Pattern.compile("§[0-9a-fklmnorA-FKLMNOR]");
    private static final Pattern OUTER_FORMAT_PATTERN = Pattern.compile("^((?:\\s|§[0-9a-fklmnorA-FKLMNOR])*)(.*?)((?:\\s|§[0-9a-fk-orA-FK-OR])*)$", Pattern.DOTALL);
    private static final Pattern NPC_PREFIX_PATTERN = Pattern.compile(
            "^((?:§[0-9a-fklmnorA-FKLMNOR])*)\\[NPC\\]((?:\\s|§[0-9a-fklmnorA-FKLMNOR])+)([^:：\\n]+?)((?:§[0-9a-fklmnorA-FKLMNOR])*)([:：](?:\\s|§[0-9a-fklmnorA-FKLMNOR])*(.*))?$",
            Pattern.DOTALL
    );
    private static final Pattern BRACKET_OPTION_PATTERN = Pattern.compile("^\\[([^\\[\\]]{2,})\\]$");
    private static final Pattern NAMED_SPEAKER = Pattern.compile(
            "^(?:§[0-9a-fk-orA-FK-OR])*[\\[【](?:NPC|BOSS)[\\]】]\\s*((?:§[0-9a-fk-orA-FK-OR])*[^:：\\n]+?)([:：].*)$",
            Pattern.DOTALL);
    private static final Pattern MULTI_SPACE = Pattern.compile("\\s+");

    public static final String TOKEN_SETS = "capture_token_sets";
    private static final Map<String, DialogueDictionary> BUNDLED_CACHE = new ConcurrentHashMap<>();

    public record DialogueRule(Pattern pattern, String zhTemplate, boolean verbatim, Map<String, String> types,
                               Map<String, String> tokenSets) {}

    private final Map<String, String> exactDialogues = new ConcurrentHashMap<>();
    private final Map<String, String> exactLowerDialogues = new ConcurrentHashMap<>();
    private final Set<String> manualLowerDialogues = ConcurrentHashMap.newKeySet();
    private final List<DialogueRule> patternRules = new CopyOnWriteArrayList<>();
    private final ReviewedPatterns reviewed = new ReviewedPatterns();

    private volatile TokenDictionary tokenDictionary;
    private volatile Map<String, Map<String, String>> captureTokenSets = Map.of();
    private volatile BiFunction<String, String, String> captureLocalizer = this::resolveMappedToken;

    void setCaptureLocalizer(BiFunction<String, String, String> localizer) {
        captureLocalizer = localizer;
    }

    public DialogueDictionary() {
        this(null, null);
    }

    public DialogueDictionary(TokenDictionary tokenDictionary, ItemDictionary itemDictionary) {
        this.tokenDictionary = tokenDictionary;
    }

    public void setDictionaries(TokenDictionary tokenDictionary, ItemDictionary itemDictionary) {
        this.tokenDictionary = tokenDictionary;
    }

    public void registerDialogue(String en, String zhTw) {
        registerDialogueInternal(en, zhTw, true);
    }

    private void registerDialogueInternal(String en, String zhTw, boolean manual) {
        if (en == null || zhTw == null) return;
        String cleanEn = normalizeKey(en);
        if (cleanEn.isEmpty()) return;
        exactDialogues.put(cleanEn, zhTw);
        String lower = cleanEn.toLowerCase(Locale.ROOT);
        exactLowerDialogues.put(lower, zhTw);
        if (manual) {
            manualLowerDialogues.add(lower);
        }
    }

    public void registerPattern(String regex, String zhTemplate) {
        registerPattern(regex, zhTemplate, false);
    }

    public void registerPattern(String regex, String zhTemplate, boolean verbatim) {
        registerPattern(regex, zhTemplate, verbatim, Map.of());
    }

    void registerPattern(String regex, String zhTemplate, boolean verbatim, Map<String, String> types) {
        registerPattern(regex, zhTemplate, verbatim, types, Map.of());
    }

    private void registerPattern(String regex, String zhTemplate, boolean verbatim, Map<String, String> types,
                                 Map<String, String> tokenSets) {
        if (regex == null || regex.isBlank() || zhTemplate == null) return;
        var replacement = new DialogueRule(Pattern.compile(regex), zhTemplate, verbatim, types, tokenSets);
        for (int index = 0; index < patternRules.size(); index++) {
            if (patternRules.get(index).pattern().pattern().equals(regex)) {
                patternRules.set(index, replacement);
                return;
            }
        }
        patternRules.add(replacement);
    }

    public int size() {
        return exactDialogues.size();
    }

    public int patternCount() {
        return patternRules.size();
    }

    public void clear() {
        exactDialogues.clear();
        exactLowerDialogues.clear();
        manualLowerDialogues.clear();
        patternRules.clear();
        reviewed.clear();
        captureTokenSets = Map.of();
    }

    public void loadFromResource(String resourcePath) throws IOException {
        if (BundledResourceCache.isBundledPath(resourcePath)) {
            DialogueDictionary snapshot = BundledResourceCache.getOrLoad(BUNDLED_CACHE, resourcePath, () -> {
                DialogueDictionary loaded = new DialogueDictionary();
                loaded.loadFromJson(BundledResourceCache.readResourceUtf8(DialogueDictionary.class, resourcePath));
                return loaded;
            });
            copyFrom(snapshot);
            return;
        }
        loadFromJson(BundledResourceCache.readResourceUtf8(getClass(), resourcePath));
    }

    void copyFrom(DialogueDictionary src) {
        if (src == null) return;
        exactDialogues.putAll(src.exactDialogues);
        exactLowerDialogues.putAll(src.exactLowerDialogues);
        manualLowerDialogues.addAll(src.manualLowerDialogues);
        mergePatternRules(src.patternRules);
        reviewed.mergeFrom(src.reviewed);
        if (!src.captureTokenSets.isEmpty()) {
            if (captureTokenSets.isEmpty()) {
                captureTokenSets = src.captureTokenSets;
            } else {
                Map<String, Map<String, String>> merged = new HashMap<>(captureTokenSets);
                src.captureTokenSets.forEach((setKey, map) -> {
                    Map<String, String> existing = merged.get(setKey);
                    if (existing == null || existing.isEmpty()) {
                        merged.put(setKey, map);
                    } else {
                        Map<String, String> combined = new HashMap<>(existing);
                        combined.putAll(map);
                        merged.put(setKey, Map.copyOf(combined));
                    }
                });
                captureTokenSets = Map.copyOf(merged);
            }
        }
    }

    void loadPatterns(JsonArray arr) {
        if (arr == null || arr.isEmpty()) return;
        List<DialogueRule> compiled = new ArrayList<>(arr.size());
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            if (o.has("regex") && o.has("zh_tw")) {
                String regex = o.get("regex").getAsString();
                if (regex.isBlank()) continue;
                compiled.add(new DialogueRule(
                        Pattern.compile(regex),
                        o.get("zh_tw").getAsString(),
                        o.has("verbatim") && o.get("verbatim").getAsBoolean(),
                        ReviewedPatterns.captureTypes(o),
                        readStringMap(o.getAsJsonObject("capture_tokens"))));
            }
        }
        mergePatternRules(compiled);
    }

    private void mergePatternRules(Collection<DialogueRule> incoming) {
        if (incoming == null || incoming.isEmpty()) return;
        if (patternRules.isEmpty()) {
            patternRules.addAll(incoming);
            return;
        }
        Map<String, DialogueRule> staged = new LinkedHashMap<>();
        for (DialogueRule prev : patternRules) {
            staged.put(prev.pattern().pattern(), prev);
        }
        for (DialogueRule next : incoming) {
            staged.put(next.pattern().pattern(), next);
        }
        patternRules.clear();
        patternRules.addAll(staged.values());
    }

    public void loadFromFile(Path path) throws IOException {
        loadFromJson(Files.readString(path, StandardCharsets.UTF_8));
    }

    public void loadFromJson(String json) {
        if (json == null || json.isBlank()) return;
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        loadCaptureTokenSets(root);

        JsonObject mapObj = root.has("dialogues") && root.get("dialogues").isJsonObject()
                ? root.getAsJsonObject("dialogues")
                : root;

        for (Map.Entry<String, JsonElement> entry : mapObj.entrySet()) {
            if ("meta".equals(entry.getKey()) || "patterns".equals(entry.getKey())
                    || TOKEN_SETS.equals(entry.getKey())
                    || ReviewedPatterns.SECTION.equals(entry.getKey())) continue;
            if (entry.getValue().isJsonPrimitive()) {
                registerDialogueInternal(entry.getKey(), entry.getValue().getAsString(), false);
            } else if (entry.getValue().isJsonObject()) {
                JsonObject o = entry.getValue().getAsJsonObject();
                String en = o.has("en") ? o.get("en").getAsString() : entry.getKey();
                String zh = o.has("zh_tw") ? o.get("zh_tw").getAsString() : "";
                if (!zh.isEmpty()) {
                    registerDialogueInternal(en, zh, false);
                }
            }
        }

        if (root.has("patterns") && root.get("patterns").isJsonArray()) {
            loadPatterns(root.getAsJsonArray("patterns"));
        }
        loadReviewed(root, "dialogues");
    }

    void loadReviewed(JsonObject root, String section) {
        reviewed.load(root, section);
    }

    private void loadCaptureTokenSets(JsonObject root) {
        JsonObject sets = root.getAsJsonObject(TOKEN_SETS);
        if (sets == null) return;
        Map<String, Map<String, String>> updated = new HashMap<>(captureTokenSets);
        sets.entrySet().forEach(entry -> {
            Map<String, String> aliases = new HashMap<>(updated.getOrDefault(entry.getKey(), Map.of()));
            readStringMap(entry.getValue().getAsJsonObject()).forEach((alias, token) ->
                    aliases.put(normalizeKey(alias).toLowerCase(Locale.ROOT), token));
            updated.put(entry.getKey(), Map.copyOf(aliases));
        });
        captureTokenSets = Map.copyOf(updated);
    }

    private static Map<String, String> readStringMap(JsonObject json) {
        if (json == null) return Map.of();
        Map<String, String> result = new HashMap<>();
        json.entrySet().forEach(entry -> result.put(entry.getKey(), entry.getValue().getAsString()));
        return Map.copyOf(result);
    }

    /**
     * Translates a dialogue line or chat component text leaf while preserving formatting,
     * [NPC] tags, and interactive bracket options. Returns original text if no translation applies.
     */
    public String translate(String raw) {
        if (raw == null || raw.isEmpty()) return raw;

        Matcher outer = OUTER_FORMAT_PATTERN.matcher(raw);
        String lead = "";
        String core = raw;
        String trail = "";
        if (outer.matches()) {
            lead = outer.group(1);
            core = outer.group(2);
            trail = outer.group(3);
        }

        if (core.isEmpty()) return raw;
        String cleanCore = normalizeKey(core);
        if (cleanCore.isEmpty() || cleanCore.equals("[NPC]")) {
            return raw;
        }

        // 1. Direct full-string match (handles full lines as well as exact option/button labels)
        String manual = matchReviewed(cleanCore);
        if (manual != null) return lead + addNpcReference(core, manual) + trail;
        String direct = lookupExact(cleanCore);
        if (direct != null) {
            return lead + addNpcReference(core, resolveTemplate(direct)) + trail;
        }

        String dynamic = translatePatternOrNpc(core, cleanCore);
        if (dynamic != null) {
            return lead + dynamic + trail;
        }

        // Interactive option brackets e.g. "[Uh, yeah. I'm the best.]" or "[Click Here]"
        Matcher optMatcher = BRACKET_OPTION_PATTERN.matcher(cleanCore);
        if (optMatcher.matches()) {
            String inner = optMatcher.group(1).trim();
            String innerHit = translateFragment(inner);
            if (innerHit != null) {
                String innerColorPrefix = extractLeadingColor(core.substring(1));
                return lead + "[" + innerColorPrefix + innerHit + "]" + trail;
            }
        }

        return raw;
    }

    /**
     * Safe variant of {@link #translate(String)} for UI, container, tab-list, and scoreboard text.
     * Matches multi-word/punctuated exact entries, [NPC] prefixes, regex patterns, and explicitly
     * registered entries while skipping JSON-loaded bare single-word alphabetic NPC keys (e.g.
     * "An", "Sam", "Bestiary", "Bingo"), short fragments (< 3 ASCII letters), and bare
     * single-word bracket tags (e.g. "[SAM]").
     */
    public String translateUiPhrase(String raw) {
        if (raw == null || raw.isEmpty()) return raw;

        Matcher outer = OUTER_FORMAT_PATTERN.matcher(raw);
        String lead = "";
        String core = raw;
        String trail = "";
        if (outer.matches()) {
            lead = outer.group(1);
            core = outer.group(2);
            trail = outer.group(3);
        }

        if (core.isEmpty()) return raw;
        String cleanCore = normalizeKey(core);
        if (cleanCore.isEmpty() || cleanCore.equals("[NPC]") || countAsciiLetters(cleanCore) < 3) {
            return raw;
        }

        boolean singleAlphaWord = isSingleAlphabeticWord(cleanCore);
        String manual = matchReviewed(cleanCore);
        if (manual != null) return lead + addNpcReference(core, manual) + trail;

        // 1. Exact full-string match (skip JSON-loaded bare single-word alphabetic NPC keys)
        if (!singleAlphaWord || manualLowerDialogues.contains(cleanCore.toLowerCase(Locale.ROOT))) {
            String direct = lookupExact(cleanCore);
            if (direct != null) {
                return lead + addNpcReference(core, resolveTemplate(direct)) + trail;
            }
        }

        String dynamic = translatePatternOrNpc(core, cleanCore);
        if (dynamic != null) {
            return lead + dynamic + trail;
        }

        // Interactive bracket options only when inner text is not a bare single-word tag (e.g. "[SAM]")
        Matcher optMatcher = BRACKET_OPTION_PATTERN.matcher(cleanCore);
        if (optMatcher.matches()) {
            String inner = optMatcher.group(1).trim();
            if (!isSingleAlphabeticWord(inner)) {
                String innerHit = translateFragment(inner);
                if (innerHit != null) {
                    String innerColorPrefix = extractLeadingColor(core.substring(1));
                    return lead + "[" + innerColorPrefix + innerHit + "]" + trail;
                }
            }
        }

        return raw;
    }

    private String translatePatternOrNpc(String core, String cleanCore) {
        String patternHit = matchPattern(cleanCore);
        if (patternHit != null) {
            return addNpcReference(core, patternHit);
        }

        Matcher npcMatcher = NPC_PREFIX_PATTERN.matcher(core);
        if (npcMatcher.matches()) {
            String npcTagColor = npcMatcher.group(1);
            String postTagSpace = npcMatcher.group(2);
            String rawNpcName = npcMatcher.group(3);
            String preColonColor = npcMatcher.group(4);
            String colonAndBody = npcMatcher.group(5);
            String rawBody = npcMatcher.group(6);

            String translatedNpc = translateNpcName(rawNpcName.trim());
            if (colonAndBody == null) {
                if (!translatedNpc.equals(rawNpcName.trim())) {
                    return npcTagColor + "[NPC]" + postTagSpace + translatedNpc + preColonColor;
                }
            } else {
                String colonSep = extractColonPrefix(colonAndBody, rawBody);
                String translatedBody = rawBody != null ? translateDialogueBody(rawBody) : "";
                if (!translatedNpc.equals(rawNpcName.trim()) || (rawBody != null && !translatedBody.equals(rawBody))) {
                    return npcTagColor + "[NPC]" + postTagSpace + translatedNpc + preColonColor + colonSep + translatedBody;
                }
            }
        }

        return null;
    }

    private String addNpcReference(String source, String translated) {
        Matcher original = NAMED_SPEAKER.matcher(source);
        if (!original.matches()) return translated;
        String english = normalizeKey(original.group(1));
        Matcher display = NAMED_SPEAKER.matcher(translated);
        if (!display.matches()) return translated;
        String name = normalizeKey(display.group(1));
        if (name.contains("（" + english + "）") || name.contains("(" + english + ")")) return translated;
        String bilingual = name.equalsIgnoreCase(english) ? resolveMappedToken("npc", english)
                : CaptureTemplate.bilingualName(name, english);
        if (bilingual.equals(name)) return translated;
        String color = extractLeadingColor(display.group(1));
        return translated.substring(0, display.start(1)) + color + bilingual + translated.substring(display.end(1));
    }

    private static int countAsciiLetters(String s) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                count++;
            }
        }
        return count;
    }

    private static boolean isSingleAlphabeticWord(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'))) {
                return false;
            }
        }
        return true;
    }

    private String translateDialogueBody(String rawBody) {
        if (rawBody == null || rawBody.isEmpty()) return rawBody;
        String hit = translateFragment(normalizeKey(rawBody));
        return hit != null ? extractLeadingColor(rawBody) + hit : rawBody;
    }

    private String translateFragment(String source) {
        String rendered = matchReviewed(source);
        if (rendered != null) return rendered;
        String exact = lookupExact(source);
        return exact != null ? resolveTemplate(exact) : matchPattern(source);
    }
    private String translateNpcName(String cleanOrFormattedName) {
        String clean = stripColor(cleanOrFormattedName).trim();
        if (clean.isEmpty()) return cleanOrFormattedName;
        String prefix = extractLeadingColor(cleanOrFormattedName);
        String bilingual = resolveMappedToken("npc", clean);
        if (!bilingual.equals(clean)) return prefix + bilingual;
        String directNpc = lookupExact(clean);
        if (directNpc != null) {
            return prefix + resolveTemplate(directNpc);
        }
        return cleanOrFormattedName;
    }

    private String lookupExact(String cleanKey) {
        if (cleanKey == null || cleanKey.isEmpty()) return null;
        String hit = exactDialogues.get(cleanKey);
        if (hit != null) return hit;
        return exactLowerDialogues.get(cleanKey.toLowerCase(Locale.ROOT));
    }

    private String matchReviewed(String cleanText) {
        var rendered = reviewed.match(cleanText, this::resolveTemplate, captureLocalizer);
        return rendered == null ? null : rendered.text();
    }

    private String matchPattern(String cleanText) {
        for (DialogueRule rule : patternRules) {
            Matcher m = rule.pattern().matcher(cleanText);
            if (m.matches()) {
                CaptureTemplate.Rendered result = CaptureTemplate.bind(rule.zhTemplate(), m, this::resolveTemplate,
                        (role, value) -> {
                            String tokenSet = rule.tokenSets().get(role);
                            if (tokenSet != null) return resolveMappedToken(tokenSet, value);
                            String type = rule.types().get(role);
                            if (type != null) return captureLocalizer.apply(type, value);
                            if (tokenDictionary == null && !rule.verbatim()) {
                                String exact = lookupExact(normalizeKey(value));
                                if (exact != null) return exact;
                            }
                            return value;
                        });
                if (result != null) return result.text();
            }
        }
        return null;
    }

    private String resolveMappedToken(String set, String value) {
        if (tokenDictionary == null) return value;
        Map<String, String> aliases = captureTokenSets.get(set);
        if (aliases == null) return value;
        String id = aliases.get(normalizeKey(value).toLowerCase(Locale.ROOT));
        if (id == null) return value;
        TokenRecord token = tokenDictionary.get(id);
        if (token == null || token.zh_tw().isBlank()) return value;
        String localized = tokenDictionary.resolveString(token.zh_tw());
        return "npc".equals(set) ? CaptureTemplate.bilingualName(localized, value) : localized;
    }

    private String resolveTemplate(String template) {
        if (template == null) return "";
        return tokenDictionary != null ? tokenDictionary.resolveString(template) : template;
    }

    private static String normalizeKey(String text) {
        String stripped = stripColor(text);
        return MULTI_SPACE.matcher(stripped.trim()).replaceAll(" ");
    }

    private static String stripColor(String text) {
        if (text == null) return "";
        return COLOR_STRIP_PATTERN.matcher(text).replaceAll("");
    }

    private static String extractLeadingColor(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i + 1 < text.length() && text.charAt(i) == '§') {
            sb.append(text, i, i + 2);
            i += 2;
        }
        return sb.toString();
    }

    private static String extractColonPrefix(String colonAndBody, String rawBody) {
        if (rawBody == null || rawBody.isEmpty()) return colonAndBody;
        int idx = colonAndBody.length() - rawBody.length();
        return idx > 0 ? colonAndBody.substring(0, idx) : ": ";
    }
}
