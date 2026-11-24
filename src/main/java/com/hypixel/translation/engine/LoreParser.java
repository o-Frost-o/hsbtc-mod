package com.hypixel.translation.engine;

import com.hypixel.translation.core.LoreLineDictionary;
import com.hypixel.translation.core.TokenDictionary;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-performance parser and translator for Hypixel SkyBlock item lore lines.
 * Accurately parses stat lines with optional leading glyphs and full-width Chinese colons (：),
 * bonus brackets (+X) [+Y], gemstone slot symbols, ability blocks, resource costs, cooldowns,
 * all 146+ enchantments, skill/dungeon/slayer/Garden/HotM/Kuudra requirements, rarity footers,
 * and optional normalized LoreLineDictionary prose fallback.
 */
public class LoreParser {
    private static final String FC = "(?:§[0-9a-fklmnorA-FKLMNOR])*";

    private static final Pattern STAT_LINE_PATTERN = Pattern.compile(
            "^((?:§[0-9a-fklmnorA-FKLMNOR])+)?((?:[^\\x00-\\x7F]+\\s+)(?:§[0-9a-fklmnorA-FKLMNOR]+)*)?([A-Za-z][A-Za-z ]*?)((?:§[0-9a-fklmnorA-FKLMNOR])+)?\\s*:\\s*(.*)$"
    );
    private static final Pattern ENCHANT_TOKEN_PATTERN = Pattern.compile("(?<![A-Za-z0-9])([A-Za-z][A-Za-z' -]{1,30}?)\\s+([IVXLCDM]+|\\d+)(?![A-Za-z0-9])");

    private static final Pattern CATA_REQ_PATTERN = Pattern.compile(
            "Requires\\s+(" + FC + ")The Catacombs\\s+(Master Mode\\s+)?Floor\\s+([IVXLCDM]+)\\s+Completion(" + FC + "\\.?)"
    );
    private static final Pattern KUUDRA_REQ_PATTERN = Pattern.compile(
            "Requires\\s+(" + FC + ")Kuudra\\s+(Basic|Hot|Burning|Fiery|Infernal)\\s+Tier\\s+Completion(" + FC + "\\.?)"
    );
    private static final Pattern SLAYER_REQ_PATTERN = Pattern.compile(
            "Requires\\s+(" + FC + ")([A-Za-z ]+?)\\s+Slayer\\s+(\\d+)(" + FC + "\\.?)"
    );
    private static final Pattern SKILL_REQ_PATTERN = Pattern.compile(
            "Requires\\s+(" + FC + ")([A-Za-z ]+?)\\s+Skill\\s+(\\d+)(" + FC + "\\.?)"
    );
    private static final Pattern GARDEN_REQ_PATTERN = Pattern.compile(
            "Requires\\s+(" + FC + ")Garden Level\\s+(\\d+)(" + FC + "\\.?)"
    );
    private static final Pattern HOTM_REQ_PATTERN = Pattern.compile(
            "Requires\\s+(" + FC + ")(?:Heart of the Mountain|HotM)\\s+Tier\\s+(\\d+)(" + FC + "\\.?)"
    );
    private static final Pattern HOTM_TIER_PATTERN = Pattern.compile(
            "(" + FC + ")(?:Heart of the Mountain|HotM)\\s+Tier\\s+(\\d+)(" + FC + "\\.?)"
    );
    private static final Pattern COLLECTION_REQ_PATTERN = Pattern.compile(
            "Requires\\s+(" + FC + ")([A-Za-z ]+?)\\s+Collection\\s+([IVXLCDM]+|\\d+)(" + FC + "\\.?)"
    );
    private static final Pattern REPUTATION_REQ_PATTERN = Pattern.compile(
            "Requires\\s+(" + FC + ")([\\d,]+)\\s+(Mage|Barbarian)\\s+Reputation(" + FC + "\\.?)"
    );
    private static final Pattern LEVEL_REQ_PATTERN = Pattern.compile(
            "Requires\\s+(" + FC + ")([A-Za-z ]+?)\\s+Level\\s+(\\d+)(" + FC + "\\.?)"
    );
    private static final Pattern BULLET_SKILL_LEVEL_PATTERN = Pattern.compile(
            "^(\\s*" + FC + "-\\s*" + FC + ")([A-Za-z ]+?)\\s+Level\\s+(\\d+)(" + FC + ")$"
    );
    private static final Pattern PET_TYPE_PATTERN = Pattern.compile(
            "^(" + FC + ")([A-Za-z]+)\\s+Pet(" + FC + ")$"
    );
    private static final Pattern BULLET_SOURCE_PATTERN = Pattern.compile(
            "^(\\s*" + FC + "-\\s*" + FC + ")([A-Za-z' ]+?)\\s+\\((" + FC + ")([IVXLCDM]+(?:-[IVXLCDM]+)?)(" + FC + ")\\)(" + FC + ")$"
    );
    private static final Pattern BULLET_SIMPLE_PATTERN = Pattern.compile(
            "^(\\s*" + FC + "-\\s*" + FC + ")([A-Za-z][A-Za-z' -]+?)(" + FC + ")$"
    );

    private static final Pattern COOLDOWN_SEC_PATTERN = Pattern.compile("(?<=\\d)s\\b");
    private static final Pattern COLOR_STRIP = Pattern.compile("§[0-9a-fklmnorA-FKLMNOR]");
    private static final Pattern OBFUSCATED_STRIP = Pattern.compile("§k[^\\s§]+");
    private static final Pattern LOWERCASE_ASCII = Pattern.compile("[a-z]");
    private static final Pattern RARITY_KEYWORD_WORD = Pattern.compile(
            "\\b(?:VERY[ _]SPECIAL|LEGENDARY|MYTHIC|UNCOMMON|DIVINE|SPECIAL|ULTIMATE|EPIC|RARE|COMMON|ADMIN)\\b"
    );

