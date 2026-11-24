package com.hypixel.translation.mixin;

import com.hypixel.translation.config.ComponentCategory;
import com.hypixel.translation.core.TranslationCore;
import com.hypixel.translation.interception.InterceptionManager;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Mixin for BossHealthOverlay to translate boss bar text during rendering.
 */
@Mixin(BossHealthOverlay.class)
public class BossHealthOverlayMixin {

    @ModifyArg(
        method = "extractRenderState",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"),
        index = 1
    )
    private Component translateBossBarText(Component original) {
        if (!InterceptionManager.getInstance().shouldInterceptDisplay(ComponentCategory.BOSS_BAR)) return original;
        return TranslationCore.translateComponent(original, ComponentCategory.BOSS_BAR);
    }
}
