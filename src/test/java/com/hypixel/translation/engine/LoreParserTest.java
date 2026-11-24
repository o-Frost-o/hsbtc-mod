package com.hypixel.translation.engine;

import com.hypixel.translation.core.LoreLineDictionary;
import com.hypixel.translation.e2e.BaseE2ETest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("engine")
@Tag("component")
public class LoreParserTest extends BaseE2ETest {

    @Test
    @DisplayName("Verify stat line translation with numbers and formatting")
    void testBasicStatLine() {
        assertEquals("§7傷害：§c+260", loreParser.translateLoreLine("§7Damage: §c+260"));
        assertEquals("§7力量：§c+150", loreParser.translateLoreLine("§7Strength: §c+150"));
        assertEquals("§7智力：§b+350", loreParser.translateLoreLine("§7Intelligence: §b+350"));
    }

    @Test
    @DisplayName("Verify round and square bracket preservation in stat lines")
    void testBracketPreservation() {
        assertEquals("§7傷害：§c+310 §e(+30) §a[+20]", loreParser.translateLoreLine("§7Damage: §c+310 §e(+30) §a[+20]"));
        assertEquals("§7暴擊傷害：§9+250% §e(+35%)", loreParser.translateLoreLine("§7Crit Damage: §9+250% §e(+35%)"));
    }

    @Test
    @DisplayName("Verify gemstone slot symbol and bracket preservation")
    void testGemstoneSlots() {
        assertEquals("§7寶石槽：§8[\ue003] [⚔]", loreParser.translateLoreLine("§7Gemstones: §8[\ue003] [⚔]"));
        assertEquals("§7寶石槽：§8[§7\ue015§8] [✦]", loreParser.translateLoreLine("§7Gemstones: §8[§7\ue015§8] [✦]"));
    }

    @Test
    @DisplayName("Verify ability header and action trigger translation")
    void testAbilityHeader() {
        String line = "§6Ability: Instant Transmission  §e§lRIGHT CLICK";
        assertEquals("§6技能：瞬間移動  §e§l右鍵點擊", loreParser.translateLoreLine(line));

        String line2 = "§6Ability: Salvation  §e§lLEFT CLICK";
        assertEquals("§6技能：拯救  §e§l左鍵點擊", loreParser.translateLoreLine(line2));
    }

    @Test
    @DisplayName("Verify cost and cooldown translation")
    void testCostAndCooldown() {
        assertEquals("§8魔力消耗：§b150\ue003", loreParser.translateLoreLine("§8Mana Cost: §b150\ue003"));
        assertEquals("§8冷卻時間：§a10秒", loreParser.translateLoreLine("§8Cooldown: §a10s"));
        assertEquals("§7射擊冷卻：§a0.5秒", loreParser.translateLoreLine("§7Shot Cooldown: §a0.5s"));
        assertEquals("§8活力消耗：§4450\ue028", loreParser.translateLoreLine("§8Vitality Cost: §4450\ue028"));
        assertEquals("§8靈流消耗：§31⸎", loreParser.translateLoreLine("§8Soulflow Cost: §31⸎"));
    }

    @Test
    @DisplayName("Verify enchantment line translation with Roman numerals")
    void testEnchantmentLine() {
        String line = "§9Sharpness V, Critical VI, Giant Killer V";
        assertEquals("§9鋒利 V, 暴擊 VI, 巨人殺手 V", loreParser.translateLoreLine(line));
    }

    @Test
    @DisplayName("Verify rarity and dungeon category footer translation")
    void testRarityFooter() {
        assertEquals("§6§l傳奇 地下城 劍", loreParser.translateLoreLine("§6§lLEGENDARY DUNGEON SWORD"));
        assertEquals("§d§l神話 弓", loreParser.translateLoreLine("§d§lMYTHIC BOW"));
        assertEquals("§9§l稀有 劍", loreParser.translateLoreLine("§9§lRARE SWORD"));
        assertEquals("§a§l罕見 胸甲", loreParser.translateLoreLine("§a§lUNCOMMON CHESTPLATE"));
        assertEquals("§a§l罕見 劍", loreParser.translateLoreLine("§a§lUNCOMMON SWORD"));
        assertEquals("§a§l罕見 飾品", loreParser.translateLoreLine("§a§lUNCOMMON ACCESSORY"));
    }

