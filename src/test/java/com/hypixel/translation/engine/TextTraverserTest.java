package com.hypixel.translation.engine;

import com.hypixel.translation.e2e.BaseE2ETest;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

@Tag("engine")
@Tag("component")
public class TextTraverserTest extends BaseE2ETest {

    @Test
    @DisplayName("Verify literal translation preserves component text")
    void testLiteralTranslation() {
        Component c = Component.literal("Hello World");
        MutableComponent t = textTraverser.translateRecursive(c, s -> "哈囉世界");
        assertEquals("哈囉世界", t.getString());
    }

    @Test
    @DisplayName("Verify Style colors, bold, italic, and underline are preserved")
    void testStylePreservation() {
        Style style = Style.EMPTY
                .withColor(TextColor.fromLegacyFormat(ChatFormatting.GOLD))
                .withBold(true)
                .withItalic(true)
                .withUnderlined(true);

        Component c = Component.literal("Styled Text").setStyle(style);
        MutableComponent t = textTraverser.translateRecursive(c, s -> "樣式文字");

        assertEquals("樣式文字", t.getString());
        assertNotNull(t.getStyle());
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GOLD), t.getStyle().getColor());
        assertTrue(t.getStyle().isBold());
        assertTrue(t.getStyle().isItalic());
        assertTrue(t.getStyle().isUnderlined());
    }

    @Test
    @DisplayName("Verify ClickEvent RunCommand command string is preserved 100% verbatim")
    void testClickEventRunCommandVerbatim() {
        ClickEvent click = new ClickEvent.RunCommand("/island warp home");
        Component c = Component.literal("[WARP]").setStyle(Style.EMPTY.withClickEvent(click));

        MutableComponent t = textTraverser.translateRecursive(c, s -> "[傳送]");
        assertEquals("[傳送]", t.getString());
        assertInstanceOf(ClickEvent.RunCommand.class, t.getStyle().getClickEvent());
        assertEquals("/island warp home", ((ClickEvent.RunCommand) t.getStyle().getClickEvent()).command());
    }

    @Test
    @DisplayName("Verify ClickEvent OpenUrl URI is preserved 100% verbatim")
    void testClickEventOpenUrlVerbatim() {
        ClickEvent click = new ClickEvent.OpenUrl(URI.create("https://wiki.hypixel.net"));
        Component c = Component.literal("[WIKI]").setStyle(Style.EMPTY.withClickEvent(click));

        MutableComponent t = textTraverser.translateRecursive(c, s -> "[維基]");
        assertEquals("[維基]", t.getString());
        assertInstanceOf(ClickEvent.OpenUrl.class, t.getStyle().getClickEvent());
        assertEquals("https://wiki.hypixel.net", ((ClickEvent.OpenUrl) t.getStyle().getClickEvent()).uri().toString());
    }

    @Test
    @DisplayName("Verify HoverEvent ShowText inner tooltip component is recursively translated")
    void testHoverEventRecursiveTranslation() {
        Component inner = Component.literal("Damage: +260");
        HoverEvent hover = new HoverEvent.ShowText(inner);
        Component c = Component.literal("Hover Slot").setStyle(Style.EMPTY.withHoverEvent(hover));

        MutableComponent t = textTraverser.translateRecursive(c, s -> {
            if (s.equals("Hover Slot")) return "懸浮槽位";
            if (s.equals("Damage: +260")) return "傷害：+260";
            return s;
        });

        assertEquals("懸浮槽位", t.getString());
        assertNotNull(t.getStyle().getHoverEvent());
        assertInstanceOf(HoverEvent.ShowText.class, t.getStyle().getHoverEvent());
        HoverEvent.ShowText st = (HoverEvent.ShowText) t.getStyle().getHoverEvent();
        assertEquals("傷害：+260", st.value().getString());
    }

    @Test
    @DisplayName("Verify nested sibling components maintain tree hierarchy and order")
    void testNestedSiblingsPreservation() {
        Component root = Component.literal("A ")
                .append(Component.literal("B "))
                .append(Component.literal("C"));

        MutableComponent t = textTraverser.translateRecursive(root, s -> {
            if (s.equals("A ")) return "甲 ";
            if (s.equals("B ")) return "乙 ";
            if (s.equals("C")) return "丙";
            return s;
        });

        assertEquals("甲 乙 丙", t.getString());
        assertEquals(2, t.getSiblings().size());
    }

    @Test
    @DisplayName("Verify empty component handling does not throw exception")
    void testEmptyComponentHandling() {
        MutableComponent t = textTraverser.translateRecursive(Component.empty(), s -> s);
        assertEquals("", t.getString());
    }

    @Test
    @DisplayName("Verify Style tri-state explicit Boolean.FALSE is preserved without font pollution")
    void testTriStateStylePreservationExplicitFalse() {
        Style explicitFalse = Style.EMPTY.withBold(false).withUnderlined(false);
        Component c = Component.literal("Unstyled Text").setStyle(explicitFalse);

        MutableComponent t = textTraverser.translateRecursive(c, s -> "無樣式文字");

        assertEquals("無樣式文字", t.getString());
        assertNotNull(t.getStyle());
        assertEquals(explicitFalse, t.getStyle());
        assertTrue(t.getStyle().toString().contains("!bold"), "Style must retain explicit !bold");
        assertTrue(t.getStyle().toString().contains("!underlined"), "Style must retain explicit !underlined");
        assertFalse(t.getStyle().isBold());
        assertFalse(t.getStyle().isUnderlined());
    }

    @Test
    @DisplayName("Verify TranslatableContents with non-empty siblings preserves both translatable text and sibling styles")
    void testTranslatableContentsWithSiblings() {
        Component c = Component.translatableWithFallback("item.minecraft.diamond_sword", "Diamond Sword")
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" Damage").withStyle(ChatFormatting.RED));

        MutableComponent t = textTraverser.translateRecursive(c, s -> {
            if ("Diamond Sword".equals(s)) return "鑽石劍";
            if (" Damage".equals(s)) return " 傷害";
            return s;
        });

        assertEquals("鑽石劍 傷害", t.getString());
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.AQUA), t.getStyle().getColor());
        assertEquals(1, t.getSiblings().size());
        assertEquals(" 傷害", t.getSiblings().get(0).getString());
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.RED), t.getSiblings().get(0).getStyle().getColor());
    }

    @Test
    @DisplayName("Verify TranslatableContents with styled Component arguments preserves per-arg styles")
    void testTranslatableContentsWithStyledArgs() {
        Component c = Component.translatableWithFallback(
                        "hsbtr.test.stat",
                        "Stat: %s",
                        Component.literal("Damage").withStyle(ChatFormatting.RED))
                .withStyle(ChatFormatting.GRAY);

        MutableComponent t = textTraverser.translateRecursive(c, s -> {
            if ("Stat: ".equals(s)) return "屬性：";
            if ("Damage".equals(s)) return "傷害";
            return s;
        });

        assertEquals("屬性：傷害", t.getString());
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GRAY), t.getStyle().getColor());
        assertEquals(2, t.getSiblings().size());
        assertEquals("傷害", t.getSiblings().get(1).getString());
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.RED), t.getSiblings().get(1).getStyle().getColor());
    }

    @Test
    @DisplayName("Verify 24-bit RGB TextColor, nested sibling HoverEvent.ShowText, and ClickEvent.SuggestCommand preservation")
    void testRgbHoverAndSuggestCommandPreservation() {
        TextColor rgb = TextColor.fromRgb(0x123456);
        ClickEvent suggest = new ClickEvent.SuggestCommand("/msg Hypixel hello");
        HoverEvent hover = new HoverEvent.ShowText(Component.literal("Damage").withStyle(ChatFormatting.RED));
        Component root = Component.literal("Prefix ")
                .setStyle(Style.EMPTY.withColor(rgb))
                .append(Component.literal("[CLICK]").setStyle(Style.EMPTY.withClickEvent(suggest).withHoverEvent(hover)));

        MutableComponent t = textTraverser.translateRecursive(root, s -> {
            if ("Prefix ".equals(s)) return "前綴 ";
            if ("[CLICK]".equals(s)) return "[點擊]";
            if ("Damage".equals(s)) return "傷害";
            return s;
        });

        assertEquals("前綴 [點擊]", t.getString());
        assertEquals(rgb, t.getStyle().getColor());
        Component child = t.getSiblings().get(0);
        assertInstanceOf(ClickEvent.SuggestCommand.class, child.getStyle().getClickEvent());
        assertEquals("/msg Hypixel hello", ((ClickEvent.SuggestCommand) child.getStyle().getClickEvent()).command());
        assertInstanceOf(HoverEvent.ShowText.class, child.getStyle().getHoverEvent());
        Component hoverVal = ((HoverEvent.ShowText) child.getStyle().getHoverEvent()).value();
        assertEquals("傷害", hoverVal.getString());
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.RED), hoverVal.getStyle().getColor());
    }
}