    private static final Map<String, String> STAT_LABELS = Map.ofEntries(
            Map.entry("Damage", "傷害"),
            Map.entry("Strength", "力量"),
            Map.entry("Defense", "防禦力"),
            Map.entry("True Defense", "真實防禦力"),
            Map.entry("Health", "生命值"),
            Map.entry("Health Regen", "生命恢復"),
            Map.entry("Vitality", "活力"),
            Map.entry("Mending", "治療加成"),
            Map.entry("Intelligence", "智力"),
            Map.entry("Ability Damage", "技能傷害"),
            Map.entry("Crit Damage", "暴擊傷害"),
            Map.entry("Crit Chance", "暴擊機率"),
            Map.entry("Attack Speed", "攻擊速度"),
            Map.entry("Bonus Attack Speed", "額外攻擊速度"),
            Map.entry("Swing Range", "揮擊距離"),
            Map.entry("Speed", "速度"),
            Map.entry("Mining Speed", "採礦速度"),
            Map.entry("Mining Fortune", "採礦幸運"),
            Map.entry("Mining Spread", "挖掘擴散"),
            Map.entry("Gemstone Spread", "寶石擴散"),
            Map.entry("Pristine", "無瑕"),
            Map.entry("Breaking Power", "破壞力"),
            Map.entry("Farming Fortune", "農耕幸運"),
            Map.entry("Foraging Fortune", "伐木幸運"),
            Map.entry("Sweep", "橫掃"),
            Map.entry("Ferocity", "兇暴"),
            Map.entry("Sea Creature Chance", "海怪機率"),
            Map.entry("Double Hook Chance", "雙倍上鉤機率"),
            Map.entry("Trophy Fish Chance", "獎盃魚機率"),
            Map.entry("Treasure Chance", "寶藏機率"),
            Map.entry("Fishing Speed", "釣魚速度"),
            Map.entry("Magic Find", "魔法幸運"),
            Map.entry("Pet Luck", "寵物幸運"),
            Map.entry("Heat Resistance", "耐熱性"),
            Map.entry("Cold Resistance", "耐寒性"),
            Map.entry("Pressure Resistance", "耐壓性"),
            Map.entry("Gear Score", "裝備分數"),
            Map.entry("Combat Wisdom", "戰鬥智慧"),
            Map.entry("Mining Wisdom", "採礦智慧"),
            Map.entry("Farming Wisdom", "農耕智慧"),
            Map.entry("Foraging Wisdom", "伐木智慧"),
            Map.entry("Fishing Wisdom", "釣魚智慧"),
            Map.entry("Enchanting Wisdom", "附魔智慧"),
            Map.entry("Alchemy Wisdom", "煉金智慧"),
            Map.entry("Carpentry Wisdom", "木工智慧"),
            Map.entry("Runecrafting Wisdom", "符文智慧"),
            Map.entry("Social Wisdom", "社交智慧"),
            Map.entry("Taming Wisdom", "馴養智慧"),
            Map.entry("Rift Time", "裂隙時間"),
            Map.entry("Rift Damage", "裂隙傷害"),
            Map.entry("Mana Regen", "魔力恢復"),
            Map.entry("Pull Speed", "拉竿速度"),
            Map.entry("Tracking", "追蹤"),
            Map.entry("Max Storage", "最大儲存量"),
            Map.entry("Resources Generated", "已產出資源"),
            Map.entry("Time Between Actions", "行動間隔時間"),
            Map.entry("Accessory Power", "飾品能量"),
            Map.entry("Bonus Pest Chance", "額外害蟲機率"),
            Map.entry("Max Bonus", "最大加成"),
            Map.entry("Capacity", "容量"),
            Map.entry("Duration", "持續時間"),
            Map.entry("Obtained", "已獲得")
    );

    private static final Map<String, String> SKILL_NAMES = Map.ofEntries(
            Map.entry("Combat", "戰鬥"),
            Map.entry("Farming", "農耕"),
            Map.entry("Mining", "採礦"),
            Map.entry("Fishing", "釣魚"),
            Map.entry("Foraging", "伐木"),
            Map.entry("Enchanting", "附魔"),
            Map.entry("Alchemy", "煉金"),
            Map.entry("Taming", "馴養"),
            Map.entry("Carpentry", "木工"),
            Map.entry("Runecrafting", "符文工藝"),
            Map.entry("Social", "社交"),
            Map.entry("Hunting", "狩獵"),
            Map.entry("Catacombs", "地下墓穴"),
            Map.entry("Dungeoneering", "地下城")
    );

    private static final List<Map.Entry<String, String>> ABILITY_HEADERS_AND_TRIGGERS = List.of(
            Map.entry("SHIFT RIGHT CLICK", "潛行右鍵點擊"),
            Map.entry("SHIFT LEFT CLICK", "潛行左鍵點擊"),
            Map.entry("MIDDLE CLICK", "中鍵點擊"),
            Map.entry("RIGHT CLICK", "右鍵點擊"),
            Map.entry("LEFT CLICK", "左鍵點擊"),
            Map.entry("ITEM ABILITY", "物品技能"),
            Map.entry("PASSIVE", "被動"),
            Map.entry("SNEAK", "潛行"),
            Map.entry("Full Set Bonus:", "全套裝加成："),
            Map.entry("Tiered Bonus:", "階級加成："),
            Map.entry("Extra Bonus:", "額外加成："),
            Map.entry("Piece Bonus:", "單件加成："),
            Map.entry("Ability:", "技能："),
            Map.entry("Passive:", "被動：")
    );

