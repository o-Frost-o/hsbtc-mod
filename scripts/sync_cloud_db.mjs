#!/usr/bin/env node
/**
 * Manual sync script for merging standalone Cloud DB (`docs/data/overrides.json` and
 * `docs/data/review_status.json`) back into Mod resource dictionaries and `docs/data/base/`.
 *
 * Usage:
 *   node scripts/sync_cloud_db.mjs [--reset-overrides] [--sync-prism]
 */
'use strict';

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { createRequire } from 'module';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const require = createRequire(import.meta.url);
const engine = require('../docs/hsbtr-engine.js');

const ROOT = path.resolve(__dirname, '..');
const DATA_DIR = path.join(ROOT, 'docs', 'data');
const BASE_DIR = path.join(DATA_DIR, 'base');
const MOD_DICT_DIR = path.join(ROOT, 'src', 'main', 'resources', 'assets', 'hsbtr', 'dictionaries');
const PRISM_DICT_DIR = fs.existsSync('D:\\PrismLauncher\\instances\\26.2\\minecraft\\config\\hypixel_skyblock_translation')
    ? 'D:\\PrismLauncher\\instances\\26.2\\minecraft\\config\\hypixel_skyblock_translation'
    : 'C:\\Users\\User\\AppData\\Roaming\\PrismLauncher\\instances\\26.2\\minecraft\\config\\hypixel_skyblock_translation';

const args = new Set(process.argv.slice(2));
const resetOverrides = args.has('--reset-overrides');
const syncPrism = args.has('--sync-prism');

const readJson = (p) => JSON.parse(fs.readFileSync(p, 'utf8'));
const writeJson = (p, data) => fs.writeFileSync(p, JSON.stringify(data, null, 2) + '\n', 'utf8');

function main() {
    const base = {
        tokens: readJson(path.join(BASE_DIR, 'tokens.json')),
        items: readJson(path.join(BASE_DIR, 'items.json')),
        item_lore_en: readJson(path.join(BASE_DIR, 'item_lore_en.json')),
        lore_lines: readJson(path.join(BASE_DIR, 'lore_lines.json')),
        menus: readJson(path.join(BASE_DIR, 'menus.json')),
        dialogues: readJson(path.join(BASE_DIR, 'dialogues.json'))
    };
    const overrides = readJson(path.join(DATA_DIR, 'overrides.json'));
    const reviewStatus = readJson(path.join(DATA_DIR, 'review_status.json'));

    const merged = engine.mergeDictionaries(base, overrides);

    writeJson(path.join(MOD_DICT_DIR, 'tokens.json'), merged.tokens);
    writeJson(path.join(MOD_DICT_DIR, 'items.json'), merged.items);
    writeJson(path.join(MOD_DICT_DIR, 'menus.json'), merged.menus);
    writeJson(path.join(MOD_DICT_DIR, 'lore_lines.json'), merged.lore_lines);
    writeJson(path.join(MOD_DICT_DIR, 'dialogues.json'), merged.dialogues);

    for (const f of ['tokens.json', 'items.json', 'menus.json', 'lore_lines.json', 'dialogues.json']) {
        fs.copyFileSync(path.join(MOD_DICT_DIR, f), path.join(BASE_DIR, f));
    }

    if (resetOverrides) {
        writeJson(path.join(DATA_DIR, 'overrides.json'), engine.createEmptyOverrides());
    }

    if (syncPrism && fs.existsSync(PRISM_DICT_DIR)) {
        for (const f of ['tokens.json', 'items.json', 'menus.json', 'lore_lines.json', 'dialogues.json']) {
            fs.copyFileSync(path.join(MOD_DICT_DIR, f), path.join(PRISM_DICT_DIR, f));
        }
        writeJson(path.join(PRISM_DICT_DIR, 'review_status.json'), reviewStatus);
    }

    console.log('[sync_cloud_db] Merged Cloud DB overrides into Mod dictionaries successfully.');
    console.log(`  Tokens: ${Object.keys(merged.tokens).length}`);
    console.log(`  Items: ${Object.keys(merged.items).length}`);
    console.log(`  Verified review entries: ${Object.keys(reviewStatus).length}`);
}

main();
