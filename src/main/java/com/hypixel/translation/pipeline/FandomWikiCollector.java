package com.hypixel.translation.pipeline;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FandomWikiCollector {
    private static final String API_URL = "https://hypixel-skyblock.fandom.com/api.php?action=parse&page=Module:Reforge/Data&prop=wikitext&format=json";
    private final HttpClient client;
    private final Path cacheDir;
    private final Gson gson = new Gson();

    public record WikiReforge(
        String name,
        String category,
        String source,
        String description
    ) {}

    public FandomWikiCollector() {
        this(Path.of("cache", "fandom"));
    }

    public FandomWikiCollector(Path cacheDir) {
        this(cacheDir, HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build());
    }

    public FandomWikiCollector(Path cacheDir, HttpClient client) {
        this.cacheDir = cacheDir;
        this.client = client;
    }

    public Map<String, WikiReforge> collect() {
        String wikitext = fetchWikitext();
        if (wikitext == null || wikitext.isEmpty()) {
            return Collections.emptyMap();
        }
        return parseLuaReforges(wikitext);
    }

    public Optional<String> fetchReforgeLuaWikitext() {
        String wikitext = fetchWikitext();
        return (wikitext != null && !wikitext.isBlank()) ? Optional.of(wikitext) : Optional.empty();
    }

    public Map<String, String> extractReforgesFromWikitext(String wikitext) {
        Map<String, String> reforges = new HashMap<>();
        Pattern pattern = Pattern.compile("\\['([^']+)'\\]\\s*=\\s*\\{");
        Matcher matcher = pattern.matcher(wikitext);
        while (matcher.find()) {
            String name = matcher.group(1).trim();
            if (!name.isEmpty() && !name.startsWith("--")) {
                reforges.put(name, name);
            }
        }
        return reforges;
    }

    private String fetchWikitext() {
        Path cacheFile = cacheDir.resolve("Module_Reforge_Data.json");

        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("User-Agent", "HypixelSkyblockTranslationMod/1.0")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body() != null) {
                Files.createDirectories(cacheDir);
                Files.writeString(cacheFile, resp.body());
                JsonObject obj = gson.fromJson(resp.body(), JsonObject.class);
                if (obj.has("parse") && obj.getAsJsonObject("parse").has("wikitext")) {
                    return obj.getAsJsonObject("parse").getAsJsonObject("wikitext").get("*").getAsString();
                }
            }
        } catch (Exception ignored) {}

        if (Files.exists(cacheFile)) {
            try {
                JsonObject obj = gson.fromJson(Files.readString(cacheFile), JsonObject.class);
                if (obj.has("parse") && obj.getAsJsonObject("parse").has("wikitext")) {
                    return obj.getAsJsonObject("parse").getAsJsonObject("wikitext").get("*").getAsString();
                }
            } catch (Exception ignored) {}
        }

        return "";
    }

    public Map<String, WikiReforge> parseLuaReforges(String wikitext) {
        Map<String, WikiReforge> map = new LinkedHashMap<>();
        Pattern catPattern = Pattern.compile("^\t\\['([^']+)'\\]\\s*=\\s*\\{", Pattern.MULTILINE);
        Matcher catMatcher = catPattern.matcher(wikitext);
        List<int[]> catSpans = new ArrayList<>();
        List<String> catNames = new ArrayList<>();
        while (catMatcher.find()) {
            catNames.add(catMatcher.group(1));
            catSpans.add(new int[]{catMatcher.start(), catMatcher.end()});
        }

        Pattern refPattern = Pattern.compile("\\[\'([^\']+)\'\\]\\s*=\\s*\\{\\s*stats");
        Pattern srcPattern = Pattern.compile("source\\s*=\\s*\'([^\']*)\'");
        Pattern descPattern = Pattern.compile("description\\s*=\\s*\'([^\']*)\'");

        for (int i = 0; i < catSpans.size(); i++) {
            String cat = catNames.get(i);
            int start = catSpans.get(i)[1];
            int end = (i + 1 < catSpans.size()) ? catSpans.get(i + 1)[0] : wikitext.length();
            String block = wikitext.substring(start, end);

            Matcher rm = refPattern.matcher(block);
            while (rm.find()) {
                String refName = rm.group(1);
                int braceCount = 0;
                int subEnd = -1;
                for (int j = rm.start(); j < block.length(); j++) {
                    char ch = block.charAt(j);
                    if (ch == '{') braceCount++;
                    else if (ch == '}') {
                        braceCount--;
                        if (braceCount == 0) {
                            subEnd = j;
                            break;
                        }
                    }
                }
                String inner = (subEnd != -1) ? block.substring(rm.start(), subEnd) : block.substring(rm.start());
                Matcher sm = srcPattern.matcher(inner);
                String source = sm.find() ? sm.group(1) : "Basic";
                Matcher dm = descPattern.matcher(inner);
                String desc = dm.find() ? dm.group(1) : "";
                map.putIfAbsent(refName, new WikiReforge(refName, cat, source, desc));
            }
        }

        // Fallback simple matcher if complex structure was not found
        if (map.isEmpty()) {
            Map<String, String> simple = extractReforgesFromWikitext(wikitext);
            for (String ref : simple.keySet()) {
                map.put(ref, new WikiReforge(ref, "General", "Basic", ""));
            }
        }

        return map;
    }
}