    private static final List<Map.Entry<String, String>> ABILITY_NAMES = sortLongestFirst(Map.ofEntries(
            Map.entry("Instant Transmission", "瞬間移動"),
            Map.entry("Ether Transmission", "以太傳送"),
            Map.entry("Wither Impact", "凋零衝擊"),
            Map.entry("Implosion", "內爆"),
            Map.entry("Shadow Warp", "暗影躍遷"),
            Map.entry("Wither Shield", "凋零護盾"),
            Map.entry("Salvation", "拯救"),
            Map.entry("Giant's Slam", "巨人猛擊"),
            Map.entry("Fire Veil", "烈焰面紗"),
            Map.entry("Molten Wave", "熔岩波"),
            Map.entry("Lightning Strike", "雷擊"),
            Map.entry("Throwing Axe", "飛斧"),
            Map.entry("Guided Sheep", "導向綿羊"),
            Map.entry("Seismic Wave", "震地波"),
            Map.entry("Dragon Rage", "龍怒"),
            Map.entry("Parley", "談判"),
            Map.entry("Frenzied", "狂亂"),
            Map.entry("Phantom Hook", "幻影鉤爪"),
            Map.entry("Double Hook", "雙倍上鉤"),
            Map.entry("Raise Souls", "喚魂"),
            Map.entry("Bat Swarm", "蝙蝠群"),
            Map.entry("Ice Spray", "冰霜噴霧"),
            Map.entry("Fire Freeze", "烈焰凍結"),
            Map.entry("Gravity Storm", "重力風暴"),
            Map.entry("Cells Alignment", "細胞校準"),
            Map.entry("Ragnarock", "諸神黃昏"),
            Map.entry("Soulcry", "靈魂哀號"),
            Map.entry("Pickobulus", "礦鎬爆破"),
            Map.entry("Mining Speed Boost", "採礦速度提升"),
            Map.entry("Maniac Miner", "狂熱礦工"),
            Map.entry("Vein Seeker", "礦脈探索者"),
            Map.entry("Gemstone Infusion", "寶石灌注"),
            Map.entry("Sheer Force", "純粹之力"),
            Map.entry("Tunnel Vision", "隧道視野"),
            Map.entry("Shortbow", "短弓"),
            Map.entry("Endurance", "耐久"),
            Map.entry("Bloodlust", "嗜血"),
            Map.entry("Dominus", "支配"),
            Map.entry("Arcane", "奧術"),
            Map.entry("Fervor", "狂熱"),
            Map.entry("Greed", "貪婪"),
            Map.entry("Overflux", "超載充能"),
            Map.entry("Plasmaflux", "電漿充能"),
            Map.entry("Mana Flux", "魔力充能"),
            Map.entry("Radiant", "光輝"),
            Map.entry("Grapple", "抓鉤"),
            Map.entry("Throw", "投擲"),
            Map.entry("Heal", "治療"),
            Map.entry("Rapid Fire", "急速射擊"),
            Map.entry("Explosive Shot", "爆炸射擊"),
            Map.entry("Phantom Blade", "幻影之刃"),
            Map.entry("Starlight Wand", "星光魔杖"),
            Map.entry("Wither Cloak", "凋零斗篷"),
            Map.entry("Creeper Veil", "苦力怕面紗"),
            Map.entry("Teleport", "傳送"),
            Map.entry("Reap", "收割"),
            Map.entry("Maelstrom", "漩渦"),
            Map.entry("Heat Shield", "熱能護盾"),
            Map.entry("Frostbite", "霜凍噬咬"),
            Map.entry("Homing", "追蹤"),
            Map.entry("Soulflow", "靈流"),
            Map.entry("Frozen Blazing Aura", "冰封烈焰光環"),
            Map.entry("Blazing Aura", "熾熱光環"),
            Map.entry("Mist Aura", "迷霧靈氣"),
            Map.entry("To the Moon!", "直衝月球！"),
            Map.entry("Clownin' Around", "小丑鬧翻天"),
            Map.entry("Second Wind", "再起之風"),
            Map.entry("Terrain Toss", "地形投擲"),
            Map.entry("Future Footsteps", "未來足跡"),
            Map.entry("Rejuvenate", "復甦"),
            Map.entry("Attunement", "調諧"),
            Map.entry("Wraith Drain", "幽魂汲取"),
            Map.entry("Burning Vengeance", "燃燒復仇"),
            Map.entry("Webcutter", "蛛網切割器")
    ));

    private static final List<Map.Entry<String, String>> COST_LABELS = List.of(
            Map.entry("Mana Cost:", "魔力消耗："),
            Map.entry("Soulflow Cost:", "靈流消耗："),
            Map.entry("Vitality Cost:", "活力消耗："),
            Map.entry("Health Cost:", "生命消耗："),
            Map.entry("Coins Cost:", "金幣消耗："),
            Map.entry("Coin Cost:", "金幣消耗："),
            Map.entry("Flowstate:", "心流狀態："),
            Map.entry("Charges:", "充能次數：")
    );

    private static final Map<String, String> ENCHANTMENTS = buildEnchantmentMap();
    private static final List<Map.Entry<String, String>> SORTED_ENCHANTMENTS = sortLongestFirst(ENCHANTMENTS);

    private static final List<Map.Entry<String, String>> RARITY_AND_CATEGORIES = sortLongestFirst(Map.ofEntries(
            Map.entry("VERY SPECIAL", "極度特殊"),
            Map.entry("VERY_SPECIAL", "極度特殊"),
            Map.entry("LEGENDARY", "傳奇"),
            Map.entry("MYTHIC", "神話"),
            Map.entry("UNCOMMON", "罕見"),
            Map.entry("DIVINE", "神聖"),
            Map.entry("SPECIAL", "特殊"),
            Map.entry("ULTIMATE", "終極"),
            Map.entry("EPIC", "史詩"),
            Map.entry("RARE", "稀有"),
            Map.entry("COMMON", "普通"),
            Map.entry("ADMIN", "管理員"),
            Map.entry("DUNGEON ABILITY SCROLL", "地下城技能卷軸"),
            Map.entry("ABILITY SCROLL", "技能卷軸"),
            Map.entry("DUNGEON ITEM", "地下城物品"),
            Map.entry("DUNGEON", "地下城"),
            Map.entry("FISHING WEAPON", "釣魚武器"),
            Map.entry("FISHING ROD", "釣竿"),
            Map.entry("FARMING TOOL", "農耕工具"),
            Map.entry("FOREST SHARD", "森林碎片"),
            Map.entry("WATER SHARD", "水生碎片"),
            Map.entry("COMBAT SHARD", "戰鬥碎片"),
            Map.entry("SLAYER SWORD", "殺手劍"),
            Map.entry("DWARVEN METAL", "矮人金屬"),
            Map.entry("GARDEN CHIP", "花園晶片"),
            Map.entry("RIFT TIMECHARM", "裂隙時間護符"),
            Map.entry("CARNIVAL MASK", "嘉年華面具"),
            Map.entry("FISHING ROD PART", "釣竿零件"),
            Map.entry("ROD PART", "釣竿零件"),
            Map.entry("WATERING CAN", "灑水壺"),
            Map.entry("REFORGE STONE", "重鑄石"),
            Map.entry("POWER STONE", "力量石"),
            Map.entry("TRAVEL SCROLL", "傳送卷軸"),
            Map.entry("ENCHANTED BOOK", "附魔書"),
            Map.entry("POWER ORB", "能量球"),
            Map.entry("PET ITEM", "寵物道具"),
            Map.entry("PET SKIN", "寵物造型"),
            Map.entry("HATCCESSORY", "頭飾品"),
            Map.entry("HATCESSORY", "頭飾品"),
            Map.entry("CHESTPLATE", "胸甲"),
            Map.entry("LEGGINGS", "護腿"),
            Map.entry("HELMET", "頭盔"),
            Map.entry("BOOTS", "靴子"),
            Map.entry("NECKLACE", "項鍊"),
            Map.entry("BRACELET", "手環"),
            Map.entry("GAUNTLET", "手套"),
            Map.entry("LONGSWORD", "長劍"),
            Map.entry("PICKAXE", "鎬"),
            Map.entry("ACCESSORY", "飾品"),
            Map.entry("DEPLOYABLE", "部署物"),
            Map.entry("COSMETIC", "外觀"),
            Map.entry("CLOAK", "斗篷"),
            Map.entry("GLOVES", "護手"),
            Map.entry("DRILL", "鑽頭"),
            Map.entry("SWORD", "劍"),
            Map.entry("BOW", "弓"),
            Map.entry("WAND", "魔杖"),
            Map.entry("STAFF", "法杖"),
            Map.entry("SHOVEL", "鏟"),
            Map.entry("SHEARS", "剪刀"),
            Map.entry("VACUUM", "吸塵器"),
            Map.entry("MINION", "僕從"),
            Map.entry("MEMENTO", "紀念品"),
            Map.entry("BELT", "腰帶"),
            Map.entry("ARROW", "箭矢"),
            Map.entry("BAIT", "魚餌"),
            Map.entry("AXE", "斧"),
            Map.entry("HOE", "鋤"),
            Map.entry("PET", "寵物"),
            Map.entry("PORTAL", "傳送門"),
            Map.entry("DYE", "染料"),
            Map.entry("TROPHY", "獎盃"),
            Map.entry("SACK", "收納袋"),
            Map.entry("MUTATION", "變異體"),
            Map.entry("SHARD", "碎片"),
            Map.entry("CONSUMABLE", "消耗品"),
            Map.entry("MISC", "雜項"),
            Map.entry("RABBIT", "兔子"),
            Map.entry("GEMSTONE", "寶石"),
            Map.entry("ORE", "礦石"),
            Map.entry("BLOCK", "方塊"),
            Map.entry("BOOSTER", "增益道具"),
            Map.entry("SALT", "鹽"),
            Map.entry("TRAP", "陷阱"),
            Map.entry("LASSO", "套索"),
            Map.entry("NET", "捕蟲網"),
            Map.entry("CHISEL", "鑿子"),
            Map.entry("CAPSULE", "膠囊")
    ));

