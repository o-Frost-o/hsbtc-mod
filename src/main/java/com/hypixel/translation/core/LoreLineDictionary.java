package com.hypixel.translation.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runtime normalized lore-line dictionary for Hypixel SkyBlock item descriptions.
 * Normalizes numbers and Roman numerals into indexed placeholders ({0}, {1}, ...),
 * tracks Minecraft § formatting state across placeholders without injecting § codes
 * on plain MC 26.2 Component strings, and supports bundled + external JSON overlays.
 */
public class LoreLineDictionary {

    public record CapturedArg(String value, String format) {}

    public record NormalizedLine(
            String cleanText,
            String templateKey,
            List<CapturedArg> args,
            String leadingPrefix,
            String trailingSuffix,
            String baseFormat
    ) {}

    private static final Pattern PLACEHOLDER_TOKEN_PATTERN = Pattern.compile(
            "(?<![A-Za-z0-9]|[A-Za-z]-)(?:"
                    + "\\d+(?:,\\d+)*(?:\\.\\d+)?(?=(?:[smhkxSMHKX]|st|nd|rd|th)?(?![A-Za-z0-9]))"
                    + "|(?<=Tier |Floor |Phase |Collection |\\()I(?![A-Za-z0-9])"
                    + "|(?:II|III|IV|V|VI|VII|VIII|IX|X)(?![A-Za-z0-9])"
                    + ")"
    );
    private static final Pattern HAS_INDEXED_PLACEHOLDER = Pattern.compile(".*\\{\\d+}.*", Pattern.DOTALL);
    private static final Pattern PLACEHOLDER_REF_PATTERN = Pattern.compile("\\{(\\d+)}");
    private static final Pattern CJK_PATTERN = Pattern.compile("[\\u4e00-\\u9fff]");
    private static final Pattern ASCII_LETTER_PATTERN = Pattern.compile("[A-Za-z]");
    private static final Pattern WORD_SPACE = Pattern.compile("[\\s\\p{Zs}]+");
    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("§[0-9a-fklmnorA-FKLMNOR]");
    private static final Map<String, LoreLineDictionary> BUNDLED_CACHE = new ConcurrentHashMap<>();

    private final Map<String, String> exactLines = new ConcurrentHashMap<>();
    private final Map<String, String> exactLowerLines = new ConcurrentHashMap<>();
    private final Map<String, String> templateLines = new ConcurrentHashMap<>();
    private final Map<String, String> templateLowerLines = new ConcurrentHashMap<>();

    private record ParagraphRule(Pattern pattern, String translation, Map<String, Map<String, String>> terms) {
        String localize(String role, String value) {
            Map<String, String> choices = role == null ? null : terms.get(role);
            return choices != null ? choices.get(value.toLowerCase(Locale.ROOT)) : value;
        }
    }

    private final Map<String, String> paragraphs = new ConcurrentHashMap<>();
    private final List<ParagraphRule> paragraphRules = new CopyOnWriteArrayList<>();
    private final ReviewedPatterns reviewed = new ReviewedPatterns();
    private volatile java.util.function.BiFunction<String, String, String> captureLocalizer = (type, value) -> value;

    void setCaptureLocalizer(java.util.function.BiFunction<String, String, String> localizer) {
        captureLocalizer = localizer;
    }

    public LoreLineDictionary() {}

    public void registerLine(String templateOrExactEn, String zhTw) {
        if (templateOrExactEn == null || zhTw == null) return;
        String key = normalizeKey(templateOrExactEn);
        if (key.isEmpty()) return;
        if (HAS_INDEXED_PLACEHOLDER.matcher(key).matches()) {
            templateLines.put(key, zhTw);
            templateLowerLines.put(key.toLowerCase(Locale.ROOT), zhTw);
        } else {
            exactLines.put(key, zhTw);
            exactLowerLines.put(key.toLowerCase(Locale.ROOT), zhTw);
        }
    }

    static String normalizeKey(String text) {
        return WORD_SPACE.matcher(COLOR_CODE_PATTERN.matcher(text).replaceAll(""))
                .replaceAll(" ").trim().replace('’', '\'');
    }

    public int size() {
        return exactLines.size() + templateLines.size() + paragraphs.size() + paragraphRules.size();
    }

    public int exactCount() {
        return exactLines.size();
    }

    public int templateCount() {
        return templateLines.size();
    }

    public void clear() {
        exactLines.clear();
        exactLowerLines.clear();
        templateLines.clear();
        templateLowerLines.clear();
        paragraphs.clear();
        paragraphRules.clear();
        reviewed.clear();
    }

