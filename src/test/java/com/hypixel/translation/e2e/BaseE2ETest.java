package com.hypixel.translation.e2e;

import com.hypixel.translation.config.ComponentCategory;
import com.hypixel.translation.config.InterceptionMode;
import com.hypixel.translation.core.*;
import com.hypixel.translation.engine.*;

import com.google.gson.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Shared test fixture base class for 4-tier opaque-box E2E testing
 * and component test suites in the Hypixel SkyBlock Translation Mod (zh_tw).
 */
public abstract class BaseE2ETest {

    @TempDir
    public Path tempDir;

    protected Path configDir;
    protected TokenDictionary tokenDictionary;
    protected CsvSyncManager csvSyncManager;
    protected UntranslatedCaptureQueue untranslatedQueue;
    protected DynamicItemCombiner dynamicItemCombiner;
    protected LoreParser loreParser;
    protected RomanNumeralParser romanNumeralParser;
    protected TextTraverser textTraverser;
    protected InterceptionManager interceptionManager;
    protected TranslationCore translationCore;

    @BeforeEach
    public void setUpBase() throws Exception {
        configDir = tempDir.resolve("config");
        Files.createDirectories(configDir);
        com.hypixel.translation.config.ModConfig.getInstance().setConfigPath(configDir.resolve("config.json"));

        tokenDictionary = createStandardTokenDictionary();
        csvSyncManager = new CsvSyncManager();
        untranslatedQueue = new UntranslatedCaptureQueue(5000);
        interceptionManager = new InterceptionManager();
        romanNumeralParser = new RomanNumeralParser();
        loreParser = new LoreParser(tokenDictionary, romanNumeralParser);
        dynamicItemCombiner = new DynamicItemCombiner(tokenDictionary, loreParser);
        DynamicItemCombiner.setInstance(dynamicItemCombiner);
        InterceptionManager.setInstance(interceptionManager);
        textTraverser = new TextTraverser();
        translationCore = new TranslationCore(tokenDictionary, dynamicItemCombiner, loreParser, untranslatedQueue, interceptionManager, textTraverser);
    }

    @AfterEach
    public void tearDownBase() {
        com.hypixel.translation.config.ModConfig.getInstance().setConfigPath(
                java.nio.file.Paths.get("config", "hypixel_skyblock_translation", "config.json")
        );
    }

    // =========================================================================
    // Factory Helpers
    // =========================================================================

