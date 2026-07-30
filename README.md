# 電商轉盤抽獎平台 (ecommerce-lottery-platform)

一個電商轉盤抽獎後端，重點放在**高併發下的庫存與抽獎次數控制**，以及一個可水平擴展、環境可切換的部署形態。前後端分離、RESTful API、JWT 權限分級，附一頁極簡轉盤前端可直接玩。

> 三種獎品各有庫存與不同中獎機率，加上「銘謝惠顧」合計 100%；支援單抽 / 連抽；防止重複抽獎、防止超抽、防止超出個人次數上限。

---

## 功能對照（考題需求）

| 需求 | 實作 |
|---|---|
| 多獎品 + 庫存 + 機率，銘謝惠顧合計 100% | `prize` 表，機率用整數萬分比；活化活動時驗證總和 = 10000 |
| 獎品內容可動態配置 | `/api/admin/**` 可即時改名稱 / 機率 / 庫存，並刷新 Redis |
| 單抽 / 連抽 | `POST /api/activities/{id}/draws` 帶 `times`（1–10） |
| 活動各自的抽獎次數上限 | `lottery_activity.per_user_draw_limit`、`total_draw_limit` |
| 使用者不可超出次數 | Redis Lua 原子預扣配額 |
| 防止超抽 | Redis 預扣 + **DB 條件式扣減為最終真值**（`WHERE remaining_stock > 0`） |
| 防止重複抽獎 | 冪等鍵（`SET NX` + `draw_record` 唯一索引），重送回同結果 |
| 高併發事務一致性 | 見「併發設計」；併發整合測試證明不超抽 / 不超次數 |
| 分散式可水平擴展 | 無狀態服務（JWT）、Redis/MySQL 為共享狀態，附 K8s 3 副本範例 |
| 環境變數切換 DataSource / 連線池 | `application.yml` 全部吃環境變數（含 HikariCP） |
| 前後端分離 + RESTful + 驗證 + 權限分級 | Spring Web + Spring Security（USER / ADMIN） |
| 錯誤流程 + 輸入驗證 | 全域 `@RestControllerAdvice` + Bean Validation |
| API 文件 | springdoc / Swagger UI |
| Table Schema | Flyway `V1__init_schema.sql`（DDL）+ `V2__seed_data.sql`（DML） |
| 單元測試 | 機率分布 / 邊界 / 錯誤場景 + Testcontainers 併發測試 |

---

## 技術棧

Java 17 · Spring Boot 3.3（Web / Data JPA / Security / Validation / Actuator）· MySQL 8 · Redis 7 · Flyway · JWT（jjwt）· springdoc-openapi · JUnit5 + Mockito + Testcontainers · Docker / K8s。

---

## 架構與併發設計

採**模組化單體 + DDD 分層**：每個 bounded context（`activity` / `prize` / `draw` / `riskcontrol` / `auth`）內部再分 `domain / application / api`。刻意不拆成多個微服務——以這題的規模，拆服務只會增加分散式事務與維運成本；但邊界切乾淨了，之後要拆隨時可拆（見 [docs/architecture.md](docs/architecture.md) 的演進路徑）。

**併發正確性的核心想法：DB 是最終真值，Redis 是擋在前面的加速層。**

一次抽獎的流程：

1. **配額**：Redis Lua 原子檢查並預扣使用者（與活動整體）抽獎次數，超過就整批擋掉。
2. **冪等**：同一 `idempotencyKey` 直接回放原結果，不會重複抽。
3. **選獎**：加權隨機（含銘謝惠顧）選出候選獎品。
4. **庫存**：Redis Lua 原子預扣該獎品庫存；成功後再用 DB 條件式扣減
   `UPDATE prize SET remaining_stock = remaining_stock - 1 WHERE id = ? AND remaining_stock > 0`。
   這行 SQL 讓**超抽在資料庫層根本不可能發生**——就算 Redis 因故放行，DB 回傳 0 列就代表真的沒了，於是**降級為「銘謝惠顧」**並把 Redis 的預扣補回去。

換句話說：Redis 掛了，抽獎照樣正確（冷快取會回退到 DB），只是熱門獎品的 DB 壓力會上升。這個取捨在面試時很好講。

