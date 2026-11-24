package com.hypixel.translation.interception;

import com.hypixel.translation.config.ComponentCategory;
import com.hypixel.translation.config.ModConfig;
import com.hypixel.translation.core.DialogueDictionary;
import com.hypixel.translation.core.TokenDictionary;
import com.hypixel.translation.core.TranslationCore;
import com.hypixel.translation.core.UntranslatedCaptureQueue;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Handles non-destructive translation of scoreboard objective titles and sidebar entries.
 * Reassembles Hypixel 1.8 split-sibling lines (Team prefix + §[g-z] dummy + suffix)
 * into the full visible line before translation, then maps original per-character Style/TextColor
 * back onto the translated Component via LCS + proportional gap alignment.
 */
public class ScoreboardTranslator {

    private final TokenDictionary injectedTokenDict;
    private final DialogueDictionary injectedDialogueDict;

    public ScoreboardTranslator() {
        this(null, null);
    }

    public ScoreboardTranslator(TokenDictionary tokenDict) {
        this(tokenDict, null);
    }

    public ScoreboardTranslator(TokenDictionary tokenDict, DialogueDictionary dialogueDict) {
        this.injectedTokenDict = tokenDict;
        this.injectedDialogueDict = dialogueDict;
    }

    public Component translateLine(Component component) {
        if (injectedTokenDict != null || injectedDialogueDict != null) {
            return translate(component, injectedTokenDict, injectedDialogueDict);
        }
        return translate(component);
    }

    /**
     * Translates a scoreboard Component (title or sidebar row) using TranslationCore singleton.
     */
    public static Component translate(Component component) {
        if (component == null) return null;

        TranslationCore core = TranslationCore.getInstance();
        TokenDictionary dict = core != null ? core.getTokenDictionary() : null;
        DialogueDictionary dialogueDict = core != null ? core.getDialogueDictionary() : null;
        UntranslatedCaptureQueue queue = core != null ? core.getUntranslatedQueue() : null;
        ModConfig config = core != null ? core.getConfig() : ModConfig.getInstance();

        return translateInternal(component, dict, dialogueDict, queue, config);
    }

    /**
     * Dependency-injected overload for unit testing and isolated pipelines.
     */
    public static Component translate(Component component, TokenDictionary tokenDict, DialogueDictionary dialogueDict) {
        return translateInternal(component, tokenDict, dialogueDict, null, null);
    }

    /**
     * Translates a raw scoreboard string line using TranslationCore singleton.
     */
    public static String translateText(String line) {
        if (line == null || line.trim().isEmpty()) return line;
        TranslationCore core = TranslationCore.getInstance();
        TokenDictionary dict = core != null ? core.getTokenDictionary() : null;
        DialogueDictionary dialogueDict = core != null ? core.getDialogueDictionary() : null;
        return translateText(line, dict, dialogueDict);
    }

    /**
     * Dependency-injected overload for translating a raw scoreboard string line.
     */
    public static String translateText(String line, TokenDictionary tokenDict, DialogueDictionary dialogueDict) {
        if (line == null || line.trim().isEmpty()) return line;
        String translated = line;
        if (dialogueDict != null) {
            translated = dialogueDict.translateUiPhrase(translated);
        }
        if (translated.equals(line) && tokenDict != null) {
            translated = tokenDict.translateText(line);
        }
        return translated;
    }

