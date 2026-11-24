package com.hypixel.translation.engine;

import com.hypixel.translation.core.CaptureTemplate;

import com.hypixel.translation.core.ItemDictionary;
import com.hypixel.translation.core.ItemLoreOverrides;
import com.hypixel.translation.core.ItemTranslationRecord;
import com.hypixel.translation.core.TokenDictionary;
import com.hypixel.translation.core.TokenRecord;
import com.hypixel.translation.core.TranslationCore;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.*;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dynamic item decomposition and translation engine.
 * Handles dual-path collision resolution for 276 collision base items (e.g. Ancient Claw, Warped Stone),
 * dungeon and master mode star extraction (✪, ➊-➎), pet level decomposition,
 * tooltip preview toggle, and suffix fallback rules.
 */
public class DynamicItemCombiner {
    public record DecomposedName(
        String reforge,
        String baseName,
        String stars,
        String petLevel,
        String colorPrefix
    ) {}

    private static final Set<String> DEFAULT_COLLISION_BASE_ITEMS = Set.of(
            "Ancient Claw", "Ancient Cloak", "Warped Stone", "Bulky Stone", "Blessed Fruit",
            "Calcified Heart", "Beady Eyes", "Light Blue Carpet", "Giant Isopod Shard", "Heavy Helmet",
            "Wise Dragon Helmet", "Spiritual Stone", "Fabled Stone", "Heroic Stone", "Submerged Stone",
            "Spicy Squid", "Heavy Pearl"
    );

    private static final Map<String, String> REFORGES = Map.ofEntries(
            Map.entry("Heroic", "英雄"),
            Map.entry("Fabled", "傳說"),
            Map.entry("Ancient", "遠古"),
            Map.entry("Withered", "凋零"),
            Map.entry("Spiritual", "精神"),
            Map.entry("Jaded", "碧玉"),
            Map.entry("Warped", "扭曲"),
            Map.entry("Mythic", "神話"),
            Map.entry("Spicy", "辛辣"),
            Map.entry("Gentle", "溫和"),
            Map.entry("Odd", "古怪"),
            Map.entry("Fast", "迅速"),
            Map.entry("Fair", "公正"),
            Map.entry("Epic", "史詩"),
            Map.entry("Sharp", "鋒利"),
            Map.entry("Heavy", "重型"),
            Map.entry("Wise", "智慧"),
            Map.entry("Pure", "純淨"),
            Map.entry("Smart", "精明"),
            Map.entry("Clean", "乾淨"),
            Map.entry("Fierce", "兇猛"),
            Map.entry("Titanic", "泰坦"),
            Map.entry("Necrotic", "死靈"),
            Map.entry("Loving", "仁愛"),
            Map.entry("Ridiculous", "荒謬"),
            Map.entry("Giant", "巨人"),
            Map.entry("Submerged", "淹沒"),
            Map.entry("Blessed", "祝福"),
            Map.entry("Bulky", "笨重")
    );

    private static final Map<String, String> KNOWN_ITEMS = Map.ofEntries(
            Map.entry("Hyperion", "亥伯龍"),
            Map.entry("Terminator", "終結者"),
            Map.entry("Aspect of the End", "終界之劍"),
            Map.entry("Baby Yeti", "雪怪寶寶"),
            Map.entry("Chestplate of Divan", "迪萬胸甲"),
            Map.entry("Divan's Drill", "迪萬鑽頭"),
            Map.entry("Ancient Claw", "古代之爪"),
            Map.entry("Warped Stone", "扭曲之石"),
            Map.entry("Heavy Helmet", "重型頭盔"),
            Map.entry("Bulky Stone", "笨重之石"),
            Map.entry("Spicy Squid", "辛辣烏賊"),
            Map.entry("Blessed Fruit", "受祝福的水果"),
            Map.entry("Ancient Cloak", "古代斗篷"),
            Map.entry("Wise Dragon Helmet", "智慧巨龍頭盔"),
            Map.entry("Calcified Heart", "鈣化之心"),
            Map.entry("Beady Eyes", "小圓眼"),
            Map.entry("Light Blue Carpet", "淡藍色地毯"),
            Map.entry("Giant Isopod Shard", "巨型等足蟲碎片"),
            Map.entry("Spiritual Stone", "精神之石"),
            Map.entry("Fabled Stone", "傳說之石"),
            Map.entry("Heroic Stone", "英雄之石")
    );

    private static final Pattern STAR_PATTERN = Pattern.compile("((?:§[0-9a-fk-or]|✪|[➊➋➌➍➎]|\\s)+)$");

    private static final List<Map.Entry<String, String>> FALLBACK_SUFFIXES = List.of(
            Map.entry("Shortsword", " 短劍"),
            Map.entry("Broadsword", " 闊劍"),
            Map.entry("Longsword", " 長劍"),
            Map.entry("Shortbow", " 短弓"),
            Map.entry("Chestplate", " 胸甲"),
            Map.entry("Leggings", " 護腿"),
            Map.entry("Helmet", " 頭盔"),
            Map.entry("Boots", " 靴子"),
            Map.entry("Necklace", " 項鍊"),
            Map.entry("Gauntlet", " 手套"),
            Map.entry("Cloak", " 斗篷"),
            Map.entry("Gloves", " 護手"),
            Map.entry("Belt", " 腰帶"),
            Map.entry("Pickaxe", " 鎬"),
            Map.entry("Fishing Rod", " 釣竿"),
            Map.entry("Drill", " 鑽頭"),
            Map.entry("Scythe", " 鐮刀"),
            Map.entry("Katana", " 太刀"),
            Map.entry("Staff", " 法杖"),
            Map.entry("Wand", " 魔杖"),
            Map.entry("Talisman", " 護身符"),
            Map.entry("Artifact", " 神器"),
            Map.entry("Relic", " 聖物"),
            Map.entry("Ring", " 戒指"),
            Map.entry("Gemstone", " 寶石"),
            Map.entry("Pet Skin", " 寵物造型"),
            Map.entry("Potion", " 藥水"),
            Map.entry("Minion", " 僕從"),
            Map.entry("Rune", " 符文"),
            Map.entry("Shard", " 碎片"),
            Map.entry("Dye", " 染料")
    );