    public void loadFromResource(String resourcePath) throws IOException {
        if (BundledResourceCache.isBundledPath(resourcePath)) {
            LoreLineDictionary snapshot = BundledResourceCache.getOrLoad(BUNDLED_CACHE, resourcePath, () -> {
                LoreLineDictionary loaded = new LoreLineDictionary();
                loaded.loadFromJson(BundledResourceCache.readResourceUtf8(LoreLineDictionary.class, resourcePath));
                return loaded;
            });
            copyFrom(snapshot);
            return;
        }
        loadFromJson(BundledResourceCache.readResourceUtf8(getClass(), resourcePath));
    }

    private void copyFrom(LoreLineDictionary src) {
        if (src == null) return;
        exactLines.putAll(src.exactLines);
        exactLowerLines.putAll(src.exactLowerLines);
        templateLines.putAll(src.templateLines);
        templateLowerLines.putAll(src.templateLowerLines);
        paragraphs.putAll(src.paragraphs);
        mergeParagraphRules(src.paragraphRules);
        reviewed.mergeFrom(src.reviewed);
    }

    private void mergeParagraphRules(List<ParagraphRule> incoming) {
        if (incoming == null || incoming.isEmpty()) return;
        if (paragraphRules.isEmpty()) {
            paragraphRules.addAll(incoming);
            return;
        }
        Map<String, ParagraphRule> staged = new java.util.LinkedHashMap<>();
        for (ParagraphRule prev : paragraphRules) {
            staged.put(prev.pattern().pattern(), prev);
        }
        for (ParagraphRule next : incoming) {
            staged.put(next.pattern().pattern(), next);
        }
        paragraphRules.clear();
        paragraphRules.addAll(staged.values());
    }

    public void loadFromFile(Path path) throws IOException {
        loadFromJson(Files.readString(path, StandardCharsets.UTF_8));
    }

    public void loadFromJson(String json) {
        if (json == null || json.isBlank()) return;
        JsonElement parsed = JsonParser.parseString(json);
        if (!parsed.isJsonObject()) return;
        JsonObject root = parsed.getAsJsonObject();

        JsonObject mapObj = root.has("lines") && root.get("lines").isJsonObject()
                ? root.getAsJsonObject("lines")
                : root;

        for (Map.Entry<String, JsonElement> entry : mapObj.entrySet()) {
            if ("meta".equals(entry.getKey()) || "paragraphs".equals(entry.getKey())
                    || "paragraph_patterns".equals(entry.getKey()) || ReviewedPatterns.SECTION.equals(entry.getKey())) continue;
            JsonElement val = entry.getValue();
            if (val.isJsonPrimitive()) {
                String zh = val.getAsString();
                if (!zh.isBlank()) {
                    registerLine(entry.getKey(), zh);
                }
            } else if (val.isJsonObject()) {
                JsonObject obj = val.getAsJsonObject();
                String en = obj.has("en") && !obj.get("en").isJsonNull() ? obj.get("en").getAsString() : entry.getKey();
                String zh = obj.has("zh_tw") && !obj.get("zh_tw").isJsonNull() ? obj.get("zh_tw").getAsString() : "";
                if (!zh.isBlank()) {
                    registerLine(en, zh);
                }
            }
        }
        loadParagraphs(root);
        reviewed.load(root, "paragraphs");
        reviewed.load(root, "lines");
    }

    private void loadParagraphs(JsonObject root) {
        if (root.has("paragraphs")) {
            for (var entry : root.getAsJsonObject("paragraphs").entrySet()) {
                paragraphs.put(normalizeKey(entry.getKey()).toLowerCase(Locale.ROOT), entry.getValue().getAsString());
            }
        }
        if (root.has("paragraph_patterns")) {
            List<ParagraphRule> compiledRules = new ArrayList<>();
            for (var element : root.getAsJsonArray("paragraph_patterns")) {
                var rule = element.getAsJsonObject();
                var regex = rule.get("regex").getAsString();
                Map<String, Map<String, String>> terms = new HashMap<>();
                if (rule.has("terms")) {
                    rule.getAsJsonObject("terms").entrySet().forEach(role -> {
                        Map<String, String> choices = new HashMap<>();
                        role.getValue().getAsJsonObject().entrySet().forEach(choice ->
                                choices.put(choice.getKey().toLowerCase(Locale.ROOT), choice.getValue().getAsString()));
                        terms.put(role.getKey(), Map.copyOf(choices));
                    });
                }
                compiledRules.add(new ParagraphRule(Pattern.compile(regex), rule.get("zh_tw").getAsString(), Map.copyOf(terms)));
            }
            mergeParagraphRules(compiledRules);
        }
    }

