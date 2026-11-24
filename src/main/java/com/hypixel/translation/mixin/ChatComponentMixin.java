package com.hypixel.translation.mixin;

import com.hypixel.translation.config.ComponentCategory;
import com.hypixel.translation.core.TranslationCore;
import com.hypixel.translation.interception.ChatTranslator;
import com.hypixel.translation.interception.InterceptionManager;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Mixin for ChatComponent to perform non-destructive display-layer translation
 * of chat messages and NPC dialogues while keeping in-memory chat history and
 * Skyblocker chat listeners untouched in original English, preserving all
 * ClickEvent, HoverEvent, and Style formatting, and toggling NPC dialogues
 * back to original English while Alt is held.
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {

    @Shadow
    @Final
    private List<GuiMessage> allMessages;

    @Shadow
    @Final
    private List<GuiMessage.Line> trimmedMessages;

    @Shadow
    private int chatScrollbarPos;

    @Shadow
    private void refreshTrimmedMessages() {}

    @Shadow
    public int getLinesPerPage() {
        return 0;
    }

    @Unique
    private boolean hsbtr$lastChatAltActive;

    @Unique
    private boolean hsbtr$lastChatDisplayEnabled = true;

    @Unique
    private int hsbtr$lastReloadCount = -1;

    @Inject(
        method = "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V",
        at = @At("HEAD")
    )
    private void hsbtr$syncChatAltPreview(CallbackInfo ci) {
        boolean displayEnabled = InterceptionManager.getInstance().shouldInterceptDisplay(ComponentCategory.CHAT);
        boolean altActive = displayEnabled && ChatTranslator.isChatAltPreviewActive();
        TranslationCore core = TranslationCore.getInstance();
        int reloadCount = core != null ? core.getReloadCount() : 0;

        boolean altChanged = altActive != hsbtr$lastChatAltActive;
        boolean modeChanged = displayEnabled != hsbtr$lastChatDisplayEnabled;
        boolean reloaded = hsbtr$lastReloadCount >= 0 && reloadCount != hsbtr$lastReloadCount;

        hsbtr$lastChatAltActive = altActive;
        hsbtr$lastChatDisplayEnabled = displayEnabled;
        hsbtr$lastReloadCount = reloadCount;

        if (!altChanged && !modeChanged && !reloaded) {
            return;
        }
        if (this.allMessages == null || this.allMessages.isEmpty()) {
            return;
        }
        if (altChanged && !modeChanged && !reloaded && !ChatTranslator.hasNpcDialogue(this.allMessages)) {
            return;
        }

        int savedScroll = this.chatScrollbarPos;
        this.chatScrollbarPos = 0;
        this.refreshTrimmedMessages();
        int maxScroll = Math.max(0, this.trimmedMessages.size() - this.getLinesPerPage());
        this.chatScrollbarPos = Math.min(savedScroll, maxScroll);
    }

    @ModifyVariable(
        method = "addMessageToDisplayQueue",
        at = @At("HEAD"),
        argsOnly = true
    )
    private GuiMessage onAddMessageToDisplayQueue(GuiMessage message) {
        if (message == null || message.content() == null) {
            return message;
        }
        if (!InterceptionManager.getInstance().shouldInterceptDisplay(ComponentCategory.CHAT)) {
            return message;
        }

        Component original = message.content();
        Component translated = ChatTranslator.translateChatMessage(original);
        if (translated == null || translated == original) {
            return message;
        }

        return new GuiMessage(
            message.addedTime(),
            translated,
            message.signature(),
            message.source(),
            message.tag()
        );
    }
}
