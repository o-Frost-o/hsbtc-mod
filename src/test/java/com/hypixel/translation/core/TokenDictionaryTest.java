package com.hypixel.translation.core;

import com.hypixel.translation.e2e.BaseE2ETest;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
@Tag("component")
public class TokenDictionaryTest extends BaseE2ETest {

    private TokenDictionary dict;

    @BeforeEach
    void initDict() {
        dict = new TokenDictionary();
        dict.registerToken(new TokenRecord("STAT_DAMAGE", "Damage", "傷害", "\ue050", "§c", "stat"));
        dict.registerToken(new TokenRecord("STAT_STRENGTH", "Strength", "力量", "\ue051", "§c", "stat"));
        dict.registerToken(new TokenRecord("STAT_HEALTH", "Health", "生命值", "\ue020", "§c", "stat"));
    }

    @Test
    @DisplayName("Verify token registration and size")
    void testRegistrationAndSize() {
        assertEquals(3, dict.size());
        TokenRecord rec = dict.get("STAT_DAMAGE");
        assertNotNull(rec);
        assertEquals("Damage", rec.en());
        assertEquals("傷害", rec.zh_tw());
        assertEquals("\ue050", rec.glyph());
        assertEquals("§c", rec.color());
    }

    @Test
    @DisplayName("Verify single token resolution")
    void testResolveSingleToken() {
        assertEquals("傷害", dict.resolveToken("STAT_DAMAGE"));
        assertEquals("力量", dict.resolveToken("STAT_STRENGTH"));
    }

    @Test
    @DisplayName("Verify token resolution with glyph and color prefixes")
    void testResolveGlyphAndColor() {
        String template = "{STAT_DAMAGE_COLOR}+{STAT_DAMAGE_GLYPH} {STAT_DAMAGE}";
        assertEquals("§c+\ue050 傷害", dict.resolveString(template));
    }

    @Test
    @DisplayName("Verify uncataloged token retains placeholder")
    void testUncatalogedTokenFallback() {
        assertEquals("{UNKNOWN_STAT}", dict.resolveToken("UNKNOWN_STAT"));
        assertEquals("Prefix {UNKNOWN_STAT} Suffix", dict.resolveString("Prefix {UNKNOWN_STAT} Suffix"));
    }

    @Test
    @DisplayName("Verify circular token dependency does not cause infinite loop")
    void testCircularTokenDependency() {
        dict.registerToken(new TokenRecord("LOOP_A", "A", "{LOOP_B}", "", "", "test"));
        dict.registerToken(new TokenRecord("LOOP_B", "B", "{LOOP_A}", "", "", "test"));

        assertDoesNotThrow(() -> {
            String res = dict.resolveString("{LOOP_A}");
            assertNotNull(res);
        });
    }

    @Test
    @DisplayName("Verify JSON file export and load roundtrip")
    void testJsonExportAndLoadRoundtrip() throws Exception {
        Path jsonFile = tempDir.resolve("test_tokens.json");
        dict.exportToFile(jsonFile);
        assertTrue(Files.exists(jsonFile));

        TokenDictionary reloaded = new TokenDictionary();
        reloaded.loadFromFile(jsonFile);

        assertEquals(3, reloaded.size());
        assertEquals("傷害", reloaded.resolveToken("STAT_DAMAGE"));
        assertEquals("生命值", reloaded.resolveToken("STAT_HEALTH"));
    }

    @Test
    @DisplayName("Verify empty template and null template handling")
    void testEmptyAndNullTemplates() {
        assertNull(dict.resolveString(null));
        assertEquals("", dict.resolveString(""));
        assertEquals("No tokens here", dict.resolveString("No tokens here"));
    }

    @Test
    @DisplayName("Verify word-boundary protection on real bundled tokens and live template resolution")
    void testBundledWordBoundaryAndCrossRef() throws Exception {
        TokenDictionary bundled = new TokenDictionary();
        bundled.loadFromResource("/assets/hsbtr/dictionaries/tokens.json");

        assertTrue(bundled.size() >= 900, "Bundled tokens.json should contain expanded glossary");
        assertEquals("Fairy Soul", bundled.translateText("Fairy Soul"));
        assertEquals("Cleanse", bundled.translateText("Cleanse"));
        assertEquals("Lightning", bundled.translateText("Lightning"));
        assertEquals("Fastest", bundled.translateText("Fastest"));
        assertEquals("要塞", bundled.translateText("Stronghold"));

        // Verify adjacent Minecraft formatting code boundary works without substring corruption
        assertEquals("§7傷害: §c+100", bundled.translateText("§7Damage: §c+100"));

        // Live {TOKEN_ID} resolution inside translateText
        TokenDictionary xref = new TokenDictionary();
        xref.registerToken(new TokenRecord("NPC_JERRY", "Jerry", "傑瑞", "", "§e", "npc"));
        xref.registerToken(new TokenRecord("GREET_JERRY", "Talk to Jerry", "與{NPC_JERRY}對話", "", "§f", "general"));
        assertEquals("與傑瑞對話 now", xref.translateText("Talk to Jerry now"));
    }

