package com.hypixel.translation.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * RFC 4180 compliant CSV synchronizer supporting two-way sync for glossary tokens and items.
 * Handles embedded quotes, commas, multiline fields, mixed CRLF/LF line endings,
 * UTF-8 BOM encoding for Excel compatibility, and transactional rollback on malformed input.
 */
public class CsvSyncManager {
    public static final String UTF8_BOM = "\uFEFF";
    public static final String CRLF = "\r\n";

    public CsvSyncManager() {}

    // =========================================================================
    // Tokens CSV Sync
    // =========================================================================

    public void exportTokensToCsv(TokenDictionary dict, Path path) throws IOException {
        String csv = exportTokensToCsvString(dict);
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, UTF8_BOM + csv, StandardCharsets.UTF_8);
    }

    public String exportTokensToCsvString(TokenDictionary dict) {
        StringBuilder sb = new StringBuilder();
        sb.append("\"token_id\",\"en\",\"zh_tw\",\"category\"").append(CRLF);
        for (TokenRecord r : dict.getTokens().values()) {
            sb.append(escape(r.tokenId())).append(",")
              .append(escape(r.en())).append(",")
              .append(escape(r.zh_tw())).append(",")
              .append(escape(r.category())).append(CRLF);
        }
        return sb.toString();
    }

    public void importTokensFromCsv(TokenDictionary dict, Path path) throws IOException {
        importTokensFromCsvString(dict, Files.readString(path, StandardCharsets.UTF_8));
    }

    public void importTokensFromCsvString(TokenDictionary dict, String csvContent) {
        if (csvContent == null || csvContent.trim().isEmpty()) {
            return;
        }
        if (csvContent.startsWith(UTF8_BOM)) {
            csvContent = csvContent.substring(1);
        }

        List<List<String>> rows = parseCsv(csvContent);
        if (rows.isEmpty()) {
            return;
        }

        // Staging map ensures transactional atomicity (all-or-nothing)
        Map<String, TokenRecord> staging = new LinkedHashMap<>();
        boolean isHeader = true;

        for (int lineNum = 0; lineNum < rows.size(); lineNum++) {
            List<String> row = rows.get(lineNum);
            if (isHeader) {
                isHeader = false;
                continue;
            }
            if (row.isEmpty() || (row.size() == 1 && row.get(0).isBlank())) {
                continue;
            }
            if (row.size() < 4) {
                throw new IllegalArgumentException("Malformed CSV row at index " + lineNum + ": expected at least 4 columns, got " + row.size());
            }

            String id = row.get(0).trim();
            if (id.isEmpty()) {
                throw new IllegalArgumentException("Malformed CSV row at index " + lineNum + ": token_id cannot be empty");
            }

            String en = row.get(1);
            String zh = row.get(2);
            String cat = row.get(3);

            TokenRecord existing = dict.get(id);
            String glyph = existing != null ? existing.glyph() : "";
            String color = existing != null ? existing.color() : "";

            if (row.size() >= 6) {
                glyph = row.get(4);
                color = row.get(5);
            }

            staging.put(id, new TokenRecord(id, en, zh, glyph, color, cat));
        }

        // Commit all staged records
        for (TokenRecord rec : staging.values()) {
            dict.registerToken(rec);
        }
    }

    // =========================================================================
    // Items CSV Sync
    // =========================================================================

    public void exportItemsToCsv(ItemDictionary dict, Path path) throws IOException {
        String csv = exportItemsToCsvString(dict);
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, UTF8_BOM + csv, StandardCharsets.UTF_8);
    }

    public String exportItemsToCsvString(ItemDictionary dict) {
        StringBuilder sb = new StringBuilder();
        sb.append("\"id\",\"name_en\",\"name_zh_tw\",\"category\",\"rarity\",\"lore_template\"").append(CRLF);
        for (ItemTranslationRecord rec : dict.getItems().values()) {
            String loreJoined = String.join("\n", rec.lore_template());
            sb.append(escape(rec.id())).append(",")
              .append(escape(rec.name_en())).append(",")
              .append(escape(rec.name_zh_tw())).append(",")
              .append(escape(rec.category())).append(",")
              .append(escape(rec.rarity())).append(",")
              .append(escape(loreJoined)).append(CRLF);
        }
        return sb.toString();
    }

    public void importItemsFromCsv(ItemDictionary dict, Path path) throws IOException {
        importItemsFromCsvString(dict, Files.readString(path, StandardCharsets.UTF_8));
    }

    public void importItemsFromCsvString(ItemDictionary dict, String csvContent) {
        if (csvContent == null || csvContent.trim().isEmpty()) {
            return;
        }
        if (csvContent.startsWith(UTF8_BOM)) {
            csvContent = csvContent.substring(1);
        }

        List<List<String>> rows = parseCsv(csvContent);
        if (rows.isEmpty()) {
            return;
        }

        Map<String, ItemTranslationRecord> staging = new LinkedHashMap<>();
        boolean isHeader = true;

        for (int lineNum = 0; lineNum < rows.size(); lineNum++) {
            List<String> row = rows.get(lineNum);
            if (isHeader) {
                isHeader = false;
                continue;
            }
            if (row.isEmpty() || (row.size() == 1 && row.get(0).isBlank())) {
                continue;
            }
            if (row.size() < 6) {
                throw new IllegalArgumentException("Malformed CSV row at index " + lineNum + ": expected at least 6 columns, got " + row.size());
            }

            String id = row.get(0).trim();
            if (id.isEmpty()) {
                throw new IllegalArgumentException("Malformed CSV row at index " + lineNum + ": id cannot be empty");
            }

            String en = row.get(1);
            String zh = row.get(2);
            String cat = row.get(3);
            String rarity = row.get(4);
            String loreRaw = row.get(5);
            List<String> lore = loreRaw.isEmpty() ? List.of() : Arrays.asList(loreRaw.split("\r?\n"));

            staging.put(id, new ItemTranslationRecord(id, en, zh, cat, rarity, lore));
        }

        for (ItemTranslationRecord rec : staging.values()) {
            dict.registerItem(rec);
        }
    }

    // =========================================================================
    // RFC 4180 Parsing & Escaping Engine
    // =========================================================================

    public List<List<String>> parseCsv(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> currentRow = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;
        char[] chars = text.toCharArray();

        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < chars.length && chars[i + 1] == '"') {
                        currentField.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    currentField.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    currentRow.add(currentField.toString());
                    currentField.setLength(0);
                } else if (c == '\r') {
                    if (i + 1 < chars.length && chars[i + 1] == '\n') {
                        i++;
                    }
                    currentRow.add(currentField.toString());
                    currentField.setLength(0);
                    rows.add(new ArrayList<>(currentRow));
                    currentRow.clear();
                } else if (c == '\n') {
                    currentRow.add(currentField.toString());
                    currentField.setLength(0);
                    rows.add(new ArrayList<>(currentRow));
                    currentRow.clear();
                } else {
                    currentField.append(c);
                }
            }
        }

        if (inQuotes) {
            throw new IllegalArgumentException("Malformed CSV: Unterminated quote at end of input");
        }

        if (currentField.length() > 0 || !currentRow.isEmpty()) {
            currentRow.add(currentField.toString());
            rows.add(currentRow);
        }

        return rows;
    }

    public String escape(String s) {
        if (s == null) return "\"\"";
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
