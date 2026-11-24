package com.hypixel.translation.e2e;

import com.hypixel.translation.config.ComponentCategory;
import com.hypixel.translation.config.ModConfig;
import com.hypixel.translation.core.TokenDictionary;
import com.hypixel.translation.interception.ScoreboardTranslator;
import com.hypixel.translation.interception.TooltipTranslator;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.component.ItemLore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R1 Opaque-Box E2E Test Suite: Item Text Color, MC 26.2 Component Style/TextColor Preservation,
 * Legacy § Formatting Retention, LoreParser Structural Rules, Scoreboard Split-Line Reassembly,
 * and UI/Tooltip Dialogue Routing.
 */
@Tag("e2e")
@Tag("r1")
@DisplayName("R1: Component Style, TextColor & Formatting Preservation E2E")
public class R1ComponentStyleE2ETest extends BaseE2ETest {

    private static final Pattern CJK_PATTERN = Pattern.compile("[\\u4e00-\\u9fff]");

    private record StyledSpan(Style style, String text) {}

    private record RenderedCharStyles(String text, List<Style> styles) {}

    private static List<StyledSpan> collectSpans(Component component) {
        List<StyledSpan> spans = new ArrayList<>();
        if (component != null) {
            component.visit((style, text) -> {
                if (text != null && !text.isEmpty()) {
                    spans.add(new StyledSpan(style, text));
                }
                return Optional.empty();
            }, Style.EMPTY);
        }
        return spans;
    }

    private static RenderedCharStyles flattenRenderedStyles(Component component) {
        StringBuilder sb = new StringBuilder();
        List<Style> styles = new ArrayList<>();
        if (component != null) {
            component.visit((style, text) -> {
                if (text != null && !text.isEmpty()) {
                    sb.append(text);
                    for (int i = 0; i < text.length(); i++) {
                        styles.add(style);
                    }
                }
                return Optional.empty();
            }, Style.EMPTY);
        }
        return new RenderedCharStyles(sb.toString(), styles);
    }

    private static TextColor legacyColor(ChatFormatting formatting) {
        return TextColor.fromLegacyFormat(formatting);
    }

    private static void assertSubstringStyle(
            Component component,
            String substring,
            TextColor expectedColor,
            Boolean expectedBold,
            Boolean expectedItalic,
            Boolean expectedObfuscated
    ) {
        RenderedCharStyles rendered = flattenRenderedStyles(component);
        int start = rendered.text().indexOf(substring);
        assertTrue(start >= 0,
                "Expected substring '" + substring + "' in rendered text '" + rendered.text() + "'");

        for (int i = start; i < start + substring.length(); i++) {
            Style s = rendered.styles().get(i);
            if (expectedColor != null) {
                assertEquals(expectedColor, s.getColor(),
                        "Color mismatch at char '" + rendered.text().charAt(i)
                                + "' in substring '" + substring + "' of '" + rendered.text() + "'");
            }
            if (expectedBold != null) {
                assertEquals(expectedBold, s.isBold(),
                        "Bold flag mismatch at char '" + rendered.text().charAt(i)
                                + "' in substring '" + substring + "'");
            }
            if (expectedItalic != null) {
                assertEquals(expectedItalic, s.isItalic(),
                        "Italic flag mismatch at char '" + rendered.text().charAt(i)
                                + "' in substring '" + substring + "'");
            }
            if (expectedObfuscated != null) {
                assertEquals(expectedObfuscated, s.isObfuscated(),
                        "Obfuscated flag mismatch at char '" + rendered.text().charAt(i)
                                + "' in substring '" + substring + "'");
            }
        }
    }

    private static void assertAllVisibleCharsNonWhite(Component component) {
        RenderedCharStyles rendered = flattenRenderedStyles(component);
        boolean foundVisible = false;
        for (int i = 0; i < rendered.text().length(); i++) {
            char c = rendered.text().charAt(i);
            if (Character.isWhitespace(c)) {
                continue;
            }
            foundVisible = true;
            TextColor color = rendered.styles().get(i).getColor();
            assertNotNull(color,
                    "Expected non-null TextColor on '" + c + "' in '" + rendered.text() + "' (must not flatten to unstyled white)");
            assertNotEquals(TextColor.WHITE, color,
                    "Expected non-white TextColor on '" + c + "' in '" + rendered.text() + "'");
        }
        assertTrue(foundVisible, "Expected at least one visible character in '" + rendered.text() + "'");
    }

    private static boolean containsCjk(String text) {
        return text != null && CJK_PATTERN.matcher(text).find();
    }

    private static List<String> snapshotStrings(List<Component> components) {
        List<String> out = new ArrayList<>(components.size());
        for (Component c : components) {
            out.add(c.getString());
        }
        return out;
    }

    private static List<List<StyledSpan>> snapshotSpans(List<Component> components) {
        List<List<StyledSpan>> out = new ArrayList<>(components.size());
        for (Component c : components) {
            out.add(collectSpans(c));
        }
        return out;
    }

    private static void assertListUnmutated(List<Component> original, List<String> expectedStrings, List<List<StyledSpan>> expectedSpans) {
        assertEquals(expectedStrings.size(), original.size(), "Original tooltip list size must not mutate");
        for (int i = 0; i < original.size(); i++) {
            assertEquals(expectedStrings.get(i), original.get(i).getString(),
                    "Original Component string at index " + i + " must remain unmutated");
            assertEquals(expectedSpans.get(i), collectSpans(original.get(i)),
                    "Original Component styles at index " + i + " must remain unmutated");
        }
    }

    // =========================================================================
    // Tier 1: Feature Coverage (R1-T1-01 .. R1-T1-09)
    // =========================================================================
    @Nested
    @Tag("tier1")
    @DisplayName("Tier 1: Feature Coverage (R1-T1-01..09)")
    class Tier1Tests {