    private static volatile DynamicItemCombiner instance;

    public static DynamicItemCombiner getInstance() {
        if (instance == null) {
            try {
                com.hypixel.translation.core.TranslationCore core = com.hypixel.translation.core.TranslationCore.getInstance();
                if (core != null) {
                    return core.getCombiner();
                }
            } catch (Throwable ignored) {}
        }
        return instance;
    }

    public static void setInstance(DynamicItemCombiner combiner) {
        instance = combiner;
    }

    private final TokenDictionary tokenDict;
    private final LoreParser loreParser;
    private final Set<String> collisionBaseItems;
    private volatile long cachedTokenVersion = -1;
    private volatile Map<String, String> dynamicReforgeMapCache = REFORGES;
    private volatile List<String> sortedReforgesCache = null;
    private volatile int cachedItemDictSize = -1;
    private volatile Set<String> mergedCollisionCache = null;

    public DynamicItemCombiner(TokenDictionary tokenDict, LoreParser loreParser) {
        this(tokenDict, loreParser, DEFAULT_COLLISION_BASE_ITEMS);
    }

    public DynamicItemCombiner(TokenDictionary tokenDict, LoreParser loreParser, Set<String> collisionBaseItems) {
        this.tokenDict = tokenDict;
        this.loreParser = loreParser;
        this.collisionBaseItems = (collisionBaseItems != null) ? collisionBaseItems : DEFAULT_COLLISION_BASE_ITEMS;
    }

    private Map<String, String> getReforgeMap() {
        long ver = tokenDict != null ? tokenDict.getVersion() : -1;
        if (sortedReforgesCache != null && ver == cachedTokenVersion) {
            return dynamicReforgeMapCache;
        }
        Map<String, String> merged = new HashMap<>(REFORGES);
        if (tokenDict != null) {
            for (TokenRecord rec : tokenDict.getTokens().values()) {
                if ("REFORGE".equalsIgnoreCase(rec.category()) || rec.tokenId().startsWith("REFORGE_")) {
                    if (rec.en() != null && !rec.en().isBlank() && rec.zh_tw() != null && !rec.zh_tw().isBlank()) {
                        merged.put(rec.en().trim(), tokenDict.resolveString(rec.zh_tw()));
                    }
                }
            }
        }
        List<String> sorted = new ArrayList<>(merged.keySet());
        sorted.sort((a, b) -> Integer.compare(b.length(), a.length()));
        dynamicReforgeMapCache = Collections.unmodifiableMap(merged);
        sortedReforgesCache = Collections.unmodifiableList(sorted);
        cachedTokenVersion = ver;
        return dynamicReforgeMapCache;
    }

    private List<String> getSortedReforges() {
        getReforgeMap();
        return sortedReforgesCache;
    }

