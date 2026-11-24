# HSBTC GitHub OAuth Proxy (Cloudflare Workers)

此目錄提供給 `https://o-frost-o.github.io/hsbtc-mod/` 使用的免費 **Cloudflare Workers** OAuth Token 交換代理程式。
由於 GitHub 官方的 `/login/oauth/access_token` 禁止瀏覽器跨網域（CORS）直接請求，且 `Client Secret` 不可寫入公開靜態網頁，透過這個極簡 Worker 即可讓所有使用者安全地透過 **GitHub 官方授權畫面 (`github.com/login/oauth/authorize`)** 一鍵登入。

## 設定步驟（約 3 分鐘）

### 1. 建立 GitHub OAuth App
1. 前往 [GitHub Developer Settings → OAuth Apps → New OAuth App](https://github.com/settings/applications/new)。
2. 填寫以下資訊：
   - **Application name**：`HSBTC 繁中翻譯平台`
   - **Homepage URL**：`https://o-frost-o.github.io/hsbtc-mod/`
   - **Authorization callback URL**：`https://o-frost-o.github.io/hsbtc-mod/`
3. 建立後複製 **Client ID**，並點擊 **Generate a new client secret** 取得 **Client Secret**。

### 2. 部署至 Cloudflare Workers（免費方案）
在 `oauth-worker/` 目錄下執行：

```bash
npx wrangler login
npx wrangler secret put GITHUB_CLIENT_ID
npx wrangler secret put GITHUB_CLIENT_SECRET
npx wrangler deploy
```

> 亦可直接在 Cloudflare Dashboard 建立 Worker，貼上 `src/index.js` 內容，並在 **Settings → Variables and Secrets** 新增：
> - 純文字變數：`ALLOWED_ORIGINS` = `https://o-frost-o.github.io`、`ALLOWED_REDIRECT_PREFIX` = `https://o-frost-o.github.io/hsbtc-mod`
> - 加密 Secret：`GITHUB_CLIENT_ID`、`GITHUB_CLIENT_SECRET`

部署完成後會取得專屬端點網址，例如：
`https://hsbtc-github-oauth.<你的帳號>.workers.dev/exchange`

### 3. 綁定至前端設定 (`docs/hsbtr-cloud.js`)
開啟 `docs/hsbtr-cloud.js` 並填入公開的 `oauthClientId` 與 `oauthExchangeUrl`（**切勿填入 `Client Secret`**）：

```js
oauthClientId: 'Ov23li...',
oauthExchangeUrl: 'https://hsbtc-github-oauth.<你的帳號>.workers.dev/exchange'
```

推送至 `main` 後，GitHub Actions 會自動更新 `https://o-frost-o.github.io/hsbtc-mod/`，所有使用者即可點擊「使用 GitHub 官方帳號登入」直接完成授權。
