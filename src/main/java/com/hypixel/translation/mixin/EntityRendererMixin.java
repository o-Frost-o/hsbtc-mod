package com.hypixel.translation.mixin;

import com.hypixel.translation.config.ComponentCategory;
import com.hypixel.translation.interception.HologramTranslator;
import com.hypixel.translation.interception.InterceptionManager;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin for EntityRenderer to translate entity nametags and floating ArmorStand holograms.
 */
@Mixin(EntityRenderer.class)
public class EntityRendererMixin {

    @Inject(method = "getNameTag", at = @At("RETURN"), cancellable = true)
    private void onGetNameTag(Entity entity, CallbackInfoReturnable<Component> cir) {
        if (!InterceptionManager.getInstance().shouldInterceptDisplay(ComponentCategory.ENTITY_TAGS)) return;
        Component original = cir.getReturnValue();
        if (original != null) {
            cir.setReturnValue(HologramTranslator.translate(original, entity));
        }
    }
}
