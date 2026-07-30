# 資料模型 (ER Diagram)

對應 Flyway `V1__init_schema.sql`。機率以整數萬分比（0–10000）儲存避免浮點誤差；`prize` 與 `lottery_activity` 帶 `version` 欄位做樂觀鎖。

```mermaid
erDiagram
    APP_USER ||--o{ DRAW_RECORD : "draws"
    LOTTERY_ACTIVITY ||--o{ PRIZE : "has"
    LOTTERY_ACTIVITY ||--o{ DRAW_RECORD : "produces"
    PRIZE ||--o{ DRAW_RECORD : "awarded as"

    APP_USER {
        bigint id PK
        varchar username UK
        varchar password_hash
        varchar role "USER / ADMIN"
        datetime created_at
    }

    LOTTERY_ACTIVITY {
        bigint id PK
        varchar code UK
        varchar name
        varchar status "DRAFT / ACTIVE / ENDED"
        int per_user_draw_limit
        bigint total_draw_limit "nullable"
        datetime start_time "nullable"
        datetime end_time "nullable"
        bigint version "optimistic lock"
        datetime created_at
        datetime updated_at
    }

    PRIZE {
        bigint id PK
        bigint activity_id FK
        varchar name
        varchar type "PRIZE / THANKS"
        int probability "basis of 10000"
        int total_stock
        int remaining_stock
        bigint version "optimistic lock"
        datetime created_at
        datetime updated_at
    }

    DRAW_RECORD {
        bigint id PK
        bigint activity_id FK
        bigint user_id FK
        bigint prize_id FK
        varchar result "WIN / THANKS"
        varchar idempotency_key UK "防重複抽獎"
        datetime created_at
    }
```

## 關係說明

- 一個 **活動 (LOTTERY_ACTIVITY)** 底下有多個 **獎品 (PRIZE)**，其中一筆 `type = THANKS` 代表「銘謝惠顧」、不消耗庫存；同一活動所有獎品機率總和必須為 10000。
- 每次抽獎寫一筆 **DRAW_RECORD**，指向活動、使用者與實際中到的獎品。
- `draw_record.idempotency_key` 為唯一鍵：同一抽獎請求重送不會重複入帳（前面另有 Redis `SET NX` 擋，DB 唯一鍵是最後防線）。
- `draw_record.user_id` 邏輯上參照 `app_user.id`；未建 DB 外鍵是刻意的——抽獎是高頻寫入路徑，少一個外鍵檢查可降低鎖競爭，使用者存在性由應用層（JWT）保證。
