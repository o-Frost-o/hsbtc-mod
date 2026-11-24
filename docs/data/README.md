# HSBTR 雲端翻譯資料庫 (`docs/data`)

此目錄為 **Hypixel SkyBlock 繁體中文翻譯網頁 (`github.io`)** 的獨立雲端資料庫，供所有編輯者共享，不依賴本機 Minecraft 熱重載。

## 目錄結構

- `base/`：模組內建基準字典快照（`tokens.json`, `items.json`, `item_lore_en.json`, `lore_lines.json`, `menus.json`, `dialogues.json`）。
- `overrides.json`：由線上編輯器與 GitHub Actions IssueOps 累積的所有社群新增／修改譯文與動態規則。讀取時會依層級覆蓋於 `base/` 之上。
- `review_status.json`：已驗證條目的 `entryId -> revision` SHA-256 雜湊對照表（與遊戲設定資料夾內的 `review_status.json` 格式相容）。
- `contributors.json`：已驗證的 GitHub 編輯者貢獻統計；當唯一已驗證條目數 `verifiedCount >= threshold`（預設 `25`）時，會自動顯示於網頁的貢獻者名單。

## 後續手動使用 Agent / 指令同步回 Mod

若要將雲端累積的翻譯合併回 Mod 原始碼字典（`src/main/resources/assets/hsbtr/dictionaries/`），可執行：

```bash
node scripts/sync_cloud_db.mjs
```

此腳本會安全將 `docs/data/overrides.json` 與 `docs/data/review_status.json` 的更新套用至 `src/main/resources/assets/hsbtr/dictionaries/` 與 `docs/data/base/`（並可加上 `--reset-overrides` 在版本封裝時清空差異檔），且不影響 Mod 的任何運行時設定。