    @Test
    @DisplayName("Verify uncommon rarity footer translation precedence over common")
    void testUncommonRarityFooterPrecedence() {
        assertEquals("§a§l罕見 胸甲", loreParser.translateLoreLine("§a§lUNCOMMON CHESTPLATE"));
        assertEquals("§a§l罕見 劍", loreParser.translateLoreLine("§a§lUNCOMMON SWORD"));
        assertEquals("§a§l罕見 飾品", loreParser.translateLoreLine("§a§lUNCOMMON ACCESSORY"));
        assertEquals("§a§l罕見 弓", loreParser.translateLoreLine("§a§lUNCOMMON BOW"));
        assertEquals("§f§l普通 劍", loreParser.translateLoreLine("§f§lCOMMON SWORD"));
        assertEquals("§c§l極度特殊 飾品", loreParser.translateLoreLine("§c§lVERY SPECIAL ACCESSORY"));
    }

    @Test
    @DisplayName("Verify recombobulator obfuscated rarity tag preservation")
    void testObfuscatedRecombobulatorTag() {
        String line = "§d§l§ka§r §d§lMYTHIC DUNGEON SWORD §r§d§l§ka";
        String translated = loreParser.translateLoreLine(line);
        assertTrue(translated.contains("§ka"));
        assertTrue(translated.contains("神話 地下城 劍"));

        String multiChar = "§6§l§kaa MYTHIC DUNGEON SWORD §6§l§kaa";
        assertEquals("§6§l§kaa 神話 地下城 劍 §6§l§kaa", loreParser.translateLoreLine(multiChar));
    }

    @Test
    @DisplayName("Verify extended 148 NEU enchantments, Turbo enchants, and Bonus headers")
    void testExtendedEnchantsAndBonuses() {
        assertEquals("§d§l終極智慧 V, §9生長 VI, 保護 VI",
                loreParser.translateLoreLine("§d§lUltimate Wise V, §9Growth VI, Protection VI"));
        assertEquals("§9海洋祝福 VI, 渦輪地獄疙瘩 V",
                loreParser.translateLoreLine("§9Luck of the Sea VI, Turbo-Warts V"));
        assertEquals("§6全套裝加成：潛行右鍵點擊",
                loreParser.translateLoreLine("§6Full Set Bonus: SHIFT RIGHT CLICK"));
        assertEquals("§c需要 終界使者殺手 7 級。",
                loreParser.translateLoreLine("§cRequires Enderman Slayer 7."));
        assertEquals("§8* 合作模式靈魂綁定 *",
                loreParser.translateLoreLine("§8* Co-op Soulbound *"));
    }

    @Test
    @DisplayName("Verify leading-glyph stat lines with pre-colon color and plain text")
    void testLeadingGlyphStats() {
        assertEquals("§c❤ 生命值§8：§c600", loreParser.translateLoreLine("§c❤ Health§8: §c600"));
        assertEquals("§c❁ 傷害§8：§c100", loreParser.translateLoreLine("§c❁ Damage§8: §c100"));
        assertEquals("§a❈ 防禦力§8：§a1,000", loreParser.translateLoreLine("§a❈ Defense§8: §a1,000"));
        assertEquals("❤ 生命值：600", loreParser.translateLoreLine("❤ Health: 600"));
        assertEquals("§c❤ 生命值§8：", loreParser.translateLoreLine("§c❤ Health§8:"));
    }

