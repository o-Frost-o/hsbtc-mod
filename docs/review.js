(() => {
    'use strict';

    const byId = id => document.getElementById('review-' + id);
    const tab = document.getElementById('tab-review');
    const value = byId('value');
    const source = byId('source');
    const query = byId('query');
    const kind = byId('kind');
    const kindLabels = new Map([...kind.options].map(option => [option.value, option.textContent]));
    const statusButtons = [...tab.querySelectorAll('[data-review-status]')];
    const state = { entry: null, status: 'pending', page: null, filter: { query: '', kind: '' }, busy: false };
    const colors = {
        0: ['黑色', '#000000'], 1: ['深藍', '#0000aa'], 2: ['深綠', '#00aa00'], 3: ['深青', '#00aaaa'],
        4: ['深紅', '#aa0000'], 5: ['深紫', '#aa00aa'], 6: ['金色', '#ffaa00'], 7: ['淺灰', '#aaaaaa'],
        8: ['深灰', '#555555'], 9: ['藍色', '#5555ff'], a: ['綠色', '#55ff55'], b: ['青色', '#55ffff'],
        c: ['紅色', '#ff5555'], d: ['粉紫', '#ff55ff'], e: ['黃色', '#ffff55'], f: ['白色', '#ffffff']
    };
    const formatting = /§([0-9a-fk-or])/gi;
    const namedToken = /\$\{([A-Za-z][A-Za-z0-9]*)\}|%([A-Za-z][A-Za-z0-9]*):("(?:[^"\\\r\n]|\\["\\/bfnrt]|\\u[0-9a-fA-F]{4})*")/g;
    const variables = new RegExp(namedToken.source + '|\\$[1-9]\\d*|\\{(?:\\d+|[A-Z][A-Z0-9_]*)\\}|%(?:[1-9]\\d*\\$)?[sd]', 'g');
    const numberSample = /^[+-]?[0-9]{1,16}(?:,[0-9]{3}){0,4}(?:\.[0-9]{1,8})?[kKmMbB]?%?$/;
    const roleForm = byId('role-form');
    const glossaryMarker = /(?<!\$)\{([A-Z][A-Z0-9_]+)\}/g;
    let glossary = {};
    let roles = [];
    let selection;
    let excerpt;
    let parentDraft;
    const testValues = new Map();
    const tokenRef = id => '$' + '{' + id + '}';
    const inlineRef = (id, sample) => '%' + id + ':' + JSON.stringify(sample);
    const rewriteTokens = (text, replace) => text.replace(namedToken,
        (reference, legacy, typed, sample) => replace(reference, legacy || typed, sample ? JSON.parse(sample) : ''));
    const bilingualSample = (translated, english) => english && translated !== english
        && !translated.includes('（' + english + '）') && !translated.includes('(' + english + ')')
        ? translated + '（' + english + '）' : translated;

    function resolveGlossary(text, visited = []) {
        if (visited.length > 5) return text;
        return text.replace(glossaryMarker, (marker, id) => {
            const token = Object.hasOwn(glossary, id) ? glossary[id] : null;
            return token && !visited.includes(id) ? resolveGlossary(token.zh_tw, [...visited, id]) : marker;
        });
    }

    const make = (tag, text, css) => {
        const element = document.createElement(tag);
        if (text !== undefined) element.textContent = text;
        if (css) element.className = css;
        return element;
    };

    async function api(action, payload) {
        if (!window.HsbtrCloud?.getSnapshot().ready) {
            throw new Error('正在初始化雲端翻譯資料庫，請稍候…');
        }
        switch (action) {
            case 'page': return HsbtrCloud.reviewPage(payload);
            case 'next': return HsbtrCloud.reviewNext(payload);
            case 'excerpt': return HsbtrCloud.reviewExcerpt(payload);
            case 'save': return await HsbtrCloud.reviewSave(payload, false);
            case 'verify': return await HsbtrCloud.reviewSave(payload, true);
            default: throw new Error('審核操作無效。');
        }
    }

    function dirty() {
        return !!state.entry && (value.value !== state.entry.value
            || JSON.stringify(roles) !== JSON.stringify(state.entry.roles || []));
    }

    function pendingDraft() {
        return dirty() || !!parentDraft && parentDraft.value !== parentDraft.entry.value;
    }

    function selectedId() {
        return parentDraft?.entry.id || state.entry?.id;
    }

    function allowNavigation() {
        return !pendingDraft() || window.confirm('這筆譯文還沒儲存。要捨棄修改並繼續嗎？');
    }

    function feedback(message = '', tone = '') {
        const target = byId('feedback');
        target.textContent = message;
        target.dataset.tone = tone;
    }

    function syncControls() {
        const editable = state.entry && !state.busy;
        for (const input of [byId('save'), byId('verify'), byId('next'), ...tab.querySelectorAll('[data-code]')]) {
            input.disabled = !editable;
        }
        value.readOnly = !editable;
        byId('mark').disabled = !editable || roles.length >= 12;
        byId('return-item').disabled = state.busy;
        const canSave = editable && roleForm.hidden && !!value.value.replace(formatting, '').trim() && !bindingError();
        byId('save').disabled = !canSave || !dirty();
        byId('verify').disabled = !canSave || (state.entry.verified && !dirty());
        kind.disabled = state.busy;
        query.disabled = state.busy;
        for (const button of statusButtons) {
            button.disabled = state.busy;
            button.setAttribute('aria-pressed', String(button.dataset.reviewStatus === state.status));
        }
        const page = state.page;
        byId('page-prev').disabled = state.busy || !page || page.page === 0;
        byId('page-next').disabled = state.busy || !page || (page.page + 1) * page.pageSize >= page.matching;
        for (const row of byId('results').querySelectorAll('button')) row.disabled = state.busy;
    }

    async function run(operation) {
        if (state.busy) return;
        state.busy = true;
        syncControls();
        try {
            await operation();
        } catch (error) {
            feedback(error.message || '無法連線到雲端資料庫。', 'error');
            if (!state.entry) byId('empty').textContent = '無法載入翻譯。請重新整理頁面後再試一次。';
        } finally {
            state.busy = false;
            syncControls();
        }
    }

    function plain(text) {
        return text.replace(formatting, '').replace(/\s+/g, ' ').trim();
    }

    function tag(entry) {
        const badge = make('span', kindLabels.get(entry.kind), 'review-kind');
        badge.dataset.kind = entry.kind;
        return badge;
    }

    function renderQueue() {
        const page = state.page;
        const list = byId('results');
        list.replaceChildren();
        for (const entry of page.rows) {
            const button = make('button', undefined, 'review-row');
            button.type = 'button';
            if (entry.id === selectedId()) button.setAttribute('aria-current', 'true');
            const meta = make('span', undefined, 'review-row-meta');
            meta.append(tag(entry));
            if (entry.verified) {
                const check = make('span', '✓', 'review-row-check');
                check.title = '已驗證';
                meta.append(check);
            }
            const heading = entry.sample ? rewriteTokens(entry.sample, (ref, id, sample) => sample || ref)
                : entry.heading || entry.source;
            button.append(meta, make('strong', plain(heading) || '（無原文）'),
                make('span', plain(resolveGlossary(entry.value)) || '尚無譯文', 'review-row-value'));
            button.addEventListener('click', () => {
                if (entry.id !== selectedId() && allowNavigation()) {
                    openEntry(entry);
                    renderQueue();
                    list.querySelector('[aria-current]')?.focus({ preventScroll: true });
                    syncControls();
                }
            });
            const li = make('li');
            li.append(button);
            list.append(li);
        }
        if (!page.rows.length) list.append(make('li', '沒有符合條件的條目。', 'review-no-results'));

        const count = new Intl.NumberFormat('zh-TW');
        byId('count').textContent = count.format(page.verified) + ' / ' + count.format(page.total) + ' 筆已驗證';
        byId('progress').max = page.total || 1;
        byId('progress').value = page.verified;
        byId('pages').textContent = page.matching
            ? '第 ' + count.format(page.page + 1) + ' / ' + count.format(Math.ceil(page.matching / page.pageSize)) + ' 頁'
            : '0 筆';
    }

    function openEntry(entry, { keepParent = false } = {}) {
        if (!keepParent) parentDraft = undefined;
        state.entry = entry;
        selection = undefined;
        roleForm.hidden = true;
        roles = (entry?.roles || []).map(role => ({ ...role }));
        testValues.clear();
        excerpt = undefined;
        byId('empty').hidden = !!entry;
        byId('editor').hidden = !entry;
        byId('preview-panel').hidden = !entry;
        feedback();
        if (!entry) {
            byId('empty').textContent = state.status === 'pending' && state.page?.total
                ? '這個範圍已沒有待驗證的翻譯。' : '沒有符合條件的翻譯。';
            return;
        }

        const badge = byId('entry-kind');
        badge.dataset.kind = entry.kind;
        badge.textContent = kindLabels.get(entry.kind);
        const context = byId('entry-context');
        context.textContent = entry.context;
        context.title = entry.context;
        context.hidden = !entry.context;
        const isRule = ['uiRule', 'loreRule', 'dialogueRule'].includes(entry.kind);
        const isItemLore = entry.kind === 'itemLore';
        byId('source-label').textContent = isRule ? (entry.sample ? '英文範例（含 token）' : '比對規則（正規表示式）')
            : isItemLore ? '英文道具敘述' : '英文原文';
        source.value = entry.sample || entry.source;
        source.placeholder = isItemLore && !entry.source ? '尚未收錄這筆道具的英文敘述。' : '';
        byId('mark').hidden = !entry.markable && !(isItemLore && entry.source);
        byId('excerpt-bar').hidden = !parentDraft;
        byId('value-label').textContent = parentDraft ? '此段的繁中譯文' : '繁中譯文';
        value.value = entry.value;
        const hint = byId('entry-hint');
        byId('rule-regex').hidden = !entry.sample;
        byId('rule-regex').open = false;
        byId('regex-text').textContent = entry.sample ? entry.source : '';
        hint.textContent = entry.kind === 'dialogue' || entry.kind === 'dialogueRule'
            ? '統一名詞請在譯文中填入 {TOKEN_ID}，預覽會帶入詞彙表的譯名。'
            : isRule ? (entry.sample ? '可在譯文中調整 token 的位置。'
            : '可調整變數的位置，預覽會保留變數標記。')
            : isItemLore && entry.source ? '在原文選取數值或詞組，再按「標記 token」即可編輯對應段落。'
            : '';
        hint.hidden = !hint.textContent;
        renderVariables();
        renderRoles();
        renderDraft();
    }

    function tokens(text) {
        return [...text.matchAll(variables)].map(match => match[0]);
    }

    function renderVariables() {
        const chips = byId('variable-chips');
        chips.replaceChildren();
        const references = new Set(tokens(state.entry.value).filter(token => !managed(token)));
        for (const token of references) {
            const button = make('button', token, 'review-variable');
            button.type = 'button';
            button.title = '在游標處插入 ' + token;
            button.addEventListener('click', () => {
                if (!state.busy) insert(token);
            });
            chips.append(button);
        }
        byId('variables').hidden = !references.size;
    }

    function managed(reference) {
        return state.entry.markable && [...reference.matchAll(namedToken)].length > 0;
    }

    function sampleFor(role) {
        return source.value.slice(role.start, role.end).replace(formatting, '').replace(/\s+/g, ' ');
    }

    function bindingError() {
        if (!state.entry?.markable) return '';
        if (excerpt && !roles.length) return '請標記原文的 token，並將它插入譯文。';
        const references = [...value.value.matchAll(namedToken)].map(match => match[1] || match[2]);
        const unknown = references.find(id => !roles.some(role => role.id === id));
        if (unknown) return '原文尚未標記 ' + tokenRef(unknown);
        const incorrect = roles.find(role => references.filter(id => id === role.id).length !== 1);
        return incorrect ? '請在譯文中放入一次 ' + tokenRef(incorrect.id) : '';
    }

    function renderRoles() {
        const area = byId('roles');
        area.hidden = !state.entry.markable;
        if (area.hidden) return;
        byId('role-hint').textContent = roles.length
            ? '點擊 token 可插入譯文。更改測試值可預覽不同的遊戲數值。'
            : '在原文選取完整數值或詞組，再按「標記 token」。';
        const list = byId('role-list');
        list.replaceChildren();
        for (const role of roles) {
            const chip = make('div', undefined, 'wb-role-chip');
            chip.dataset.type = role.type;
            const add = make('button', undefined, 'wb-chip-main');
            add.type = 'button';
            const token = inlineRef(role.id, role.localized || sampleFor(role));
            add.title = '插入 ' + token;
            add.append(make('code', '%' + role.id), make('samp', sampleFor(role)));
            add.onclick = () => { if (!state.busy) insert(token); };
            const edit = make('button', '編輯', 'wb-chip-menu');
            edit.type = 'button';
            edit.setAttribute('aria-label', '編輯 token ' + role.id);
            edit.onclick = () => { if (!state.busy) openRole(role); };
            const remove = make('button', '×', 'wb-chip-menu');
            remove.type = 'button';
            remove.setAttribute('aria-label', '移除 token ' + role.id);
            remove.onclick = () => {
                if (state.busy) return;
                roles = roles.filter(other => other.id !== role.id);
                value.value = rewriteTokens(value.value, (ref, id, sample) =>
                    id === role.id ? sample || role.localized || sampleFor(role) : ref);
                testValues.delete(role.id);
                renderRoles();
                renderDraft();
            };
            chip.append(add, edit, remove);
            if (role.type === 'number') {
                const preview = make('label', '測試值', 'review-role-test');
                const input = make('input');
                input.type = 'text';
                input.spellcheck = false;
                input.maxLength = 45;
                input.setAttribute('aria-label', role.id + ' 測試值');
                input.placeholder = sampleFor(role);
                input.value = testValues.get(role.id) || '';
                input.oninput = () => {
                    const candidate = input.value.trim();
                    const valid = !candidate || numberSample.test(candidate);
                    input.setAttribute('aria-invalid', String(!valid));
                    if (valid && candidate) testValues.set(role.id, candidate);
                    else testValues.delete(role.id);
                    renderDraft();
                };
                preview.append(input);
                chip.append(preview);
            }
            list.append(chip);
        }
    }

    function openRole(role) {
        const start = role?.start ?? source.selectionStart;
        const end = role?.end ?? source.selectionEnd;
        const sample = sampleFor({ start, end });
        if (!sample.trim()) {
            feedback('請先在英文原文選取要標記的數值或詞組。', 'error');
            source.focus();
            return;
        }
        selection = { start, end, previous: role?.id };
        const numeric = numberSample.test(sample);
        let index = 1;
        const prefix = numeric ? 'num' : 'term';
        while (roles.some(other => other.id === prefix + index)) index++;
        byId('role-sample').textContent = sample;
        byId('role-id').value = role?.id || prefix + index;
        byId('role-type').value = role?.type || (numeric ? 'number' : 'fixed');
        byId('role-local').value = role?.localized || '';
        byId('role-error').textContent = '';
        byId('role-local-field').hidden = byId('role-type').value === 'number';
        roleForm.hidden = false;
        byId('role-error').hidden = true;
        byId('role-insert').hidden = !!role;
        feedback();
        syncControls();
        byId('role-id').select();
    }

    function closeRole() {
        roleForm.hidden = true;
        selection = undefined;
        syncControls();
    }

    byId('role-type').addEventListener('change', () => {
        byId('role-local-field').hidden = byId('role-type').value === 'number';
        if (!selection?.previous && /^(?:num|term|item|npc)[0-9]+$/.test(byId('role-id').value)) {
            const prefix = { number: 'num', fixed: 'term', item: 'item', npc: 'npc' }[byId('role-type').value];
            let index = 1;
            while (roles.some(role => role.id === prefix + index)) index++;
            byId('role-id').value = prefix + index;
        }
    });
    byId('role-cancel').addEventListener('click', closeRole);
    roleForm.addEventListener('submit', event => {
        event.preventDefault();
        if (!selection || state.busy) return;
        const { start, end, previous } = selection;
        const id = byId('role-id').value.trim();
        const type = byId('role-type').value;
        const sample = sampleFor({ start, end });
        const error = byId('role-error');
        if (source.value.slice(start, end).includes('\n') || sample !== sample.trim() || !sample
            || (type === 'number' && !numberSample.test(sample))) {
            error.textContent = '請選取同一行的完整數值或詞組，並排除前後空白。';
            error.hidden = false;
            return;
        }
        if (roles.some(role => role.id !== previous
            && (role.id === id || role.start < end && role.end > start))) {
            error.textContent = 'Token ID 或原文範圍重複。';
            error.hidden = false;
            return;
        }
        const localized = type === 'number' ? '' : byId('role-local').value.trim();
        roles = [...roles.filter(role => role.id !== previous), { id, type, start, end, localized }]
            .sort((first, second) => first.start - second.start);
        if (previous && previous !== id) {
            value.value = rewriteTokens(value.value, (ref, former, example) =>
                former === previous ? (ref.startsWith('%') ? inlineRef(id, example) : tokenRef(id)) : ref);
            testValues.delete(previous);
        }
        closeRole();
        feedback();
        renderRoles();
        if (event.submitter === byId('role-insert')) insert(inlineRef(id, localized || sample));
        else renderDraft();
    });

    function codeState() {
        return { color: '', styles: new Set() };
    }

    function applyCode(state, code) {
        if (code in colors || code === 'r') {
            state.color = code === 'r' ? '' : code;
            state.styles.clear();
        } else {
            state.styles.add(code);
        }
    }

    function styleAt(text) {
        const style = codeState();
        for (const match of text.matchAll(formatting)) applyCode(style, match[1].toLowerCase());
        return style;
    }

    function renderPreview(target, text, baseFormat, emptyMessage) {
        target.replaceChildren();
        target.classList.toggle('review-preview-empty', !text);
        if (!text) {
            target.textContent = emptyMessage;
            return;
        }

        const active = styleAt(baseFormat);
        const restore = styleAt(baseFormat);
        let offset = 0;
        const append = content => {
            if (!content) return;
            const span = make('span', content);
            if (active.color) span.style.color = colors[active.color][1];
            if (active.styles.has('l')) span.classList.add('review-bold');
            if (active.styles.has('o')) span.classList.add('review-italic');
            if (active.styles.has('n')) span.classList.add('review-under');
            if (active.styles.has('m')) span.classList.add('review-strike');
            if (active.styles.has('k')) {
                span.title = '原文：' + content;
                span.setAttribute('aria-label', content);
                if (!window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
                    span.textContent = content.replace(/\S/gu, ch => {
                        const chars = '◆◇■▲※×*#';
                        return chars[ch.codePointAt(0) % chars.length];
                    });
                }
                span.classList.add('review-obfuscated');
            }
            target.append(span);
        };
        for (const match of text.matchAll(formatting)) {
            append(text.slice(offset, match.index));
            const code = match[1].toLowerCase();
            applyCode(active, code);
            if (code === 'r') {
                active.color = restore.color;
                active.styles = new Set(restore.styles);
            }
            offset = match.index + match[0].length;
        }
        append(text.slice(offset));
    }

    function renderDraft() {
        if (!state.entry) return;
        const saved = !dirty();
        const status = byId('entry-state');
        status.textContent = !saved ? '未儲存變更' : state.entry.verified ? '✓ 已驗證' : '待驗證';
        status.dataset.state = saved && state.entry.verified ? 'verified' : saved ? 'pending' : 'dirty';
        const available = tokens(value.value);
        const missing = tokens(state.entry.value).filter(token => {
            if (managed(token)) return false;
            const index = available.indexOf(token);
            if (index < 0) return true;
            available.splice(index, 1);
            return false;
        });
        const warning = byId('token-warning');
        const binding = bindingError();
        warning.hidden = !binding && !missing.length;
        warning.textContent = binding || (missing.length ? '請確認移除的變數 ' + [...new Set(missing)].join('、') : '');
        const samples = new Map();
        const englishSource = rewriteTokens(state.entry.sample || source.value, (ref, id, example) => {
            samples.set(id, example);
            return example || ref;
        });
        const draft = rewriteTokens(value.value, (reference, id, example) => {
            const role = roles.find(role => role.id === id);
            if (!role) return state.entry.captureTypes?.[id] === 'npc' && example
                ? bilingualSample(example, samples.get(id)) : example || reference;
            const english = sampleFor(role);
            const translated = role.type === 'number'
                ? testValues.get(id) || english : example || role.localized || english;
            return role.type === 'npc' ? bilingualSample(translated, english) : translated;
        });
        const english = roles.reduceRight((text, role) => testValues.has(role.id)
            ? text.slice(0, role.start) + testValues.get(role.id) + text.slice(role.end)
            : text, englishSource);
        const tokenSource = byId('token-source');
        tokenSource.hidden = !roles.length;
        if (!tokenSource.hidden) {
            const raw = roles.reduceRight((text, role) =>
                text.slice(0, role.start) + inlineRef(role.id, sampleFor(role)) + text.slice(role.end),
            state.entry.sample || source.value);
            tokenSource.textContent = '英文 token 形式　' + raw.replace(formatting, '');
        }
        renderPreview(byId('preview-english'), english, state.entry.format, '此條目沒有英文原文。');
        renderPreview(byId('preview'), resolveGlossary(draft), state.entry.format, '輸入譯文後，即可在這裡預覽。');
        syncControls();
    }

    function applyFormatInsert(targetField, text, code, onAfterInsert) {
        const { selectionStart: start, selectionEnd: end } = targetField;
        if (code && start !== end && code !== 'r') {
            const format = styleAt(targetField.value.slice(0, end));
            const suffix = '§r' + (format.color ? '§' + format.color : '')
                + [...format.styles].map(style => '§' + style).join('');
            targetField.setRangeText(text + targetField.value.slice(start, end) + suffix, start, end, 'end');
        } else if (code) {
            targetField.setRangeText(text, start, start, 'end');
        } else {
            targetField.setRangeText(text, start, end, 'end');
        }
        targetField.focus();
        if (onAfterInsert) onAfterInsert();
    }

    function insert(text, code) {
        applyFormatInsert(value, text, code, () => {
            feedback();
            renderDraft();
        });
    }

    async function loadPage(page, { keep = false, selected = '' } = {}) {
        state.page = await api('page', { ...state.filter, status: state.status, page, selected });
        if (state.page.selectedPage !== undefined && state.page.selectedPage !== state.page.page) {
            state.page = await api('page', { ...state.filter, status: state.status, page: state.page.selectedPage });
        }
        if (!keep) openEntry(state.page.rows.find(row => row.id === selected) || state.page.rows[0] || null);
        renderQueue();
        const current = byId('results').querySelector('[aria-current]');
        if (current) {
            const list = byId('results');
            if (current.offsetTop < list.scrollTop
                || current.offsetTop + current.offsetHeight > list.scrollTop + list.clientHeight) {
                list.scrollTop = current.offsetTop;
            }
        } else {
            byId('results').scrollTop = 0;
        }
    }

    async function save(verify) {
        const entry = state.entry;
        if (!entry) {
            feedback('目前尚無可儲存的條目，請先等待資料庫載入完成。', 'error');
            return;
        }
        const saved = await api(verify ? 'verify' : 'save', {
            id: entry.id, revision: entry.revision, value: value.value, roles, excerpt
        });
        openEntry(saved, { keepParent: !!parentDraft });
        const snap = window.HsbtrCloud?.getSnapshot();
        const uploading = Boolean(snap?.uploading || snap?.uploadQueueCount > 0);
        feedback(
            verify
                ? (uploading ? '已驗證，正上傳至雲端資料庫…' : '譯文已儲存並驗證寫入雲端資料庫。')
                : (uploading ? '已儲存，正上傳至雲端資料庫…' : '譯文已儲存至雲端資料庫。'),
            'ok'
        );
        await loadPage(state.page.page, { keep: true, selected: selectedId() });
    }

    async function markItemExcerpt() {
        const range = { id: state.entry.id, start: source.selectionStart, end: source.selectionEnd, sentence: true };
        if (range.start >= range.end) {
            feedback('請先在英文敘述中選取要標記的數值或詞組。', 'error');
            source.focus();
            return;
        }
        const item = state.entry;
        const original = source.value;
        const selected = original.slice(range.start, range.end);
        const draft = { entry: item, value: value.value, sourceStart: range.start, sourceEnd: range.end,
            caretStart: value.selectionStart, caretEnd: value.selectionEnd, scrollTop: source.scrollTop };
        const entry = await api('excerpt', range);
        parentDraft = draft;
        openEntry(entry, { keepParent: true });
        if (entry.excerpt) excerpt = range;
        if (!entry.roles?.length && entry.excerpt && draft.value) {
            const translated = draft.value.split('\n');
            const english = original.split('\n');
            if (english.length === translated.length
                && english.every((line, index) => !plain(line) === !plain(translated[index]))) {
                const first = original.slice(0, entry.sourceStart).split('\n').length - 1;
                const count = original.slice(entry.sourceStart, entry.sourceEnd).split('\n').length;
                value.value = translated.slice(first, first + count).map(line => line.trim()).join(' ');
            }
        }
        const sample = selected.replace(formatting, '');
        const rawMatch = original.slice(entry.sourceStart, entry.sourceEnd) === source.value;
        const offset = rawMatch ? range.start - entry.sourceStart : source.value.indexOf(sample);
        if (!selected.includes('\n') && sample && offset >= 0
            && (rawMatch || offset === source.value.lastIndexOf(sample))) {
            source.setSelectionRange(offset, offset + (rawMatch ? selected.length : sample.length));
            openRole();
        } else {
            feedback('已開啟所選段落。請選取其中一個數值或詞組，再標記 token。', 'ok');
            source.focus();
        }
        renderDraft();
    }

    function restoreItem() {
        if (!parentDraft || state.busy) return;
        if (dirty() && !window.confirm('這個段落尚未儲存。要捨棄段落修改並返回完整敘述嗎？')) return;
        const draft = parentDraft;
        openEntry(draft.entry);
        value.value = draft.value;
        value.setSelectionRange(draft.caretStart, draft.caretEnd);
        source.setSelectionRange(draft.sourceStart, draft.sourceEnd);
        source.scrollTop = draft.scrollTop;
        renderDraft();
        value.focus();
    }

    async function next() {
        const result = await api('next', { ...state.filter, after: selectedId() });
        if (!result.entry) {
            feedback(state.entry && !state.entry.verified
                ? '這是目前篩選條件下唯一的待驗證條目。'
                : '這個範圍已沒有待驗證的翻譯。', 'ok');
            return;
        }
        if (state.status === 'verified') state.status = 'pending';
        openEntry(result.entry);
        await loadPage(result.page, { keep: true, selected: result.entry.id });
        value.focus();
    }

    function changeFilter(status = state.status) {
        if (state.busy) return;
        if (!allowNavigation()) {
            query.value = state.filter.query;
            kind.value = state.filter.kind;
            return;
        }
        state.filter = { query: query.value, kind: kind.value };
        state.status = status;
        run(() => loadPage(0));
    }

    function populateColorSwatches(container) {
        for (const [code, [name, rgb]] of Object.entries(colors)) {
            const button = make('button', undefined, 'review-swatch');
            button.type = 'button';
            button.dataset.code = code;
            button.style.setProperty('--swatch', rgb);
            button.title = name + ' §' + code;
            button.setAttribute('aria-label', button.title);
            container.append(button);
        }
    }

    populateColorSwatches(byId('colors'));

    byId('format-tools').addEventListener('click', event => {
        const button = event.target.closest('[data-code]');
        if (!button || state.busy || !state.entry) return;
        const code = button.dataset.code;
        byId('colors').closest('details').open = false;
        if (code in colors) tab.style.setProperty('--review-sample', colors[code][1]);
        insert('§' + code, code);
    });

    let queryTimer;
    query.addEventListener('input', () => {
        window.clearTimeout(queryTimer);
        queryTimer = window.setTimeout(() => changeFilter(), 220);
    });
    query.addEventListener('keydown', event => {
        if (event.key !== 'Enter') return;
        window.clearTimeout(queryTimer);
        changeFilter();
    });
    kind.addEventListener('change', () => {
        window.clearTimeout(queryTimer);
        changeFilter();
    });
    for (const button of statusButtons) {
        button.addEventListener('click', () => {
            window.clearTimeout(queryTimer);
            changeFilter(button.dataset.reviewStatus);
        });
    }
    value.addEventListener('input', () => {
        feedback();
        renderDraft();
    });
    byId('mark').addEventListener('mousedown', event => event.preventDefault());
    byId('mark').addEventListener('click', () => {
        if (state.entry?.kind === 'itemLore') run(markItemExcerpt);
        else openRole();
    });
    byId('return-item').addEventListener('click', restoreItem);
    byId('page-prev').addEventListener('click', () => {
        if (allowNavigation()) run(() => loadPage(state.page.page - 1));
    });
    byId('page-next').addEventListener('click', () => {
        if (allowNavigation()) run(() => loadPage(state.page.page + 1));
    });
    byId('save').addEventListener('click', () => run(() => save(false)));
    byId('verify').addEventListener('click', () => run(() => save(true)));
    byId('next').addEventListener('click', () => {
        if (allowNavigation()) run(next);
    });
    document.querySelector('[data-tab=review]').addEventListener('click', () => {
        if (!pendingDraft() && !parentDraft) run(() => loadPage(state.page?.page || 0, { selected: selectedId() }));
    });
    window.addEventListener('beforeunload', event => {
        if (pendingDraft() || window.HsbtrCloud?.hasPendingUploads?.()) event.preventDefault();
    });

    document.addEventListener('hsbtr:glossary-tokens', event => {
        glossary = event.detail || {};
        if (state.page) renderQueue();
        if (state.entry) renderDraft();
    });
    document.addEventListener('hsbtr:cloud-ready', () => run(() => loadPage(0)));
    document.addEventListener('hsbtr:cloud-upload-status', event => {
        const d = event.detail;
        if (!d || d.uploading || d.uploadQueueCount > 0 || d.uploadError) return;
        const msg = byId('feedback')?.textContent || '';
        if (msg.includes('正上傳至雲端資料庫')) {
            feedback(d.auth?.pushAccess ? '✔ 驗證結果已上傳至雲端資料庫。' : '✔ 已建立雲端提交（GitHub Actions 寫入中）。', 'ok');
        }
    });
    document.addEventListener('hsbtr:cloud-upload-error', event => {
        const msg = event.detail?._meta?.error || event.detail?.uploadError;
        if (msg) feedback(`雲端上傳失敗：${msg}`, 'error');
    });
    document.addEventListener('hsbtr:cloud-update', event => {
        const reason = event.detail?._meta?.reason;
        if (reason === 'review-enqueue' || event.detail?._meta?.fromUploadFlush) return;
        if (!state.busy && !pendingDraft() && !parentDraft && window.HsbtrCloud?.getSnapshot().ready) {
            run(() => loadPage(state.page?.page || 0, { keep: true, selected: selectedId() }));
        }
    });

    // =========================================================================
    // Add Extra Translation Modal Controller
    // =========================================================================

    const extraModal = document.getElementById('modal-extra-translation');
    const extraKind = document.getElementById('extra-kind');
    const extraSource = document.getElementById('extra-source');
    const extraValue = document.getElementById('extra-value');
    const extraItemEn = document.getElementById('extra-item-en');
    const extraItemId = document.getElementById('extra-item-id');
    const extraItemZh = document.getElementById('extra-item-zh');
    const extraItemCat = document.getElementById('extra-item-cat');
    const extraItemRarity = document.getElementById('extra-item-rarity');
    const extraItemLore = document.getElementById('extra-item-lore');
    const extraVerify = document.getElementById('extra-verify');
    const extraStatus = document.getElementById('extra-status');
    const RARITY_COLOR = {
        COMMON: '§f', UNCOMMON: '§a', RARE: '§9', EPIC: '§5',
        LEGENDARY: '§6', MYTHIC: '§d', DIVINE: '§b', SPECIAL: '§c'
    };
    let extraItemIdCustom = false;
    let extraFocusField = extraValue;
    let extraSubmitting = false;

    const cleanKey = (text, lore) => {
        const normalized = String(text || '').replace(formatting, '').replace(/[\s\u00A0\u2000-\u200B\u3000]+/gu, ' ').trim();
        return lore ? normalized.replace(/’/g, "'") : normalized;
    };

    const setExtraStatus = (msg = '', tone = '') => {
        if (!extraStatus) return;
        extraStatus.textContent = msg;
        extraStatus.dataset.tone = tone;
    };

    const updateExtraModeView = () => {
        const isItem = extraKind?.value === 'item';
        document.getElementById('extra-text-fields')?.classList.toggle('hidden', isItem);
        document.getElementById('extra-item-fields')?.classList.toggle('hidden', !isItem);
        extraFocusField = isItem ? extraItemZh : extraValue;
        updateExtraHintsAndPreview();
    };

    const lookupExistingExtra = (k, rawKey) => {
        const merged = window.HsbtrCloud?.getSnapshot().merged;
        if (!merged || !rawKey) return '';
        if (k === 'item') {
            const it = merged.items?.[rawKey];
            return it ? `${it.name_zh_tw || it.name_en || rawKey}` : '';
        }
        const map = {
            dialogue: merged.dialogues?.dialogues,
            person: merged.menus?.people,
            uiName: merged.menus?.titles,
            uiLine: merged.menus?.lines,
            loreLine: merged.lore_lines?.lines,
            paragraph: merged.lore_lines?.paragraphs
        }[k];
        if (!map || !Object.hasOwn(map, rawKey)) return '';
        const found = map[rawKey];
        return typeof found === 'string' ? found : (found?.zh_tw || '');
    };

    const updateExtraHintsAndPreview = () => {
        if (!extraKind) return;
        const k = extraKind.value;
        const previewEl = document.getElementById('extra-preview');
        if (k === 'item') {
            const id = extraItemId.value.trim().toUpperCase();
            const existing = lookupExistingExtra('item', id);
            const hintEl = document.getElementById('extra-item-existing-hint');
            if (hintEl) {
                hintEl.textContent = !id
                    ? '將以道具 ID 寫入雲端字庫。'
                    : (existing ? `💡 目前字庫已存在此道具：${existing}（送出將更新該道具）` : '✨ 新道具：送出後將新增至道具庫與審核清單。');
            }
            const format = RARITY_COLOR[extraItemRarity.value] || '§f';
            const lore = extraItemLore.value.trim();
            const fullText = [extraItemZh.value, lore].filter(Boolean).join('\n');
            renderPreview(previewEl, resolveGlossary(fullText), format, '輸入道具名稱後，即可在這裡預覽。');
        } else {
            const norm = cleanKey(extraSource.value, k === 'loreLine' || k === 'paragraph');
            const existing = lookupExistingExtra(k, norm);
            const hintEl = document.getElementById('extra-existing-hint');
            if (hintEl) {
                hintEl.textContent = !norm
                    ? ''
                    : (existing ? `💡 字庫已有對應譯文：「${existing}」（送出將更新覆寫）` : '✨ 新條目：儲存後將加入雲端共享字庫。');
            }
            renderPreview(previewEl, resolveGlossary(extraValue.value), '', '輸入譯文後，即可在這裡預覽。');
        }
    };

    const resetExtraInputs = ({ keepKind = true } = {}) => {
        if (!keepKind && extraKind) extraKind.value = 'dialogue';
        if (extraSource) extraSource.value = '';
        if (extraValue) extraValue.value = '';
        if (extraItemEn) extraItemEn.value = '';
        if (extraItemId) extraItemId.value = '';
        if (extraItemZh) extraItemZh.value = '';
        if (extraItemCat) extraItemCat.value = 'MISC';
        if (extraItemRarity) extraItemRarity.value = 'COMMON';
        if (extraItemLore) extraItemLore.value = '';
        extraItemIdCustom = false;
        setExtraStatus();
        updateExtraModeView();
    };

    const openExtraModal = ({ presetKind = '', presetSource = '' } = {}) => {
        if (!extraModal) return;
        resetExtraInputs({ keepKind: !presetKind });
        if (presetKind && extraKind) extraKind.value = presetKind;
        if (presetSource) {
            if (extraKind.value === 'item') {
                extraItemEn.value = presetSource.replace(formatting, '').trim();
                extraItemId.value = extraItemEn.value.toUpperCase().replace(/[^A-Z0-9]+/g, '_').replace(/^_+|_+$/g, '').slice(0, 64);
            } else {
                extraSource.value = presetSource;
            }
        }
        updateExtraModeView();
        if (!window.HsbtrCloud?.getSnapshot().ready) {
            setExtraStatus('雲端資料庫載入中，完成後即可直接送出。');
        }
        extraModal.classList.remove('hidden');
        setTimeout(() => {
            if (extraKind.value === 'item') (extraItemEn.value ? extraItemZh : extraItemEn).focus();
            else (extraSource.value ? extraValue : extraSource).focus();
        }, 0);
    };

    const submitExtra = async (keepOpen) => {
        if (extraSubmitting) return;
        extraSubmitting = true;
        const btnNext = document.getElementById('btn-extra-save-next');
        const btnView = document.getElementById('btn-extra-save-view');
        if (btnNext) btnNext.disabled = true;
        if (btnView) btnView.disabled = true;
        const k = extraKind.value;
        try {
            if (!window.HsbtrCloud?.getSnapshot().ready) {
                setExtraStatus('正在等待雲端資料庫就緒，請稍候…');
                if (window.HsbtrCloud?.whenReady) {
                    await window.HsbtrCloud.whenReady();
                } else {
                    setExtraStatus('正在初始化雲端資料庫，請稍候…', 'error');
                    return;
                }
            }
            const payload = k === 'item'
                ? {
                    kind: 'item',
                    itemId: extraItemId.value,
                    nameEn: extraItemEn.value,
                    nameZh: extraItemZh.value,
                    category: extraItemCat.value,
                    rarity: extraItemRarity.value,
                    lore: extraItemLore.value.split('\n'),
                    verify: extraVerify.checked
                }
                : {
                    kind: k,
                    source: extraSource.value,
                    value: extraValue.value,
                    verify: extraVerify.checked
                };
            setExtraStatus('正在驗證並排入上傳佇列…');
            const saved = await window.HsbtrCloud.addExtraTranslation(payload);
            if (keepOpen) {
                resetExtraInputs({ keepKind: true });
                setExtraStatus('✔ 已加入雲端上傳佇列，可繼續新增下一筆。', 'ok');
                await loadPage(state.page?.page || 0, { keep: true, selected: selectedId() });
                (k === 'item' ? extraItemEn : extraSource).focus();
            } else {
                extraModal.classList.add('hidden');
                if (saved.verified && state.status === 'pending') state.status = 'all';
                else if (!saved.verified && state.status === 'verified') state.status = 'pending';
                const reviewNav = document.querySelector('.nav-tab[data-tab="review"]');
                if (reviewNav && !reviewNav.classList.contains('active')) {
                    document.querySelectorAll('.nav-tab').forEach(b => b.classList.remove('active'));
                    document.querySelectorAll('.tab-panel').forEach(p => p.classList.remove('active'));
                    reviewNav.classList.add('active');
                    tab.classList.add('active');
                }
                openEntry(saved);
                feedback(saved.verified ? '✔ 已新增額外翻譯內容（已標記驗證）並送往雲端資料庫。' : '✔ 已新增額外翻譯內容並送往雲端資料庫。', 'ok');
                await loadPage(0, { keep: true, selected: saved.id });
            }
        } catch (err) {
            const msg = err?.message || '無法新增翻譯內容。';
            setExtraStatus(msg, 'error');
            if (k === 'item') {
                if (/道具 ID/.test(msg)) extraItemId?.focus();
                else if (/英文原名/.test(msg)) extraItemEn?.focus();
                else if (/中文名稱/.test(msg)) extraItemZh?.focus();
                else if (/Lore/.test(msg)) extraItemLore?.focus();
            } else if (/原文/.test(msg)) {
                extraSource?.focus();
            } else if (/譯文|統一名詞/.test(msg)) {
                extraValue?.focus();
            }
        } finally {
            extraSubmitting = false;
            if (btnNext) btnNext.disabled = false;
            if (btnView) btnView.disabled = false;
        }
    };

    if (extraModal) {
        populateColorSwatches(document.getElementById('extra-colors'));
        document.getElementById('extra-format-tools')?.addEventListener('click', event => {
            const button = event.target.closest('[data-code]');
            if (!button) return;
            const code = button.dataset.code;
            const details = document.getElementById('extra-colors')?.closest('details');
            if (details) details.open = false;
            const target = extraFocusField || (extraKind.value === 'item' ? extraItemZh : extraValue);
            applyFormatInsert(target, '§' + code, code, () => {
                setExtraStatus();
                updateExtraHintsAndPreview();
            });
        });

        for (const f of [extraValue, extraItemZh, extraItemLore]) {
            f?.addEventListener('focus', () => { extraFocusField = f; });
        }
        extraItemId?.addEventListener('input', () => {
            extraItemIdCustom = Boolean(extraItemId.value.trim());
            setExtraStatus();
            updateExtraHintsAndPreview();
        });
        extraItemEn?.addEventListener('input', () => {
            if (!extraItemIdCustom) {
                extraItemId.value = extraItemEn.value.trim().toUpperCase().replace(/[^A-Z0-9]+/g, '_').replace(/^_+|_+$/g, '').slice(0, 64);
            }
            setExtraStatus();
            updateExtraHintsAndPreview();
        });
        for (const el of [extraSource, extraValue, extraItemZh, extraItemLore]) {
            el?.addEventListener('input', () => {
                setExtraStatus();
                updateExtraHintsAndPreview();
            });
        }
        extraKind?.addEventListener('change', updateExtraModeView);
        extraItemRarity?.addEventListener('change', updateExtraHintsAndPreview);
        document.getElementById('btn-open-extra-modal')?.addEventListener('click', () => openExtraModal());
        document.getElementById('btn-extra-save-next')?.addEventListener('click', () => submitExtra(true));
        document.getElementById('btn-extra-save-view')?.addEventListener('click', () => submitExtra(false));
        document.addEventListener('hsbtr:open-extra-modal', event => {
            openExtraModal({ presetKind: event.detail?.kind || '', presetSource: event.detail?.source || '' });
        });
        document.addEventListener('hsbtr:cloud-ready', () => {
            if (!extraModal.classList.contains('hidden') && extraStatus?.textContent?.includes('載入中')) {
                setExtraStatus('✔ 雲端資料庫已就緒。', 'ok');
            }
        });
        document.addEventListener('hsbtr:cloud-auth', () => {
            if (!extraModal.classList.contains('hidden') && window.HsbtrCloud?.getSnapshot().auth?.loggedIn) {
                if (extraStatus?.textContent?.includes('GitHub 帳號驗證')) {
                    setExtraStatus('✔ 已完成 GitHub 登入，請再次點擊送出按鈕。', 'ok');
                }
            }
        });
    }

    if (window.HsbtrCloud?.getSnapshot().ready) {
        run(() => loadPage(0));
    } else {
        openEntry(null);
        byId('empty').textContent = '正在載入雲端翻譯資料庫，請稍候…';
        feedback('正在載入雲端翻譯資料庫…');
        syncControls();
    }
})();