併發測試 `ConcurrentDrawTest` 用真的 MySQL + Redis，300 條執行緒同時搶 20 個庫存，斷言中獎數恰好等於庫存、`remaining_stock` 不為負；另一個測試讓單一使用者狂打 50 次、上限 5 次，斷言剛好 5 次成功。

---

## 快速開始（Docker）

```bash
docker compose up --build
```

- API / 前端：<http://localhost:8080>
- Swagger UI：<http://localhost:8080/swagger-ui.html>
- 預設帳號：`alice / alice123`（USER）、`admin / admin123`（ADMIN）

打開首頁登入就能轉盤。單抽、連抽 5 次、抽獎紀錄都能玩。

## 本機開發（不用 compose 跑 app）

```bash
# 只起基礎設施
docker run -d --name mysql -e MYSQL_DATABASE=lottery -e MYSQL_USER=lottery \
  -e MYSQL_PASSWORD=lottery -e MYSQL_ROOT_PASSWORD=root -p 3306:3306 mysql:8.0
docker run -d --name redis -p 6379:6379 redis:7-alpine

./mvnw spring-boot:run
```

## 測試

```bash
./mvnw test
```

單元測試（機率分布 / 邊界 / 錯誤場景）不需要任何基礎設施即可跑；`ConcurrentDrawTest` 需要本機有 Docker（Testcontainers 會自動起 MySQL + Redis）。

---

## API 一覽

| Method | Path | 權限 | 說明 |
|---|---|---|---|
| POST | `/api/auth/register` | 公開 | 註冊一般使用者 |
| POST | `/api/auth/login` | 公開 | 登入取得 JWT |
| GET | `/api/activities` | 登入 | 進行中的活動 |
| GET | `/api/activities/{id}` | 登入 | 活動明細 + 獎品 |
| POST | `/api/activities/{id}/draws` | USER | 單抽 / 連抽 |
| GET | `/api/activities/{id}/my-records` | USER | 我的抽獎紀錄 |
| POST | `/api/admin/activities` | ADMIN | 建立活動 |
| PUT | `/api/admin/activities/{id}` | ADMIN | 更新活動（活化時驗證機率總和） |
| POST | `/api/admin/activities/{id}/prizes` | ADMIN | 新增獎品 |
| PUT | `/api/admin/prizes/{id}` | ADMIN | 改機率 / 庫存（即時生效） |
| GET | `/api/admin/activities/{id}/stats` | ADMIN | 抽獎統計 |

所有回應統一包在 `{ success, code, message, data }`。詳細參數見 Swagger。

---

## 資料表

見 [`src/main/resources/db/migration/V1__init_schema.sql`](src/main/resources/db/migration/V1__init_schema.sql)：
`app_user` / `lottery_activity` / `prize` / `draw_record`。機率以整數萬分比儲存，避免浮點誤差。

---

## 目錄結構

```
src/main/java/com/amway/ecommerce/lottery
├── activity      # 活動 aggregate + 查詢
├── prize         # 獎品 aggregate + 機率驗證
├── draw          # 抽獎用例、加權隨機、紀錄
├── riskcontrol   # Redis Lua 原子配額 / 庫存
├── auth          # JWT、Spring Security、角色
├── admin         # 動態配置 API
├── common        # ApiResponse、全域例外
└── config        # OpenAPI、picker bean
```

---

## 設計取捨 / 已知限制（待改進）

- **超抽以 DB 為準、Redis 為輔**：正確性不依賴 Redis，但 Redis 與 DB 之間仍是最終一致。極端情況下（app 在預扣 Redis 後、DB commit 前 crash）Redis 庫存可能短暫少算——**只會少賣、不會超賣**，可靠對帳排程補回。目前先留 TODO。
- **中獎通知**目前同步寫 DB，之後應改成發 MQ 事件（出貨 / 通知解耦）。
- 連抽是逐次抽、逐筆寫；量大時可改批次寫入降低 DB round-trip。
- 尚未做 rate-limit（IP 層）與驗證碼；正式上線前需補。
- 前端只是驗證用的單頁 demo，不代表正式前端。

> 之後想做：把 `draw`（寫）與 `stats/records`（讀）拆成 CQRS 讀寫分離，讀走獨立投影表 / 快取。
