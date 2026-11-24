package com.hypixel.translation.pipeline;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hypixel.translation.core.ItemTranslationRecord;
import com.hypixel.translation.core.TokenRecord;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SchemaNormalizer {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static final Pattern STAT_LINE_PATTERN = Pattern.compile(
        "^((?:§[0-9a-fklmnor])+)?([A-Za-z ]+):\\s*((?:§[0-9a-fklmnor])+)?([+-]?)([0-9]+(?:\\.[0-9]+)?%?)(.*)$"
    );

    private static final Pattern MANA_COST_PATTERN = Pattern.compile(
        "^((?:§[0-9a-fklmnor])*)Mana Cost:\\s*((?:§[0-9a-fklmnor])*)\\d+(.*)$"
    );

    private static final Pattern COOLDOWN_PATTERN = Pattern.compile(
        "^((?:§[0-9a-fklmnor])*)Cooldown:\\s*((?:§[0-9a-fklmnor])*)\\d+(?:\\.\\d+)?s(.*)$"
    );

    public static String normalizeStat(String raw) {
        if (raw == null || raw.isBlank()) return "UNKNOWN";
        String clean = raw.trim().toUpperCase().replace("-", "_").replace(" ", "_");
        return switch (clean) {
            case "DEFENCE", "DEFENSE" -> "DEFENSE";
            case "DAMAGE", "DMG" -> "DAMAGE";
            case "STRENGTH", "STR" -> "STRENGTH";
            case "CRITICAL_CHANCE", "CRIT_CHANCE", "CC" -> "CRIT_CHANCE";
            case "CRITICAL_DAMAGE", "CRIT_DAMAGE", "CD" -> "CRIT_DAMAGE";
            case "INTELLIGENCE", "INT", "MANA" -> "INTELLIGENCE";
            case "WALK_SPEED", "SPEED", "SPD" -> "SPEED";
            case "ATTACK_SPEED", "BONUS_ATTACK_SPEED", "BAS" -> "ATTACK_SPEED";
            case "FEROCITY", "FER" -> "FEROCITY";
            case "HEALTH", "HP" -> "HEALTH";
            case "HEALTH_REGEN", "HEALTH_REGENERATION" -> "HEALTH_REGEN";
            case "MAGIC_FIND", "MF" -> "MAGIC_FIND";
            case "PET_LUCK", "PL" -> "PET_LUCK";
            case "TRUE_DEFENSE", "TRUE_DEFENCE", "TD" -> "TRUE_DEFENSE";
            case "ABILITY_DAMAGE", "ABILITY_DAMAGE_PERCENT" -> "ABILITY_DAMAGE";
            case "SEA_CREATURE_CHANCE", "SCC" -> "SEA_CREATURE_CHANCE";
            case "FISHING_SPEED" -> "FISHING_SPEED";
            case "MINING_SPEED" -> "MINING_SPEED";
            case "MINING_FORTUNE" -> "MINING_FORTUNE";
            case "FARMING_FORTUNE" -> "FARMING_FORTUNE";
            case "FORAGING_FORTUNE" -> "FORAGING_FORTUNE";
            case "BREAKING_POWER" -> "BREAKING_POWER";
            case "PRISTINE" -> "PRISTINE";
            case "RIFT_TIME" -> "RIFT_TIME";
            default -> clean;
        };
    }

    public static String normalizeRarity(String rawRarity) {
        if (rawRarity == null || rawRarity.isBlank()) return "COMMON";
        String clean = rawRarity.trim().toUpperCase().replace("-", "_").replace(" ", "_");
        return switch (clean) {
            case "VERY_SPECIAL" -> "VERY_SPECIAL";
            case "SPECIAL" -> "SPECIAL";
            case "DIVINE" -> "DIVINE";
            case "MYTHIC" -> "MYTHIC";
            case "LEGENDARY" -> "LEGENDARY";
            case "EPIC" -> "EPIC";
            case "RARE" -> "RARE";
            case "UNCOMMON" -> "UNCOMMON";
            case "COMMON" -> "COMMON";
            case "ULTIMATE" -> "ULTIMATE";
            case "ADMIN" -> "ADMIN";
            default -> clean;
        };
    }

    public static String normalizeCategory(String rawCategory) {
        if (rawCategory == null || rawCategory.isBlank()) return "MISC";
        String clean = rawCategory.trim().toUpperCase().replace("-", "_").replace(" ", "_");
        return switch (clean) {
            case "SWORD" -> "SWORD";
            case "BOW" -> "BOW";
            case "ARMOR", "HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS" -> clean;
            case "ACCESSORY" -> "ACCESSORY";
            case "FISHING_ROD", "FISHING_WEAPON" -> "FISHING_ROD";
            case "PICKAXE", "AXE", "SHOVEL", "HOE", "DRILL" -> clean;
            case "PET" -> "PET";
            case "REFORGE_STONE" -> "REFORGE_STONE";
            default -> clean;
        };
    }

    public List<String> extractLoreTemplate(List<String> rawLore) {
        if (rawLore == null || rawLore.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> template = new ArrayList<>(rawLore.size());
        for (String line : rawLore) {
            Matcher manaMatcher = MANA_COST_PATTERN.matcher(line);
            if (manaMatcher.matches()) {
                String prefix = manaMatcher.group(1) != null ? manaMatcher.group(1) : "";
                String valPrefix = manaMatcher.group(2) != null ? manaMatcher.group(2) : "";
                String suffix = manaMatcher.group(3) != null ? manaMatcher.group(3) : "";
                template.add(prefix + "Mana Cost: " + valPrefix + "{0}" + suffix);
                continue;
            }

            Matcher cdMatcher = COOLDOWN_PATTERN.matcher(line);
            if (cdMatcher.matches()) {
                String prefix = cdMatcher.group(1) != null ? cdMatcher.group(1) : "";
                String valPrefix = cdMatcher.group(2) != null ? cdMatcher.group(2) : "";
                String suffix = cdMatcher.group(3) != null ? cdMatcher.group(3) : "";
                template.add(prefix + "Cooldown: " + valPrefix + "{0}s" + suffix);
                continue;
            }

            Matcher m = STAT_LINE_PATTERN.matcher(line);
            if (m.matches()) {
                String prefix = m.group(1) != null ? m.group(1) : "";
                String statLabel = m.group(2).trim();
                String valPrefix = m.group(3) != null ? m.group(3) : "";
                String sign = m.group(4) != null ? m.group(4) : "";
                String val = m.group(5);
                String suffix = m.group(6) != null ? m.group(6) : "";
                String normStat = normalizeStat(statLabel);
                template.add(prefix + "{STAT_" + normStat + "}: " + valPrefix + sign + "{0}" + suffix);
                continue;
            }

            template.add(line);
        }
        return template;
    }

    public Map<String, ItemTranslationRecord> mergeItems(
        JsonArray hypixelItems,
        Map<String, NeuRepoCollector.NeuItem> neuItems,
        Map<String, String> translations
    ) {
        Map<String, ItemTranslationRecord> result = new TreeMap<>();

        // 1. Ingest Hypixel official items
        if (hypixelItems != null) {
            for (JsonElement el : hypixelItems) {
                if (!el.isJsonObject()) continue;
                JsonObject obj = el.getAsJsonObject();
                String id = obj.get("id").getAsString();
                String nameEn = obj.has("name") ? obj.get("name").getAsString() : id;
                String category = obj.has("category") ? normalizeCategory(obj.get("category").getAsString()) : "MISC";
                String rarity = obj.has("tier") ? normalizeRarity(obj.get("tier").getAsString()) : "COMMON";
                String nameZhTw = translations.getOrDefault(id, translations.getOrDefault(nameEn, nameEn));

                List<String> loreTemplate = new ArrayList<>();
                if (neuItems.containsKey(id)) {
                    NeuRepoCollector.NeuItem neuItem = neuItems.get(id);
                    loreTemplate = extractLoreTemplate(neuItem.lore());
                }

                result.put(id, new ItemTranslationRecord(id, nameEn, nameZhTw, category, rarity, loreTemplate));
            }
        }

        // 2. Ingest NEU Repo items not present in Hypixel API (e.g. variants, dungeon items, pet tiers)
        for (Map.Entry<String, NeuRepoCollector.NeuItem> entry : neuItems.entrySet()) {
            String id = entry.getKey();
            if (result.containsKey(id)) continue;

            NeuRepoCollector.NeuItem item = entry.getValue();
            String rawDisplayName = item.displayname() != null ? item.displayname() : id;
            String nameEn = rawDisplayName.replaceAll("§[0-9a-fklmnor]", "").trim();
            String nameZhTw = translations.getOrDefault(id, translations.getOrDefault(nameEn, nameEn));
            List<String> loreTemplate = extractLoreTemplate(item.lore());

            result.put(id, new ItemTranslationRecord(id, nameEn, nameZhTw, "MISC", "COMMON", loreTemplate));
        }

        return result;
    }

    public void normalizeAndExport(
        NeuRepoCollector.NeuRepoResult neu,
        HypixelApiCollector.HypixelResult hypixel,
        Map<String, FandomWikiCollector.WikiReforge> wiki,
        Path outputDir,
        Map<String, TokenRecord> baseTokens,
        Map<String, String> translations
    ) throws IOException {
        Files.createDirectories(outputDir);

        // 1. Build tokens dictionary
        Map<String, TokenRecord> tokens = new TreeMap<>(baseTokens);

        // Add reforges from NEU
        if (neu != null && neu.reforges() != null) {
            for (String ref : neu.reforges().keySet()) {
                String tokenId = "REFORGE_" + ref.toUpperCase().replaceAll("[^A-Z0-9]", "_");
                if (!tokens.containsKey(tokenId)) {
                    tokens.put(tokenId, new TokenRecord(tokenId, ref, ref, "", "§9", "reforge"));
                }
            }
        }
        if (neu != null && neu.reforgeStones() != null) {
            for (NeuRepoCollector.NeuReforgeStone stone : neu.reforgeStones().values()) {
                String ref = stone.reforgeName();
                String tokenId = "REFORGE_" + ref.toUpperCase().replaceAll("[^A-Z0-9]", "_");
                if (!tokens.containsKey(tokenId)) {
                    tokens.put(tokenId, new TokenRecord(tokenId, ref, ref, "", "§9", "reforge"));
                }
            }
        }

        // Add reforges from Fandom Wiki
        if (wiki != null) {
            for (String ref : wiki.keySet()) {
                String tokenId = "REFORGE_" + ref.toUpperCase().replaceAll("[^A-Z0-9]", "_");
                if (!tokens.containsKey(tokenId)) {
                    tokens.put(tokenId, new TokenRecord(tokenId, ref, ref, "", "§9", "reforge"));
                }
            }
        }

        // Export tokens.json
        Path tokensPath = outputDir.resolve("tokens.json");
        Files.writeString(tokensPath, GSON.toJson(tokens), StandardCharsets.UTF_8);

        // 2. Build items dictionary
        JsonArray hypArray = null;
        if (hypixel != null && hypixel.items() != null) {
            hypArray = new JsonArray();
            for (HypixelApiCollector.HypixelItem hi : hypixel.items().values()) {
                JsonObject o = new JsonObject();
                o.addProperty("id", hi.id());
                o.addProperty("name", hi.name());
                o.addProperty("category", hi.category());
                o.addProperty("tier", hi.tier());
                hypArray.add(o);
            }
        }

        Map<String, NeuRepoCollector.NeuItem> neuItems = neu != null ? neu.items() : Map.of();
        Map<String, ItemTranslationRecord> items = mergeItems(hypArray, neuItems, translations);

        // Export items.json
        Path itemsPath = outputDir.resolve("items.json");
        Files.writeString(itemsPath, GSON.toJson(items), StandardCharsets.UTF_8);
    }
}
