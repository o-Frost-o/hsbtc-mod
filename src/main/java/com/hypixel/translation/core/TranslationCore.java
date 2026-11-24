package com.hypixel.translation.core;

import com.hypixel.translation.config.ComponentCategory;
import com.hypixel.translation.config.ModConfig;
import com.hypixel.translation.engine.DynamicItemCombiner;
import com.hypixel.translation.engine.LoreParser;
import com.hypixel.translation.engine.RomanNumeralParser;
import com.hypixel.translation.engine.TextTraverser;
import com.hypixel.translation.interception.InterceptionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Central orchestrator and dependency injection container for translation singletons.
 * Binds TokenDictionary, ItemDictionary, DynamicItemCombiner, LoreParser,
 * UntranslatedCaptureQueue, InterceptionManager, TextTraverser, and ModConfig.
 */
public class TranslationCore {

    private static final Logger LOGGER = LoggerFactory.getLogger("HypixelSkyblockTranslation");
    private static volatile TranslationCore INSTANCE;

    public static TranslationCore getInstance() {
        if (INSTANCE == null) {
            synchronized (TranslationCore.class) {
                if (INSTANCE == null) {
                    initialize();
                }
            }
        }
        return INSTANCE;
    }

    public static synchronized void initialize() {
        LOGGER.info("[TranslationCore] Initializing translation services...");

        ModConfig config = ModConfig.getInstance();
        config.load();

        TokenDictionary tokenDictionary = new TokenDictionary();
        loadBuiltinTokens(tokenDictionary);

        ItemDictionary itemDictionary = new ItemDictionary();
        loadBuiltinItems(itemDictionary);

        DialogueDictionary dialogueDictionary = new DialogueDictionary(tokenDictionary, itemDictionary);
        loadBuiltinDialogues(dialogueDictionary);

        LoreLineDictionary loreLineDictionary = new LoreLineDictionary();
        loadBuiltinLoreLines(loreLineDictionary);

        // Load external dictionaries if present in config directory
        Path configDir = (config.getConfigPath() != null) ? config.getConfigPath().getParent() : null;
        if (configDir != null) {
            Path tokensPath = configDir.resolve("tokens.json");
            if (Files.exists(tokensPath)) {
                try {
                    tokenDictionary.loadFromJson(Files.readString(tokensPath, StandardCharsets.UTF_8));
                    LOGGER.info("[TranslationCore] Loaded {} tokens from external config.", tokenDictionary.size());
                } catch (Exception e) {
                    LOGGER.warn("[TranslationCore] Could not load external tokens.json: {}", e.getMessage());
                }
            }
            Path itemsPath = configDir.resolve("items.json");
            if (Files.exists(itemsPath)) {
                try {
                    itemDictionary.loadFromJson(Files.readString(itemsPath, StandardCharsets.UTF_8));
                    LOGGER.info("[TranslationCore] Loaded {} items from external config.", itemDictionary.size());
                } catch (Exception e) {
                    LOGGER.warn("[TranslationCore] Could not load external items.json: {}", e.getMessage());
                }
            }
            Path dialoguesPath = configDir.resolve("dialogues.json");
            if (Files.exists(dialoguesPath)) {
                try {
                    dialogueDictionary.loadFromFile(dialoguesPath);
                    LOGGER.info("[TranslationCore] Loaded {} dialogues from external config.", dialogueDictionary.size());
                } catch (Exception e) {
                    LOGGER.warn("[TranslationCore] Could not load external dialogues.json: {}", e.getMessage());
                }
            }
            Path loreLinesPath = configDir.resolve("lore_lines.json");
            if (Files.exists(loreLinesPath)) {
                try {
                    loreLineDictionary.loadFromFile(loreLinesPath);
                    LOGGER.info("[TranslationCore] Loaded {} lore line templates from external config.", loreLineDictionary.size());
                } catch (Exception e) {
                    LOGGER.warn("[TranslationCore] Could not load external lore_lines.json: {}", e.getMessage());
                }
            }
        }

        itemDictionary.refreshEditedLore();
        RomanNumeralParser romanParser = new RomanNumeralParser();
        LoreParser loreParser = new LoreParser(tokenDictionary, romanParser, loreLineDictionary);
        DynamicItemCombiner combiner = new DynamicItemCombiner(tokenDictionary, loreParser);
        DynamicItemCombiner.setInstance(combiner);

        UntranslatedCaptureQueue untranslatedQueue = new UntranslatedCaptureQueue(5000);
        if (configDir != null) {
            Path capturePath = configDir.resolve("untranslated_capture.json");
            if (Files.exists(capturePath)) {
                try {
                    int restored = untranslatedQueue.loadFromJson(capturePath);
                    LOGGER.info("[TranslationCore] Restored {} untranslated capture entries from disk.", restored);
                } catch (Exception e) {
                    LOGGER.warn("[TranslationCore] Could not load untranslated_capture.json: {}", e.getMessage());
                }
            }
        }
        InterceptionManager interceptionManager = new InterceptionManager(config);
        InterceptionManager.setInstance(interceptionManager);

        TextTraverser textTraverser = TextTraverser.getInstance();

        INSTANCE = new TranslationCore(
                tokenDictionary,
                itemDictionary,
                dialogueDictionary,
                loreLineDictionary,
                combiner,
                loreParser,
                untranslatedQueue,
                interceptionManager,
                textTraverser,
                config
        );

        INSTANCE.reloadMenuDictionary(configDir);
        LOGGER.info("[TranslationCore] Translation services initialized successfully.");
    }

