package com.hypixel.translation.engine;

import com.hypixel.translation.core.TokenRecord;
import com.hypixel.translation.e2e.BaseE2ETest;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@Tag("engine")
@Tag("component")
public class DynamicItemCombinerTest extends BaseE2ETest {

    @Test
    @DisplayName("Verify standard reforge decomposition and translation")
    void testStandardReforge() {
        MutableComponent comp = dynamicItemCombiner.translateItemName(null, "§6Heroic Hyperion");
        assertEquals("§6英雄 亥伯龍", comp.getString());
        assertEquals("Hyperion", dynamicItemCombiner.getCleanEnglishName("§6Heroic Hyperion", "heroic"));
    }

    @Test
    @DisplayName("Verify dungeon stars extraction and retention")
    void testDungeonStars() {
        MutableComponent comp = dynamicItemCombiner.translateItemName(null, "§6Hyperion ✪✪✪✪✪");
        assertEquals("§6亥伯龍 ✪✪✪✪✪", comp.getString());
        assertEquals("Hyperion", dynamicItemCombiner.getCleanEnglishName("§6Hyperion ✪✪✪✪✪", null));
    }

    @Test
    @DisplayName("Verify master mode stars extraction and retention")
    void testMasterModeStars() {
        MutableComponent comp = dynamicItemCombiner.translateItemName(null, "§6Terminator ✪✪✪✪§c➊");
        assertEquals("§6終結者 ✪✪✪✪§c➊", comp.getString());
        assertEquals("Terminator", dynamicItemCombiner.getCleanEnglishName("§6Terminator ✪✪✪✪§c➊", null));
    }

    @Test
    @DisplayName("Verify 276 collision base item Ancient Claw does not strip Ancient prefix")
    void testCollisionAncientClaw() {
        // Path A: without modifier NBT
        MutableComponent compA = dynamicItemCombiner.translateItemName(null, "§aAncient Claw");
        assertEquals("§a古代之爪", compA.getString());
        assertEquals("Ancient Claw", dynamicItemCombiner.getCleanEnglishName("§aAncient Claw", null));

        // Path B: with actual reforge Ancient Ancient Claw
        MutableComponent compB = dynamicItemCombiner.translateItemName(null, "§aAncient Ancient Claw");
        assertEquals("§a遠古 古代之爪", compB.getString());
        assertEquals("Ancient Claw", dynamicItemCombiner.getCleanEnglishName("§aAncient Ancient Claw", "ancient"));
    }

    @Test
    @DisplayName("Verify 276 collision base item Warped Stone text-only matching")
    void testCollisionWarpedStone() {
        MutableComponent comp = dynamicItemCombiner.translateItemName(null, "§5Warped Stone");
        assertEquals("§5扭曲之石", comp.getString());
        assertEquals("Warped Stone", dynamicItemCombiner.getCleanEnglishName("§5Warped Stone", null));
    }

    @Test
    @DisplayName("Verify pet level decomposition and rarity color retention")
    void testPetLevelDecomposition() {
        MutableComponent comp = dynamicItemCombiner.translateItemName(null, "§7[Lvl 100] §6Baby Yeti");
        assertEquals("§7[Lvl 100] §6雪怪寶寶", comp.getString());
        assertEquals("Baby Yeti", dynamicItemCombiner.getCleanEnglishName("§7[Lvl 100] §6Baby Yeti", null));
    }

    @Test
    @DisplayName("Verify uncataloged item suffix fallback rules")
    void testSuffixFallbackRules() {
        MutableComponent sword = dynamicItemCombiner.translateItemName(null, "Emerald Shortsword");
        assertEquals("Emerald 短劍", sword.getString());

        MutableComponent helmet = dynamicItemCombiner.translateItemName(null, "Obsidian Helmet");
        assertEquals("Obsidian 頭盔", helmet.getString());
    }