    private static final List<Map.Entry<String, String>> EXACT_SYSTEM_LINES = List.of(
            Map.entry("Right-click to add this pet to your pet menu!", "右鍵點擊將此寵物加入寵物選單！"),
            Map.entry("Can be upgraded at Kat in The Hub!", "可在空島中心的凱特處升級！"),
            Map.entry("Right-click to use your class ability!", "右鍵點擊使用職業技能！"),
            Map.entry("Right-click to send to Hunting Box!", "右鍵點擊送至狩獵盒！"),
            Map.entry("Right-click to view rarity scaling!", "右鍵點擊查看稀有度成長！"),
            Map.entry("Works while in Accessory Bag!", "放在飾品袋中即可生效！"),
            Map.entry("Shift Right-click to move all!", "潛行右鍵點擊全部移動！"),
            Map.entry("Shift Right-Click to redeem all!", "潛行右鍵點擊全部兌換！"),
            Map.entry("Shift Right-click to redeem all!", "潛行右鍵點擊全部兌換！"),
            Map.entry("Requires Any of the following:", "需要以下任一條件："),
            Map.entry("Right-click to view recipes!", "右鍵點擊查看合成配方！"),
            Map.entry("This item can be reforged!", "此道具可以被重鑄！"),
            Map.entry("Shortbow: Instantly shoots!", "短弓：瞬間射擊！"),
            Map.entry("Right-click to configure!", "右鍵點擊設定！"),
            Map.entry("Right-click to consume!", "右鍵點擊消耗！"),
            Map.entry("Click to consume!", "點擊消耗！"),
            Map.entry("Left-click to consume!", "左鍵點擊消耗！"),
            Map.entry("Right-Click to redeem!", "右鍵點擊兌換！"),
            Map.entry("Right-click to redeem!", "右鍵點擊兌換！"),
            Map.entry("Right-click to toggle!", "右鍵點擊切換！"),
            Map.entry("Right-click to equip!", "右鍵點擊裝備！"),
            Map.entry("Click to view recipes!", "點擊查看合成配方！"),
            Map.entry("* Co-op Soulbound *", "* 合作模式靈魂綁定 *"),
            Map.entry("Combinable in Anvil", "可在鐵砧合併"),
            Map.entry("Right-click to open!", "右鍵點擊開啟！"),
            Map.entry("Right-click to use!", "右鍵點擊使用！"),
            Map.entry("Rift-Transferable", "可傳送至裂隙"),
            Map.entry("Co-op Soulbound", "合作模式靈魂綁定"),
            Map.entry("Collection Item", "收藏品"),
            Map.entry("Consumed on use", "使用後消耗"),
            Map.entry("* Soulbound *", "* 靈魂綁定 *"),
            Map.entry("Requirements:", "需求："),
            Map.entry("Dungeon Item", "地下城物品"),
            Map.entry("Applied To:", "適用於："),
            Map.entry("Conflicts:", "衝突："),
            Map.entry("Soulbound", "靈魂綁定"),
            Map.entry("Furniture", "家具"),
            Map.entry("Requires:", "需要："),
            Map.entry("Sources:", "來源：")
    );

    private static final Map<String, String> SOURCE_NAMES = Map.ofEntries(
            Map.entry("Bazaar", "市集"),
            Map.entry("Enchantment Table", "附魔台"),
            Map.entry("Experiments", "實驗台"),
            Map.entry("Jacob's Farming Contest", "雅各布農業競賽"),
            Map.entry("Catacombs", "地下墓穴"),
            Map.entry("Fishing", "釣魚"),
            Map.entry("Kuudra", "庫德拉"),
            Map.entry("Dark Auction", "暗黑拍賣場"),
            Map.entry("Scorpius", "史考皮斯"),
            Map.entry("Crafting Recipe", "合成配方"),
            Map.entry("SkyMart", "空島商店"),
            Map.entry("Community Shop", "社群商店"),
            Map.entry("Tree Gifts", "樹禮"),
            Map.entry("Pearl Dealer", "珍珠商人"),
            Map.entry("Turbo Gourd", "渦輪瓜果"),
            Map.entry("Enchanted Turbo Gourd", "附魔渦輪瓜果")
    );

    private static final Map<String, String> BULLET_SIMPLE_MAP = Map.ofEntries(
            Map.entry("Gauntlet", "手套"),
            Map.entry("Sword", "劍"),
            Map.entry("Longsword", "長劍"),
            Map.entry("Armor", "盔甲"),
            Map.entry("Farming Tool", "農耕工具"),
            Map.entry("Bow", "弓"),
            Map.entry("Fishing Rod", "釣竿"),
            Map.entry("Axe", "斧"),
            Map.entry("Pickaxe", "鎬"),
            Map.entry("Drill", "鑽頭"),
            Map.entry("Boots", "靴子"),
            Map.entry("Necklace", "項鍊"),
            Map.entry("Helmet", "頭盔"),
            Map.entry("Gloves", "護手"),
            Map.entry("Belt", "腰帶"),
            Map.entry("Bracelet", "手環"),
            Map.entry("Cloak", "斗篷"),
            Map.entry("Shears", "剪刀"),
            Map.entry("Leggings", "護腿"),
            Map.entry("Chestplate", "胸甲"),
            Map.entry("Shovel", "鏟"),
            Map.entry("Tools", "工具"),
            Map.entry("Vacuum", "吸塵器"),
            Map.entry("Wand", "魔杖"),
            Map.entry("Hoe", "鋤"),
            Map.entry("Fishing Weapon", "釣魚武器"),
            Map.entry("Shiny Rod", "閃亮釣竿")
    );

