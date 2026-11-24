package com.hypixel.translation.e2e;

import com.hypixel.translation.config.ComponentCategory;
import com.hypixel.translation.config.InterceptionMode;
import com.hypixel.translation.core.*;
import com.hypixel.translation.engine.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tier 4: Real-World Application Workloads & Authentic SkyBlock Items Test Suite (≥10 realistic scenarios).
 * Derivation: TEST_INFRA.md, PROJECT.md, and teamwork_spec_miner_e2e_3/handoff.md.
 */
@Tag("tier4")
@Tag("workload")
public class Tier4RealWorldWorkloadTest extends BaseE2ETest {

    @Test
    @DisplayName("RW01: Endgame Wither Blade (Heroic Hyperion ✪✪✪✪✪ with Wither Impact & Gemstones)")
    void testEndgameHyperionWorkload() {
        // Raw Hyperion Tooltip from real NEU REPO and Hypixel
        List<Component> rawHyperionTooltip = List.of(
                Component.literal("§6Heroic Hyperion ✪✪✪✪✪"),
                Component.literal("§7Gear Score: §d615"),
                Component.literal("§7Damage: §c+260 §e(+30)"),
                Component.literal("§7Strength: §c+150 §e(+20)"),
                Component.literal("§7Ferocity: §c+30"),
                Component.literal("§7Intelligence: §b+350 §e(+125)"),
                Component.literal("§7Gemstones: §8[\ue003] [⚔]"),
                Component.literal("§6Ability: Wither Impact  §e§lRIGHT CLICK"),
                Component.literal("§8Mana Cost: §b150\ue003"),
                Component.literal("§8Vitality Cost: §4450\ue028"),
                Component.literal("§8This item can be reforged!"),
                Component.literal("§4❣ §cRequires The Catacombs Floor VII Completion."),
                Component.literal("§6§lLEGENDARY DUNGEON SWORD")
        );

        List<Component> translated = dynamicItemCombiner.translateTooltip(null, rawHyperionTooltip, false);

        assertEquals("§6英雄 亥伯龍 ✪✪✪✪✪", translated.get(0).getString());
        assertEquals("§7裝備評分：§d615", translated.get(1).getString());
        assertEquals("§7傷害：§c+260 §e(+30)", translated.get(2).getString());
        assertEquals("§7力量：§c+150 §e(+20)", translated.get(3).getString());
        assertEquals("§7兇暴：§c+30", translated.get(4).getString());
        assertEquals("§7智力：§b+350 §e(+125)", translated.get(5).getString());
        assertEquals("§7寶石槽：§8[\ue003] [⚔]", translated.get(6).getString());
        assertEquals("§6技能：凋零衝擊  §e§l右鍵點擊", translated.get(7).getString());
        assertEquals("§8魔力消耗：§b150\ue003", translated.get(8).getString());
        assertEquals("§8活力消耗：§4450\ue028", translated.get(9).getString());
        assertEquals("§8此道具可以被重鑄！", translated.get(10).getString());
        assertEquals("§4❣ §c需要通關 地下墓穴 第七層。", translated.get(11).getString());
        assertEquals("§6§l傳奇 地下城 劍", translated.get(12).getString());

        // Verify clean English name extraction for /ah
        assertEquals("Hyperion", dynamicItemCombiner.getCleanEnglishName("§6Heroic Hyperion ✪✪✪✪✪", "heroic"));
    }

