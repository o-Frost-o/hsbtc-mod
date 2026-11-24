#!/usr/bin/env node
/**
 * GitHub Actions IssueOps Processor for HSBTR Shared Cloud DB.
 * Reads GitHub issue event payload, validates translation operations using HsbtrEngine,
 * attributes verification ONLY to github.event.issue.user.login, updates docs/data/,
 * and emits step outputs/comment summary for the workflow.
 */
'use strict';

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { createRequire } from 'module';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ROOT = path.resolve(__dirname, '../..');
const DATA_DIR = path.join(ROOT, 'docs', 'data');
const BASE_DIR = path.join(DATA_DIR, 'base');

const require = createRequire(import.meta.url);
const engine = require(path.join(ROOT, 'docs', 'hsbtr-engine.js'));

const readJson = (p) => JSON.parse(fs.readFileSync(p, 'utf8'));
const writeJson = (p, d) => fs.writeFileSync(p, JSON.stringify(d, null, 2) + '\n', 'utf8');

function setOutput(name, value) {
    const outFile = process.env.GITHUB_OUTPUT;
    if (!outFile) return;
    const delim = 'EOF_' + Math.random().toString(36).slice(2);
    fs.appendFileSync(outFile, `${name}<<${delim}\n${value}\n${delim}\n`, 'utf8');
}

function failSubmission(reason) {
    console.error('[apply_submission] Rejected:', reason);
    setOutput('status', 'rejected');
    setOutput('committed', 'false');
    setOutput('message', reason);
    process.exit(0);
}

function main() {
    const eventPath = process.env.GITHUB_EVENT_PATH;
    if (!eventPath || !fs.existsSync(eventPath)) {
        throw new Error('Missing GITHUB_EVENT_PATH');
    }
    const event = readJson(eventPath);
    const issue = event.issue;
    if (!issue || typeof issue.body !== 'string') {
        failSubmission('Issue 內容格式不完整。');
    }

    const match = issue.body.match(/<!--\s*hsbtr-submission:v1\s*([\s\S]*?)-->/);
    if (!match) {
        failSubmission('找不到有效的 `<!-- hsbtr-submission:v1 ... -->` 資料區塊，無法自動合併。');
    }

    let submission;
    try {
        submission = JSON.parse(match[1].trim());
    } catch (e) {
        failSubmission(`JSON 解析失敗：${e.message}`);
    }

    if (!submission || !Array.isArray(submission.ops) || !submission.ops.length || submission.ops.length > 20) {
        failSubmission('操作陣列為空或單次提交超過上限 (20 筆)。');
    }

    const base = {
        tokens: readJson(path.join(BASE_DIR, 'tokens.json')),
        items: readJson(path.join(BASE_DIR, 'items.json')),
        item_lore_en: readJson(path.join(BASE_DIR, 'item_lore_en.json')),
        lore_lines: readJson(path.join(BASE_DIR, 'lore_lines.json')),
        menus: readJson(path.join(BASE_DIR, 'menus.json')),
        dialogues: readJson(path.join(BASE_DIR, 'dialogues.json'))
    };

    let ov = readJson(path.join(DATA_DIR, 'overrides.json'));
    let rs = readJson(path.join(DATA_DIR, 'review_status.json'));
    let contribDoc = readJson(path.join(DATA_DIR, 'contributors.json'));

    const verifiedIds = [];
    let editCount = 0;
    const changedFiles = new Set();

    try {
        for (const op of submission.ops) {
            const res = engine.applyOperation(ov, rs, op, base);
            ov = res.overrides;
            rs = res.reviewStatus;
            for (const f of res.changedFiles) changedFiles.add(f);
            verifiedIds.push(...res.verifiedEntryIds);
            editCount += res.editCount;
        }
    } catch (err) {
        failSubmission(`驗證未通過，未寫入資料庫：${err.message}`);
    }

    const actor = {
        login: issue.user?.login || '',
        avatarUrl: issue.user?.avatar_url || '',
        htmlUrl: issue.user?.html_url || ''
    };
    if (!actor.login) {
        failSubmission('無法驗證提交者的 GitHub 帳號。');
    }

    contribDoc = engine.updateContributors(contribDoc, actor, verifiedIds, editCount);
    const contributorStats = contribDoc.contributors[actor.login];

    if (changedFiles.has('overrides.json')) writeJson(path.join(DATA_DIR, 'overrides.json'), ov);
    if (changedFiles.has('review_status.json')) writeJson(path.join(DATA_DIR, 'review_status.json'), rs);
    writeJson(path.join(DATA_DIR, 'contributors.json'), contribDoc);

    const threshold = contribDoc.threshold || 25;
    const vCount = contributorStats?.verifiedCount || 0;
    const milestoneNote = vCount >= threshold
        ? `🎉 已達標（**${vCount} / ${threshold}**），已收錄至網頁官方貢獻者名單！`
        : `目前唯一審核驗證進度：**${vCount} / ${threshold}**（還差 ${threshold - vCount} 條列入官方貢獻者名單）。`;

    const msg = [
        `✅ 已由 GitHub Actions 成功驗證並寫入共享雲端資料庫！（Issue #${issue.number}）`,
        `- 驗證編輯者：@${actor.login}`,
        `- 本次更新檔案：${[...changedFiles, 'contributors.json'].join(', ')}`,
        `- ${milestoneNote}`
    ].join('\n');

    setOutput('status', 'applied');
    setOutput('committed', 'true');
    setOutput('summary', String(submission.summary || `Issue #${issue.number}`).replace(/[\r\n]/g, ' ').slice(0, 72));
    setOutput('actor', actor.login);
    setOutput('message', msg);
    console.log('[apply_submission] Success:', actor.login, 'verifiedIds:', verifiedIds.length, 'editCount:', editCount);
}

main();