    private final TokenDictionary tokenDict;
    private final RomanNumeralParser romanParser;
    private volatile LoreLineDictionary loreLineDict;

    public LoreParser() {
        this(null, new RomanNumeralParser(), null);
    }

    public LoreParser(TokenDictionary tokenDict, RomanNumeralParser romanParser) {
        this(tokenDict, romanParser, null);
    }

    public LoreParser(TokenDictionary tokenDict, RomanNumeralParser romanParser, LoreLineDictionary loreLineDict) {
        this.tokenDict = tokenDict;
        this.romanParser = romanParser;
        this.loreLineDict = loreLineDict;
    }

    public void setLoreLineDictionary(LoreLineDictionary loreLineDict) {
        this.loreLineDict = loreLineDict;
    }

    public LoreLineDictionary getLoreLineDictionary() {
        return loreLineDict;
    }

    public String translateLoreLine(String line) {
        if (line == null || line.isEmpty()) return line;

        // 1. Gemstones Line: "§7Gemstones: §8[\ue003] [⚔]" or "§7Gemstone Slot: ..."
        if (line.contains("Gemstones:") || line.contains("Gemstone Slots:") || line.contains("Gemstone Slot:")) {
            return line.replaceAll("Gemstones:\\s*", "寶石槽：")
                    .replaceAll("Gemstone Slots:\\s*", "寶石槽：")
                    .replaceAll("Gemstone Slot:\\s*", "寶石槽：");
        }

        // 2. Ability / Bonus Headers
        if (line.contains("Ability:") || line.contains("Full Set Bonus:") || line.contains("Tiered Bonus:")
                || line.contains("Extra Bonus:") || line.contains("Piece Bonus:") || line.contains("Passive:")) {
            return translateAbilityLine(line);
        }

        // 3. Ability costs & Cooldowns
        for (Map.Entry<String, String> cost : COST_LABELS) {
            if (line.contains(cost.getKey())) {
                String res = line.replaceAll(Pattern.quote(cost.getKey()) + "\\s*", cost.getValue());
                return res.replaceAll("(\\d[\\d,]*(?:\\.\\d+)?%?)\\s+of\\s+max\\b", "最大值的 $1");
            }
        }
        if (line.contains("Shot Cooldown:")) {
            String res = line.replaceAll("(?i)Shot Cooldown:\\s*", "射擊冷卻：");
            return COOLDOWN_SEC_PATTERN.matcher(res).replaceAll("秒");
        }
        if (line.contains("Cooldown:")) {
            String res = line.replaceAll("(?i)Cooldown:\\s*", "冷卻時間：");
            return COOLDOWN_SEC_PATTERN.matcher(res).replaceAll("秒");
        }

        // 4. Requirements & Special system lines (evaluated before generic stat lines)
        String sys = translateSystemOrRequirementLine(line);
        if (sys != null) {
            return sys;
        }

        // 5. Stat Line with optional leading glyph, pre-colon color, and round/square brackets
        Matcher sm = STAT_LINE_PATTERN.matcher(line);
        if (sm.matches()) {
            String color = sm.group(1) != null ? sm.group(1) : "";
            String glyph = sm.group(2) != null ? sm.group(2) : "";
            String label = sm.group(3).trim();
            String preColonColor = sm.group(4) != null ? sm.group(4) : "";
            String rest = sm.group(5);
            if (isStatLine(label, rest)) {
                if ("Time Between Actions".equals(label) && rest != null && !rest.isEmpty()) {
                    rest = COOLDOWN_SEC_PATTERN.matcher(rest).replaceAll("秒");
                } else if ("Capacity".equals(label) && rest != null && !rest.isEmpty()) {
                    rest = rest.replaceAll("(\\d[\\d,]*)\\s+of\\s+each\\s+item\\b", "每種物品 $1 個");
                }
                return color + glyph + translateStatLabel(label) + preColonColor + "：" + rest;
            }
        }

        // 6. Enchantments Line (matched cleanly across all 146+ SkyBlock enchantments)
        String enchTranslated = tryTranslateEnchantmentLine(line);
        if (enchTranslated != null) {
            return enchTranslated;
        }

        // 7. Rarity & Category Footer
        if (isRarityFooterLine(line)) {
            return translateRarityLine(line);
        }

        // 8. Optional normalized lore-line dictionary lookup
        LoreLineDictionary lld = this.loreLineDict;
        if (lld != null) {
            String dictTranslated = lld.translate(line);
            if (dictTranslated != null) {
                return dictTranslated;
            }
        }

        return line;
    }

    private String translateAbilityLine(String line) {
        String out = line;
        for (Map.Entry<String, String> entry : ABILITY_HEADERS_AND_TRIGGERS) {
            if (out.contains(entry.getKey())) {
                if (entry.getKey().endsWith(":")) {
                    out = out.replaceAll(Pattern.quote(entry.getKey()) + "\\s*", entry.getValue());
                } else {
                    out = out.replace(entry.getKey(), entry.getValue());
                }
            }
        }
        for (Map.Entry<String, String> ab : ABILITY_NAMES) {
            if (out.contains(ab.getKey())) {
                out = out.replace(ab.getKey(), ab.getValue());
            }
        }
        out = out.replace("技能：Set", "技能：設定");
        if (tokenDict != null) {
            out = tokenDict.translateText(out);
        }
        return out;
    }