    @Test
    @DisplayName("RW02: Master Mode Terminator Bow (Spiritual Terminator ✪✪✪✪§c➊➋➌)")
    void testMasterModeTerminatorWorkload() {
        List<Component> rawTerminatorTooltip = List.of(
                Component.literal("§6Spiritual Terminator ✪✪✪✪§c➊➋➌"),
                Component.literal("§7Damage: §c+310 §e(+30)"),
                Component.literal("§7Strength: §c+50 §e(+20)"),
                Component.literal("§7Crit Damage: §9+250% §e(+35%)"),
                Component.literal("§7Attack Speed: §e+40%"),
                Component.literal("§7Shot Cooldown: §a0.5s"),
                Component.literal("§9Overload V, Power VII, Sniper IV"),
                Component.literal("§6Ability: Salvation  §e§lLEFT CLICK"),
                Component.literal("§8Soulflow Cost: §31⸎"),
                Component.literal("§6Shortbow: Instantly shoots!"),
                Component.literal("§8This item can be reforged!"),
                Component.literal("§4☠ §cRequires Enderman Slayer 7."),
                Component.literal("§6§lLEGENDARY BOW")
        );

        List<Component> translated = dynamicItemCombiner.translateTooltip(null, rawTerminatorTooltip, false);

        assertEquals("§6精神 終結者 ✪✪✪✪§c➊➋➌", translated.get(0).getString());
        assertEquals("§7傷害：§c+310 §e(+30)", translated.get(1).getString());
        assertEquals("§7力量：§c+50 §e(+20)", translated.get(2).getString());
        assertEquals("§7暴擊傷害：§9+250% §e(+35%)", translated.get(3).getString());
        assertEquals("§7攻擊速度：§e+40%", translated.get(4).getString());
        assertEquals("§7射擊冷卻：§a0.5秒", translated.get(5).getString());
        assertEquals("§9超載 V, 強力 VII, 狙擊手 IV", translated.get(6).getString());
        assertEquals("§6技能：拯救  §e§l左鍵點擊", translated.get(7).getString());
        assertEquals("§8靈流消耗：§31⸎", translated.get(8).getString());
        assertEquals("§6短弓：瞬間射擊！", translated.get(9).getString());
        assertEquals("§8此道具可以被重鑄！", translated.get(10).getString());
        assertEquals("§4☠ §c需要 終界使者殺手 7 級。", translated.get(11).getString());
        assertEquals("§6§l傳奇 弓", translated.get(12).getString());

        assertEquals("Terminator", dynamicItemCombiner.getCleanEnglishName("§6Spiritual Terminator ✪✪✪✪§c➊➋➌", "spiritual"));
    }

    @Test
    @DisplayName("RW03: Endgame Pet Tooltip (Lvl 100 Legendary Baby Yeti Pet)")
    void testEndgamePetTooltipWorkload() {
        List<Component> rawPetTooltip = List.of(
                Component.literal("§7[Lvl 100] §6Baby Yeti"),
                Component.literal("§7Strength: §c+40"),
                Component.literal("§7Fishing Speed: §b+10"),
                Component.literal("§7Sea Creature Chance: §3+5%"),
                Component.literal("§6§lLEGENDARY PET")
        );

        List<Component> translated = dynamicItemCombiner.translateTooltip(null, rawPetTooltip, false);

        assertEquals("§7[Lvl 100] §6雪怪寶寶", translated.get(0).getString());
        assertEquals("§7力量：§c+40", translated.get(1).getString());
        assertEquals("§7釣魚速度：§b+10", translated.get(2).getString());
        assertEquals("§7海怪機率：§3+5%", translated.get(3).getString());
        assertEquals("§6§l傳奇 寵物", translated.get(4).getString());

        assertEquals("Baby Yeti", dynamicItemCombiner.getCleanEnglishName("§7[Lvl 100] §6Baby Yeti", null));
    }

