package com.hypixel.translation.core;

import com.google.gson.*;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe master item dictionary storing translated SkyBlock items and lore templates.
 */
public class ItemDictionary {
    private static final Map<String, ItemDictionary> BUNDLED_CACHE = new ConcurrentHashMap<>();

    private final Map<String, ItemTranslationRecord> items = new ConcurrentHashMap<>();
    private final Map<String, ItemTranslationRecord> itemsByEnglishName = new ConcurrentHashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private Map<String, List<String>> bundledLore = Map.of();
    private volatile ItemLoreOverrides editedLore = ItemLoreOverrides.EMPTY;

    public ItemDictionary() {}

    public void registerItem(ItemTranslationRecord item) {
        if (item != null && item.id() != null && !item.id().isBlank()) {
            items.put(item.id(), item);
            if (item.name_en() != null && !item.name_en().isBlank()) {
                itemsByEnglishName.put(item.name_en().trim().toLowerCase(Locale.ROOT), item);
            }
        }
    }

    public void registerItems(Collection<ItemTranslationRecord> records) {
        if (records != null) {
            for (ItemTranslationRecord rec : records) {
                registerItem(rec);
            }
        }
    }

    public ItemTranslationRecord get(String id) {
        return items.get(id);
    }

    public ItemTranslationRecord getByEnglishName(String englishName) {
        if (englishName == null || englishName.isBlank()) return null;
        return itemsByEnglishName.get(englishName.trim().toLowerCase(Locale.ROOT));
    }

    public boolean containsItem(String id) {
        return items.containsKey(id);
    }

    public ItemTranslationRecord removeItem(String id) {
        ItemTranslationRecord removed = items.remove(id);
        if (removed != null && removed.name_en() != null) {
            itemsByEnglishName.remove(removed.name_en().trim().toLowerCase(Locale.ROOT));
        }
        return removed;
    }

    public int size() {
        return items.size();
    }

    public Map<String, ItemTranslationRecord> getItems() {
        return Collections.unmodifiableMap(items);
    }

    public void clear() {
        items.clear();
        itemsByEnglishName.clear();
        bundledLore = Map.of();
        editedLore = ItemLoreOverrides.EMPTY;
    }

    public void loadFromResource(String resourcePath) throws IOException {
        loadResource(resourcePath, false);
    }

    void loadBundledItems(String resourcePath) throws IOException {
        loadResource(resourcePath, true);
    }

    private void loadResource(String resourcePath, boolean bundled) throws IOException {
        if (BundledResourceCache.isBundledPath(resourcePath)) {
            ItemDictionary snapshot = BundledResourceCache.getOrLoad(BUNDLED_CACHE, resourcePath, () -> {
                ItemDictionary loaded = new ItemDictionary();
                loaded.loadFromJson(BundledResourceCache.readResourceUtf8(ItemDictionary.class, resourcePath), true);
                return loaded;
            });
            copyFrom(snapshot, bundled);
            return;
        }
        loadFromJson(BundledResourceCache.readResourceUtf8(getClass(), resourcePath), bundled);
    }

    private void copyFrom(ItemDictionary src, boolean bundled) {
        if (src == null) return;
        items.putAll(src.items);
        itemsByEnglishName.putAll(src.itemsByEnglishName);
        if (bundled) {
            bundledLore = src.bundledLore;
        }
    }

    public void loadFromFile(Path path) throws IOException {
        loadFromJson(Files.readString(path, StandardCharsets.UTF_8));
    }

    public void loadFromJson(String json) {
        loadFromJson(json, false);
    }

    private void loadFromJson(String json, boolean bundled) {
        if (json == null || json.isBlank()) return;
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonObject itemsObj = root.has("items") ? root.getAsJsonObject("items") : root;
        Map<String, List<String>> baseline = bundled ? new HashMap<>() : Map.of();
        for (Map.Entry<String, JsonElement> e : itemsObj.entrySet()) {
            if (!e.getValue().isJsonObject()) continue;
            JsonObject o = e.getValue().getAsJsonObject();
            String id = e.getKey();
            String en = o.has("name_en") ? o.get("name_en").getAsString() : "";
            String zh = o.has("name_zh_tw") ? o.get("name_zh_tw").getAsString() : "";
            String cat = o.has("category") ? o.get("category").getAsString() : "MISC";
            String rarity = o.has("rarity") ? o.get("rarity").getAsString() : "COMMON";
            List<String> lore = new ArrayList<>();
            if (o.has("lore_template") && o.get("lore_template").isJsonArray()) {
                for (JsonElement le : o.getAsJsonArray("lore_template")) {
                    lore.add(le.getAsString());
                }
            }
            var record = new ItemTranslationRecord(id, en, zh, cat, rarity, lore);
            registerItem(record);
            if (bundled) baseline.put(id, List.copyOf(record.lore_template()));
        }
        if (bundled) bundledLore = Collections.unmodifiableMap(baseline);
    }

    void refreshEditedLore() {
        var logger = LoggerFactory.getLogger("HypixelSkyblockTranslation");
        try {
            editedLore = ItemLoreOverrides.compile(bundledLore, items);
            if (editedLore.size() > 0) logger.info("[ItemDictionary] Applied edited tooltip text for {} items.", editedLore.size());
        } catch (IOException | JsonParseException ex) {
            logger.warn("[ItemDictionary] Could not load the source for edited item tooltips.", ex);
        }
    }

    public boolean hasEditedLore() { return editedLore.size() > 0; }

    public boolean hasEditedLore(String itemId, String name) { return editedLore.hasPatchFor(itemId, name); }

    public Map<Integer, ItemLoreOverrides.Match> matchEditedLore(String itemId, String name, List<String> original) {
        return editedLore.match(itemId, name, original);
    }

    public void exportToFile(Path path) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, exportToJson(), StandardCharsets.UTF_8);
    }

    public String exportToJson() {
        JsonObject root = new JsonObject();
        JsonObject itemsObj = new JsonObject();
        for (Map.Entry<String, ItemTranslationRecord> e : items.entrySet()) {
            JsonObject o = new JsonObject();
            ItemTranslationRecord rec = e.getValue();
            o.addProperty("id", rec.id());
            o.addProperty("name_en", rec.name_en());
            o.addProperty("name_zh_tw", rec.name_zh_tw());
            o.addProperty("category", rec.category());
            o.addProperty("rarity", rec.rarity());
            JsonArray loreArr = new JsonArray();
            for (String line : rec.lore_template()) {
                loreArr.add(line);
            }
            o.add("lore_template", loreArr);
            itemsObj.add(e.getKey(), o);
        }
        root.add("items", itemsObj);
        return gson.toJson(root);
    }
}
