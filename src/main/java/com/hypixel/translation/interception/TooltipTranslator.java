package com.hypixel.translation.interception;

import com.hypixel.translation.config.ComponentCategory;
import com.hypixel.translation.config.ModConfig;
import com.hypixel.translation.core.CaptureTemplate;
import com.hypixel.translation.core.ItemLoreOverrides;
import com.hypixel.translation.core.LoreLineDictionary;
import com.hypixel.translation.core.MenuDictionary;
import com.hypixel.translation.core.TranslationCore;
import com.hypixel.translation.engine.DynamicItemCombiner;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.StringSplitter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Handles tooltip interception and translation.
 * Coordinates with DynamicItemCombiner, Alt/Shift preview toggle, and English subtitle formatting.
 */
public class TooltipTranslator {
    private static final ThreadLocal<List<Component>> RECENT_ITEM_TOOLTIP = new ThreadLocal<>();
    private static final ThreadLocal<MenuCapture> RECENT_MENU_CAPTURE = new ThreadLocal<>();

    private static final int MAX_DESCRIPTION_LINES = 12;
    private static final int MAX_DESCRIPTION_LENGTH = 750;
    private static final StringSplitter HEADLESS_SPLITTER = new StringSplitter((codePoint, style) -> codePoint < 128 ? 1 : 2);

    private record MenuCapture(String title, List<String> unresolved) {}
    private record WrappedDescription(int end, Component source, CaptureTemplate.Rendered translation) {}

    public static void markRecentlyTranslatedItemTooltip(List<Component> list) {
        if (list != null) {
            RECENT_ITEM_TOOLTIP.set(list);
        }
    }

    public static boolean consumeRecentlyTranslatedItemTooltip(List<Component> list) {
        List<Component> prev = RECENT_ITEM_TOOLTIP.get();
        RECENT_ITEM_TOOLTIP.remove();
        return prev != null && prev == list;
    }

    public static boolean isAltOrShiftPreviewActive() {
        return ModConfig.getInstance().isEnableAltShiftPreview() && isAltOrShiftDown();
    }

    /**
     * Checks if either Left/Right Alt or Left/Right Shift is currently held down.
     * Safely returns false in headless or non-GUI test environments.
     */
    public static boolean isAltOrShiftDown() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return false;
            Window window = mc.getWindow();
            if (window == null) return false;
            return InputConstants.isKeyDown(window, InputConstants.KEY_LALT)
                    || InputConstants.isKeyDown(window, InputConstants.KEY_RALT)
                    || InputConstants.isKeyDown(window, InputConstants.KEY_LSHIFT)
                    || InputConstants.isKeyDown(window, InputConstants.KEY_RSHIFT);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Translates an ItemStack tooltip, respecting Alt/Shift preview and gray English subtitle configuration.
     */
    public static List<Component> translateItemTooltip(ItemStack stack, List<Component> originalTooltip) {
        return translateItemTooltip(stack, originalTooltip, isAltOrShiftDown());
    }

    /**
     * Translates an ItemStack tooltip with explicit alt/shift preview state.
     */
    public static List<Component> translateItemTooltip(ItemStack stack, List<Component> originalTooltip, boolean isAltOrShiftDown) {
        return translateItemTooltip(stack, originalTooltip, isAltOrShiftDown, null);
    }

    public static List<Component> translateContainerTooltip(ItemStack stack, List<Component> originalTooltip, String title) {
        return translateItemTooltip(stack, originalTooltip, isAltOrShiftDown(), title);
    }