    private ItemDictionary resolveItemDictionary() {
        try {
            TranslationCore core = TranslationCore.getInstance();
            return core != null ? core.getItemDictionary() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Set<String> getMergedCollisionItems() {
        ItemDictionary itemDict = resolveItemDictionary();
        int itemSize = itemDict != null ? itemDict.size() : 0;
        if (mergedCollisionCache != null && itemSize == cachedItemDictSize) {
            return mergedCollisionCache;
        }
        Set<String> merged = new HashSet<>(collisionBaseItems);
        merged.addAll(KNOWN_ITEMS.keySet());
        if (itemDict != null && itemSize > 0) {
            for (ItemTranslationRecord rec : itemDict.getItems().values()) {
                if (rec.name_en() != null && !rec.name_en().isBlank()) {
                    merged.add(rec.name_en().trim());
                }
            }
        }
        mergedCollisionCache = Collections.unmodifiableSet(merged);
        cachedItemDictSize = itemSize;
        return mergedCollisionCache;
    }

    public DecomposedName decomposeName(String rawDisplayName, String nbtModifier) {
        if (rawDisplayName == null) {
            return new DecomposedName(null, "", "", null, "");
        }

        String color = "";
        String clean = rawDisplayName;
        if (rawDisplayName.startsWith("§")) {
            int idx = 0;
            while (idx + 1 < rawDisplayName.length() && rawDisplayName.charAt(idx) == '§') {
                idx += 2;
            }
            color = rawDisplayName.substring(0, idx);
            clean = rawDisplayName.substring(idx);
        }

        // Pet level check: e.g. "[Lvl 100] Baby Yeti"
        String petLevel = null;
        if (clean.startsWith("[Lvl ")) {
            int endBracket = clean.indexOf(']');
            if (endBracket != -1) {
                petLevel = clean.substring(5, endBracket);
                String after = clean.substring(endBracket + 1).trim();
                return new DecomposedName(null, after, "", petLevel, color);
            }
        }

        // Extract stars (dungeon stars ✪ and master mode stars ➊-➎ with color codes)
        String stars = "";
        Matcher sm = STAR_PATTERN.matcher(clean);
        if (sm.find() && (clean.contains("✪") || clean.matches(".*[➊➋➌➍➎].*"))) {
            stars = sm.group(1).trim();
            clean = clean.substring(0, sm.start()).trim();
        }

        List<String> allReforges = getSortedReforges();

        // Path A: Check NBT modifier first if provided
        if (nbtModifier != null && !nbtModifier.isEmpty()) {
            for (String ref : allReforges) {
                if (ref.equalsIgnoreCase(nbtModifier)) {
                    String reforgePrefix = ref + " ";
                    if (clean.toLowerCase(Locale.ROOT).startsWith(reforgePrefix.toLowerCase(Locale.ROOT))) {
                        clean = clean.substring(reforgePrefix.length()).trim();
                    }
                    return new DecomposedName(ref, clean, stars, null, color);
                }
            }
        }

        Set<String> protectedBaseItems = getMergedCollisionItems();

        // Path B: Text-only parsing with Collision protection
        // If base name is an exact match for a known/collision item, do NOT strip prefix!
        if (protectedBaseItems.contains(clean)) {
            return new DecomposedName(null, clean, stars, null, color);
        }

        // Check if it's a reforge applied on a known/collision base item (e.g. "Ancient Ancient Claw")
        for (String ref : allReforges) {
            if (clean.startsWith(ref + " ")) {
                String candidateBase = clean.substring(ref.length() + 1).trim();
                if (protectedBaseItems.contains(candidateBase)) {
                    return new DecomposedName(ref, candidateBase, stars, null, color);
                }
            }
        }

        // General reforge matching
        for (String ref : allReforges) {
            if (clean.startsWith(ref + " ")) {
                String base = clean.substring(ref.length() + 1).trim();
                return new DecomposedName(ref, base, stars, null, color);
            }
        }

        return new DecomposedName(null, clean, stars, null, color);
    }

    private record NbtAttributes(String modifier, String itemId) {}

    private NbtAttributes extractNbtAttributes(ItemStack stack) {
        String nbtMod = null;
        String nbtItemId = null;
        if (stack != null) {
            try {
                CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
                if (cd != null) {
                    CompoundTag tag = cd.copyTag();
                    CompoundTag extra = tag.getCompoundOrEmpty("ExtraAttributes");
                    String mod = extra.getStringOr("modifier", tag.getStringOr("modifier", ""));
                    if (!mod.isEmpty()) {
                        nbtMod = mod;
                    }
                    String id = extra.getStringOr("id", tag.getStringOr("id", ""));
                    if (!id.isEmpty()) {
                        nbtItemId = id;
                    }
                }
            } catch (Throwable ignored) {}
        }
        return new NbtAttributes(nbtMod, nbtItemId);
    }

    private String resolveReforgeZh(String reforge) {
        String zhRef = getReforgeMap().getOrDefault(reforge, reforge);
        if (tokenDict != null) {
            String refKey = "REFORGE_" + reforge.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "_");
            TokenRecord refTok = tokenDict.get(refKey);
            if (refTok != null && !refTok.zh_tw().isEmpty() && !refTok.zh_tw().equalsIgnoreCase(refTok.en())) {
                zhRef = tokenDict.resolveString(refTok.zh_tw());
            }
        }
        return zhRef;
    }

    private String resolveBaseNameZh(String cleanBase, String nbtItemId) {
        String tokenKey1 = "ITEM_" + cleanBase.toUpperCase().replace(" ", "_").replace("'", "");
        String tokenKey2 = "ITEM_" + cleanBase.toUpperCase().replace(" ", "_").replace("'S", "");
        TokenRecord itemToken = null;
        if (tokenDict != null) {
            itemToken = tokenDict.get(tokenKey1) != null ? tokenDict.get(tokenKey1) : tokenDict.get(tokenKey2);
        }

        if (itemToken != null && !itemToken.zh_tw().isEmpty()) {
            return itemToken.zh_tw();
        }
        if (KNOWN_ITEMS.containsKey(cleanBase)) {
            return KNOWN_ITEMS.get(cleanBase);
        }
        String dictMatch = lookupItemDictionary(nbtItemId, cleanBase, tokenKey1.substring(5), tokenKey2.substring(5));
        return (dictMatch != null) ? dictMatch : fallbackTranslate(cleanBase);
    }

    public MutableComponent translateItemName(ItemStack stack, String englishName) {
        NbtAttributes nbt = extractNbtAttributes(stack);
        DecomposedName d = decomposeName(englishName, nbt.modifier());

        StringBuilder sb = new StringBuilder();
        if (d.colorPrefix != null) sb.append(d.colorPrefix);

        if (d.petLevel != null) {
            sb.append("[Lvl ").append(d.petLevel).append("] ");
        }

        if (d.reforge != null) {
            sb.append(resolveReforgeZh(d.reforge)).append(" ");
        }

        // Extract any inline color in baseName (e.g. §6Baby Yeti)
        String base = d.baseName;
        String baseColor = "";
        if (base.startsWith("§")) {
            int idx = 0;
            while (idx + 1 < base.length() && base.charAt(idx) == '§') {
                idx += 2;
            }
            baseColor = base.substring(0, idx);
            base = base.substring(idx);
        }

        String zhBase = resolveBaseNameZh(base, nbt.itemId());
        sb.append(baseColor).append(zhBase);

        if (d.stars != null && !d.stars.isEmpty()) {
            sb.append(" ").append(d.stars);
        }

        return Component.literal(sb.toString().trim());
    }

    public MutableComponent translateItemNameComponent(ItemStack stack, Component originalName) {
        if (originalName == null) {
            return Component.empty();
        }
        FlattenedRuns flat = flattenStyledRuns(originalName);
        if (flat.runs().isEmpty()) {
            return Component.empty().setStyle(flat.emptyFallbackStyle());
        }
        if (flat.runs().size() == 1) {
            StyledRun only = flat.runs().get(0);
            return translateItemName(stack, only.text()).setStyle(only.style());
        }

        NbtAttributes nbt = extractNbtAttributes(stack);
        String fullText = flat.fullText();
        Style[] charStyles = buildCharStyles(flat.runs(), fullText.length());

        int colorEnd = 0;
        while (colorEnd + 1 < fullText.length() && fullText.charAt(colorEnd) == '§') {
            colorEnd += 2;
        }

        DecomposedName d = decomposeName(fullText, nbt.modifier());
        List<StyledRun> out = new ArrayList<>();
        if (colorEnd > 0) {
            appendSubRuns(out, flat.runs(), 0, colorEnd);
        }

        if (d.petLevel() != null) {
            int endBracket = fullText.indexOf(']', colorEnd);
            if (endBracket != -1) {
                int afterStart = skipWhitespace(fullText, endBracket + 1, fullText.length());
                if (afterStart > endBracket + 1) {
                    appendSubRuns(out, flat.runs(), colorEnd, afterStart);
                } else {
                    appendSubRuns(out, flat.runs(), colorEnd, endBracket + 1);
                    out.add(new StyledRun(" ", charStyles[endBracket]));
                }
                int baseStart = afterStart;
                int baseEnd = trimEnd(fullText, baseStart, fullText.length());
                if (baseStart < baseEnd) {
                    appendTranslatedBaseRuns(out, flat.runs(), charStyles, fullText, baseStart, baseEnd, nbt.itemId());
                }
                return buildComponentFromRuns(out, flat.emptyFallbackStyle());
            }
        }

        int contentEnd = fullText.length();
        int starsStart = -1;
        int starsEnd = -1;
        if (d.stars() != null && !d.stars().isEmpty()
                && (fullText.contains("✪") || fullText.matches("(?s).*[➊➋➌➍➎].*"))) {
            Matcher sm = STAR_PATTERN.matcher(fullText.substring(colorEnd));
            if (sm.find()) {
                int starsMatchStart = colorEnd + sm.start();
                contentEnd = trimEnd(fullText, colorEnd, starsMatchStart);
                starsStart = skipWhitespace(fullText, starsMatchStart, fullText.length());
                starsEnd = trimEnd(fullText, starsStart, fullText.length());
            }
        } else {
            contentEnd = trimEnd(fullText, colorEnd, fullText.length());
        }

        int baseStart = colorEnd;
        String cleanContent = fullText.substring(colorEnd, contentEnd);
        if (d.reforge() != null) {
            if (cleanContent.toLowerCase(Locale.ROOT).startsWith(d.reforge().toLowerCase(Locale.ROOT) + " ")) {
                int refEnd = colorEnd + d.reforge().length();
                int spaceIdx = refEnd;
                baseStart = skipWhitespace(fullText, spaceIdx + 1, contentEnd);
                out.add(new StyledRun(resolveReforgeZh(d.reforge()), dominantStyle(charStyles, fullText, colorEnd, refEnd)));
                out.add(new StyledRun(" ", charStyles[spaceIdx]));
            } else {
                out.add(new StyledRun(resolveReforgeZh(d.reforge()) + " ", dominantStyle(charStyles, fullText, baseStart, contentEnd)));
            }
        }

        if (baseStart < contentEnd) {
            appendTranslatedBaseRuns(out, flat.runs(), charStyles, fullText, baseStart, contentEnd, nbt.itemId());
        }

        if (starsStart >= 0 && starsStart < starsEnd) {
            if (!out.isEmpty()) {
                out.add(new StyledRun(" ", charStyles[Math.max(colorEnd, starsStart - 1)]));
            }
            appendSubRuns(out, flat.runs(), starsStart, starsEnd);
        }

        return buildComponentFromRuns(out, flat.emptyFallbackStyle());
    }

    private void appendTranslatedBaseRuns(List<StyledRun> out,
                                          List<StyledRun> srcRuns,
                                          Style[] charStyles,
                                          String fullText,
                                          int baseStart,
                                          int baseEnd,
                                          String nbtItemId) {
        String rawBase = fullText.substring(baseStart, baseEnd);
        int inlineLen = 0;
        while (inlineLen + 1 < rawBase.length() && rawBase.charAt(inlineLen) == '§') {
            inlineLen += 2;
        }
        String baseColor = rawBase.substring(0, inlineLen);
        String cleanBase = rawBase.substring(inlineLen);
        String zhBase = resolveBaseNameZh(cleanBase, nbtItemId);

        if (zhBase.equals(cleanBase)) {
            appendSubRuns(out, srcRuns, baseStart, baseEnd);
            return;
        }

        int cleanStart = baseStart + inlineLen;
        for (Map.Entry<String, String> entry : FALLBACK_SUFFIXES) {
            String suffix = entry.getKey();
            if (cleanBase.endsWith(suffix) && cleanBase.length() > suffix.length()) {
                String prefix = cleanBase.substring(0, cleanBase.length() - suffix.length()).trim();
                if ((prefix + entry.getValue()).equals(zhBase) && cleanBase.startsWith(prefix)) {
                    if (inlineLen > 0) {
                        appendSubRuns(out, srcRuns, baseStart, cleanStart);
                    }
                    appendSubRuns(out, srcRuns, cleanStart, cleanStart + prefix.length());
                    int suffixStyleStart = Math.min(baseEnd - 1, cleanStart + prefix.length());
                    out.add(new StyledRun(entry.getValue(), dominantStyle(charStyles, fullText, suffixStyleStart, baseEnd)));
                    return;
                }
                break;
            }
        }

        int styleStart = cleanStart < baseEnd ? cleanStart : baseStart;
        out.add(new StyledRun(baseColor + zhBase, dominantStyle(charStyles, fullText, styleStart, baseEnd)));
    }

    public MutableComponent translateLoreComponent(Component line) {
        return translateStyledLine(line, loreParser::translateLoreLine);
    }

    public static MutableComponent translateStyledLine(Component line, UnaryOperator<String> translate) {
        if (line == null) {
            return Component.empty();
        }
        FlattenedRuns flat = flattenStyledRuns(line, translate);
        if (flat.runs().isEmpty()) {
            return Component.empty().setStyle(flat.emptyFallbackStyle());
        }
        if (flat.runs().size() == 1) {
            StyledRun only = flat.runs().get(0);
            String translated = translate.apply(only.text());
            return Component.literal(translated != null ? translated : only.text()).setStyle(only.style());
        }

        String fullText = flat.fullText();
        String translatedText = translate.apply(fullText);
        if (translatedText == null || translatedText.equals(fullText)) {
            List<StyledRun> runs = flat.runs();
            int firstCore = 0;
            int lastCore = runs.size() - 1;
            while (firstCore <= lastCore && runs.get(firstCore).style().isObfuscated()) {
                firstCore++;
            }
            while (lastCore >= firstCore && runs.get(lastCore).style().isObfuscated()) {
                lastCore--;
            }
            if ((firstCore > 0 || lastCore < runs.size() - 1) && firstCore <= lastCore) {
                List<StyledRun> coreRuns = runs.subList(firstCore, lastCore + 1);
                StringBuilder coreSb = new StringBuilder();
                for (StyledRun r : coreRuns) {
                    coreSb.append(r.text());
                }
                String coreText = coreSb.toString();
                String coreTranslated = translate.apply(coreText);
                if (coreTranslated != null && !coreTranslated.equals(coreText)) {
                    List<StyledRun> combined = new ArrayList<>();
                    combined.addAll(runs.subList(0, firstCore));
                    if (!coreTranslated.isEmpty()) {
                        combined.addAll(alignTranslatedRuns(coreRuns, coreText, coreTranslated, flat.emptyFallbackStyle()));
                    }
                    combined.addAll(runs.subList(lastCore + 1, runs.size()));
                    return buildComponentFromRuns(combined, flat.emptyFallbackStyle());
                }
            }
            return buildComponentFromRuns(runs, flat.emptyFallbackStyle());
        }
        if (translatedText.isEmpty()) {
            return Component.empty().setStyle(flat.emptyFallbackStyle());
        }

        return buildComponentFromRuns(
                alignTranslatedRuns(flat.runs(), fullText, translatedText, flat.emptyFallbackStyle()),
                flat.emptyFallbackStyle()
        );
    }

    public static MutableComponent translateStyledParagraph(Component original, CaptureTemplate.Rendered paragraph) {
        FlattenedRuns source = flattenStyledRuns(original);
        return buildComponentFromRuns(alignTranslatedRuns(source.runs(), source.fullText(), paragraph.text(),
                source.emptyFallbackStyle(), paragraph.spans()), source.emptyFallbackStyle());
    }

    private static List<StyledRun> alignTranslatedRuns(List<StyledRun> srcRuns,
                                                String fullText,
                                                String translatedText,
                                                Style emptyFallbackStyle) {
        return alignTranslatedRuns(srcRuns, fullText, translatedText, emptyFallbackStyle, List.of());
    }

    private static List<StyledRun> alignTranslatedRuns(List<StyledRun> srcRuns, String fullText, String translatedText,
                                                       Style emptyFallbackStyle, List<CaptureTemplate.Span> captures) {
        String cleanTranslated = translatedText;
        Style[] overrideStyles = null;
        if (fullText.indexOf('§') < 0 && translatedText.indexOf('§') >= 0) {
            StringBuilder cleanSb = new StringBuilder(translatedText.length());
            List<Style> ovList = new ArrayList<>(translatedText.length());
            Style activeOverride = Style.EMPTY;
            for (int k = 0; k < translatedText.length(); k++) {
                char c = translatedText.charAt(k);
                if (c == '§' && k + 1 < translatedText.length()) {
                    char code = translatedText.charAt(k + 1);
                    ChatFormatting fmt = ChatFormatting.getByCode(code);
                    if (fmt != null) {
                        activeOverride = (fmt == ChatFormatting.RESET)
                                ? Style.EMPTY
                                : activeOverride.applyLegacyFormat(fmt);
                        k++;
                        continue;
                    }
                }
                cleanSb.append(c);
                ovList.add(activeOverride);
            }
            cleanTranslated = cleanSb.toString();
            if (cleanTranslated.isEmpty()) {
                return List.of();
            }
            overrideStyles = ovList.toArray(new Style[0]);
        }

        Style[] srcStyles = buildCharStyles(srcRuns, fullText.length());
        int M = fullText.length();
        int N = cleanTranslated.length();
        Style[] dstStyles = new Style[N];

        if ((long) M * N > 100_000L) {
            for (int j = 0; j < N; j++) {
                int mapped = Math.min(M - 1, (int) (((j + 0.5) * M) / N));
                dstStyles[j] = srcStyles[mapped];
            }
        } else {
            int[][] dp = new int[M + 1][N + 1];
            for (int i = M - 1; i >= 0; i--) {
                char sc = fullText.charAt(i);
                for (int j = N - 1; j >= 0; j--) {
                    if (charsMatch(sc, cleanTranslated.charAt(j))) {
                        dp[i][j] = 1 + dp[i + 1][j + 1];
                    } else {
                        dp[i][j] = Math.max(dp[i + 1][j], dp[i][j + 1]);
                    }
                }
            }

            List<int[]> anchors = new ArrayList<>();
            int i = 0;
            int j = 0;
            int lastSrc = -2;
            int lastDst = -2;
            while (i < M && j < N) {
                if (charsMatch(fullText.charAt(i), cleanTranslated.charAt(j))) {
                    boolean contiguous = (i == lastSrc + 1 && j == lastDst + 1);
                    if (!contiguous && dp[i][j] == dp[i + 1][j]
                            && hasCloserSourceMatch(fullText, cleanTranslated, srcStyles, dp, i, j, M, N)) {
                        i++;
                        continue;
                    }
                    if (!contiguous && dp[i][j] == dp[i][j + 1]
                            && hasCloserTargetMatch(fullText, cleanTranslated, dp, i, j, M, N)) {
                        j++;
                        continue;
                    }
                    anchors.add(new int[]{i, j});
                    lastSrc = i;
                    lastDst = j;
                    i++;
                    j++;
                } else if (dp[i + 1][j] > dp[i][j + 1]) {
                    i++;
                } else if (dp[i + 1][j] < dp[i][j + 1]) {
                    j++;
                } else if (isProtectedBoundarySpace(fullText, srcStyles, i, M)
                        && cleanTranslated.indexOf(' ', j) >= 0) {
                    j++;
                } else if (i / (double) M <= j / (double) N) {
                    i++;
                } else {
                    j++;
                }
            }
            anchors.add(new int[]{M, N});

            int prevSrc = 0;
            int prevDst = 0;
            for (int[] anchor : anchors) {
                int srcIdx = anchor[0];
                int dstIdx = anchor[1];
                int gapDst = dstIdx - prevDst;
                int effectivePrevSrc = prevSrc;
                if (gapDst > 0 && prevDst > 0 && cleanTranslated.charAt(prevDst - 1) == '：'
                        && prevSrc > 0 && prevSrc + 1 < srcIdx
                        && fullText.charAt(prevSrc - 1) == ':' && fullText.charAt(prevSrc) == ' '
                        && cleanTranslated.charAt(prevDst) != ' ') {
                    effectivePrevSrc = prevSrc + 1;
                }
                int gapSrc = srcIdx - effectivePrevSrc;
                for (int t = prevDst; t < dstIdx; t++) {
                    if (gapSrc > 0) {
                        int mapped = Math.min(srcIdx - 1, effectivePrevSrc + (int) (((t - prevDst + 0.5) * gapSrc) / gapDst));
                        dstStyles[t] = srcStyles[mapped];
                    } else {
                        dstStyles[t] = prevDst > 0
                                ? dstStyles[prevDst - 1]
                                : (srcIdx < M ? srcStyles[srcIdx] : emptyFallbackStyle);
                    }
                }
                if (srcIdx < M && dstIdx < N) {
                    dstStyles[dstIdx] = srcStyles[srcIdx];
                }
                prevSrc = srcIdx + 1;
                prevDst = dstIdx + 1;
            }
        }

        // Named captures keep their original styling when moved in the localized sentence.
        if (overrideStyles == null && fullText.indexOf('§') < 0) {
            for (CaptureTemplate.Span capture : captures) {
                int sourceStart = capture.sourceStart();
                int sourceEnd = capture.sourceEnd();
                int outputStart = capture.outputStart();
                int outputEnd = capture.outputEnd();
                if (sourceStart < 0 || sourceEnd > M || sourceEnd <= sourceStart
                        || outputStart < 0 || outputEnd > N || outputEnd <= outputStart) continue;
                for (int index = outputStart; index < outputEnd; index++) {
                    int offset = (index - outputStart) * (sourceEnd - sourceStart) / (outputEnd - outputStart);
                    dstStyles[index] = srcStyles[sourceStart + offset];
                }
            }
        }

        if (overrideStyles != null) {
            for (int t = 0; t < N; t++) {
                Style ov = overrideStyles[t];
                if (ov != null && !ov.isEmpty()) {
                    dstStyles[t] = ov.applyTo(dstStyles[t]);
                }
            }
        }

        List<StyledRun> outRuns = new ArrayList<>();
        int runStart = 0;
        for (int t = 1; t <= N; t++) {
            if (t == N || !Objects.equals(dstStyles[t], dstStyles[runStart])) {
                outRuns.add(new StyledRun(cleanTranslated.substring(runStart, t), dstStyles[runStart]));
                runStart = t;
            }
        }
        return outRuns;
    }

    private record StyledRun(String text, Style style) {}

    private record FlattenedRuns(List<StyledRun> runs, Style emptyFallbackStyle) {
        String fullText() {
            StringBuilder sb = new StringBuilder();
            for (StyledRun r : runs) {
                sb.append(r.text());
            }
            return sb.toString();
        }
    }

    private static Style translateHoverInStyle(Style style, UnaryOperator<String> translate) {
        if (style == null || translate == null) {
            return style;
        }
        if (style.getHoverEvent() instanceof net.minecraft.network.chat.HoverEvent.ShowText(Component hoverVal) && hoverVal != null) {
            Component translatedHover = translateStyledLine(hoverVal, translate);
            if (!Objects.equals(translatedHover, hoverVal)) {
                return style.withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(translatedHover));
            }
        }
        return style;
    }