    private String translateSystemOrRequirementLine(String line) {
        String clean = COLOR_STRIP.matcher(line).replaceAll("").trim();
        String cleanNoLeadGlyph = clean.replaceFirst("^[^\\x00-\\x7F]+\\s+", "");

        for (Map.Entry<String, String> exact : EXACT_SYSTEM_LINES) {
            String key = exact.getKey();
            if (clean.equals(key) || cleanNoLeadGlyph.equals(key) || (key.length() > 14 && line.contains(key))) {
                if (line.contains(key)) {
                    return line.replace(key, exact.getValue());
                }
                if ("* Co-op Soulbound *".equals(key) && line.contains("Co-op Soulbound")) {
                    return line.replace("Co-op Soulbound", "合作模式靈魂綁定");
                }
                if ("* Soulbound *".equals(key) && line.contains("Soulbound")) {
                    return line.replace("Soulbound", "靈魂綁定");
                }
            }
        }

        Matcher cata = CATA_REQ_PATTERN.matcher(line);
        if (cata.find()) {
            String g1 = safeGroup(cata, 1);
            boolean master = cata.group(2) != null;
            String floorZh = translateDungeonFloor(cata.group(3));
            String trail = fmtTrail(safeGroup(cata, 4));
            return replaceSpan(line, cata, "需要通關 " + g1 + "地下墓穴 " + (master ? "大師模式 " : "") + "第" + floorZh + "層" + trail);
        }

        Matcher kuudra = KUUDRA_REQ_PATTERN.matcher(line);
        if (kuudra.find()) {
            String g1 = safeGroup(kuudra, 1);
            String tierZh = kuudraTierZh(kuudra.group(2));
            String trail = fmtTrail(safeGroup(kuudra, 3));
            return replaceSpan(line, kuudra, "需要通關 " + g1 + "庫德拉 " + tierZh + "階級" + trail);
        }

        Matcher slayer = SLAYER_REQ_PATTERN.matcher(line);
        if (slayer.find()) {
            String g1 = safeGroup(slayer, 1);
            String slayerZh = translateSlayerName(slayer.group(2));
            String trail = fmtTrail(safeGroup(slayer, 4));
            return replaceSpan(line, slayer, "需要 " + g1 + slayerZh + "殺手 " + slayer.group(3) + " 級" + trail);
        }

        Matcher skill = SKILL_REQ_PATTERN.matcher(line);
        if (skill.find()) {
            String g1 = safeGroup(skill, 1);
            String skillZh = translateSkillName(skill.group(2));
            String trail = fmtTrail(safeGroup(skill, 4));
            return replaceSpan(line, skill, "需要 " + g1 + skillZh + "技能 " + skill.group(3) + " 級" + trail);
        }

        Matcher garden = GARDEN_REQ_PATTERN.matcher(line);
        if (garden.find()) {
            String g1 = safeGroup(garden, 1);
            String trail = fmtTrail(safeGroup(garden, 3));
            return replaceSpan(line, garden, "需要 " + g1 + "花園等級 " + garden.group(2) + trail);
        }

        Matcher hotmReq = HOTM_REQ_PATTERN.matcher(line);
        if (hotmReq.find()) {
            String g1 = safeGroup(hotmReq, 1);
            String trail = fmtTrail(safeGroup(hotmReq, 3));
            return replaceSpan(line, hotmReq, "需要 " + g1 + "山之心階級 " + hotmReq.group(2) + trail);
        }

        Matcher hotmTier = HOTM_TIER_PATTERN.matcher(line);
        if (hotmTier.find()) {
            String g1 = safeGroup(hotmTier, 1);
            String trail = fmtTrail(safeGroup(hotmTier, 3));
            return replaceSpan(line, hotmTier, g1 + "山之心階級 " + hotmTier.group(2) + trail);
        }

        Matcher coll = COLLECTION_REQ_PATTERN.matcher(line);
        if (coll.find()) {
            String g1 = safeGroup(coll, 1);
            String itemName = coll.group(2).trim();
            String itemZh = tokenDict != null ? tokenDict.translateText(itemName) : itemName;
            String trail = fmtTrail(safeGroup(coll, 4));
            return replaceSpan(line, coll, "需要 " + g1 + itemZh + " 收藏 " + coll.group(3) + trail);
        }

        Matcher rep = REPUTATION_REQ_PATTERN.matcher(line);
        if (rep.find()) {
            String g1 = safeGroup(rep, 1);
            String factionZh = "Mage".equals(rep.group(3)) ? "法師" : "蠻族";
            String trail = fmtTrail(safeGroup(rep, 4));
            return replaceSpan(line, rep, "需要 " + g1 + rep.group(2) + " " + factionZh + "聲望" + trail);
        }

        Matcher lvl = LEVEL_REQ_PATTERN.matcher(line);
        if (lvl.find()) {
            String g1 = safeGroup(lvl, 1);
            String skillZh = translateSkillName(lvl.group(2));
            String trail = fmtTrail(safeGroup(lvl, 4));
            return replaceSpan(line, lvl, "需要 " + g1 + skillZh + " 等級 " + lvl.group(3) + trail);
        }

        Matcher bulletSkill = BULLET_SKILL_LEVEL_PATTERN.matcher(line);
        if (bulletSkill.matches()) {
            String g1 = safeGroup(bulletSkill, 1);
            String skillZh = translateSkillName(bulletSkill.group(2));
            String g4 = safeGroup(bulletSkill, 4);
            return g1 + skillZh + " 等級 " + bulletSkill.group(3) + g4;
        }

        Matcher petType = PET_TYPE_PATTERN.matcher(line);
        if (petType.matches()) {
            String g1 = safeGroup(petType, 1);
            String skillName = petType.group(2).trim();
            String g3 = safeGroup(petType, 3);
            if (SKILL_NAMES.containsKey(skillName) || (tokenDict != null && tokenDict.containsToken("SKILL_" + skillName.toUpperCase(Locale.ROOT)))) {
                return g1 + translateSkillName(skillName) + "寵物" + g3;
            }
        }

        Matcher bulletSrc = BULLET_SOURCE_PATTERN.matcher(line);
        if (bulletSrc.matches()) {
            String srcName = bulletSrc.group(2).trim();
            String srcZh = SOURCE_NAMES.get(srcName);
            if (srcZh != null) {
                return safeGroup(bulletSrc, 1) + srcZh + " (" + safeGroup(bulletSrc, 3) + bulletSrc.group(4) + safeGroup(bulletSrc, 5) + ")" + safeGroup(bulletSrc, 6);
            }
        }

        Matcher bulletSimple = BULLET_SIMPLE_PATTERN.matcher(line);
        if (bulletSimple.matches()) {
            String target = bulletSimple.group(2).trim();
            String targetZh = BULLET_SIMPLE_MAP.get(target);
            if (targetZh == null) {
                targetZh = ENCHANTMENTS.get(target);
            }
            if (targetZh != null) {
                return safeGroup(bulletSimple, 1) + targetZh + safeGroup(bulletSimple, 3);
            }
        }

        return null;
    }

    private static String safeGroup(Matcher m, int group) {
        String g = m.group(group);
        return g != null ? g : "";
    }

    private static String replaceSpan(String line, Matcher m, String replacement) {
        return line.substring(0, m.start()) + replacement + line.substring(m.end());
    }

