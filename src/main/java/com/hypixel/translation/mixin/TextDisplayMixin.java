package com.hypixel.translation.mixin;

import com.hypixel.translation.config.ComponentCategory;
import com.hypixel.translation.interception.HologramTranslator;
import com.hypixel.translation.interception.InterceptionManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin for Display.TextDisplay to translate floating TextDisplay holograms
 * non-destructively at the display layer while keeping SynchedEntityData intact.
 */
@Mixin(Display.TextDisplay.class)
public class TextDisplayMixin {

    @Inject(method = "getText", at = @At("RETURN"), cancellable = true)
    private void onGetText(CallbackInfoReturnable<Component> cir) {
        if (!InterceptionManager.getInstance().shouldInterceptDisplay(ComponentCategory.ENTITY_TAGS)) {
            return;
        }
        Component original = cir.getReturnValue();
        if (original != null) {
            cir.setReturnValue(HologramTranslator.translate(original, this));
        }
    }
}