    private static FlattenedRuns flattenStyledRuns(Component comp) {
        return flattenStyledRuns(comp, null);
    }

    private static FlattenedRuns flattenStyledRuns(Component comp, UnaryOperator<String> translate) {
        List<StyledRun> runs = new ArrayList<>();
        Style[] firstVisitedStyle = new Style[1];
        comp.visit((style, text) -> {
            Style s = translateHoverInStyle(style != null ? style : Style.EMPTY, translate);
            if (firstVisitedStyle[0] == null) {
                firstVisitedStyle[0] = s;
            }
            if (text != null && !text.isEmpty()) {
                if (!runs.isEmpty() && Objects.equals(runs.get(runs.size() - 1).style(), s)) {
                    StyledRun prev = runs.remove(runs.size() - 1);
                    runs.add(new StyledRun(prev.text() + text, s));
                } else {
                    runs.add(new StyledRun(text, s));
                }
            }
            return Optional.empty();
        }, Style.EMPTY);
        Style fallback = firstVisitedStyle[0] != null
                ? firstVisitedStyle[0]
                : translateHoverInStyle(comp.getStyle() != null ? comp.getStyle() : Style.EMPTY, translate);
        return new FlattenedRuns(runs, fallback);
    }

    private static MutableComponent buildComponentFromRuns(List<StyledRun> rawRuns, Style emptyStyle) {
        List<StyledRun> merged = new ArrayList<>();
        for (StyledRun r : rawRuns) {
            if (r.text() == null || r.text().isEmpty()) continue;
            Style s = r.style() != null ? r.style() : Style.EMPTY;
            if (!merged.isEmpty() && Objects.equals(merged.get(merged.size() - 1).style(), s)) {
                StyledRun prev = merged.remove(merged.size() - 1);
                merged.add(new StyledRun(prev.text() + r.text(), s));
            } else {
                merged.add(new StyledRun(r.text(), s));
            }
        }
        if (merged.isEmpty()) {
            return Component.empty().setStyle(emptyStyle != null ? emptyStyle : Style.EMPTY);
        }
        if (merged.size() == 1) {
            return Component.literal(merged.get(0).text()).setStyle(merged.get(0).style());
        }
        MutableComponent root = Component.empty();
        for (StyledRun r : merged) {
            root.append(Component.literal(r.text()).setStyle(r.style()));
        }
        return root;
    }

