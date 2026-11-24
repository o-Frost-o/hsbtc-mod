/**
 * Hypixel SkyBlock Translation Mod (zh_tw) — Standalone GitHub Pages UI Controller
 * Pure Vanilla JavaScript (Zero external CDN / npm dependencies)
 */

(() => {
    'use strict';

    const escapeHtml = (str) => {
        if (!str) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    };

    const parseMinecraftText = (raw) => {
        if (!raw) return '';
        const escaped = escapeHtml(raw);
        let activeClasses = [];
        let html = '';
        const parts = escaped.split(/(?:§|&)([0-9a-fk-or])/gi);

        for (let i = 0; i < parts.length; i++) {
            if (i % 2 === 1) {
                const code = parts[i].toLowerCase();
                if (code === 'r') {
                    activeClasses = [];
                } else if ('0123456789abcdef'.includes(code)) {
                    activeClasses = activeClasses.filter(c => !c.startsWith('mc-') || 'lmon'.includes(c[3]));
                    activeClasses.push(`mc-${code}`);
                } else if ('lmon'.includes(code)) {
                    activeClasses.push(`mc-${code}`);
                }
            } else {
                const text = parts[i];
                if (text.length > 0) {
                    html += activeClasses.length > 0
                        ? `<span class="${activeClasses.join(' ')}">${text}</span>`
                        : text;
                }
            }
        }
        return html;
    };

    const stripColorCodes = (str) => str ? String(str).replace(/(?:§|&)[0-9a-fk-or]/gi, '') : '';

    const State = {
        tokens: {},
        tokenFilter: { query: '', category: 'ALL' },
        itemFilter: { query: '', category: '', page: 1, pageSize: 30 },
        itemsList: [],
        untranslatedList: [],
        untranslatedFilter: '',
        activeModal: null
    };

    const showToast = (message, type = 'info') => {
        const container = document.getElementById('toast-container');
        if (!container) return;
        const toast = document.createElement('div');
        toast.className = `toast toast-${type}`;
        const icons = { success: '✔', error: '✖', warning: '⚠', info: 'ℹ' };
        toast.innerHTML = `<span class="toast-icon">${icons[type] || 'ℹ'}</span><span class="toast-msg">${escapeHtml(message)}</span>`;
        container.appendChild(toast);
        setTimeout(() => {
            toast.style.opacity = '0';
            toast.style.transform = 'translateY(10px)';
            toast.style.transition = 'all 0.3s ease';
            setTimeout(() => toast.remove(), 300);
        }, 3600);
    };

    const downloadJson = (filename, data) => {
        const blob = new Blob([JSON.stringify(data, null, 2) + '\n'], { type: 'application/json;charset=utf-8' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        a.remove();
        URL.revokeObjectURL(url);
    };

    // =========================================================================
    // Cloud Sync Status & GitHub Authentication UI
    // =========================================================================

    const refreshHeaderAndStatus = () => {
        const snap = HsbtrCloud.getSnapshot();
        const badge = document.getElementById('conn-badge');
        const connText = document.getElementById('conn-text');
        const sbConn = document.getElementById('sb-conn-status');

        if (!snap.ready) {
            badge.className = 'status-badge';
            connText.textContent = '載入雲端資料庫…';
        } else if (snap.pendingCount > 0) {
            badge.className = 'status-badge connected';
            connText.textContent = `⏳ 同步寫入中 (${snap.pendingCount})`;
            sbConn.textContent = `雲端狀態: ⏳ ${snap.pendingCount} 筆提交正由 GitHub Actions 寫入`;
        } else {
            const label = snap.sourceMode === 'github-api' ? 'GitHub API 即時同步'
                : snap.sourceMode === 'github-raw' ? 'GitHub 雲端 HEAD' : '本地靜態快照';
            badge.className = 'status-badge connected';
            connText.textContent = label;
            sbConn.textContent = `雲端狀態: 🟢 ${label} (${snap.config.owner}/${snap.config.repo})`;
        }

        const tokenCount = Object.keys(snap.merged?.tokens || {}).length;
        const itemCount = Object.keys(snap.merged?.items || {}).length;
        const verifiedCount = Object.keys(snap.reviewStatus || {}).length;
        const overrideCount = HsbtrCloud.countOverrides();

        document.getElementById('sb-tokens').textContent = tokenCount;
        document.getElementById('sb-items').textContent = itemCount;
        document.getElementById('sb-verified').textContent = verifiedCount;
        document.getElementById('sb-untranslated').textContent = State.untranslatedList.length;
        document.getElementById('stat-verified-count').textContent = verifiedCount;
        document.getElementById('stat-override-count').textContent = overrideCount;
        document.getElementById('badge-token-count').textContent = tokenCount;
        document.getElementById('badge-item-count').textContent = itemCount;
        document.getElementById('badge-capture-count').textContent = State.untranslatedList.length;

        const cDoc = snap.contributorsDoc || { threshold: 25, contributors: {} };
        const threshold = cDoc.threshold || 25;
        const allUsers = Object.values(cDoc.contributors || {});
        const officialCount = allUsers.filter(u => (u.verifiedCount || 0) >= threshold).length;
        document.getElementById('badge-contrib-count').textContent = officialCount;

        const authBtn = document.getElementById('btn-github-auth');
        const userChip = document.getElementById('github-user-chip');
        const sbAuth = document.getElementById('sb-auth-status');

        if (snap.auth.loggedIn && snap.auth.user) {
            const login = snap.auth.user.login;
            const me = cDoc.contributors?.[login];
            const myCount = me?.verifiedCount || 0;

            authBtn.classList.add('hidden');
            userChip.classList.remove('hidden');
            document.getElementById('github-user-avatar').src = snap.auth.user.avatar_url || '';
            document.getElementById('github-user-login').textContent = '@' + login;
            const modeEl = document.getElementById('github-auth-mode');
            const modeLabel = snap.auth.pushAccess ? '直寫授權' : 'IssueOps';
            modeEl.textContent = modeLabel;
            modeEl.className = 'auth-mode-pill' + (snap.auth.pushAccess ? '' : ' issueops');
            document.getElementById('github-user-progress').textContent = myCount >= threshold
                ? `✔ ${myCount} (貢獻者)` : `${myCount} / ${threshold}`;
            const methodTag = snap.auth.method === 'oauth' ? 'OAuth' : 'PAT';
            sbAuth.textContent = `GitHub: @${login} (${methodTag} · ${snap.auth.pushAccess ? 'Push 直寫' : 'IssueOps'})`;
        } else {
            authBtn.classList.remove('hidden');
            userChip.classList.add('hidden');
            sbAuth.textContent = 'GitHub: 未登入（唯讀瀏覽）';
        }
    };

    const openAuthModal = () => {
        const err = document.getElementById('github-auth-error');
        const oauthStatus = document.getElementById('github-oauth-status');
        const oauthBtn = document.getElementById('btn-github-oauth-login');

        err.textContent = '';
        err.classList.add('hidden');
        oauthStatus.textContent = '';
        oauthStatus.classList.add('hidden');

        const configured = HsbtrCloud.isOAuthConfigured();
        oauthBtn.disabled = !configured;

        document.getElementById('input-github-token').value = '';
        document.getElementById('modal-github-auth').classList.remove('hidden');
        State.activeModal = 'modal-github-auth';
        if (configured) {
            setTimeout(() => oauthBtn.focus(), 40);
        } else {
            setTimeout(() => document.getElementById('input-github-token').focus(), 40);
        }
    };

    const closeModal = (modalId) => {
        const modal = document.getElementById(modalId);
        if (modal) modal.classList.add('hidden');
        if (State.activeModal === modalId) {
            const visible = [...document.querySelectorAll('.modal-backdrop:not(.hidden)')].pop();
            State.activeModal = visible ? visible.id : null;
        }
    };

    // =========================================================================
    // Tab 1: Token Dictionary Controller
    // =========================================================================

    const renderTokens = () => {
        const tbody = document.getElementById('tokens-tbody');
        const q = State.tokenFilter.query.toLowerCase().trim();
        const cat = State.tokenFilter.category;

        const filtered = Object.entries(State.tokens).filter(([id, tok]) => {
            if (cat !== 'ALL' && (tok.category || 'general').toLowerCase() !== cat.toLowerCase()) return false;
            if (!q) return true;
            return id.toLowerCase().includes(q)
                || (tok.en && tok.en.toLowerCase().includes(q))
                || (tok.zh_tw && tok.zh_tw.toLowerCase().includes(q));
        });

        document.getElementById('badge-token-count').textContent = Object.keys(State.tokens).length;
        document.getElementById('tokens-pagination-info').textContent = `顯示 ${filtered.length} / 共 ${Object.keys(State.tokens).length} 個詞條`;

        if (!filtered.length) {
            tbody.innerHTML = `<tr><td colspan="6" class="table-loading">查無相符詞條</td></tr>`;
            return;
        }

        const visible = filtered.slice(0, 500);
        tbody.innerHTML = visible.map(([id, tok]) => `
            <tr data-token-id="${escapeHtml(id)}">
                <td><span class="token-code">{${escapeHtml(id)}}</span></td>
                <td>${escapeHtml(tok.en || '')}</td>
                <td>${parseMinecraftText(`${tok.color || ''}${tok.zh_tw || ''}`)}</td>
                <td><span class="category-tag">${escapeHtml(tok.category || 'general')}</span></td>
                <td><small style="color:var(--text-muted)">${escapeHtml(tok.color || '')} ${escapeHtml(tok.glyph || '')}</small></td>
                <td class="text-center"><button class="btn btn-secondary btn-xs btn-edit-token" data-id="${escapeHtml(id)}">編輯</button></td>
            </tr>
        `).join('') + (filtered.length > 500 ? `<tr><td colspan="6" class="table-loading">為保持流暢僅顯示前 500 筆，請輸入關鍵字進一步縮小範圍（共 ${filtered.length} 筆）。</td></tr>` : '');
    };

    const openTokenModal = (tokenId = null) => {
        const modal = document.getElementById('modal-token');
        const idInput = document.getElementById('form-token-id');
        const tok = tokenId ? State.tokens[tokenId] : null;
        document.getElementById('modal-token-title').textContent = tok ? `編輯詞條: ${tokenId}` : '新增 Token 詞條';
        idInput.value = tokenId || '';
        idInput.readOnly = Boolean(tok);
        document.getElementById('form-token-en').value = tok?.en || '';
        document.getElementById('form-token-zh').value = tok?.zh_tw || '';
        document.getElementById('form-token-cat').value = tok?.category || 'stat';
        document.getElementById('form-token-color').value = tok?.color || '';
        updateTokenModalPreview();
        modal.classList.remove('hidden');
        State.activeModal = 'modal-token';
    };

    const updateTokenModalPreview = () => {
        const id = document.getElementById('form-token-id').value.trim();
        const zh = document.getElementById('form-token-zh').value.trim();
        const color = document.getElementById('form-token-color').value.trim();
        document.getElementById('form-token-tag-preview').textContent = id ? `{${id}}` : '{TOKEN_ID}';
        document.getElementById('form-token-rendered-preview').innerHTML = parseMinecraftText(`${color}${zh || '預覽內容'}`);
    };

    const saveTokenModal = async () => {
        const tokenId = document.getElementById('form-token-id').value.trim().toUpperCase();
        const en = document.getElementById('form-token-en').value.trim();
        const zh_tw = document.getElementById('form-token-zh').value.trim();
        const category = document.getElementById('form-token-cat').value.trim() || 'general';
        const color = document.getElementById('form-token-color').value.trim();

        if (!tokenId || !zh_tw) {
            showToast('Token ID 與繁體中文翻譯為必填項目！', 'warning');
            return;
        }
        try {
            const res = await HsbtrCloud.saveToken({ tokenId, en, zh_tw, category, color });
            closeModal('modal-token');
            showToast(res.mode === 'issueops' ? `詞條 ${tokenId} 已建立雲端提交 (Issue #${res.issueNumber})` : `詞條 ${tokenId} 已寫入雲端資料庫！`, 'success');
        } catch (e) {
            showToast(`儲存失敗: ${e.message}`, 'error');
        }
    };

    // =========================================================================
    // Tab 2: Item Translations Controller
    // =========================================================================

    const fetchAndRenderItems = () => {
        const tbody = document.getElementById('items-tbody');
        if (!HsbtrCloud.getSnapshot().ready) return;

        const q = State.itemFilter.query.trim();
        const cat = State.itemFilter.category;
        const page = State.itemFilter.page;
        const pageSize = State.itemFilter.pageSize;
        const offset = (page - 1) * pageSize;

        const { total, rows } = HsbtrCloud.queryItems(q, cat, pageSize, offset);
        State.itemsList = rows;

        document.getElementById('item-prev-page').disabled = page <= 1;
        document.getElementById('item-next-page').disabled = offset + rows.length >= total;
        document.getElementById('item-page-indicator').textContent = `第 ${page} 頁`;
        document.getElementById('items-pagination-info').textContent = `本頁顯示 ${rows.length} / 共 ${total} 筆`;

        if (!rows.length) {
            tbody.innerHTML = `<tr><td colspan="6" class="table-loading">查無相符道具</td></tr>`;
            return;
        }
        const rarityColors = {
            COMMON: '#ffffff', UNCOMMON: '#55ff55', RARE: '#5555ff', EPIC: '#aa00aa',
            LEGENDARY: '#ffaa00', MYTHIC: '#ff55ff', DIVINE: '#55ffff', SPECIAL: '#ff5555'
        };
        tbody.innerHTML = rows.map(item => `
            <tr data-item-id="${escapeHtml(item.id)}">
                <td><span class="token-code">${escapeHtml(item.id)}</span></td>
                <td>${escapeHtml(item.name_en || '')}</td>
                <td>${parseMinecraftText(item.name_zh_tw || '')}</td>
                <td><span class="category-tag">${escapeHtml(item.category || 'MISC')}</span></td>
                <td><span class="rarity-tag" style="color: ${rarityColors[item.rarity] || '#ffffff'}">${escapeHtml(item.rarity || 'COMMON')}</span></td>
                <td class="text-center"><button class="btn btn-secondary btn-xs btn-edit-item" data-id="${escapeHtml(item.id)}">編輯</button></td>
            </tr>
        `).join('');
    };

    const openItemModal = (itemId) => {
        const item = HsbtrCloud.getSnapshot().merged?.items?.[itemId] || State.itemsList.find(i => i.id === itemId);
        if (!item) return;
        document.getElementById('form-item-id').value = item.id;
        document.getElementById('form-item-en').value = item.name_en || '';
        document.getElementById('form-item-zh').value = item.name_zh_tw || '';
        document.getElementById('form-item-cat').value = item.category || 'MISC';
        document.getElementById('form-item-rarity').value = item.rarity || 'COMMON';
        document.getElementById('form-item-lore').value = (item.lore_template || []).join('\n');
        document.getElementById('modal-item').classList.remove('hidden');
        State.activeModal = 'modal-item';
    };

    const saveItemModal = async () => {
        const id = document.getElementById('form-item-id').value;
        const name_en = document.getElementById('form-item-en').value;
        const name_zh_tw = document.getElementById('form-item-zh').value.trim();
        const category = document.getElementById('form-item-cat').value;
        const rarity = document.getElementById('form-item-rarity').value;
        const lore_template = document.getElementById('form-item-lore').value.split('\n');

        try {
            const res = await HsbtrCloud.saveItem({ id, name_en, name_zh_tw, category, rarity, lore_template });
            closeModal('modal-item');
            showToast(res.mode === 'issueops' ? `道具 ${id} 已建立雲端提交 (Issue #${res.issueNumber})` : `道具 ${id} 已寫入雲端資料庫！`, 'success');
        } catch (e) {
            showToast(`儲存失敗: ${e.message}`, 'error');
        }
    };

    // =========================================================================
    // Tab 3: Live Preview Controller (Pure Client JS Engine)
    // =========================================================================

    const SAMPLES = {
        hyperion: {
            name: 'Fabled Hyperion ✪✪✪✪✪',
            lore: [
                'Gear Score: 610', 'Damage: +260', 'Strength: +150', 'Crit Damage: +50%', '',
                'Ability: Wither Impact  RIGHT CLICK', 'Teleport 10 blocks ahead and implode...',
                'Mana Cost: 150', '', 'Requires The Catacombs Floor VII Completion.', '§d§lMYTHIC SWORD'
            ]
        },
        aote: {
            name: 'Heroic Aspect of the End',
            lore: [
                'Damage: +100', 'Strength: +100', '', 'Ability: Instant Transmission  RIGHT CLICK',
                'Teleport 8 blocks ahead of you.', 'Mana Cost: 50', '', '§9§lRARE SWORD'
            ]
        },
        divan: {
            name: 'Jaded Chestplate of Divan',
            lore: [
                'Defense: +250', 'Mining Speed: +120', 'Mining Fortune: +60',
                'Gemstones: [⚔] [⛏]', '', '§6§lLEGENDARY ARMOR'
            ]
        }
    };

    const executePreview = () => {
        const snap = HsbtrCloud.getSnapshot();
        if (!snap.ready) return;
        const nameInput = document.getElementById('preview-item-name').value.trim();
        const loreLines = document.getElementById('preview-item-lore').value.split('\n');
        const result = HsbtrEngine.previewItem(snap.merged, nameInput, loreLines);

        document.getElementById('mc-preview-title').innerHTML = parseMinecraftText(result.name || nameInput);
        document.getElementById('mc-preview-lore').innerHTML = (result.lore || []).map(line =>
            !line.trim() ? '<div class="mc-line">&nbsp;</div>' : `<div class="mc-line">${parseMinecraftText(line)}</div>`
        ).join('');

        const clean = stripColorCodes(nameInput);
        document.getElementById('chip-reforge').textContent = `詞綴: ${clean.split(' ')[0] || '無'}`;
        document.getElementById('chip-base').textContent = `本體: ${result.name ? stripColorCodes(result.name) : clean}`;
        document.getElementById('chip-stars').textContent = clean.includes('✪') ? '星數: ✪' : '星數: 無';
        showToast('即時預覽已更新', 'info');
    };

    // =========================================================================
    // Tab 4: Untranslated Capture JSON File Queue
    // =========================================================================

    const renderUntranslated = () => {
        const tbody = document.getElementById('capture-tbody');
        const records = State.untranslatedList;
        document.getElementById('badge-capture-count').textContent = records.length;
        document.getElementById('capture-count-info').textContent = `共載入 ${records.length} 條未收錄文本`;

        const filter = State.untranslatedFilter.toLowerCase().trim();
        const filtered = records.filter(r => !filter || (r.cleanText && r.cleanText.toLowerCase().includes(filter)));

        if (!filtered.length) {
            tbody.innerHTML = `<tr><td colspan="5" class="table-loading">${records.length ? '沒有符合過濾條件的條目' : '請匯入遊戲設定中的 untranslated_capture.json'}</td></tr>`;
            return;
        }
        tbody.innerHTML = filtered.slice(0, 300).map(r => `
            <tr>
                <td><span class="token-code">${escapeHtml(r.hash ? r.hash.substring(0, 8) : '--------')}</span></td>
                <td><strong>${escapeHtml(r.cleanText)}</strong></td>
                <td>${parseMinecraftText(r.rawText || r.cleanText)}</td>
                <td><span class="category-tag">${escapeHtml(r.seenCount || 1)} 次</span></td>
                <td class="text-center">
                    <button class="btn btn-primary btn-xs btn-workbench-capture" data-hash="${escapeHtml(r.hash)}">翻譯</button>
                    <button class="btn btn-secondary btn-xs btn-promote-token" data-text="${escapeHtml(r.cleanText)}">+ 詞條</button>
                </td>
            </tr>
        `).join('');
    };

    const loadCaptureFile = (file) => {
        const reader = new FileReader();
        reader.onload = (e) => {
            try {
                const parsed = JSON.parse(e.target.result);
                const list = Array.isArray(parsed) ? parsed : Object.values(parsed || {});
                State.untranslatedList = list.filter(r => r && (r.cleanText || r.rawText));
                HsbtrCloud.writeStorage(HsbtrCloud.STORAGE_CAPTURE, State.untranslatedList.slice(0, 2000));
                renderUntranslated();
                refreshHeaderAndStatus();
                showToast(`已匯入 ${State.untranslatedList.length} 條未翻譯擷取紀錄`, 'success');
            } catch (err) {
                showToast(`無法解析擷取檔: ${err.message}`, 'error');
            }
        };
        reader.readAsText(file, 'UTF-8');
    };

    // =========================================================================
    // Tab 5: Contributors Leaderboard & Pending Submissions
    // =========================================================================

    const renderContributors = () => {
        const snap = HsbtrCloud.getSnapshot();
        const cDoc = snap.contributorsDoc || { threshold: 25, contributors: {} };
        const threshold = cDoc.threshold || 25;
        const users = Object.values(cDoc.contributors || {});
        const official = users.filter(u => (u.verifiedCount || 0) >= threshold)
            .sort((a, b) => (b.verifiedCount || 0) - (a.verifiedCount || 0) || (b.editCount || 0) - (a.editCount || 0));

        document.getElementById('contrib-threshold-val').textContent = threshold;
        document.getElementById('contrib-total-verified').textContent = Object.keys(snap.reviewStatus || {}).length;
        document.getElementById('contrib-official-count').textContent = official.length;
        document.getElementById('contrib-pending-count').textContent = snap.pendingCount;

        const personal = document.getElementById('contrib-personal-box');
        if (snap.auth.loggedIn && snap.auth.user) {
            const login = snap.auth.user.login;
            const me = cDoc.contributors?.[login] || { verifiedCount: 0, editCount: 0 };
            const pct = Math.min(100, Math.round(((me.verifiedCount || 0) / threshold) * 100));
            const remaining = Math.max(0, threshold - (me.verifiedCount || 0));
            personal.innerHTML = `
                <div style="display:flex; justify-content:space-between; align-items:center; flex-wrap:wrap; gap:8px;">
                    <strong>您的貢獻進度 (@${escapeHtml(login)})</strong>
                    <span style="font-size:13px; color:var(--mc-aqua);">${me.verifiedCount || 0} / ${threshold} 條唯一驗證（累計編輯 ${me.editCount || 0} 次）</span>
                </div>
                <div class="contrib-progress-track"><div class="contrib-progress-fill" style="width:${pct}%"></div></div>
                <p style="font-size:12px; color:var(--text-secondary);">
                    ${remaining === 0 ? '🎉 您已達成官方貢獻者門檻，已列於下方貢獻者名單！' : `再完成 ${remaining} 條不同條目的審核驗證即可進入官方貢獻者名單。`}
                </p>
            `;
        } else {
            personal.innerHTML = `
                <div style="display:flex; justify-content:space-between; align-items:center; flex-wrap:wrap; gap:10px;">
                    <span style="font-size:13px; color:var(--text-secondary);">登入 GitHub 帳號即可累計翻譯驗證條目數，達 ${threshold} 條即可名列貢獻者榜單。</span>
                    <button id="btn-contrib-signin" class="btn btn-primary btn-sm" type="button">🔑 立即登入 GitHub</button>
                </div>
            `;
            document.getElementById('btn-contrib-signin')?.addEventListener('click', openAuthModal);
        }

        const grid = document.getElementById('contrib-roster-grid');
        if (!official.length) {
            grid.innerHTML = `<div style="grid-column:1/-1; color:var(--text-secondary); padding:18px; text-align:center; background:var(--bg-input); border-radius:8px;">
                目前尚無成員累積達 ${threshold} 條唯一驗證。歡迎至「翻譯審核」分頁參與驗證，成為第一位官方貢獻者！
            </div>`;
        } else {
            grid.innerHTML = official.map((u, idx) => `
                <div class="contrib-member-card">
                    <img class="contrib-member-avatar" src="${escapeHtml(u.avatarUrl || `https://github.com/${u.login}.png`)}" alt="${escapeHtml(u.login)}">
                    <div class="contrib-member-info">
                        <a class="contrib-member-login" href="${escapeHtml(u.htmlUrl || `https://github.com/${u.login}`)}" target="_blank" rel="noopener noreferrer">#${idx + 1} @${escapeHtml(u.login)}</a>
                        <div style="margin-top:4px; font-size:12px; color:var(--text-secondary);">
                            <span class="contrib-badge">✓ ${u.verifiedCount} 條已驗證</span>
                            <span style="margin-left:6px;">編輯 ${u.editCount || 0} 次</span>
                        </div>
                    </div>
                </div>
            `).join('');
        }

        const pendingWrap = document.getElementById('contrib-pending-wrap');
        const pendingList = document.getElementById('contrib-pending-list');
        if (!snap.pendingSubmissions.length) {
            pendingWrap.classList.add('hidden');
        } else {
            pendingWrap.classList.remove('hidden');
            pendingList.innerHTML = snap.pendingSubmissions.map(sub => `
                <li class="pending-item">
                    <span>⏳ <strong>@${escapeHtml(sub.submittedBy || 'contributor')}</strong> — ${escapeHtml(sub.summary || '雲端翻譯提交')}</span>
                    ${sub.htmlUrl ? `<a href="${escapeHtml(sub.htmlUrl)}" target="_blank" rel="noopener noreferrer">Issue #${sub.issueNumber} ↗</a>` : ''}
                </li>
            `).join('');
        }
    };

    // =========================================================================
    // Cloud Update Handler & Event Wiring
    // =========================================================================

    const handleCloudStateChange = () => {
        const snap = HsbtrCloud.getSnapshot();
        State.tokens = snap.merged?.tokens || {};
        document.dispatchEvent(new CustomEvent('hsbtr:workbench-tokens', { detail: State.tokens }));
        refreshHeaderAndStatus();
        renderTokens();
        if (document.getElementById('tab-items').classList.contains('active')) fetchAndRenderItems();
        if (document.getElementById('tab-contributors').classList.contains('active')) renderContributors();
    };

    document.addEventListener('hsbtr:cloud-ready', handleCloudStateChange);
    document.addEventListener('hsbtr:cloud-update', handleCloudStateChange);
    document.addEventListener('hsbtr:cloud-auth', () => {
        refreshHeaderAndStatus();
        if (document.getElementById('tab-contributors').classList.contains('active')) renderContributors();
    });
    document.addEventListener('hsbtr:cloud-oauth-success', () => {
        const snap = HsbtrCloud.getSnapshot();
        closeModal('modal-github-auth');
        if (snap.auth.loggedIn && snap.auth.user) {
            showToast(`已透過 GitHub 官方帳號登入 @${snap.auth.user.login} (${snap.auth.pushAccess ? '直寫模式' : 'IssueOps 模式'})`, 'success');
        }
    });
    document.addEventListener('hsbtr:cloud-oauth-error', () => {
        const res = HsbtrCloud.consumeOAuthResult();
        if (res && !res.ok && res.error) {
            const oauthStatus = document.getElementById('github-oauth-status');
            if (oauthStatus) {
                oauthStatus.textContent = res.error;
                oauthStatus.classList.remove('hidden');
            }
            showToast(res.error, 'error');
        }
    });
    document.addEventListener('hsbtr:cloud-merged', () => showToast('GitHub Actions 已完成雲端合併，已載入最新共享資料！', 'success'));
    document.addEventListener('hsbtr:require-auth', () => {
        showToast('請先驗證 GitHub 帳號再進行編輯或驗證操作', 'warning');
        openAuthModal();
    });

    document.addEventListener('DOMContentLoaded', async () => {
        State.untranslatedList = HsbtrCloud.readStorage(HsbtrCloud.STORAGE_CAPTURE, []);
        renderUntranslated();

        document.querySelectorAll('.nav-tab').forEach(btn => {
            btn.addEventListener('click', () => {
                const tab = btn.getAttribute('data-tab');
                document.querySelectorAll('.nav-tab').forEach(b => b.classList.remove('active'));
                document.querySelectorAll('.tab-panel').forEach(p => p.classList.remove('active'));
                btn.classList.add('active');
                document.getElementById(`tab-${tab}`)?.classList.add('active');

                if (tab === 'tokens') renderTokens();
                if (tab === 'items') fetchAndRenderItems();
                if (tab === 'capture') renderUntranslated();
                if (tab === 'contributors') renderContributors();
            });
        });

        // Auth UI (Official OAuth + PAT Fallback)
        document.getElementById('btn-github-auth').addEventListener('click', openAuthModal);
        document.getElementById('btn-github-logout').addEventListener('click', () => {
            HsbtrCloud.logout();
            showToast('已登出 GitHub 驗證帳號', 'info');
        });
        document.getElementById('btn-github-oauth-login').addEventListener('click', async () => {
            const oauthBtn = document.getElementById('btn-github-oauth-login');
            const oauthStatus = document.getElementById('github-oauth-status');
            oauthStatus.classList.add('hidden');
            oauthBtn.disabled = true;
            try {
                const res = await HsbtrCloud.startOAuthLogin();
                if (res.mode === 'popup') {
                    showToast('已開啟 GitHub 官方授權視窗，請完成授權…', 'info');
                }
            } catch (err) {
                oauthStatus.textContent = err.message;
                oauthStatus.classList.remove('hidden');
            } finally {
                oauthBtn.disabled = !HsbtrCloud.isOAuthConfigured();
            }
        });
        const verifyAuthAction = async (e) => {
            if (e) e.preventDefault();
            const token = document.getElementById('input-github-token').value;
            const errEl = document.getElementById('github-auth-error');
            const btn = document.getElementById('btn-verify-github-token');
            btn.disabled = true;
            errEl.classList.add('hidden');
            try {
                const auth = await HsbtrCloud.verifyToken(token, { authMethod: 'pat' });
                closeModal('modal-github-auth');
                showToast(`已登入 GitHub 帳號 @${auth.user.login} (${auth.pushAccess ? '直寫模式' : 'IssueOps 模式'})`, 'success');
                renderContributors();
            } catch (err) {
                errEl.textContent = err.message;
                errEl.classList.remove('hidden');
            } finally {
                btn.disabled = false;
            }
        };
        document.getElementById('github-token-form').addEventListener('submit', verifyAuthAction);

        // Cloud Sync Buttons
        const syncAction = async () => {
            try {
                await HsbtrCloud.syncDynamic();
                showToast('已從 GitHub 同步最新共享翻譯資料庫', 'success');
            } catch (e) {
                showToast(`雲端同步失敗: ${e.message}`, 'error');
            }
        };
        document.getElementById('btn-sync-cloud').addEventListener('click', syncAction);
        document.getElementById('btn-sync-cloud-card').addEventListener('click', syncAction);
        document.getElementById('btn-contrib-refresh').addEventListener('click', syncAction);

        // Token Search & Modal
        const tokenSearch = document.getElementById('token-search');
        const tokenClear = document.getElementById('token-search-clear');
        tokenSearch.addEventListener('input', (e) => {
            State.tokenFilter.query = e.target.value;
            tokenClear.classList.toggle('hidden', !e.target.value);
            renderTokens();
        });
        tokenClear.addEventListener('click', () => {
            tokenSearch.value = '';
            tokenClear.classList.add('hidden');
            State.tokenFilter.query = '';
            renderTokens();
        });
        document.getElementById('token-category-filters').addEventListener('click', (e) => {
            if (!e.target.classList.contains('filter-pill')) return;
            document.querySelectorAll('.filter-pill').forEach(p => p.classList.remove('active'));
            e.target.classList.add('active');
            State.tokenFilter.category = e.target.getAttribute('data-category');
            renderTokens();
        });
        document.getElementById('btn-add-token').addEventListener('click', () => openTokenModal(null));
        document.getElementById('btn-save-token').addEventListener('click', saveTokenModal);
        document.getElementById('tokens-tbody').addEventListener('click', (e) => {
            const btn = e.target.closest('.btn-edit-token');
            if (btn) openTokenModal(btn.getAttribute('data-id'));
        });
        ['form-token-id', 'form-token-zh', 'form-token-color'].forEach(id => {
            document.getElementById(id).addEventListener('input', updateTokenModalPreview);
        });

        // Items Search & Modal
        let itemDebounce = null;
        document.getElementById('item-search').addEventListener('input', (e) => {
            clearTimeout(itemDebounce);
            itemDebounce = setTimeout(() => {
                State.itemFilter.query = e.target.value;
                State.itemFilter.page = 1;
                fetchAndRenderItems();
            }, 180);
        });
        document.getElementById('item-category-select').addEventListener('change', (e) => {
            State.itemFilter.category = e.target.value;
            State.itemFilter.page = 1;
            fetchAndRenderItems();
        });
        document.getElementById('item-prev-page').addEventListener('click', () => {
            if (State.itemFilter.page > 1) { State.itemFilter.page--; fetchAndRenderItems(); }
        });
        document.getElementById('item-next-page').addEventListener('click', () => {
            State.itemFilter.page++;
            fetchAndRenderItems();
        });
        document.getElementById('items-tbody').addEventListener('click', (e) => {
            const btn = e.target.closest('.btn-edit-item');
            if (btn) openItemModal(btn.getAttribute('data-id'));
        });
        document.getElementById('btn-save-item').addEventListener('click', saveItemModal);

        // Live Preview
        document.getElementById('btn-execute-preview').addEventListener('click', executePreview);
        document.querySelectorAll('.sample-buttons button').forEach(btn => {
            btn.addEventListener('click', () => {
                const sample = SAMPLES[btn.getAttribute('data-sample')];
                if (!sample) return;
                document.getElementById('preview-item-name').value = sample.name;
                document.getElementById('preview-item-lore').value = sample.lore.join('\n');
                executePreview();
            });
        });

        // Capture JSON Upload & Actions
        const capZone = document.getElementById('capture-drop-zone');
        const capInput = document.getElementById('capture-file-input');
        document.getElementById('btn-import-capture').addEventListener('click', () => capInput.click());
        capInput.addEventListener('change', (e) => { if (e.target.files[0]) loadCaptureFile(e.target.files[0]); });
        capZone.addEventListener('dragover', (e) => { e.preventDefault(); capZone.classList.add('dragover'); });
        capZone.addEventListener('dragleave', () => capZone.classList.remove('dragover'));
        capZone.addEventListener('drop', (e) => {
            e.preventDefault();
            capZone.classList.remove('dragover');
            if (e.dataTransfer.files[0]) loadCaptureFile(e.dataTransfer.files[0]);
        });
        document.getElementById('btn-clear-capture').addEventListener('click', () => {
            State.untranslatedList = [];
            HsbtrCloud.writeStorage(HsbtrCloud.STORAGE_CAPTURE, []);
            renderUntranslated();
            refreshHeaderAndStatus();
        });
        document.getElementById('capture-search').addEventListener('input', (e) => {
            State.untranslatedFilter = e.target.value;
            renderUntranslated();
        });
        document.getElementById('capture-tbody').addEventListener('click', (e) => {
            const translate = e.target.closest('.btn-workbench-capture');
            if (translate) {
                const item = State.untranslatedList.find(r => r.hash === translate.dataset.hash);
                if (item) document.dispatchEvent(new CustomEvent('hsbtr:workbench-source', { detail: item }));
                return;
            }
            const btn = e.target.closest('.btn-promote-token');
            if (btn) {
                const text = btn.getAttribute('data-text');
                openTokenModal('UI_' + text.toUpperCase().replace(/[^A-Z0-9]+/g, '_').substring(0, 24));
                document.getElementById('form-token-en').value = text;
            }
        });

        // Client-Side CSV Export & Import
        document.getElementById('btn-export-csv').addEventListener('click', () => {
            const csv = HsbtrEngine.exportTokensCsv(State.tokens);
            const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = 'tokens.csv';
            document.body.appendChild(a);
            a.click();
            a.remove();
            URL.revokeObjectURL(url);
        });
        document.getElementById('btn-download-overrides').addEventListener('click', () => {
            downloadJson('overrides.json', HsbtrCloud.getSnapshot().overrides);
        });
        document.getElementById('btn-download-review-status').addEventListener('click', () => {
            downloadJson('review_status.json', HsbtrCloud.getSnapshot().reviewStatus);
        });

        const dropZone = document.getElementById('csv-drop-zone');
        const fileInput = document.getElementById('csv-file-input');
        const confirmBtn = document.getElementById('btn-confirm-import');
        let stagedCsvData = null;
        dropZone.addEventListener('click', () => fileInput.click());
        dropZone.addEventListener('dragover', (e) => { e.preventDefault(); dropZone.classList.add('dragover'); });
        dropZone.addEventListener('dragleave', () => dropZone.classList.remove('dragover'));
        dropZone.addEventListener('drop', (e) => {
            e.preventDefault();
            dropZone.classList.remove('dragover');
            if (e.dataTransfer.files[0]) handleCsvFile(e.dataTransfer.files[0]);
        });
        fileInput.addEventListener('change', (e) => { if (e.target.files[0]) handleCsvFile(e.target.files[0]); });
        const handleCsvFile = (file) => {
            const reader = new FileReader();
            reader.onload = (e) => {
                stagedCsvData = e.target.result;
                const rows = stagedCsvData.split('\n').filter(r => r.trim()).length;
                document.getElementById('import-file-name').textContent = file.name;
                document.getElementById('import-row-count').textContent = `已準備 ${rows} 行資料`;
                document.getElementById('import-preview-box').classList.remove('hidden');
                confirmBtn.disabled = false;
            };
            reader.readAsText(file, 'UTF-8');
        };
        confirmBtn.addEventListener('click', async () => {
            if (!stagedCsvData) return;
            try {
                const parsed = HsbtrEngine.parseTokensCsv(stagedCsvData);
                const res = await HsbtrCloud.importCsvTokens(parsed);
                showToast(
                    res.mode === 'issueops'
                        ? `CSV 已解析 ${parsed.length} 筆並送出雲端提交 (Issue #${res.issueNumber})`
                        : `CSV 成功寫入 ${parsed.length} 筆詞彙至雲端資料庫！`,
                    'success'
                );
                confirmBtn.disabled = true;
                document.getElementById('import-preview-box').classList.add('hidden');
                stagedCsvData = null;
            } catch (err) {
                showToast(`匯入失敗: ${err.message}`, 'error');
            }
        });

        // Modals close & Escape
        document.querySelectorAll('[data-close]').forEach(btn => {
            btn.addEventListener('click', () => closeModal(btn.getAttribute('data-close')));
        });
        document.querySelectorAll('.modal-backdrop').forEach(modal => {
            modal.addEventListener('click', (e) => { if (e.target === modal) closeModal(modal.id); });
        });
        window.addEventListener('keydown', (e) => {
            if (e.key === 'Escape' && State.activeModal) closeModal(State.activeModal);
        });

        document.getElementById('preview-item-lore').value = SAMPLES.hyperion.lore.join('\n');

        try {
            await HsbtrCloud.init();
            const oauthRes = HsbtrCloud.consumeOAuthResult();
            if (oauthRes) {
                if (oauthRes.ok && oauthRes.auth?.user?.login) {
                    showToast(`已透過 GitHub 官方帳號登入 @${oauthRes.auth.user.login}`, 'success');
                } else if (!oauthRes.ok && oauthRes.error) {
                    showToast(oauthRes.error, 'error');
                }
            }
            executePreview();
        } catch (err) {
            document.dispatchEvent(new Event('hsbtr:workbench-tokens-error'));
            document.getElementById('conn-badge').className = 'status-badge disconnected';
            document.getElementById('conn-text').textContent = '資料庫載入失敗';
            document.getElementById('sb-conn-status').textContent = `錯誤: ${err.message}`;
            showToast('無法初始化雲端字典: ' + err.message, 'error');
        }
    });
})();
