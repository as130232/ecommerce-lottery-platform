# 水平擴展 / 分散式驗證

服務是**無狀態的**（JWT 認證、沒有 server-side session），共享狀態只在 MySQL 與 Redis。因此多開幾個 app 實例就能水平擴展，正確性由「Redis Lua 原子操作 + DB 條件式扣減」保證，跟實例數量無關。

`deploy/scaling/` 提供一個「**2 個 app 實例 + nginx 負載平衡 + 共享 MySQL/Redis**」的堆疊，用來實際證明併發保證在**跨行程**（不只單一 JVM 多執行緒）時依然成立。

## 啟動

```bash
docker compose -f deploy/scaling/docker-compose.scale.yml up --build
# 對外入口：http://localhost:8080  （可用 LB_PORT 環境變數改 host port）
```

## 已驗證

**1) nginx 有在兩個實例間輪詢**（回應的 `X-Upstream` 交替）：

```
X-Upstream: 172.22.0.5:8080   # app1
X-Upstream: 172.22.0.4:8080   # app2
X-Upstream: 172.22.0.5:8080
X-Upstream: 172.22.0.4:8080
```

**2) 兩個實例共享狀態**：同一使用者在 app1 抽獎、接著查詢紀錄卻由 app2 回應，仍讀得到剛剛那筆——證明抽獎、庫存、次數都落在共享的 MySQL/Redis：

```
draw #1       served by: 172.22.0.5:8080   (app1)
my-records    served by: 172.22.0.4:8080   (app2)  -> 回傳 1 筆（就是剛剛那次）
```

## 壓力測試：跨實例不超賣

用 admin 建一個高中獎率、低庫存的活動（例如中獎機率 90%、庫存 15），再用負載工具打進 nginx。因為每次抽獎最終都要過 DB 的
`UPDATE prize SET remaining_stock = remaining_stock - 1 WHERE id = ? AND remaining_stock > 0`，
無論請求落在哪個實例，中獎總數都不會超過庫存、`remaining_stock` 不會變負。

```bash
# 範例：用 hey 打 200 個並發抽獎請求（token 需先登入取得）
hey -n 200 -c 30 -m POST \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"times":1}' \
  http://localhost:8080/api/activities/<id>/draws

# 打完用 admin stats 檢查：awarded <= totalStock、remaining >= 0
curl -H "Authorization: Bearer $ADMIN" http://localhost:8080/api/admin/activities/<id>/stats
```

> 同樣的不變量也由 `ConcurrentDrawTest`（真 MySQL+Redis、300 併發）在 CI 層級自動驗證；本堆疊是把它延伸到「多實例」情境的手動 demo。

## 正式環境

`deploy/k8s/deployment.yaml` 是等價的 K8s 版本：`replicas: 3`、readiness/liveness probe、資源限制，組態一樣走環境變數，同一映像檔即可跨環境部署。
