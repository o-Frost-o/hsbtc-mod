package com.hypixel.translation.interception;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.translation.config.ComponentCategory;
import com.hypixel.translation.core.DialogueDictionary;
import com.hypixel.translation.config.ModConfig;
import com.hypixel.translation.core.TranslationCore;
import com.hypixel.translation.engine.DynamicItemCombiner;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuLocalizationTest {
    @TempDir
    Path directory;

    private Field instanceField;
    private TranslationCore previousCore;
    private ModConfig previousConfig;
    private DynamicItemCombiner previousCombiner;
    private InterceptionManager previousInterception;

    @BeforeEach
    void loadDictionaries() throws Exception {
        instanceField = TranslationCore.class.getDeclaredField("INSTANCE");
        instanceField.setAccessible(true);
        previousCore = (TranslationCore) instanceField.get(null);
        previousConfig = ModConfig.getInstance();
        Field combinerField = DynamicItemCombiner.class.getDeclaredField("instance");
        combinerField.setAccessible(true);
        previousCombiner = (DynamicItemCombiner) combinerField.get(null);
        previousInterception = InterceptionManager.getInstance();

        ModConfig config = new ModConfig();
        config.setConfigPath(directory.resolve("config.json"));
        ModConfig.setInstance(config);
        Files.writeString(directory.resolve("config.json"), "{\"untranslatedCapture\":true}");
        Files.writeString(directory.resolve("menus.json"), """
                {
                  "titles":{"Personal Bank":"個人銀行（Personal Bank）","Emerald Collection":"綠寶石收集（Emerald Collection）","Enchant Item":"附魔物品"},
                  "lines":{"Deposit Coins":"存入硬幣","Click to withdraw coins!":"點擊提領硬幣！","Pear":"梨"},
                  "people":{"Anita":"安妮塔（Anita）","Jacobus":"雅各布斯（Jacobus）"},
                  "patterns":[{"regex":"^Balance: ([0-9,]+) Coins$","zh_tw":"餘額：$1 枚硬幣"},
                    {"regex":"^Playing on: ([A-Za-z]+)$","zh_tw":"目前遊玩的檔案：$1","verbatim":true}]
                }
                """);
        Files.writeString(directory.resolve("dialogues.json"), """
                {"dialogues":{"[NPC] Anita: Hello!":"[NPC] 安妮塔: 你好！",
                  "You have not unlocked the Personal Bank!":"你尚未解鎖個人銀行（Personal Bank）！",
                  "[BOSS] Arachne: Can you see me?":"[BOSS] 阿拉克妮：你看得見我嗎？"}}
                """);
        Files.writeString(directory.resolve("lore_lines.json"), """
                {"lines":{
                  "Grants a {0}% chance to get {1}x XP Orbs from killing mobs and mining.":
                  "擊殺生物或挖礦時，有 {0}% 機率獲得 {1} 倍經驗球。"
                }}
                """);
        TranslationCore.initialize();
    }

    @AfterEach
    void restoreApplicationState() throws Exception {
        instanceField.set(null, previousCore);
        ModConfig.setInstance(previousConfig);
        DynamicItemCombiner.setInstance(previousCombiner);
        InterceptionManager.setInstance(previousInterception);
    }

    @Test
    void translatesStyledTitleAcrossComponentBoundaries() {
        Component title = Component.literal("Emerald ")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal("Collection").withStyle(ChatFormatting.YELLOW));
        Component translated = TranslationCore.translateComponent(title, ComponentCategory.CONTAINER_UI);
        assertEquals("綠寶石收集（Emerald Collection）", translated.getString());
        assertEquals("Emerald Collection", title.getString());
        assertEquals(translated.getString(),
                TranslationCore.translateComponent(translated, ComponentCategory.CONTAINER_UI).getString());
        assertTrue(translated.getSiblings().stream().anyMatch(run -> run.getStyle().getColor() != null));
    }

    @Test
    void appliesMenuLabelsAndRecordsUnknownTooltipWithTitle() {
        List<Component> tooltip = List.of(
                Component.literal("Deposit ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("Coins").withStyle(ChatFormatting.YELLOW)),
                Component.literal("Click to withdraw coins!"),
                Component.literal("Balance: 12,345 Coins"),
                Component.literal("This fixture has no known translation."));
        var output = TooltipTranslator.translateContainerTooltip(null, tooltip, "Personal Bank");
        assertEquals(List.of("存入硬幣", "點擊提領硬幣！", "餘額：12,345 枚硬幣", "This fixture has no known translation."),
                output.stream().map(Component::getString).toList());
        assertEquals("Deposit Coins", tooltip.getFirst().getString());
        assertTrue(TranslationCore.getInstance().getUntranslatedQueue().getRecords().values().stream()
                .anyMatch(row -> row.category().equals("MENU_TOOLTIP:Personal Bank")
                        && row.cleanText().equals("This fixture has no known translation.")));
    }

    @Test
    void translatesClickableBookPagesAcrossLines() {
        ClickEvent click = new ClickEvent.RunCommand("/bank");
        Style choiceStyle = Style.EMPTY.withClickEvent(click);
        Component source = Component.literal("Deposit ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Coins\n").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("Click to withdraw ").setStyle(choiceStyle))
                .append(Component.literal("coins!").setStyle(choiceStyle));
        Component localized = TranslationCore.translateBookPage(source);
        assertEquals("存入硬幣\n點擊提領硬幣！", localized.getString());
        assertTrue(localized.visit((style, text) -> {
            return click.equals(style.getClickEvent()) && text.contains("提領")
                    ? Optional.of(true) : Optional.empty();
        }, Style.EMPTY).orElse(false));
    }

    @Test
    void translatesSplitServerNoticesAndBossDialogue() {
        Component notice = Component.literal("You have not unlocked the ")
                .append(Component.literal("Personal Bank").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("!"));
        assertEquals("你尚未解鎖個人銀行（Personal Bank）！",
                TranslationCore.translateComponent(notice, ComponentCategory.CHAT).getString());
        Component boss = Component.literal("[BOSS] Arachne: ").withStyle(ChatFormatting.RED)
                .append(Component.literal("Can you see me?"));
        assertEquals("[BOSS] 阿拉克妮（Arachne）：你看得見我嗎？",
                TranslationCore.translateComponent(boss, ComponentCategory.CHAT).getString());
    }

    @Test
    void translatesWrappedEnchantEffectWithUnseenValuesAndKeepsColor() {
        Component firstLine = Component.literal("Grants a\u00a0")
                .append(Component.literal("37.5%").withStyle(ChatFormatting.GOLD))
                .append(" chance to get 4x XP");
        var source = List.of(Component.literal("Experience IV"), firstLine,
                Component.literal("Orbs from killing mobs and mining."), Component.empty(),
                Component.literal("Other information."));
        var display = TooltipTranslator.translateContainerTooltip(null, source, "Enchant Item");
        assertEquals("擊殺生物或挖礦時，有37.5%機率獲得4倍經驗球。",
                display.subList(1, display.size() - 2).stream().map(Component::getString)
                        .reduce("", (left, right) -> left + right).replace(" ", ""));
        assertTrue(display.stream().anyMatch(line ->
                line.visit((style, text) -> text.contains("37.5") && style.getColor() != null
                        ? Optional.of(true) : Optional.empty(), Style.EMPTY).orElse(false)));
        assertEquals("Grants a\u00a037.5% chance to get 4x XP", firstLine.getString());
    }

    @Test
    void prefersSpeakerScopedPhonePatternAndCapturesUnknownSpeech() {
        var core = TranslationCore.getInstance();
        core.getDialogueDictionary().registerPattern(
                "^\\[NPC\\] Jacobus: ✆ I've gone ahead and deposited ([0-9,]+) Coins into your purse\\.$",
                "[NPC] 雅各布斯（Jacobus）：✆ 我已經把 $1 枚硬幣退到你的錢包裡了。", true);
        Component call = Component.literal("[NPC] Jacobus: ✆ I've gone ahead and deposited ")
                .append(Component.literal("4,321").withStyle(ChatFormatting.GOLD))
                .append(" Coins into your purse.");
        assertEquals("[NPC] 雅各布斯（Jacobus）：✆ 我已經把 4,321 枚硬幣退到你的錢包裡了。",
                TranslationCore.translateComponent(call, ComponentCategory.CHAT).getString());
        core.getDialogueDictionary().registerDialogue("[NPC] Jacobus: ✆ Hello hello?",
                "[NPC] 雅各布斯（Jacobus）：✆ 喂？喂？");
        assertEquals("[NPC] 雅各布斯（Jacobus）：✆ 喂？喂？",
                core.translateAndCapture("[NPC] Jacobus: ✆ Hello hello?", ComponentCategory.CHAT));
        String unknown = "[NPC] Jacobus: ✆ I have another event to tell you about.";
        assertEquals("[NPC] 雅各布斯（Jacobus）: ✆ I have another event to tell you about.",
                core.translateAndCapture(unknown, ComponentCategory.CHAT));
        assertTrue(core.getUntranslatedQueue().getRecords().values().stream()
                .anyMatch(row -> row.category().equals("CHAT") && row.cleanText().equals(unknown)));
    }

    @Test
    void retainsDynamicProfileNameEvenWhenItIsAlsoALabel() {
        assertEquals("目前遊玩的檔案：Pear",
                TranslationCore.getInstance().getMenuDictionary().translate("Playing on: Pear", false));
    }

    @Test
    void reviewedParagraphSupersedesAlreadyTranslatedTooltipFragments() {
        var core = TranslationCore.getInstance();
        core.getMenuDictionary().loadFromJson("""
                {"lines":{"Keep your gear in this bag.":"把裝備保持在此袋。",
                          "Use it from any island.":"從任何島嶼使用它。"}}
                """);
        core.getLoreLineDictionary().loadFromJson("""
                {"lines":{},"paragraphs":{
                  "Keep your gear in this bag. Use it from any island.":"把裝備放進袋裡，無論在哪座島都能取用。"
                }}
                """);
        List<Component> tooltip = List.of(Component.literal("Gear Bag"), Component.literal("Keep your gear in this bag."),
                Component.literal("Use it from any island."), Component.empty(), Component.literal("Other information."));
        var result = TooltipTranslator.translateContainerTooltip(null, tooltip, "Personal Bank");
        assertEquals("把裝備放進袋裡，無論在哪座島都能取用。",
                result.subList(1, result.size() - 2).stream().map(Component::getString)
                        .reduce("", String::concat));
        assertEquals("Other information.", result.getLast().getString());
    }

    @Test
    void movesDamageRangeAndTheirColorsToNaturalPositions() {
        var core = TranslationCore.getInstance();
        var rules = JsonParser.parseString("""
                {"lines":{},"paragraph_patterns":[{"terms":{}}]}
                """).getAsJsonObject();
        var rule = rules.getAsJsonArray("paragraph_patterns").get(0).getAsJsonObject();
        rule.addProperty("regex", "^Deals up to (?<damage>[0-9]+)% of your damage dealt to other monsters within "
                + "(?<range>[0-9.]+) blocks of the target\\.$");
        rule.addProperty("zh_tw", "對目標周圍 ${range} 格內的其他怪物，最多造成原傷害 ${damage}% 的傷害。");
        core.getLoreLineDictionary().loadFromJson(rules.toString());

        Component damage = Component.literal("Deals up to ")
                .append(Component.literal("23").withStyle(ChatFormatting.GOLD))
                .append("% of your damage dealt");
        Component range = Component.literal("to other monsters within ")
                .append(Component.literal("4.5").withStyle(ChatFormatting.AQUA))
                .append(" blocks of the target.");
        var display = TooltipTranslator.translateContainerTooltip(null,
                List.of(Component.literal("Cleave"), damage, range), "Enchant Item");

        String result = display.subList(1, display.size()).stream().map(Component::getString)
                .reduce("", String::concat).replace(" ", "");
        assertEquals("對目標周圍4.5格內的其他怪物，最多造成原傷害23%的傷害。", result);
        assertTrue(hasStyledValue(display, "4.5", ChatFormatting.AQUA));
        assertTrue(hasStyledValue(display, "23", ChatFormatting.GOLD));
        assertEquals("Deals up to 23% of your damage dealt", damage.getString());
    }

    private static boolean hasStyledValue(List<Component> lines, String value, ChatFormatting formatting) {
        return lines.stream().anyMatch(line -> line.visit((style, text) ->
                text.contains(value) && Style.EMPTY.withColor(formatting).getColor().equals(style.getColor())
                        ? Optional.of(true) : Optional.empty(), Style.EMPTY).orElse(false));
    }

    @Test
    void rendersShadowFuryAsAFullEffectAndKeepsChangedValuesAndColors() throws Exception {
        List<Component> tooltip = List.of(Component.literal("Shadow Fury"),
                Component.literal("Rapidly teleports you to up to ")
                        .append(Component.literal("7").withStyle(ChatFormatting.AQUA))
                        .append(" enemies within"),
                Component.literal("18").withStyle(ChatFormatting.YELLOW)
                        .append(" blocks, rooting each of them and allowing"),
                Component.literal("you to hit them."));
        var result = TooltipTranslator.translateContainerTooltip(null, tooltip, "Personal Bank");
        String combined = result.subList(1, result.size()).stream().map(Component::getString)
                .reduce("", String::concat).replace(" ", "");
        assertEquals("在18格內迅速傳送到最多7名敵人身旁，禁錮每一個目標，讓你能趁機攻擊。", combined);
        assertTrue(hasStyledValue(result, "18", ChatFormatting.YELLOW));
        assertTrue(hasStyledValue(result, "7", ChatFormatting.AQUA));
        var core = TranslationCore.getInstance();
        try (var menus = getClass().getResourceAsStream("/assets/hsbtr/dictionaries/menus.json")) {
            assertNotNull(menus);
            core.getMenuDictionary().loadFromJson(new String(menus.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
        assertEquals("最好的一輪", core.translateAndCapture("Best Run", ComponentCategory.CONTAINER_UI));
        assertEquals("最佳一輪得分", core.translateAndCapture("Best Run Score", ComponentCategory.TOOLTIPS));
        assertEquals("最好的一輪：06:42.237", core.translateAndCapture("Best Run: 06:42.237", ComponentCategory.CHAT));
    }

    @Test
    void reloadAppliesEditedItemLoreToRealTooltipWithoutReplacingLiveStats() throws Exception {
        var core = TranslationCore.getInstance();
        var item = core.getItemDictionary().get("VORPAL_KATANA");
        var saved = new ArrayList<>(item.lore_template());
        int blockEnd = 6;
        while (blockEnd < saved.size() && !saved.get(blockEnd).isBlank()) {
            blockEnd++;
        }
        saved.subList(6, blockEnd).clear();
        saved.addAll(6, List.of("§7對終界使者造成 §a+250%§7 傷害。", "§7減傷 9%，並獲得戰鬥智慧。"));
        var lore = new JsonArray();
        saved.forEach(lore::add);
        var record = new JsonObject();
        record.addProperty("name_en", item.name_en());
        record.addProperty("name_zh_tw", item.name_zh_tw());
        record.add("lore_template", lore);
        var items = new JsonObject();
        items.add(item.id(), record);
        var document = new JsonObject();
        document.add("items", items);
        var file = directory.resolve("items.json");
        Files.writeString(file, document.toString());

        List<Component> raw = List.of(Component.literal("Fabled Vorpal Katana ✪✪"),
                Component.literal("Damage: +389").withStyle(ChatFormatting.RED), Component.empty(),
                Component.literal("Deal +250% damage to Endermen. Receive 9% less damage from"),
                Component.literal("Endermen when held. Gain +30☯ Combat Wisdom against Endermen."),
                Component.empty(), Component.literal("§aUnchanged marker."));
        core.reload();
        var actual = TooltipTranslator.translateItemTooltip(null, raw, false);
        assertEquals("對終界使者造成 +250% 傷害。", actual.get(3).getString());
        assertEquals("減傷 9%，並獲得戰鬥智慧。", actual.get(4).getString());
        assertTrue(hasStyledValue(actual, "+250%", ChatFormatting.GREEN));
        assertTrue(actual.get(1).getString().contains("+389"));
        assertEquals(raw.stream().map(Component::getString).toList(), TooltipTranslator.translateItemTooltip(null, raw, true)
                .stream().map(Component::getString).toList());

        var otherTooltip = new ArrayList<>(raw);
        otherTooltip.set(0, Component.literal("Different Katana"));
        assertTrue(TooltipTranslator.translateItemTooltip(null, otherTooltip, false).stream()
                .noneMatch(line -> line.getString().equals("減傷 9%，並獲得戰鬥智慧。")));
        Files.writeString(file, "{}");
        core.reload();
        assertTrue(TooltipTranslator.translateItemTooltip(null, raw, false).stream()
                .noneMatch(line -> line.getString().equals("減傷 9%，並獲得戰鬥智慧。")));
    }

    @Test
    void fillsNamedAndDoubleDigitCapturesOnlyOnce() {
        DialogueDictionary dictionary = new DialogueDictionary();
        dictionary.registerPattern("^Send (?<amount>\\$2) to (?<target>Builder)$",
                "將 ${target} 設為收件者，金額為 ${amount}。", true);
        assertEquals("將 Builder 設為收件者，金額為 $2。", dictionary.translate("Send $2 to Builder"));
        dictionary.registerPattern("^(.)(.)(.)(.)(.)(.)(.)(.)(.)(.)$", "$10 收到 $1", true);
        assertEquals("J 收到 A", dictionary.translate("ABCDEFGHIJ"));
    }

    @Test
    void keepsOriginalNpcNameForExistingDialogueAndNameTags() {
        Component dialogue = Component.literal("[NPC] ").append(Component.literal("Anita: Hello!"));
        assertEquals("[NPC] 安妮塔（Anita）: 你好！",
                TranslationCore.translateComponent(dialogue, ComponentCategory.CHAT).getString());
        Component label = Component.literal("Anita").setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD));
        assertEquals("安妮塔（Anita）", HologramTranslator.translate(label, null).getString());
    }
}