    private static void loadBuiltinTokens(TokenDictionary dict) {
        // Attempt to load from bundled assets
        try {
            dict.loadFromResource("/assets/hsbtr/dictionaries/tokens.json");
            LOGGER.info("[TranslationCore] Loaded {} tokens from bundle.", dict.size());
            return;
        } catch (Exception e) {
            LOGGER.warn("[TranslationCore] Could not load bundled tokens.json: {}", e.getMessage());
        }

        // Register default fundamental tokens if not found
        registerDefaultTokens(dict);
    }

    private static void registerDefaultTokens(TokenDictionary dict) {
        dict.registerToken(new TokenRecord("STAT_DAMAGE", "Damage", "傷害", "\ue050", "§c", "stat"));
        dict.registerToken(new TokenRecord("STAT_STRENGTH", "Strength", "力量", "\ue051", "§c", "stat"));
        dict.registerToken(new TokenRecord("STAT_DEFENSE", "Defense", "防禦力", "\ue023", "§a", "stat"));
        dict.registerToken(new TokenRecord("STAT_HEALTH", "Health", "生命值", "\ue020", "§c", "stat"));
        dict.registerToken(new TokenRecord("STAT_INTELLIGENCE", "Intelligence", "智力", "\ue003", "§b", "stat"));
        dict.registerToken(new TokenRecord("STAT_CRIT_DAMAGE", "Crit Damage", "暴擊傷害", "\ue007", "§9", "stat"));
        dict.registerToken(new TokenRecord("STAT_CRIT_CHANCE", "Crit Chance", "暴擊機率", "\ue02c", "§9", "stat"));
        dict.registerToken(new TokenRecord("STAT_ATTACK_SPEED", "Attack Speed", "攻擊速度", "\ue021", "§e", "stat"));
        dict.registerToken(new TokenRecord("STAT_SPEED", "Speed", "速度", "\ue022", "§f", "stat"));
        dict.registerToken(new TokenRecord("STAT_MINING_SPEED", "Mining Speed", "採礦速度", "\ue015", "§6", "stat"));
        dict.registerToken(new TokenRecord("STAT_MINING_FORTUNE", "Mining Fortune", "採礦幸運", "\ue053", "§6", "stat"));
        dict.registerToken(new TokenRecord("STAT_FEROCITY", "Ferocity", "兇暴", "\ue016", "§c", "stat"));
        dict.registerToken(new TokenRecord("STAT_SEA_CREATURE_CHANCE", "Sea Creature Chance", "海怪機率", "\ue017", "§3", "stat"));
        dict.registerToken(new TokenRecord("STAT_FISHING_SPEED", "Fishing Speed", "釣魚速度", "\ue018", "§b", "stat"));
        dict.registerToken(new TokenRecord("STAT_MAGIC_FIND", "Magic Find", "魔法魔尋", "\ue01a", "§b", "stat"));
    }

