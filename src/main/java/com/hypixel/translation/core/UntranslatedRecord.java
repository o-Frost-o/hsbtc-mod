package com.hypixel.translation.core;

import com.google.gson.annotations.SerializedName;

/**
 * Telemetry record for untranslated text captured during gameplay.
 */
public record UntranslatedRecord(
    @SerializedName("hash") String hash,
    @SerializedName("cleanText") String cleanText,
    @SerializedName("rawText") String rawText,
    @SerializedName("category") String category,
    @SerializedName("timestamp") long timestamp,
    @SerializedName("seenCount") int seenCount
) {
    public UntranslatedRecord {
        if (hash == null) hash = "";
        if (cleanText == null) cleanText = "";
        if (rawText == null) rawText = "";
        if (category == null) category = "GENERAL";
    }
}