    private static String fmtTrail(String trailing) {
        if (trailing == null || trailing.isEmpty()) return "";
        return trailing.contains(".") ? trailing.replace(".", "。") : trailing;
    }

    private static String kuudraTierZh(String tier) {
        return switch (tier) {
            case "Basic" -> "基礎";
            case "Hot" -> "炙熱";
            case "Burning" -> "燃燒";
            case "Fiery" -> "烈焰";
            case "Infernal" -> "地獄";
            default -> tier;
        };
    }

    private String translateSkillName(String name) {
        String trimmed = name.trim();
        if (tokenDict != null) {
            String key = "SKILL_" + trimmed.toUpperCase(Locale.ROOT).replace(" ", "_");
            String resolved = tokenDict.resolveToken(key);
            if (resolved != null && !resolved.startsWith("{")) {
                return resolved;
            }
        }
        String mapped = SKILL_NAMES.get(trimmed);
        if (mapped != null) {
            return mapped;
        }
        return tokenDict != null ? tokenDict.translateText(trimmed) : trimmed;
    }

    private String tryTranslateEnchantmentLine(String line) {
        String clean = COLOR_STRIP.matcher(line).replaceAll("");
        Matcher m = ENCHANT_TOKEN_PATTERN.matcher(clean);
        boolean hasKnownEnchantWithLevel = false;
        while (m.find()) {
            String candidate = m.group(1).trim();
            if (ENCHANTMENTS.containsKey(candidate)) {
                hasKnownEnchantWithLevel = true;
                break;
            }
        }
        if (!hasKnownEnchantWithLevel) {
            return null;
        }

        String out = line;
        for (Map.Entry<String, String> entry : SORTED_ENCHANTMENTS) {
            String en = entry.getKey();
            if (out.contains(en)) {
                String pattern = "(?:(?<![A-Za-z0-9])|(?<=§[0-9a-fklmnorA-FKLMNOR]))" + Pattern.quote(en) + "(?=(?:\\s+(?:[IVXLCDM]+|\\d+)|,|§|$))";
                out = out.replaceAll(pattern, Matcher.quoteReplacement(entry.getValue()));
            }
        }
        if (romanParser != null) {
            out = romanParser.translateRomanLine(out, false);
        }
        return out;
    }

    private boolean isStatLine(String label, String rest) {
        if (STAT_LABELS.containsKey(label) && !"Obtained".equals(label)) {
            return true;
        }
        if (tokenDict != null) {
            String tokenKey = "STAT_" + label.toUpperCase(Locale.ROOT).replace(" ", "_");
            if (tokenDict.containsToken(tokenKey)) {
                return true;
            }
        }
        return rest != null && rest.matches("^((?:§[0-9a-fklmnorA-FKLMNOR])*)[+-]?\\d.*");
    }

    private String translateStatLabel(String label) {
        if (tokenDict != null) {
            String tokenKey = "STAT_" + label.toUpperCase(Locale.ROOT).replace(" ", "_");
            String resolved = tokenDict.resolveToken(tokenKey);
            if (resolved != null && !resolved.startsWith("{")) {
                return resolved;
            }
        }
        return STAT_LABELS.getOrDefault(label, label);
    }

    private static boolean isRarityFooterLine(String line) {
        String noObf = OBFUSCATED_STRIP.matcher(line).replaceAll("");
        String clean = COLOR_STRIP.matcher(noObf).replaceAll("").trim();
        if (clean.isEmpty() || LOWERCASE_ASCII.matcher(clean).find()) {
            return false;
        }
        return RARITY_KEYWORD_WORD.matcher(clean).find();
    }

    private String translateRarityLine(String line) {
        String out = line;
        for (Map.Entry<String, String> entry : RARITY_AND_CATEGORIES) {
            String key = entry.getKey();
            if (out.contains(key)) {
                out = out.replaceAll("(?:(?<![A-Z_])|(?<=§[0-9a-fklmnorA-FKLMNOR]))" + Pattern.quote(key) + "(?![A-Z_])",
                        Matcher.quoteReplacement(entry.getValue()));
            }
        }
        return out;
    }

    private static String translateDungeonFloor(String roman) {
        return switch (roman) {
            case "I" -> "一";
            case "II" -> "二";
            case "III" -> "三";
            case "IV" -> "四";
            case "V" -> "五";
            case "VI" -> "六";
            case "VII" -> "七";
            default -> roman;
        };
    }

    private static String translateSlayerName(String name) {
        return switch (name.trim()) {
            case "Enderman" -> "終界使者";
            case "Zombie", "Revenant" -> "殭屍";
            case "Spider", "Tarantula" -> "蜘蛛";
            case "Wolf", "Sven" -> "狼";
            case "Blaze", "Inferno" -> "烈焰使者";
            case "Vampire", "Riftstalker" -> "吸血鬼";
            default -> name;
        };
    }

