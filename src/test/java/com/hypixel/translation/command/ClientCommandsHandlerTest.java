package com.hypixel.translation.command;

import com.hypixel.translation.config.ComponentCategory;
import com.hypixel.translation.config.InterceptionMode;
import com.hypixel.translation.config.ModConfig;
import com.hypixel.translation.core.TranslationCore;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("command")
@Tag("component")
public class ClientCommandsHandlerTest {

    private CommandDispatcher<FabricClientCommandSource> dispatcher;
    private List<Component> feedbackList;
    private FabricClientCommandSource dummySource;

    @BeforeEach
    void setUp() {
        TranslationCore.initialize();
        dispatcher = new CommandDispatcher<>();
        feedbackList = new ArrayList<>();

        dummySource = (FabricClientCommandSource) Proxy.newProxyInstance(
                FabricClientCommandSource.class.getClassLoader(),
                new Class<?>[]{FabricClientCommandSource.class},
                (proxy, method, args) -> {
                    if ("sendFeedback".equals(method.getName())) {
                        feedbackList.add((Component) args[0]);
                        return null;
                    }
                    if ("sendError".equals(method.getName())) {
                        feedbackList.add((Component) args[0]);
                        return null;
                    }
                    return null;
                }
        );

        ClientCommandsHandler.registerCommands(dispatcher);
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    @DisplayName("Verify command tree structure contains all subcommands")
    void testCommandTreeRegistration() {
        CommandNode<FabricClientCommandSource> root = dispatcher.getRoot().getChild("hsbtr");
        assertNotNull(root, "Root command /hsbtr must be registered");

        assertNotNull(root.getChild("ui"), "Subcommand /hsbtr ui must exist");
        assertNotNull(root.getChild("web"), "Subcommand /hsbtr web must exist");
        assertNotNull(root.getChild("reload"), "Subcommand /hsbtr reload must exist");
        assertNotNull(root.getChild("status"), "Subcommand /hsbtr status must exist");
    }

    @Test
    @DisplayName("Verify /hsbtr reload triggers TranslationCore reload and provides feedback")
    void testExecuteReload() throws Exception {
        int initialReloads = TranslationCore.getInstance().getReloadCount();

        int result = dispatcher.execute("hsbtr reload", dummySource);
        assertEquals(1, result);

        assertEquals(initialReloads + 1, TranslationCore.getInstance().getReloadCount());
        assertFalse(feedbackList.isEmpty());
        String text = feedbackList.getLast().getString();
        assertTrue(text.contains("熱重載完成"), "Feedback should indicate reload completion: " + text);
    }

    @Test
    @DisplayName("Verify /hsbtr ui opens browser or generates clickable chat link to GitHub Pages site")
    void testExecuteUi() throws Exception {
        int result = dispatcher.execute("hsbtr ui", dummySource);
        assertEquals(1, result);

        assertFalse(feedbackList.isEmpty());
        Component msg = feedbackList.getLast();
        String fullText = msg.getString();
        assertTrue(fullText.contains(ClientCommandsHandler.WEB_TRANSLATION_URL),
                "Feedback should contain GitHub Pages URL: " + fullText);

        // Verify clickable component is attached
        boolean foundClickable = false;
        for (Component sibling : msg.toFlatList()) {
            if (sibling.getStyle().getClickEvent() instanceof ClickEvent.OpenUrl openUrl) {
                assertEquals(ClientCommandsHandler.WEB_TRANSLATION_URL, openUrl.uri().toString());
                foundClickable = true;
                break;
            }
        }
        assertTrue(foundClickable, "Feedback must contain an OpenUrl ClickEvent");
    }

    @Test
    @DisplayName("Verify /hsbtr web behaves as alias to /hsbtr ui")
    void testExecuteWebAlias() throws Exception {
        int result = dispatcher.execute("hsbtr web", dummySource);
        assertEquals(1, result);

        assertFalse(feedbackList.isEmpty());
        String text = feedbackList.getLast().getString();
        assertTrue(text.contains(ClientCommandsHandler.WEB_TRANSLATION_URL));
    }

    @Test
    @DisplayName("Verify /hsbtr status prints mod information, port, metrics, and interception modes")
    void testExecuteStatus() throws Exception {
        ModConfig.getInstance().setInterceptionMode(ComponentCategory.CHAT, InterceptionMode.PACKET);
        ModConfig.getInstance().setInterceptionMode(ComponentCategory.TOOLTIPS, InterceptionMode.DISABLED);

        int result = dispatcher.execute("hsbtr status", dummySource);
        assertEquals(1, result);

        assertFalse(feedbackList.isEmpty());
        StringBuilder allText = new StringBuilder();
        for (Component c : feedbackList) {
            allText.append(c.getString()).append("\n");
        }
        String output = allText.toString();

        assertTrue(output.contains("Hypixel SkyBlock 繁體中文模組"));
        assertTrue(output.contains("Tokens"));
        assertTrue(output.contains("Items"));
        assertTrue(output.contains("Untranslated"));
        assertTrue(output.contains("PACKET"));
        assertTrue(output.contains("DISABLED"));
        assertTrue(output.contains("DISPLAY"));
    }
}
