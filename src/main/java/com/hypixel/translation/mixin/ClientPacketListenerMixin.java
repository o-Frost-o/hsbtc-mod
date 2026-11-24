package com.hypixel.translation.mixin;

import com.hypixel.translation.config.ComponentCategory;
import com.hypixel.translation.core.TranslationCore;
import com.hypixel.translation.interception.InterceptionManager;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Mixin for ClientPacketListener to intercept incoming network packets
 * when the corresponding category is configured for PACKET level interception.
 */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    @ModifyVariable(method = "handleSystemChat", at = @At("HEAD"), argsOnly = true)
    private ClientboundSystemChatPacket onHandleSystemChat(ClientboundSystemChatPacket packet) {
        if (packet == null) return null;
        boolean isActionBar = packet.overlay();
        ComponentCategory cat = isActionBar ? ComponentCategory.ACTION_BAR : ComponentCategory.CHAT;
        if (!InterceptionManager.getInstance().shouldInterceptPacket(cat)) return packet;

        Component translated = TranslationCore.translateComponent(packet.content(), cat);
        return new ClientboundSystemChatPacket(translated, isActionBar);
    }

    @ModifyVariable(method = "setActionBarText", at = @At("HEAD"), argsOnly = true)
    private ClientboundSetActionBarTextPacket onSetActionBarText(ClientboundSetActionBarTextPacket packet) {
        if (packet == null) return null;
        if (!InterceptionManager.getInstance().shouldInterceptPacket(ComponentCategory.ACTION_BAR)) return packet;

        Component translated = TranslationCore.translateComponent(packet.text(), ComponentCategory.ACTION_BAR);
        return new ClientboundSetActionBarTextPacket(translated);
    }

    @ModifyVariable(method = "setTitleText", at = @At("HEAD"), argsOnly = true)
    private ClientboundSetTitleTextPacket onSetTitleText(ClientboundSetTitleTextPacket packet) {
        if (packet == null) return null;
        if (!InterceptionManager.getInstance().shouldInterceptPacket(ComponentCategory.TITLE_SUBTITLE)) return packet;

        Component translated = TranslationCore.translateComponent(packet.text(), ComponentCategory.TITLE_SUBTITLE);
        return new ClientboundSetTitleTextPacket(translated);
    }

    @ModifyVariable(method = "setSubtitleText", at = @At("HEAD"), argsOnly = true)
    private ClientboundSetSubtitleTextPacket onSetSubtitleText(ClientboundSetSubtitleTextPacket packet) {
        if (packet == null) return null;
        if (!InterceptionManager.getInstance().shouldInterceptPacket(ComponentCategory.TITLE_SUBTITLE)) return packet;

        Component translated = TranslationCore.translateComponent(packet.text(), ComponentCategory.TITLE_SUBTITLE);
        return new ClientboundSetSubtitleTextPacket(translated);
    }

    @ModifyVariable(method = "handleOpenScreen", at = @At("HEAD"), argsOnly = true)
    private ClientboundOpenScreenPacket onHandleOpenScreen(ClientboundOpenScreenPacket packet) {
        if (packet == null) return null;
        if (!InterceptionManager.getInstance().shouldInterceptPacket(ComponentCategory.CONTAINER_UI)) return packet;

        Component translated = TranslationCore.translateComponent(packet.getTitle(), ComponentCategory.CONTAINER_UI);
        return new ClientboundOpenScreenPacket(packet.getContainerId(), packet.getType(), translated);
    }

    @ModifyVariable(method = "handleTabListCustomisation", at = @At("HEAD"), argsOnly = true)
    private ClientboundTabListPacket onHandleTabListCustomisation(ClientboundTabListPacket packet) {
        if (packet == null) return null;
        if (!InterceptionManager.getInstance().shouldInterceptPacket(ComponentCategory.TAB_LIST)) return packet;

        Component translatedHeader = TranslationCore.translateComponent(packet.header(), ComponentCategory.TAB_LIST);
        Component translatedFooter = TranslationCore.translateComponent(packet.footer(), ComponentCategory.TAB_LIST);
        return new ClientboundTabListPacket(translatedHeader, translatedFooter);
    }
}