    @Test
    @DisplayName("Verify DialogueDictionary translates bundled NPC dialogue and preserves clickable option Style/Events")
    void testDialogueDictionaryAndClickEventPreservation() throws Exception {
        TokenDictionary bundledTerms = new TokenDictionary();
        bundledTerms.loadFromResource("/assets/hsbtr/dictionaries/tokens.json");
        DialogueDictionary dlg = new DialogueDictionary(bundledTerms, TranslationCore.getInstance().getItemDictionary());
        dlg.loadFromResource("/assets/hsbtr/dictionaries/dialogues.json");

        assertTrue(dlg.size() >= 1000, "Bundled dialogues.json should contain Wiki NPC dialogues and options");
        assertTrue(dlg.patternCount() >= 20, "Bundled dialogues.json should contain regex patterns");

        String npcLine = "§e[NPC] §fAdventurer Saul§f: So, have you defeated The Professor yet?";
        String translatedNpc = dlg.translate(npcLine);
        assertTrue(translatedNpc.contains("冒險家索爾") && translatedNpc.contains("教授"),
                "Expected translated NPC dialogue, got: " + translatedNpc);

        // Pattern match for Alcina stews
        String stewLine = "EW! You ate a Bone Broth. Was it worth it?";
        String translatedStew = dlg.translate(stewLine);
        assertTrue(translatedStew.contains("Bone Broth") && translatedStew.contains("值得"),
                "Expected Alcina stew pattern match, got: " + translatedStew);

        // Preserve ClickEvent and HoverEvent across split interactive option component
        Style optStyle = Style.EMPTY
                .withClickEvent(new ClickEvent.RunCommand("/selectoption 1"))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click Here to Teleport")));
        MutableComponent root = Component.empty()
                .append(Component.literal("Select an option: "))
                .append(Component.literal("[Uh, yeah. I'm the best.]").setStyle(optStyle));

        MutableComponent out = textTraverser.translateRecursive(root, dlg::translate);
        assertEquals("請選擇一個選項： [呃，對啊，我最強。]", out.getString());
        ClickEvent ce = out.getSiblings().get(1).getStyle().getClickEvent();
        assertNotNull(ce);
        assertInstanceOf(ClickEvent.RunCommand.class, ce);
        assertEquals("/selectoption 1", ((ClickEvent.RunCommand) ce).command());
    }

    @Test
    void dialogueResolvesOnlyExplicitGlossaryReferencesAndTracksEditsToTheToken() {
        TokenDictionary terms = new TokenDictionary();
        terms.registerToken(new TokenRecord("LOC_THE_END", "The End", "終界", "", "", "location"));
        terms.registerToken(new TokenRecord("DIALOGUE_GUIDE", "Guide", "嚮導", "", "", "dialogue"));
        assertEquals("Guide", terms.translateText("Guide"));
        DialogueDictionary dictionary = new DialogueDictionary(terms, new ItemDictionary());
        dictionary.loadFromJson("""
                {
                  "capture_token_sets": {"npc":{"Guide":"DIALOGUE_GUIDE"}, "region":{"The End":"LOC_THE_END"}},
                  "dialogues": {"[NPC] Guide: Visit The End.":"[NPC] {DIALOGUE_GUIDE}（Guide）：去{LOC_THE_END}。"},
                  "patterns": [
                    {"regex":"^Teleport to (.+)$","zh_tw":"傳送至 $1", "capture_tokens":{"1":"region"}},
                    {"regex":"^You wrote (.+)$","zh_tw":"你寫了 $1"}
                  ]
                }
                """);
        assertEquals("[NPC] 嚮導（Guide）：去終界。", dictionary.translate("[NPC] Guide: Visit The End."));
        assertEquals("[NPC] 嚮導（Guide）: This is The End of the road.",
                dictionary.translate("[NPC] Guide: This is The End of the road."));
        assertEquals("[The End]", dictionary.translate("[The End]"));
        assertEquals("傳送至 終界", dictionary.translate("Teleport to The End"));
        assertEquals("你寫了 The End", dictionary.translate("You wrote The End"));
        assertEquals("[你寫了 {LOC_THE_END}]", dictionary.translate("[You wrote {LOC_THE_END}]"));

        terms.registerToken(new TokenRecord("LOC_THE_END", "The End", "終界島", "", "", "location"));
        assertEquals("[NPC] 嚮導（Guide）：去終界島。", dictionary.translate("[NPC] Guide: Visit The End."));
        assertEquals("傳送至 終界島", dictionary.translate("Teleport to The End"));

        dictionary.loadFromJson("""
                {"patterns":[{"regex":"^Teleport to (.+)$","zh_tw":"前往 $1", "capture_tokens":{"1":"region"}}]}
                """);
        assertEquals("前往 終界島", dictionary.translate("Teleport to The End"));
    }

    @Test
    @DisplayName("Verify UntranslatedCaptureQueue disk persistence roundtrip")
    void testUntranslatedCapturePersistenceRoundtrip() throws Exception {
        UntranslatedCaptureQueue q1 = new UntranslatedCaptureQueue(100);
        q1.capture("§e[NPC] Mysterious Stranger: Greetings traveler", "CHAT");
        q1.capture("Mysterious Stranger: Greetings traveler", "CHAT");
        Path file = tempDir.resolve("untranslated_capture.json");
        q1.flushToJson(file);
        assertTrue(Files.exists(file));

        UntranslatedCaptureQueue q2 = new UntranslatedCaptureQueue(100);
        int restored = q2.loadFromJson(file);
        assertTrue(restored >= 1);
        assertEquals(q1.size(), q2.size());
    }
}