    public static NormalizedLine normalize(String rawLine) {
        if (rawLine == null || rawLine.isEmpty()) {
            return new NormalizedLine("", "", List.of(), "", "", "");
        }

        int len = rawLine.length();
        int trailingStart = len;
        while (trailingStart > 0 && isWordSpace(rawLine.charAt(trailingStart - 1))) {
            trailingStart--;
        }
        String trailingSuffix = rawLine.substring(trailingStart);

        int leadEnd = 0;
        while (leadEnd < trailingStart) {
            char c = rawLine.charAt(leadEnd);
            if (isWordSpace(c)) {
                leadEnd++;
            } else if (c == '§' && leadEnd + 1 < trailingStart && isFormatChar(rawLine.charAt(leadEnd + 1))) {
                leadEnd += 2;
            } else {
                break;
            }
        }
        String leadingPrefix = rawLine.substring(0, leadEnd);

        StringBuilder activeColor = new StringBuilder();
        StringBuilder activeStyles = new StringBuilder();
        for (int i = 0; i < leadEnd; i++) {
            if (rawLine.charAt(i) == '§' && i + 1 < leadEnd && isFormatChar(rawLine.charAt(i + 1))) {
                applyFormatCode(rawLine.charAt(i + 1), activeColor, activeStyles);
                i++;
            }
        }

        StringBuilder cleanSb = new StringBuilder();
        List<String> formatPerChar = new ArrayList<>();
        for (int i = leadEnd; i < trailingStart; i++) {
            char c = rawLine.charAt(i);
            if (c == '§' && i + 1 < trailingStart && isFormatChar(rawLine.charAt(i + 1))) {
                applyFormatCode(rawLine.charAt(i + 1), activeColor, activeStyles);
                i++;
                continue;
            }
            if (isWordSpace(c)) {
                if (cleanSb.isEmpty() || cleanSb.charAt(cleanSb.length() - 1) == ' ') continue;
                c = ' ';
            }
            cleanSb.append(c == '’' ? '\'' : c);
            formatPerChar.add(activeColor.toString() + activeStyles);
        }

        // Trim any trailing whitespace uncovered after trailing § codes
        while (!cleanSb.isEmpty() && Character.isWhitespace(cleanSb.charAt(cleanSb.length() - 1))) {
            cleanSb.setLength(cleanSb.length() - 1);
            formatPerChar.remove(formatPerChar.size() - 1);
        }

        String clean = cleanSb.toString();
        if (clean.isEmpty()) {
            return new NormalizedLine("", "", List.of(), leadingPrefix, trailingSuffix, "");
        }

        String baseFormat = formatPerChar.isEmpty() ? "" : formatPerChar.get(0);
        Matcher m = PLACEHOLDER_TOKEN_PATTERN.matcher(clean);
        StringBuilder tmpl = new StringBuilder();
        List<CapturedArg> args = new ArrayList<>();
        int last = 0;
        int idx = 0;
        while (m.find()) {
            tmpl.append(clean, last, m.start());
            tmpl.append('{').append(idx).append('}');
            String fmt = m.start() < formatPerChar.size() ? formatPerChar.get(m.start()) : baseFormat;
            args.add(new CapturedArg(m.group(), fmt));
            last = m.end();
            idx++;
        }
        tmpl.append(clean, last, clean.length());

        return new NormalizedLine(clean, tmpl.toString(), List.copyOf(args), leadingPrefix, trailingSuffix, baseFormat);
    }

    /**
     * Translates a lore line if an exact or normalized template match exists.
     * Returns null if no translation matches or the input should not be translated.
     */
    public String translate(String rawLine) {
        NormalizedLine norm = normalize(rawLine);
        if (!isEnglish(norm.cleanText())) return null;
        CaptureTemplate.Rendered reviewed = matchParagraph(rawLine, norm);
        return reviewed != null ? reviewed.text() : translateLine(norm);
    }

    public CaptureTemplate.Rendered matchParagraph(String rawLine) {
        if (paragraphs.isEmpty() && paragraphRules.isEmpty() && reviewed.isEmpty()) return null;
        NormalizedLine norm = normalize(rawLine);
        return isEnglish(norm.cleanText()) ? matchParagraph(rawLine, norm) : null;
    }

    private static boolean isEnglish(String text) {
        return !text.isEmpty() && !CJK_PATTERN.matcher(text).find() && ASCII_LETTER_PATTERN.matcher(text).find();
    }