    private static Style[] buildCharStyles(List<StyledRun> runs, int length) {
        Style[] charStyles = new Style[length];
        int pos = 0;
        for (StyledRun r : runs) {
            String t = r.text();
            Style s = r.style() != null ? r.style() : Style.EMPTY;
            for (int k = 0; k < t.length() && pos < length; k++) {
                charStyles[pos++] = s;
            }
        }
        return charStyles;
    }

    private static void appendSubRuns(List<StyledRun> out, List<StyledRun> runs, int start, int end) {
        if (start >= end) return;
        int pos = 0;
        for (StyledRun r : runs) {
            String t = r.text();
            int rStart = pos;
            int rEnd = pos + t.length();
            pos = rEnd;
            if (rEnd <= start || rStart >= end) continue;
            int s = Math.max(start, rStart) - rStart;
            int e = Math.min(end, rEnd) - rStart;
            if (s < e) {
                out.add(new StyledRun(t.substring(s, e), r.style()));
            }
        }
    }

    private static Style dominantStyle(Style[] charStyles, String fullText, int start, int end) {
        if (charStyles.length == 0 || start >= end) {
            return Style.EMPTY;
        }
        int s = Math.max(0, start);
        int e = Math.min(charStyles.length, end);
        if (s >= e) return Style.EMPTY;
        Map<Style, Integer> counts = new LinkedHashMap<>();
        for (int i = s; i < e; i++) {
            if (!Character.isWhitespace(fullText.charAt(i))) {
                counts.merge(charStyles[i], 1, Integer::sum);
            }
        }
        if (counts.isEmpty()) {
            return charStyles[s];
        }
        Style best = charStyles[s];
        int bestCount = -1;
        for (Map.Entry<Style, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > bestCount) {
                bestCount = entry.getValue();
                best = entry.getKey();
            }
        }
        return best;
    }

    private static int skipWhitespace(String text, int start, int limit) {
        int idx = start;
        while (idx < limit && Character.isWhitespace(text.charAt(idx))) {
            idx++;
        }
        return idx;
    }

    private static int trimEnd(String text, int minIdx, int limit) {
        int idx = limit;
        while (idx > minIdx && Character.isWhitespace(text.charAt(idx - 1))) {
            idx--;
        }
        return idx;
    }

    private static boolean charsMatch(char src, char dst) {
        return src == dst || (src == ':' && dst == '：');
    }

    private static boolean isProtectedBoundarySpace(String src, Style[] srcStyles, int i, int M) {
        if (srcStyles == null || i < 0 || i >= M || src.charAt(i) != ' ') {
            return false;
        }
        if (i > 0 && src.charAt(i - 1) == ':') {
            return false;
        }
        return (i > 0 && !Objects.equals(srcStyles[i], srcStyles[i - 1]))
                || (i + 1 < M && !Objects.equals(srcStyles[i], srcStyles[i + 1]));
    }

    private static boolean hasCloserSourceMatch(String src, String dst, Style[] srcStyles, int[][] dp, int i, int j, int M, int N) {
        char targetChar = dst.charAt(j);
        int curScore = dp[i][j];
        double curDist = Math.abs(i / (double) M - j / (double) N);
        boolean afterColon = (targetChar == ' ' && i > 0 && src.charAt(i - 1) == ':');
        boolean atStyleBoundary = (srcStyles != null)
                && ((i > 0 && !Objects.equals(srcStyles[i], srcStyles[i - 1]))
                || (i + 1 < M && !Objects.equals(srcStyles[i], srcStyles[i + 1])));
        for (int i2 = i + 1; i2 < M; i2++) {
            if (dp[i2][j] < curScore) break;
            if (charsMatch(src.charAt(i2), targetChar)) {
                boolean forwardContiguous = (i2 + 1 < M && j + 1 < N
                        && charsMatch(src.charAt(i2 + 1), dst.charAt(j + 1))
                        && dp[i2 + 1][j + 1] + 1 == dp[i2][j]);
                if (forwardContiguous) {
                    return true;
                }
                if (targetChar == ' ' && srcStyles != null && !afterColon && atStyleBoundary) {
                    boolean i2AtBoundary = (i2 > 0 && !Objects.equals(srcStyles[i2], srcStyles[i2 - 1]))
                            || (i2 + 1 < M && !Objects.equals(srcStyles[i2], srcStyles[i2 + 1]));
                    if (!Objects.equals(srcStyles[i], srcStyles[i2]) || !i2AtBoundary) {
                        continue;
                    }
                }
                double d2 = Math.abs(i2 / (double) M - j / (double) N);
                if (d2 + 1e-9 < curDist) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasCloserTargetMatch(String src, String dst, int[][] dp, int i, int j, int M, int N) {
        char srcChar = src.charAt(i);
        int curScore = dp[i][j];
        double curDist = Math.abs(i / (double) M - j / (double) N);
        for (int j2 = j + 1; j2 < N; j2++) {
            if (dp[i][j2] < curScore) break;
            if (charsMatch(srcChar, dst.charAt(j2))) {
                if (j2 + 1 < N && i + 1 < M
                        && charsMatch(src.charAt(i + 1), dst.charAt(j2 + 1))
                        && dp[i + 1][j2 + 1] + 1 == dp[i][j2]) {
                    return true;
                }
                double d2 = Math.abs(i / (double) M - j2 / (double) N);
                if (d2 + 1e-9 < curDist) {
                    return true;
                }
            }
        }
        return false;
    }

    private String lookupItemDictionary(String nbtItemId, String baseName, String normId1, String normId2) {
        try {
            com.hypixel.translation.core.TranslationCore core = com.hypixel.translation.core.TranslationCore.getInstance();
            if (core == null || core.getItemDictionary() == null) return null;
            com.hypixel.translation.core.ItemDictionary itemDict = core.getItemDictionary();
            com.hypixel.translation.core.ItemTranslationRecord rec = null;
            if (nbtItemId != null && !nbtItemId.isEmpty()) {
                rec = itemDict.get(nbtItemId);
            }
            if (rec == null && normId1 != null) {
                rec = itemDict.get(normId1);
            }
            if (rec == null && normId2 != null) {
                rec = itemDict.get(normId2);
            }
            if (rec == null && baseName != null) {
                rec = itemDict.getByEnglishName(baseName);
            }
            if (rec != null && rec.name_zh_tw() != null && !rec.name_zh_tw().isBlank()
                    && !rec.name_zh_tw().equals(rec.name_en())) {
                return tokenDict != null ? tokenDict.resolveString(rec.name_zh_tw()) : rec.name_zh_tw();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public boolean hasKnownItem(ItemStack stack) {
        if (stack == null) return false;
        String itemId = extractNbtAttributes(stack).itemId();
        ItemDictionary dictionary = resolveItemDictionary();
        return itemId != null && dictionary != null && dictionary.get(itemId) != null;
    }

    public Map<Integer, ItemLoreOverrides.Match> matchEditedItemLore(
            ItemStack stack, List<Component> tooltip) {
        ItemDictionary dictionary = resolveItemDictionary();
        if (dictionary == null || !dictionary.hasEditedLore() || tooltip.size() < 2 || tooltip.getFirst() == null) {
            return Map.of();
        }
        NbtAttributes nbt = extractNbtAttributes(stack);
        String baseName = nbt.itemId() == null ? decomposeName(tooltip.getFirst().getString(), nbt.modifier()).baseName() : null;
        if (!dictionary.hasEditedLore(nbt.itemId(), baseName)) return Map.of();
        return dictionary.matchEditedLore(nbt.itemId(), baseName,
                tooltip.stream().map(line -> line == null ? "" : line.getString()).toList());
    }

    public String getCleanEnglishName(ItemStack stack) {
        if (stack == null) return "";
        String rawName = stack.getHoverName().getString();
        String nbtMod = null;
        try {
            CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
            if (cd != null) {
                CompoundTag tag = cd.copyTag();
                CompoundTag extra = tag.getCompoundOrEmpty("ExtraAttributes");
                String mod = extra.getStringOr("modifier", "");
                if (!mod.isEmpty()) {
                    nbtMod = mod;
                }
            }
        } catch (Throwable ignored) {}
        return getCleanEnglishName(rawName, nbtMod);
    }

    public String getCleanEnglishName(String rawDisplayName, String nbtModifier) {
        DecomposedName d = decomposeName(rawDisplayName, nbtModifier);
        return d.baseName.replaceAll("§[0-9a-fk-or]", "").trim();
    }

    public List<Component> translateTooltip(ItemStack stack, List<Component> originalTooltip, boolean isAltOrShiftDown) {
        if (originalTooltip == null) return List.of();
        if (isAltOrShiftDown) {
            return new ArrayList<>(originalTooltip); // Revert to pure original English
        }

        List<Component> translated = new ArrayList<>();
        for (int i = 0; i < originalTooltip.size(); i++) {
            Component line = originalTooltip.get(i);
            if (i == 0) {
                translated.add(translateItemNameComponent(stack, line));
            } else {
                translated.add(translateLoreComponent(line));
            }
        }
        return translated;
    }

    public String fallbackTranslate(String name) {
        if (name == null) return "";
        for (Map.Entry<String, String> entry : FALLBACK_SUFFIXES) {
            String suffix = entry.getKey();
            if (name.endsWith(suffix) && name.length() > suffix.length()) {
                return name.substring(0, name.length() - suffix.length()).trim() + entry.getValue();
            }
        }
        return name;
    }
}