    @Test
    @DisplayName("Verify tooltip translation with Alt/Shift preview toggle")
    void testTooltipPreviewToggle() {
        List<Component> original = List.of(
                Component.literal("§6Heroic Hyperion ✪✪✪✪✪"),
                Component.literal("§7Damage: §c+260")
        );

        // Alt down -> English returned
        List<Component> english = dynamicItemCombiner.translateTooltip(null, original, true);
        assertEquals("§6Heroic Hyperion ✪✪✪✪✪", english.get(0).getString());

        // Alt up -> zh_tw returned
        List<Component> zh = dynamicItemCombiner.translateTooltip(null, original, false);
        assertEquals("§6英雄 亥伯龍 ✪✪✪✪✪", zh.get(0).getString());
    }

    @Test
    @DisplayName("Verify dynamic TokenDictionary reforges and extended suffix fallback")
    void testDynamicReforgeAndExtendedSuffixes() {
        tokenDictionary.registerToken(new TokenRecord("REFORGE_LUSTROUS", "Lustrous", "光澤", "", "§d", "reforge"));
        MutableComponent lustrousDrill = dynamicItemCombiner.translateItemName(null, "§dLustrous Divan's Drill");
        assertEquals("§d光澤 迪萬鑽頭", lustrousDrill.getString());

        assertEquals("Sapphire 護手", dynamicItemCombiner.fallbackTranslate("Sapphire Gloves"));
        assertEquals("Crimson 項鍊", dynamicItemCombiner.fallbackTranslate("Crimson Necklace"));
        assertEquals("Emerald 短劍", dynamicItemCombiner.fallbackTranslate("Emerald Shortsword"));
    }

    private static List<Style> collectCharStyles(Component component) {
        List<Style> styles = new ArrayList<>();
        component.visit((style, text) -> {
            if (text != null) {
                for (int i = 0; i < text.length(); i++) {
                    styles.add(style);
                }
            }
            return Optional.empty();
        }, Style.EMPTY);
        return styles;
    }

    private static void assertSubstrStyle(Component comp, String substr, TextColor color, Boolean bold, Boolean italic, Boolean obfuscated) {
        String full = comp.getString();
        int idx = full.indexOf(substr);
        assertTrue(idx >= 0, "Expected '" + substr + "' in '" + full + "'");
        List<Style> styles = collectCharStyles(comp);
        for (int i = idx; i < idx + substr.length(); i++) {
            Style s = styles.get(i);
            if (color != null) {
                assertEquals(color, s.getColor(), "Color mismatch at '" + full.charAt(i) + "' in '" + full + "'");
            }
            if (bold != null) {
                assertEquals(bold, s.isBold(), "Bold mismatch at '" + full.charAt(i) + "' in '" + full + "'");
            }
            if (italic != null) {
                assertEquals(italic, s.isItalic(), "Italic mismatch at '" + full.charAt(i) + "' in '" + full + "'");
            }
            if (obfuscated != null) {
                assertEquals(obfuscated, s.isObfuscated(), "Obfuscated mismatch at '" + full.charAt(i) + "' in '" + full + "'");
            }
        }
    }

    @Test
    @DisplayName("Verify single-node and ItemStack.getStyledHoverName-shaped Component item names preserve RGB, ChatFormatting, bold, and !italic")
    void testModernItemNameComponentStylePreservation() {
        TextColor customGold = TextColor.fromRgb(0xFFAA11);
        Component hoverLike = Component.empty()
                .append(Component.literal("Heroic Hyperion")
                        .setStyle(Style.EMPTY.withColor(customGold).withBold(true).withItalic(false)))
                .withStyle(ChatFormatting.GOLD);

        MutableComponent translated = dynamicItemCombiner.translateItemNameComponent(null, hoverLike);
        assertEquals("英雄 亥伯龍", translated.getString());
        assertSubstrStyle(translated, "英雄 亥伯龍", customGold, true, false, null);
    }

