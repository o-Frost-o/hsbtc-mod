package com.hypixel.translation.core;

import com.hypixel.translation.config.ComponentCategory;
import com.hypixel.translation.e2e.BaseE2ETest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
@Tag("component")
public class UntranslatedCaptureQueueTest extends BaseE2ETest {

    @Test
    @DisplayName("Verify clean text color code stripping on capture")
    void testCleanTextStripping() {
        untranslatedQueue.capture("§e§lWelcome §r§7to §bSkyBlock!", ComponentCategory.CHAT);
        assertEquals(1, untranslatedQueue.size());

        UntranslatedRecord rec = untranslatedQueue.getRecords().values().iterator().next();
        assertEquals("Welcome to SkyBlock!", rec.cleanText());
        assertEquals("§e§lWelcome §r§7to §bSkyBlock!", rec.rawText());
        assertEquals("CHAT", rec.category());
        assertEquals(1, rec.seenCount());
    }

    @Test
    @DisplayName("Verify SHA-256 deduplication and seen_count increment")
    void testDeduplicationAndSeenCount() {
        String msg = "§6Rare Drop! §9Exp Bottle";
        for (int i = 0; i < 5; i++) {
            untranslatedQueue.capture(msg, ComponentCategory.CHAT);
        }
        assertEquals(1, untranslatedQueue.size());

        UntranslatedRecord rec = untranslatedQueue.getRecords().values().iterator().next();
        assertEquals("Rare Drop! Exp Bottle", rec.cleanText());
        assertEquals(5, rec.seenCount());
    }

    @Test
    @DisplayName("Verify thread-safe concurrent capture without ConcurrentModificationException")
    void testConcurrentCaptureSafety() throws Exception {
        int threads = 16;
        int messagesPerThread = 50;
        ExecutorService exec = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            final int tid = t;
            exec.submit(() -> {
                try {
                    for (int m = 0; m < messagesPerThread; m++) {
                        // 10 distinct messages across all threads
                        untranslatedQueue.capture("§a[NPC] Message " + (m % 10), ComponentCategory.CHAT);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        exec.shutdown();

        assertEquals(10, untranslatedQueue.size());
        int totalSeen = untranslatedQueue.getRecords().values().stream()
                .mapToInt(UntranslatedRecord::seenCount)
                .sum();
        assertEquals(threads * messagesPerThread, totalSeen);
    }

    @Test
    @DisplayName("Verify bounded capacity drops excess records without unbounded memory growth")
    void testBoundedCapacityEnforcement() {
        UntranslatedCaptureQueue smallQueue = new UntranslatedCaptureQueue(100);
        for (int i = 0; i < 200; i++) {
            smallQueue.capture("Unique Message " + i, ComponentCategory.ACTION_BAR);
        }
        assertEquals(100, smallQueue.size());
    }

    @Test
    @DisplayName("Verify rejection of null, whitespace, and color-only strings")
    void testRejectionOfEmptyInputs() {
        untranslatedQueue.capture(null, ComponentCategory.TOOLTIPS);
        untranslatedQueue.capture("", ComponentCategory.TOOLTIPS);
        untranslatedQueue.capture("    ", ComponentCategory.TOOLTIPS);
        untranslatedQueue.capture("§c§l§r", ComponentCategory.TOOLTIPS);
        assertEquals(0, untranslatedQueue.size());
    }

    @Test
    @DisplayName("Verify queue clearing and JSON serialization")
    void testQueueClearAndJsonFlush() throws Exception {
        untranslatedQueue.capture("Message 1", ComponentCategory.CHAT);
        untranslatedQueue.capture("Message 2", ComponentCategory.CHAT);
        assertEquals(2, untranslatedQueue.size());

        Path jsonFile = tempDir.resolve("capture_test.json");
        untranslatedQueue.flushToJson(jsonFile);
        assertTrue(Files.exists(jsonFile));
        assertTrue(Files.readString(jsonFile).contains("Message 1"));

        untranslatedQueue.clear();
        assertEquals(0, untranslatedQueue.size());
    }
}
