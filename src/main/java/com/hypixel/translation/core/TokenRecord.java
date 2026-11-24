package com.hypixel.translation.core;

import com.google.gson.annotations.SerializedName;

public record TokenRecord(
    @SerializedName("tokenId") String tokenId,
    @SerializedName("en") String en,
    @SerializedName("zh_tw") String zh_tw,
    @SerializedName("glyph") String glyph,
    @SerializedName("color") String color,
    @SerializedName("category") String category
) {
    public TokenRecord(String tokenId, String en, String zh_tw, String glyph, String color) {
        this(tokenId, en, zh_tw, glyph, color, "general");
    }

    public TokenRecord {
        if (tokenId == null || tokenId.isBlank()) {
            throw new IllegalArgumentException("tokenId cannot be null or empty");
        }
        if (en == null) en = "";
        if (zh_tw == null) zh_tw = "";
        if (glyph == null) glyph = "";
        if (color == null) color = "";
        if (category == null) category = "general";
    }

    public String zhTw() {
        return zh_tw;
    }
}