    private static void loadBuiltinItems(ItemDictionary dict) {
        try {
            dict.loadBundledItems("/assets/hsbtr/dictionaries/items.json");
            LOGGER.info("[TranslationCore] Loaded {} items from bundle.", dict.size());
        } catch (Exception e) {
            LOGGER.warn("[TranslationCore] Could not load bundled items.json: {}", e.getMessage());
        }
    }

    private static void loadBuiltinDialogues(DialogueDictionary dict) {
        try {
            dict.loadFromResource("/assets/hsbtr/dictionaries/dialogues.json");
            LOGGER.info("[TranslationCore] Loaded {} dialogues ({} patterns) from bundle.", dict.size(), dict.patternCount());
        } catch (Exception e) {
            LOGGER.warn("[TranslationCore] Could not load bundled dialogues.json: {}", e.getMessage());
        }
    }

    private static void loadBuiltinLoreLines(LoreLineDictionary dict) {
        try {
            if (TranslationCore.class.getResource("/assets/hsbtr/dictionaries/lore_lines.json") == null) {
                LOGGER.debug("[TranslationCore] Optional bundled lore_lines.json not present; skipping.");
                return;
            }
            dict.loadFromResource("/assets/hsbtr/dictionaries/lore_lines.json");
            LOGGER.info("[TranslationCore] Loaded {} lore line templates from bundle.", dict.size());
        } catch (Exception e) {
            LOGGER.debug("[TranslationCore] Could not load bundled lore_lines.json: {}", e.getMessage());
        }
    }

    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("§[0-9a-fklmnorA-FKLMNOR]");
    private static final Pattern CJK_CHAR_PATTERN = Pattern.compile("[\\u4e00-\\u9fff]");
    private static final Pattern SCOREBOARD_META_PATTERN = Pattern.compile("^\\d{2}/\\d{2}/\\d{2}(?:\\s+[mML][\\w-]+)?$");
    private static final Pattern PLAYER_CHAT_PATTERN = Pattern.compile("^(?:\\[(?:VIP\\+{0,1}|MVP\\+{0,2}|YOUTUBE|ADMIN|MOD|HELPER|PIG\\+{3})\\]\\s+)?[A-Za-z0-9_]{2,16}:\\s+.*");

    private final TokenDictionary tokenDictionary;
    private final ItemDictionary itemDictionary;
    private final DialogueDictionary dialogueDictionary;
    private final LoreLineDictionary loreLineDictionary;
    private final DynamicItemCombiner combiner;
    private final LoreParser loreParser;
    private final UntranslatedCaptureQueue untranslatedQueue;
    private final InterceptionManager interceptionManager;
    private final TextTraverser textTraverser;
    private final ModConfig config;
    private final AtomicInteger reloadCount = new AtomicInteger(0);
    private volatile MenuDictionary menuDictionary = new MenuDictionary();

    public TranslationCore(TokenDictionary tokenDictionary,
                           DynamicItemCombiner combiner,
                           LoreParser loreParser,
                           UntranslatedCaptureQueue untranslatedQueue,
                           InterceptionManager interceptionManager,
                           TextTraverser textTraverser) {
        this(tokenDictionary, new ItemDictionary(), combiner, loreParser, untranslatedQueue, interceptionManager, textTraverser, ModConfig.getInstance());
    }

    public TranslationCore(TokenDictionary tokenDictionary,
                           ItemDictionary itemDictionary,
                           DynamicItemCombiner combiner,
                           LoreParser loreParser,
                           UntranslatedCaptureQueue untranslatedQueue,
                           InterceptionManager interceptionManager,
                           TextTraverser textTraverser,
                           ModConfig config) {
        this(tokenDictionary, itemDictionary, new DialogueDictionary(tokenDictionary, itemDictionary),
                combiner, loreParser, untranslatedQueue, interceptionManager, textTraverser, config);
    }