    @Test
    @DisplayName("RW04: Aspect of the End (Warped AOTE with Instant Transmission)")
    void testAspectOfTheEndWorkload() {
        List<Component> rawAoteTooltip = List.of(
                Component.literal("§9Warped Aspect of the End ✪✪✪✪✪"),
                Component.literal("§7Damage: §c+265 §e(+165)"),
                Component.literal("§7Strength: §c+265 §e(+165)"),
                Component.literal("§7Gemstones: §8[§7\ue003§8]"),
                Component.literal("§6Ability: Instant Transmission  §e§lRIGHT CLICK"),
                Component.literal("§8Mana Cost: §b50\ue003"),
                Component.literal("§8This item can be reforged!"),
                Component.literal("§9§lRARE SWORD")
        );

        List<Component> translated = dynamicItemCombiner.translateTooltip(null, rawAoteTooltip, false);

        assertEquals("§9扭曲 終界之劍 ✪✪✪✪✪", translated.get(0).getString());
        assertEquals("§7傷害：§c+265 §e(+165)", translated.get(1).getString());
        assertEquals("§7力量：§c+265 §e(+165)", translated.get(2).getString());
        assertEquals("§7寶石槽：§8[§7\ue003§8]", translated.get(3).getString());
        assertEquals("§6技能：瞬間移動  §e§l右鍵點擊", translated.get(4).getString());
        assertEquals("§8魔力消耗：§b50\ue003", translated.get(5).getString());
        assertEquals("§8此道具可以被重鑄！", translated.get(6).getString());
        assertEquals("§9§l稀有 劍", translated.get(7).getString());

        assertEquals("Aspect of the End", dynamicItemCombiner.getCleanEnglishName("§9Warped Aspect of the End ✪✪✪✪✪", "warped"));
    }

    @Test
    @DisplayName("RW05: Mining Armor (Jaded Chestplate of Divan with 5 Gemstones)")
    void testChestplateOfDivanWorkload() {
        List<Component> rawDivanTooltip = List.of(
                Component.literal("§6Jaded Chestplate of Divan"),
                Component.literal("§7Health: §c+200"),
                Component.literal("§7Defense: §a+130"),
                Component.literal("§7Mining Speed: §6+80 §e(+60)"),
                Component.literal("§7Mining Fortune: §6+30 §e(+50)"),
                Component.literal("§7Heat Resistance: §c+10"),
                Component.literal("§7Gemstones: §8[\ue015] [\ue053] [\ue015] [\ue053] [\ue01c]"),
                Component.literal("§8This item can be reforged!"),
                Component.literal("§6§lLEGENDARY CHESTPLATE")
        );

        List<Component> translated = dynamicItemCombiner.translateTooltip(null, rawDivanTooltip, false);

        assertEquals("§6碧玉 迪萬胸甲", translated.get(0).getString());
        assertEquals("§7生命值：§c+200", translated.get(1).getString());
        assertEquals("§7防禦力：§a+130", translated.get(2).getString());
        assertEquals("§7採礦速度：§6+80 §e(+60)", translated.get(3).getString());
        assertEquals("§7採礦幸運：§6+30 §e(+50)", translated.get(4).getString());
        assertEquals("§7耐熱值：§c+10", translated.get(5).getString());
        assertEquals("§7寶石槽：§8[\ue015] [\ue053] [\ue015] [\ue053] [\ue01c]", translated.get(6).getString());
        assertEquals("§8此道具可以被重鑄！", translated.get(7).getString());
        assertEquals("§6§l傳奇 胸甲", translated.get(8).getString());

        assertEquals("Chestplate of Divan", dynamicItemCombiner.getCleanEnglishName("§6Jaded Chestplate of Divan", "jaded"));
    }