    public TokenDictionary createStandardTokenDictionary() {
        TokenDictionary dict = new TokenDictionary();
        // Stats
        dict.registerToken(new TokenRecord("STAT_DAMAGE", "Damage", "傷害", "\ue050", "§c", "stat"));
        dict.registerToken(new TokenRecord("STAT_STRENGTH", "Strength", "力量", "\ue051", "§c", "stat"));
        dict.registerToken(new TokenRecord("STAT_DEFENSE", "Defense", "防禦力", "\ue023", "§a", "stat"));
        dict.registerToken(new TokenRecord("STAT_HEALTH", "Health", "生命值", "\ue020", "§c", "stat"));
        dict.registerToken(new TokenRecord("STAT_INTELLIGENCE", "Intelligence", "智力", "\ue003", "§b", "stat"));
        dict.registerToken(new TokenRecord("STAT_CRIT_DAMAGE", "Crit Damage", "暴擊傷害", "\ue007", "§9", "stat"));
        dict.registerToken(new TokenRecord("STAT_CRIT_CHANCE", "Crit Chance", "暴擊機率", "\ue02c", "§9", "stat"));
        dict.registerToken(new TokenRecord("STAT_ATTACK_SPEED", "Attack Speed", "攻擊速度", "\ue021", "§e", "stat"));
        dict.registerToken(new TokenRecord("STAT_SPEED", "Speed", "速度", "\ue022", "§f", "stat"));
        dict.registerToken(new TokenRecord("STAT_MINING_SPEED", "Mining Speed", "採礦速度", "\ue015", "§6", "stat"));
        dict.registerToken(new TokenRecord("STAT_MINING_FORTUNE", "Mining Fortune", "採礦幸運", "\ue053", "§6", "stat"));
        dict.registerToken(new TokenRecord("STAT_FEROCITY", "Ferocity", "兇暴", "\ue016", "§c", "stat"));
        dict.registerToken(new TokenRecord("STAT_SEA_CREATURE_CHANCE", "Sea Creature Chance", "海怪機率", "\ue017", "§3", "stat"));
        dict.registerToken(new TokenRecord("STAT_FISHING_SPEED", "Fishing Speed", "釣魚速度", "\ue018", "§b", "stat"));
        dict.registerToken(new TokenRecord("STAT_MAGIC_FIND", "Magic Find", "魔法魔尋", "\ue01a", "§b", "stat"));
        dict.registerToken(new TokenRecord("STAT_HEAT_RESISTANCE", "Heat Resistance", "耐熱值", "\ue01c", "§c", "stat"));
        dict.registerToken(new TokenRecord("STAT_GEAR_SCORE", "Gear Score", "裝備評分", "", "§d", "stat"));

        // Reforges
        dict.registerToken(new TokenRecord("REFORGE_HEROIC", "Heroic", "英雄", "", "", "reforge"));
        dict.registerToken(new TokenRecord("REFORGE_FABLED", "Fabled", "傳說", "", "", "reforge"));
        dict.registerToken(new TokenRecord("REFORGE_ANCIENT", "Ancient", "遠古", "", "", "reforge"));
        dict.registerToken(new TokenRecord("REFORGE_WITHERED", "Withered", "凋零", "", "", "reforge"));
        dict.registerToken(new TokenRecord("REFORGE_SPIRITUAL", "Spiritual", "精神", "", "", "reforge"));
        dict.registerToken(new TokenRecord("REFORGE_JADED", "Jaded", "碧玉", "", "", "reforge"));
        dict.registerToken(new TokenRecord("REFORGE_WARPED", "Warped", "扭曲", "", "", "reforge"));
        dict.registerToken(new TokenRecord("REFORGE_MYTHIC", "Mythic", "神話", "", "", "reforge"));

        // Items
        dict.registerToken(new TokenRecord("ITEM_HYPERION", "Hyperion", "亥伯龍", "", "", "item"));
        dict.registerToken(new TokenRecord("ITEM_TERMINATOR", "Terminator", "終結者", "", "", "item"));
        dict.registerToken(new TokenRecord("ITEM_ASPECT_OF_THE_END", "Aspect of the End", "終界之劍", "", "", "item"));
        dict.registerToken(new TokenRecord("ITEM_BABY_YETI", "Baby Yeti", "雪怪寶寶", "", "", "item"));
        dict.registerToken(new TokenRecord("ITEM_DIVAN_CHESTPLATE", "Chestplate of Divan", "迪萬胸甲", "", "", "item"));
        dict.registerToken(new TokenRecord("ITEM_DIVAN_DRILL", "Divan's Drill", "迪萬鑽頭", "", "", "item"));
        dict.registerToken(new TokenRecord("ITEM_ANCIENT_CLAW", "Ancient Claw", "古代之爪", "", "", "item"));
        dict.registerToken(new TokenRecord("ITEM_WARPED_STONE", "Warped Stone", "扭曲之石", "", "", "item"));
        dict.registerToken(new TokenRecord("ITEM_HEAVY_HELMET", "Heavy Helmet", "重型頭盔", "", "", "item"));
        dict.registerToken(new TokenRecord("ITEM_TIER_BOOST", "Tier Boost", "階級提升", "", "", "item"));

        // Locations & UI
        dict.registerToken(new TokenRecord("LOC_CATACOMBS", "The Catacombs", "地下墓穴", "", "", "location"));
        dict.registerToken(new TokenRecord("UI_PURSE", "Purse", "錢包", "", "", "ui"));
        dict.registerToken(new TokenRecord("UI_BANK", "Bank", "銀行", "", "", "ui"));
        dict.registerToken(new TokenRecord("UI_BITS", "Bits", "天幣", "", "", "ui"));
        dict.registerToken(new TokenRecord("UI_LOCATION", "Location", "區域", "", "", "ui"));
        dict.registerToken(new TokenRecord("UI_PARTY", "Party", "隊伍", "", "", "ui"));

        // UI, HUD, Scoreboard & Holograms
        dict.registerToken(new TokenRecord("UI_MENU", "Menu", "選單", "", "", "ui"));
        dict.registerToken(new TokenRecord("UI_SKYBLOCK_MENU", "SkyBlock Menu", "SkyBlock 選單", "", "", "ui"));
        dict.registerToken(new TokenRecord("UI_ANVIL", "Anvil", "鐵砧", "", "", "ui"));
        dict.registerToken(new TokenRecord("UI_SKYBLOCK", "SKYBLOCK", "空島生存", "", "§e§l", "ui"));
        dict.registerToken(new TokenRecord("STAT_MANA", "Mana", "魔力", "\ue003", "§b", "stat"));
        dict.registerToken(new TokenRecord("STAT_MANA_PER_SEC", "Mana/s", "魔力/秒", "", "§a", "stat"));
        dict.registerToken(new TokenRecord("STAT_HP", "HP", "生命", "", "§a", "stat"));
        dict.registerToken(new TokenRecord("NPC_JERRY", "Jerry", "傑瑞", "", "§e", "npc"));
        dict.registerToken(new TokenRecord("UI_CLICK", "CLICK", "點擊", "", "§a", "ui"));
        dict.registerToken(new TokenRecord("UI_OBJECTIVE", "Objective:", "目標：", "", "§6", "ui"));
        dict.registerToken(new TokenRecord("LOC_FLOOR_7_ARENA", "Floor VII Boss Arena", "第七層首領競技場", "", "", "location"));
        dict.registerToken(new TokenRecord("BOSS_NECRON", "Corrupted Necron", "腐化涅克隆", "", "", "boss"));
        dict.registerToken(new TokenRecord("UI_GOLD", "GOLD", "黃金", "", "§6", "ui"));
        dict.registerToken(new TokenRecord("UI_CLICK_TO_COLLECT", "Click to collect!", "點擊領取！", "", "§e", "ui"));
        dict.registerToken(new TokenRecord("UI_ADMIN", "[ADMIN]", "[管理員]", "", "§c", "ui"));

        return dict;
    }