    public TranslationCore(TokenDictionary tokenDictionary,
                           ItemDictionary itemDictionary,
                           DialogueDictionary dialogueDictionary,
                           DynamicItemCombiner combiner,
                           LoreParser loreParser,
                           UntranslatedCaptureQueue untranslatedQueue,
                           InterceptionManager interceptionManager,
                           TextTraverser textTraverser,
                           ModConfig config) {
        this(tokenDictionary, itemDictionary, dialogueDictionary,
                loreParser != null ? loreParser.getLoreLineDictionary() : null,
                combiner, loreParser, untranslatedQueue, interceptionManager, textTraverser, config);
    }

    public TranslationCore(TokenDictionary tokenDictionary,
                           ItemDictionary itemDictionary,
                           DialogueDictionary dialogueDictionary,
                           LoreLineDictionary loreLineDictionary,
                           DynamicItemCombiner combiner,
                           LoreParser loreParser,
                           UntranslatedCaptureQueue untranslatedQueue,
                           InterceptionManager interceptionManager,
                           TextTraverser textTraverser,
                           ModConfig config) {
        this.tokenDictionary = tokenDictionary;
        this.itemDictionary = itemDictionary;
        this.dialogueDictionary = dialogueDictionary;
        this.loreLineDictionary = loreLineDictionary;
        this.combiner = combiner;
        this.loreParser = loreParser;
        this.untranslatedQueue = untranslatedQueue;
        this.interceptionManager = interceptionManager;
        this.textTraverser = textTraverser;
        this.config = config;
        if (loreLineDictionary != null) loreLineDictionary.setCaptureLocalizer(this::localizeCapture);
        menuDictionary.setCaptureLocalizer(this::localizeCapture);
    }

    public synchronized void reload() {
        reloadCount.incrementAndGet();
        LOGGER.info("[TranslationCore] Reloading configuration and dictionaries (reload count: {})...", reloadCount.get());
        if (config != null) {
            config.load();
        }

        Path configDir = (config != null && config.getConfigPath() != null)
                ? config.getConfigPath().getParent()
                : null;

        loadBuiltinTokens(tokenDictionary);
        if (configDir != null) {
            Path tokensPath = configDir.resolve("tokens.json");
            if (Files.exists(tokensPath)) {
                try {
                    tokenDictionary.loadFromJson(Files.readString(tokensPath, StandardCharsets.UTF_8));
                    LOGGER.info("[TranslationCore] Reloaded {} tokens from external file {}.", tokenDictionary.size(), tokensPath);
                } catch (Exception e) {
                    LOGGER.warn("[TranslationCore] Failed to reload tokens from {}: {}", tokensPath, e.getMessage());
                }
            }
        }

        loadBuiltinItems(itemDictionary);
        if (configDir != null) {
            Path itemsPath = configDir.resolve("items.json");
            if (Files.exists(itemsPath)) {
                try {
                    itemDictionary.loadFromJson(Files.readString(itemsPath, StandardCharsets.UTF_8));
                    LOGGER.info("[TranslationCore] Reloaded {} items from external file {}.", itemDictionary.size(), itemsPath);
                } catch (Exception e) {
                    LOGGER.warn("[TranslationCore] Failed to reload items from {}: {}", itemsPath, e.getMessage());
                }
            }
        }

        itemDictionary.refreshEditedLore();
        if (dialogueDictionary != null) {
            dialogueDictionary.clear();
            loadBuiltinDialogues(dialogueDictionary);
            if (configDir != null) {
                Path dialoguesPath = configDir.resolve("dialogues.json");
                if (Files.exists(dialoguesPath)) {
                    try {
                        dialogueDictionary.loadFromFile(dialoguesPath);
                        LOGGER.info("[TranslationCore] Reloaded {} dialogues from external file {}.", dialogueDictionary.size(), dialoguesPath);
                    } catch (Exception e) {
                        LOGGER.warn("[TranslationCore] Failed to reload dialogues from {}: {}", dialoguesPath, e.getMessage());
                    }
                }
            }
        }

        if (loreLineDictionary != null) {
            loreLineDictionary.clear();
            loadBuiltinLoreLines(loreLineDictionary);
            if (configDir != null) {
                Path loreLinesPath = configDir.resolve("lore_lines.json");
                if (Files.exists(loreLinesPath)) {
                    try {
                        loreLineDictionary.loadFromFile(loreLinesPath);
                        LOGGER.info("[TranslationCore] Reloaded {} lore line templates from external file {}.", loreLineDictionary.size(), loreLinesPath);
                    } catch (Exception e) {
                        LOGGER.warn("[TranslationCore] Failed to reload lore_lines.json from {}: {}", loreLinesPath, e.getMessage());
                    }
                }
            }
        }

        reloadMenuDictionary(configDir);
        if (tokenDictionary != null) {
            tokenDictionary.clearCache();
        }
    }

