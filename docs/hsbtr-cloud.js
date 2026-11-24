/**
 * Hypixel SkyBlock Translation Mod (zh_tw) — GitHub Cloud DB & Auth Client
 * Manages:
 *  - Base dictionary loading from static assets (docs/data/base/)
 *  - Uncached live reads of overrides, review_status, and contributors from GitHub HEAD
 *  - Pending IssueOps overlay persisted in localStorage (survives page reload/reopen)
 *  - GitHub Personal Access Token authentication & role detection (push vs IssueOps)
 *  - Atomic multi-file commits (Git Data API) for collaborators & IssueOps for external editors
 */
(function (root) {
    'use strict';

    const CONFIG = {
        owner: 'o-Frost-o',
        repo: 'hsbtc-mod',
        branch: 'main',
        dataPath: 'docs/data',
        siteUrl: 'https://o-frost-o.github.io/hsbtc-mod/',
        repoUrl: 'https://github.com/o-frost-o/hsbtc-mod',
        // 填入 GitHub OAuth App Client ID 與 Cloudflare Worker (/exchange) 端點 URL（切勿填入 Client Secret）
        oauthClientId: 'Ov23ligHSADzFgeOE0oG',
        oauthExchangeUrl: 'https://hsbtc-github-oauth.bingshuang0w0.workers.dev/exchange',
        oauthScope: 'public_repo'
    };

    const STORAGE_AUTH = 'hsbtr.github_auth.v1';
    const STORAGE_OAUTH_TX = 'hsbtr.oauth_tx.v1';
    const STORAGE_PENDING = 'hsbtr.pending_submissions.v1';
    const STORAGE_CAPTURE = 'hsbtr.capture_queue.v1';
    const BASE_FILES = ['tokens', 'items', 'item_lore_en', 'lore_lines', 'menus', 'dialogues'];

    const utf8FromBase64 = (b64) => {
        const bin = atob(String(b64).replace(/\s+/g, ''));
        const bytes = new Uint8Array(bin.length);
        for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
        return new TextDecoder('utf-8').decode(bytes);
    };

    const base64UrlEncode = (bytes) => {
        let bin = '';
        for (let i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i]);
        return btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
    };

    const listeners = new Set();
    const state = {
        ready: false,
        sourceMode: 'loading', // 'github-api' | 'github-raw' | 'local'
        lastSyncedAt: null,
        lastOAuthResult: null,
        auth: { token: '', user: null, pushAccess: false, method: 'pat' },
        base: {},
        cloudOverrides: HsbtrEngine.createEmptyOverrides(),
        cloudReviewStatus: {},
        contributorsDoc: { version: 1, threshold: 25, updatedAt: '', contributors: {} },
        pendingSubmissions: [],
        effectiveOverrides: HsbtrEngine.createEmptyOverrides(),
        effectiveReviewStatus: {},
        effectiveContributors: { version: 1, threshold: 25, updatedAt: '', contributors: {} },
        merged: null,
        reviewIndex: null
    };

    let pollTimer = null;

    function emit(eventName = 'update') {
        document.dispatchEvent(new CustomEvent('hsbtr:cloud-' + eventName, { detail: getSnapshot() }));
        for (const fn of listeners) {
            try { fn(eventName, getSnapshot()); } catch { /* ignore listener errors */ }
        }
    }

    function readStorage(key, fallback) {
        try {
            const raw = localStorage.getItem(key);
            return raw ? JSON.parse(raw) : fallback;
        } catch {
            return fallback;
        }
    }

    function writeStorage(key, val) {
        try {
            if (val === null || val === undefined) localStorage.removeItem(key);
            else localStorage.setItem(key, JSON.stringify(val));
        } catch { /* storage may be restricted */ }
    }

    async function ghFetch(path, options = {}) {
        const headers = new Headers(options.headers || {});
        headers.set('Accept', 'application/vnd.github+json');
        headers.set('X-GitHub-Api-Version', '2022-11-28');
        if (state.auth.token) headers.set('Authorization', `Bearer ${state.auth.token}`);
        const url = path.startsWith('http') ? path : `https://api.github.com${path}`;
        const resp = await fetch(url, { cache: 'no-store', ...options, headers });
        const text = await resp.text();
        let data = {};
        if (text) {
            try { data = JSON.parse(text); } catch { data = { message: text }; }
        }
        if (!resp.ok) {
            const err = new Error(data.message || `GitHub API HTTP ${resp.status}`);
            err.status = resp.status;
            err.data = data;
            throw err;
        }
        return data;
    }

    async function fetchCloudJson(filename, unauthRef = CONFIG.branch) {
        const rel = `./data/${filename}?t=${Date.now()}`;
        const rawRef = encodeURIComponent(unauthRef || CONFIG.branch);
        const rawUrl = `https://raw.githubusercontent.com/${CONFIG.owner}/${CONFIG.repo}/${rawRef}/${CONFIG.dataPath}/${filename}?t=${Date.now()}`;

        if (state.auth.token) {
            try {
                const res = await ghFetch(`/repos/${CONFIG.owner}/${CONFIG.repo}/contents/${CONFIG.dataPath}/${filename}?ref=${encodeURIComponent(CONFIG.branch)}`);
                if (res && res.encoding === 'base64' && typeof res.content === 'string') {
                    return { data: JSON.parse(utf8FromBase64(res.content)), mode: 'github-api' };
                }
                if (res && res.sha) {
                    const blob = await ghFetch(`/repos/${CONFIG.owner}/${CONFIG.repo}/git/blobs/${res.sha}`);
                    return { data: JSON.parse(utf8FromBase64(blob.content)), mode: 'github-api' };
                }
            } catch (e) {
                if (e.status !== 404 && state.sourceMode !== 'local') {
                    console.warn(`[HsbtrCloud] Contents API fallback for ${filename}:`, e.message);
                }
            }
        }

        try {
            const resp = await fetch(rawUrl, { cache: 'no-store' });
            if (resp.ok) return { data: await resp.json(), mode: 'github-raw' };
        } catch { /* fall through to static origin */ }

        const localResp = await fetch(rel, { cache: 'no-store' });
        if (!localResp.ok) throw new Error(`無法載入 ${filename} (${localResp.status})`);
        return { data: await localResp.json(), mode: 'local' };
    }

    function recomputeEffectiveState() {
        let ov = state.cloudOverrides || HsbtrEngine.createEmptyOverrides();
        let rs = state.cloudReviewStatus || {};
        let contribs = state.contributorsDoc || { version: 1, threshold: 25, updatedAt: '', contributors: {} };

        const validPending = [];
        for (const sub of state.pendingSubmissions) {
            try {
                let verifiedIds = [];
                let editCount = 0;
                for (const op of (sub.ops || [])) {
                    const res = HsbtrEngine.applyOperation(ov, rs, op, state.base);
                    ov = res.overrides;
                    rs = res.reviewStatus;
                    verifiedIds.push(...res.verifiedEntryIds);
                    editCount += res.editCount;
                }
                if (sub.submittedBy) {
                    contribs = HsbtrEngine.updateContributors(contribs, { login: sub.submittedBy }, verifiedIds, editCount);
                }
                validPending.push(sub);
            } catch {
                // Prune pending op if already merged or superseded remotely
            }
        }
        state.pendingSubmissions = validPending;
        writeStorage(STORAGE_PENDING, validPending);

        state.effectiveOverrides = ov;
        state.effectiveReviewStatus = rs;
        state.effectiveContributors = contribs;
        state.merged = HsbtrEngine.mergeDictionaries(state.base, ov);
        state.reviewIndex = HsbtrEngine.createReviewIndex(state.merged);
    }

    function extractIssueSubmission(issue) {
        const body = String(issue?.body || '');
        const match = body.match(/<!--\s*hsbtr-submission:v1\s*([\s\S]*?)-->/);
        if (!match) return null;
        try {
            const parsed = JSON.parse(match[1].trim());
            if (!parsed || !Array.isArray(parsed.ops) || !parsed.ops.length) return null;
            return {
                issueNumber: issue.number,
                htmlUrl: issue.html_url,
                submittedBy: issue.user?.login || '',
                createdAt: issue.created_at,
                summary: parsed.summary || issue.title,
                ops: parsed.ops
            };
        } catch {
            return null;
        }
    }

    async function reconcileOpenIssues() {
        try {
            const issues = await ghFetch(`/repos/${CONFIG.owner}/${CONFIG.repo}/issues?state=open&per_page=30`);
            if (!Array.isArray(issues)) return;
            const openNumbers = new Set();
            const mergedMap = new Map();

            for (const iss of issues) {
                if (!String(iss.title || '').startsWith('[HSBTR-SUBMISSION]')) continue;
                openNumbers.add(iss.number);
                const extracted = extractIssueSubmission(iss);
                if (extracted) mergedMap.set(iss.number, extracted);
            }
            for (const existing of state.pendingSubmissions) {
                if (!existing.issueNumber || openNumbers.has(existing.issueNumber)) {
                    if (!mergedMap.has(existing.issueNumber)) mergedMap.set(existing.issueNumber, existing);
                }
            }
            state.pendingSubmissions = [...mergedMap.values()].sort((a, b) => (a.issueNumber || 0) - (b.issueNumber || 0));
            writeStorage(STORAGE_PENDING, state.pendingSubmissions);
        } catch {
            // Repo may be unpublished or offline; keep local pending entries intact
        }
    }

    function schedulePendingPoll() {
        if (pollTimer) {
            clearInterval(pollTimer);
            pollTimer = null;
        }
        if (!state.pendingSubmissions.length) return;
        pollTimer = setInterval(async () => {
            if (!state.pendingSubmissions.length) {
                clearInterval(pollTimer);
                pollTimer = null;
                return;
            }
            try {
                const prevCount = state.pendingSubmissions.length;
                await syncDynamic({ silent: true });
                if (state.pendingSubmissions.length < prevCount) {
                    emit('merged');
                }
            } catch { /* ignore transient poll failures */ }
        }, 6000);
    }

    function isOAuthConfigured() {
        return Boolean(String(CONFIG.oauthClientId || '').trim() && String(CONFIG.oauthExchangeUrl || '').trim());
    }

    async function createPkceChallenge(verifier) {
        if (!globalThis.crypto?.subtle) {
            throw new Error('目前瀏覽器不支援 WebCrypto PKCE，請使用 HTTPS 網站或下方 PAT 登入。');
        }
        const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier));
        return base64UrlEncode(new Uint8Array(digest));
    }

    function randomUrlToken(byteLen = 32) {
        if (!globalThis.crypto?.getRandomValues) {
            throw new Error('目前瀏覽器不支援安全亂數產生器，請改用現代瀏覽器或 PAT 登入。');
        }
        const buf = new Uint8Array(byteLen);
        crypto.getRandomValues(buf);
        return base64UrlEncode(buf);
    }

    function installPopupBridge() {
        if (typeof window === 'undefined' || installPopupBridge._done) return;
        installPopupBridge._done = true;

        const acceptFromStorage = () => {
            const saved = readStorage(STORAGE_AUTH, null);
            if (!saved?.token || !saved?.user?.login) return;
            const changed = state.auth.token !== saved.token || state.auth.user?.login !== saved.user.login;
            state.auth = {
                token: saved.token,
                user: saved.user,
                pushAccess: Boolean(saved.pushAccess),
                method: saved.method || 'oauth'
            };
            if (changed) {
                emit('auth');
                emit('oauth-success');
                if (state.ready) syncDynamic().catch(() => { });
            }
        };

        window.addEventListener('storage', (e) => {
            if (e.key === STORAGE_AUTH) acceptFromStorage();
        });
        window.addEventListener('message', (e) => {
            if (e.origin !== window.location.origin) return;
            if (e.data?.type === 'hsbtr:oauth-complete') acceptFromStorage();
            else if (e.data?.type === 'hsbtr:oauth-error') {
                state.lastOAuthResult = { ok: false, error: e.data.error || 'GitHub OAuth 登入失敗。' };
                emit('oauth-error');
            }
        });
        if (typeof BroadcastChannel === 'function') {
            try {
                const ch = new BroadcastChannel('hsbtr_oauth');
                ch.onmessage = (e) => {
                    if (e.data?.type === 'hsbtr:oauth-complete') acceptFromStorage();
                    else if (e.data?.type === 'hsbtr:oauth-error') {
                        state.lastOAuthResult = { ok: false, error: e.data.error || 'GitHub OAuth 登入失敗。' };
                        emit('oauth-error');
                    }
                };
            } catch { /* ignore BroadcastChannel restrictions */ }
        }
    }

    function notifyOpener(payload) {
        try {
            if (typeof BroadcastChannel === 'function') {
                const ch = new BroadcastChannel('hsbtr_oauth');
                ch.postMessage(payload);
                ch.close();
            }
        } catch { /* ignore */ }
        try {
            if (window.opener && window.opener !== window) {
                window.opener.postMessage(payload, window.location.origin);
            }
        } catch { /* ignore */ }
    }

    async function startOAuthLogin() {
        if (!isOAuthConfigured()) {
            throw new Error('尚未設定 GitHub OAuth App 與 Cloudflare Worker 端點。');
        }
        const oauthState = randomUrlToken(24);
        const codeVerifier = randomUrlToken(32);
        const codeChallenge = await createPkceChallenge(codeVerifier);
        const redirectUri = `${window.location.origin}${window.location.pathname}`;

        writeStorage(STORAGE_OAUTH_TX, {
            state: oauthState,
            codeVerifier,
            redirectUri,
            popup: true,
            createdAt: Date.now()
        });

        const authUrl = new URL('https://github.com/login/oauth/authorize');
        authUrl.searchParams.set('client_id', CONFIG.oauthClientId.trim());
        authUrl.searchParams.set('redirect_uri', redirectUri);
        authUrl.searchParams.set('scope', CONFIG.oauthScope || 'public_repo');
        authUrl.searchParams.set('state', oauthState);
        authUrl.searchParams.set('code_challenge', codeChallenge);
        authUrl.searchParams.set('code_challenge_method', 'S256');

        const popup = window.open(
            authUrl.toString(),
            'hsbtr_github_oauth',
            'width=620,height=760,menubar=no,toolbar=no,location=yes,resizable=yes,scrollbars=yes'
        );
        if (!popup || popup.closed || typeof popup.closed === 'undefined') {
            writeStorage(STORAGE_OAUTH_TX, {
                state: oauthState,
                codeVerifier,
                redirectUri,
                popup: false,
                createdAt: Date.now()
            });
            window.location.assign(authUrl.toString());
            return { mode: 'redirect' };
        }
        try { popup.focus(); } catch { /* ignore */ }
        return { mode: 'popup' };
    }

    async function handleOAuthCallback() {
        if (typeof window === 'undefined') return { handled: false };
        const params = new URLSearchParams(window.location.search || '');
        const code = params.get('code');
        const returnedState = params.get('state');
        const oauthError = params.get('error');
        const oauthErrorDesc = params.get('error_description');

        if (!code && !oauthError) return { handled: false };

        const cleanUrl = `${window.location.pathname}${window.location.hash || ''}`;
        try {
            window.history.replaceState({}, document.title, cleanUrl);
        } catch { /* ignore */ }

        const tx = readStorage(STORAGE_OAUTH_TX, null);
        writeStorage(STORAGE_OAUTH_TX, null);

        if (oauthError) {
            const msg = oauthErrorDesc || `已取消 GitHub 授權 (${oauthError})`;
            state.lastOAuthResult = { ok: false, error: msg };
            if (tx?.popup) {
                notifyOpener({ type: 'hsbtr:oauth-error', error: msg });
                try { window.close(); } catch { /* ignore */ }
            }
            return { handled: true, ok: false, error: msg };
        }

        try {
            if (!isOAuthConfigured()) {
                throw new Error('尚未設定 GitHub OAuth 交換端點。');
            }
            if (!tx || !tx.state || !returnedState || tx.state !== returnedState || (Date.now() - Number(tx.createdAt || 0)) > 10 * 60 * 1000) {
                throw new Error('GitHub OAuth state 驗證失敗或已逾時，請重新登入。');
            }
            const resp = await fetch(CONFIG.oauthExchangeUrl.trim(), {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    code,
                    code_verifier: tx.codeVerifier,
                    redirect_uri: tx.redirectUri
                })
            });
            const data = await resp.json().catch(() => ({}));
            if (!resp.ok || !data?.access_token) {
                throw new Error(data?.error_description || data?.error || `OAuth Token 交換失敗 (HTTP ${resp.status})`);
            }
            const auth = await verifyToken(data.access_token, { skipSync: true, authMethod: 'oauth' });
            state.lastOAuthResult = { ok: true, auth };
            if (tx.popup) {
                notifyOpener({ type: 'hsbtr:oauth-complete', login: auth.user?.login || '' });
                try { window.close(); } catch { /* ignore */ }
            }
            return { handled: true, ok: true, auth };
        } catch (err) {
            const msg = err?.message || 'GitHub OAuth 登入失敗。';
            state.lastOAuthResult = { ok: false, error: msg };
            if (tx?.popup) {
                notifyOpener({ type: 'hsbtr:oauth-error', error: msg });
                try { window.close(); } catch { /* ignore */ }
            }
            return { handled: true, ok: false, error: msg };
        }
    }

    function consumeOAuthResult() {
        const res = state.lastOAuthResult;
        state.lastOAuthResult = null;
        return res;
    }

    async function init() {
        installPopupBridge();
        const savedAuth = readStorage(STORAGE_AUTH, null);
        if (savedAuth?.token) {
            state.auth.token = savedAuth.token;
            state.auth.user = savedAuth.user || null;
            state.auth.pushAccess = Boolean(savedAuth.pushAccess);
            state.auth.method = savedAuth.method || 'pat';
        }
        state.pendingSubmissions = readStorage(STORAGE_PENDING, []);

        const cbRes = await handleOAuthCallback();

        await Promise.all(BASE_FILES.map(async (name) => {
            const resp = await fetch(`./data/base/${name}.json`);
            if (!resp.ok) throw new Error(`無法載入基礎字典 base/${name}.json`);
            state.base[name] = await resp.json();
        }));

        if (state.auth.token && !cbRes?.ok) {
            try {
                await verifyToken(state.auth.token, { skipSync: true, authMethod: state.auth.method });
            } catch {
                // Keep token if offline, clear only on 401
            }
        }

        await syncDynamic();
        state.ready = true;
        emit('ready');
    }

    async function syncDynamic({ silent = false } = {}) {
        let unauthRef = CONFIG.branch;
        if (!state.auth.token) {
            try {
                const ref = await ghFetch(`/repos/${CONFIG.owner}/${CONFIG.repo}/git/ref/heads/${encodeURIComponent(CONFIG.branch)}`);
                if (ref?.object?.sha && /^[0-9a-f]{40}$/i.test(ref.object.sha)) {
                    unauthRef = ref.object.sha;
                }
            } catch { /* fallback to branch ref */ }
        }
        const [ovRes, rsRes, cRes] = await Promise.all([
            fetchCloudJson('overrides.json', unauthRef),
            fetchCloudJson('review_status.json', unauthRef),
            fetchCloudJson('contributors.json', unauthRef)
        ]);
        state.cloudOverrides = ovRes.data;
        state.cloudReviewStatus = rsRes.data;
        state.contributorsDoc = cRes.data;
        state.sourceMode = ovRes.mode;
        state.lastSyncedAt = new Date().toISOString();

        if (ovRes.mode !== 'local') {
            await reconcileOpenIssues();
        }
        recomputeEffectiveState();
        schedulePendingPoll();
        if (!silent) emit('update');
    }

    async function verifyToken(token, { skipSync = false, authMethod = 'pat' } = {}) {
        const cleanTok = String(token || '').trim();
        if (!cleanTok) throw new Error('請輸入 GitHub Personal Access Token (PAT)。');
        try {
            const prevTok = state.auth.token;
            state.auth.token = cleanTok;
            const user = await ghFetch('/user');
            let pushAccess = false;
            try {
                const repoInfo = await ghFetch(`/repos/${CONFIG.owner}/${CONFIG.repo}`);
                pushAccess = Boolean(repoInfo?.permissions?.push || repoInfo?.permissions?.admin || repoInfo?.permissions?.maintain);
            } catch {
                pushAccess = false;
            }
            state.auth = { token: cleanTok, user, pushAccess, method: authMethod };
            writeStorage(STORAGE_AUTH, state.auth);
            if (!skipSync && prevTok !== cleanTok) await syncDynamic();
            else emit('auth');
            return state.auth;
        } catch (err) {
            if (err.status === 401 || !skipSync) {
                state.auth = { token: '', user: null, pushAccess: false, method: 'pat' };
                writeStorage(STORAGE_AUTH, null);
                emit('auth');
            }
            throw new Error(err.status === 401 ? 'GitHub Token 無效或已過期，請重新產生。' : `驗證失敗: ${err.message}`);
        }
    }

    function logout() {
        state.auth = { token: '', user: null, pushAccess: false, method: 'pat' };
        writeStorage(STORAGE_AUTH, null);
        emit('auth');
    }

    function requireAuth() {
        if (state.auth.token && state.auth.user?.login) return state.auth;
        document.dispatchEvent(new CustomEvent('hsbtr:require-auth'));
        throw new Error('請先完成 GitHub 帳號驗證後再儲存或驗證翻譯。');
    }

    async function createBlob(contentObj) {
        const res = await ghFetch(`/repos/${CONFIG.owner}/${CONFIG.repo}/git/blobs`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ content: JSON.stringify(contentObj, null, 2) + '\n', encoding: 'utf-8' })
        });
        return res.sha;
    }

    async function directCommitOps(ops, summary, actor) {
        for (let attempt = 0; attempt < 3; attempt++) {
            await syncDynamic({ silent: true });
            let ov = state.cloudOverrides;
            let rs = state.cloudReviewStatus;
            const verifiedIds = [];
            let editCount = 0;
            for (const op of ops) {
                const res = HsbtrEngine.applyOperation(ov, rs, op, state.base);
                ov = res.overrides;
                rs = res.reviewStatus;
                verifiedIds.push(...res.verifiedEntryIds);
                editCount += res.editCount;
            }
            const contrib = HsbtrEngine.updateContributors(state.contributorsDoc, actor, verifiedIds, editCount);

            const ref = await ghFetch(`/repos/${CONFIG.owner}/${CONFIG.repo}/git/ref/heads/${encodeURIComponent(CONFIG.branch)}`);
            const headSha = ref.object.sha;
            const headCommit = await ghFetch(`/repos/${CONFIG.owner}/${CONFIG.repo}/git/commits/${headSha}`);

            const treeItems = [];
            const blobOverrides = await createBlob(ov);
            treeItems.push({ path: `${CONFIG.dataPath}/overrides.json`, mode: '100644', type: 'blob', sha: blobOverrides });
            if (verifiedIds.length > 0) {
                const blobRs = await createBlob(rs);
                treeItems.push({ path: `${CONFIG.dataPath}/review_status.json`, mode: '100644', type: 'blob', sha: blobRs });
            }
            const blobContrib = await createBlob(contrib);
            treeItems.push({ path: `${CONFIG.dataPath}/contributors.json`, mode: '100644', type: 'blob', sha: blobContrib });

            const newTree = await ghFetch(`/repos/${CONFIG.owner}/${CONFIG.repo}/git/trees`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ base_tree: headCommit.tree.sha, tree: treeItems })
            });
            const newCommit = await ghFetch(`/repos/${CONFIG.owner}/${CONFIG.repo}/git/commits`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    message: `docs(data): ${summary} (@${actor.login})`,
                    tree: newTree.sha,
                    parents: [headSha]
                })
            });

            try {
                await ghFetch(`/repos/${CONFIG.owner}/${CONFIG.repo}/git/refs/heads/${encodeURIComponent(CONFIG.branch)}`, {
                    method: 'PATCH',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ sha: newCommit.sha, force: false })
                });
                state.cloudOverrides = ov;
                state.cloudReviewStatus = rs;
                state.contributorsDoc = contrib;
                state.lastSyncedAt = new Date().toISOString();
                recomputeEffectiveState();
                emit('update');
                return { mode: 'direct', commitSha: newCommit.sha, verifiedCount: contrib.contributors[actor.login]?.verifiedCount || 0 };
            } catch (err) {
                if (err.status !== 422 || attempt === 2) throw err;
            }
        }
    }

    async function submitIssueOp(ops, summary, actor) {
        // Validate against latest state before opening GitHub Issue
        let ov = state.effectiveOverrides;
        let rs = state.effectiveReviewStatus;
        for (const op of ops) {
            const res = HsbtrEngine.applyOperation(ov, rs, op, state.base);
            ov = res.overrides;
            rs = res.reviewStatus;
        }

        const safeSummary = String(summary || '更新繁中翻譯資料庫').replace(/[\r\n]/g, ' ').slice(0, 120);
        const payload = { v: 1, ops, summary: safeSummary };
        const body = [
            '此 Issue 由 **HSBTR GitHub Pages 線上翻譯平台**建立，將由 GitHub Actions 自動驗證並寫入共享資料庫 `docs/data/`。',
            '',
            `- GitHub 驗證編輯者：@${actor.login}`,
            `- 操作摘要：${safeSummary}`,
            '',
            '<!-- hsbtr-submission:v1',
            JSON.stringify(payload),
            '-->'
        ].join('\n');

        const created = await ghFetch(`/repos/${CONFIG.owner}/${CONFIG.repo}/issues`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ title: `[HSBTR-SUBMISSION] ${safeSummary}`, body })
        });

        state.pendingSubmissions.push({
            issueNumber: created.number,
            htmlUrl: created.html_url,
            submittedBy: actor.login,
            createdAt: created.created_at,
            summary: safeSummary,
            ops
        });
        recomputeEffectiveState();
        schedulePendingPoll();
        emit('update');
        return { mode: 'issueops', issueNumber: created.number, htmlUrl: created.html_url };
    }

    async function commitOps(ops, summary) {
        const auth = requireAuth();
        const actor = {
            login: auth.user.login,
            avatarUrl: auth.user.avatar_url || '',
            htmlUrl: auth.user.html_url || `https://github.com/${auth.user.login}`
        };
        if (auth.pushAccess) {
            return await directCommitOps(ops, summary, actor);
        }
        return await submitIssueOp(ops, summary, actor);
    }

    function getSnapshot() {
        return {
            ready: state.ready,
            config: CONFIG,
            oauthConfigured: isOAuthConfigured(),
            sourceMode: state.sourceMode,
            lastSyncedAt: state.lastSyncedAt,
            auth: {
                loggedIn: Boolean(state.auth.user?.login),
                user: state.auth.user,
                pushAccess: state.auth.pushAccess,
                method: state.auth.method || 'pat'
            },
            merged: state.merged,
            reviewIndex: state.reviewIndex,
            reviewStatus: state.effectiveReviewStatus,
            overrides: state.effectiveOverrides,
            contributorsDoc: state.effectiveContributors,
            pendingCount: state.pendingSubmissions.length,
            pendingSubmissions: state.pendingSubmissions
        };
    }

    function countOverrides() {
        const ov = state.effectiveOverrides || {};
        return Object.keys(ov.tokens || {}).length
            + Object.keys(ov.items || {}).length
            + Object.keys(ov.menus?.titles || {}).length
            + Object.keys(ov.menus?.lines || {}).length
            + Object.keys(ov.menus?.people || {}).length
            + (ov.menus?.patterns || []).length
            + Object.keys(ov.lore_lines?.lines || {}).length
            + Object.keys(ov.lore_lines?.paragraphs || {}).length
            + (ov.lore_lines?.paragraph_patterns || []).length
            + Object.keys(ov.dialogues?.dialogues || {}).length
            + (ov.dialogues?.patterns || []).length;
    }

    root.HsbtrCloud = {
        CONFIG,
        STORAGE_CAPTURE,
        init,
        isOAuthConfigured,
        startOAuthLogin,
        handleOAuthCallback,
        consumeOAuthResult,
        syncDynamic,
        verifyToken,
        logout,
        requireAuth,
        commitOps,
        getSnapshot,
        countOverrides,
        readStorage,
        writeStorage,
        subscribe: (fn) => { listeners.add(fn); return () => listeners.delete(fn); },

        // Convenience API adapters replacing old /api/* calls
        getTokens: () => state.merged?.tokens || {},
        queryItems: (q = '', category = '', limit = 30, offset = 0) => {
            const ql = String(q).trim().toLowerCase();
            const cl = String(category).trim().toUpperCase();
            const list = Object.values(state.merged?.items || {}).filter(item => {
                if (cl && String(item.category || 'MISC').toUpperCase() !== cl) return false;
                if (!ql) return true;
                return String(item.id || '').toLowerCase().includes(ql)
                    || String(item.name_en || '').toLowerCase().includes(ql)
                    || String(item.name_zh_tw || '').toLowerCase().includes(ql);
            });
            return { total: list.length, rows: list.slice(offset, offset + limit) };
        },
        saveToken: (record) => commitOps([{ type: 'token', token: record }], `更新詞彙 {${record.tokenId}}`),
        importCsvTokens: (tokens) => commitOps([{ type: 'tokens_batch', tokens }], `CSV 批次匯入 ${tokens.length} 筆詞彙`),
        saveItem: (item) => commitOps([{ type: 'item', item }], `更新道具 ${item.id}`),
        lookupWorkbench: (kind, source) => {
            const clean = String(source || '').trim();
            const m = state.merged;
            let existing = null;
            if (kind === 'paragraph') existing = m?.lore_lines?.paragraphs?.[clean] || m?.lore_lines?.lines?.[clean] || null;
            else if (kind === 'menu') existing = m?.menus?.lines?.[clean] || null;
            else if (kind === 'title') existing = m?.menus?.titles?.[clean] || null;
            else if (kind === 'dialogue') {
                const raw = m?.dialogues?.dialogues?.[clean];
                existing = typeof raw === 'string' ? raw : raw?.zh_tw || null;
            }
            return { existing };
        },
        previewWorkbench: (payload) => {
            const res = HsbtrEngine.previewWorkbenchDraft(state.merged, payload.kind, payload.source, payload.translation, payload.roles);
            const lookupRes = root.HsbtrCloud.lookupWorkbench(payload.kind, payload.source);
            return {
                preview: res.preview,
                existing: lookupRes.existing,
                revision: HsbtrEngine.digest(state.reviewIndex?.size || 0, payload.kind, payload.source)
            };
        },
        saveWorkbench: async (payload) => {
            const previewRes = root.HsbtrCloud.previewWorkbench(payload);
            const res = await commitOps([{ type: 'workbench', payload }], `工作台新增 ${payload.kind} 翻譯`);
            return { ...previewRes, ...res, status: 'saved' };
        },
        reviewPage: (req) => state.reviewIndex.page(req, state.effectiveReviewStatus),
        reviewNext: (req) => state.reviewIndex.next(req, state.effectiveReviewStatus),
        reviewExcerpt: (req) => state.reviewIndex.excerpt({ ...req, sentence: true }, state.effectiveReviewStatus),
        reviewSave: async (req, verify) => {
            const existing = state.reviewIndex.lookup(req.id);
            const label = existing ? (existing.context || existing.target?.key || existing.kind) : req.id.slice(0, 8);
            await commitOps(
                [{ type: 'review', review: { id: req.id, revision: req.revision, value: req.value, roles: req.roles, excerpt: req.excerpt, verify } }],
                `${verify ? '驗證並儲存' : '儲存'}審核條目 ${String(label).slice(0, 48)}`
            );
            const updated = state.reviewIndex.lookup(req.id);
            if (!updated) throw new Error('儲存成功，但找不到更新後條目，請重新同步。');
            return state.reviewIndex.toJson(updated, state.effectiveReviewStatus);
        }
    };
})(typeof globalThis !== 'undefined' ? globalThis : window);
