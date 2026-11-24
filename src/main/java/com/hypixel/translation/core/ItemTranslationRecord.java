package com.hypixel.translation.core;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public record ItemTranslationRecord(
    @SerializedName("id") String id,
    @SerializedName("name_en") String name_en,
    @SerializedName("name_zh_tw") String name_zh_tw,
    @SerializedName("category") String category,
    @SerializedName("rarity") String rarity,
    @SerializedName("lore_template") List<String> lore_template
) {
    public ItemTranslationRecord(String id, String name_zh_tw, String category, List<String> lore_template) {
        this(id, "", name_zh_tw, category, "COMMON", lore_template);
    }

    public ItemTranslationRecord {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id cannot be null or empty");
        }
        if (name_en == null) name_en = "";
        if (name_zh_tw == null) name_zh_tw = "";
        if (category == null) category = "MISC";
        if (rarity == null) rarity = "COMMON";
        if (lore_template == null) lore_template = List.of();
    }

    public String nameZhTw() {
        return name_zh_tw;
    }

    public List<String> loreTemplate() {
        return lore_template;
    }
}