    private void reloadMenuDictionary(Path configDir) {
        MenuDictionary bundled = new MenuDictionary();
        try {
            bundled.loadFromResource("/assets/hsbtr/dictionaries/menus.json");
        } catch (Exception e) {
            LOGGER.warn("[TranslationCore] Could not load bundled menus.json: {}", e.getMessage());
            bundled = new MenuDictionary();
        }

        MenuDictionary updated = bundled;
        if (configDir != null) {
            Path external = configDir.resolve("menus.json");
            if (Files.exists(external)) {
                try {
                    MenuDictionary replacement = new MenuDictionary();
                    replacement.loadFromJson(Files.readString(external, StandardCharsets.UTF_8));
                    updated = replacement;
                } catch (Exception e) {
                    LOGGER.warn("[TranslationCore] Could not load external menus.json: {}", e.getMessage());
                }
            }
        }
        updated.setCaptureLocalizer(this::localizeCapture);
        menuDictionary = updated;
    }

    private String localizeNpc(String value) {
        String known = menuDictionary.translatePerson(value);
        if (!known.equals(value)) return CaptureTemplate.bilingualName(known, value);
        var record = itemDictionary.getByEnglishName(value + " (NPC)");
        if (record == null || !record.id().endsWith("_NPC") || record.name_zh_tw().isBlank()) return value;
        String localized = record.name_zh_tw().replaceFirst("\\s*[(（]NPC[)）]$", "").strip();
        return localized.isEmpty() ? value : CaptureTemplate.bilingualName(localized, value);
    }

    private String localizeCapture(String type, String value) {
        if ("npc".equals(type)) return localizeNpc(value);
        if (!"item".equals(type)) return value;
        var item = itemDictionary.getByEnglishName(value);
        if (item == null) item = itemDictionary.getByEnglishName(value.replaceFirst("(?i) (?:woods?|logs)$", " Log"));
        if (item == null && value.endsWith("s")) {
            item = itemDictionary.getByEnglishName(value.substring(0, value.length() - 1));
        }
        return item == null || item.name_zh_tw().isBlank() ? value : item.name_zh_tw();
    }

    public MenuDictionary getMenuDictionary() {
        return menuDictionary;
    }

    private static final Pattern NPC_SPEECH =
            Pattern.compile("^\\[(?:NPC|BOSS)]\\s+[^:：\\n]+[:：]\\s*(.*)$", Pattern.DOTALL);
    private static final Pattern ENGLISH_REFERENCE = Pattern.compile("（[A-Za-z][^）]*）");
    private static final Pattern ENGLISH_PROSE = Pattern.compile(
            "(?i)\\b(?:the|and|your|you|with|from|for|will|are|has|this|that|into)\\s+[a-z]{2,}\\b");

    public static boolean hasUntranslatedProse(String text) {
        if (text == null) return false;
        String plain = COLOR_CODE_PATTERN.matcher(text).replaceAll("");
        return ENGLISH_PROSE.matcher(ENGLISH_REFERENCE.matcher(plain).replaceAll("")).find();
    }

