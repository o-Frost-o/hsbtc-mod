package com.hypixel.translation.e2e;

import com.google.gson.JsonObject;
import com.hypixel.translation.command.ClientCommandsHandler;
import com.hypixel.translation.config.ComponentCategory;
import com.hypixel.translation.config.InterceptionMode;
import com.hypixel.translation.config.ModConfig;
import com.hypixel.translation.core.*;
import com.hypixel.translation.engine.*;
import com.hypixel.translation.interception.*;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import net.minecraft.network.chat.contents.PlainTextContents;
import org.junit.jupiter.api.*;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tier 5 Adversarial & Hardening Test Suite (Interception, Chat Alt Preview, Config, Commands).
 * Covers deep white-box edge cases, boundaries, and concurrency.
 */
@Tag("tier5")
@Tag("adversarial")
public class Tier5InterceptionWebAdversarialTest extends BaseE2ETest {

    private CommandDispatcher<FabricClientCommandSource> dispatcher;
    private List<Component> feedbackList;
    private FabricClientCommandSource dummySource;

    @BeforeEach
    public void setUpTier5() {
        TranslationCore.initialize();
        dispatcher = new CommandDispatcher<>();
        feedbackList = new CopyOnWriteArrayList<>();

        dummySource = (FabricClientCommandSource) Proxy.newProxyInstance(
                FabricClientCommandSource.class.getClassLoader(),
                new Class<?>[]{FabricClientCommandSource.class},
                (proxy, method, args) -> {
                    if ("sendFeedback".equals(method.getName()) || "sendError".equals(method.getName())) {
                        feedbackList.add((Component) args[0]);
                        return null;
                    }
                    return null;
                }
        );

        ClientCommandsHandler.registerCommands(dispatcher);
    }

    // =========================================================================
    // Category 1: Interception & Deep Text Traversal Adversarial Probes
    // =========================================================================

    @Test
    @DisplayName("T5-INT-01: Component deep recursion stack integrity (Depth 50, 100, 200)")
    void testComponentDeepRecursionStackIntegrity() {
        int[] depths = {50, 100, 200};

        for (int depth : depths) {
            MutableComponent root = Component.literal("Root");
            MutableComponent current = root;
            for (int i = 1; i <= depth; i++) {
                MutableComponent child = Component.literal(" Node_" + i);
                current.append(child);
                current = child;
            }

            MutableComponent translated = textTraverser.translateRecursive(root, s -> {
                if (s.contains("Node")) return s.replace("Node", "節點");
                return s;
            });

            assertNotNull(translated);
            assertTrue(translated.getString().contains("節點_1"));
            assertTrue(translated.getString().contains("節點_" + depth));
        }
    }

    @Test
    @DisplayName("T5-INT-02: Broad component sibling tree scalability (500 siblings)")
    void testComponentBroadSiblingsScalability() {
        MutableComponent root = Component.literal("Stats: ");
        for (int i = 0; i < 500; i++) {
            root.append(Component.literal("Damage " + i + ", "));
        }

        MutableComponent translated = textTraverser.translateRecursive(root, s -> tokenDictionary.translateText(s));
        assertNotNull(translated);
        assertTrue(translated.getString().contains("傷害 0, "));
        assertTrue(translated.getString().contains("傷害 499, "));
        assertEquals(500, translated.getSiblings().size());
    }

    @Test
    @DisplayName("T5-INT-03: Interactive ClickEvent and HoverEvent 100% verbatim preservation without mutation")
    void testInteractiveEventImmutabilityAndPreservation() {
        Style origStyle = Style.EMPTY
                .withColor(ChatFormatting.GOLD)
                .withBold(true)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.RunCommand("/warp dungeon_hub"))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Damage: +260")));

        MutableComponent origComp = Component.literal("Click Here to Teleport").setStyle(origStyle);

        MutableComponent translated = textTraverser.translateRecursive(origComp, s -> tokenDictionary.translateText(s));

