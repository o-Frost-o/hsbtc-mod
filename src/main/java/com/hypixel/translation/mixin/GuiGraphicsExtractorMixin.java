package com.hypixel.translation.mixin;

import com.hypixel.translation.config.ComponentCategory;
import com.hypixel.translation.core.TranslationCore;
import com.hypixel.translation.interception.InterceptionManager;
import com.hypixel.translation.interception.TooltipTranslator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Mixin for GuiGraphicsExtractor to translate non-container ItemStack tooltips,
 * single-component UI tooltips, and generic List<Component> tooltips without double-translating
 * container slot tooltips already processed by AbstractContainerScreenMixin.
 */
@Mixin(GuiGraphicsExtractor.class)
public class GuiGraphicsExtractorMixin {

    @Unique
    private static final ThreadLocal<ItemStack> hsbtr$activeItemStack = new ThreadLocal<>();

    @Inject(
        method = "setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V",
        at = @At("HEAD")
    )
    private void hsbtr$onItemTooltipHead(Font font, ItemStack stack, int x, int y, CallbackInfo ci) {
        hsbtr$activeItemStack.set(stack);
    }

    @Inject(
        method = "setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V",
        at = @At("RETURN")
    )
    private void hsbtr$onItemTooltipReturn(Font font, ItemStack stack, int x, int y, CallbackInfo ci) {
        hsbtr$activeItemStack.remove();
    }

    @ModifyVariable(
        method = "setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;IILnet/minecraft/resources/Identifier;)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private List<Component> onSetTooltipForNextFrame(List<Component> components) {
        if (components == null || components.isEmpty()) return components;
        if (!InterceptionManager.getInstance().shouldInterceptDisplay(ComponentCategory.TOOLTIPS)) return components;

        if (TooltipTranslator.consumeRecentlyTranslatedItemTooltip(components)) {
            return components;
        }
        ItemStack itemStack = hsbtr$activeItemStack.get();
        if (itemStack != null) {
            Minecraft client = Minecraft.getInstance();
            var screen = client == null ? null : client.gui.screen();
            if (screen != null && InterceptionManager.isSkyBlock()) {
                return TooltipTranslator.translateContainerTooltip(itemStack, components, screen.getTitle().getString());
            }
            return TooltipTranslator.translateItemTooltip(itemStack, components);
        }
        return TooltipTranslator.translateGenericTooltip(components);
    }

    @ModifyVariable(
        method = "setTooltipForNextFrame(Lnet/minecraft/network/chat/Component;II)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private Component onSetSingleTooltip(Component component) {
        if (component == null || TooltipTranslator.isAltOrShiftPreviewActive()) return component;
        if (!InterceptionManager.getInstance().shouldInterceptDisplay(ComponentCategory.TOOLTIPS)) return component;
        return TranslationCore.translateComponent(component, ComponentCategory.TOOLTIPS);
    }

    @ModifyVariable(
        method = "setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IILnet/minecraft/resources/Identifier;)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private Component onSetSingleStyledTooltip(Component component) {
        if (component == null || TooltipTranslator.isAltOrShiftPreviewActive()) return component;
        if (!InterceptionManager.getInstance().shouldInterceptDisplay(ComponentCategory.TOOLTIPS)) return component;
        return TranslationCore.translateComponent(component, ComponentCategory.TOOLTIPS);
    }

    @ModifyVariable(
        method = "setComponentTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;II)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private List<Component> onSetComponentTooltipForNextFrame(List<Component> components) {
        if (components == null || components.isEmpty()) return components;
        if (!InterceptionManager.getInstance().shouldInterceptDisplay(ComponentCategory.TOOLTIPS)) return components;
        return TooltipTranslator.translateGenericTooltip(components);
    }

    @ModifyVariable(
        method = "setComponentTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/resources/Identifier;)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private List<Component> onSetComponentTooltipForNextFrameStyled(List<Component> components) {
        if (components == null || components.isEmpty()) return components;
        if (!InterceptionManager.getInstance().shouldInterceptDisplay(ComponentCategory.TOOLTIPS)) return components;
        return TooltipTranslator.translateGenericTooltip(components);
    }
}