    private static List<Map.Entry<String, String>> sortLongestFirst(Map<String, String> source) {
        List<Map.Entry<String, String>> list = new ArrayList<>(source.entrySet());
        list.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));
        return Collections.unmodifiableList(list);
    }

    private static Map<String, String> buildEnchantmentMap() {
        Map<String, String> m = new HashMap<>();
        // Combat & Sword Enchantments
        m.put("Bane of Arthropods", "節肢剋星");
        m.put("Cleave", "順劈斬");
        m.put("Critical", "暴擊");
        m.put("Cubism", "立體剋星");
        m.put("Dragon Hunter", "屠龍者");
        m.put("Ender Slayer", "終界殺手");
        m.put("Execute", "處決");
        m.put("Experience", "經驗加成");
        m.put("Fire Aspect", "燃燒");
        m.put("First Strike", "先發制人");
        m.put("Giant Killer", "巨人殺手");
        m.put("Impaling", "穿刺");
        m.put("Knockback", "擊退");
        m.put("Lethality", "致命破甲");
        m.put("Life Steal", "生命竊取");
        m.put("Looting", "掠奪");
        m.put("Luck", "幸運");
        m.put("Mana Steal", "魔力竊取");
        m.put("Prosecute", "起訴");
        m.put("Scavenger", "拾荒者");
        m.put("Sharpness", "鋒利");
        m.put("Smite", "不死剋星");
        m.put("Smoldering", "悶燒");
        m.put("Syphon", "虹吸");
        m.put("Tabasco", "辣醬增傷");
        m.put("Thunderbolt", "雷霆閃擊");
        m.put("Thunderlord", "雷神");
        m.put("Titan Killer", "泰坦殺手");
        m.put("Triple-Strike", "三連擊");
        m.put("Triple Strike", "三連擊");
        m.put("Vampirism", "吸血鬼");
        m.put("Venomous", "劇毒");
        m.put("Vicious", "惡毒");
        m.put("Champion", "冠軍");
        m.put("Divine Gift", "神聖賜福");
        m.put("Drain", "吸取");
        // Bow Enchantments
        m.put("Aiming", "瞄準");
        m.put("Chance", "機率掉落");
        m.put("Dragon Tracer", "尋龍矢");
        m.put("Flame", "火焰矢");
        m.put("Infinite Quiver", "無限箭袋");
        m.put("Overload", "超載");
        m.put("Piercing", "穿透");
        m.put("Power", "強力");
        m.put("Punch", "衝擊");
        m.put("Snipe", "狙擊");
        m.put("Sniper", "狙擊手");
        m.put("Toxophilite", "神射手");
        // Armor & Equipment Enchantments
        m.put("Aqua Affinity", "親水性");
        m.put("Big Brain", "大腦袋");
        m.put("Blast Protection", "爆炸保護");
        m.put("Counter-Strike", "反擊");
        m.put("Counter Strike", "反擊");
        m.put("Depth Strider", "深海漫遊");
        m.put("Feather Falling", "輕盈墜落");
        m.put("Ferocious Mana", "兇暴魔力");
        m.put("Fire Protection", "火焰保護");
        m.put("Frost Walker", "冰霜行者");
        m.put("Growth", "生長");
        m.put("Hardened Mana", "硬化魔力");
        m.put("Hardened Vitality", "硬化活力");
        m.put("Strong Vitality", "強力活力");
        m.put("Vampiric Vitality", "吸血活力");
        m.put("Vivacious Vitality", "生機活力");
        m.put("Hecatomb", "大屠殺");
        m.put("Ice Cold", "冰寒");
        m.put("Mana Vampire", "魔力汲取");
        m.put("Projectile Protection", "彈射物保護");
        m.put("Protection", "保護");
        m.put("Reflection", "反射");
        m.put("Rejuvenate", "復甦");
        m.put("Respiration", "水下呼吸");
        m.put("Respite", "喘息");
        m.put("Smarty Pants", "聰明褲");
        m.put("Strong Mana", "強力魔力");
        m.put("Sugar Rush", "糖果衝刺");
        m.put("Thorns", "荊棘");
        m.put("Transylvanian", "外西凡尼亞");
        m.put("True Protection", "真實保護");
        m.put("Quantum", "量子");
        m.put("Prosperity", "繁榮");
        m.put("Green Thumb", "園藝能手");
        m.put("Pesterminator", "害蟲終結者");
        // Mining, Farming, Foraging & Tool Enchantments
        m.put("Compact", "壓縮");
        m.put("Cultivating", "耕耘");
        m.put("Dedication", "奉獻");
        m.put("Delicate", "謹慎");
        m.put("Efficiency", "效率");
        m.put("Fortune", "幸運");
        m.put("Harvesting", "收割");
        m.put("Lapidary", "寶石匠");
        m.put("Paleontologist", "古生物學家");
        m.put("Pristine", "無瑕");
        m.put("Replenish", "自動補種");
        m.put("Silk Touch", "絲綢之觸");
        m.put("Smelting Touch", "自動冶煉");
        m.put("Sunder", "劈裂");
        m.put("Small Brain", "小腦袋");
        m.put("Petalfall", "落花");
        m.put("Absorb", "吸收");
        m.put("Turbo-Cactus", "渦輪仙人掌");
        m.put("Turbo-Cacti", "渦輪仙人掌");
        m.put("Turbo-Cane", "渦輪甘蔗");
        m.put("Turbo-Carrot", "渦輪胡蘿蔔");
        m.put("Turbo-Coco", "渦輪可可豆");
        m.put("Turbo-Cocoa", "渦輪可可豆");
        m.put("Turbo-Melon", "渦輪西瓜");
        m.put("Turbo-Moonflower", "渦輪月光花");
        m.put("Turbo-Mushrooms", "渦輪蘑菇");
        m.put("Turbo-Potato", "渦輪馬鈴薯");
        m.put("Turbo-Pumpkin", "渦輪南瓜");
        m.put("Turbo-Rose", "渦輪野玫瑰");
        m.put("Turbo-Sunflower", "渦輪向日葵");
        m.put("Turbo-Warts", "渦輪地獄疙瘩");
        m.put("Turbo-Wheat", "渦輪小麥");
        // Fishing Enchantments
        m.put("Angler", "垂釣者");
        m.put("Blessing", "祝福");
        m.put("Caster", "拋竿手");
        m.put("Charm", "魅力");
        m.put("Corruption", "腐化");
        m.put("Expertise", "專業知識");
        m.put("Frail", "脆弱");
        m.put("Luck of the Sea", "海洋祝福");
        m.put("Lure", "魚餌");
        m.put("Magnet", "磁鐵");
        m.put("Piscary", "漁業");
        m.put("Quick Bite", "快速咬鉤");
        m.put("Spiked Hook", "尖刺魚鉤");
        m.put("Tidal", "潮汐");
        // Misc & Hunting Enchantments
        m.put("Bug Blender", "害蟲攪碎器");
        m.put("Cayenne", "辣椒");
        m.put("Feast", "盛宴");
        m.put("Karma", "業力");
        m.put("Magmarizer", "岩漿化");
        m.put("Prismatic", "稜彩");
        m.put("Rainbow", "彩虹");
        m.put("Forestry", "林業");
        // Ultimate Enchantments
        m.put("Bank", "銀行");
        m.put("Bobbin' Time", "線軸時刻");
        m.put("Chimera", "奇美拉");
        m.put("Combo", "連擊");
        m.put("Duplex", "雙重射擊");
        m.put("Fatal Tempo", "致命節奏");
        m.put("First Impression", "第一印象");
        m.put("Flash", "閃現");
        m.put("Flowstate", "心流狀態");
        m.put("Habanero Tactics", "哈瓦那戰術");
        m.put("Inferno", "煉獄");
        m.put("Last Stand", "背水一戰");
        m.put("Legion", "軍團");
        m.put("Missile", "導彈");
        m.put("No Pain No Gain", "沒有痛苦沒有收穫");
        m.put("One For All", "萬眾歸一");
        m.put("Refrigerate", "冷凍");
        m.put("Reiterate", "重申");
        m.put("Rend", "撕裂");
        m.put("Soul Eater", "噬魂者");
        m.put("Sunset", "日落");
        m.put("Swarm", "蟲群");
        m.put("The One", "唯一");
        m.put("Ultimate Jerry", "終極傑瑞");
        m.put("Ultimate Wise", "終極智慧");
        m.put("Wisdom", "智慧");
        return Collections.unmodifiableMap(m);
    }
}