    @Test
    @DisplayName("Verify plain and inline-colored Skill, Slayer, Garden, HotM, and Kuudra requirements")
    void testExpandedRequirements() {
        assertEquals("§4❣ §c需要 §a戰鬥技能 22 級§c。",
                loreParser.translateLoreLine("§4❣ §cRequires §aCombat Skill 22§c."));
        assertEquals("§4☠ §c需要 §5狼殺手 3 級§c。",
                loreParser.translateLoreLine("§4☠ §cRequires §5Wolf Slayer 3§c."));
        assertEquals("§4❣ §c需要 §a花園等級 5§c。",
                loreParser.translateLoreLine("§4❣ §cRequires §aGarden Level 5§c."));
        assertEquals("§4❣ §c需要 §5山之心階級 5§c。",
                loreParser.translateLoreLine("§4❣ §cRequires §5Heart of the Mountain Tier 5§c."));
        assertEquals("需要 山之心階級 7。",
                loreParser.translateLoreLine("Requires HotM Tier 7."));
        assertEquals("§7- §a附魔 等級 22",
                loreParser.translateLoreLine("§7- §aEnchanting Level 22"));
        assertEquals("§7- §5山之心階級 3",
                loreParser.translateLoreLine("§7- §5Heart of the Mountain Tier 3"));
        assertEquals("§4❣ §c需要通關 庫德拉 地獄階級。",
                loreParser.translateLoreLine("§4❣ §cRequires Kuudra Infernal Tier Completion."));
    }

    @Test
    @DisplayName("Verify expanded rarity/category footers, PET ITEM ordering, and prose safety")
    void testExpandedRarityFootersAndProseSafety() {
        assertEquals("§5§l史詩 寵物道具", loreParser.translateLoreLine("§5§lEPIC PET ITEM"));
        assertEquals("§f§l普通 傳送門", loreParser.translateLoreLine("§f§lCOMMON PORTAL"));
        assertEquals("§6§l傳奇 染料", loreParser.translateLoreLine("§6§lLEGENDARY DYE"));
        assertEquals("§9§l稀有 獎盃", loreParser.translateLoreLine("§9§lRARE TROPHY"));
        assertEquals("§5§l史詩 收納袋", loreParser.translateLoreLine("§5§lEPIC SACK"));
        assertEquals("§c§l特殊 紀念品", loreParser.translateLoreLine("§c§lSPECIAL MEMENTO"));
        assertEquals("§9§l稀有 變異體", loreParser.translateLoreLine("§9§lRARE MUTATION"));
        assertEquals("§9§l稀有 森林碎片 (ID R34)", loreParser.translateLoreLine("§9§lRARE FOREST SHARD (ID R34)"));
        assertEquals("§c§l特殊 頭飾品", loreParser.translateLoreLine("§c§lSPECIAL HATCCESSORY"));
        assertEquals("§9§l稀有 部署物", loreParser.translateLoreLine("§9§lRARE DEPLOYABLE"));
        assertEquals("§9§l稀有 力量石", loreParser.translateLoreLine("§9§lRARE POWER STONE"));
        assertEquals("§5§l史詩 地下城物品", loreParser.translateLoreLine("§5§lEPIC DUNGEON ITEM"));

        // Prose containing uppercase rarity words must NOT be mangled
        String prose = "§7Turns your RARE Eerie Pet into its";
        assertEquals(prose, loreParser.translateLoreLine(prose));
    }

