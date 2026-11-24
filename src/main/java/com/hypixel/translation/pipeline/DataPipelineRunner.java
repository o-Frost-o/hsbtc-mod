package com.hypixel.translation.pipeline;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hypixel.translation.core.ItemTranslationRecord;
import com.hypixel.translation.core.TokenRecord;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class DataPipelineRunner {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static void main(String[] args) {
        Path outputDir = Path.of("src", "main", "resources", "assets", "hsbtr", "dictionaries");
        Path neuDir = NeuRepoCollector.DEFAULT_PRISM_REPO_PATH;
        boolean offline = false;
        boolean verbose = false;

        for (int i = 0; i < args.length; i++) {
            if ("--output-dir".equals(args[i]) && i + 1 < args.length) {
                outputDir = Path.of(args[++i]);
            } else if ("--neu-dir".equals(args[i]) && i + 1 < args.length) {
                neuDir = Path.of(args[++i]);
            } else if ("--offline".equals(args[i])) {
                offline = true;
            } else if ("--verbose".equals(args[i])) {
                verbose = true;
            }
        }

        System.out.println("[DataPipeline] Initializing Data Collection Pipeline...");
        System.out.println("[DataPipeline] Output Directory: " + outputDir.toAbsolutePath());
        System.out.println("[DataPipeline] NEU Repo Directory: " + neuDir.toAbsolutePath());

        // 1. NEU Repo Collection
        NeuRepoCollector neuCollector = new NeuRepoCollector(neuDir);
        NeuRepoCollector.NeuRepoResult neuResult = null;
        if (Files.exists(neuDir)) {
            try {
                neuResult = neuCollector.collect();
                System.out.println("[DataPipeline] Ingested from NEU: " + neuResult.items().size() + " items, "
                    + neuResult.reforges().size() + " reforges, "
                    + neuResult.reforgeStones().size() + " stones, "
                    + neuResult.collisionBaseNames().size() + " collisions.");
            } catch (Exception e) {
                System.err.println("[DataPipeline] Warning: Failed to collect from NEU: " + e.getMessage());
            }
        } else {
            System.out.println("[DataPipeline] Note: NEU Repo directory does not exist at " + neuDir);
        }

        // 2. Hypixel API Collection
        HypixelApiCollector.HypixelResult hypixelResult = null;
        if (!offline) {
            try {
                HypixelApiCollector hypixelCollector = new HypixelApiCollector();
                hypixelResult = hypixelCollector.collect();
                System.out.println("[DataPipeline] Ingested from Hypixel API: " + hypixelResult.items().size() + " items.");
            } catch (Exception e) {
                System.err.println("[DataPipeline] Warning: Hypixel API fetch skipped: " + e.getMessage());
            }
        }

        // 3. Fandom Wiki Collection
        Map<String, FandomWikiCollector.WikiReforge> wikiReforges = null;
        if (!offline) {
            try {
                FandomWikiCollector wikiCollector = new FandomWikiCollector();
                wikiReforges = wikiCollector.collect();
                System.out.println("[DataPipeline] Ingested from Fandom Wiki: " + wikiReforges.size() + " reforges.");
            } catch (Exception e) {
                System.err.println("[DataPipeline] Warning: Fandom Wiki fetch skipped: " + e.getMessage());
            }
        }

        // 4. Schema Normalization & Master Dictionaries Export
        SchemaNormalizer normalizer = new SchemaNormalizer();
        Map<String, TokenRecord> baseTokens = buildBaseTokenTaxonomy();
        Map<String, String> curatedTranslations = buildCuratedTranslations();

        try {
            normalizer.normalizeAndExport(
                neuResult,
                hypixelResult,
                wikiReforges,
                outputDir,
                baseTokens,
                curatedTranslations
            );

            System.out.println("[DataPipeline] Successfully written dictionaries to: " + outputDir.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("[DataPipeline] Error writing dictionaries: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static Map<String, TokenRecord> buildBaseTokenTaxonomy() {
        Map<String, TokenRecord> map = new TreeMap<>();

        // Stats
        addToken(map, "STAT_DAMAGE", "Damage", "傷害", "\ue050", "§c", "stat");
        addToken(map, "STAT_STRENGTH", "Strength", "力量", "\ue051", "§c", "stat");
        addToken(map, "STAT_DEFENSE", "Defense", "防禦力", "\ue023", "§a", "stat");
        addToken(map, "STAT_HEALTH", "Health", "生命值", "\ue020", "§c", "stat");
        addToken(map, "STAT_INTELLIGENCE", "Intelligence", "智力", "\ue003", "§b", "stat");
        addToken(map, "STAT_CRIT_CHANCE", "Crit Chance", "暴擊機率", "\ue02c", "§9", "stat");
        addToken(map, "STAT_CRIT_DAMAGE", "Crit Damage", "暴擊傷害", "\ue007", "§9", "stat");
        addToken(map, "STAT_SPEED", "Speed", "速度", "\ue022", "§f", "stat");
        addToken(map, "STAT_ATTACK_SPEED", "Attack Speed", "攻擊速度", "\ue021", "§e", "stat");
        addToken(map, "STAT_FEROCITY", "Ferocity", "兇暴", "\ue016", "§c", "stat");
        addToken(map, "STAT_MAGIC_FIND", "Magic Find", "魔法魔尋", "\ue01a", "§b", "stat");
        addToken(map, "STAT_PET_LUCK", "Pet Luck", "寵物幸運", "♣", "§d", "stat");
        addToken(map, "STAT_TRUE_DEFENSE", "True Defense", "真實防禦", "❂", "§f", "stat");
        addToken(map, "STAT_ABILITY_DAMAGE", "Ability Damage", "技能傷害", "๑", "§c", "stat");
        addToken(map, "STAT_MINING_SPEED", "Mining Speed", "採礦速度", "\ue015", "§6", "stat");
        addToken(map, "STAT_MINING_FORTUNE", "Mining Fortune", "採礦幸運", "\ue053", "§6", "stat");
        addToken(map, "STAT_FARMING_FORTUNE", "Farming Fortune", "農業幸運", "☘", "§6", "stat");
        addToken(map, "STAT_FORAGING_FORTUNE", "Foraging Fortune", "伐木幸運", "☘", "§6", "stat");
        addToken(map, "STAT_PRISTINE", "Pristine", "純淨度", "✧", "§5", "stat");
        addToken(map, "STAT_BREAKING_POWER", "Breaking Power", "破壞力", "Ⓟ", "§2", "stat");
        addToken(map, "STAT_FISHING_SPEED", "Fishing Speed", "釣魚速度", "\ue018", "§b", "stat");
        addToken(map, "STAT_SEA_CREATURE_CHANCE", "Sea Creature Chance", "海怪機率", "\ue017", "§3", "stat");
        addToken(map, "STAT_HEAT_RESISTANCE", "Heat Resistance", "耐熱值", "\ue01c", "§c", "stat");
        addToken(map, "STAT_GEAR_SCORE", "Gear Score", "裝備評分", "", "§d", "stat");

        // Rarities
        addToken(map, "RARITY_COMMON", "COMMON", "普通", "", "§f", "rarity");
        addToken(map, "RARITY_UNCOMMON", "UNCOMMON", "罕見", "", "§a", "rarity");
        addToken(map, "RARITY_RARE", "RARE", "稀有", "", "§9", "rarity");
        addToken(map, "RARITY_EPIC", "EPIC", "史詩", "", "§5", "rarity");
        addToken(map, "RARITY_LEGENDARY", "LEGENDARY", "傳奇", "", "§6", "rarity");
        addToken(map, "RARITY_MYTHIC", "MYTHIC", "神話", "", "§d", "rarity");
        addToken(map, "RARITY_DIVINE", "DIVINE", "神聖", "", "§b", "rarity");
        addToken(map, "RARITY_SPECIAL", "SPECIAL", "特殊", "", "§c", "rarity");
        addToken(map, "RARITY_VERY_SPECIAL", "VERY SPECIAL", "極度特殊", "", "§c", "rarity");
        addToken(map, "RARITY_ULTIMATE", "ULTIMATE", "終極", "", "§4", "rarity");
        addToken(map, "RARITY_ADMIN", "ADMIN", "管理員", "", "§4", "rarity");

        // Reforges
        addToken(map, "REFORGE_HEROIC", "Heroic", "英雄", "", "§9", "reforge");
        addToken(map, "REFORGE_FABLED", "Fabled", "傳說", "", "§9", "reforge");
        addToken(map, "REFORGE_ANCIENT", "Ancient", "遠古", "", "§9", "reforge");
        addToken(map, "REFORGE_WITHERED", "Withered", "凋零", "", "§9", "reforge");
        addToken(map, "REFORGE_SPIRITUAL", "Spiritual", "精神", "", "§9", "reforge");
        addToken(map, "REFORGE_JADED", "Jaded", "碧玉", "", "§9", "reforge");
        addToken(map, "REFORGE_WARPED", "Warped", "扭曲", "", "§9", "reforge");
        addToken(map, "REFORGE_MYTHIC", "Mythic", "神話", "", "§9", "reforge");
        addToken(map, "REFORGE_SPICY", "Spicy", "辛辣", "", "§9", "reforge");
        addToken(map, "REFORGE_SHARP", "Sharp", "鋒利", "", "§9", "reforge");
        addToken(map, "REFORGE_CLEAN", "Clean", "純淨", "", "§9", "reforge");
        addToken(map, "REFORGE_FIERCE", "Fierce", "兇猛", "", "§9", "reforge");
        addToken(map, "REFORGE_WISE", "Wise", "明智", "", "§9", "reforge");
        addToken(map, "REFORGE_GIANT", "Giant", "巨人", "", "§9", "reforge");
        addToken(map, "REFORGE_NECROTIC", "Necrotic", "死靈", "", "§9", "reforge");
        addToken(map, "REFORGE_LOVING", "Loving", "慈愛", "", "§9", "reforge");
        addToken(map, "REFORGE_PERFECT", "Perfect", "完美", "", "§9", "reforge");
        addToken(map, "REFORGE_SUBMERGED", "Submerged", "潛水", "", "§9", "reforge");
        addToken(map, "REFORGE_BLESSED", "Blessed", "祝福", "", "§9", "reforge");
        addToken(map, "REFORGE_BOUNTIFUL", "Bountiful", "富饒", "", "§9", "reforge");
        addToken(map, "REFORGE_TOIL", "Toil", "辛勞", "", "§9", "reforge");
        addToken(map, "REFORGE_REFINED", "Refined", "精煉", "", "§9", "reforge");
        addToken(map, "REFORGE_STELLAR", "Stellar", "恆星", "", "§9", "reforge");
        addToken(map, "REFORGE_GILDED", "Gilded", "鍍金", "", "§9", "reforge");
        addToken(map, "REFORGE_TITANIC", "Titanic", "泰坦", "", "§9", "reforge");
        addToken(map, "REFORGE_HEAVY", "Heavy", "沉重", "", "§9", "reforge");
        addToken(map, "REFORGE_LIGHT", "Light", "輕盈", "", "§9", "reforge");
        addToken(map, "REFORGE_PURE", "Pure", "純粹", "", "§9", "reforge");
        addToken(map, "REFORGE_SMART", "Smart", "聰慧", "", "§9", "reforge");
        addToken(map, "REFORGE_SUSPICIOUS", "Suspicious", "可疑", "", "§9", "reforge");
        addToken(map, "REFORGE_DIRTY", "Dirty", "骯髒", "", "§9", "reforge");

        // Skills
        addToken(map, "SKILL_COMBAT", "Combat", "戰鬥", "", "§c", "skill");
        addToken(map, "SKILL_MINING", "Mining", "採掘", "", "§8", "skill");
        addToken(map, "SKILL_FARMING", "Farming", "農業", "", "§6", "skill");
        addToken(map, "SKILL_FORAGING", "Foraging", "伐木", "", "§2", "skill");
        addToken(map, "SKILL_FISHING", "Fishing", "釣魚", "", "§9", "skill");
        addToken(map, "SKILL_ENCHANTING", "Enchanting", "附魔", "", "§3", "skill");
        addToken(map, "SKILL_ALCHEMY", "Alchemy", "鍊金", "", "§d", "skill");
        addToken(map, "SKILL_CARPENTRY", "Carpentry", "木工", "", "§e", "skill");
        addToken(map, "SKILL_RUNECRAFTING", "Runecrafting", "符文", "", "§5", "skill");
        addToken(map, "SKILL_SOCIAL", "Social", "社交", "", "§b", "skill");
        addToken(map, "SKILL_TAMING", "Taming", "馴化", "", "§a", "skill");
        addToken(map, "SKILL_DUNGEONEERING", "Dungeoneering", "地下城", "", "§c", "skill");

        // Collections
        addToken(map, "COLLECTION_FARMING", "Farming", "農業收藏", "", "§6", "collection");
        addToken(map, "COLLECTION_MINING", "Mining", "採掘收藏", "", "§8", "collection");
        addToken(map, "COLLECTION_COMBAT", "Combat", "戰鬥收藏", "", "§c", "collection");
        addToken(map, "COLLECTION_FORAGING", "Foraging", "伐木收藏", "", "§2", "collection");
        addToken(map, "COLLECTION_FISHING", "Fishing", "釣魚收藏", "", "§9", "collection");
        addToken(map, "COLLECTION_RIFT", "Rift", "裂隙收藏", "", "§5", "collection");

        // Mayors
        addToken(map, "MAYOR_AATROX", "Aatrox", "亞托克斯", "", "§6", "mayor");
        addToken(map, "MAYOR_COLE", "Cole", "科爾", "", "§6", "mayor");
        addToken(map, "MAYOR_DIANA", "Diana", "黛安娜", "", "§6", "mayor");
        addToken(map, "MAYOR_DIAZ", "Diaz", "迪亞茲", "", "§6", "mayor");
        addToken(map, "MAYOR_FINNEGAN", "Finnegan", "芬尼根", "", "§6", "mayor");
        addToken(map, "MAYOR_FOXY", "Foxy", "福克茜", "", "§6", "mayor");
        addToken(map, "MAYOR_MARINA", "Marina", "瑪麗娜", "", "§6", "mayor");
        addToken(map, "MAYOR_PAUL", "Paul", "保羅", "", "§6", "mayor");
        addToken(map, "MAYOR_SCORPIUS", "Scorpius", "天蠍", "", "§6", "mayor");
        addToken(map, "MAYOR_DERPY", "Derpy", "呆呆", "", "§6", "mayor");
        addToken(map, "MAYOR_JERRY", "Jerry", "傑瑞", "", "§6", "mayor");

        // UI & Locations
        addToken(map, "UI_AUCTION_HOUSE", "Auction House", "拍賣場", "", "§6", "ui");
        addToken(map, "UI_BAZAAR", "Bazaar", "市集", "", "§e", "ui");
        addToken(map, "UI_BANK", "Bank", "銀行", "", "§6", "ui");
        addToken(map, "UI_PURSE", "Purse", "錢包", "", "§6", "ui");
        addToken(map, "UI_BITS", "Bits", "天幣", "", "§b", "ui");
        addToken(map, "UI_LOCATION", "Location", "區域", "", "§a", "ui");
        addToken(map, "UI_PARTY", "Party", "隊伍", "", "§9", "ui");
        addToken(map, "UI_CONFIRM", "Confirm", "確認", "", "§a", "ui");
        addToken(map, "UI_CANCEL", "Cancel", "取消", "", "§c", "ui");
        addToken(map, "UI_BACK", "Go Back", "返回", "", "§a", "ui");
        addToken(map, "UI_CLOSE", "Close", "關閉", "", "§c", "ui");
        addToken(map, "UI_RIGHT_CLICK", "Right-click", "右鍵點擊", "", "§e", "ui");
        addToken(map, "UI_LEFT_CLICK", "Left-click", "左鍵點擊", "", "§e", "ui");
        addToken(map, "LOC_CATACOMBS", "The Catacombs", "地下墓穴", "", "§c", "location");

        // Categories
        addToken(map, "CATEGORY_SWORD", "SWORD", "劍", "⚔", "§6", "category");
        addToken(map, "CATEGORY_BOW", "BOW", "弓", "➶", "§6", "category");
        addToken(map, "CATEGORY_HELMET", "HELMET", "頭盔", "", "§6", "category");
        addToken(map, "CATEGORY_CHESTPLATE", "CHESTPLATE", "胸甲", "", "§6", "category");
        addToken(map, "CATEGORY_LEGGINGS", "LEGGINGS", "護腿", "", "§6", "category");
        addToken(map, "CATEGORY_BOOTS", "BOOTS", "靴子", "", "§6", "category");
        addToken(map, "CATEGORY_ACCESSORY", "ACCESSORY", "飾品", "", "§6", "category");
        addToken(map, "CATEGORY_FISHING_ROD", "FISHING ROD", "釣竿", "☂", "§6", "category");
        addToken(map, "CATEGORY_PICKAXE", "PICKAXE", "鎬", "⛏", "§6", "category");
        addToken(map, "CATEGORY_AXE", "AXE", "斧", "🪓", "§6", "category");
        addToken(map, "CATEGORY_SHOVEL", "SHOVEL", "鍬", "", "§6", "category");
        addToken(map, "CATEGORY_HOE", "HOE", "鋤", "", "§6", "category");
        addToken(map, "CATEGORY_DRILL", "DRILL", "鑽頭", "⸕", "§6", "category");
        addToken(map, "CATEGORY_PET", "PET", "寵物", "", "§d", "category");
        addToken(map, "CATEGORY_REFORGE_STONE", "REFORGE STONE", "重鑄石", "", "§9", "category");

        return map;
    }

    private static void addToken(Map<String, TokenRecord> map, String id, String en, String zh, String glyph, String color, String cat) {
        map.put(id, new TokenRecord(id, en, zh, glyph, color, cat));
    }

    public static Map<String, String> buildCuratedTranslations() {
        Map<String, String> t = new HashMap<>();
        t.put("HYPERION", "亥伯龍");
        t.put("Hyperion", "亥伯龍");
        t.put("TERMINATOR", "終結者");
        t.put("Terminator", "終結者");
        t.put("ASPECT_OF_THE_END", "終界之劍");
        t.put("Aspect of the End", "終界之劍");
        t.put("ASPECT_OF_THE_DRAGONS", "巨龍之劍");
        t.put("Aspect of the Dragons", "巨龍之劍");
        t.put("VALKYRIE", "女武神");
        t.put("SCYLLA", "斯庫拉");
        t.put("ASTRAEA", "阿斯特蕾亞");
        t.put("NECRON_CHESTPLATE", "涅克隆胸甲");
        t.put("STORM_CHESTPLATE", "風暴胸甲");
        t.put("GOLDOR_CHESTPLATE", "戈爾德胸甲");
        t.put("MAXOR_CHESTPLATE", "麥克索胸甲");
        t.put("SHADOW_ASSASSIN_CHESTPLATE", "暗影刺客胸甲");
        t.put("CHESTPLATE_OF_DIVAN", "迪萬胸甲");
        t.put("Chestplate of Divan", "迪萬胸甲");
        t.put("DIVAN_DRILL", "迪萬鑽頭");
        t.put("Divan's Drill", "迪萬鑽頭");
        t.put("BABY_YETI", "雪怪寶寶");
        t.put("Baby Yeti", "雪怪寶寶");
        t.put("ENDER_DRAGON", "終界龍");
        t.put("Ender Dragon", "終界龍");
        t.put("GOLDEN_DRAGON", "黃金龍");
        t.put("Golden Dragon", "黃金龍");
        t.put("ANCIENT_CLAW", "古代之爪");
        t.put("Ancient Claw", "古代之爪");
        t.put("WARPED_STONE", "扭曲之石");
        t.put("Warped Stone", "扭曲之石");
        t.put("HEAVY_HELMET", "重型頭盔");
        t.put("Heavy Helmet", "重型頭盔");
        t.put("TIER_BOOST", "階級提升");
        t.put("Tier Boost", "階級提升");
        return t;
    }
}
