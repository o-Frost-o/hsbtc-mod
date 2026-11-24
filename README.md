# Hypixel SkyBlock 繁體中文翻譯模組 (`hsbtc-mod`)

適用 **Minecraft 26.2 (Fabric)** 的 Hypixel SkyBlock 非破壞性繁體中文顯示層翻譯模組，並搭配獨立運作的 GitHub Pages 共用翻譯平台。

- 作者：`o.frost.o` (`o-Frost-o`)
- 協助翻譯：[https://o-frost-o.github.io/hsbtc-mod/](https://o-frost-o.github.io/hsbtc-mod/)

最近發現我AI的額度用不完了，有一天邀請朋友玩Hypixel Skyblock被以不會英文為由回絕後靈感乍現，對阿我把它翻成中文不就行了！ :D

## 功能

### NPC對話、介面、道具......通通都翻譯了！

本模組的目標是將遊戲的所有內容進行翻譯，目前已經翻譯了約90%的文本，資料來源於官方及粉絲Wiki，安裝模組後會自動翻譯內容至中文，在聊天室或道具敘述顯示時按下Alt鍵即可顯示原文。

### 浮動數值翻譯適應及中文倒裝適應

為滿足以上需求，我們將特定的單字或數值賦予一個該文本項目中獨特的Token ID，並在翻譯後的文本將不同Token放到合適的位置中。

舉個例子：You received 1 wood from Charlie 可以對 1 wood 和 Charlie 賦予Token ID-->You received {token:1} from {token:2} --> 你從Charlie獲得了1 wood！

當然，Token本身可以經過翻譯：

你從Charlie獲得了1 wood！ --> 你從查理(Charlie)獲得了1塊木頭！