    @Test
    @DisplayName("Verify high-frequency headers, pet types, source/applied-to/conflict bullets, and click lines")
    void testHeadersPetTypesAndBullets() {
        assertEquals("§8可在鐵砧合併", loreParser.translateLoreLine("§8Combinable in Anvil"));
        assertEquals("§7適用於：", loreParser.translateLoreLine("§7Applied To:"));
        assertEquals("§7來源：", loreParser.translateLoreLine("§7Sources:"));
        assertEquals("§7衝突：", loreParser.translateLoreLine("§7Conflicts:"));
        assertEquals("§7需求：", loreParser.translateLoreLine("§7Requirements:"));
        assertEquals("§7收藏品", loreParser.translateLoreLine("§7Collection Item"));
        assertEquals("§7家具", loreParser.translateLoreLine("§7Furniture"));
        assertEquals("§8使用後消耗", loreParser.translateLoreLine("§8Consumed on use"));
        assertEquals("§8放在飾品袋中即可生效！", loreParser.translateLoreLine("§8Works while in Accessory Bag!"));
        assertEquals("§7可在空島中心的凱特處升級！", loreParser.translateLoreLine("§7Can be upgraded at Kat in The Hub!"));
        assertEquals("§8戰鬥寵物", loreParser.translateLoreLine("§8Combat Pet"));
        assertEquals("§7- 市集 (I-V)", loreParser.translateLoreLine("§7- Bazaar (I-V)"));
        assertEquals("§7- 附魔台 (I-V)", loreParser.translateLoreLine("§7- Enchantment Table (I-V)"));
        assertEquals("§7- 手套", loreParser.translateLoreLine("§7- Gauntlet"));
        assertEquals("§7- 長劍", loreParser.translateLoreLine("§7- Longsword"));
        assertEquals("§7- 硬化活力", loreParser.translateLoreLine("§7- Hardened Vitality"));
        assertEquals("§e右鍵點擊送至狩獵盒！", loreParser.translateLoreLine("§eRight-click to send to Hunting Box!"));
        assertEquals("§e潛行右鍵點擊全部移動！", loreParser.translateLoreLine("§eShift Right-click to move all!"));
    }

    @Test
    @DisplayName("Verify LoreLineDictionary normalization, reordered § preservation, plain zero-§ output, and ID/pronoun safety")
    void testLoreLineDictionaryNormalizationAndTranslation() {
        LoreParser zeroArg = new LoreParser();
        assertNull(zeroArg.getLoreLineDictionary());
        assertEquals("§7傷害：§c+100", zeroArg.translateLoreLine("§7Damage: §c+100"));

        LoreLineDictionary.NormalizedLine norm = LoreLineDictionary.normalize("§7Earn §d25% §7more Slayer XP.");
        assertEquals("Earn {0}% more Slayer XP.", norm.templateKey());
        assertEquals(1, norm.args().size());
        assertEquals("25", norm.args().get(0).value());
        assertEquals("§d", norm.args().get(0).format());

        LoreLineDictionary dict = new LoreLineDictionary();
        dict.loadFromJson("""
                {
                  "meta": {"version": 1},
                  "lines": {
                    "Grants +{0} Accessory Power per {1}": "每 {1} 個提供 +{0} 飾品能量",
                    "Place this minion and it will start": "放置此僕從後，它將開始"
                  }
                }
                """);
        LoreParser withDict = new LoreParser(tokenDictionary, romanNumeralParser, dict);

        assertEquals("§7每 §a2§7 個提供 +§61§7 飾品能量",
                withDict.translateLoreLine("§7Grants §6+1 Accessory Power §7per §a2"));
        assertEquals("每 2 個提供 +1 飾品能量",
                withDict.translateLoreLine("Grants +1 Accessory Power per 2"));
        assertFalse(withDict.translateLoreLine("Grants +1 Accessory Power per 2").contains("§"));
        assertEquals("§7放置此僕從後，它將開始",
                withDict.translateLoreLine("§7Place this minion and it will start"));

        // Non-corruption of IDs and English pronoun "I"
        LoreLineDictionary.NormalizedLine idLt = LoreLineDictionary.normalize("Obtained from LT-522.");
        assertEquals("Obtained from LT-522.", idLt.templateKey());
        LoreLineDictionary.NormalizedLine idR34 = LoreLineDictionary.normalize("ID R34");
        assertEquals("ID R34", idR34.templateKey());
        LoreLineDictionary.NormalizedLine pronounI = LoreLineDictionary.normalize("I will return");
        assertEquals("I will return", pronounI.templateKey());

        // §r reset on argument when baseFormat is non-empty
        LoreLineDictionary resetDict = new LoreLineDictionary();
        resetDict.registerLine("Grants {0} damage", "提供 {0} 傷害");
        assertEquals("§7提供 §r10§7 傷害", resetDict.translate("§7Grants §r10 §7damage"));
        assertEquals("提供 10 傷害", resetDict.translate("Grants 10 damage"));
        assertFalse(resetDict.translate("Grants 10 damage").contains("§"));
    }
}
