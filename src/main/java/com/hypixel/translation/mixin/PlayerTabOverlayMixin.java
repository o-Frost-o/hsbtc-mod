package com.hypixel.translation.mixin;

import com.hypixel.translation.config.ComponentCategory;
import com.hypixel.translation.core.TranslationCore;
import com.hypixel.translation.interception.InterceptionManager;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin for PlayerTabOverlay to translate Tab list player display names, headers, and footers.
 */
@Mixin(PlayerTabOverlay.class)
public class PlayerTabOverlayMixin {

    /**
     * Translates Tab player list entries during display formatting.
     */
    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void onGetNameForDisplay(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
        if (!InterceptionManager.getInstance().shouldInterceptDisplay(ComponentCategory.TAB_LIST)) return;
        Component original = cir.getReturnValue();
        if (original != null) {
            cir.setReturnValue(TranslationCore.translateComponent(original, ComponentCategory.TAB_LIST));
        }
    }

    /**
     * Translates Tab list header and footer text lines.
     */
    @ModifyArg(
        method = "extractRenderState",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;split(Lnet/minecraft/network/chat/FormattedText;I)Ljava/util/List;"),
        index = 0
    )
    private FormattedText translateTabHeaderFooter(FormattedText text) {
        if (!InterceptionManager.getInstance().shouldInterceptDisplay(ComponentCategory.TAB_LIST)) return text;
        if (text instanceof Component comp) {
            return TranslationCore.translateComponent(comp, ComponentCategory.TAB_LIST);
        }
        return text;
    }
}
