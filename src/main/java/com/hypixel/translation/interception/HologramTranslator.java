package com.hypixel.translation.interception;

import com.hypixel.translation.config.ModConfig;
import com.hypixel.translation.core.DialogueDictionary;
import com.hypixel.translation.core.ItemDictionary;
import com.hypixel.translation.core.ItemTranslationRecord;
import com.hypixel.translation.core.MenuDictionary;
import com.hypixel.translation.engine.DynamicItemCombiner;
import com.hypixel.translation.core.TokenDictionary;
import com.hypixel.translation.core.TranslationCore;
import com.hypixel.translation.core.UntranslatedCaptureQueue;
import com.hypixel.translation.engine.TextTraverser;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles translation of floating holograms, NPC nametags, ArmorStand markers,
 * and TextDisplay entities during client entity rendering.
 */
public class HologramTranslator {
    private static final Pattern OUTER_FORMAT = Pattern.compile("^((?:\\s|§[0-9a-fk-orA-FK-OR])*)(.*?)((?:\\s|§[0-9a-fk-orA-FK-OR])*)$");

    /**
     * Translates a nametag or floating hologram Component.
     *
     * @param component The original nametag component.
     * @param entity The entity being rendered (optional context, may be null).
     * @return The translated component.
     */
    public static Component translate(Component component, Object entity) {
        if (component == null) return null;

        TranslationCore core = TranslationCore.getInstance();
        TokenDictionary dict = core != null ? core.getTokenDictionary() : null;
        DialogueDictionary dialogueDict = core != null ? core.getDialogueDictionary() : null;
        ItemDictionary itemDict = core != null ? core.getItemDictionary() : null;
        TextTraverser traverser = TextTraverser.getInstance();
        MenuDictionary menu = core != null ? core.getMenuDictionary() : null;
        String plain = component.getString();
        if (menu != null && !menu.translatePerson(plain).equals(plain)) {
            return DynamicItemCombiner.translateStyledLine(component, menu::translatePerson);
        }
        if (dialogueDict != null && !dialogueDict.translate(plain).equals(plain)) {
            return DynamicItemCombiner.translateStyledLine(component, dialogueDict::translate);
        }

        return traverser.translateRecursive(component, s -> {
            if (s == null || s.trim().isEmpty()) return s;

            String translated = s;
            // Apply composite hologram action phrases first (longest phrases first)
            if (translated.contains("RIGHT CLICK") || translated.contains("LEFT CLICK")
                    || translated.contains("CLICK TO TALK") || translated.contains("CLICK")
                    || translated.contains("TALK") || translated.contains("[NPC]")) {
                translated = translated.replace("RIGHT CLICK", "右鍵點擊")
                                       .replace("LEFT CLICK", "左鍵點擊")
                                       .replace("CLICK TO TALK", "點擊交談")
                                       .replace("CLICK", "點擊")
                                       .replace("TALK", "交談");
            }

            if (dialogueDict != null) {
                String dlg = dialogueDict.translate(translated);
                if (!dlg.equals(translated)) {
                    return dlg;
                }
            }

            if (dict != null && (menu == null || !menu.hasEnglishReference(translated))) {
                translated = dict.translateText(translated);
            }

            // Fallback lookup for bare NPC nametags stored as *_NPC in ItemDictionary
            if (translated.equals(s) && itemDict != null) {
                Matcher om = OUTER_FORMAT.matcher(s);
                if (om.matches() && !om.group(2).isEmpty()) {
                    String coreName = om.group(2).replaceAll("§[0-9a-fk-or]", "").trim();
                    ItemTranslationRecord npcRec = itemDict.get(coreName.toUpperCase(Locale.ROOT).replace(' ', '_') + "_NPC");
                    if (npcRec == null) {
                        ItemTranslationRecord byEn = itemDict.getByEnglishName(coreName);
                        if (byEn != null && ("NPC".equalsIgnoreCase(byEn.category()) || byEn.id().endsWith("_NPC"))) {
                            npcRec = byEn;
                        }
                    }
                    if (npcRec != null && npcRec.name_zh_tw() != null && !npcRec.name_zh_tw().isBlank()
                            && !npcRec.name_zh_tw().equals(npcRec.name_en())) {
                        translated = om.group(1) + npcRec.name_zh_tw() + om.group(3);
                    }
                }
            }

            // Capture untranslated NPC/hologram strings if enabled
            if (translated.equals(s) && ModConfig.getInstance().isUntranslatedCapture()) {
                if (core != null) {
                    UntranslatedCaptureQueue queue = core.getUntranslatedQueue();
                    if (queue != null) {
                        queue.capture(s, "hologram");
                    }
                }
            }

            return translated;
        });
    }
}