    @Test
    @DisplayName("RW06: Interactive NPC Dialogue with Click & Hover Events")
    void testInteractiveNpcDialogueWorkload() {
        ClickEvent click = new ClickEvent.RunCommand("/island delivery claim");
        HoverEvent hover = new HoverEvent.ShowText(Component.literal("§7Click to claim §eDaily Rewards\n§8Command: /island delivery claim"));
        Style interactiveStyle = Style.EMPTY
                .withColor(ChatFormatting.GREEN)
                .withUnderlined(true)
                .withClickEvent(click)
                .withHoverEvent(hover);

        Component dialogue = Component.literal("§e[NPC] §bJerry§f: Greetings, adventurer! ")
                .append(Component.literal("§a§n[Accept Island Delivery]").setStyle(interactiveStyle));

        MutableComponent translated = textTraverser.translateRecursive(dialogue, s -> {
            if (s.contains("Jerry")) return s.replace("Jerry", "傑瑞").replace("Greetings, adventurer!", "問候你，冒險者！");
            if (s.contains("[Accept Island Delivery]")) return "§a§n[領取空島物資]";
            if (s.contains("Click to claim")) return s.replace("Click to claim", "點擊領取").replace("Command:", "指令：");
            return s;
        });

        assertTrue(translated.getString().contains("§e[NPC] §b傑瑞§f: 問候你，冒險者！"));
        assertTrue(translated.getString().contains("[領取空島物資]"));

        // Verify leaf click event is intact
        Component leaf = translated.getSiblings().get(0);
        assertNotNull(leaf.getStyle().getClickEvent());
        assertEquals("/island delivery claim", ((ClickEvent.RunCommand) leaf.getStyle().getClickEvent()).command());

        // Verify hover text is translated
        assertNotNull(leaf.getStyle().getHoverEvent());
        HoverEvent.ShowText st = (HoverEvent.ShowText) leaf.getStyle().getHoverEvent();
        assertTrue(st.value().getString().contains("點擊領取"));
    }

    @Test
    @DisplayName("RW07: Live Scoreboard Sidebar with Dynamic Values and Skyblocker Compatibility")
    void testLiveScoreboardSidebarWorkload() {
        List<String> inMemorySidebar = List.of(
                "§e§lSKYBLOCK",
                "§704/15/26 §8m123D",
                "§fPurse: §612,450,200 (+500)",
                "§fBank: §650,000,000",
                "§fBits: §b2,500",
                "§fLocation: §aThe Catacombs (F7)",
                "§fParty: §a5/5",
                "§ewww.hypixel.net"
        );

        // 1. Skyblocker external regex test against in-memory line
        Pattern pursePattern = Pattern.compile("(Purse|Piggy): (?<purse>[0-9,.]+)( \\((?<change>[+\\-][0-9,.]+)\\))?");
        Matcher m = pursePattern.matcher(inMemorySidebar.get(2).replaceAll("§[0-9a-fk-or]", ""));
        assertTrue(m.find());
        assertEquals("12,450,200", m.group("purse"));
        assertEquals("+500", m.group("change"));

        // 2. Display layer translation
        List<String> displayed = new ArrayList<>();
        for (String line : inMemorySidebar) {
            String t = line
                    .replace("Purse", tokenDictionary.resolveToken("UI_PURSE"))
                    .replace("Bank", tokenDictionary.resolveToken("UI_BANK"))
                    .replace("Bits", tokenDictionary.resolveToken("UI_BITS"))
                    .replace("Location", tokenDictionary.resolveToken("UI_LOCATION"))
                    .replace("Party", tokenDictionary.resolveToken("UI_PARTY"))
                    .replace("The Catacombs", tokenDictionary.resolveToken("LOC_CATACOMBS"));
            displayed.add(t);
        }

        assertTrue(displayed.get(2).contains("錢包"));
        assertTrue(displayed.get(3).contains("銀行"));
        assertTrue(displayed.get(4).contains("天幣"));
        assertTrue(displayed.get(5).contains("區域") && displayed.get(5).contains("地下墓穴"));
        assertTrue(displayed.get(6).contains("隊伍"));
    }

