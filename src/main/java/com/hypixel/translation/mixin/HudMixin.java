package com.hypixel.translation.mixin;

import com.hypixel.translation.config.ComponentCategory;
import com.hypixel.translation.core.TranslationCore;
import com.hypixel.translation.interception.InterceptionManager;
import com.hypixel.translation.interception.ScoreboardTranslator;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Mixin for net.minecraft.client.gui.Hud.
 * Intercepts Scoreboard sidebar rendering, ActionBar overlay messages, and Title/Subtitle notifications.
 */
@Mixin(Hud.class)
public class HudMixin {

    /**
     * Translates scoreboard sidebar entries and objective title during rendering.
     * Leaves in-memory Scoreboard intact for Skyblocker.
     */
    @ModifyArg(
        method = "displayScoreboardSidebar",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V"),
        index = 1
    )
    private Component translateScoreboardText(Component original) {
        if (!InterceptionManager.getInstance().shouldInterceptDisplay(ComponentCategory.SCOREBOARD)) return original;
        return ScoreboardTranslator.translate(original);
    }

    /**
     * Intercepts ActionBar overlay messages.
     */
    @ModifyVariable(method = "setOverlayMessage", at = @At("HEAD"), argsOnly = true)
    private Component onSetOverlayMessage(Component component) {
        if (!InterceptionManager.getInstance().shouldInterceptDisplay(ComponentCategory.ACTION_BAR)) return component;
        return TranslationCore.translateComponent(component, ComponentCategory.ACTION_BAR);
    }

    /**
     * Intercepts main Title notifications.
     */
    @ModifyVariable(method = "setTitle", at = @At("HEAD"), argsOnly = true)
    private Component onSetTitle(Component component) {
        if (!InterceptionManager.getInstance().shouldInterceptDisplay(ComponentCategory.TITLE_SUBTITLE)) return component;
        return TranslationCore.translateComponent(component, ComponentCategory.TITLE_SUBTITLE);
    }

    /**
     * Intercepts Subtitle notifications.
     */
    @ModifyVariable(method = "setSubtitle", at = @At("HEAD"), argsOnly = true)
    private Component onSetSubtitle(Component component) {
        if (!InterceptionManager.getInstance().shouldInterceptDisplay(ComponentCategory.TITLE_SUBTITLE)) return component;
        return TranslationCore.translateComponent(component, ComponentCategory.TITLE_SUBTITLE);
    }
}
