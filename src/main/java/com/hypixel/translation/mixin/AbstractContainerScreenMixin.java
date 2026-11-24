package com.hypixel.translation.mixin;

import com.hypixel.translation.config.ComponentCategory;
import com.hypixel.translation.core.TranslationCore;
import com.hypixel.translation.engine.DynamicItemCombiner;
import com.hypixel.translation.interception.InterceptionManager;
import com.hypixel.translation.interception.TooltipTranslator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Mixin for AbstractContainerScreen to translate container menu titles (Chest, SkyBlock Menu, Anvil),
 * translate item tooltips in container slots, and provide Ctrl+C clean English name copy.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

    @Shadow
    @Nullable
    protected Slot hoveredSlot;

    /**
     * Translates container title (ordinal 0 of text call in extractLabels).
     */
    @ModifyArg(
        method = "extractLabels",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V", ordinal = 0),
        index = 1
    )
    private Component translateContainerTitle(Component originalTitle) {
        if (!InterceptionManager.getInstance().shouldInterceptDisplay(ComponentCategory.CONTAINER_UI)) return originalTitle;
        return TranslationCore.translateComponent(originalTitle, ComponentCategory.CONTAINER_UI);
    }

    /**
     * Translates container item tooltip with Alt/Shift preview and optional English subtitle.
     */
    @Inject(
        method = "getTooltipFromContainerItem",
        at = @At("RETURN"),
        cancellable = true
    )
    private void onGetTooltipFromContainerItem(ItemStack stack, CallbackInfoReturnable<List<Component>> cir) {
        if (!InterceptionManager.getInstance().shouldInterceptDisplay(ComponentCategory.TOOLTIPS)) return;
        List<Component> originalTooltip = cir.getReturnValue();
        if (originalTooltip == null || originalTooltip.isEmpty()) return;

        Component title = ((AbstractContainerScreen<?>) (Object) this).getTitle();
        List<Component> translated = TooltipTranslator.translateContainerTooltip(stack, originalTooltip, title.getString());
        TooltipTranslator.markRecentlyTranslatedItemTooltip(translated);
        cir.setReturnValue(translated);
    }

    /**
     * Intercepts Ctrl+C key combination to copy clean English item name to clipboard.
     */
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (event != null && event.isCopy() && this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
            ItemStack stack = this.hoveredSlot.getItem();
            DynamicItemCombiner combiner = DynamicItemCombiner.getInstance();
            if (combiner != null) {
                String cleanName = combiner.getCleanEnglishName(stack);
                if (cleanName != null && !cleanName.isEmpty()) {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc != null && mc.keyboardHandler != null) {
                        mc.keyboardHandler.setClipboard(cleanName);
                        if (mc.gui != null && mc.gui.hud != null) {
                            mc.gui.hud.setOverlayMessage(Component.literal("§a[HSBTR] 複製英文原名: " + cleanName), false);
                        }
                        cir.setReturnValue(true);
                    }
                }
            }
        }
    }
}