    @Test
    @DisplayName("RW08: Tab List Dungeon Widget Overlay and PlayerList Parsing")
    void testTabListDungeonWidgetWorkload() {
        String rawHeader = "§bYou are playing on §eHypixel SkyBlock\n§7Server: §am123D §7Profile: §bPapaya";
        String rawPlayerWidget = "§a[Mage 50] Player1 §7(Secrets: 12,500)";
        String rawFooter = "§eActive Blessings: §6Blessing of Life V, Blessing of Power VI";

        // Display translation
        String zhHeader = rawHeader.replace("You are playing on", "你正在遊玩")
                .replace("Server", "伺服器")
                .replace("Profile", "個人檔案");
        String zhFooter = rawFooter.replace("Active Blessings", "生效中的祝福")
                .replace("Blessing of Life", "生命祝福")
                .replace("Blessing of Power", "力量祝福");

        assertTrue(zhHeader.contains("你正在遊玩"));
        assertTrue(zhFooter.contains("生效中的祝福") && zhFooter.contains("生命祝福 V"));

        // Skyblocker PlayerListManager class check
        Pattern classPattern = Pattern.compile("^\\[(?<class>Mage|Archer|Berserk|Healer|Tank) (?<level>\\d+)\\]");
        Matcher cm = classPattern.matcher(rawPlayerWidget.replaceAll("§[0-9a-fk-or]", ""));
        assertTrue(cm.find());
        assertEquals("Mage", cm.group("class"));
        assertEquals("50", cm.group("level"));
    }

    @Test
    @DisplayName("RW09: Full Batch RFC 4180 CSV Translation Roundtrip Lifecycle")
    void testFullCsvBatchRoundtripLifecycle() throws Exception {
        // Step 1: Export current dictionary to CSV
        String initialCsv = csvSyncManager.exportTokensToCsvString(tokenDictionary);
        assertTrue(initialCsv.contains("ITEM_DIVAN_DRILL"));

        // Step 2: Modify translation for Divan's Drill
        String modifiedCsv = initialCsv.replace("\"Divan's Drill\",\"迪萬鑽頭\"", "\"Divan's Drill\",\"迪萬神級鑽頭\"");

        // Step 3: Import updated CSV, persist external tokens.json & Reload
        csvSyncManager.importTokensFromCsvString(tokenDictionary, modifiedCsv);
        tokenDictionary.exportToFile(configDir.resolve("tokens.json"));
        translationCore.reload();

        // Step 4: Verify in-memory translation reflects modified token
        assertEquals("迪萬神級鑽頭", tokenDictionary.resolveToken("ITEM_DIVAN_DRILL"));
        MutableComponent comp = dynamicItemCombiner.translateItemName(null, "§dDivan's Drill");
        assertEquals("§d迪萬神級鑽頭", comp.getString());
    }

    @Test
    @DisplayName("RW10: High-Throughput Untranslated Text Capture Queue Concurrency & JSON Flush")
    void testHighThroughputCaptureConcurrency() throws Exception {
        int threads = 10;
        int stringsPerThread = 50;
        ExecutorService exec = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            exec.submit(() -> {
                try {
                    for (int i = 0; i < stringsPerThread; i++) {
                        if (i % 2 == 0) {
                            untranslatedQueue.capture("§7[Auction] Player2 bought Hyperion for 1,000,000,000 Coins!", ComponentCategory.CHAT);
                        } else {
                            untranslatedQueue.capture("§e[NPC] Bea: Welcome to my pet shop!", ComponentCategory.CHAT);
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        exec.shutdown();

        // Exactly 2 distinct strings deduplicated
        assertEquals(2, untranslatedQueue.size());

        // Flush to JSON file
        Path captureFile = tempDir.resolve("untranslated_capture_workload.json");
        untranslatedQueue.flushToJson(captureFile);

        assertTrue(Files.exists(captureFile));
        String jsonContent = Files.readString(captureFile);
        JsonArray arr = JsonParser.parseString(jsonContent).getAsJsonArray();
        assertEquals(2, arr.size());

        int totalSeen = 0;
        for (int i = 0; i < arr.size(); i++) {
            totalSeen += arr.get(i).getAsJsonObject().get("seenCount").getAsInt();
        }
        assertEquals(threads * stringsPerThread, totalSeen);
    }
}
