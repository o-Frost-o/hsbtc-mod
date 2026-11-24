# Hypixel SkyBlock 繁體中文翻譯模組 (`hsbtc-mod`)

適用 **Minecraft 26.2 (Fabric)** 的 Hypixel SkyBlock 非破壞性繁體中文顯示層翻譯模組，並搭配獨立運作的 GitHub Pages 共用翻譯平台。

- 作者：`o.frost.o` (`o-Frost-o`)
- 獨立翻譯網站：[https://o-frost-o.github.io/hsbtc-mod/](https://o-frost-o.github.io/hsbtc-mod/)
- 指令說明：
  - `/hsbtr ui`（或 `/hsbtr web`）：開啟獨立線上翻譯審核平台
  - `/hsbtr reload`：重新載入本機字典與設定
  - `/hsbtr status`：檢視各介面類別攔截模式與字典統計

## 主要特色

- **非破壞性渲染層翻譯（DISPLAY Mode）**：保留伺服器原始資料，確保與 Skyblocker 等模組相容。
- **按住 `Alt` / `Shift` 瞬時檢視英文原文**：除道具 Tooltip 外，聊天室中的 NPC / Boss 對話與互動選項亦支援按住 `Alt` 鍵即時切回英文原文預覽。
- **獨立雲端翻譯網頁（GitHub Pages + IssueOps）**：
  - 全網頁前端與雲端共享資料庫位於 `docs/`，已從 Mod 本體完全抽離（無需本機伺服器或熱重載）。
  - 所有編輯與驗證需先完成 GitHub 帳號驗證（支援倉庫協作者直寫與一般貢獻者透過 GitHub Actions IssueOps 自動合併寫入）。
  - 累積至少 **25 條**唯一翻譯審核驗證的編輯者將自動列入網頁的 **🏆 貢獻者名單**。

## 開發者／Agent 資料庫回寫

若要手動將線上平台累積的 `docs/data/overrides.json` 與 `review_status.json` 同步回 Mod 內建資源字典：

```bash
node scripts/sync_cloud_db.mjs
```