        // 1. Assert translated component
        assertNotNull(translated);
        Style transStyle = translated.getStyle();
        assertNotNull(transStyle);
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GOLD), transStyle.getColor());
        assertEquals(Boolean.TRUE, transStyle.isBold());
        assertEquals(Boolean.TRUE, transStyle.isUnderlined());

        // ClickEvent verbatim preserved
        assertNotNull(transStyle.getClickEvent());
        assertInstanceOf(ClickEvent.RunCommand.class, transStyle.getClickEvent());
        assertEquals("/warp dungeon_hub", ((ClickEvent.RunCommand) transStyle.getClickEvent()).command());

        // HoverEvent translated recursively
        assertNotNull(transStyle.getHoverEvent());
        assertTrue(transStyle.getHoverEvent() instanceof HoverEvent.ShowText);
        HoverEvent.ShowText showText = (HoverEvent.ShowText) transStyle.getHoverEvent();
        assertTrue(showText.value().getString().contains("傷害: +260"));

        // 2. Immutability verification: Original component and style MUST NOT be mutated
        assertEquals("Damage: +260", ((HoverEvent.ShowText) origComp.getStyle().getHoverEvent()).value().getString());
        assertEquals("/warp dungeon_hub", ((ClickEvent.RunCommand) origComp.getStyle().getClickEvent()).command());
    }

    @Test
    @DisplayName("T5-INT-04: Explicit Boolean.FALSE style attributes preservation")
    void testStyleNegativeFormattingPreservation() {
        Style origStyle = Style.EMPTY
                .withColor(ChatFormatting.RED)
                .withBold(true)
                .withItalic(false)
                .withStrikethrough(false);

        MutableComponent origComp = Component.literal("Unformatted").setStyle(origStyle);
        MutableComponent translated = textTraverser.translateRecursive(origComp, s -> s);

        Style transStyle = translated.getStyle();
        assertEquals(Boolean.TRUE, transStyle.isBold());
        assertEquals(Boolean.FALSE, transStyle.isItalic(), "Explicit Boolean.FALSE italic must be preserved");
        assertEquals(Boolean.FALSE, transStyle.isStrikethrough(), "Explicit Boolean.FALSE strikethrough must be preserved");
    }

    @Test
    @DisplayName("T5-INT-05: Empty and null component traversal safety")
    void testEmptyAndNullComponentTraversalSafety() {
        // Null component
        MutableComponent nullResult = textTraverser.translateRecursive(null, s -> s);
        assertNotNull(nullResult);
        assertEquals("", nullResult.getString());

        // Empty literal
        MutableComponent emptyResult = textTraverser.translateRecursive(Component.empty(), s -> s);
        assertNotNull(emptyResult);
        assertEquals("", emptyResult.getString());

        // Null string translator
        MutableComponent noTrans = textTraverser.translateRecursive(Component.literal("Raw Text"), null);
        assertNotNull(noTrans);
        assertEquals("Raw Text", noTrans.getString());
    }

    @Test
    @DisplayName("T5-INT-06: TooltipTranslator Alt/Shift toggle and gray English subtitle")
    void testTooltipTranslatorAltShiftToggleFidelity() {
        List<Component> englishTooltip = List.of(
                Component.literal("Hyperion"),
                Component.literal("Damage: +260")
        );

        ModConfig config = ModConfig.getInstance();
        config.setEnableAltShiftPreview(true);
        config.setShowEnglishSubtitle(false);

        // When Alt/Shift is DOWN -> returns original unmodified English copy
        List<Component> previewResult = TooltipTranslator.translateItemTooltip(null, englishTooltip, true);
        assertEquals(2, previewResult.size());
        assertEquals("Hyperion", previewResult.get(0).getString());
        assertEquals("Damage: +260", previewResult.get(1).getString());

        // When Alt/Shift is UP -> translates
        List<Component> translatedResult = TooltipTranslator.translateItemTooltip(null, englishTooltip, false);
        assertEquals(2, translatedResult.size());
        assertTrue(translatedResult.get(0).getString().contains("亥伯龍") || translatedResult.get(0).getString().contains("Hyperion"));
        assertTrue(translatedResult.get(1).getString().contains("傷害：+260") || translatedResult.get(1).getString().contains("傷害: +260"));

        // When English subtitle is ENABLED
        config.setShowEnglishSubtitle(true);
        // stack is null, so subtitle is omitted safely without NPE
        List<Component> noStackSubtitle = TooltipTranslator.translateItemTooltip(null, englishTooltip, false);
        assertEquals(2, noStackSubtitle.size());
    }

    @Test
    @DisplayName("T5-INT-07: TooltipTranslator generic tooltip null element preservation")
    void testTooltipTranslatorGenericNullElements() {
        List<Component> input = Arrays.asList(
                Component.literal("Damage"),
                null,
                Component.literal("Strength")
        );

        List<Component> result = TooltipTranslator.translateGenericTooltip(input);
        assertEquals(3, result.size());
        assertEquals("傷害", result.get(0).getString());
        assertNull(result.get(1), "Null element in tooltip list must remain null");
        assertEquals("力量", result.get(2).getString());
    }

    @Test
    @DisplayName("T5-INT-08: ScoreboardTranslator untranslated capture trigger")
    void testScoreboardTranslatorUntranslatedCaptureTrigger() {
        ModConfig.getInstance().setUntranslatedCapture(true);
        UntranslatedCaptureQueue queue = TranslationCore.getInstance().getUntranslatedQueue();
        int initialQueueSize = queue.size();

        // 1. Known token -> translated, not captured
        Component knownComp = Component.literal("Purse: 1,000");
        Component transKnown = ScoreboardTranslator.translate(knownComp);
        assertTrue(transKnown.getString().contains("錢包: 1,000"));
        assertEquals(initialQueueSize, queue.size());

        // 2. Unknown string -> captured
        Component unknownComp = Component.literal("Mysterious Unknown Objective Line 7819");
        ScoreboardTranslator.translate(unknownComp);
        assertTrue(queue.size() > initialQueueSize);
    }

    @Test
    @DisplayName("T5-INT-09: HologramTranslator tags and null context safety")
    void testHologramTranslatorTagsAndNullSafety() {
        // Null component
        assertNull(HologramTranslator.translate(null, null));

        // NPC tag
        Component npc = HologramTranslator.translate(Component.literal("[NPC] Jerry"), null);
        assertNotNull(npc);
        assertTrue(npc.getString().contains("傑瑞"));

        // CLICK TO TALK composite translation
        Component click = HologramTranslator.translate(Component.literal("CLICK TO TALK"), null);
        assertNotNull(click);
        assertTrue(click.getString().contains("點擊交談"),
                "HologramTranslator must translate 'CLICK TO TALK' to '點擊交談', got: " + click.getString());

        // RIGHT CLICK composite translation (longest phrase before single word CLICK)
        Component rightClick = HologramTranslator.translate(Component.literal("RIGHT CLICK TO OPEN"), null);
        assertNotNull(rightClick);
        assertTrue(rightClick.getString().contains("右鍵點擊"),
                "HologramTranslator must translate 'RIGHT CLICK' before 'CLICK', got: " + rightClick.getString());
    }

    // =========================================================================
    // Category 6: 3-State Category Toggles & ModConfig Thread Safety
    // =========================================================================

    @Test
    @DisplayName("T5-CFG-01: All 9 ComponentCategory 3-State toggle matrix (DISPLAY, PACKET, DISABLED)")
    void testAllNineComponentCategoriesThreeStateMatrix() {
        com.hypixel.translation.interception.InterceptionManager mgr = com.hypixel.translation.interception.InterceptionManager.getInstance();

        for (ComponentCategory cat : ComponentCategory.values()) {
            // 1. DISPLAY mode
            mgr.setMode(cat, InterceptionMode.DISPLAY);
            assertTrue(mgr.shouldInterceptDisplay(cat), cat + " should intercept display in DISPLAY mode");
            assertFalse(mgr.shouldInterceptPacket(cat), cat + " should NOT intercept packet in DISPLAY mode");

            // 2. PACKET mode
            mgr.setMode(cat, InterceptionMode.PACKET);
            assertFalse(mgr.shouldInterceptDisplay(cat), cat + " should NOT intercept display in PACKET mode");
            assertTrue(mgr.shouldInterceptPacket(cat), cat + " should intercept packet in PACKET mode");

            // 3. DISABLED mode
            mgr.setMode(cat, InterceptionMode.DISABLED);
            assertFalse(mgr.shouldInterceptDisplay(cat), cat + " should NOT intercept display in DISABLED mode");
            assertFalse(mgr.shouldInterceptPacket(cat), cat + " should NOT intercept packet in DISABLED mode");
        }
    }

    @Test
    @DisplayName("T5-CFG-02: Category isolation ensures mutating one category causes no cross-talk")
    void testCategoryIsolationNoCrossTalk() {
        com.hypixel.translation.interception.InterceptionManager mgr = com.hypixel.translation.interception.InterceptionManager.getInstance();

        // Reset all to DISPLAY
        for (ComponentCategory cat : ComponentCategory.values()) {
            mgr.setMode(cat, InterceptionMode.DISPLAY);
        }

        // Mutate TOOLTIPS to PACKET
        mgr.setMode(ComponentCategory.TOOLTIPS, InterceptionMode.PACKET);
        assertEquals(InterceptionMode.PACKET, mgr.getMode(ComponentCategory.TOOLTIPS));

        // Verify other 8 categories remain DISPLAY
        for (ComponentCategory cat : ComponentCategory.values()) {
            if (cat != ComponentCategory.TOOLTIPS) {
                assertEquals(InterceptionMode.DISPLAY, mgr.getMode(cat), cat + " must remain unaffected");
            }
        }

        // Mutate CHAT to DISABLED
        mgr.setMode(ComponentCategory.CHAT, InterceptionMode.DISABLED);
        assertEquals(InterceptionMode.DISABLED, mgr.getMode(ComponentCategory.CHAT));
        assertEquals(InterceptionMode.PACKET, mgr.getMode(ComponentCategory.TOOLTIPS));
        assertEquals(InterceptionMode.DISPLAY, mgr.getMode(ComponentCategory.SCOREBOARD));
    }

    @Test
    @DisplayName("T5-CFG-03: ModConfig recovery from corrupted JSON and invalid enum names")
    void testModConfigCorruptedJsonRecovery() throws Exception {
        Path testCfg = tempDir.resolve("corrupt_test_cfg.json");

        // 1. Invalid enum value in categories
        String corruptJson = """
        {
          "enableAltShiftPreview": true,
          "categories": {
            "TOOLTIPS": "SUPER_PACKET_TURBO",
            "CHAT": "DISABLED"
          }
        }
        """;
        Files.writeString(testCfg, corruptJson, StandardCharsets.UTF_8);

        ModConfig config = new ModConfig();
        config.loadFrom(testCfg);

        // TOOLTIPS fell back to default DISPLAY safely
        assertEquals(InterceptionMode.DISPLAY, config.getInterceptionMode(ComponentCategory.TOOLTIPS));
        // CHAT loaded DISABLED cleanly
        assertEquals(InterceptionMode.DISABLED, config.getInterceptionMode(ComponentCategory.CHAT));

        // 2. Syntax-corrupted JSON
        Files.writeString(testCfg, "{ incomplete json ...", StandardCharsets.UTF_8);
        assertDoesNotThrow(() -> config.loadFrom(testCfg), "Loading corrupted JSON must not throw unhandled exception");
    }

    @Test
    @DisplayName("T5-CFG-04: ModConfig concurrent thread stress (40 threads x 100 operations)")
    void testModConfigConcurrentThreadStress() throws Exception {
        ModConfig config = ModConfig.getInstance();
        int numThreads = 40;
        int opsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger errors = new AtomicInteger(0);

        ComponentCategory[] categories = ComponentCategory.values();
        InterceptionMode[] modes = InterceptionMode.values();

        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < opsPerThread; j++) {
                        ComponentCategory cat = categories[(threadId + j) % categories.length];
                        InterceptionMode mode = modes[(threadId + j) % modes.length];

                        config.setInterceptionMode(cat, mode);
                        InterceptionMode read = config.getInterceptionMode(cat);
                        assertNotNull(read);

                        config.setEnableAltShiftPreview(j % 2 == 0);
                        boolean preview = config.isEnableAltShiftPreview();
                        assertEquals(preview, config.isEnableAltShiftPreview());

                        config.setShowEnglishSubtitle(j % 3 == 0);
                        boolean sub = config.isShowEnglishSubtitle();
                        assertEquals(sub, config.isShowEnglishSubtitle());
                    }
                } catch (Throwable t) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed);
        assertEquals(0, errors.get(), "Concurrent access to ModConfig produced errors");
    }

    // =========================================================================
    // Category 7: ClientCommandsHandler & System Integration
    // =========================================================================

    @Test
    @DisplayName("T5-CMD-01: Brigadier command tree registration fidelity")
    void testBrigadierTreeRegistrationFidelity() {
        CommandNode<FabricClientCommandSource> root = dispatcher.getRoot().getChild("hsbtr");
        assertNotNull(root, "Root command /hsbtr must be registered");

        assertNotNull(root.getChild("ui"), "Subcommand /hsbtr ui must exist");
        assertNotNull(root.getChild("web"), "Subcommand /hsbtr web must exist");
        assertNotNull(root.getChild("reload"), "Subcommand /hsbtr reload must exist");
        assertNotNull(root.getChild("status"), "Subcommand /hsbtr status must exist");
    }

    @Test
    @DisplayName("T5-CMD-02: /hsbtr reload feedback and atomic counter increment")
    void testCommandReloadFeedbackAndCount() throws Exception {
        TranslationCore.initialize();
        int initialCount = TranslationCore.getInstance().getReloadCount();

        int result = dispatcher.execute("hsbtr reload", dummySource);
        assertEquals(1, result);

        assertEquals(initialCount + 1, TranslationCore.getInstance().getReloadCount());
        assertFalse(feedbackList.isEmpty());
        String feedback = feedbackList.getLast().getString();
        assertTrue(feedback.contains("熱重載完成"));
        assertTrue(feedback.contains(String.valueOf(initialCount + 1)));
    }

    @Test
    @DisplayName("T5-CMD-03: /hsbtr status comprehensive feedback verification")
    void testCommandStatusFeedbackComprehensiveDetails() throws Exception {
        int result = dispatcher.execute("hsbtr status", dummySource);
        assertEquals(1, result);

        assertFalse(feedbackList.isEmpty());
        String fullOutput = feedbackList.stream().map(Component::getString).reduce("", (a, b) -> a + "\n" + b);

        assertTrue(fullOutput.contains("Hypixel SkyBlock 繁體中文模組"));
        assertTrue(fullOutput.contains("1.0.0"));
        assertTrue(fullOutput.contains("詞彙對照表條數"));
        assertTrue(fullOutput.contains("道具庫項目數"));
        assertTrue(fullOutput.contains("未翻譯擷取佇列"));
        assertTrue(fullOutput.contains("元件文字攔截模式"));
        assertTrue(fullOutput.contains("TOOLTIPS"));
        assertTrue(fullOutput.contains("SCOREBOARD"));
    }

    @Test
    @DisplayName("T5-CMD-04: /hsbtr ui headless safety and standalone GitHub Pages clickable link")
    void testCommandOpenUiHeadlessSafety() throws Exception {
        int result = dispatcher.execute("hsbtr ui", dummySource);
        assertEquals(1, result);

        assertFalse(feedbackList.isEmpty());
        Component msg = feedbackList.getLast();
        String text = msg.getString();
        assertTrue(text.contains(ClientCommandsHandler.WEB_TRANSLATION_URL),
                "Feedback must provide standalone web URL: " + text);

        // Verify clickable style
        List<Component> siblings = msg.getSiblings();
        boolean hasClickableLink = false;
        for (Component sib : siblings) {
            Style s = sib.getStyle();
            if (s != null && s.getClickEvent() != null) {
                if (s.getClickEvent() instanceof ClickEvent.OpenUrl openUrl) {
                    if (openUrl.uri().toString().equals(ClientCommandsHandler.WEB_TRANSLATION_URL)) {
                        hasClickableLink = true;
                    }
                }
            }
        }
        assertTrue(hasClickableLink, "Chat feedback must contain clickable OpenUrl ClickEvent");
    }

    @Test
    @DisplayName("T5-CMD-05: Concurrent /hsbtr reload execution thread safety")
    void testConcurrentReloadCommandThreadSafety() throws Exception {
        TranslationCore.initialize();
        int initialCount = TranslationCore.getInstance().getReloadCount();

        int numWorkers = 24;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch latch = new CountDownLatch(numWorkers);
        java.util.concurrent.atomic.AtomicInteger cmdSuccess = new java.util.concurrent.atomic.AtomicInteger(0);

        for (int i = 0; i < numWorkers; i++) {
            executor.submit(() -> {
                try {
                    int res = dispatcher.execute("hsbtr reload", dummySource);
                    if (res == 1) {
                        cmdSuccess.incrementAndGet();
                    }
                } catch (Throwable ignored) {
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed);
        assertEquals(numWorkers, cmdSuccess.get());
        assertEquals(initialCount + numWorkers, TranslationCore.getInstance().getReloadCount());
    }

    @Test
    @DisplayName("T5-CHAT-01: Adversarial NPC dialogue vs rank tag Alt preview classification")
    void testChatAltPreviewAdversarialTags() {
        TranslationCore.initialize();
        ModConfig config = ModConfig.getInstance();
        boolean prevAlt = config.isEnableAltShiftPreview();
        try {
            config.setEnableAltShiftPreview(true);

            Component npcLine = Component.literal("§e[NPC] §bLibrarian§f: §rHello! Click me to open the Enchanting Table!");
            assertTrue(ChatTranslator.isNpcDialogue(npcLine));
            Component npcAlt = ChatTranslator.translateChatMessage(npcLine, true);
            assertEquals(npcLine.getString(), npcAlt.getString());

            Component bossLine = Component.literal("§c[BOSS] The Watcher§f: §rThings look a bit different down here.");
            assertTrue(ChatTranslator.isNpcDialogue(bossLine));
            assertEquals(bossLine.getString(), ChatTranslator.translateChatMessage(bossLine, true).getString());

            Component npcOption = Component.literal("[Yes]")
                    .withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/selectnpcoption 1")));
            assertTrue(ChatTranslator.isNpcDialogue(npcOption));
            assertEquals("[Yes]", ChatTranslator.translateChatMessage(npcOption, true).getString());

            Component rankMessage = Component.literal("[MVP+] PlayerName: Hello SkyBlock");
            assertFalse(ChatTranslator.isNpcDialogue(rankMessage), "Player rank tag must not trigger NPC dialogue Alt preview");

            Component modNotice = Component.literal("§b[HSBTR]§r 已重新載入所有翻譯字典。");
            assertFalse(ChatTranslator.isNpcDialogue(modNotice));

            config.setEnableAltShiftPreview(false);
            Component npcAltDisabled = ChatTranslator.translateChatMessage(npcLine, true);
            assertNotNull(npcAltDisabled);
        } finally {
            config.setEnableAltShiftPreview(prevAlt);
        }
    }
}
