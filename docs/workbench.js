(() => {
    'use strict';

    const byId = id => document.getElementById('wb-' + id);
    const source = byId('source');
    const target = byId('target');
    const kind = byId('kind');
    const dialog = byId('role-dialog');
    const tag = id => '$' + '{' + id + '}';
    const rolePattern = /\$\{([A-Za-z][A-Za-z0-9]*)\}|%([A-Za-z][A-Za-z0-9]*):("(?:[^"\\\r\n]|\\["\\/bfnrt]|\\u[0-9a-fA-F]{4})*")/g;
    const numberPattern = /^[+-]?[0-9]{1,16}(?:,[0-9]{3}){0,4}(?:\.[0-9]{1,8})?[kKmMbB]?%?$/;
    const stripColor = value => value.replace(/§[0-9a-fk-or]/gi, '');
    const clean = value => stripColor(value).replace(/\s+/gu, ' ').trim();
    const storageKey = 'hsbtr.workbench.v1';

    const example = {
        kind: 'paragraph',
        source: 'Deals up to 23% of your damage dealt to\nother monsters within 4.5 blocks of the target.',
        translation: '對目標周圍 ' + tag('range') + ' 格內的其他怪物，最多造成原傷害 ' + tag('damage') + ' 的傷害。',
        context: '附魔效果 · 順劈斬（Cleave）',
        roles: []
    };
    for (const [id, value] of [['damage', '23%'], ['range', '4.5']]) {
        const start = example.source.indexOf(value);
        example.roles.push({ id, start, end: start + value.length, type: 'number', localized: '' });
    }

    let roles = [];
    let context = '';
    let previousSource = '';
    let existing = null;
    let verified = null;
    let draftVersion = 0;
    let busy = false;
    let changed = false;
    let lookupTimer;
    let selection;
    let editId;
    let glossary = null;
    let glossaryFailed = false;

    function element(name, className, text) {
        const node = document.createElement(name);
        if (className) node.className = className;
        if (text !== undefined) node.textContent = text;
        return node;
    }

    function draft() {
        return { kind: kind.value, source: source.value, translation: target.value, roles, context };
    }

    function persist() {
        try { sessionStorage.setItem(storageKey, JSON.stringify({ ...draft(), dirty: changed })); } catch { /* The editor also works without storage. */ }
    }

    function installDraft(data) {
        kind.value = data.kind;
        source.value = data.source;
        target.value = data.translation;
        previousSource = source.value;
        context = data.context || '';
        roles = data.roles.map(role => ({ ...role }));
        changed = Boolean(data.dirty);
        invalidate(true);
        target.setSelectionRange(target.value.length, target.value.length);
    }

    function status(message = '', tone = '') {
        const output = byId('feedback');
        output.textContent = message;
        output.dataset.tone = tone;
    }

    function invalidate(needsLookup = false) {
        draftVersion++;
        verified = null;
        status('');
        persist();
        render();
        if (needsLookup) {
            existing = null;
            byId('use-existing').disabled = true;
            clearTimeout(lookupTimer);
            lookupTimer = setTimeout(lookup, 270);
            renderGlossary();
        }
    }

    function draftPreview() {
        return target.value.replace(rolePattern, (reference, legacy, typed, sample) => {
            const role = roles.find(r => r.id === (legacy || typed));
            const english = role ? clean(source.value.slice(role.start, role.end)) : '';
            return role ? (role.type === 'number' ? english : (sample && JSON.parse(sample)) || role.localized || english) : reference;
        });
    }

    function bag(values) {
        const counts = new Map();
        for (const value of values) counts.set(value, (counts.get(value) || 0) + 1);
        return counts;
    }

    function unmatched(a, b) {
        const counts = bag(b);
        return a.filter(value => {
            const count = counts.get(value) || 0;
            if (count) counts.set(value, count - 1);
            return count === 0;
        });
    }

    function validate() {
        const errors = [];
        const warnings = [];
        const original = clean(source.value);
        const translated = clean(draftPreview());
        if (!original) errors.push('請輸入原文');
        if (original.length > 300) errors.push('原文合併空白後最多 300 字元');
        const lines = source.value.replace(/\r/g, '').trim().split('\n');
        if (kind.value === 'paragraph' && (lines.length > 6 || lines.some(line => !line.trim()))) {
            errors.push('請一次處理一段，最多六行且不含空行');
        }
        if (!target.value.trim()) errors.push('請輸入譯文');
        if (target.value.trim().includes('\n')) errors.push('請將譯文寫成一段，由預覽模擬換行');
        if (kind.value === 'title' && roles.length) errors.push('標題使用固定對照，請移除 token');
        if (kind.value !== 'paragraph' && roles.some(role => role.localized)) {
            errors.push('固定譯名適用於說明段落，請修改 token');
        }
        const ids = new Set(roles.map(role => role.id));
        const refs = Array.from(target.value.matchAll(rolePattern), m => m[1] || m[2]);
        const refsCount = bag(refs);
        for (const id of ids) if (!refsCount.has(id)) errors.push('缺少 ' + tag(id));
        for (const [id, count] of refsCount) {
            if (!ids.has(id)) errors.push('未標記 ' + tag(id));
            else if (count > 1) errors.push('重複引用 ' + tag(id));
        }
        if (/\$\{|\$[0-9]+|\{[0-9]+\}|%[A-Za-z][A-Za-z0-9]*:/.test(target.value.replace(rolePattern, ''))) errors.push('有尚未綁定的參數');
        if (original && translated) {
            const numbers = value => value.match(/[+-]?\d[\d,]*(?:\.\d+)?[kKmMbB]?%?/g) || [];
            const icons = value => value.match(/[\uE000-\uF8FF✪❤❈☠☘☯✯✔✖✆⸕]/gu) || [];
            const missingNumbers = unmatched(numbers(original), numbers(translated));
            const extraNumbers = unmatched(numbers(translated), numbers(original));
            const missingIcons = unmatched(icons(original), icons(translated));
            if (missingNumbers.length || extraNumbers.length) {
                warnings.push('請核對數值' + (missingNumbers.length ? '，原文 ' + missingNumbers.join('、') : '')
                    + (extraNumbers.length ? '，譯文 ' + extraNumbers.join('、') : ''));
            }
            if (missingIcons.length) warnings.push('請核對圖示 ' + missingIcons.join(' '));
            if (!/[\u3400-\u9fff]/u.test(translated)) warnings.push('目前沒有繁中文字');
        }
        return { errors, warnings };
    }

    function renderChecks() {
        const { errors, warnings } = validate();
        const entries = [];
        for (const message of errors) entries.push(element('span', 'wb-check error', '！ ' + message));
        for (const message of warnings) entries.push(element('span', 'wb-check warn', '△ ' + message));
        if (!entries.length) entries.push(element('span', 'wb-check', '✓ token、數值與圖示檢查完成'));
        byId('checks').replaceChildren(...entries);
        byId('verify').disabled = errors.length > 0 || busy;
        byId('save').disabled = errors.length > 0 || busy || !verified || Boolean(verified.saved);
    }

    function renderPreview() {
        byId('preview-context').textContent = context || kind.selectedOptions[0].textContent;
        byId('preview-label').textContent = verified ? (verified.saved ? '已套用' : '引擎已核對') : '草稿';
        byId('preview-text').textContent = stripColor(verified?.preview || draftPreview()) || '輸入譯文後預覽';
    }

    function renderRoles() {
        const list = byId('roles');
        byId('role-count').textContent = roles.length + ' 個';
        byId('mark').disabled = kind.value === 'title' || roles.length >= 12;
        byId('role-hint').textContent = kind.value === 'title'
            ? '標題使用固定對照。' : '選取原文中的數值或對象後標記。點擊 token 可插入譯文。';
        if (!roles.length) {
            list.replaceChildren(element('span', 'wb-role-empty', '尚未標記 token'));
            return;
        }
        const chips = roles.map(role => {
            const chip = element('div', 'wb-role-chip');
            chip.dataset.type = role.type;
            const insert = element('button', 'wb-chip-main');
            insert.type = 'button';
            const sample = clean(source.value.slice(role.start, role.end));
            insert.title = '插入 ' + tag(role.id);
            insert.append(element('code', '', role.id), element('samp', '', sample));
            insert.onclick = () => insertText(tag(role.id));
            const edit = element('button', 'wb-chip-menu', '編輯');
            edit.type = 'button';
            edit.setAttribute('aria-label', '編輯 token ' + role.id);
            edit.onclick = () => openRole(role);
            const remove = element('button', 'wb-chip-menu', '×');
            remove.type = 'button';
            remove.setAttribute('aria-label', '移除 token ' + role.id);
            remove.onclick = () => {
                roles = roles.filter(row => row.id !== role.id);
                changed = true;
                invalidate();
            };
            chip.append(insert, edit, remove);
            return chip;
        });
        list.replaceChildren(...chips);
    }

    function render() {
        byId('context').textContent = context || '可直接貼上一段原文，或從擷取佇列帶入。';
        byId('count').textContent = clean(source.value).length + ' / 300';
        byId('target-count').textContent = target.value.length + ' 字元';
        renderRoles();
        renderPreview();
        renderChecks();
    }

    function insertText(value) {
        target.setRangeText(value, target.selectionStart, target.selectionEnd, 'end');
        target.focus();
        changed = true;
        invalidate();
    }

    function remapRoles(from, to) {
        let prefix = 0;
        while (prefix < from.length && prefix < to.length && from[prefix] === to[prefix]) prefix++;
        let tail = 0;
        while (tail < from.length - prefix && tail < to.length - prefix
                && from[from.length - 1 - tail] === to[to.length - 1 - tail]) tail++;
        const oldEnd = from.length - tail;
        const increase = to.length - from.length;
        roles = roles.flatMap(role => {
            if (role.end <= prefix) return [role];
            if (role.start >= oldEnd) return [{ ...role, start: role.start + increase, end: role.end + increase }];
            return [];
        });
    }

    source.addEventListener('input', () => {
        const count = roles.length;
        remapRoles(previousSource, source.value);
        previousSource = source.value;
        changed = true;
        invalidate(true);
        if (roles.length < count) status('原文有變動，已移除受影響的 token。請重新標記。', 'error');
    });
    target.addEventListener('input', () => { changed = true; invalidate(); });
    kind.addEventListener('change', () => { changed = true; invalidate(true); });

    async function api(payload) {
        if (!window.HsbtrCloud?.getSnapshot().ready) {
            throw new Error('正在載入雲端字典，請稍候…');
        }
        if (payload.action === 'lookup') return HsbtrCloud.lookupWorkbench(payload.kind, payload.source);
        if (payload.action === 'preview') return HsbtrCloud.previewWorkbench(payload);
        if (payload.action === 'save') return await HsbtrCloud.saveWorkbench(payload);
        throw new Error('未知的工作台操作。');
    }

    async function lookup() {
        const sample = source.value;
        const type = kind.value;
        if (!clean(sample) || clean(sample).length > 300) return;
        try {
            const result = await api({ action: 'lookup', source: sample, kind: type });
            if (sample !== source.value || type !== kind.value) return;
            existing = result.existing;
            byId('use-existing').disabled = !existing;
            byId('use-existing').title = existing ? '引用：' + existing : '';
        } catch { /* Lookup is optional while writing a draft. */ }
    }

    async function verify(save) {
        if (busy || validate().errors.length || (save && !verified)) return;
        const captured = draftVersion;
        const submitted = draft();
        const revision = verified?.revision;
        busy = true;
        renderChecks();
        status(save ? '正在儲存並套用…' : '正在用遊戲字典核對…');
        try {
            const result = await api({ ...submitted, action: save ? 'save' : 'preview', revision });
            if (captured !== draftVersion) {
                if (save) status('已儲存先前核對的版本。請重新核對目前草稿。', 'success');
                return;
            }
            verified = { ...result, saved: save };
            if (save) { changed = false; persist(); }
            status(
                save
                    ? (result.mode === 'issueops' ? '已提交至 GitHub Actions 背景合併，寫入雲端前草稿進度已自動保存。' : '已寫入 GitHub 雲端共享資料庫。')
                    : '預覽已核對，可以儲存。',
                'success'
            );
        } catch (error) {
            if (captured === draftVersion) {
                verified = null;
                status(error.message, 'error');
            }
        } finally {
            busy = false;
            renderChecks();
            renderPreview();
        }
    }

    byId('verify').onclick = () => verify(false);
    byId('save').onclick = () => verify(true);
    byId('use-existing').onclick = () => {
        if (!existing) return;
        if (target.value.trim() && !window.confirm('用現有譯文取代這份草稿？')) return;
        target.value = stripColor(existing);
        target.setSelectionRange(target.value.length, target.value.length);
        changed = true;
        invalidate();
    };

    const localizedField = () => {
        byId('role-localized-field').hidden = kind.value !== 'paragraph' || byId('role-type').value !== 'fixed';
    };

    function openRole(role = null) {
        const start = role?.start ?? source.selectionStart;
        const end = role?.end ?? source.selectionEnd;
        const sample = clean(source.value.slice(start, end));
        if (!sample) {
            status('請先在原文選取一個數值或名稱。', 'error');
            source.focus();
            return;
        }
        selection = { start, end };
        editId = role?.id;
        const available = prefix => {
            let index = 1;
            while (roles.some(row => row.id === prefix + index)) index++;
            return prefix + index;
        };
        byId('selection').textContent = sample;
        byId('role-id').value = editId || available(numberPattern.test(sample) ? 'value' : 'target');
        byId('role-type').value = role?.type || (numberPattern.test(sample) ? 'number' : 'fixed');
        byId('role-localized').value = role?.localized || '';
        byId('role-error').textContent = '';
        localizedField();
        dialog.showModal();
        byId('role-id').select();
    }

    byId('mark').onclick = () => openRole();
    byId('mark').onmousedown = event => event.preventDefault();
    byId('role-type').onchange = localizedField;
    byId('role-cancel').onclick = () => dialog.close();
    byId('role-form').addEventListener('submit', event => {
        event.preventDefault();
        const id = byId('role-id').value.trim();
        const type = byId('role-type').value;
        const error = byId('role-error');
        if (roles.some(role => role.id !== editId && (role.id === id
                || role.start < selection.end && selection.start < role.end))) {
            error.textContent = 'Token ID 或原文範圍重複。';
            return;
        }
        const sample = source.value.slice(selection.start, selection.end);
        if (sample !== sample.trim() || (type === 'number' && !numberPattern.test(clean(sample)))) {
            error.textContent = '請選取完整的值，並排除前後空白。';
            return;
        }
        const local = byId('role-localized-field').hidden ? '' : byId('role-localized').value.trim();
        roles = [...roles.filter(role => role.id !== editId), { id, type, ...selection, localized: local }]
            .sort((a, b) => a.start - b.start);
        if (editId && id !== editId) {
            target.value = target.value.replace(rolePattern, (ref, legacy, typed, sample) =>
                (legacy || typed) === editId ? (sample ? '%' + id + ':' + sample : tag(id)) : ref);
        }
        dialog.close();
        changed = true;
        invalidate();
    });

    function width(wide) {
        byId('tooltip').classList.toggle('wide', wide);
        byId('narrow').setAttribute('aria-pressed', String(!wide));
        byId('wide').setAttribute('aria-pressed', String(wide));
    }
    byId('narrow').onclick = () => width(false);
    byId('wide').onclick = () => width(true);

    function renderGlossary() {
        const list = byId('glossary-results');
        const needle = byId('glossary-query').value.trim().toLowerCase();
        const original = source.value.toLowerCase();
        if (!glossary) {
            list.replaceChildren(element('li', 'wb-glossary-empty', glossaryFailed ? '無法載入本機名詞表。' : '等待本機名詞表…'));
            return;
        }
        const matches = glossary.filter(row => needle
            ? [row.en, row.zh, row.id].some(value => value.toLowerCase().includes(needle))
            : row.en.length > 2 && original.includes(row.en.toLowerCase()));
        matches.sort((a, b) => needle
            ? Number(b.en.toLowerCase() === needle) - Number(a.en.toLowerCase() === needle) || a.en.length - b.en.length
            : b.en.length - a.en.length);
        if (!matches.length) {
            list.replaceChildren(element('li', 'wb-glossary-empty', needle ? '沒有相符名詞。' : '搜尋名詞以查閱既有譯法。'));
            return;
        }
        const unique = new Set();
        const visible = matches.filter(row => {
            const key = row.en + '\u0000' + row.zh;
            if (unique.has(key)) return false;
            unique.add(key);
            return true;
        }).slice(0, 5);
        list.replaceChildren(...visible.map(row => {
            const item = element('li');
            const button = element('button', 'wb-glossary-entry');
            button.type = 'button';
            button.title = '插入「' + row.zh + '」';
            const en = element('small', '', row.en + ' · ');
            en.append(element('em', '', row.id));
            button.append(element('strong', '', row.zh), en, element('span', '', '+'));
            button.onclick = () => insertText(row.zh);
            item.append(button);
            return item;
        }));
    }

    byId('glossary-query').oninput = renderGlossary;
    document.addEventListener('hsbtr:workbench-tokens-error', () => {
        glossaryFailed = true;
        renderGlossary();
    });
    document.addEventListener('hsbtr:workbench-tokens', event => {
        glossary = Object.entries(event.detail).flatMap(([id, token]) => {
            if (typeof token?.en !== 'string' || typeof token?.zh_tw !== 'string') return [];
            return [{ id, en: stripColor(token.en), zh: stripColor(token.zh_tw) }];
        }).filter(row => row.en && row.zh && !/\{[A-Z_0-9]+}/.test(row.zh));
        renderGlossary();
    });
    byId('example').onclick = () => {
        if (!changed || window.confirm('載入範例會取代目前草稿。繼續？')) installDraft(example);
    };
    byId('open-queue').onclick = () => document.querySelector('[data-tab=capture]').click();

    document.addEventListener('hsbtr:workbench-source', event => {
        if (changed && !window.confirm('把這筆擷取帶入工作台並取代目前草稿？')) return;
        const record = event.detail;
        const category = record.category || '';
        const type = category === 'CHAT' || category === 'ACTION_BAR' ? 'dialogue'
            : category === 'CONTAINER_UI' ? 'title' : category.startsWith('MENU_TOOLTIP') ? 'paragraph' : 'menu';
        const label = category.startsWith('MENU_TOOLTIP:') ? '提示框 · ' + category.slice(13)
            : type === 'dialogue' ? 'NPC／系統訊息' : type === 'title' ? '介面標題' : '遊戲擷取';
        installDraft({ kind: type, source: stripColor(record.rawText || record.cleanText || ''),
            translation: '', roles: [], context: label });
        document.querySelector('[data-tab=workbench]').click();
        source.focus();
    });

    try {
        const saved = JSON.parse(sessionStorage.getItem(storageKey) || 'null');
        installDraft(saved?.roles && typeof saved.source === 'string' ? saved : example);
    } catch {
        installDraft(example);
    }
    document.addEventListener('hsbtr:cloud-ready', () => lookup());
    renderGlossary();
})();
