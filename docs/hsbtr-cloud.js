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
    const STORAGE_UPLOAD_QUEUE = 'hsbtr.upload_queue.v1';
    const STORAGE_CAPTURE = 'hsbtr.capture_queue.v1';
    const BASE_FILES = ['tokens', 'items', 'item_lore_en', 'lore_lines', 'menus', 'dialogues'];

    const utf8FromBase64 = (b64) => {
        const bin = atob(String(b64).replace(/\s+/g, ''));
        const bytes = new Uint8Array(bin.length);
        for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
        return new TextDecoder('utf-8').decode(bytes);
    };

    const utf8ToBase64 = (str) => {
        const bytes = new TextEncoder().encode(String(str));
        let bin = '';
        for (let i = 0; i < bytes.length; i += 0x8000) {
            bin += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
        }
        return btoa(bin);
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
        cloudHeadSha: '',
        cloudTreeSha: '',
        auth: { token: '', user: null, pushAccess: false, method: 'pat' },
        base: {},
        cloudOverrides: HsbtrEngine.createEmptyOverrides(),
        cloudReviewStatus: {},
        contributorsDoc: { version: 1, threshold: 25, updatedAt: '', contributors: {} },
        pendingSubmissions: [],
        uploadQueue: [],
        preQueueReviewEntries: new Map(),
        uploadState: { flushing: false, lastError: '' },
        effectiveOverrides: HsbtrEngine.createEmptyOverrides(),
        effectiveReviewStatus: {},
        effectiveContributors: { version: 1, threshold: 25, updatedAt: '', contributors: {} },
        merged: null,
        reviewIndex: null
    };

    let pollTimer = null;
    let flushPromise = null;
    let flushScheduled = false;

    function emit(eventName = 'update', meta = null) {
        const detail = meta ? { ...getSnapshot(), _meta: meta } : getSnapshot();
        document.dispatchEvent(new CustomEvent('hsbtr:cloud-' + eventName, { detail }));
        for (const fn of listeners) {
            try { fn(eventName, detail); } catch { /* ignore listener errors */ }
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

    async function ghGraphql(query, variables = {}) {
        const headers = new Headers();
        headers.set('Accept', 'application/vnd.github+json');
        headers.set('Content-Type', 'application/json');
        headers.set('X-GitHub-Api-Version', '2022-11-28');
        if (state.auth.token) headers.set('Authorization', `Bearer ${state.auth.token}`);
        const resp = await fetch('https://api.github.com/graphql', {
            method: 'POST',
            cache: 'no-store',
            headers,
            body: JSON.stringify({ query, variables })
        });
        const data = await resp.json().catch(() => ({}));
        if (!resp.ok || data.errors?.length) {
            const msg = data.errors?.map(e => e.message).join('; ') || data.message || `GitHub GraphQL HTTP ${resp.status}`;
            const err = new Error(msg);
            err.status = resp.status;
            err.graphqlErrors = data.errors || null;
            throw err;
        }
        return data.data;
    }

    async function fetchCloudBundleGraphql() {
        const ref = `refs/heads/${CONFIG.branch}`;
        const pfx = `${CONFIG.branch}:${CONFIG.dataPath}`;
        const query = `
            query ($owner: String!, $repo: String!, $ref: String!, $ovExp: String!, $rsExp: String!, $ctExp: String!) {
                repository(owner: $owner, name: $repo) {
                    ref(qualifiedName: $ref) {
                        target {
                            oid
                            ... on Commit { tree { oid } }
                        }
                    }
                    ov: object(expression: $ovExp) { ... on Blob { text } }
                    rs: object(expression: $rsExp) { ... on Blob { text } }
                    ct: object(expression: $ctExp) { ... on Blob { text } }
                }
            }
        `;
        const data = await ghGraphql(query, {
            owner: CONFIG.owner,
            repo: CONFIG.repo,
            ref,
            ovExp: `${pfx}/overrides.json`,
            rsExp: `${pfx}/review_status.json`,
            ctExp: `${pfx}/contributors.json`
        });
        const repoNode = data?.repository;
        const target = repoNode?.ref?.target;
        if (!target?.oid || typeof repoNode?.ov?.text !== 'string' || typeof repoNode?.rs?.text !== 'string' || typeof repoNode?.ct?.text !== 'string') {
            throw new Error('Incomplete GraphQL repository bundle');
        }
        return {
            headSha: target.oid,
            treeSha: target.tree?.oid || '',
            ov: JSON.parse(repoNode.ov.text),
            rs: JSON.parse(repoNode.rs.text),
            contrib: JSON.parse(repoNode.ct.text)
        };
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

    function snapshotPreQueueEntry(entry) {
        if (!entry) return null;
        return {
            id: entry.id,
            dictionary: entry.dictionary,
            kind: entry.kind,
            target: { ...entry.target },
            source: entry.source,
            context: entry.context,
            value: entry.value,
            format: entry.format,
            record: entry.record ? JSON.parse(JSON.stringify(entry.record)) : entry.record,
            dynamic: entry.dynamic ? JSON.parse(JSON.stringify(entry.dynamic)) : null,
            _rev: entry._rev || null,
            markable: entry.markable
        };
    }

    function recomputeEffectiveState() {
        let ov = state.cloudOverrides || HsbtrEngine.createEmptyOverrides();
        let rs = state.cloudReviewStatus || {};
        let contribs = state.contributorsDoc || { version: 1, threshold: 25, updatedAt: '', contributors: {} };

        let merged = HsbtrEngine.mergeDictionaries(state.base, ov);
        let reviewIndex = HsbtrEngine.createReviewIndex(merged);
        state.preQueueReviewEntries = new Map();

        const validPending = [];
        for (const sub of state.pendingSubmissions) {
            try {
                let verifiedIds = [];
                let editCount = 0;
                for (const op of (sub.ops || [])) {
                    const res = HsbtrEngine.applyOperation(ov, rs, op, state.base, { merged, reviewIndex });
                    ov = res.overrides;
                    rs = res.reviewStatus;
                    HsbtrEngine.applyMergedPatch(merged, reviewIndex, res);
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

        const validQueue = [];
        for (const qItem of state.uploadQueue) {
            try {
                const res = HsbtrEngine.applyOperation(ov, rs, qItem.op, state.base, { merged, reviewIndex });
                const touched = res.reviewPatch?.extraPatches?.length
                    ? res.reviewPatch.extraPatches
                    : (res.reviewPatch?.entry ? [res.reviewPatch.entry] : []);
                const ids = [];
                for (const t of touched) {
                    if (!t?.id) continue;
                    ids.push(t.id);
                    if (!state.preQueueReviewEntries.has(t.id)) {
                        const existingEntry = reviewIndex.lookup(t.id);
                        state.preQueueReviewEntries.set(t.id, existingEntry ? snapshotPreQueueEntry(existingEntry) : null);
                    }
                }
                if (ids.length) qItem.affectedIds = ids;
                ov = res.overrides;
                rs = res.reviewStatus;
                HsbtrEngine.applyMergedPatch(merged, reviewIndex, res);
                const actorLogin = qItem.actorLogin || state.auth.user?.login;
                if (actorLogin) {
                    contribs = HsbtrEngine.updateContributors(contribs, { login: actorLogin }, res.verifiedEntryIds, res.editCount);
                }
                validQueue.push(qItem);
            } catch {
                // Drop queued op invalidated remotely
            }
        }
        state.uploadQueue = validQueue;
        writeStorage(STORAGE_UPLOAD_QUEUE, validQueue);

        state.effectiveOverrides = ov;
        state.effectiveReviewStatus = rs;
        state.effectiveContributors = contribs;
        state.merged = merged;
        state.reviewIndex = reviewIndex;
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
        state.uploadQueue = readStorage(STORAGE_UPLOAD_QUEUE, []);

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
        if (state.uploadQueue.length > 0 && state.auth.token && state.auth.user?.login) {
            scheduleUploadFlush();
        }
    }

    async function syncDynamic({ silent = false, skipIssues = false, waitQueue = false } = {}) {
        if (waitQueue && (flushPromise || state.uploadQueue.length > 0)) {
            await flushUploadQueue();
        }
        if (state.auth.token) {
            try {
                const bundle = await fetchCloudBundleGraphql();
                state.cloudHeadSha = bundle.headSha;
                state.cloudTreeSha = bundle.treeSha;
                state.cloudOverrides = bundle.ov;
                state.cloudReviewStatus = bundle.rs;
                state.contributorsDoc = bundle.contrib;
                state.sourceMode = 'github-api';
                state.lastSyncedAt = new Date().toISOString();
                if (!skipIssues && (!state.auth.pushAccess || state.pendingSubmissions.length > 0)) {
                    await reconcileOpenIssues();
                }
                recomputeEffectiveState();
                schedulePendingPoll();
                if (!silent) emit('update');
                return;
            } catch {
                // Fall back to REST / raw / local snapshot
            }
        }

        let unauthRef = CONFIG.branch;
        if (!state.auth.token) {
            try {
                const ref = await ghFetch(`/repos/${CONFIG.owner}/${CONFIG.repo}/git/ref/heads/${encodeURIComponent(CONFIG.branch)}`);
                if (ref?.object?.sha && /^[0-9a-f]{40}$/i.test(ref.object.sha)) {
                    unauthRef = ref.object.sha;
                    state.cloudHeadSha = ref.object.sha;
                    state.cloudTreeSha = '';
                }
            } catch { /* fallback to branch ref */ }
        } else {
            state.cloudHeadSha = '';
            state.cloudTreeSha = '';
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

        if (!skipIssues && ovRes.mode !== 'local' && (!state.auth.pushAccess || state.pendingSubmissions.length > 0)) {
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

    function makeBatchContext(existingOv, existingRs, existingBaseRes) {
        if (!existingBaseRes && !state.pendingSubmissions.length && !state.preQueueReviewEntries.size && state.cloudOverrides === existingOv) {
            return { merged: state.merged, reviewIndex: state.reviewIndex };
        }
        return null;
    }

    function applyBatchOps(startOv, startRs, ops, baseContext = null) {
        let ov = startOv;
        let rs = startRs;
        const verifiedIds = [];
        let editCount = 0;
        const changedFiles = new Set();

        const batchUpdated = new Map();
        const batchIdx = baseContext?.reviewIndex ? {
            lookup: (id) => batchUpdated.has(id)
                ? batchUpdated.get(id)
                : (state.preQueueReviewEntries.has(id) ? state.preQueueReviewEntries.get(id) : baseContext.reviewIndex.lookup(id)),
            entryRevision: baseContext.reviewIndex.entryRevision,
            excerpt: baseContext.reviewIndex.excerpt
        } : null;
        const batchCtx = (baseContext?.merged && batchIdx) ? { merged: baseContext.merged, reviewIndex: batchIdx } : null;

        for (const op of ops) {
            const res = HsbtrEngine.applyOperation(ov, rs, op, state.base, batchCtx);
            ov = res.overrides;
            rs = res.reviewStatus;
            for (const f of res.changedFiles) changedFiles.add(f);
            verifiedIds.push(...res.verifiedEntryIds);
            editCount += res.editCount;
            const touched = res.reviewPatch?.extraPatches?.length
                ? res.reviewPatch.extraPatches
                : (res.reviewPatch?.entry ? [res.reviewPatch.entry] : []);
            for (const t of touched) {
                if (t?.id) batchUpdated.set(t.id, t);
            }
        }
        return { ov, rs, verifiedIds, editCount, changedFiles, patchMap: batchUpdated };
    }

    function buildFilesToCommit(ov, rs, contrib, changedFiles) {
        const files = [];
        if (changedFiles.has('overrides.json')) {
            files.push({ name: 'overrides.json', text: JSON.stringify(ov, null, 2) + '\n' });
        }
        if (changedFiles.has('review_status.json')) {
            files.push({ name: 'review_status.json', text: JSON.stringify(rs, null, 2) + '\n' });
        }
        files.push({ name: 'contributors.json', text: JSON.stringify(contrib, null, 2) + '\n' });
        return files;
    }

    async function commitFilesGraphql(headSha, headline, files) {
        const mutation = `
            mutation ($input: CreateCommitOnBranchInput!) {
                createCommitOnBranch(input: $input) {
                    commit { oid }
                }
            }
        `;
        const data = await ghGraphql(mutation, {
            input: {
                branch: {
                    repositoryNameWithOwner: `${CONFIG.owner}/${CONFIG.repo}`,
                    branchName: CONFIG.branch
                },
                expectedHeadOid: headSha,
                message: { headline },
                fileChanges: {
                    additions: files.map(f => ({
                        path: `${CONFIG.dataPath}/${f.name}`,
                        contents: utf8ToBase64(f.text)
                    }))
                }
            }
        });
        const oid = data?.createCommitOnBranch?.commit?.oid;
        if (!oid) throw new Error('GraphQL createCommitOnBranch missing commit oid');
        return { commitSha: oid, treeSha: '', via: 'graphql' };
    }

    async function commitFilesRest(headSha, cachedTreeSha, commitMsg, files) {
        let baseTreeSha = cachedTreeSha;
        if (!baseTreeSha) {
            const headCommit = await ghFetch(`/repos/${CONFIG.owner}/${CONFIG.repo}/commits/${headSha}`);
            baseTreeSha = headCommit?.commit?.tree?.sha || headCommit?.tree?.sha;
        }
        if (!baseTreeSha) {
            const gitCommit = await ghFetch(`/repos/${CONFIG.owner}/${CONFIG.repo}/git/commits/${headSha}`);
            baseTreeSha = gitCommit.tree.sha;
        }
        const treeItems = files.map(f => ({
            path: `${CONFIG.dataPath}/${f.name}`,
            mode: '100644',
            type: 'blob',
            content: f.text
        }));
        const newTree = await ghFetch(`/repos/${CONFIG.owner}/${CONFIG.repo}/git/trees`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ base_tree: baseTreeSha, tree: treeItems })
        });
        const newCommit = await ghFetch(`/repos/${CONFIG.owner}/${CONFIG.repo}/git/commits`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ message: commitMsg, tree: newTree.sha, parents: [headSha] })
        });
        await ghFetch(`/repos/${CONFIG.owner}/${CONFIG.repo}/git/refs/heads/${encodeURIComponent(CONFIG.branch)}`, {
            method: 'PATCH',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ sha: newCommit.sha, force: false })
        });
        return { commitSha: newCommit.sha, treeSha: newTree.sha, via: 'rest' };
    }

    function isHeadConflictError(err) {
        if (!err) return false;
        if (err.status === 422) return true;
        return /expected.*(oid|branch|head|point)|head.*(mismatch|conflict|changed)|not fast-forward|already exists|was at|cannot be cleanly/i.test(String(err.message || ''));
    }

    async function directCommitOps(ops, summary, actor, { skipUiUpdate = false } = {}) {
        const safeHeadline = String(summary || '更新繁中翻譯資料庫').replace(/[\r\n]/g, ' ').slice(0, 120);
        const commitMsg = `docs(data): ${safeHeadline} (@${actor.login})`;

        for (let attempt = 0; attempt < 3; attempt++) {
            if (!state.cloudHeadSha || attempt > 0) {
                await syncDynamic({ silent: true, skipIssues: true });
            }
            const useCtx = (attempt === 0) ? makeBatchContext(state.cloudOverrides, state.cloudReviewStatus, null)
                || (state.preQueueReviewEntries.size ? { merged: state.merged, reviewIndex: state.reviewIndex } : null)
                : null;
            const { ov, rs, verifiedIds, editCount, changedFiles, patchMap } = applyBatchOps(
                state.cloudOverrides,
                state.cloudReviewStatus,
                ops,
                useCtx
            );
            const contrib = HsbtrEngine.updateContributors(state.contributorsDoc, actor, verifiedIds, editCount);
            const files = buildFilesToCommit(ov, rs, contrib, changedFiles);

            let commitResult;
            try {
                commitResult = await commitFilesGraphql(state.cloudHeadSha, commitMsg, files);
            } catch (gqlErr) {
                if (isHeadConflictError(gqlErr)) {
                    state.cloudHeadSha = '';
                    state.cloudTreeSha = '';
                    if (attempt === 2) throw gqlErr;
                    continue;
                }
                try {
                    commitResult = await commitFilesRest(state.cloudHeadSha, state.cloudTreeSha, commitMsg, files);
                } catch (restErr) {
                    if (isHeadConflictError(restErr) && attempt < 2) {
                        state.cloudHeadSha = '';
                        state.cloudTreeSha = '';
                        continue;
                    }
                    throw restErr;
                }
            }

            state.cloudHeadSha = commitResult.commitSha;
            state.cloudTreeSha = commitResult.treeSha || '';
            state.cloudOverrides = ov;
            state.cloudReviewStatus = rs;
            state.contributorsDoc = contrib;
            state.lastSyncedAt = new Date().toISOString();

            if (!skipUiUpdate) {
                recomputeEffectiveState();
                emit('update');
            }
            return {
                mode: 'direct',
                commitSha: commitResult.commitSha,
                via: commitResult.via,
                patchMap,
                verifiedCount: contrib.contributors[actor.login]?.verifiedCount || 0
            };
        }
    }

    async function submitIssueOp(ops, summary, actor, { skipUiUpdate = false } = {}) {
        const useCtx = !state.pendingSubmissions.length && !state.preQueueReviewEntries.size
            ? { merged: state.merged, reviewIndex: state.reviewIndex }
            : null;
        applyBatchOps(state.effectiveOverrides, state.effectiveReviewStatus, ops, useCtx);

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
        if (!skipUiUpdate) {
            recomputeEffectiveState();
            schedulePendingPoll();
            emit('update');
        }
        return { mode: 'issueops', issueNumber: created.number, htmlUrl: created.html_url };
    }

    async function flushUploadQueue() {
        if (flushPromise) return await flushPromise;
        if (!state.uploadQueue.length) return;
        flushPromise = (async () => {
            state.uploadState.flushing = true;
            state.uploadState.lastError = '';
            emit('upload-status');
            let uploadedAny = false;
            let lastMode = 'direct';
            try {
                while (state.uploadQueue.length > 0) {
                    if (!state.auth.token || !state.auth.user?.login) break;
                    const auth = state.auth;
                    const actor = {
                        login: auth.user.login,
                        avatarUrl: auth.user.avatar_url || '',
                        htmlUrl: auth.user.html_url || `https://github.com/${auth.user.login}`
                    };
                    const maxBatch = auth.pushAccess ? 25 : 20;
                    const batch = state.uploadQueue
                        .filter(q => !q.actorLogin || q.actorLogin === auth.user.login)
                        .slice(0, maxBatch);
                    if (!batch.length) break;

                    const batchOps = batch.map(b => b.op);
                    const batchSummary = batch.length === 1
                        ? batch[0].summary
                        : `驗證並儲存 ${batch.length} 筆審核條目`;

                    let commitPatchMap = null;
                    try {
                        if (auth.pushAccess) {
                            const res = await directCommitOps(batchOps, batchSummary, actor, { skipUiUpdate: true });
                            lastMode = res.mode;
                            commitPatchMap = res.patchMap || null;
                        } else {
                            const res = await submitIssueOp(batchOps, batchSummary, actor, { skipUiUpdate: true });
                            lastMode = res.mode;
                            schedulePendingPoll();
                        }
                        const committedSet = new Set(batch.map(b => b.qid));
                        state.uploadQueue = state.uploadQueue.filter(q => !committedSet.has(q.qid));
                        writeStorage(STORAGE_UPLOAD_QUEUE, state.uploadQueue);
                        for (const b of batch) {
                            const rids = Array.isArray(b.affectedIds) && b.affectedIds.length
                                ? b.affectedIds
                                : (b.op?.type === 'review' && b.op.review?.id ? [b.op.review.id] : []);
                            for (const rid of rids) {
                                const stillQueued = state.uploadQueue.some(q =>
                                    (Array.isArray(q.affectedIds) && q.affectedIds.includes(rid))
                                    || (q.op?.type === 'review' && q.op.review?.id === rid)
                                );
                                if (stillQueued) {
                                    if (commitPatchMap?.has(rid)) {
                                        state.preQueueReviewEntries.set(rid, snapshotPreQueueEntry(commitPatchMap.get(rid)));
                                    }
                                } else {
                                    state.preQueueReviewEntries.delete(rid);
                                }
                            }
                        }
                        uploadedAny = true;
                        emit('upload-status');
                    } catch (err) {
                        const msg = err?.message || '雲端上傳失敗';
                        const isConflict = /已被其他貢獻者更新|找不到審核條目|無法定位/.test(msg);
                        if (isConflict) {
                            await syncDynamic({ silent: true, skipIssues: true });
                            const dropSet = new Set();
                            for (const b of batch) {
                                try {
                                    HsbtrEngine.applyOperation(state.effectiveOverrides, state.effectiveReviewStatus, b.op, state.base, { merged: state.merged, reviewIndex: state.reviewIndex });
                                } catch {
                                    dropSet.add(b.qid);
                                }
                            }
                            if (!dropSet.size) batch.forEach(b => dropSet.add(b.qid));
                            state.uploadQueue = state.uploadQueue.filter(q => !dropSet.has(q.qid));
                            writeStorage(STORAGE_UPLOAD_QUEUE, state.uploadQueue);
                            recomputeEffectiveState();
                            state.uploadState.lastError = msg;
                            emit('upload-error', { error: msg });
                            emit('update');
                            continue;
                        }
                        state.uploadState.lastError = msg;
                        emit('upload-error', { error: msg });
                        break;
                    }
                }
            } finally {
                state.uploadState.flushing = false;
                flushScheduled = false;
                flushPromise = null;
                if (uploadedAny) {
                    if (!state.uploadQueue.length && !state.pendingSubmissions.length) {
                        state.effectiveOverrides = state.cloudOverrides;
                        state.effectiveReviewStatus = state.cloudReviewStatus;
                        state.effectiveContributors = state.contributorsDoc;
                    }
                    emit('update', { fromUploadFlush: true, mode: lastMode });
                }
                emit('upload-status');
            }
        })();
        return await flushPromise;
    }

    function scheduleUploadFlush() {
        if (flushPromise || flushScheduled || !state.uploadQueue.length) return;
        flushScheduled = true;
        setTimeout(() => {
            flushScheduled = false;
            flushUploadQueue().catch(() => { /* handled via upload-error event */ });
        }, 0);
    }

    async function commitOps(ops, summary) {
        const auth = requireAuth();
        if (flushPromise || state.uploadQueue.length > 0) {
            await flushUploadQueue();
        }
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

    function hasPendingUploads() {
        return state.uploadQueue.length > 0 || state.uploadState.flushing;
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
            pendingSubmissions: state.pendingSubmissions,
            uploadQueueCount: state.uploadQueue.length,
            uploading: state.uploadState.flushing,
            uploadError: state.uploadState.lastError
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
        hasPendingUploads,
        flushUploadQueue,
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
        reviewPage: (req) => state.reviewIndex.page(req, state.effectiveReviewStatus),
        reviewNext: (req) => state.reviewIndex.next(req, state.effectiveReviewStatus),
        reviewExcerpt: (req) => state.reviewIndex.excerpt({ ...req, sentence: true }, state.effectiveReviewStatus),
        reviewSave: async (req, verify) => {
            const existing = state.reviewIndex.lookup(req.id);
            const label = existing ? (existing.context || existing.target?.key || existing.kind) : req.id.slice(0, 8);
            const op = {
                type: 'review',
                review: { id: req.id, revision: req.revision, value: req.value, roles: req.roles, excerpt: req.excerpt, verify }
            };
            const summary = `${verify ? '驗證並儲存' : '儲存'}審核條目 ${String(label).slice(0, 48)}`;
            return enqueueInteractiveOp(op, summary, req.id);
        },
        addExtraTranslation: async (extra) => {
            const payload = { ...(extra || {}), verify: extra?.verify !== false };
            const op = { type: 'extra', extra: payload };
            const label = payload.kind === 'item'
                ? `${payload.itemId || payload.nameEn || 'ITEM'}`
                : String(payload.source || '').trim();
            const summary = `新增翻譯內容 ${String(label).slice(0, 48)}`;
            return enqueueInteractiveOp(op, summary);
        }
    };

    function enqueueInteractiveOp(op, summary, preferredId = '') {
        const auth = requireAuth();
        const actor = {
            login: auth.user.login,
            avatarUrl: auth.user.avatar_url || '',
            htmlUrl: auth.user.html_url || `https://github.com/${auth.user.login}`
        };
        const res = HsbtrEngine.applyOperation(
            state.effectiveOverrides,
            state.effectiveReviewStatus,
            op,
            state.base,
            { merged: state.merged, reviewIndex: state.reviewIndex }
        );
        const touched = res.reviewPatch?.extraPatches?.length
            ? res.reviewPatch.extraPatches
            : (res.reviewPatch?.entry ? [res.reviewPatch.entry] : []);
        const affectedIds = [];
        const changed = Boolean(res.reviewPatch?.changedValue);
        for (const t of touched) {
            if (!t?.id) continue;
            affectedIds.push(t.id);
            if (changed && !state.preQueueReviewEntries.has(t.id)) {
                const pre = state.reviewIndex.lookup(t.id);
                state.preQueueReviewEntries.set(t.id, pre ? snapshotPreQueueEntry(pre) : null);
            }
        }
        if (!HsbtrEngine.applyMergedPatch(state.merged, state.reviewIndex, res)) {
            state.merged = HsbtrEngine.mergeDictionaries(state.base, res.overrides);
            state.reviewIndex = HsbtrEngine.createReviewIndex(state.merged);
        }
        state.effectiveOverrides = res.overrides;
        state.effectiveReviewStatus = res.reviewStatus;
        state.effectiveContributors = HsbtrEngine.updateContributors(state.effectiveContributors, actor, res.verifiedEntryIds, res.editCount);

        state.uploadQueue.push({
            qid: randomUrlToken(8),
            op,
            summary,
            actorLogin: actor.login,
            affectedIds,
            enqueuedAt: Date.now()
        });
        writeStorage(STORAGE_UPLOAD_QUEUE, state.uploadQueue);
        state.uploadState.lastError = '';

        scheduleUploadFlush();
        emit('upload-status');
        emit('update', { reason: 'review-enqueue' });

        const targetId = preferredId || res.reviewPatch?.entry?.id || affectedIds[0];
        const updated = targetId ? state.reviewIndex.lookup(targetId) : null;
        if (!updated) throw new Error('儲存成功，但找不到更新後條目，請重新同步。');
        return state.reviewIndex.toJson(updated, state.effectiveReviewStatus);
    }
})(typeof globalThis !== 'undefined' ? globalThis : window);
