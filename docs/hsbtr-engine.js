/**
 * Hypixel SkyBlock Translation Mod (zh_tw) — Shared Translation & Review Engine
 * Universal Module (Browser global `HsbtrEngine` + Node CommonJS `module.exports`).
 * Zero external dependencies. Matches Java TranslationReview & TranslationWorkbench contracts.
 */
(function (root, factory) {
    if (typeof module === 'object' && module.exports) {
        module.exports = factory();
    } else {
        root.HsbtrEngine = factory();
    }
}(typeof globalThis !== 'undefined' ? globalThis : this, function () {
    'use strict';

    const PAGE_SIZE = 24;
    const RESERVED = new Set(['meta', 'patterns', 'paragraphs', 'paragraph_patterns', 'review_patterns', 'capture_token_sets']);
    const MARKABLE = new Set(['uiName', 'uiLine', 'loreLine', 'paragraph', 'dialogue']);
    const UNSAFE_KEYS = new Set(['__proto__', 'constructor', 'prototype']);
    const VALID_TOKEN_CATS = new Set([
        'stat', 'reforge', 'item', 'category', 'ability', 'enchant', 'location', 'ui',
        'collection', 'mayor', 'rarity', 'skill', 'npc', 'boss', 'general',
        'dialogue', 'mob', 'potion', 'feature', 'event', 'attribute', 'faction', 'mechanic'
    ]);
    const NAMED_REFERENCE_RE = /\$\{([A-Za-z][A-Za-z0-9]*)\}|%([A-Za-z][A-Za-z0-9]*):("(?:[^"\\\r\n]|\\["\\/bfnrt]|\\u[0-9a-fA-F]{4})*")/g;
    const UNSUPPORTED_REF_RE = /\$\{|\$[0-9]+|\{[0-9]+\}|%[A-Za-z][A-Za-z0-9]*:/;
    const SOURCE_REF_RE = /\$\{|\$[0-9]+|\{[0-9]+\}/;
    const GLOSSARY_REF_RE = /(?<!\$)\{([A-Z][A-Z0-9_]+)\}/g;
    const LEGACY_CODE_RE = /§[0-9a-fk-or]/gi;
    const ROLE_ID_RE = /^[a-z][a-zA-Z0-9]{0,23}$/;
    const NUMBER_RE = /^[+-]?[0-9]{1,16}(?:,[0-9]{3}){0,4}(?:\.[0-9]{1,8})?[kKmMbB]?%?$/;
    const ENTITY_RE = /^[\p{L}\p{M}\p{N}][\p{L}\p{M}\p{N} .’'_&/\-]{0,88}$/u;
    const TOOLTIP_HEADING_RE = /^[^.?!:：]{1,55}[:：]/;

    // =========================================================================
    // Synchronous Cross-Environment SHA-256 Digest (part + 0x00)
    // =========================================================================

    const nodeCrypto = (typeof require === 'function' && typeof window === 'undefined')
        ? (() => { try { return require('crypto'); } catch { return null; } })()
        : null;

    const SHA256_K = new Uint32Array([
        0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
        0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
        0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
        0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
    ]);
    const SHA256_W = new Uint32Array(64);
    const textEncoder = new TextEncoder();

    /** Computes lowercase SHA-256 hex digest of a Uint8Array in pure JS. */
    function sha256Bytes(msg) {
        const bitLenHi = Math.floor(msg.length / 0x20000000);
        const bitLenLo = (msg.length << 3) >>> 0;
        const padLen = ((msg.length + 9 + 63) >> 6) << 6;
        const padded = new Uint8Array(padLen);
        padded.set(msg);
        padded[msg.length] = 0x80;
        const view = new DataView(padded.buffer);
        view.setUint32(padLen - 8, bitLenHi, false);
        view.setUint32(padLen - 4, bitLenLo, false);

        let h0 = 0x6a09e667, h1 = 0xbb67ae85, h2 = 0x3c6ef372, h3 = 0xa54ff53a;
        let h4 = 0x510e527f, h5 = 0x9b05688c, h6 = 0x1f83d9ab, h7 = 0x5be0cd19;

        for (let off = 0; off < padLen; off += 64) {
            for (let i = 0; i < 16; i++) SHA256_W[i] = view.getUint32(off + i * 4, false);
            for (let i = 16; i < 64; i++) {
                const s0 = (SHA256_W[i - 15] >>> 7 | SHA256_W[i - 15] << 25)
                    ^ (SHA256_W[i - 15] >>> 18 | SHA256_W[i - 15] << 14) ^ (SHA256_W[i - 15] >>> 3);
                const s1 = (SHA256_W[i - 2] >>> 17 | SHA256_W[i - 2] << 15)
                    ^ (SHA256_W[i - 2] >>> 19 | SHA256_W[i - 2] << 13) ^ (SHA256_W[i - 2] >>> 10);
                SHA256_W[i] = (SHA256_W[i - 16] + s0 + SHA256_W[i - 7] + s1) >>> 0;
            }
            let a = h0, b = h1, c = h2, d = h3, e = h4, f = h5, g = h6, h = h7;
            for (let i = 0; i < 64; i++) {
                const S1 = (e >>> 6 | e << 26) ^ (e >>> 11 | e << 21) ^ (e >>> 25 | e << 7);
                const ch = (e & f) ^ (~e & g);
                const t1 = (h + S1 + ch + SHA256_K[i] + SHA256_W[i]) >>> 0;
                const S0 = (a >>> 2 | a << 30) ^ (a >>> 13 | a << 19) ^ (a >>> 22 | a << 10);
                const maj = (a & b) ^ (a & c) ^ (b & c);
                const t2 = (S0 + maj) >>> 0;
                h = g; g = f; f = e; e = (d + t1) >>> 0;
                d = c; c = b; b = a; a = (t1 + t2) >>> 0;
            }
            h0 = (h0 + a) >>> 0; h1 = (h1 + b) >>> 0; h2 = (h2 + c) >>> 0; h3 = (h3 + d) >>> 0;
            h4 = (h4 + e) >>> 0; h5 = (h5 + f) >>> 0; h6 = (h6 + g) >>> 0; h7 = (h7 + h) >>> 0;
        }
        return [h0, h1, h2, h3, h4, h5, h6, h7]
            .map(n => n.toString(16).padStart(8, '0')).join('');
    }

    /** Computes null-delimited SHA-256 hex digest matching Java TranslationReview.digest(). */
    function digest(...parts) {
        if (nodeCrypto) {
            const hash = nodeCrypto.createHash('sha256');
            for (const part of parts) {
                hash.update(Buffer.from(String(part ?? ''), 'utf8'));
                hash.update(Buffer.from([0]));
            }
            return hash.digest('hex');
        }
        const chunks = parts.map(p => textEncoder.encode(String(p ?? '')));
        let total = chunks.length;
        for (const c of chunks) total += c.length;
        const buf = new Uint8Array(total);
        let pos = 0;
        for (const c of chunks) {
            buf.set(c, pos);
            pos += c.length;
            buf[pos++] = 0;
        }
        return sha256Bytes(buf);
    }

    // =========================================================================
    // Object & Regex Helpers
    // =========================================================================

    function assertSafeKey(key, label = 'key') {
        if (typeof key !== 'string' || !key || UNSAFE_KEYS.has(key) || UNSAFE_KEYS.has(key.toLowerCase()) || key.includes('\0')) {
            throw new Error(`無效的 ${label}: ${String(key)}`);
        }
    }

    function safeClone(val) {
        if (val === null || typeof val !== 'object') return val;
        if (Array.isArray(val)) return val.map(safeClone);
        const out = {};
        for (const [k, v] of Object.entries(val)) {
            if (UNSAFE_KEYS.has(k)) continue;
            out[k] = safeClone(v);
        }
        return out;
    }

    function str(obj, key) {
        return (obj && typeof obj === 'object' && typeof obj[key] === 'string') ? obj[key] : '';
    }

    function isPlainObject(v) {
        return v !== null && typeof v === 'object' && !Array.isArray(v);
    }

    function copyBaseMap(src, dst = {}) {
        if (!isPlainObject(src)) return dst;
        for (const [k, v] of Object.entries(src)) {
            if (UNSAFE_KEYS.has(k)) continue;
            dst[k] = v;
        }
        return dst;
    }

    function deepCopySafeMap(src, dst = {}) {
        if (!isPlainObject(src)) return dst;
        for (const [k, v] of Object.entries(src)) {
            if (UNSAFE_KEYS.has(k)) continue;
            dst[k] = safeClone(v);
        }
        return dst;
    }

    function quoteRegexLiteral(s) {
        return '\\Q' + String(s).replace(/\\E/g, '\\E\\\\E\\Q') + '\\E';
    }

    function escapeRegExp(s) {
        return String(s).replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    }

    /** Translates a subset of Java Pattern syntax (including \Q...\E) into JS RegExp safely. */
    function compileJavaRegex(pattern) {
        let out = '';
        let i = 0;
        let inQuote = false;
        while (i < pattern.length) {
            if (!inQuote && pattern[i] === '\\' && pattern[i + 1] === 'Q') {
                inQuote = true;
                i += 2;
                continue;
            }
            if (inQuote && pattern[i] === '\\' && pattern[i + 1] === 'E') {
                inQuote = false;
                i += 2;
                continue;
            }
            if (inQuote) {
                out += escapeRegExp(pattern[i]);
                i++;
            } else {
                out += pattern[i];
                i++;
            }
        }
        return new RegExp(out, 'u');
    }

    function stripLegacyColor(text) {
        return String(text || '').replace(LEGACY_CODE_RE, '');
    }

    function containsQuery(text, query) {
        if (!text) return false;
        const lower = text.toLowerCase();
        if (lower.includes(query)) return true;
        return text.includes('§') && stripLegacyColor(text).toLowerCase().includes(query);
    }

    function rarityColor(rarity) {
        switch (rarity) {
            case 'UNCOMMON': return '§a';
            case 'RARE': return '§9';
            case 'EPIC': return '§5';
            case 'LEGENDARY': return '§6';
            case 'MYTHIC': return '§d';
            case 'DIVINE': return '§b';
            case 'SPECIAL':
            case 'VERY_SPECIAL': return '§c';
            default: return '§f';
        }
    }

    function createEmptyOverrides() {
        return {
            version: 1,
            updatedAt: new Date().toISOString(),
            tokens: {},
            items: {},
            menus: { titles: {}, lines: {}, people: {}, patterns: [], review_patterns: {} },
            lore_lines: { lines: {}, paragraphs: {}, paragraph_patterns: [], review_patterns: {} },
            dialogues: { dialogues: {}, patterns: [], review_patterns: {} }
        };
    }

    function mergePatternArrays(baseArr, overrideArr) {
        const out = [];
        const seen = new Set();
        for (const r of (Array.isArray(overrideArr) ? overrideArr : [])) {
            if (isPlainObject(r) && typeof r.regex === 'string' && !seen.has(r.regex)) {
                seen.add(r.regex);
                out.push(safeClone(r));
            }
        }
        for (const r of (Array.isArray(baseArr) ? baseArr : [])) {
            if (isPlainObject(r) && typeof r.regex === 'string' && !seen.has(r.regex)) {
                seen.add(r.regex);
                out.push(r);
            }
        }
        return out;
    }

    function mergeReviewPatterns(baseRP, overrideRP) {
        const merged = {};
        if (isPlainObject(baseRP)) {
            for (const [scope, map] of Object.entries(baseRP)) {
                if (UNSAFE_KEYS.has(scope) || !isPlainObject(map)) continue;
                merged[scope] = copyBaseMap(map, {});
            }
        }
        if (isPlainObject(overrideRP)) {
            for (const [scope, map] of Object.entries(overrideRP)) {
                if (UNSAFE_KEYS.has(scope) || !isPlainObject(map)) continue;
                if (!merged[scope]) merged[scope] = {};
                deepCopySafeMap(map, merged[scope]);
            }
        }
        return merged;
    }

    /** Produces merged dictionaries by overlaying overrides cleanly onto immutable base dicts. */
    function mergeDictionaries(base, rawOverrides) {
        const ov = isPlainObject(rawOverrides) ? rawOverrides : createEmptyOverrides();
        const tokens = deepCopySafeMap(ov.tokens, copyBaseMap(base.tokens, {}));

        const baseItems = isPlainObject(base.items?.items) ? base.items.items : (base.items || {});
        const items = copyBaseMap(baseItems, {});
        if (isPlainObject(ov.items)) {
            for (const [k, rec] of Object.entries(ov.items)) {
                if (UNSAFE_KEYS.has(k) || !isPlainObject(rec)) continue;
                items[k] = isPlainObject(items[k]) ? { ...items[k], ...safeClone(rec) } : safeClone(rec);
            }
        }

        const bm = base.menus || {};
        const om = ov.menus || {};
        const menus = {
            meta: bm.meta || {},
            titles: deepCopySafeMap(om.titles, copyBaseMap(bm.titles, {})),
            lines: deepCopySafeMap(om.lines, copyBaseMap(bm.lines, {})),
            people: deepCopySafeMap(om.people, copyBaseMap(bm.people, {})),
            patterns: mergePatternArrays(bm.patterns, om.patterns),
            review_patterns: mergeReviewPatterns(bm.review_patterns, om.review_patterns)
        };

        const bl = base.lore_lines || {};
        const ol = ov.lore_lines || {};
        const lore_lines = {
            meta: bl.meta || {},
            lines: deepCopySafeMap(ol.lines, copyBaseMap(bl.lines, {})),
            paragraphs: deepCopySafeMap(ol.paragraphs, copyBaseMap(bl.paragraphs, {})),
            paragraph_patterns: mergePatternArrays(bl.paragraph_patterns, ol.paragraph_patterns),
            review_patterns: mergeReviewPatterns(bl.review_patterns, ol.review_patterns)
        };

        const bd = base.dialogues || {};
        const od = ov.dialogues || {};
        const dialogues = {
            meta: bd.meta || {},
            dialogues: deepCopySafeMap(od.dialogues, copyBaseMap(bd.dialogues, {})),
            patterns: mergePatternArrays(bd.patterns, od.patterns),
            capture_token_sets: bd.capture_token_sets || {},
            review_patterns: mergeReviewPatterns(bd.review_patterns, od.review_patterns)
        };

        return {
            tokens,
            items,
            item_lore_en: base.item_lore_en || {},
            menus,
            lore_lines,
            dialogues
        };
    }

    // =========================================================================
    // Token Glossary Resolution & Tooltip Preview
    // =========================================================================

    function resolveTokenPlaceholders(text, tokens, visited = []) {
        if (!text || visited.length > 5 || !text.includes('{')) return text || '';
        return text.replace(GLOSSARY_REF_RE, (marker, id) => {
            const rec = Object.hasOwn(tokens, id) ? tokens[id] : null;
            if (!rec || visited.includes(id) || typeof rec.zh_tw !== 'string') return marker;
            return resolveTokenPlaceholders(rec.zh_tw, tokens, [...visited, id]);
        });
    }

    function previewItem(merged, itemName, loreLines) {
        const tokens = merged.tokens || {};
        const items = merged.items || {};
        const loreDict = merged.lore_lines || {};

        let translatedName = '';
        const cleanName = stripLegacyColor(itemName || '').trim();
        if (cleanName) {
            const byEn = Object.values(items).find(i => i && i.name_en && i.name_en.toLowerCase() === cleanName.toLowerCase());
            if (byEn && byEn.name_zh_tw) {
                translatedName = byEn.name_zh_tw;
            } else {
                const words = cleanName.split(/\s+/);
                const stars = words.filter(w => /^[✪➊➋➌➍➎]+$/.test(w)).join(' ');
                const coreWords = words.filter(w => !/^[✪➊➋➌➍➎]+$/.test(w));
                let reforgeZh = '';
                let body = coreWords.join(' ');
                if (coreWords.length >= 2) {
                    const first = coreWords[0].toLowerCase();
                    const refTok = Object.values(tokens).find(t => t.category === 'reforge' && t.en && t.en.toLowerCase() === first);
                    if (refTok) {
                        reforgeZh = `${refTok.color || ''}${refTok.zh_tw}`;
                        body = coreWords.slice(1).join(' ');
                    }
                }
                const bodyItem = Object.values(items).find(i => i && i.name_en && i.name_en.toLowerCase() === body.toLowerCase());
                let bodyZh = bodyItem?.name_zh_tw;
                if (!bodyZh) {
                    const bodyTok = Object.values(tokens).find(t => t.en && t.en.toLowerCase() === body.toLowerCase());
                    bodyZh = bodyTok ? bodyTok.zh_tw : body;
                }
                translatedName = [reforgeZh, bodyZh, stars ? `§6${stars}` : ''].filter(Boolean).join(' ');
            }
        }

        const outLore = (loreLines || []).map(raw => {
            const trimmed = stripLegacyColor(raw).trim();
            if (!trimmed) return '';
            if (loreDict.lines?.[trimmed]) return resolveTokenPlaceholders(String(loreDict.lines[trimmed]), tokens);
            if (loreDict.paragraphs?.[trimmed]) return resolveTokenPlaceholders(String(loreDict.paragraphs[trimmed]), tokens);

            const statM = trimmed.match(/^([A-Za-z ]+):\s*([+-]?\d[\d,.]*(?:%)?)$/);
            if (statM) {
                const statTok = Object.values(tokens).find(t => t.category === 'stat' && t.en && t.en.toLowerCase() === statM[1].toLowerCase());
                if (statTok) {
                    const col = statTok.color || '§c';
                    return `§7${statTok.zh_tw}：${col}${statM[2]}`;
                }
            }
            const abM = trimmed.match(/^Ability:\s*(.+?)\s{2,}(RIGHT CLICK|LEFT CLICK|SNEAK)$/);
            if (abM) {
                const act = abM[2] === 'RIGHT CLICK' ? '右鍵點擊' : abM[2] === 'LEFT CLICK' ? '左鍵點擊' : '蹲下觸發';
                return `§6技能：${abM[1]}  §e§l${act}`;
            }
            if (/^Mana Cost:\s*(\d+)/.test(trimmed)) {
                return `§8魔力消耗：§3${trimmed.match(/^Mana Cost:\s*(\d+)/)[1]}`;
            }
            for (const rule of (loreDict.paragraph_patterns || [])) {
                if (!rule?.regex || typeof rule.zh_tw !== 'string') continue;
                try {
                    const m = compileJavaRegex(rule.regex).exec(trimmed);
                    if (m) {
                        let zh = rule.zh_tw;
                        if (m.groups) {
                            for (const [g, val] of Object.entries(m.groups)) {
                                const mapped = rule.terms?.[g]?.[val] ?? val;
                                zh = zh.replace(new RegExp('\\$\\{' + escapeRegExp(g) + '\\}|%' + escapeRegExp(g) + ':"[^"]*"', 'g'), mapped);
                            }
                        }
                        return resolveTokenPlaceholders(zh, tokens);
                    }
                } catch { /* ignore incompatible PCRE rule */ }
            }
            let txt = trimmed;
            for (const tok of Object.values(tokens)) {
                if (tok.en && tok.zh_tw && tok.en.length > 2 && txt.toUpperCase().includes(tok.en.toUpperCase())) {
                    txt = txt.replace(new RegExp('\\b' + escapeRegExp(tok.en) + '\\b', 'gi'), `${tok.color || ''}${tok.zh_tw}`);
                }
            }
            return `§7${txt}`;
        });

        return { name: translatedName, lore: outLore };
    }

    // =========================================================================
    // Review Queue Indexer & Hashing (Exact TranslationReview Port)
    // =========================================================================

    function entryId(kind, target) {
        return digest(kind, target.key, target.role, target.choice);
    }

    function entryRevision(entry) {
        if (entry._rev) return entry._rev;
        const details = entry.dynamic ? JSON.stringify(entry.dynamic)
            : (entry.target.rule && !entry.target.role ? JSON.stringify(entry.record) : '');
        entry._rev = digest(entry.source, entry.value, entry.format, entry.context, details);
        return entry._rev;
    }

    function itemNameFor(item, key) {
        const n = str(item, 'name_en');
        return n.trim() ? n : key;
    }

    function createReviewIndex(merged) {
        const byId = new Map();
        const ordered = [];

        function put(dictionary, kind, target, source, context, value, format, record, dynamic = null) {
            const id = entryId(kind, target);
            const entry = {
                id, dictionary, kind, target, source, context, value, format, record, dynamic,
                _rev: null,
                markable: MARKABLE.has(kind) && !SOURCE_REF_RE.test(source)
            };
            if (!byId.has(id)) ordered.push(id);
            byId.set(id, entry);
            return entry;
        }

        // 1. ITEMS
        const itemLoreEn = merged.item_lore_en || {};
        for (const [key, item] of Object.entries(merged.items || {})) {
            if (!isPlainObject(item)) continue;
            const name = itemNameFor(item, key);
            put('ITEMS', 'itemName',
                { section: '', key, field: 'name_zh_tw', rule: false, role: '', choice: '' },
                name, key, str(item, 'name_zh_tw'), rarityColor(str(item, 'rarity')), item);
            if (Array.isArray(item.lore_template) && item.lore_template.length > 0) {
                put('ITEMS', 'itemLore',
                    { section: '', key, field: 'lore_template', rule: false, role: '', choice: '' },
                    str(itemLoreEn, key), name === key ? key : `${name} · ${key}`,
                    item.lore_template.join('\n'), '§7', item);
            }
        }

        // 2. MENUS, LORE, DIALOGUE, TOKENS
        function collectExact(dictionary, root, section, kind, allowFlat) {
            const values = isPlainObject(root?.[section]) ? root[section] : (allowFlat ? root : {});
            for (const [key, val] of Object.entries(values || {})) {
                if (RESERVED.has(key)) continue;
                if (typeof val === 'string') {
                    put(dictionary, kind, { section, key, field: '', rule: false, role: '', choice: '' }, key, '', val, '', null);
                } else if (allowFlat && isPlainObject(val)) {
                    const en = str(val, 'en');
                    put(dictionary, kind, { section, key, field: 'zh_tw', rule: false, role: '', choice: '' },
                        en || key, (!en || en === key) ? '' : key, str(val, 'zh_tw'), '', val);
                }
            }
            const rules = root?.review_patterns?.[section];
            if (!isPlainObject(rules) || !MARKABLE.has(kind)) return;
            for (const [key, rule] of Object.entries(rules)) {
                if (!isPlainObject(rule)) continue;
                let target = { section, key, field: '', rule: false, role: '', choice: '' };
                const baseEntry = byId.get(entryId(kind, target));
                if (baseEntry) target = baseEntry.target;
                put(dictionary, kind, target, str(rule, 'source'), baseEntry ? baseEntry.context : '',
                    str(rule, 'zh_tw'), '', baseEntry ? baseEntry.record : null, rule);
            }
        }

        function collectRules(dictionary, root, section, kind) {
            const arr = root?.[section];
            if (!Array.isArray(arr)) return;
            for (const rule of arr) {
                if (!isPlainObject(rule)) continue;
                const regex = str(rule, 'regex');
                if (!regex || !Object.hasOwn(rule, 'zh_tw')) continue;
                const target = { section, key: regex, field: 'zh_tw', rule: true, role: '', choice: '' };
                put(dictionary, kind, target, regex, '', str(rule, 'zh_tw'), '', rule);
                if (dictionary === 'LORE' && isPlainObject(rule.terms)) {
                    for (const [role, choices] of Object.entries(rule.terms)) {
                        if (!isPlainObject(choices)) continue;
                        for (const [choice, val] of Object.entries(choices)) {
                            put(dictionary, 'ruleTerm',
                                { section, key: regex, field: '', rule: true, role, choice },
                                choice, `${role} · ${regex}`, String(val), '', rule);
                        }
                    }
                }
            }
        }

        collectExact('MENUS', merged.menus, 'titles', 'uiName', false);
        collectExact('MENUS', merged.menus, 'lines', 'uiLine', false);
        collectExact('MENUS', merged.menus, 'people', 'person', false);
        collectRules('MENUS', merged.menus, 'patterns', 'uiRule');

        collectExact('LORE', merged.lore_lines, 'lines', 'loreLine', true);
        collectExact('LORE', merged.lore_lines, 'paragraphs', 'paragraph', false);
        collectRules('LORE', merged.lore_lines, 'paragraph_patterns', 'loreRule');

        collectExact('DIALOGUE', merged.dialogues, 'dialogues', 'dialogue', true);
        collectRules('DIALOGUE', merged.dialogues, 'patterns', 'dialogueRule');

        for (const [key, tok] of Object.entries(merged.tokens || {})) {
            if (!isPlainObject(tok)) continue;
            put('TOKENS', 'token', { section: '', key, field: 'zh_tw', rule: false, role: '', choice: '' },
                str(tok, 'en'), key, str(tok, 'zh_tw'), str(tok, 'color'), tok);
        }

        const entries = ordered.map(id => byId.get(id)).filter(Boolean);
        const byKind = new Map();
        for (const entry of entries) {
            let bucket = byKind.get(entry.kind);
            if (!bucket) {
                bucket = [];
                byKind.set(entry.kind, bucket);
            }
            bucket.push(entry);
        }

        function upsertEntry(entry) {
            const prev = byId.get(entry.id);
            if (!prev) {
                entries.push(entry);
                let bucket = byKind.get(entry.kind);
                if (!bucket) {
                    bucket = [];
                    byKind.set(entry.kind, bucket);
                }
                bucket.push(entry);
            } else {
                prev.value = entry.value;
                prev.record = entry.record;
                prev.dynamic = entry.dynamic;
                prev._rev = entry._rev ?? null;
                return prev;
            }
            byId.set(entry.id, entry);
            return entry;
        }

        function isVerified(entry, reviewStatus) {
            const saved = reviewStatus?.[entry.id];
            if (typeof saved !== 'string' || !saved) return false;
            if (saved === entryRevision(entry)) return true;
            if (entry.kind !== 'itemLore') return false;
            const key = entry.target.key;
            return saved === digest(itemNameFor(entry.record, key), entry.value, entry.format, key, '');
        }

        function matches(entry, kindFilter, query) {
            if (kindFilter && entry.kind !== kindFilter) return false;
            if (!query) return true;
            if (containsQuery(entry.source, query) || containsQuery(entry.value, query)
                || containsQuery(entry.context, query)
                || (entry.target.rule && containsQuery(str(entry.record, 'sample_en'), query))) {
                return true;
            }
            if (entry.dictionary === 'DIALOGUE' && entry.value.includes('{')) {
                return containsQuery(resolveTokenPlaceholders(entry.value, merged.tokens), query);
            }
            return false;
        }

        function toJson(entry, reviewStatus) {
            const item = {
                id: entry.id,
                kind: entry.kind,
                source: entry.source,
                context: entry.context,
                value: entry.value,
                format: entry.format,
                verified: isVerified(entry, reviewStatus),
                revision: entryRevision(entry),
                markable: entry.markable
            };
            if (entry.kind === 'itemLore') item.heading = itemNameFor(entry.record, entry.target.key);
            if (entry.target.rule && entry.record && typeof entry.record.sample_en === 'string') {
                item.sample = entry.record.sample_en;
                if (entry.record.capture_types) item.captureTypes = safeClone(entry.record.capture_types);
            }
            if (entry.dynamic?.roles) item.roles = safeClone(entry.dynamic.roles);
            return item;
        }

        function page(req, reviewStatus) {
            const query = str(req, 'query').trim().toLowerCase();
            const kind = str(req, 'kind');
            const status = str(req, 'status');
            const requestedPage = Math.max(0, Number(req.page) || 0);
            const selected = str(req, 'selected');

            const pool = kind ? (byKind.get(kind) || []) : entries;
            const results = [];
            let total = 0, checked = 0;
            for (const entry of pool) {
                if (query && !matches(entry, '', query)) continue;
                total++;
                const ver = isVerified(entry, reviewStatus);
                if (ver) checked++;
                if ((status === 'verified' && !ver) || (status === 'pending' && ver)) continue;
                results.push(entry);
            }
            const matching = results.length;
            const pg = Math.min(requestedPage, Math.floor(Math.max(0, matching - 1) / PAGE_SIZE));
            const start = pg * PAGE_SIZE;
            const rows = results.slice(start, start + PAGE_SIZE).map(e => toJson(e, reviewStatus));
            const resp = { rows, total, verified: checked, matching, page: pg, pageSize: PAGE_SIZE };
            if (selected) {
                const idx = results.findIndex(r => r.id === selected);
                if (idx >= 0) resp.selectedPage = Math.floor(idx / PAGE_SIZE);
            }
            return resp;
        }

        function nextPending(req, reviewStatus) {
            const after = str(req, 'after');
            const kind = str(req, 'kind');
            const query = str(req, 'query').trim().toLowerCase();
            const pool = kind ? (byKind.get(kind) || []) : entries;
            let first = null, next = null, passed = !after, idx = 0, nextIdx = 0;
            for (const entry of pool) {
                if (entry.id === after) {
                    passed = true;
                    if ((!query || matches(entry, '', query)) && !isVerified(entry, reviewStatus)) idx++;
                    continue;
                }
                if ((query && !matches(entry, '', query)) || isVerified(entry, reviewStatus)) continue;
                if (!first) first = entry;
                if (passed) { next = entry; nextIdx = idx; break; }
                idx++;
            }
            if (!next) next = first;
            return next ? { entry: toJson(next, reviewStatus), page: Math.floor(nextIdx / PAGE_SIZE) } : {};
        }

        function computeExcerpt(req, reviewStatus) {
            const item = byId.get(str(req, 'id'));
            if (!item || item.kind !== 'itemLore' || !item.source.trim()) {
                throw new Error('沒有可選取的道具原文。');
            }
            const raw = item.source;
            let start = Number(req.start), end = Number(req.end);
            if (!Number.isInteger(start) || !Number.isInteger(end) || start < 0 || start >= end || end > raw.length) {
                throw new Error('請先選取要翻譯的道具說明。');
            }
            let startLine = start === 0 ? 0 : raw.lastIndexOf('\n', start - 1) + 1;
            let endLine = raw.indexOf('\n', end - 1);
            if (endLine < 0) endLine = raw.length;
            if (req.sentence && raw.indexOf('\n', startLine) === (endLine === raw.length ? -1 : endLine)) {
                [startLine, endLine] = wrappedSentence(raw, startLine, endLine);
            }
            const src = raw.slice(startLine, endLine);
            const key = cleanSource(src, true).trim();
            const srcLines = src.split(/\r?\n/);
            if (!key || key.length > 300 || srcLines.length > 6 || srcLines.some(l => !cleanSource(l, true).trim())) {
                throw new Error('請選取同一段的 1–6 行說明，排除空白行。');
            }
            const target = { section: 'paragraphs', key, field: '', rule: false, role: '', choice: '' };
            const id = entryId('paragraph', target);
            const existing = byId.get(id);
            const draft = existing || {
                id, dictionary: 'LORE', kind: 'paragraph', target, source: src, context: '',
                value: merged.lore_lines?.paragraphs?.[key] || merged.lore_lines?.lines?.[key] || '',
                format: '', record: null, dynamic: null, _rev: null, markable: !SOURCE_REF_RE.test(src)
            };
            return { ...toJson(draft, reviewStatus), excerpt: !existing, sourceStart: startLine, sourceEnd: endLine };
        }

        return {
            entries,
            byId,
            page,
            next: nextPending,
            excerpt: computeExcerpt,
            isVerified,
            toJson,
            entryRevision,
            upsertEntry,
            get size() { return entries.length; },
            lookup: id => byId.get(id) || null
        };
    }

    function wrappedSentence(raw, start, end) {
        let from = start, to = end, count = 1;
        let first = raw.slice(start, end);
        while (from > 0 && count < 6) {
            const prev = raw.lastIndexOf('\n', from - 2) + 1;
            const line = raw.slice(prev, from - 1);
            if (!continuesSentence(line, first)) break;
            from = prev; first = line; count++;
        }
        let last = raw.slice(start, end);
        while (to < raw.length && count < 6) {
            let next = raw.indexOf('\n', to + 1);
            if (next < 0) next = raw.length;
            const line = raw.slice(to + 1, next);
            if (!continuesSentence(last, line)) break;
            to = next; last = line; count++;
        }
        if (cleanSource(raw.slice(from, to), true).trim().length > 300) return [start, end];
        return [from, to];
    }

    function continuesSentence(first, second) {
        const left = cleanSource(first, true).trim();
        const right = cleanSource(second, true).trim();
        return !!left && !!right && !'.!?…:：'.includes(left[left.length - 1])
            && !TOOLTIP_HEADING_RE.test(left) && !TOOLTIP_HEADING_RE.test(right);
    }

    // =========================================================================
    // Workbench & Review Rule Compilation (Exact TranslationWorkbench Port)
    // =========================================================================

    function cleanSource(text, lore) {
        const plain = String(text || '').replace(LEGACY_CODE_RE, '').replace(/[\s\u00A0\u2000-\u200B\u3000]+/gu, ' ');
        return lore ? plain.replace(/’/g, "'") : plain;
    }

    function splitsNumber(raw, lore, start, end) {
        const before = cleanSource(raw.slice(0, start), lore);
        const after = cleanSource(raw.slice(end), lore);
        const last = before.length - 1;
        if ((last >= 0 && /\d/.test(before[last])) || (after.length > 0 && /\d/.test(after[0]))) return true;
        return (last >= 0 && '+-'.includes(before[last]))
            || (last > 0 && '.,'.includes(before[last]) && /\d/.test(before[last - 1]))
            || (after.length > 1 && '.,'.includes(after[0]) && /\d/.test(after[1]));
    }

    function compileRuleEntry(kindName, raw, translation, rolesInput, review, importedRoles) {
        const lore = kindName === 'paragraph' || kindName === 'LORE';
        const src = cleanSource(raw, lore).trim();
        if (!src || raw.length > 1200 || src.length > 300 || raw.includes('\0')) {
            throw new Error('原文需有 1–300 字元（空白會合併）。');
        }
        if (review && SOURCE_REF_RE.test(src)) {
            throw new Error('請使用不含既有參數的英文原文，原文最多 300 字元。');
        }
        if (lore) {
            const lines = raw.trim().split(/\r?\n/);
            if (lines.length > 6 || lines.some(l => !cleanSource(l, true).trim())) {
                throw new Error('請一次處理一段，保留原有換行，最多六行且不含空行。');
            }
        }
        const trans = review ? String(translation || '') : String(translation || '').trim();
        if (!trans.trim() || trans.length > 1200 || trans.includes('\0') || trans.split(/\r?\n/).length > 1) {
            throw new Error('請輸入完整譯文（單一段落，最多 1,200 字元）。');
        }
        const rawRoles = Array.isArray(rolesInput) ? rolesInput : [];
        if (rawRoles.length > 12 || (kindName === 'title' && rawRoles.length > 0)) {
            throw new Error('標題使用固定對照，其他用途最多標記 12 個 token。');
        }

        const roles = [];
        const ids = new Set();
        for (const r of rawRoles) {
            if (!isPlainObject(r)) throw new Error('token 物件格式無效。');
            const id = str(r, 'id'), type = str(r, 'type');
            const start = Number(r.start), end = Number(r.end);
            if (!ROLE_ID_RE.test(id) || UNSAFE_KEYS.has(id) || ids.has(id)
                || !Number.isInteger(start) || !Number.isInteger(end) || start < 0 || end > raw.length || start >= end
                || !['number', 'fixed', 'item', 'npc'].includes(type)
                || (!review && type !== 'number' && type !== 'fixed')) {
                throw new Error('token 名稱、類型或原文範圍無效。');
            }
            ids.add(id);
            const selected = cleanSource(raw.slice(start, end), lore);
            const localized = str(r, 'localized').trim();
            const isNum = type === 'number';
            if (!selected.trim() || selected !== selected.trim() || selected.length > 90
                || (isNum && (!NUMBER_RE.test(selected) || (!importedRoles && splitsNumber(raw, lore, start, end))))
                || (localized && (isNum || (!review && kindName !== 'paragraph')))
                || localized.length > 120 || localized.includes('\n') || localized.includes('\0')) {
                throw new Error('請選取完整數值或名稱。固定 token 的自訂譯名適用於說明段落。');
            }
            if ((type === 'item' || type === 'npc') && !ENTITY_RE.test(selected)) {
                throw new Error('請完整選取英文物品名或 NPC 名稱。');
            }
            roles.push({ id, start, end, type, value: selected, localized });
        }

        const used = new Set();
        const samples = new Map();
        const rest = trans.replace(NAMED_REFERENCE_RE, (_, legacy, typed, sampleJson) => {
            const name = legacy || typed;
            if (!ids.has(name) || used.has(name)) {
                throw new Error('每個 token 必須存在且只引用一次。');
            }
            used.add(name);
            const sample = sampleJson ? JSON.parse(sampleJson) : '';
            if (sample.length > 120 || sample.includes('\0') || sample.includes('\n')) {
                throw new Error('Token 的範例值限單行且最多 120 字元。');
            }
            if (sample.trim()) samples.set(name, sample);
            return '';
        });
        if (used.size !== ids.size || UNSUPPORTED_REF_RE.test(rest)) {
            throw new Error('譯文缺少 token，或包含尚未標記的參數。');
        }
        if (roles.length === 0) return { source: src, exact: trans, rule: null };

        roles.sort((a, b) => a.start - b.start);
        let pattern = '^';
        const terms = {};
        const types = {};
        let pos = 0;
        for (const role of roles) {
            if (role.start < pos) throw new Error('token 的原文範圍互相重疊。');
            const before = cleanSource(raw.slice(pos, role.start), lore);
            const segment = pos === 0 ? before.trimStart() : before;
            const sub = role.type === 'number' ? '[+-]?[0-9]{1,16}(?:,[0-9]{3}){0,4}(?:\\.[0-9]{1,8})?[kKmMbB]?%?'
                : (role.type === 'npc' || role.type === 'item') ? "[\\p{L}\\p{M}\\p{N}][\\p{L}\\p{M}\\p{N} .’'_&/\\-]{0,88}?"
                : quoteRegexLiteral(role.value);
            pattern += quoteRegexLiteral(segment) + `(?<${role.id}>${sub})`;
            if (role.type !== 'fixed' && role.type !== 'number') types[role.id] = role.type;
            const sample = samples.has(role.id) ? samples.get(role.id) : role.localized;
            if (role.type !== 'number' && (review || kindName === 'paragraph') && sample && sample.toLowerCase() !== role.value.toLowerCase()) {
                terms[role.id] = { [role.value]: sample };
            }
            pos = role.end;
        }
        pattern += quoteRegexLiteral(cleanSource(raw.slice(pos), lore).trimEnd()) + '$';

        const jsRegex = compileJavaRegex(pattern);
        if (!jsRegex.test(src)) {
            throw new Error('token 範圍和標準化原文對不上，請重新標記。');
        }
        const rule = { regex: pattern, zh_tw: trans, verbatim: true };
        if (Object.keys(terms).length) rule.terms = terms;
        if (Object.keys(types).length) rule.capture_types = types;
        return { source: src, exact: null, rule };
    }

    function previewWorkbenchDraft(merged, kindName, raw, translation, rolesInput) {
        const compiled = compileRuleEntry(kindName, raw, translation, rolesInput, false, false);
        const tokens = merged.tokens || {};
        if (!compiled.rule) {
            return { preview: resolveTokenPlaceholders(compiled.exact, tokens), compiled };
        }
        const m = compileJavaRegex(compiled.rule.regex).exec(compiled.source);
        if (!m) throw new Error('無法預覽此條翻譯，請檢查原文與 token 範圍。');
        let rendered = compiled.exact || compiled.rule.zh_tw;
        rendered = rendered.replace(NAMED_REFERENCE_RE, (_, legacy, typed, sampleJson) => {
            const name = legacy || typed;
            const cap = m.groups?.[name] ?? '';
            const role = (rolesInput || []).find(r => r.id === name);
            if (role?.type === 'number') return cap;
            return (sampleJson && JSON.parse(sampleJson)) || compiled.rule.terms?.[name]?.[cap] || role?.localized || cap;
        });
        return { preview: resolveTokenPlaceholders(rendered, tokens), compiled };
    }

    // =========================================================================
    // Transactional Cloud DB Operation Applier (Overrides + Review + Contributors)
    // =========================================================================

    function ensureSection(obj, key) {
        assertSafeKey(key);
        if (!isPlainObject(obj[key])) obj[key] = {};
        return obj[key];
    }

    function upsertRuleArray(arr, newRule) {
        const out = [safeClone(newRule)];
        for (const r of (Array.isArray(arr) ? arr : [])) {
            if (isPlainObject(r) && r.regex !== newRule.regex) out.push(r);
        }
        return out;
    }

    /** Applies one validated operation atomically to cloned overrides + reviewStatus, with optional cached merged/index reuse. */
    function applyOperation(overrides, reviewStatus, op, baseDicts, ctx = null) {
        if (!isPlainObject(op) || !op.type) throw new Error('無效的操作格式。');
        const nextOv = safeClone(overrides || createEmptyOverrides());
        const nextReview = safeClone(reviewStatus || {});
        const now = new Date().toISOString();

        const changed = [];
        const verifiedEntryIds = [];
        let editCount = 0;
        let reviewPatch = null;

        const applyTokenRecord = (rawTok) => {
            const id = str(rawTok, 'tokenId').trim().toUpperCase();
            assertSafeKey(id, 'tokenId');
            if (!/^[A-Z0-9_]{1,80}$/.test(id)) throw new Error(`Token ID 格式不合規範: ${id}`);
            const zh_tw = str(rawTok, 'zh_tw').trim();
            if (!zh_tw || zh_tw.includes('\0')) throw new Error('繁體中文翻譯為必填項目。');
            const en = str(rawTok, 'en').trim();
            const cat = str(rawTok, 'category').trim().toLowerCase() || 'general';
            const category = VALID_TOKEN_CATS.has(cat) ? cat : 'general';
            const color = str(rawTok, 'color').trim();
            const glyph = str(rawTok, 'glyph').trim();
            ensureSection(nextOv, 'tokens')[id] = { tokenId: id, en, zh_tw, glyph, color, category };
            editCount++;
            changed.push('overrides.json');
        };

        if (op.type === 'token') {
            applyTokenRecord(op.token);
        } else if (op.type === 'tokens_batch') {
            if (!Array.isArray(op.tokens) || !op.tokens.length || op.tokens.length > 2000) {
                throw new Error('批次詞彙數量須介於 1–2000 之間。');
            }
            for (const t of op.tokens) applyTokenRecord(t);
        } else if (op.type === 'item') {
            const it = op.item || {};
            const id = str(it, 'id').trim();
            assertSafeKey(id, 'item.id');
            const name_zh_tw = str(it, 'name_zh_tw').trim();
            if (!name_zh_tw || name_zh_tw.includes('\0')) throw new Error('道具繁體中文名稱為必填項目。');
            const merged = ctx?.merged || mergeDictionaries(baseDicts, nextOv);
            const prev = merged.items[id] || {};
            ensureSection(nextOv, 'items')[id] = {
                id,
                name_en: str(it, 'name_en') || prev.name_en || id,
                name_zh_tw,
                category: str(it, 'category') || prev.category || 'MISC',
                rarity: str(it, 'rarity') || prev.rarity || 'COMMON',
                lore_template: Array.isArray(it.lore_template) ? it.lore_template.map(String) : (prev.lore_template || [])
            };
            editCount++;
            changed.push('overrides.json');
        } else if (op.type === 'workbench') {
            const p = op.payload || {};
            const kind = str(p, 'kind');
            const raw = str(p, 'source');
            const trans = str(p, 'translation');
            const map = {
                paragraph: { dict: 'lore_lines', exact: 'paragraphs', rules: 'paragraph_patterns' },
                menu: { dict: 'menus', exact: 'lines', rules: 'patterns' },
                title: { dict: 'menus', exact: 'titles', rules: 'patterns' },
                dialogue: { dict: 'dialogues', exact: 'dialogues', rules: 'patterns' }
            }[kind];
            if (!map) throw new Error('請選擇有效的文字用途。');
            const compiled = compileRuleEntry(kind, raw, trans, p.roles || [], false, false);
            const targetDict = ensureSection(nextOv, map.dict);
            if (!compiled.rule) {
                ensureSection(targetDict, map.exact)[compiled.source] = compiled.exact;
            } else {
                targetDict[map.rules] = upsertRuleArray(targetDict[map.rules], compiled.rule);
            }
            editCount++;
            changed.push('overrides.json');
        } else if (op.type === 'review') {
            const mergedBefore = ctx?.merged || mergeDictionaries(baseDicts, nextOv);
            const revIndex = ctx?.reviewIndex || createReviewIndex(mergedBefore);
            const r = op.review || {};
            const id = str(r, 'id');
            let entry = revIndex.lookup(id);
            let newExcerpt = false;
            if (!entry && isPlainObject(r.excerpt)) {
                const ex = revIndex.excerpt({ ...r.excerpt, sentence: true }, nextReview);
                entry = {
                    id: ex.id, dictionary: 'LORE', kind: 'paragraph',
                    target: { section: 'paragraphs', key: cleanSource(ex.source, true).trim(), field: '', rule: false, role: '', choice: '' },
                    source: ex.source, context: '', value: ex.value, format: '', record: null, dynamic: null, _rev: ex.revision, markable: ex.markable
                };
                newExcerpt = true;
            }
            if (!entry || entry.id !== id) throw new Error('找不到審核條目或條目已變更，請重新同步後再試。');
            if (r.revision && revIndex.entryRevision(entry) !== r.revision) {
                throw new Error('這筆譯文已被其他貢獻者更新，請先重新同步最新雲端資料。');
            }
            const value = str(r, 'value');
            if (!value || value.includes('\0') || !stripLegacyColor(value).trim()) {
                throw new Error('請填寫有效的譯文。');
            }
            if (entry.dictionary === 'DIALOGUE') {
                for (const m of value.matchAll(GLOSSARY_REF_RE)) {
                    if (!mergedBefore.tokens[m[1]] && !entry.value.includes(m[0])) {
                        throw new Error(`統一名詞表沒有 {${m[1]}}。`);
                    }
                }
            }
            const roles = Array.isArray(r.roles) ? r.roles : (entry.dynamic?.roles ? safeClone(entry.dynamic.roles) : []);
            if ((roles.length > 0 && !entry.markable) || (newExcerpt && !roles.length)) {
                throw new Error('請在可標記的原文中加入語意 token，並在譯文引用。');
            }
            if (!roles.length && entry.markable && new RegExp(NAMED_REFERENCE_RE.source).test(value)) {
                throw new Error('請先在原文標記譯文所引用的 token。');
            }

            let dynamic = null;
            if (roles.length > 0) {
                const existingDyn = entry.dynamic;
                const sameRoles = existingDyn && JSON.stringify(roles) === JSON.stringify(existingDyn.roles);
                if (sameRoles && value === entry.value) {
                    dynamic = safeClone(existingDyn);
                } else {
                    const importedRoles = !!(sameRoles && existingDyn.sample_en);
                    const compiled = compileRuleEntry(
                        entry.dictionary === 'LORE' ? 'paragraph' : 'menu',
                        entry.source, value, roles, true, importedRoles);
                    const newRule = { ...compiled.rule, source: entry.source, roles: safeClone(roles) };
                    if (importedRoles) {
                        dynamic = safeClone(existingDyn);
                        dynamic.zh_tw = value;
                        if (newRule.terms) {
                            dynamic.terms = dynamic.terms || {};
                            for (const [roleK, choices] of Object.entries(newRule.terms)) {
                                dynamic.terms[roleK] = { ...(dynamic.terms[roleK] || {}), ...choices };
                            }
                        }
                    } else {
                        dynamic = newRule;
                    }
                }
            }

            let updatedRecord = entry.record;
            const changedValue = newExcerpt || entry.value !== value || JSON.stringify(dynamic) !== JSON.stringify(entry.dynamic);
            if (changedValue) {
                editCount++;
                changed.push('overrides.json');
                const target = entry.target;
                const dictKeyMap = { ITEMS: 'items', TOKENS: 'tokens', MENUS: 'menus', LORE: 'lore_lines', DIALOGUE: 'dialogues' };
                const dKey = dictKeyMap[entry.dictionary];
                const dObj = ensureSection(nextOv, dKey);

                if (dynamic || entry.dynamic) {
                    const rp = ensureSection(dObj, 'review_patterns');
                    const scopeMap = ensureSection(rp, target.section || 'root');
                    if (dynamic) {
                        assertSafeKey(target.key);
                        scopeMap[target.key] = dynamic;
                    } else {
                        delete scopeMap[target.key];
                    }
                }
                if (!dynamic) {
                    if (entry.dictionary === 'ITEMS') {
                        const prev = mergedBefore.items[target.key] || {};
                        const nextItem = safeClone(prev);
                        assertSafeKey(target.key);
                        if (entry.kind === 'itemLore') {
                            nextItem.lore_template = value.split('\n');
                        } else {
                            nextItem.name_zh_tw = value;
                        }
                        dObj[target.key] = nextItem;
                        updatedRecord = nextItem;
                    } else if (entry.dictionary === 'TOKENS') {
                        const prev = mergedBefore.tokens[target.key] || {};
                        assertSafeKey(target.key);
                        const nextTok = { ...safeClone(prev), zh_tw: value };
                        dObj[target.key] = nextTok;
                        updatedRecord = nextTok;
                    } else if (target.rule) {
                        const arr = Array.isArray(dObj[target.section]) ? [...dObj[target.section]] : [];
                        let ruleObj = arr.find(x => x && x.regex === target.key);
                        if (!ruleObj) {
                            ruleObj = safeClone(entry.record);
                            arr.unshift(ruleObj);
                        }
                        if (!target.role) ruleObj[target.field || 'zh_tw'] = value;
                        else ensureSection(ensureSection(ruleObj, 'terms'), target.role)[target.choice] = value;
                        dObj[target.section] = arr;
                        updatedRecord = ruleObj;
                    } else {
                        const container = target.section ? ensureSection(dObj, target.section) : dObj;
                        assertSafeKey(target.key);
                        if (!target.field) {
                            container[target.key] = value;
                        } else {
                            const rec = safeClone(entry.record || {});
                            rec[target.field] = value;
                            container[target.key] = rec;
                            updatedRecord = rec;
                        }
                    }
                }
            }

            const updatedEntry = changedValue
                ? { ...entry, value, dynamic, record: updatedRecord, _rev: null }
                : entry;
            if (r.verify) {
                nextReview[id] = entryRevision(updatedEntry);
                verifiedEntryIds.push(id);
                changed.push('review_status.json');
            }
            reviewPatch = { entry: updatedEntry, newExcerpt, changedValue };
        } else {
            throw new Error(`不支援的操作類型: ${op.type}`);
        }

        if (changed.includes('overrides.json')) {
            nextOv.updatedAt = now;
        }
        return {
            overrides: nextOv,
            reviewStatus: nextReview,
            changedFiles: [...new Set(changed)],
            verifiedEntryIds,
            editCount,
            reviewPatch
        };
    }

    /** Patches an existing merged + reviewIndex in-place after applyOperation, avoiding full 113k-entry rebuilds for single review ops. */
    function applyMergedPatch(merged, reviewIndex, opResult) {
        if (!merged || !reviewIndex || !opResult?.reviewPatch) return false;
        const { entry, changedValue, newExcerpt } = opResult.reviewPatch;
        if (!entry) return false;
        if (!changedValue) return true;

        const target = entry.target;
        const dictKeyMap = { ITEMS: 'items', TOKENS: 'tokens', MENUS: 'menus', LORE: 'lore_lines', DIALOGUE: 'dialogues' };
        const dKey = dictKeyMap[entry.dictionary];
        const dObj = merged[dKey];
        if (!dObj) return false;

        if (entry.dynamic) {
            const rp = ensureSection(dObj, 'review_patterns');
            const scopeMap = ensureSection(rp, target.section || 'root');
            scopeMap[target.key] = safeClone(entry.dynamic);
        } else {
            if (dObj.review_patterns?.[target.section || 'root'] && Object.hasOwn(dObj.review_patterns[target.section || 'root'], target.key)) {
                delete dObj.review_patterns[target.section || 'root'][target.key];
            }
            if (entry.dictionary === 'ITEMS') {
                dObj[target.key] = safeClone(entry.record);
            } else if (entry.dictionary === 'TOKENS') {
                dObj[target.key] = safeClone(entry.record);
            } else if (target.rule) {
                const arr = Array.isArray(dObj[target.section]) ? dObj[target.section] : [];
                const idx = arr.findIndex(x => x && x.regex === target.key);
                if (idx >= 0) arr[idx] = safeClone(entry.record);
                else arr.unshift(safeClone(entry.record));
                dObj[target.section] = arr;
            } else {
                const container = target.section ? ensureSection(dObj, target.section) : dObj;
                if (!target.field) container[target.key] = entry.value;
                else container[target.key] = safeClone(entry.record);
            }
        }

        if (newExcerpt || !entry.target.rule) {
            reviewIndex.upsertEntry(entry);
            return true;
        }
        reviewIndex.upsertEntry(entry);
        // Rule edits may share entry.record with child ruleTerm entries; keep ruleTerm records synced
        for (const child of reviewIndex.entries) {
            if (child.target.rule && child.target.key === target.key && child.target.section === target.section && child.id !== entry.id) {
                child.record = entry.record;
            }
        }
        return true;
    }

    function updateContributors(contribDoc, actor, verifiedEntryIds, editCount) {
        const doc = safeClone(contribDoc || { version: 1, threshold: 25, updatedAt: '', contributors: {} });
        if (typeof doc.threshold !== 'number') doc.threshold = 25;
        if (!isPlainObject(doc.contributors)) doc.contributors = {};
        const login = str(actor, 'login').trim();
        if (!login || UNSAFE_KEYS.has(login)) throw new Error('無效的 GitHub 帳號識別。');

        const existing = doc.contributors[login] || {
            login,
            avatarUrl: str(actor, 'avatarUrl'),
            htmlUrl: str(actor, 'htmlUrl') || `https://github.com/${login}`,
            verifiedIds: [],
            verifiedCount: 0,
            editCount: 0,
            firstContributedAt: new Date().toISOString(),
            lastContributedAt: new Date().toISOString()
        };
        if (str(actor, 'avatarUrl')) existing.avatarUrl = str(actor, 'avatarUrl');
        if (str(actor, 'htmlUrl')) existing.htmlUrl = str(actor, 'htmlUrl');
        const idSet = new Set(Array.isArray(existing.verifiedIds) ? existing.verifiedIds : []);
        for (const vid of verifiedEntryIds) {
            if (typeof vid === 'string' && /^[a-f0-9]{64}$/.test(vid)) idSet.add(vid);
        }
        existing.verifiedIds = [...idSet];
        existing.verifiedCount = existing.verifiedIds.length;
        existing.editCount = (Number(existing.editCount) || 0) + (Number(editCount) || 0);
        existing.lastContributedAt = new Date().toISOString();
        doc.contributors[login] = existing;
        doc.updatedAt = new Date().toISOString();
        return doc;
    }

    // =========================================================================
    // RFC 4180 CSV Export & Import
    // =========================================================================

    function csvEscape(v) {
        return '"' + String(v ?? '').replace(/"/g, '""') + '"';
    }

    function exportTokensCsv(tokens) {
        const rows = ['"token_id","en","zh_tw","category"'];
        for (const id of Object.keys(tokens || {}).sort()) {
            const t = tokens[id];
            rows.push([csvEscape(t.tokenId || id), csvEscape(t.en || ''), csvEscape(t.zh_tw || ''), csvEscape(t.category || 'general')].join(','));
        }
        return rows.join('\n') + '\n';
    }

    function parseTokensCsv(csvText) {
        const rows = [];
        let curRow = [], curField = '', inQuotes = false;
        const src = String(csvText || '');
        for (let i = 0; i < src.length; i++) {
            const c = src[i];
            if (inQuotes) {
                if (c === '"') {
                    if (src[i + 1] === '"') { curField += '"'; i++; }
                    else inQuotes = false;
                } else {
                    curField += c;
                }
            } else if (c === '"') {
                inQuotes = true;
            } else if (c === ',') {
                curRow.push(curField);
                curField = '';
            } else if (c === '\r' || c === '\n') {
                if (c === '\r' && src[i + 1] === '\n') i++;
                curRow.push(curField);
                if (curRow.some(f => f.trim())) rows.push(curRow);
                curRow = [];
                curField = '';
            } else {
                curField += c;
            }
        }
        if (inQuotes) throw new Error('CSV 格式錯誤：存在未閉合的引號。');
        curRow.push(curField);
        if (curRow.some(f => f.trim())) rows.push(curRow);
        if (rows.length < 2) throw new Error('CSV 內容為空或缺少資料列。');

        const parsed = [];
        for (let r = 1; r < rows.length; r++) {
            const cols = rows[r];
            if (cols.length < 3) throw new Error(`第 ${r + 1} 行欄位不足（需至少 token_id, en, zh_tw）。`);
            const tokenId = cols[0].trim().toUpperCase();
            const en = cols[1].trim();
            const zh_tw = cols[2].trim();
            const category = (cols[3] || 'general').trim().toLowerCase() || 'general';
            if (!tokenId || !zh_tw) continue;
            parsed.push({ tokenId, en, zh_tw, category });
        }
        if (!parsed.length) throw new Error('CSV 中沒有任何有效詞條。');
        return parsed;
    }

    return {
        PAGE_SIZE,
        digest,
        createEmptyOverrides,
        mergeDictionaries,
        createReviewIndex,
        previewItem,
        previewWorkbenchDraft,
        compileRuleEntry,
        applyOperation,
        applyMergedPatch,
        updateContributors,
        exportTokensCsv,
        parseTokensCsv,
        resolveTokenPlaceholders,
        stripLegacyColor
    };
}));