    private static List<Component> translateItemTooltip(
            ItemStack stack, List<Component> originalTooltip, boolean isAltOrShiftDown, String title) {
        if (originalTooltip == null || originalTooltip.isEmpty()) {
            return originalTooltip != null ? new ArrayList<>(originalTooltip) : List.of();
        }

        ModConfig config = ModConfig.getInstance();
        boolean previewActive = isAltOrShiftDown && config.isEnableAltShiftPreview();
        if (previewActive) {
            // Revert immediately to pure original English
            return new ArrayList<>(originalTooltip);
        }

        DynamicItemCombiner combiner = DynamicItemCombiner.getInstance();
        List<Component> translated;
        if (combiner != null) {
            translated = combiner.translateTooltip(stack, originalTooltip, false);
        } else {
            translated = new ArrayList<>(originalTooltip);
        }

        Map<Integer, ItemLoreOverrides.Match> edits = combiner == null ? Map.of()
                : combiner.matchEditedItemLore(stack, originalTooltip);
        if (title != null || InterceptionManager.isSkyBlock() || !edits.isEmpty()) {
            TranslationCore core = TranslationCore.getInstance();
            MenuDictionary menu = core.getMenuDictionary();
            boolean showDescriptions = menu.recognizesTitle(title) || InterceptionManager.isSkyBlock();
            if (showDescriptions) {
                boolean knownItem = combiner != null && combiner.hasKnownItem(stack);
                for (int i = 0; i < originalTooltip.size(); i++) {
                    Component original = originalTooltip.get(i);
                    if (original == null) continue;
                    boolean heading = i == 0;
                    String raw = original.getString();
                    String label = menu.translate(raw, heading);
                    Component itemTranslation = translated.get(i);
                    boolean itemTranslated = itemTranslation != null && !itemTranslation.getString().equals(raw);
                    boolean bilingualLabel = heading && label.endsWith("（" + raw.strip() + "）");
                    if (!label.equals(raw) && (!knownItem || !itemTranslated || bilingualLabel
                            || !heading && TranslationCore.hasUntranslatedProse(itemTranslation.getString()))) {
                        translated.set(i, DynamicItemCombiner.translateStyledLine(original, line -> menu.translate(line, heading)));
                    }
                }
            }
            if (showDescriptions || !edits.isEmpty()) {
                translated = renderDescriptions(originalTooltip, translated, 1, core, title, edits);
            }
        }

        // Configurable gray English subtitle: §8English: <English Base Name>
        if (config.isShowEnglishSubtitle() && stack != null) {
            String cleanEnglish = combiner != null ? combiner.getCleanEnglishName(stack) : "";
            if (cleanEnglish != null && !cleanEnglish.isEmpty()) {
                translated.add(Component.literal("§8English: " + cleanEnglish));
            }
        }

        return translated;
    }

    private static List<Component> renderDescriptions(List<Component> originals, List<Component> translated,
                                                      int firstDescription, TranslationCore core, String title,
                                                      Map<Integer, ItemLoreOverrides.Match> edits) {
        LoreLineDictionary lore = core.getLoreLineDictionary();
        List<Component> result = new ArrayList<>(originals.size());
        List<String> unresolved = new ArrayList<>();
        boolean enchantMenu = title != null && (title.contains("Enchant Item") || title.contains("Enchantment Guide"));
        for (int index = 0; index < originals.size(); index++) {
            var edit = edits.get(index);
            if (edit != null) {
                appendEditedLines(result, originals, index, edit, lore);
                index = edit.lastLine();
                continue;
            }
            WrappedDescription wrapped = index >= firstDescription && lore != null
                    ? findWrappedDescription(originals, translated, index, lore, enchantMenu, edits) : null;
            if (wrapped != null) {
                appendWrappedLines(result, originals, index, wrapped, lore);
                index = wrapped.end();
                continue;
            }

            Component original = originals.get(index);
            Component localized = translated.get(index);
            result.add(localized);
            if (title != null && original != null && localized != null) {
                String english = original.getString();
                String display = localized.getString();
                if (english.equals(display) || TranslationCore.hasUntranslatedProse(display)) {
                    unresolved.add(english);
                }
            }
        }

        if (title != null) {
            MenuCapture capture = new MenuCapture(title, unresolved);
            if (!capture.equals(RECENT_MENU_CAPTURE.get())) {
                RECENT_MENU_CAPTURE.set(capture);
                unresolved.forEach(raw -> core.captureMenuTooltip(raw, title));
            }
        }
        return result;
    }