    public CaptureTemplate.Rendered matchReviewed(String rawLine) {
        if (reviewed.isEmpty()) return null;
        var norm = normalize(rawLine);
        if (!isEnglish(norm.cleanText())) return null;
        var match = reviewed.match(norm.cleanText(), s -> s, captureLocalizer);
        return match == null ? null : withSourceFormatting(rawLine, norm, match);
    }

    private CaptureTemplate.Rendered matchParagraph(String rawLine, NormalizedLine norm) {
        String clean = norm.cleanText();
        var manual = reviewed.match(clean, s -> s, captureLocalizer);
        if (manual != null) return withSourceFormatting(rawLine, norm, manual);
        String exact = paragraphs.get(clean.toLowerCase(Locale.ROOT));
        if (exact != null) {
            return new CaptureTemplate.Rendered(norm.leadingPrefix() + exact + norm.trailingSuffix());
        }
        for (ParagraphRule rule : paragraphRules) {
            Matcher source = rule.pattern().matcher(clean);
            if (!source.matches()) continue;
            CaptureTemplate.Rendered rendered = CaptureTemplate.bind(rule.translation(), source, s -> s, rule::localize);
            if (rendered != null) {
                return withSourceFormatting(rawLine, norm, rendered);
            }
        }

        String template = paragraphs.get(norm.templateKey().toLowerCase(Locale.ROOT));
        if (template == null) return null;
        String rendered = renderIndexed(template, norm);
        return rendered != null ? new CaptureTemplate.Rendered(rendered) : null;
    }

    private static CaptureTemplate.Rendered withSourceFormatting(String raw, NormalizedLine norm,
                                                                CaptureTemplate.Rendered rendered) {
        return norm.cleanText().equals(raw) ? rendered
                : new CaptureTemplate.Rendered(norm.leadingPrefix() + rendered.text() + norm.trailingSuffix());
    }

    private String translateLine(NormalizedLine norm) {
        String clean = norm.cleanText();

        String exact = exactLines.get(clean);
        if (exact == null) {
            exact = exactLowerLines.get(clean.toLowerCase(Locale.ROOT));
        }
        if (exact != null) {
            return norm.leadingPrefix() + exact + norm.trailingSuffix();
        }

        String zhTemplate = templateLines.get(norm.templateKey());
        if (zhTemplate == null) {
            zhTemplate = templateLowerLines.get(norm.templateKey().toLowerCase(Locale.ROOT));
            if (zhTemplate == null) return null;
        }

        return renderIndexed(zhTemplate, norm);
    }

    private static String renderIndexed(String zhTemplate, NormalizedLine norm) {
        boolean templateHasCodes = COLOR_CODE_PATTERN.matcher(zhTemplate).find();
        String baseFormat = norm.baseFormat();
        List<CapturedArg> args = norm.args();

        Matcher refMatcher = PLACEHOLDER_REF_PATTERN.matcher(zhTemplate);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (refMatcher.find()) {
            int argIdx = Integer.parseInt(refMatcher.group(1));
            if (argIdx < 0 || argIdx >= args.size()) {
                return null;
            }
            out.append(zhTemplate, last, refMatcher.start());
            CapturedArg arg = args.get(argIdx);
            if (templateHasCodes || arg.format().equals(baseFormat)) {
                out.append(arg.value());
            } else if (!arg.format().isEmpty() || !baseFormat.isEmpty()) {
                out.append(arg.format().isEmpty() ? "§r" : arg.format())
                        .append(arg.value())
                        .append(baseFormat.isEmpty() ? "§r" : baseFormat);
            } else {
                out.append(arg.value());
            }
            last = refMatcher.end();
        }
        out.append(zhTemplate, last, zhTemplate.length());

        return norm.leadingPrefix() + out + norm.trailingSuffix();
    }

    private static boolean isWordSpace(char c) {
        return Character.isWhitespace(c) || Character.isSpaceChar(c);
    }

    private static boolean isFormatChar(char c) {
        return (c >= '0' && c <= '9')
                || (c >= 'a' && c <= 'f')
                || (c >= 'A' && c <= 'F')
                || (c >= 'k' && c <= 'o')
                || (c >= 'K' && c <= 'O')
                || c == 'r' || c == 'R';
    }

    private static void applyFormatCode(char rawCode, StringBuilder activeColor, StringBuilder activeStyles) {
        char c = Character.toLowerCase(rawCode);
        if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')) {
            activeColor.setLength(0);
            activeColor.append('§').append(c);
            activeStyles.setLength(0);
        } else if (c == 'r') {
            activeColor.setLength(0);
            activeStyles.setLength(0);
        } else if (c >= 'k' && c <= 'o') {
            String code = "§" + c;
            if (activeStyles.indexOf(code) == -1) {
                activeStyles.append(code);
            }
        }
    }
}
