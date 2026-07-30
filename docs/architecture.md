# 架構說明與演進路徑

## 為什麼是模組化單體，而不是一開始就微服務

以這題的規模，直接拆微服務會付出分散式事務、服務間呼叫、部署與觀測的成本，卻換不到對應的好處。所以我選**模組化單體**：一個可部署單元，但內部用 DDD 的 bounded context 把邊界切乾淨，讓「之後要拆」變成低成本的事，而不是一次性大重構。

每個 context 內部分層：

```
<context>
├── domain          # 實體 / 值物件 / 領域規則（不依賴 Spring）
├── application     # 用例、交易邊界
├── api             # REST controller、DTO
└── infrastructure  # (需要時) 外部整合
```

聚合根：`LotteryActivity`、`Prize`、`DrawRecord`。`riskcontrol` 是獨立的防護層（Redis 原子操作），透過 `RiskControlPort` 介面被 `draw` 用例依賴——這個 port 就是未來抽換實作（例如換成獨立限流服務）的接縫。

## 併發一致性模型

| 關注點 | 機制 | 最終真值 |
|---|---|---|
| 使用者次數上限 | Redis Lua `INCRBY` + 上限比對（原子） | Redis（配額）+ `draw_record` 稽核 |
| 活動整體次數上限 | Redis Lua（同上，不同 key） | Redis |
| 獎品庫存 | Redis 原子預扣 → DB 條件式扣減 | **DB**（`WHERE remaining_stock > 0`） |
| 重複抽獎 | `SET NX` 冪等鍵 + `draw_record` 唯一索引 | DB 唯一索引 |

關鍵：**庫存正確性錨定在 DB 的條件式更新**，Redis 只是把熱點流量擋在資料庫前面。因此 Redis 故障只影響吞吐，不影響「絕不超賣」這個硬保證。

## 演進到 CQRS

目前讀寫共用同一組表。流量成長後：

- **寫側（Command）**：抽獎維持強一致（Redis + DB 交易）。
- **讀側（Query）**：`my-records`、`stats`、活動列表改走獨立投影（read model），由抽獎事件非同步更新，或直接讀快取。
- 中獎事件（`PrizeAwarded`）發到 MQ，出貨 / 通知 / 統計各自訂閱，達成解耦。

程式上已預留接縫：查詢走獨立的 `*QueryService`，與寫入用例分開，改成打投影表不會動到寫側。

## 拆成微服務時的切法

若真要拆，自然的邊界就是現有的 context：

- **lottery-core**：活動 / 獎品 / 抽獎（寫側，持有 MySQL）。
- **risk-control**：配額 / 庫存原子操作（持有 Redis），對外暴露 gRPC/REST。
- **query / bff**：讀模型與前端聚合。
- **auth**：獨立身分服務或走 API Gateway + OIDC。

服務間以事件（MQ）為主、同步呼叫為輔；跨服務一致性用 Saga / outbox，而不是分散式交易。

## 可觀測性與部署

- Actuator 暴露 `health`（liveness/readiness probes）與 `info`（公開）、`metrics`（需登入）。
- 無狀態 → K8s 多副本水平擴展（見 `deploy/k8s/deployment.yaml`）。
- 全部組態走環境變數 → 同一映像檔跑遍 dev / staging / prod。