    private static WrappedDescription findWrappedDescription(List<Component> lines, List<Component> localizedLines,
                                                             int start, LoreLineDictionary lore, boolean preferWhole,
                                                             Map<Integer, ItemLoreOverrides.Match> edits) {
        MutableComponent joined = Component.empty();
        WrappedDescription reviewed = null;
        WrappedDescription fallback = null;
        boolean withinSentence = true;
        int length = 0;
        boolean needsTranslation = preferWhole;
        for (int index = start; index < Math.min(lines.size(), start + MAX_DESCRIPTION_LINES); index++) {
            if (edits.containsKey(index)) break;
            Component line = lines.get(index);
            if (line == null) break;
            String english = line.getString();
            if (english.isBlank()) break;
            if (index > start) {
                String previous = lines.get(index - 1).getString().stripTrailing();
                if (".!?:".indexOf(previous.charAt(previous.length() - 1)) >= 0) withinSentence = false;
            }
            length += english.length() + 1;
            if (length > MAX_DESCRIPTION_LENGTH) break;
            if (index > start) joined.append(" ");
            joined.append(line);
            Component localizedLine = localizedLines.get(index);
            if (localizedLine != null) {
                String display = localizedLine.getString();
                needsTranslation |= english.equals(display) || TranslationCore.hasUntranslatedProse(display);
            }
            String original = joined.getString();
            CaptureTemplate.Rendered complete = lore.matchParagraph(original);
            if (complete != null && !complete.text().equals(original)) {
                reviewed = new WrappedDescription(index, joined.copy(), complete);
            } else if (reviewed == null && index > start && withinSentence && needsTranslation) {
                String localized = lore.translate(original);
                if (localized != null && !localized.equals(original)) {
                    fallback = new WrappedDescription(index, joined.copy(), new CaptureTemplate.Rendered(localized));
                }
            }
        }
        return reviewed != null ? reviewed : fallback;
    }

    private static void appendEditedLines(List<Component> result, List<Component> originals, int start,
                                          ItemLoreOverrides.Match edit, LoreLineDictionary lore) {
        if (lore != null) {
            MutableComponent joined = Component.empty();
            for (int index = start; index <= edit.lastLine(); index++) {
                if (index > start) joined.append(" ");
                if (originals.get(index) != null) joined.append(originals.get(index));
            }
            CaptureTemplate.Rendered dynamic = lore.matchReviewed(joined.getString());
            if (dynamic != null) {
                appendWrappedLines(result, originals, start, new WrappedDescription(edit.lastLine(), joined, dynamic), null);
                return;
            }
        }
        if (lore != null && edit.lines().size() == edit.lastLine() - start + 1 && start < edit.lastLine()) {
            for (int index = start; index <= edit.lastLine();) {
                WrappedDescription reviewed = findReviewedExcerpt(originals, index, edit.lastLine(), lore);
                if (reviewed != null) {
                    appendWrappedLines(result, originals, index, reviewed, null);
                    index = reviewed.end() + 1;
                } else {
                    appendEditedLine(result, originals.get(index), edit.lines().get(index - start));
                    index++;
                }
            }
            return;
        }
        for (int index = 0; index < edit.lines().size(); index++) {
            Component original = originals.get(Math.min(start + index, edit.lastLine()));
            appendEditedLine(result, original, edit.lines().get(index));
        }
    }

    private static WrappedDescription findReviewedExcerpt(List<Component> originals, int start,
                                                           int last, LoreLineDictionary lore) {
        MutableComponent joined = Component.empty();
        WrappedDescription match = null;
        int length = 0;
        for (int index = start; index <= last && index - start < MAX_DESCRIPTION_LINES; index++) {
            Component original = originals.get(index);
            if (original == null || original.getString().isBlank()) break;
            length += original.getString().length() + (index == start ? 0 : 1);
            if (length > MAX_DESCRIPTION_LENGTH) break;
            if (index != start) joined.append(" ");
            joined.append(original);
            CaptureTemplate.Rendered translation = lore.matchReviewed(joined.getString());
            if (translation != null) match = new WrappedDescription(index, joined.copy(), translation);
        }
        return match;
    }

    private static void appendEditedLine(List<Component> result, Component original, String translation) {
        result.add(DynamicItemCombiner.translateStyledParagraph(
                original == null ? Component.empty() : original, new CaptureTemplate.Rendered(translation)));
    }

