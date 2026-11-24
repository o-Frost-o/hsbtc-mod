package com.hypixel.translation.interception;

import com.hypixel.translation.config.ComponentCategory;
import com.hypixel.translation.config.ModConfig;
import com.hypixel.translation.core.DialogueDictionary;
import com.hypixel.translation.core.TranslationCore;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Handles display-layer chat translation and Alt-key original English preview for NPC dialogues.
 */
public final class ChatTranslator {
    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("§[0-9a-fk-or]", Pattern.CASE_INSENSITIVE);
    private static final Pattern NPC_PREFIX_PATTERN = Pattern.compile("^\\[(?:NPC|BOSS)\\](?:\\s+.*|$)");
    private static final Pattern BRACKET_OPTION_PATTERN = Pattern.compile("^\\[[^\\]]{1,120}\\]$");
    private static final Pattern NON_DIALOGUE_TAG_PATTERN = Pattern.compile(
            "^\\[(?:VIP\\+{0,1}|MVP\\+{0,2}|YOUTUBE|ADMIN|MOD|HELPER|PIG\\+{3}|OWNER|GUILD|PARTY|CO-OP|OFFICER|HSBTR)\\b.*",
            Pattern.CASE_INSENSITIVE
    );

    private ChatTranslator() {}

    /**
     * Checks whether Left or Right Alt is currently held down.
     * Safely returns false in headless or unit test environments.
     */
    public static boolean isAltDown() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return false;
            Window window = mc.getWindow();
            if (window == null) return false;
            return InputConstants.isKeyDown(window, InputConstants.KEY_LALT)
                    || InputConstants.isKeyDown(window, InputConstants.KEY_RALT);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Returns true when Alt-preview is enabled in ModConfig and an Alt key is pressed.
     */
    public static boolean isChatAltPreviewActive() {
        return ModConfig.getInstance().isEnableAltShiftPreview() && isAltDown();
    }

    /**
     * Determines whether a chat component represents an NPC/Boss dialogue or interactive NPC option.
     */
    public static boolean isNpcDialogue(Component message) {
        if (message == null) return false;
        String raw = message.getString();
        if (raw == null || raw.isEmpty() || raw.contains("[HSBTR]")) return false;

        String clean = COLOR_CODE_PATTERN.matcher(raw).replaceAll("").strip();
        if (clean.isEmpty()) return false;

        if (NPC_PREFIX_PATTERN.matcher(clean).matches()) {
            return true;
        }

        String lower = clean.toLowerCase(Locale.ROOT);
        if (lower.startsWith("select an option:") || lower.startsWith("click an option:")) {
            return true;
        }

        boolean hasNpcCommand = hasNpcClickCommand(message);
        if (hasNpcCommand) {
            return true;
        }

        if (BRACKET_OPTION_PATTERN.matcher(clean).matches()
                && !NON_DIALOGUE_TAG_PATTERN.matcher(clean).matches()) {
            if (hasAnyClickEvent(message)) {
                return true;
            }
            TranslationCore core = TranslationCore.getInstance();
            DialogueDictionary dict = core != null ? core.getDialogueDictionary() : null;
            if (dict != null && !dict.translate(clean).equals(clean)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if any message in the chat history list is an NPC dialogue.
     */
    public static boolean hasNpcDialogue(List<GuiMessage> messages) {
        if (messages == null || messages.isEmpty()) return false;
        for (GuiMessage message : messages) {
            if (message != null && isNpcDialogue(message.content())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Translates a chat component, preserving the original English for NPC dialogues when Alt preview is active.
     */
    public static Component translateChatMessage(Component original) {
        return translateChatMessage(original, isChatAltPreviewActive());
    }

    /**
     * Translates a chat component with an explicit Alt-key preview state.
     */
    public static Component translateChatMessage(Component original, boolean isAltDown) {
        if (original == null) return null;
        String raw = original.getString();
        if (raw != null && raw.contains("[HSBTR]")) {
            return original;
        }
        if (isAltDown && ModConfig.getInstance().isEnableAltShiftPreview() && isNpcDialogue(original)) {
            return original;
        }
        return TranslationCore.translateComponent(original, ComponentCategory.CHAT);
    }

    private static boolean hasNpcClickCommand(Component component) {
        Optional<Boolean> found = component.visit((style, text) -> {
            if (style != null && style.getClickEvent() instanceof ClickEvent.RunCommand runCmd) {
                String cmd = runCmd.command().toLowerCase(Locale.ROOT);
                if (cmd.startsWith("/selectnpcoption") || cmd.startsWith("/npcdialogue") || cmd.contains("npcoption")) {
                    return Optional.of(Boolean.TRUE);
                }
            }
            return Optional.empty();
        }, Style.EMPTY);
        return found.orElse(false);
    }

    private static boolean hasAnyClickEvent(Component component) {
        Optional<Boolean> found = component.visit(
                (style, text) -> (style != null && style.getClickEvent() != null) ? Optional.of(Boolean.TRUE) : Optional.empty(),
                Style.EMPTY
        );
        return found.orElse(false);
    }
}
