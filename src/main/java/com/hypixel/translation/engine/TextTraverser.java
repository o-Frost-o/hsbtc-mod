package com.hypixel.translation.engine;

import net.minecraft.network.chat.*;
import net.minecraft.network.chat.contents.PlainTextContents;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Deep text component traverser that recursively visits Minecraft Component trees,
 * preserves all Style formatting (colors, fonts, bold, italic, underline, strikethrough, obfuscated),
 * preserves ClickEvent records 100% verbatim without translation,
 * and recursively translates HoverEvent.ShowText tooltips.
 */
public class TextTraverser {

    private static final TextTraverser INSTANCE = new TextTraverser();

    public static TextTraverser getInstance() {
        return INSTANCE;
    }

    public TextTraverser() {}

    private record StyledPart(String text, Style style) {}

    /**
     * Recursively translates a Component tree using the given String translation function.
     *
     * @param component The original Component to translate.
     * @param stringTranslator The translation function for literal string contents.
     * @return A newly constructed MutableComponent tree with translated text and preserved events/styles.
     */
    public MutableComponent translateRecursive(Component component, Function<String, String> stringTranslator) {
        if (component == null) {
            return Component.empty();
        }

        MutableComponent result;
        ComponentContents contents = component.getContents();
        if (contents instanceof PlainTextContents ptc) {
            String selfText = ptc.text();
            String translated = applyTranslator(selfText, stringTranslator);
            result = Component.literal(translated);
        } else {
            List<StyledPart> rawParts = new ArrayList<>();
            contents.visit((partStyle, partText) -> {
                if (partText != null && !partText.isEmpty()) {
                    Style safeStyle = partStyle != null ? partStyle : Style.EMPTY;
                    if (!rawParts.isEmpty() && Objects.equals(rawParts.get(rawParts.size() - 1).style(), safeStyle)) {
                        StyledPart prev = rawParts.remove(rawParts.size() - 1);
                        rawParts.add(new StyledPart(prev.text() + partText, safeStyle));
                    } else {
                        rawParts.add(new StyledPart(partText, safeStyle));
                    }
                }
                return Optional.empty();
            }, Style.EMPTY);

            if (rawParts.isEmpty()) {
                if (component.getSiblings().isEmpty()) {
                    String fallback = component.getString();
                    result = fallback.isEmpty()
                            ? Component.empty()
                            : Component.literal(applyTranslator(fallback, stringTranslator));
                } else {
                    result = Component.empty();
                }
            } else if (rawParts.size() == 1 && rawParts.get(0).style().isEmpty()) {
                result = Component.literal(applyTranslator(rawParts.get(0).text(), stringTranslator));
            } else {
                result = Component.empty();
                for (StyledPart part : rawParts) {
                    String translatedPart = applyTranslator(part.text(), stringTranslator);
                    Style translatedPartStyle = translateStyle(part.style(), stringTranslator);
                    result.append(Component.literal(translatedPart).setStyle(translatedPartStyle));
                }
            }
        }

        Style origStyle = component.getStyle();
        if (origStyle != null) {
            result.setStyle(translateStyle(origStyle, stringTranslator));
        }

        // Recursively translate and attach all siblings
        for (Component sibling : component.getSiblings()) {
            result.append(translateRecursive(sibling, stringTranslator));
        }

        return result;
    }

    private Style translateStyle(Style origStyle, Function<String, String> stringTranslator) {
        if (origStyle == null) {
            return Style.EMPTY;
        }
        if (origStyle.getHoverEvent() instanceof HoverEvent.ShowText showText) {
            Component inner = showText.value();
            Component translatedInner = translateRecursive(inner, stringTranslator);
            return origStyle.withHoverEvent(new HoverEvent.ShowText(translatedInner));
        }
        return origStyle;
    }

    private static String applyTranslator(String text, Function<String, String> stringTranslator) {
        String translated = stringTranslator != null ? stringTranslator.apply(text) : text;
        return translated != null ? translated : text;
    }

    public static MutableComponent translate(Component component, Function<String, String> stringTranslator) {
        return INSTANCE.translateRecursive(component, stringTranslator);
    }
}
