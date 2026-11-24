package com.hypixel.translation.mixin;

import com.hypixel.translation.config.ComponentCategory;
import com.hypixel.translation.core.TranslationCore;
import com.hypixel.translation.interception.InterceptionManager;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Translates displayed book pages, including the clickable text measurements. */
@Mixin(BookViewScreen.class)
public class BookViewScreenMixin {
    @Redirect(
        method = "visitText",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/BookViewScreen$BookAccess;getPage(I)Lnet/minecraft/network/chat/Component;")
    )
    private Component translateBookPage(BookViewScreen.BookAccess access, int index) {
        Component page = access.getPage(index);
        if (!InterceptionManager.getInstance().shouldInterceptDisplay(ComponentCategory.CONTAINER_UI)
                || !InterceptionManager.isSkyBlock()) return page;

        return TranslationCore.translateBookPage(page);
    }
}