        @Test
        @DisplayName("R1-T1-01: Modern Component item name preserves GOLD rarity color and italic=false without §")
        void testModernItemNamePreservesGoldAndNonItalic() {
            Component input = Component.empty()
                    .setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE).withItalic(true))
                    .append(Component.literal("Heroic Hyperion")
                            .setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withItalic(false)));
            List<StyledSpan> origSpans = collectSpans(input);

            Component translated = dynamicItemCombiner.translateItemNameComponent(null, input);

            assertNotNull(translated);
            assertEquals("英雄 亥伯龍", translated.getString());
            assertSubstringStyle(translated, "英雄 亥伯龍", legacyColor(ChatFormatting.GOLD), null, false, null);
            assertAllVisibleCharsNonWhite(translated);
            assertEquals(origSpans, collectSpans(input));
        }

        @Test
        @DisplayName("R1-T1-02: Modern Component item name preserves 24-bit RGB TextColor and bold flag")
        void testModernItemNamePreservesRgbAndBold() {
            TextColor mythicPink = TextColor.fromRgb(0xFF55FF);
            Component input = Component.literal("Terminator")
                    .setStyle(Style.EMPTY.withColor(mythicPink).withBold(true).withItalic(false));

            Component translated = dynamicItemCombiner.translateItemNameComponent(null, input);

            assertNotNull(translated);
            assertEquals("終結者", translated.getString());
            assertSubstringStyle(translated, "終結者", mythicPink, true, false, null);
        }

        @Test
        @DisplayName("R1-T1-03: Multi-sibling pet name preserves distinct GRAY and GOLD TextColors without §")
        void testMultiSiblingPetNamePreservesDistinctColors() {
            Component input = Component.empty()
                    .append(Component.literal("[Lvl 100] ")
                            .setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY).withItalic(false)))
                    .append(Component.literal("Baby Yeti")
                            .setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withItalic(false)));

            Component translated = dynamicItemCombiner.translateItemNameComponent(null, input);

            assertNotNull(translated);
            assertEquals("[Lvl 100] 雪怪寶寶", translated.getString());
            assertSubstringStyle(translated, "[Lvl 100]", legacyColor(ChatFormatting.GRAY), null, false, null);
            assertSubstringStyle(translated, "雪怪寶寶", legacyColor(ChatFormatting.GOLD), null, false, null);
        }

        @Test
        @DisplayName("R1-T1-04: Single-node styled lore preserves GRAY and italic=false without §")
        void testSingleNodeStyledLorePreservesStyle() {
            Component input = Component.literal("Damage: +260")
                    .setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY).withItalic(false));

            Component translated = dynamicItemCombiner.translateLoreComponent(input);

            assertNotNull(translated);
            assertEquals("傷害：+260", translated.getString());
            assertSubstringStyle(translated, "傷害：+260", legacyColor(ChatFormatting.GRAY), null, false, null);
            assertAllVisibleCharsNonWhite(translated);
        }

        @Test
        @DisplayName("R1-T1-05: Multi-sibling stat lore preserves GRAY, RED, and YELLOW across segments")
        void testMultiSiblingStatLorePreservesSegmentColors() {
            Component input = Component.empty()
                    .setStyle(Style.EMPTY.withItalic(false))
                    .append(Component.literal("Damage: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("+260").withStyle(ChatFormatting.RED))
                    .append(Component.literal(" (+30)").withStyle(ChatFormatting.YELLOW));
            List<StyledSpan> origSpans = collectSpans(input);

            Component translated = dynamicItemCombiner.translateLoreComponent(input);

            assertNotNull(translated);
            assertEquals("傷害：+260 (+30)", translated.getString());
            assertSubstringStyle(translated, "傷害：", legacyColor(ChatFormatting.GRAY), null, false, null);
            assertSubstringStyle(translated, "+260", legacyColor(ChatFormatting.RED), null, false, null);
            assertSubstringStyle(translated, "(+30)", legacyColor(ChatFormatting.YELLOW), null, false, null);
            assertEquals(origSpans, collectSpans(input));
        }

        @Test
        @DisplayName("R1-T1-06: Legacy §-coded item name and lore retain § color and formatting codes")
        void testLegacySectionCodedTooltipPreserved() {
            List<Component> input = List.of(
                    Component.literal("§6Heroic Hyperion ✪✪✪✪✪"),
                    Component.literal("§7Damage: §c+260 §e(+30)")
            );

            List<Component> translated = dynamicItemCombiner.translateTooltip(null, input, false);

            assertEquals(2, translated.size());
            assertEquals("§6英雄 亥伯龍 ✪✪✪✪✪", translated.get(0).getString());
            assertEquals("§7傷害：§c+260 §e(+30)", translated.get(1).getString());
        }

        @Test
        @DisplayName("R1-T1-07: LoreParser structural rules cover leading-glyph stats, PET ITEM, missing categories, and reqs")
        void testLoreParserStructuralRules() {
            // Leading-glyph stat
            String glyphStat = loreParser.translateLoreLine("§7⚔ Damage: §c+100");
            assertTrue(glyphStat.contains("⚔"), "Leading glyph must be preserved: " + glyphStat);
            assertTrue(glyphStat.contains("傷害"), "Stat label must translate: " + glyphStat);
            assertTrue(glyphStat.contains("+100"), "Stat value must be preserved: " + glyphStat);
            assertTrue(glyphStat.contains("§7") && glyphStat.contains("§c"), "Color codes must stay intact: " + glyphStat);

            // PET ITEM ordering
            String petFooter = loreParser.translateLoreLine("§6§lLEGENDARY PET ITEM");
            assertEquals("§6§l傳奇 寵物道具", petFooter);
            assertFalse(petFooter.contains("寵物 道具"));

            // Missing categories (PORTAL, DYE, TROPHY, SACK, MEMENTO, SHARD)
            for (String[] pair : new String[][]{
                    {"§9§lRARE DYE", "稀有", "DYE"},
                    {"§5§lEPIC TROPHY", "史詩", "TROPHY"},
                    {"§a§lUNCOMMON SACK", "罕見", "SACK"},
                    {"§f§lCOMMON PORTAL", "普通", "PORTAL"},
                    {"§6§lLEGENDARY SHARD", "傳奇", "SHARD"},
                    {"§c§lSPECIAL MEMENTO", "特殊", "MEMENTO"}
            }) {
                String out = loreParser.translateLoreLine(pair[0]);
                assertTrue(out.contains(pair[1]), "Rarity '" + pair[1] + "' missing in: " + out);
                assertFalse(out.contains(pair[2]), "Category '" + pair[2] + "' left untranslated in: " + out);
                assertTrue(containsCjk(out), "Expected CJK translation in: " + out);
            }

            // Skill / Garden / HotM requirements
            for (String req : List.of(
                    "§cRequires Combat Level 24.",
                    "§cRequires Garden Level 5.",
                    "§cRequires Heart of the Mountain Tier 5."
            )) {
                String out = loreParser.translateLoreLine(req);
                assertTrue(out.startsWith("§c"), "Color code must be preserved in: " + out);
                assertTrue(out.contains("需要") && !out.contains("Requires"),
                        "Requirement line must translate to TC: " + out);
            }
        }

        @Test
        @DisplayName("R1-T1-08: ScoreboardTranslator reassembles 16-char sibling split ('Your Isla'+'nd') preserving Style")
        void testScoreboardSplitIslandPreservesStyle() {
            TokenDictionary bundledTokens = com.hypixel.translation.core.TranslationCore.getInstance().getTokenDictionary();
            String expectedIsland = bundledTokens.translateText("Your Island");
            assertTrue(containsCjk(expectedIsland) && !expectedIsland.equals("Your Island"),
                    "Bundled tokens.json must translate 'Your Island' to TC, got: " + expectedIsland);

            Component input = Component.empty()
                    .append(Component.literal(" ⏣ ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("Your Isla").withStyle(ChatFormatting.GREEN))
                    .append(Component.literal("nd").withStyle(ChatFormatting.GREEN));
            List<StyledSpan> origSpans = collectSpans(input);

            Component translated = ScoreboardTranslator.translate(input);

            assertNotNull(translated);
            assertTrue(translated.getString().contains(expectedIsland),
                    "Expected '" + expectedIsland + "' in scoreboard line, got: " + translated.getString());
            assertFalse(translated.getString().contains("Your Isla"));
            assertFalse(translated.getString().contains("Your Island"));
            assertSubstringStyle(translated, " ⏣ ", legacyColor(ChatFormatting.GRAY), null, null, null);
            assertSubstringStyle(translated, expectedIsland, legacyColor(ChatFormatting.GREEN), null, null, null);
            assertEquals(origSpans, collectSpans(input));
        }

        @Test
        @DisplayName("R1-T1-09: TranslationCore routes TOOLTIPS, CONTAINER_UI, TAB_LIST, SCOREBOARD via DialogueDictionary first")
        void testTranslationCoreDialoguePriorityRouting() {
            translationCore.getDialogueDictionary().registerDialogue("Menu", "專屬對話選單");

            for (ComponentCategory cat : List.of(
                    ComponentCategory.TOOLTIPS,
                    ComponentCategory.CONTAINER_UI,
                    ComponentCategory.TAB_LIST,
                    ComponentCategory.SCOREBOARD
            )) {
                assertEquals("專屬對話選單", translationCore.translateAndCapture("Menu", cat),
                        "Category " + cat + " must check DialogueDictionary before TokenDictionary");
                assertEquals("傷害", translationCore.translateAndCapture("Damage", cat),
                        "Category " + cat + " must fall back to TokenDictionary when DialogueDictionary has no match");
            }
        }
    }

    // =========================================================================
    // Tier 2: Boundary & Edge Cases (R1-T2-01 .. R1-T2-06)
    // =========================================================================
    @Nested
    @Tag("tier2")
    @DisplayName("Tier 2: Boundary & Edge Cases (R1-T2-01..06)")
    class Tier2Tests {

        @Test
        @DisplayName("R1-T2-01: Empty, whitespace-only, and null Component inputs do not throw")
        void testEmptyWhitespaceAndNullComponents() {
            assertDoesNotThrow(() -> {
                Component emptyName = dynamicItemCombiner.translateItemNameComponent(null, Component.empty());
                if (emptyName != null) assertEquals("", emptyName.getString());

                Component blankName = dynamicItemCombiner.translateItemNameComponent(
                        null, Component.literal("   ").setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD)));
                if (blankName != null) assertTrue(blankName.getString().isBlank());

                Component nullName = dynamicItemCombiner.translateItemNameComponent(null, null);
                if (nullName != null) assertEquals("", nullName.getString());

                Component emptyLore = dynamicItemCombiner.translateLoreComponent(Component.empty());
                if (emptyLore != null) assertEquals("", emptyLore.getString());

                Component literalEmptyLore = dynamicItemCombiner.translateLoreComponent(Component.literal(""));
                if (literalEmptyLore != null) assertEquals("", literalEmptyLore.getString());

                Component blankLore = dynamicItemCombiner.translateLoreComponent(
                        Component.literal("   ").setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)));
                if (blankLore != null) assertTrue(blankLore.getString().isBlank());

                Component nullLore = dynamicItemCombiner.translateLoreComponent(null);
                if (nullLore != null) assertEquals("", nullLore.getString());
            });
        }

        @Test
        @DisplayName("R1-T2-02: ItemLore.LORE_STYLE (DARK_PURPLE + italic=true) inheritance & explicit italic=false override")
        void testItemLoreStyleInheritanceAndChildOverride() {
            // Case A: Default ItemLore.LORE_STYLE (DARK_PURPLE + italic=true) on unstyled line
            ItemLore itemLore = new ItemLore(List.of(Component.literal("Mana Cost: 150")));
            Component styledDefaultLore = itemLore.styledLines().get(0);
            Component translatedDefault = dynamicItemCombiner.translateLoreComponent(styledDefaultLore);

            assertEquals("魔力消耗：150", translatedDefault.getString());
            assertSubstringStyle(translatedDefault, "魔力消耗：150",
                    legacyColor(ChatFormatting.DARK_PURPLE), null, true, null);

            // Case B: Parent LORE_STYLE with children explicitly overriding color and italic=false
            Component abilityLine = Component.empty()
                    .setStyle(Style.EMPTY.withColor(ChatFormatting.DARK_PURPLE).withItalic(true))
                    .append(Component.literal("Ability: Wither Impact  ")
                            .setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withItalic(false)))
                    .append(Component.literal("RIGHT CLICK")
                            .setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW).withBold(true).withItalic(false)));

            Component translatedAbility = dynamicItemCombiner.translateLoreComponent(abilityLine);

            assertEquals("技能：凋零衝擊  右鍵點擊", translatedAbility.getString());
            assertSubstringStyle(translatedAbility, "技能：凋零衝擊",
                    legacyColor(ChatFormatting.GOLD), null, false, null);
            assertSubstringStyle(translatedAbility, "右鍵點擊",
                    legacyColor(ChatFormatting.YELLOW), true, false, null);
        }

        @Test
        @DisplayName("R1-T2-03: Mixed modern Style and inline legacy § codes preserve both")
        void testMixedModernStyleAndInlineLegacyCodes() {
            Component input = Component.empty()
                    .setStyle(Style.EMPTY.withItalic(false))
                    .append(Component.literal("§7Damage: §c+260 "))
                    .append(Component.literal("(+30)").withStyle(ChatFormatting.YELLOW));

            Component translated = dynamicItemCombiner.translateLoreComponent(input);

            assertNotNull(translated);
            assertEquals("§7傷害：§c+260 (+30)", translated.getString());
            assertSubstringStyle(translated, "(+30)", legacyColor(ChatFormatting.YELLOW), null, false, null);
        }

        @Test
        @DisplayName("R1-T2-04: Preview mode (true) returns original English Component list with exact Styles unmutated")
        void testPreviewModePreservesOriginalModernStyles() {
            List<Component> original = List.of(
                    Component.literal("Heroic Hyperion").setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withItalic(false)),
                    Component.empty().setStyle(Style.EMPTY.withItalic(false))
                            .append(Component.literal("Damage: ").withStyle(ChatFormatting.GRAY))
                            .append(Component.literal("+260").withStyle(ChatFormatting.RED))
            );
            List<String> origStrings = snapshotStrings(original);
            List<List<StyledSpan>> origSpans = snapshotSpans(original);

            List<Component> preview = dynamicItemCombiner.translateTooltip(null, original, true);

            assertEquals(2, preview.size());
            assertEquals("Heroic Hyperion", preview.get(0).getString());
            assertEquals("Damage: +260", preview.get(1).getString());
            assertSubstringStyle(preview.get(0), "Heroic Hyperion", legacyColor(ChatFormatting.GOLD), null, false, null);
            assertSubstringStyle(preview.get(1), "Damage: ", legacyColor(ChatFormatting.GRAY), null, false, null);
            assertSubstringStyle(preview.get(1), "+260", legacyColor(ChatFormatting.RED), null, false, null);
            assertListUnmutated(original, origStrings, origSpans);
        }

        @Test
        @DisplayName("R1-T2-05: Collision item (Ancient Claw vs Ancient Ancient Claw) & obfuscated footer as modern Components")
        void testCollisionItemAndObfuscatedFooterModernStyles() {
            Component unreforged = Component.literal("Ancient Claw")
                    .setStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withItalic(false));
            Component reforged = Component.literal("Ancient Ancient Claw")
                    .setStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withItalic(false));

            Component outUnreforged = dynamicItemCombiner.translateItemNameComponent(null, unreforged);
            Component outReforged = dynamicItemCombiner.translateItemNameComponent(null, reforged);

            assertEquals("古代之爪", outUnreforged.getString());
            assertSubstringStyle(outUnreforged, "古代之爪", legacyColor(ChatFormatting.GREEN), null, false, null);

            assertEquals("遠古 古代之爪", outReforged.getString());
            assertSubstringStyle(outReforged, "遠古 古代之爪", legacyColor(ChatFormatting.GREEN), null, false, null);

            Component obfuscatedFooter = Component.empty()
                    .setStyle(Style.EMPTY.withColor(ChatFormatting.LIGHT_PURPLE).withBold(true).withItalic(false))
                    .append(Component.literal("a").withStyle(Style.EMPTY.withObfuscated(true)))
                    .append(Component.literal(" MYTHIC DUNGEON SWORD "))
                    .append(Component.literal("a").withStyle(Style.EMPTY.withObfuscated(true)));

            Component translatedFooter = dynamicItemCombiner.translateLoreComponent(obfuscatedFooter);
            assertTrue(translatedFooter.getString().contains("神話 地下城 劍"),
                    "Expected translated footer, got: " + translatedFooter.getString());
            assertSubstringStyle(translatedFooter, "神話 地下城 劍",
                    legacyColor(ChatFormatting.LIGHT_PURPLE), true, false, false);
            RenderedCharStyles rendered = flattenRenderedStyles(translatedFooter);
            assertTrue(rendered.styles().get(0).isObfuscated(),
                    "Leading obfuscated marker must remain obfuscated");
            assertTrue(rendered.styles().get(rendered.styles().size() - 1).isObfuscated(),
                    "Trailing obfuscated marker must remain obfuscated");
        }

        @Test
        @DisplayName("R1-T2-06: Scoreboard split with distinct sibling colors ('Pur'+'se: ' WHITE + '1,500' GOLD) & date meta")
        void testScoreboardSplitMultiColorAndDateMeta() {
            TokenDictionary bundledTokens = com.hypixel.translation.core.TranslationCore.getInstance().getTokenDictionary();
            String expectedPurse = bundledTokens.translateText("Purse");
            assertTrue(containsCjk(expectedPurse) && !expectedPurse.equals("Purse"),
                    "Bundled tokens.json must translate 'Purse' to TC, got: " + expectedPurse);

            Component purseLine = Component.empty()
                    .append(Component.literal("Pur").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("se: ").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("1,500").withStyle(ChatFormatting.GOLD));

            Component translatedPurse = ScoreboardTranslator.translate(purseLine);
            assertTrue(translatedPurse.getString().contains(expectedPurse),
                    "Expected '" + expectedPurse + "' in: " + translatedPurse.getString());
            assertTrue(translatedPurse.getString().contains("1,500"));
            assertSubstringStyle(translatedPurse, expectedPurse, legacyColor(ChatFormatting.WHITE), null, null, null);
            assertSubstringStyle(translatedPurse, "1,500", legacyColor(ChatFormatting.GOLD), null, null, null);

            Component metaLine = Component.empty()
                    .append(Component.literal("04/15/26 ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("m123D").withStyle(ChatFormatting.DARK_GRAY));

            Component translatedMeta = ScoreboardTranslator.translate(metaLine);
            assertEquals("04/15/26 m123D", translatedMeta.getString());
            assertSubstringStyle(translatedMeta, "04/15/26 ", legacyColor(ChatFormatting.GRAY), null, null, null);
            assertSubstringStyle(translatedMeta, "m123D", legacyColor(ChatFormatting.DARK_GRAY), null, null, null);
        }
    }

    // =========================================================================
    // Tier 3: Pairwise Combinations (R1-T3-01 .. R1-T3-03)
    // =========================================================================
    @Nested
    @Tag("tier3")
    @DisplayName("Tier 3: Pairwise Combinations (R1-T3-01..03)")
    class Tier3Tests {

        @Test
        @DisplayName("R1-T3-01: Pairwise translateTooltip across modern name, multi-sibling stats, glyph stat, enchants, cost, footer")
        void testPairwiseModernTooltipPreservesAllColorsAndInputImmutability() {
            List<Component> tooltip = List.of(
                    Component.literal("Heroic Hyperion").setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withItalic(false)),
                    Component.empty().setStyle(Style.EMPTY.withItalic(false))
                            .append(Component.literal("Damage: ").withStyle(ChatFormatting.GRAY))
                            .append(Component.literal("+260").withStyle(ChatFormatting.RED))
                            .append(Component.literal(" (+30)").withStyle(ChatFormatting.YELLOW)),
                    Component.empty().setStyle(Style.EMPTY.withItalic(false))
                            .append(Component.literal("⚔ Strength: ").withStyle(ChatFormatting.GRAY))
                            .append(Component.literal("+150").withStyle(ChatFormatting.RED)),
                    Component.literal("Sharpness V, Critical VI")
                            .setStyle(Style.EMPTY.withColor(ChatFormatting.BLUE).withItalic(false)),
                    Component.empty().setStyle(Style.EMPTY.withItalic(false))
                            .append(Component.literal("Mana Cost: ").withStyle(ChatFormatting.DARK_GRAY))
                            .append(Component.literal("150\ue003").withStyle(ChatFormatting.AQUA)),
                    Component.literal("LEGENDARY DUNGEON SWORD")
                            .setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withBold(true).withItalic(false))
            );
            List<String> origStrings = snapshotStrings(tooltip);
            List<List<StyledSpan>> origSpans = snapshotSpans(tooltip);

            List<Component> translated = dynamicItemCombiner.translateTooltip(null, tooltip, false);

            assertEquals(tooltip.size(), translated.size());
            assertEquals("英雄 亥伯龍", translated.get(0).getString());
            assertEquals("傷害：+260 (+30)", translated.get(1).getString());
            assertTrue(translated.get(2).getString().contains("⚔")
                    && translated.get(2).getString().contains("力量")
                    && translated.get(2).getString().contains("+150"));
            assertEquals("鋒利 V, 暴擊 VI", translated.get(3).getString());
            assertEquals("魔力消耗：150\ue003", translated.get(4).getString());
            assertEquals("傳奇 地下城 劍", translated.get(5).getString());

            for (Component line : translated) {
                assertAllVisibleCharsNonWhite(line);
            }
            assertSubstringStyle(translated.get(3), "鋒利 V, 暴擊 VI", legacyColor(ChatFormatting.BLUE), null, false, null);
            assertSubstringStyle(translated.get(4), "魔力消耗：", legacyColor(ChatFormatting.DARK_GRAY), null, false, null);
            assertSubstringStyle(translated.get(4), "150\ue003", legacyColor(ChatFormatting.AQUA), null, false, null);
            assertSubstringStyle(translated.get(5), "傳奇 地下城 劍", legacyColor(ChatFormatting.GOLD), true, false, null);

            assertListUnmutated(tooltip, origStrings, origSpans);
        }

        @Test
        @DisplayName("R1-T3-02: TooltipTranslator item tooltip (with preview toggle) and generic tooltip DialogueDictionary routing")
        void testTooltipTranslatorItemAndGenericPaths() {
            ModConfig config = ModConfig.getInstance();
            boolean prevPreview = config.isEnableAltShiftPreview();
            boolean prevSub = config.isShowEnglishSubtitle();
            try {
                config.setEnableAltShiftPreview(true);
                config.setShowEnglishSubtitle(false);

                List<Component> styledItem = List.of(
                        Component.literal("Heroic Hyperion")
                                .setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withItalic(false)),
                        Component.empty().setStyle(Style.EMPTY.withItalic(false))
                                .append(Component.literal("Damage: ").withStyle(ChatFormatting.GRAY))
                                .append(Component.literal("+260").withStyle(ChatFormatting.RED))
                );

                List<Component> translated = TooltipTranslator.translateItemTooltip(null, styledItem, false);
                assertEquals("英雄 亥伯龍", translated.get(0).getString());
                assertSubstringStyle(translated.get(0), "英雄 亥伯龍", legacyColor(ChatFormatting.GOLD), null, false, null);
                assertSubstringStyle(translated.get(1), "傷害：", legacyColor(ChatFormatting.GRAY), null, false, null);
                assertSubstringStyle(translated.get(1), "+260", legacyColor(ChatFormatting.RED), null, false, null);

                List<Component> preview = TooltipTranslator.translateItemTooltip(null, styledItem, true);
                assertEquals("Heroic Hyperion", preview.get(0).getString());
                assertSubstringStyle(preview.get(0), "Heroic Hyperion", legacyColor(ChatFormatting.GOLD), null, false, null);

                // Generic UI tooltip routed through DialogueDictionary via TranslationCore.getInstance()
                String expectedUiZh = com.hypixel.translation.core.TranslationCore.getInstance().getDialogueDictionary().translate("ATTEMPT CHALLENGE");
                assertTrue(containsCjk(expectedUiZh) && !expectedUiZh.equals("ATTEMPT CHALLENGE"),
                        "Bundled dialogues.json must translate 'ATTEMPT CHALLENGE', got: " + expectedUiZh);

                List<Component> generic = List.of(
                        Component.literal("ATTEMPT CHALLENGE")
                                .setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW).withBold(true))
                );
                List<Component> translatedGeneric = TooltipTranslator.translateGenericTooltip(generic);
                assertEquals(1, translatedGeneric.size());
                assertEquals(expectedUiZh, translatedGeneric.get(0).getString());
                assertSubstringStyle(translatedGeneric.get(0), expectedUiZh,
                        legacyColor(ChatFormatting.YELLOW), true, null, null);
            } finally {
                config.setEnableAltShiftPreview(prevPreview);
                config.setShowEnglishSubtitle(prevSub);
            }
        }

        @Test
        @DisplayName("R1-T3-03: TranslationCore.translateComponent preserves ClickEvent, HoverEvent, Style & routes CONTAINER_UI/TAB_LIST")
        void testTranslationCoreComponentPreservesEventsAndRoutesDialogues() {
            String expectedDeliveryZh = com.hypixel.translation.core.TranslationCore.getInstance()
                    .getDialogueDictionary()
                    .translate("Accept Island Delivery");
            assertTrue(containsCjk(expectedDeliveryZh) && !expectedDeliveryZh.equals("Accept Island Delivery"),
                    "Bundled dialogues.json must translate 'Accept Island Delivery', got: " + expectedDeliveryZh);

            ClickEvent click = new ClickEvent.RunCommand("/claimdelivery");
            Component hoverText = Component.literal("Accept Island Delivery")
                    .setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW));
            Component comp = Component.literal("Accept Island Delivery")
                    .setStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)
                            .withBold(true)
                            .withClickEvent(click)
                            .withHoverEvent(new HoverEvent.ShowText(hoverText)));

            for (ComponentCategory cat : List.of(ComponentCategory.CONTAINER_UI, ComponentCategory.TAB_LIST)) {
                Component out = com.hypixel.translation.core.TranslationCore.translateComponent(comp, cat);
                assertNotNull(out);
                assertEquals(expectedDeliveryZh, out.getString(),
                        "Category " + cat + " must translate via DialogueDictionary");
                assertSubstringStyle(out, expectedDeliveryZh, legacyColor(ChatFormatting.GREEN), true, null, null);
                assertInstanceOf(ClickEvent.RunCommand.class, out.getStyle().getClickEvent());
                assertEquals("/claimdelivery", ((ClickEvent.RunCommand) out.getStyle().getClickEvent()).command());

                assertInstanceOf(HoverEvent.ShowText.class, out.getStyle().getHoverEvent());
                Component translatedHover = ((HoverEvent.ShowText) out.getStyle().getHoverEvent()).value();
                assertEquals(expectedDeliveryZh, translatedHover.getString());
                assertSubstringStyle(translatedHover, expectedDeliveryZh,
                        legacyColor(ChatFormatting.YELLOW), null, null, null);
            }
        }
    }

    // =========================================================================
    // Tier 4: Real-World Workloads (R1-T4-01 .. R1-T4-02)
    // =========================================================================
    @Nested
    @Tag("tier4")
    @DisplayName("Tier 4: Real-World Workloads (R1-T4-01..02)")
    class Tier4Tests {

        @Test
        @DisplayName("R1-T4-01: Full 13-line MC 26.2 Hyperion tooltip pure Style/TextColor (no §) retains every semantic color")
        void testFullModernHyperionTooltipWorkload() {
            List<Component> hyperionTooltip = List.of(
                    Component.literal("Heroic Hyperion ✪✪✪✪✪")
                            .setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withItalic(false)),
                    Component.empty().setStyle(Style.EMPTY.withItalic(false))
                            .append(Component.literal("Gear Score: ").withStyle(ChatFormatting.GRAY))
                            .append(Component.literal("1100").withStyle(ChatFormatting.LIGHT_PURPLE)),
                    Component.empty().setStyle(Style.EMPTY.withItalic(false))
                            .append(Component.literal("Damage: ").withStyle(ChatFormatting.GRAY))
                            .append(Component.literal("+260").withStyle(ChatFormatting.RED))
                            .append(Component.literal(" (+30)").withStyle(ChatFormatting.YELLOW)),
                    Component.empty().setStyle(Style.EMPTY.withItalic(false))
                            .append(Component.literal("Strength: ").withStyle(ChatFormatting.GRAY))
                            .append(Component.literal("+150").withStyle(ChatFormatting.RED))
                            .append(Component.literal(" (+30)").withStyle(ChatFormatting.YELLOW)),
                    Component.empty().setStyle(Style.EMPTY.withItalic(false))
                            .append(Component.literal("Intelligence: ").withStyle(ChatFormatting.GRAY))
                            .append(Component.literal("+350").withStyle(ChatFormatting.AQUA))
                            .append(Component.literal(" (+150)").withStyle(ChatFormatting.YELLOW)),
                    Component.empty().setStyle(Style.EMPTY.withItalic(false))
                            .append(Component.literal("Gemstones: ").withStyle(ChatFormatting.GRAY))
                            .append(Component.literal("[\ue003] [⚔]").withStyle(ChatFormatting.DARK_GRAY)),
                    Component.empty(),
                    Component.literal("Sharpness V, Critical VI, Giant Killer V")
                            .setStyle(Style.EMPTY.withColor(ChatFormatting.BLUE).withItalic(false)),
                    Component.empty(),
                    Component.empty().setStyle(Style.EMPTY.withItalic(false))
                            .append(Component.literal("Ability: Wither Impact  ").withStyle(ChatFormatting.GOLD))
                            .append(Component.literal("RIGHT CLICK")
                                    .setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW).withBold(true))),
                    Component.empty().setStyle(Style.EMPTY.withItalic(false))
                            .append(Component.literal("Mana Cost: ").withStyle(ChatFormatting.DARK_GRAY))
                            .append(Component.literal("150\ue003").withStyle(ChatFormatting.AQUA)),
                    Component.empty().setStyle(Style.EMPTY.withItalic(false))
                            .append(Component.literal("❣ ").withStyle(ChatFormatting.DARK_RED))
                            .append(Component.literal("Requires The Catacombs Floor VII Completion.")
                                    .withStyle(ChatFormatting.RED)),
                    Component.literal("LEGENDARY DUNGEON SWORD")
                            .setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withBold(true).withItalic(false))
            );
            List<String> origStrings = snapshotStrings(hyperionTooltip);
            List<List<StyledSpan>> origSpans = snapshotSpans(hyperionTooltip);

            List<Component> translated = dynamicItemCombiner.translateTooltip(null, hyperionTooltip, false);

            assertEquals(13, translated.size());
            assertEquals("英雄 亥伯龍 ✪✪✪✪✪", translated.get(0).getString());
            assertEquals(tokenDictionary.resolveToken("STAT_GEAR_SCORE") + "：1100", translated.get(1).getString());
            assertEquals("傷害：+260 (+30)", translated.get(2).getString());
            assertEquals("力量：+150 (+30)", translated.get(3).getString());
            assertEquals("智力：+350 (+150)", translated.get(4).getString());
            assertEquals("寶石槽：[\ue003] [⚔]", translated.get(5).getString());
            assertEquals("", translated.get(6).getString());
            assertEquals("鋒利 V, 暴擊 VI, 巨人殺手 V", translated.get(7).getString());
            assertEquals("", translated.get(8).getString());
            assertEquals("技能：凋零衝擊  右鍵點擊", translated.get(9).getString());
            assertEquals("魔力消耗：150\ue003", translated.get(10).getString());
            assertEquals("❣ 需要通關 地下墓穴 第七層。", translated.get(11).getString());
            assertEquals("傳奇 地下城 劍", translated.get(12).getString());

            // Verify zero lines degrade to all-white and all semantic colors match
            for (int i = 0; i < translated.size(); i++) {
                if (!translated.get(i).getString().isBlank()) {
                    assertAllVisibleCharsNonWhite(translated.get(i));
                }
            }
            assertSubstringStyle(translated.get(0), "英雄 亥伯龍 ✪✪✪✪✪", legacyColor(ChatFormatting.GOLD), null, false, null);
            assertSubstringStyle(translated.get(1), tokenDictionary.resolveToken("STAT_GEAR_SCORE") + "：", legacyColor(ChatFormatting.GRAY), null, false, null);
            assertSubstringStyle(translated.get(1), "1100", legacyColor(ChatFormatting.LIGHT_PURPLE), null, false, null);
            assertSubstringStyle(translated.get(2), "傷害：", legacyColor(ChatFormatting.GRAY), null, false, null);
            assertSubstringStyle(translated.get(2), "+260", legacyColor(ChatFormatting.RED), null, false, null);
            assertSubstringStyle(translated.get(2), "(+30)", legacyColor(ChatFormatting.YELLOW), null, false, null);
            assertSubstringStyle(translated.get(4), "智力：", legacyColor(ChatFormatting.GRAY), null, false, null);
            assertSubstringStyle(translated.get(4), "+350", legacyColor(ChatFormatting.AQUA), null, false, null);
            assertSubstringStyle(translated.get(5), "[\ue003] [⚔]", legacyColor(ChatFormatting.DARK_GRAY), null, false, null);
            assertSubstringStyle(translated.get(7), "鋒利 V, 暴擊 VI, 巨人殺手 V", legacyColor(ChatFormatting.BLUE), null, false, null);
            assertSubstringStyle(translated.get(9), "技能：凋零衝擊", legacyColor(ChatFormatting.GOLD), null, false, null);
            assertSubstringStyle(translated.get(9), "右鍵點擊", legacyColor(ChatFormatting.YELLOW), true, false, null);
            assertSubstringStyle(translated.get(10), "魔力消耗：", legacyColor(ChatFormatting.DARK_GRAY), null, false, null);
            assertSubstringStyle(translated.get(10), "150\ue003", legacyColor(ChatFormatting.AQUA), null, false, null);
            assertSubstringStyle(translated.get(11), "❣ ", legacyColor(ChatFormatting.DARK_RED), null, false, null);
            assertSubstringStyle(translated.get(11), "需要通關 地下墓穴 第七層。", legacyColor(ChatFormatting.RED), null, false, null);
            assertSubstringStyle(translated.get(12), "傳奇 地下城 劍", legacyColor(ChatFormatting.GOLD), true, false, null);

            assertListUnmutated(hyperionTooltip, origStrings, origSpans);
        }

        @Test
        @DisplayName("R1-T4-02: Full Hypixel SkyBlock Scoreboard Sidebar with 16-char sibling splits preserves Style & Skyblocker input")
        void testFullScoreboardSidebarWorkload() {
            TokenDictionary bundledTokens = com.hypixel.translation.core.TranslationCore.getInstance().getTokenDictionary();
            String expectedSkyblock = bundledTokens.translateText("SKYBLOCK");
            String expectedIsland = bundledTokens.translateText("Your Island");
            String expectedPurse = bundledTokens.translateText("Purse");
            String expectedObjective = com.hypixel.translation.core.TranslationCore.getInstance()
                    .translateAndCapture("Objective:", ComponentCategory.SCOREBOARD);
            String expectedJerry = bundledTokens.translateText("Jerry");

            assertTrue(containsCjk(expectedSkyblock) && containsCjk(expectedIsland)
                    && containsCjk(expectedPurse) && containsCjk(expectedObjective) && containsCjk(expectedJerry));

            List<Component> sidebar = List.of(
                    Component.literal("SKYBLOCK").setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW).withBold(true)),
                    Component.empty()
                            .append(Component.literal("04/15/26 ").withStyle(ChatFormatting.GRAY))
                            .append(Component.literal("m123D").withStyle(ChatFormatting.DARK_GRAY)),
                    Component.empty()
                            .append(Component.literal(" ⏣ ").withStyle(ChatFormatting.GRAY))
                            .append(Component.literal("Your Isla").withStyle(ChatFormatting.GREEN))
                            .append(Component.literal("nd").withStyle(ChatFormatting.GREEN)),
                    Component.empty()
                            .append(Component.literal("Pur").withStyle(ChatFormatting.WHITE))
                            .append(Component.literal("se: ").withStyle(ChatFormatting.WHITE))
                            .append(Component.literal("12,450,200").withStyle(ChatFormatting.GOLD)),
                    Component.empty()
                            .append(Component.literal("Obje").withStyle(ChatFormatting.GOLD))
                            .append(Component.literal("ctive:").withStyle(ChatFormatting.GOLD)),
                    Component.empty()
                            .append(Component.literal("Talk to ").withStyle(ChatFormatting.YELLOW))
                            .append(Component.literal("Jer").withStyle(ChatFormatting.YELLOW))
                            .append(Component.literal("ry").withStyle(ChatFormatting.YELLOW)),
                    Component.literal("www.hypixel.net").setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW))
            );
            List<String> origStrings = snapshotStrings(sidebar);
            List<List<StyledSpan>> origSpans = snapshotSpans(sidebar);

            List<Component> translated = new ArrayList<>(sidebar.size());
            for (Component row : sidebar) {
                translated.add(ScoreboardTranslator.translate(row));
            }

            assertEquals(expectedSkyblock, translated.get(0).getString());
            assertSubstringStyle(translated.get(0), expectedSkyblock, legacyColor(ChatFormatting.YELLOW), true, null, null);

            assertEquals("04/15/26 m123D", translated.get(1).getString());
            assertSubstringStyle(translated.get(1), "04/15/26 ", legacyColor(ChatFormatting.GRAY), null, null, null);
            assertSubstringStyle(translated.get(1), "m123D", legacyColor(ChatFormatting.DARK_GRAY), null, null, null);

            assertTrue(translated.get(2).getString().contains(expectedIsland));
            assertFalse(translated.get(2).getString().contains("Your Isla"));
            assertSubstringStyle(translated.get(2), " ⏣ ", legacyColor(ChatFormatting.GRAY), null, null, null);
            assertSubstringStyle(translated.get(2), expectedIsland, legacyColor(ChatFormatting.GREEN), null, null, null);

            assertTrue(translated.get(3).getString().contains(expectedPurse));
            assertTrue(translated.get(3).getString().contains("12,450,200"));
            assertSubstringStyle(translated.get(3), expectedPurse, legacyColor(ChatFormatting.WHITE), null, null, null);
            assertSubstringStyle(translated.get(3), "12,450,200", legacyColor(ChatFormatting.GOLD), null, null, null);

            assertEquals(expectedObjective, translated.get(4).getString());
            assertSubstringStyle(translated.get(4), expectedObjective, legacyColor(ChatFormatting.GOLD), null, null, null);

            assertTrue(translated.get(5).getString().contains(expectedJerry));
            assertFalse(translated.get(5).getString().contains("Jerry"));
            assertSubstringStyle(translated.get(5), expectedJerry, legacyColor(ChatFormatting.YELLOW), null, null, null);

            assertEquals("www.hypixel.net", translated.get(6).getString());
            assertSubstringStyle(translated.get(6), "www.hypixel.net", legacyColor(ChatFormatting.YELLOW), null, null, null);

            // Original Scoreboard Components remain pure English for other client mods (e.g., Skyblocker)
            assertListUnmutated(sidebar, origStrings, origSpans);
        }
    }
}