    @Test
    @DisplayName("Verify multi-run pet name Component preserves GRAY level and GOLD pet name without §")
    void testMultiRunPetNameComponentPreservesDistinctStyles() {
        Component pet = Component.empty()
                .append(Component.literal("[Lvl 100] ").setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY).withItalic(false)))
                .append(Component.literal("Baby Yeti").setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withItalic(false)));

        MutableComponent translated = dynamicItemCombiner.translateItemNameComponent(null, pet);
        assertEquals("[Lvl 100] 雪怪寶寶", translated.getString());
        assertSubstrStyle(translated, "[Lvl 100]", TextColor.fromLegacyFormat(ChatFormatting.GRAY), null, false, null);
        assertSubstrStyle(translated, "雪怪寶寶", TextColor.fromLegacyFormat(ChatFormatting.GOLD), null, false, null);
    }

    @Test
    @DisplayName("Verify multi-run starred and master-starred item name preserves GOLD stars and RED master star")
    void testMultiRunStarredItemNamePreservesStarColors() {
        Component starred = Component.empty()
                .setStyle(Style.EMPTY.withItalic(false))
                .append(Component.literal("Terminator ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("✪✪✪✪").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("➊").withStyle(ChatFormatting.RED));

        MutableComponent translated = dynamicItemCombiner.translateItemNameComponent(null, starred);
        assertEquals("終結者 ✪✪✪✪➊", translated.getString());
        assertSubstrStyle(translated, "終結者", TextColor.fromLegacyFormat(ChatFormatting.GOLD), null, false, null);
        assertSubstrStyle(translated, "✪✪✪✪", TextColor.fromLegacyFormat(ChatFormatting.GOLD), null, false, null);
        assertSubstrStyle(translated, "➊", TextColor.fromLegacyFormat(ChatFormatting.RED), null, false, null);
    }

    @Test
    @DisplayName("Verify multi-run lore stat line via translateTooltip preserves GRAY, RED, YELLOW")
    void testMultiRunLoreStatLinePreservesStyles() {
        List<Component> tooltip = List.of(
                Component.literal("Hyperion").setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withItalic(false)),
                Component.empty().setStyle(Style.EMPTY.withItalic(false))
                        .append(Component.literal("Damage: ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("+260").withStyle(ChatFormatting.RED))
                        .append(Component.literal(" (+30)").withStyle(ChatFormatting.YELLOW))
        );

        List<Component> translated = dynamicItemCombiner.translateTooltip(null, tooltip, false);
        assertEquals(2, translated.size());
        assertEquals("亥伯龍", translated.get(0).getString());
        assertEquals("傷害：+260 (+30)", translated.get(1).getString());
        assertSubstrStyle(translated.get(1), "傷害：", TextColor.fromLegacyFormat(ChatFormatting.GRAY), null, false, null);
        assertSubstrStyle(translated.get(1), "+260", TextColor.fromLegacyFormat(ChatFormatting.RED), null, false, null);
        assertSubstrStyle(translated.get(1), "(+30)", TextColor.fromLegacyFormat(ChatFormatting.YELLOW), null, false, null);
    }

    @Test
    @DisplayName("Verify multi-run ability headers (double and single space), cooldown, and enchantments preserve styles")
    void testMultiRunAbilityCooldownAndEnchantmentLore() {
        // Double-space ability header
        Component doubleSpace = Component.empty().setStyle(Style.EMPTY.withItalic(false))
                .append(Component.literal("Ability: ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("Wither Impact").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("  ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("RIGHT CLICK").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        MutableComponent outDouble = dynamicItemCombiner.translateLoreComponent(doubleSpace);
        assertEquals("技能：凋零衝擊  右鍵點擊", outDouble.getString());
        assertSubstrStyle(outDouble, "技能：", TextColor.fromLegacyFormat(ChatFormatting.GOLD), null, false, null);
        assertSubstrStyle(outDouble, "凋零衝擊", TextColor.fromLegacyFormat(ChatFormatting.YELLOW), null, false, null);
        assertSubstrStyle(outDouble, "右鍵點擊", TextColor.fromLegacyFormat(ChatFormatting.YELLOW), true, false, null);

        // Single-space ability header
        Component singleSpace = Component.empty().setStyle(Style.EMPTY.withItalic(false))
                .append(Component.literal("Ability: ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("Wither Impact").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("RIGHT CLICK").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        MutableComponent outSingle = dynamicItemCombiner.translateLoreComponent(singleSpace);
        assertEquals("技能：凋零衝擊 右鍵點擊", outSingle.getString());
        assertSubstrStyle(outSingle, "技能：", TextColor.fromLegacyFormat(ChatFormatting.GOLD), null, false, null);
        assertSubstrStyle(outSingle, "凋零衝擊", TextColor.fromLegacyFormat(ChatFormatting.YELLOW), null, false, null);
        assertSubstrStyle(outSingle, "右鍵點擊", TextColor.fromLegacyFormat(ChatFormatting.YELLOW), true, false, null);

        // Cooldown multi-run
        Component cooldown = Component.empty().setStyle(Style.EMPTY.withItalic(false))
                .append(Component.literal("Cooldown: ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("10").withStyle(ChatFormatting.GREEN))
                .append(Component.literal("s").withStyle(ChatFormatting.GREEN));
        MutableComponent outCd = dynamicItemCombiner.translateLoreComponent(cooldown);
        assertEquals("冷卻時間：10秒", outCd.getString());
        assertSubstrStyle(outCd, "冷卻時間：", TextColor.fromLegacyFormat(ChatFormatting.DARK_GRAY), null, false, null);
        assertSubstrStyle(outCd, "10秒", TextColor.fromLegacyFormat(ChatFormatting.GREEN), null, false, null);

        // Enchantments multi-run
        Component ench = Component.empty().setStyle(Style.EMPTY.withItalic(false))
                .append(Component.literal("Ultimate Wise V").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD))
                .append(Component.literal(", Protection VI").withStyle(ChatFormatting.BLUE));
        MutableComponent outEnch = dynamicItemCombiner.translateLoreComponent(ench);
        assertEquals("終極智慧 V, 保護 VI", outEnch.getString());
        assertSubstrStyle(outEnch, "終極智慧 V", TextColor.fromLegacyFormat(ChatFormatting.LIGHT_PURPLE), true, false, null);
        assertSubstrStyle(outEnch, ", 保護 VI", TextColor.fromLegacyFormat(ChatFormatting.BLUE), false, false, null);
    }

    @Test
    @DisplayName("Verify obfuscated rarity footer preserves obfuscated markers and bold GOLD")
    void testObfuscatedRarityFooterPreservation() {
        Component footer = Component.empty()
                .setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withBold(true).withItalic(false))
                .append(Component.literal("a").withStyle(Style.EMPTY.withObfuscated(true)))
                .append(Component.literal(" LEGENDARY DUNGEON SWORD "))
                .append(Component.literal("a").withStyle(Style.EMPTY.withObfuscated(true)));

        MutableComponent translated = dynamicItemCombiner.translateLoreComponent(footer);
        assertEquals("a 傳奇 地下城 劍 a", translated.getString());
        assertSubstrStyle(translated, "傳奇 地下城 劍", TextColor.fromLegacyFormat(ChatFormatting.GOLD), true, false, false);
        List<Style> styles = collectCharStyles(translated);
        assertTrue(styles.get(0).isObfuscated());
        assertTrue(styles.get(styles.size() - 1).isObfuscated());

        Component mythicFooter = Component.empty()
                .setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withBold(true).withItalic(false))
                .append(Component.literal("a").withStyle(Style.EMPTY.withObfuscated(true)))
                .append(Component.literal(" MYTHIC DUNGEON SWORD "))
                .append(Component.literal("a").withStyle(Style.EMPTY.withObfuscated(true)));

        MutableComponent mythicTranslated = dynamicItemCombiner.translateLoreComponent(mythicFooter);
        assertEquals("a 神話 地下城 劍 a", mythicTranslated.getString());
        assertSubstrStyle(mythicTranslated, "神話 地下城 劍", TextColor.fromLegacyFormat(ChatFormatting.GOLD), true, false, false);
        List<Style> mythicStyles = collectCharStyles(mythicTranslated);
        assertTrue(mythicStyles.get(0).isObfuscated());
        assertTrue(mythicStyles.get(mythicStyles.size() - 1).isObfuscated());
    }

    @Test
    @DisplayName("Verify inherited ItemLore.LORE_STYLE (DARK_PURPLE + italic) and explicit italic=false override")
    void testInheritedLoreStyleAndItalicFalseOverride() {
        Component parentItalic = Component.empty()
                .setStyle(Style.EMPTY.withColor(ChatFormatting.DARK_PURPLE).withItalic(true))
                .append(Component.literal("Mana Cost: 150"));
        MutableComponent outDefault = dynamicItemCombiner.translateLoreComponent(parentItalic);
        assertEquals("魔力消耗：150", outDefault.getString());
        assertSubstrStyle(outDefault, "魔力消耗：150", TextColor.fromLegacyFormat(ChatFormatting.DARK_PURPLE), null, true, null);

        Component childNonItalic = Component.empty()
                .setStyle(Style.EMPTY.withColor(ChatFormatting.DARK_PURPLE).withItalic(true))
                .append(Component.literal("Damage: +260").setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY).withItalic(false)));
        MutableComponent outOverride = dynamicItemCombiner.translateLoreComponent(childNonItalic);
        assertEquals("傷害：+260", outOverride.getString());
        assertSubstrStyle(outOverride, "傷害：+260", TextColor.fromLegacyFormat(ChatFormatting.GRAY), null, false, null);
    }

    @Test
    @DisplayName("Verify mixed legacy § codes + Style (italic=false) retain both § codes and Style")
    void testMixedLegacyCodesAndModernStyle() {
        Component name = Component.literal("§6Heroic Hyperion").withStyle(s -> s.withItalic(false));
        MutableComponent outName = dynamicItemCombiner.translateItemNameComponent(null, name);
        assertEquals("§6英雄 亥伯龍", outName.getString());
        assertFalse(outName.getStyle().isItalic());

        Component lore = Component.literal("§7Damage: §c+260").withStyle(s -> s.withItalic(false));
        MutableComponent outLore = dynamicItemCombiner.translateLoreComponent(lore);
        assertEquals("§7傷害：§c+260", outLore.getString());
        assertFalse(outLore.getStyle().isItalic());
    }

    @Test
    @DisplayName("Verify empty/blank/null components and HoverEvent/ClickEvent preservation")
    void testEmptyBlankNullAndEventsPreservation() {
        assertEquals("", dynamicItemCombiner.translateItemNameComponent(null, null).getString());
        assertEquals("", dynamicItemCombiner.translateItemNameComponent(null, Component.empty()).getString());
        assertEquals("", dynamicItemCombiner.translateLoreComponent(null).getString());
        assertEquals("", dynamicItemCombiner.translateLoreComponent(Component.empty()).getString());
        assertEquals("   ", dynamicItemCombiner.translateLoreComponent(
                Component.literal("   ").withStyle(ChatFormatting.GRAY)).getString());

        ClickEvent click = new ClickEvent.RunCommand("/viewrecipe HYPERION");
        HoverEvent hover = new HoverEvent.ShowText(Component.literal("Click to craft"));
        Component withEvents = Component.literal("Hyperion")
                .setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withClickEvent(click).withHoverEvent(hover));
        MutableComponent out = dynamicItemCombiner.translateItemNameComponent(null, withEvents);
        assertEquals("亥伯龍", out.getString());
        assertEquals(click, out.getStyle().getClickEvent());
        assertEquals(hover, out.getStyle().getHoverEvent());
    }

    @Test
    @DisplayName("Verify multi-char §kaa footer, colon-anchor compression bleed, cross-Style space tie-break, and §-code extraction in multi-run lore")
    void testIteration2AlignmentAndSectionLeakRegressions() {
        // 1. Multi-run §kaa rarity footer (A7e)
        Component multiRunSectionK = Component.empty()
                .append(Component.literal("§6§l§k").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("aa MYTHIC DUNGEON SWORD §6§l§kaa").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        MutableComponent outMultiK = dynamicItemCombiner.translateLoreComponent(multiRunSectionK);
        assertEquals("§6§l§kaa 神話 地下城 劍 §6§l§kaa", outMultiK.getString());

        // 2. Ability: Instant Transmission  RIGHT CLICK (A8a) -> '：' stays GOLD
        Component instantTrans = Component.empty().setStyle(Style.EMPTY.withItalic(false))
                .append(Component.literal("Ability: ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("Instant Transmission").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("  ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("RIGHT CLICK").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        MutableComponent outInstant = dynamicItemCombiner.translateLoreComponent(instantTrans);
        assertEquals("技能：瞬間移動  右鍵點擊", outInstant.getString());
        assertSubstrStyle(outInstant, "技能：", TextColor.fromLegacyFormat(ChatFormatting.GOLD), null, false, null);
        assertSubstrStyle(outInstant, "瞬間移動", TextColor.fromLegacyFormat(ChatFormatting.YELLOW), null, false, null);
        assertSubstrStyle(outInstant, "右鍵點擊", TextColor.fromLegacyFormat(ChatFormatting.YELLOW), true, false, null);

        // 3. Full Set Bonus: Bloodlust (A8b) -> '：' stays GOLD, '嗜血' is RED
        Component fullSet = Component.empty().setStyle(Style.EMPTY.withItalic(false))
                .append(Component.literal("Full Set Bonus: ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("Bloodlust").withStyle(ChatFormatting.RED));
        MutableComponent outFullSet = dynamicItemCombiner.translateLoreComponent(fullSet);
        assertEquals("全套裝加成：嗜血", outFullSet.getString());
        assertSubstrStyle(outFullSet, "全套裝加成：", TextColor.fromLegacyFormat(ChatFormatting.GOLD), null, false, null);
        assertSubstrStyle(outFullSet, "嗜血", TextColor.fromLegacyFormat(ChatFormatting.RED), null, false, null);

        // 4. Requires The Catacombs Floor VII Completion. (A8c) -> '需要通關' stays RED
        Component reqCata = Component.empty().setStyle(Style.EMPTY.withItalic(false))
                .append(Component.literal("Requires ").withStyle(ChatFormatting.RED))
                .append(Component.literal("The Catacombs Floor VII Completion.").withStyle(ChatFormatting.GOLD));
        MutableComponent outReqCata = dynamicItemCombiner.translateLoreComponent(reqCata);
        assertEquals("需要通關 地下墓穴 第七層。", outReqCata.getString());
        assertSubstrStyle(outReqCata, "需要通關", TextColor.fromLegacyFormat(ChatFormatting.RED), null, false, null);
        assertSubstrStyle(outReqCata, "地下墓穴 第七層。", TextColor.fromLegacyFormat(ChatFormatting.GOLD), null, false, null);

        // 5. Multi-run lore with TokenDictionary §b-coded token (A6b) + §r reset -> no raw '§' in getString()
        tokenDictionary.registerToken(new TokenRecord("ABIL_SPARK", "Spark", "§b火花", "", "", "ability"));
        tokenDictionary.registerToken(new TokenRecord("ABIL_FLARE", "Flare", "§b火§r焰", "", "", "ability"));
        Component sparkLine = Component.empty().setStyle(Style.EMPTY.withItalic(false))
                .append(Component.literal("Ability: ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("Spark").withStyle(ChatFormatting.YELLOW));
        MutableComponent outSpark = dynamicItemCombiner.translateLoreComponent(sparkLine);
        assertEquals("技能：火花", outSpark.getString());
        assertFalse(outSpark.getString().contains("§"));
        assertSubstrStyle(outSpark, "技能：", TextColor.fromLegacyFormat(ChatFormatting.GOLD), null, false, null);
        assertSubstrStyle(outSpark, "火花", TextColor.fromLegacyFormat(ChatFormatting.AQUA), null, false, null);

        Component flareLine = Component.empty().setStyle(Style.EMPTY.withItalic(false))
                .append(Component.literal("Ability: ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("Flare").withStyle(ChatFormatting.YELLOW));
        MutableComponent outFlare = dynamicItemCombiner.translateLoreComponent(flareLine);
        assertEquals("技能：火焰", outFlare.getString());
        assertFalse(outFlare.getString().contains("§"));
        assertSubstrStyle(outFlare, "火", TextColor.fromLegacyFormat(ChatFormatting.AQUA), null, false, null);
        assertSubstrStyle(outFlare, "焰", TextColor.fromLegacyFormat(ChatFormatting.YELLOW), null, false, null);
    }
}