    // =========================================================================
    // Component Builder Helpers
    // =========================================================================

    public static MutableComponent literal(String text) {
        return Component.literal(text);
    }

    public static MutableComponent styled(String text, ChatFormatting... formattings) {
        return Component.literal(text).withStyle(formattings);
    }

    public static MutableComponent interactive(String text, String command, String hoverText) {
        Style style = Style.EMPTY
                .withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(hoverText)));
        return Component.literal(text).setStyle(style);
    }

    public static MutableComponent withHover(String text, String hoverText) {
        Style style = Style.EMPTY.withHoverEvent(new HoverEvent.ShowText(Component.literal(hoverText)));
        return Component.literal(text).setStyle(style);
    }

    public static MutableComponent withClick(String text, String command) {
        Style style = Style.EMPTY.withClickEvent(new ClickEvent.RunCommand(command));
        return Component.literal(text).setStyle(style);
    }

    // =========================================================================
    // In-Memory Contract Implementations (Delegating to Production Singletons/Classes)
    // =========================================================================

    public static class InterceptionManager extends com.hypixel.translation.interception.InterceptionManager {
        public InterceptionManager() {
            super();
        }
    }

    public static class TextTraverser extends com.hypixel.translation.engine.TextTraverser {
        public TextTraverser() {
            super();
        }
    }

    public static class TranslationCore extends com.hypixel.translation.core.TranslationCore {
        public TranslationCore(TokenDictionary tokenDictionary, DynamicItemCombiner combiner, LoreParser loreParser,
                               UntranslatedCaptureQueue untranslatedQueue, com.hypixel.translation.interception.InterceptionManager interceptionManager,
                               com.hypixel.translation.engine.TextTraverser textTraverser) {
            super(tokenDictionary, combiner, loreParser, untranslatedQueue, interceptionManager, textTraverser);
        }
    }
}