    private static Component replaceReviewedBullets(List<Component> originals, int start, int end,
                                                    Component localized, LoreLineDictionary lore) {
        List<Integer> bulletStarts = new ArrayList<>();
        List<WrappedDescription> overrides = new ArrayList<>();
        for (int index = start; index <= end; index++) {
            if (originals.get(index).getString().stripLeading().startsWith("•")) bulletStarts.add(index);
        }
        for (int position = 0; position < bulletStarts.size(); position++) {
            int first = bulletStarts.get(position);
            int last = position + 1 < bulletStarts.size() ? bulletStarts.get(position + 1) - 1 : end;
            WrappedDescription reviewed = findReviewedExcerpt(originals, first, last, lore);
            overrides.add(reviewed != null && reviewed.end() == last ? reviewed : null);
        }
        if (overrides.stream().allMatch(java.util.Objects::isNull)) return localized;

        // Both the source and the translated paragraph expose the same bullet boundaries.
        // Keep the untouched parts as styled components from the full translation.
        List<MutableComponent> parts = new ArrayList<>();
        parts.add(Component.empty());
        localized.visit((style, text) -> {
            int offset = 0;
            for (int marker = text.indexOf('•'); marker >= 0; marker = text.indexOf('•', offset)) {
                if (marker > offset) parts.getLast().append(Component.literal(text.substring(offset, marker)).setStyle(style));
                parts.add(Component.literal("•").setStyle(style));
                offset = marker + 1;
            }
            if (offset < text.length()) parts.getLast().append(Component.literal(text.substring(offset)).setStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        if (parts.size() != bulletStarts.size() + 1
                || parts.getFirst().getString().isBlank() != (bulletStarts.getFirst() == start)) {
            return localized;
        }

        MutableComponent result = Component.empty().append(parts.getFirst());
        for (int position = 0; position < overrides.size(); position++) {
            WrappedDescription verified = overrides.get(position);
            if (verified == null) {
                result.append(parts.get(position + 1));
            } else {
                result.append(DynamicItemCombiner.translateStyledParagraph(verified.source(), verified.translation()));
                if (position + 1 < overrides.size()) result.append(" ");
            }
        }
        return result;
    }

    private static void appendWrappedLines(List<Component> result, List<Component> originals, int start,
                                           WrappedDescription wrapped, LoreLineDictionary lore) {
        Minecraft client = Minecraft.getInstance();
        StringSplitter splitter = client == null || client.font == null ? HEADLESS_SPLITTER : client.font.getSplitter();
        int width = 1;
        for (int index = start; index <= wrapped.end(); index++) {
            width = Math.max(width, (int) Math.ceil(splitter.stringWidth(originals.get(index))));
        }
        Component localized = DynamicItemCombiner.translateStyledParagraph(wrapped.source(), wrapped.translation());
        if (lore != null && start < wrapped.end()) {
            localized = replaceReviewedBullets(originals, start, wrapped.end(), localized, lore);
        }
        if (start == wrapped.end()) {
            result.add(localized);
            return;
        }
        for (FormattedText line : splitter.splitLines(localized, width, Style.EMPTY)) {
            MutableComponent component = Component.empty();
            line.visit((style, text) -> {
                component.append(Component.literal(text).setStyle(style));
                return Optional.empty();
            }, Style.EMPTY);
            result.add(component);
        }
    }

    /**
     * Translates generic non-ItemStack tooltips via TextTraverser and TranslationCore.
     */
    public static List<Component> translateGenericTooltip(List<Component> components) {
        if (components == null || components.isEmpty() || isAltOrShiftPreviewActive()) {
            return components;
        }

        List<Component> result = new ArrayList<>(components.size());
        for (Component comp : components) {
            if (comp != null) {
                String str = comp.getString();
                if (str.startsWith("§8English: ")) {
                    result.add(comp);
                } else {
                    result.add(TranslationCore.translateComponent(comp, ComponentCategory.TOOLTIPS));
                }
            } else {
                result.add(null);
            }
        }
        if (components.size() > 1 && InterceptionManager.isSkyBlock()) {
            return renderDescriptions(components, result, 0, TranslationCore.getInstance(), null, Map.of());
        }
        return result;
    }
}
