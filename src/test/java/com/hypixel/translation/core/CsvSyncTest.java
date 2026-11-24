package com.hypixel.translation.core;

import com.hypixel.translation.e2e.BaseE2ETest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
@Tag("component")
public class CsvSyncTest extends BaseE2ETest {

    @Test
    @DisplayName("Verify RFC 4180 CSV export structure and headers")
    void testCsvExportStructure() {
        String csv = csvSyncManager.exportTokensToCsvString(tokenDictionary);
        assertTrue(csv.startsWith("\"token_id\",\"en\",\"zh_tw\",\"category\""));
        assertTrue(csv.contains("\"STAT_DAMAGE\",\"Damage\",\"傷害\",\"stat\""));
    }

    @Test
    @DisplayName("Verify RFC 4180 quote escaping for fields with quotes and commas")
    void testQuoteEscaping() {
        tokenDictionary.registerToken(new TokenRecord("COMPLEX_1", "Sword, \"Magic\"", "劍，\"魔法\"", "", "", "test"));
        String csv = csvSyncManager.exportTokensToCsvString(tokenDictionary);

        assertTrue(csv.contains("\"COMPLEX_1\",\"Sword, \"\"Magic\"\"\",\"劍，\"\"魔法\"\"\",\"test\""));
    }

    @Test
    @DisplayName("Verify CSV import updates existing tokens and registers new tokens")
    void testCsvImportAndRegistration() {
        String inputCsv = """
        "token_id","en","zh_tw","category"
        "STAT_DAMAGE","Damage","真·傷害","stat"
        "STAT_NEW","New Stat","新數值","stat"
        """;

        csvSyncManager.importTokensFromCsvString(tokenDictionary, inputCsv);

        assertEquals("真·傷害", tokenDictionary.resolveToken("STAT_DAMAGE"));
        assertEquals("新數值", tokenDictionary.resolveToken("STAT_NEW"));
    }

    @Test
    @DisplayName("Verify multiline CSV field preservation")
    void testMultilineFieldPreservation() {
        String multilineCsv = "\"token_id\",\"en\",\"zh_tw\",\"category\"\r\n\"LORE_1\",\"Line 1\r\nLine 2\",\"第一行\r\n第二行\",\"lore\"\r\n";
        csvSyncManager.importTokensFromCsvString(tokenDictionary, multilineCsv);

        TokenRecord rec = tokenDictionary.get("LORE_1");
        assertNotNull(rec);
        assertEquals("Line 1\r\nLine 2", rec.en());
        assertEquals("第一行\r\n第二行", rec.zh_tw());
    }

    @Test
    @DisplayName("Verify malformed CSV with unclosed quotes throws exception")
    void testMalformedCsvUnclosedQuotes() {
        String badCsv = "\"token_id\",\"en\",\"zh_tw\",\"category\"\n\"BAD\",\"Unclosed";
        assertThrows(IllegalArgumentException.class, () -> csvSyncManager.importTokensFromCsvString(tokenDictionary, badCsv));
    }

    @Test
    @DisplayName("Verify malformed CSV with missing columns throws exception")
    void testMalformedCsvMissingColumns() {
        String badCsv = "\"token_id\",\"en\",\"zh_tw\",\"category\"\n\"BAD_COL\",\"Val1\"";
        assertThrows(IllegalArgumentException.class, () -> csvSyncManager.importTokensFromCsvString(tokenDictionary, badCsv));
    }

    @Test
    @DisplayName("Verify full file roundtrip fidelity with BOM")
    void testFileRoundtripFidelity() throws Exception {
        Path csvFile = tempDir.resolve("export.csv");
        csvSyncManager.exportTokensToCsv(tokenDictionary, csvFile);
        assertTrue(Files.exists(csvFile));

        TokenDictionary reloaded = new TokenDictionary();
        csvSyncManager.importTokensFromCsv(reloaded, csvFile);

        assertEquals(tokenDictionary.size(), reloaded.size());
        for (String key : tokenDictionary.getTokens().keySet()) {
            assertEquals(tokenDictionary.get(key).zh_tw(), reloaded.get(key).zh_tw());
        }
    }
}
