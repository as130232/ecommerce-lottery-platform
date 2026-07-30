# 前端視覺統一與升級：Refined SaaS 設計

## Context

目前 `index.html`（前台轉盤抽獎）與 `admin.html`（後台設定）兩個靜態頁面存在兩個問題：

1. **不一致** — 登入框、按鈕、卡片、顯示框在兩頁之間排版與樣式各異（例如 ghost 按鈕定義不同、登入框結構不同、padding 不一致）。
2. **設計過時** — 標準的「Bootstrap 年代」組合（`#f4f5f7` 灰底 + 白卡片 + 純色紅 header），視覺扁平、缺乏質感。

目標：把兩頁統一成同一套設計語言（**只有主色不同來區分前後台**），並升級為現代、有質感的「Refined SaaS」風格（參考 Linear / Vercel / Stripe dashboard 的克制美學）。

**設計原則的關鍵取捨：**
- 結構感靠 **1px hairline 邊框**，不靠重陰影（避免 Material Design 舊感）。
- 陰影 whisper-light（一至兩層極淡）。
- Header 用極微幅（≈8%）單色深度漸層或純色，不用大幅度彩色漸層。

## 範圍

只改兩個檔案，且皆為純靜態 HTML（inline CSS + JS）：
- `src/main/resources/static/index.html`
- `src/main/resources/static/admin.html`

**不動**：轉盤 canvas 繪製邏輯（`drawWheel` / `spinTo`）、所有 JS API 呼叫、`login()` / `logout()` 的行為邏輯（只調整其中操作 DOM 顯示的部分若結構有變）。不涉及後端。

## 設計 Token（兩頁 `<style>` 頂端 `:root`，除 accent/header 外完全相同）

```css
:root {
  --bg: #eef1f5;
  --surface: #ffffff;
  --border: rgba(15,23,42,.08);
  --border-strong: rgba(15,23,42,.12);
  --radius: 14px;
  --radius-sm: 8px;
  --shadow: 0 1px 2px rgba(15,23,42,.04), 0 6px 16px rgba(15,23,42,.05);
  --shadow-hover: 0 2px 4px rgba(15,23,42,.06), 0 10px 24px rgba(15,23,42,.09);
  --text: #0f172a;
  --muted: #64748b;
  --ring: color-mix(in srgb, var(--accent) 30%, transparent);
}
```

**每頁差異（唯一不同處）：**
- 前台 `index.html`：`--accent: #d81f2c`（緋紅）；header 深緋紅（`#c8102e` 為基底，≈8% 微幅深度漸層）
- 後台 `admin.html`：`--accent: #4f6cff`（靛藍）；header 純 `#1e2d3d`（深石板藍）

## 統一元件規格

兩頁以下元件的結構與樣式必須一致，只有 accent 顏色不同：

- **Header** — flex 兩欄（左：標題 + 導航連結；右：登入後的 user-chip）。標題 `font-weight:600` + `letter-spacing:-0.01em`。`.header-link` 樣式統一（細邊框 pill）。
- **Card** — `background:var(--surface)`、`border:1px solid var(--border)`、`border-radius:var(--radius)`、`box-shadow:var(--shadow)`。卡片標題排版統一。
- **登入框（loginCard）** — 兩頁統一為同一套結構：卡片內含標題 + 帳號/密碼欄位 + 登入鈕 + 訊息，僅 accent 色不同。（前台原為單純橫排 row、後台原為另一種排法 → 收斂成同一套。）
- **user-chip（右上角帳號區）** — 統一：半透明白底 pill 容器 + 帳號名 + 登出鈕。登入後顯示，登出後隱藏。
- **按鈕** — 主鈕背景 `var(--accent)`；`hover → transform:translateY(-1px)` + `box-shadow:var(--shadow-hover)`；`transition:150ms ease`。ghost 鈕統一為白底 + accent 邊框與文字。`.mini` size 修飾保留。
- **input / select** — 統一 padding、`border-radius:var(--radius-sm)`；`focus → border-color:var(--accent)` + `box-shadow:0 0 0 3px var(--ring)`。
- **數字顯示** — quota、機率（萬分比）、統計數字加 `font-variant-numeric:tabular-nums`。

## 驗證方式

1. 啟動 Spring Boot（`./gradlew bootRun` 或 IDE）。
2. 開 `http://localhost:8080/` 與 `http://localhost:8080/admin.html`：
   - 比對兩頁登入框 / 卡片 / 按鈕 / input 結構一致，僅顏色不同（前台緋紅、後台靛藍）。
   - 主按鈕 hover 有上移 + 陰影動效；input focus 有 accent ring。
   - 登入後右上角顯示統一的 user-chip；登出恢復初始狀態。
   - 前台轉盤仍正常繪製與旋轉；後台活動 / 獎品 / 統計功能正常。