    private static boolean hasUntranslatedNpcBody(String original, String localized) {
        var source = NPC_SPEECH.matcher(COLOR_CODE_PATTERN.matcher(original).replaceAll("").strip());
        var target = NPC_SPEECH.matcher(COLOR_CODE_PATTERN.matcher(localized).replaceAll("").strip());
        return source.matches() && target.matches()
                && (source.group(1).equals(target.group(1)) || hasUntranslatedProse(target.group(1)));
    }

    public void captureMenuTooltip(String raw, String title) {
        if (config != null && config.isUntranslatedCapture() && untranslatedQueue != null
                && shouldCaptureUntranslated(raw, ComponentCategory.TOOLTIPS)) {
            String menu = COLOR_CODE_PATTERN.matcher(title).replaceAll("").strip();
            untranslatedQueue.capture(raw, "MENU_TOOLTIP:" + menu);
        }
    }

    /**
     * Convenience helper for Mixins to recursively translate a Component tree while
     * preserving Style/ClickEvent/HoverEvent and capturing untranslated lines when enabled.
     */
    public static Component translateComponent(Component component, ComponentCategory category) {
        if (component == null) return null;
        TranslationCore core = getInstance();
        if (core == null) return component;
        if (category == ComponentCategory.CONTAINER_UI || category == ComponentCategory.TOOLTIPS) {
            return DynamicItemCombiner.translateStyledLine(component, s -> core.translateAndCapture(s, category));
        }
        if (category == ComponentCategory.CHAT) {
            String original = component.getString();
            String clean = COLOR_CODE_PATTERN.matcher(original).replaceAll("").stripLeading();
            if (clean.startsWith("[NPC]") || clean.startsWith("[BOSS]")) {
                return DynamicItemCombiner.translateStyledLine(component, s -> core.translateAndCapture(s, category));
            }

            // Match server notices that split the message across differently colored components.
            if (core.dialogueDictionary != null && !component.getSiblings().isEmpty()
                    && !core.dialogueDictionary.translate(original).equals(original)) {
                return DynamicItemCombiner.translateStyledLine(component, core.dialogueDictionary::translate);
            }
        }
        return core.getTextTraverser().translateRecursive(component, s -> core.translateAndCapture(s, category));
    }

    /** Translates logical book lines across style boundaries, retaining clickable text styles. */
    public static Component translateBookPage(Component page) {
        if (page == null) return null;
        List<MutableComponent> lines = new ArrayList<>();
        lines.add(Component.empty());
        page.visit((style, literal) -> {
            String[] parts = literal.split("\\n", -1);
            for (int index = 0; index < parts.length; index++) {
                if (index > 0) lines.add(Component.empty());
                if (!parts[index].isEmpty()) {
                    lines.getLast().append(Component.literal(parts[index]).setStyle(style));
                }
            }
            return Optional.empty();
        }, Style.EMPTY);

        MutableComponent translatedPage = Component.empty();
        boolean changed = false;
        for (MutableComponent line : lines) {
            if (line != lines.getFirst()) translatedPage.append("\n");
            Component translated = translateComponent(line, ComponentCategory.TOOLTIPS);
            changed |= !translated.getString().equals(line.getString());
            translatedPage.append(translated);
        }
        return changed ? translatedPage : page;
    }

