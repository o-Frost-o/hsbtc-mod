package com.hypixel.translation.pipeline;

import com.hypixel.translation.core.ItemTranslationRecord;
import com.hypixel.translation.core.TokenRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class SchemaNormalizerTest {

    private final SchemaNormalizer normalizer = new SchemaNormalizer();

    @Test
    @DisplayName("Stat normalization handles uppercase, lowercase, abbreviations and British spellings")
    public void testStatNormalization() {
        assertEquals("DEFENSE", SchemaNormalizer.normalizeStat("DEFENSE"));
        assertEquals("DEFENSE", SchemaNormalizer.normalizeStat("defense"));
        assertEquals("DEFENSE", SchemaNormalizer.normalizeStat("DEFENCE"));
        assertEquals("DEFENSE", SchemaNormalizer.normalizeStat("defence"));

        assertEquals("DAMAGE", SchemaNormalizer.normalizeStat("DAMAGE"));
        assertEquals("DAMAGE", SchemaNormalizer.normalizeStat("damage"));
        assertEquals("DAMAGE", SchemaNormalizer.normalizeStat("DMG"));
        assertEquals("DAMAGE", SchemaNormalizer.normalizeStat("dmg"));

        assertEquals("STRENGTH", SchemaNormalizer.normalizeStat("strength"));
        assertEquals("STRENGTH", SchemaNormalizer.normalizeStat("STR"));

        assertEquals("INTELLIGENCE", SchemaNormalizer.normalizeStat("intelligence"));
        assertEquals("INTELLIGENCE", SchemaNormalizer.normalizeStat("INT"));
        assertEquals("INTELLIGENCE", SchemaNormalizer.normalizeStat("mana"));

        assertEquals("CRIT_CHANCE", SchemaNormalizer.normalizeStat("critical_chance"));
        assertEquals("CRIT_CHANCE", SchemaNormalizer.normalizeStat("crit_chance"));
        assertEquals("CRIT_CHANCE", SchemaNormalizer.normalizeStat("CC"));

        assertEquals("CRIT_DAMAGE", SchemaNormalizer.normalizeStat("critical_damage"));
        assertEquals("CRIT_DAMAGE", SchemaNormalizer.normalizeStat("crit_damage"));
        assertEquals("CRIT_DAMAGE", SchemaNormalizer.normalizeStat("CD"));

        assertEquals("TRUE_DEFENSE", SchemaNormalizer.normalizeStat("true_defense"));
        assertEquals("TRUE_DEFENSE", SchemaNormalizer.normalizeStat("true_defence"));
        assertEquals("TRUE_DEFENSE", SchemaNormalizer.normalizeStat("TD"));

        assertEquals("MAGIC_FIND", SchemaNormalizer.normalizeStat("magic_find"));
        assertEquals("MAGIC_FIND", SchemaNormalizer.normalizeStat("MF"));
    }

    @Test
    @DisplayName("Rarity and category normalization maps variants to standard uppercase enums")
    public void testRarityAndCategoryNormalization() {
        assertEquals("LEGENDARY", SchemaNormalizer.normalizeRarity("legendary"));
        assertEquals("VERY_SPECIAL", SchemaNormalizer.normalizeRarity("very special"));
        assertEquals("COMMON", SchemaNormalizer.normalizeRarity(null));

        assertEquals("SWORD", SchemaNormalizer.normalizeCategory("sword"));
        assertEquals("BOW", SchemaNormalizer.normalizeCategory("bow"));
        assertEquals("CHESTPLATE", SchemaNormalizer.normalizeCategory("chestplate"));
        assertEquals("MISC", SchemaNormalizer.normalizeCategory(""));
    }

    @Test
    @DisplayName("Lore template extraction correctly parameterizes stats, costs, and cooldowns")
    public void testLoreTemplateExtraction() {
        List<String> rawLore = List.of(
            "§7Damage: §c+260",
            "§7Strength: §c+150",
            "§7Defense: §a+100",
            "§8Mana Cost: §350",
            "§8Cooldown: §a3s",
            "§eRight-click to use ability!"
        );

        List<String> templated = normalizer.extractLoreTemplate(rawLore);
        assertNotNull(templated);
        assertEquals(6, templated.size());

        assertEquals("§7{STAT_DAMAGE}: §c+{0}", templated.get(0));
        assertEquals("§7{STAT_STRENGTH}: §c+{0}", templated.get(1));
        assertEquals("§7{STAT_DEFENSE}: §a+{0}", templated.get(2));
        assertEquals("§8Mana Cost: §3{0}", templated.get(3));
        assertEquals("§8Cooldown: §a{0}s", templated.get(4));
        assertEquals("§eRight-click to use ability!", templated.get(5));
    }

    @Test
    @DisplayName("Base token taxonomy covers stats, rarities, reforges, skills, collections, and mayors")
    public void testBaseTokenTaxonomy() {
        Map<String, TokenRecord> tokens = DataPipelineRunner.buildBaseTokenTaxonomy();
        assertNotNull(tokens);
        assertFalse(tokens.isEmpty());

        // Check key stats
        assertTrue(tokens.containsKey("STAT_DAMAGE"));
        assertEquals("傷害", tokens.get("STAT_DAMAGE").zh_tw());
        assertTrue(tokens.containsKey("STAT_DEFENSE"));
        assertEquals("防禦力", tokens.get("STAT_DEFENSE").zh_tw());
        assertTrue(tokens.containsKey("STAT_INTELLIGENCE"));
        assertEquals("智力", tokens.get("STAT_INTELLIGENCE").zh_tw());

        // Check rarities
        assertTrue(tokens.containsKey("RARITY_LEGENDARY"));
        assertEquals("傳奇", tokens.get("RARITY_LEGENDARY").zh_tw());
        assertTrue(tokens.containsKey("RARITY_MYTHIC"));

        // Check reforges
        assertTrue(tokens.containsKey("REFORGE_HEROIC"));
        assertTrue(tokens.containsKey("REFORGE_ANCIENT"));
        assertTrue(tokens.containsKey("REFORGE_WITHERED"));
        assertTrue(tokens.containsKey("REFORGE_WARPED"));

        // Check skills
        assertTrue(tokens.containsKey("SKILL_COMBAT"));
        assertTrue(tokens.containsKey("SKILL_MINING"));

        // Check collections
        assertTrue(tokens.containsKey("COLLECTION_FARMING"));

        // Check mayors
        assertTrue(tokens.containsKey("MAYOR_MARINA"));
        assertTrue(tokens.containsKey("MAYOR_AATROX"));
    }

    @Test
    @DisplayName("NeuRepoCollector computes reforge prefix collision base names correctly")
    public void testCollisionDetection() {
        NeuRepoCollector collector = new NeuRepoCollector();

        Map<String, NeuRepoCollector.NeuItem> items = new HashMap<>();
        items.put("ANCIENT_CLAW", new NeuRepoCollector.NeuItem(
            "ANCIENT_CLAW", "minecraft:bone", "§9Ancient Claw", List.of(), "", 0, "", "", List.of()
        ));
        items.put("WARPED_STONE", new NeuRepoCollector.NeuItem(
            "WARPED_STONE", "minecraft:cobblestone", "§5Warped Stone", List.of(), "", 0, "", "", List.of()
        ));
        items.put("NORMAL_SWORD", new NeuRepoCollector.NeuItem(
            "NORMAL_SWORD", "minecraft:iron_sword", "§fIron Sword", List.of(), "", 0, "", "", List.of()
        ));

        Map<String, NeuRepoCollector.NeuReforge> reforges = Map.of(
            "Ancient", new NeuRepoCollector.NeuReforge("Ancient", "ARMOR", List.of(), Map.of()),
            "Warped", new NeuRepoCollector.NeuReforge("Warped", "SWORD", List.of(), Map.of())
        );

        Set<String> collisions = collector.computeCollisions(items, reforges, Map.of());
        assertTrue(collisions.contains("Ancient Claw"));
        assertTrue(collisions.contains("Warped Stone"));
        assertFalse(collisions.contains("Iron Sword"));
    }
}