    private static Component translateInternal(Component component,
                                               TokenDictionary tokenDict,
                                               DialogueDictionary dialogueDict,
                                               UntranslatedCaptureQueue queue,
                                               ModConfig config) {
        if (component == null) return null;

        StringBuilder plainSb = new StringBuilder();
        List<Style> origStyles = new ArrayList<>();

        component.visit((leafStyle, text) -> {
            if (text == null || text.isEmpty()) {
                return Optional.empty();
            }
            Style baseStyle = leafStyle != null ? leafStyle : Style.EMPTY;
            Style currentStyle = baseStyle;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '§' && i + 1 < text.length()) {
                    char code = text.charAt(i + 1);
                    ChatFormatting fmt = ChatFormatting.getByCode(code);
                    if (fmt != null) {
                        currentStyle = (fmt == ChatFormatting.RESET)
                                ? baseStyle
                                : currentStyle.applyLegacyFormat(fmt);
                        i++;
                        continue;
                    } else if (isAsciiAlnum(code)) {
                        // Skip Hypixel 1.8 invisible scoreboard uniqueness entry tokens (e.g. §g, §h, §w)
                        i++;
                        continue;
                    }
                }
                plainSb.append(c);
                origStyles.add(currentStyle);
            }
            return Optional.empty();
        }, Style.EMPTY);

        String plainText = plainSb.toString();
        if (plainText.trim().isEmpty()) {
            return component;
        }

        String translated = plainText;
        if (dialogueDict != null) {
            translated = dialogueDict.translateUiPhrase(plainText);
        }
        if (translated.equals(plainText) && tokenDict != null) {
            translated = tokenDict.translateText(plainText);
        }

        if (translated.equals(plainText)) {
            if (config != null && config.isUntranslatedCapture() && queue != null
                    && TranslationCore.shouldCaptureUntranslated(plainText, ComponentCategory.SCOREBOARD)) {
                queue.capture(plainText, "scoreboard");
            }
            return component;
        }
        if (config != null && config.isUntranslatedCapture() && queue != null
                && hasMultipleUntranslatedWords(translated)
                && TranslationCore.shouldCaptureUntranslated(plainText, ComponentCategory.SCOREBOARD)) {
            queue.capture(plainText, "scoreboard");
        }

        StringBuilder cleanTransSb = new StringBuilder();
        List<Style> overrideStyles = new ArrayList<>();
        Style activeOverride = Style.EMPTY;
        for (int i = 0; i < translated.length(); i++) {
            char c = translated.charAt(i);
            if (c == '§' && i + 1 < translated.length()) {
                char code = translated.charAt(i + 1);
                ChatFormatting fmt = ChatFormatting.getByCode(code);
                if (fmt != null) {
                    activeOverride = (fmt == ChatFormatting.RESET)
                            ? Style.EMPTY
                            : activeOverride.applyLegacyFormat(fmt);
                    i++;
                    continue;
                }
            }
            cleanTransSb.append(c);
            overrideStyles.add(activeOverride);
        }

        String cleanTranslated = cleanTransSb.toString();
        if (cleanTranslated.isEmpty()) {
            return Component.empty();
        }

        int M = plainText.length();
        int N = cleanTranslated.length();
        Style[] mappedStyles = new Style[N];

        if ((long) M * N > 100_000L) {
            for (int t = 0; t < N; t++) {
                int oi = Math.min(M - 1, (int) (((t + 0.5) * M) / N));
                mappedStyles[t] = origStyles.get(oi);
            }
        } else {
            int[][] dp = new int[M + 1][N + 1];
            for (int i = M - 1; i >= 0; i--) {
                char sc = plainText.charAt(i);
                for (int j = N - 1; j >= 0; j--) {
                    if (charsMatch(sc, cleanTranslated.charAt(j))) {
                        dp[i][j] = 1 + dp[i + 1][j + 1];
                    } else {
                        dp[i][j] = Math.max(dp[i + 1][j], dp[i][j + 1]);
                    }
                }
            }

            List<int[]> anchors = new ArrayList<>();
            int i = 0;
            int j = 0;
            int lastSrc = -2;
            int lastDst = -2;
            while (i < M && j < N) {
                if (charsMatch(plainText.charAt(i), cleanTranslated.charAt(j))) {
                    boolean contiguous = (i == lastSrc + 1 && j == lastDst + 1);
                    if (!contiguous && dp[i][j] == dp[i + 1][j]
                            && hasCloserSourceMatch(plainText, cleanTranslated, origStyles, dp, i, j, M, N)) {
                        i++;
                        continue;
                    }
                    if (!contiguous && dp[i][j] == dp[i][j + 1]
                            && hasCloserTargetMatch(plainText, cleanTranslated, dp, i, j, M, N)) {
                        j++;
                        continue;
                    }
                    anchors.add(new int[]{i, j});
                    lastSrc = i;
                    lastDst = j;
                    i++;
                    j++;
                } else if (dp[i + 1][j] > dp[i][j + 1]) {
                    i++;
                } else if (dp[i + 1][j] < dp[i][j + 1]) {
                    j++;
                } else if (isProtectedBoundarySpace(plainText, origStyles, i, M)
                        && cleanTranslated.indexOf(' ', j) >= 0) {
                    j++;
                } else if (i / (double) M <= j / (double) N) {
                    i++;
                } else {
                    j++;
                }
            }
            anchors.add(new int[]{M, N});

            int prevSrc = 0;
            int prevDst = 0;
            for (int[] anchor : anchors) {
                int srcIdx = anchor[0];
                int dstIdx = anchor[1];
                int gapDst = dstIdx - prevDst;
                int effectivePrevSrc = prevSrc;
                if (gapDst > 0 && prevDst > 0 && cleanTranslated.charAt(prevDst - 1) == '：'
                        && prevSrc > 0 && prevSrc + 1 < srcIdx
                        && plainText.charAt(prevSrc - 1) == ':' && plainText.charAt(prevSrc) == ' '
                        && cleanTranslated.charAt(prevDst) != ' ') {
                    effectivePrevSrc = prevSrc + 1;
                }
                int gapSrc = srcIdx - effectivePrevSrc;
                for (int t = prevDst; t < dstIdx; t++) {
                    if (gapSrc > 0) {
                        int oi = Math.min(srcIdx - 1, effectivePrevSrc + (int) (((t - prevDst + 0.5) * gapSrc) / gapDst));
                        mappedStyles[t] = origStyles.get(oi);
                    } else {
                        mappedStyles[t] = prevDst > 0
                                ? mappedStyles[prevDst - 1]
                                : (srcIdx < M ? origStyles.get(srcIdx) : Style.EMPTY);
                    }
                }
                if (srcIdx < M && dstIdx < N) {
                    mappedStyles[dstIdx] = origStyles.get(srcIdx);
                }
                prevSrc = srcIdx + 1;
                prevDst = dstIdx + 1;
            }
        }

        for (int t = 0; t < N; t++) {
            Style ov = overrideStyles.get(t);
            if (ov != null && !ov.isEmpty()) {
                mappedStyles[t] = ov.applyTo(mappedStyles[t]);
            }
        }

        List<String> runTexts = new ArrayList<>();
        List<Style> runStyles = new ArrayList<>();
        int runStart = 0;
        for (int t = 1; t <= N; t++) {
            if (t == N || !Objects.equals(mappedStyles[t], mappedStyles[runStart])) {
                runTexts.add(cleanTranslated.substring(runStart, t));
                runStyles.add(mappedStyles[runStart]);
                runStart = t;
            }
        }

        if (runTexts.size() == 1) {
            return Component.literal(runTexts.get(0)).setStyle(runStyles.get(0));
        }

        MutableComponent root = Component.empty();
        for (int k = 0; k < runTexts.size(); k++) {
            root.append(Component.literal(runTexts.get(k)).setStyle(runStyles.get(k)));
        }
        return root;
    }

    private static boolean charsMatch(char src, char dst) {
        return src == dst || (src == ':' && dst == '：');
    }

    private static boolean isProtectedBoundarySpace(String src, List<Style> srcStyles, int i, int M) {
        if (srcStyles == null || i < 0 || i >= M || src.charAt(i) != ' ') {
            return false;
        }
        if (i > 0 && src.charAt(i - 1) == ':') {
            return false;
        }
        return (i > 0 && !Objects.equals(srcStyles.get(i), srcStyles.get(i - 1)))
                || (i + 1 < M && !Objects.equals(srcStyles.get(i), srcStyles.get(i + 1)));
    }

    private static boolean hasCloserSourceMatch(String src, String dst, List<Style> srcStyles, int[][] dp, int i, int j, int M, int N) {
        char targetChar = dst.charAt(j);
        int curScore = dp[i][j];
        double curDist = Math.abs(i / (double) M - j / (double) N);
        boolean afterColon = (targetChar == ' ' && i > 0 && src.charAt(i - 1) == ':');
        boolean atStyleBoundary = (srcStyles != null)
                && ((i > 0 && !Objects.equals(srcStyles.get(i), srcStyles.get(i - 1)))
                || (i + 1 < M && !Objects.equals(srcStyles.get(i), srcStyles.get(i + 1))));
        for (int i2 = i + 1; i2 < M; i2++) {
            if (dp[i2][j] < curScore) break;
            if (charsMatch(src.charAt(i2), targetChar)) {
                boolean forwardContiguous = (i2 + 1 < M && j + 1 < N
                        && charsMatch(src.charAt(i2 + 1), dst.charAt(j + 1))
                        && dp[i2 + 1][j + 1] + 1 == dp[i2][j]);
                if (forwardContiguous) {
                    return true;
                }
                if (targetChar == ' ' && srcStyles != null && !afterColon && atStyleBoundary) {
                    boolean i2AtBoundary = (i2 > 0 && !Objects.equals(srcStyles.get(i2), srcStyles.get(i2 - 1)))
                            || (i2 + 1 < M && !Objects.equals(srcStyles.get(i2), srcStyles.get(i2 + 1)));
                    if (!Objects.equals(srcStyles.get(i), srcStyles.get(i2)) || !i2AtBoundary) {
                        continue;
                    }
                }
                double d2 = Math.abs(i2 / (double) M - j / (double) N);
                if (d2 + 1e-9 < curDist) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasCloserTargetMatch(String src, String dst, int[][] dp, int i, int j, int M, int N) {
        char srcChar = src.charAt(i);
        int curScore = dp[i][j];
        double curDist = Math.abs(i / (double) M - j / (double) N);
        for (int j2 = j + 1; j2 < N; j2++) {
            if (dp[i][j2] < curScore) break;
            if (charsMatch(srcChar, dst.charAt(j2))) {
                if (j2 + 1 < N && i + 1 < M
                        && charsMatch(src.charAt(i + 1), dst.charAt(j2 + 1))
                        && dp[i + 1][j2 + 1] + 1 == dp[i][j2]) {
                    return true;
                }
                double d2 = Math.abs(i / (double) M - j2 / (double) N);
                if (d2 + 1e-9 < curDist) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasMultipleUntranslatedWords(String text) {
        if (text == null || text.isEmpty()) return false;
        int wordCount = 0;
        int currentLen = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                i++;
                continue;
            }
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                currentLen++;
            } else {
                if (currentLen >= 3) {
                    wordCount++;
                    if (wordCount >= 3) return true;
                }
                currentLen = 0;
            }
        }
        if (currentLen >= 3) {
            wordCount++;
        }
        return wordCount >= 3;
    }

    private static boolean isAsciiAlnum(char c) {
        return (c >= '0' && c <= '9')
                || (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z');
    }
}