    /**
     * Translates plain/formatted chat, dialogue, UI, or HUD text through DialogueDictionary
     * (when appropriate) and TokenDictionary, capturing meaningful untranslated English strings
     * into UntranslatedCaptureQueue when capture mode is enabled.
     */
    public String translateAndCapture(String raw, ComponentCategory category) {
        if (raw == null || raw.isEmpty()) return raw;

        if (category == ComponentCategory.CONTAINER_UI || category == ComponentCategory.TOOLTIPS) {
            if (category == ComponentCategory.TOOLTIPS && loreLineDictionary != null) {
                CaptureTemplate.Rendered reviewed = loreLineDictionary.matchParagraph(raw);
                if (reviewed != null) return reviewed.text();
            }
            if (menuDictionary.isLocalized(raw)) return raw;
            String localized = menuDictionary.translate(raw, category == ComponentCategory.CONTAINER_UI);
            if (!localized.equals(raw)) return localized;
            if (category == ComponentCategory.TOOLTIPS && loreLineDictionary != null) {
                String description = loreLineDictionary.translate(raw);
                if (description != null) return description;
            }
        }

        String result = raw;
        if (dialogueDictionary != null) {
            if (category == ComponentCategory.CHAT
                    || category == ComponentCategory.ENTITY_TAGS
                    || category == ComponentCategory.TITLE_SUBTITLE
                    || category == ComponentCategory.BOSS_BAR
                    || category == ComponentCategory.ACTION_BAR) {
                result = dialogueDictionary.translate(raw);
            } else if (category == ComponentCategory.TOOLTIPS
                    || category == ComponentCategory.CONTAINER_UI
                    || category == ComponentCategory.TAB_LIST
                    || category == ComponentCategory.SCOREBOARD) {
                result = dialogueDictionary.translateUiPhrase(raw);
            }
        }

        if (result.equals(raw) && tokenDictionary != null && category != ComponentCategory.CHAT
                && !NPC_SPEECH.matcher(COLOR_CODE_PATTERN.matcher(raw).replaceAll("").strip()).matches()) {
            result = tokenDictionary.translateText(raw);
        }

        if (config != null && config.isUntranslatedCapture() && untranslatedQueue != null
                && (result.equals(raw) || category == ComponentCategory.CHAT && hasUntranslatedNpcBody(raw, result))) {
            if (shouldCaptureUntranslated(raw, category)) {
                untranslatedQueue.capture(raw, category);
            }
        }
        return result;
    }

    public static boolean shouldCaptureUntranslated(String raw, ComponentCategory category) {
        if (raw == null || raw.isEmpty()) return false;
        String clean = COLOR_CODE_PATTERN.matcher(raw).replaceAll("").trim();
        if (clean.length() < 3 || clean.length() > 300) return false;
        if (CJK_CHAR_PATTERN.matcher(clean).find()) return false;
        if ("mc.hypixel.net".equalsIgnoreCase(clean) || "www.hypixel.net".equalsIgnoreCase(clean)) return false;
        if (SCOREBOARD_META_PATTERN.matcher(clean).matches()) return false;
        if (category == ComponentCategory.CHAT && !clean.startsWith("[NPC]") && !clean.startsWith("[BOSS]")
                && PLAYER_CHAT_PATTERN.matcher(clean).matches()) {
            return false;
        }
        int letters = 0;
        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                if (++letters >= 3) return true;
            }
        }
        return false;
    }

    public boolean flushUntranslatedQueue() {
        if (untranslatedQueue == null || config == null || config.getConfigPath() == null) {
            return false;
        }
        Path configDir = config.getConfigPath().getParent();
        if (configDir == null) return false;
        Path capturePath = configDir.resolve("untranslated_capture.json");
        try {
            if (untranslatedQueue.size() > 0 || Files.exists(capturePath)) {
                untranslatedQueue.flushToJson(capturePath);
            }
            return true;
        } catch (Exception e) {
            LOGGER.warn("[TranslationCore] Failed to flush untranslated_capture.json: {}", e.getMessage());
            return false;
        }
    }

    public int getReloadCount() {
        return reloadCount.get();
    }

    public TokenDictionary getTokenDictionary() {
        return tokenDictionary;
    }

    public ItemDictionary getItemDictionary() {
        return itemDictionary;
    }

    public DialogueDictionary getDialogueDictionary() {
        return dialogueDictionary;
    }

    public LoreLineDictionary getLoreLineDictionary() {
        return loreLineDictionary;
    }

    public DynamicItemCombiner getCombiner() {
        return combiner;
    }

    public DynamicItemCombiner getDynamicItemCombiner() {
        return combiner;
    }

    public LoreParser getLoreParser() {
        return loreParser;
    }

    public UntranslatedCaptureQueue getUntranslatedQueue() {
        return untranslatedQueue;
    }

    public InterceptionManager getInterceptionManager() {
        return interceptionManager;
    }

    public TextTraverser getTextTraverser() {
        return textTraverser;
    }

    public ModConfig getConfig() {
        return config;
    }
}
