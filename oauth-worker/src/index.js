/**
 * Cloudflare Worker: GitHub OAuth 2.0 (PKCE) Token Exchange Proxy for hsbtc-mod
 * Keeps GITHUB_CLIENT_SECRET safely off GitHub Pages while allowing browser SPA
 * on https://o-frost-o.github.io/hsbtc-mod/ to complete official GitHub login.
 */
'use strict';

const DEFAULT_ALLOWED_ORIGINS = ['https://o-frost-o.github.io'];
const DEFAULT_REDIRECT_PREFIX = 'https://o-frost-o.github.io/hsbtc-mod';

function parseAllowedOrigins(env) {
    const raw = typeof env?.ALLOWED_ORIGINS === 'string' && env.ALLOWED_ORIGINS.trim()
        ? env.ALLOWED_ORIGINS
        : DEFAULT_ALLOWED_ORIGINS.join(',');
    return raw.split(',').map(s => s.trim()).filter(Boolean);
}

function corsHeaders(origin, allowed) {
    const headers = {
        'Vary': 'Origin',
        'Cache-Control': 'no-store',
        'Access-Control-Allow-Methods': 'POST, OPTIONS',
        'Access-Control-Allow-Headers': 'Content-Type',
        'Access-Control-Max-Age': '86400'
    };
    if (origin && allowed.includes(origin)) {
        headers['Access-Control-Allow-Origin'] = origin;
    }
    return headers;
}

function jsonResponse(body, status, extraHeaders = {}) {
    return new Response(JSON.stringify(body), {
        status,
        headers: {
            'Content-Type': 'application/json; charset=utf-8',
            'Cache-Control': 'no-store',
            ...extraHeaders
        }
    });
}

export async function handleRequest(request, env = {}, fetchImpl = fetch) {
    const url = new URL(request.url);
    const origin = request.headers.get('Origin') || '';
    const allowedOrigins = parseAllowedOrigins(env);
    const headers = corsHeaders(origin, allowedOrigins);

    if (request.method === 'GET' && url.pathname === '/health') {
        return jsonResponse({
            ok: true,
            configured: Boolean(env.GITHUB_CLIENT_ID && env.GITHUB_CLIENT_SECRET)
        }, 200, headers);
    }

    if (request.method === 'OPTIONS') {
        if (!origin || !allowedOrigins.includes(origin)) {
            return new Response(null, { status: 403, headers: { 'Vary': 'Origin' } });
        }
        return new Response(null, { status: 204, headers });
    }

    if (request.method !== 'POST' || (url.pathname !== '/exchange' && url.pathname !== '/')) {
        return jsonResponse({ error: 'not_found', error_description: 'Endpoint not found.' }, 404, headers);
    }

    if (!origin || !allowedOrigins.includes(origin)) {
        return jsonResponse({ error: 'forbidden_origin', error_description: 'Origin not allowed.' }, 403, headers);
    }

    if (!env.GITHUB_CLIENT_ID || !env.GITHUB_CLIENT_SECRET) {
        return jsonResponse({
            error: 'server_not_configured',
            error_description: 'Worker missing GITHUB_CLIENT_ID or GITHUB_CLIENT_SECRET.'
        }, 500, headers);
    }

    let payload;
    try {
        payload = await request.json();
    } catch {
        return jsonResponse({ error: 'invalid_json', error_description: 'Request body must be valid JSON.' }, 400, headers);
    }

    const code = typeof payload?.code === 'string' ? payload.code.trim() : '';
    const codeVerifier = typeof payload?.code_verifier === 'string' ? payload.code_verifier.trim() : '';
    const redirectUri = typeof payload?.redirect_uri === 'string' ? payload.redirect_uri.trim() : '';
    const allowedRedirectPrefix = env.ALLOWED_REDIRECT_PREFIX || DEFAULT_REDIRECT_PREFIX;

    if (!code || code.length > 256 || !/^[A-Za-z0-9_.\-]+$/.test(code)) {
        return jsonResponse({ error: 'invalid_code', error_description: 'Invalid authorization code.' }, 400, headers);
    }
    if (!codeVerifier || codeVerifier.length < 43 || codeVerifier.length > 128 || !/^[A-Za-z0-9\-._~]+$/.test(codeVerifier)) {
        return jsonResponse({ error: 'invalid_code_verifier', error_description: 'Invalid PKCE code_verifier.' }, 400, headers);
    }
    if (!redirectUri || !redirectUri.startsWith(allowedRedirectPrefix)) {
        return jsonResponse({ error: 'invalid_redirect_uri', error_description: 'Redirect URI not allowed.' }, 400, headers);
    }

    const ghResp = await fetchImpl('https://github.com/login/oauth/access_token', {
        method: 'POST',
        headers: {
            'Accept': 'application/json',
            'Content-Type': 'application/json',
            'User-Agent': 'HSBTC-OAuth-Worker/1.0'
        },
        body: JSON.stringify({
            client_id: env.GITHUB_CLIENT_ID,
            client_secret: env.GITHUB_CLIENT_SECRET,
            code,
            code_verifier: codeVerifier,
            redirect_uri: redirectUri
        })
    });

    const ghData = await ghResp.json().catch(() => ({}));
    if (!ghResp.ok || ghData.error || !ghData.access_token) {
        return jsonResponse({
            error: ghData.error || 'token_exchange_failed',
            error_description: ghData.error_description || `GitHub HTTP ${ghResp.status}`
        }, 400, headers);
    }

    return jsonResponse({
        access_token: ghData.access_token,
        token_type: ghData.token_type || 'bearer',
        scope: ghData.scope || ''
    }, 200, headers);
}

export default {
    async fetch(request, env) {
        return handleRequest(request, env);
    }
};
