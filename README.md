# 坦克車戰爭

這是一個使用 JavaFX + Java Socket 製作的 2D 俯視角坦克對戰遊戲，主題對應期末專題的「多人連線坦克對戰」。

## 功能特色

- Java Socket 多人連線架構
- JavaFX 大廳介面，可輸入玩家名稱、伺服器 IP、Port
- 支援同一台電腦開兩個 Client 測試
- 支援兩台不同電腦在同一個區域網路連線對戰
- Server 負責遊戲規則：移動、射擊、子彈、扣血、道具、AI、勝負判定
- Client 負責玩家輸入與畫面渲染
- 使用 `image/` 資料夾內的圖片素材顯示坦克、砲塔、子彈與特效
- 每局會產生簡單隨機地圖，牆面位置會有變化
- 單人測試時會自動加入 AI；兩位真人玩家連線後會切換成純雙人對戰
- 遊戲中可按 `ESC` 開啟暫停選單，並可返回大廳重新連線

## 執行方式

本專案目前不依賴 Maven 指令啟動，請使用 `scripts/` 內的 PowerShell 腳本。

## Java 版本需求

- 最低需求：Java 17 或更新版本。
- 不一定要 Java 21；Java 17、Java 21 都可以。
- 如果同學電腦只有 Java 8 或 Java 11，會出現版本太舊或 class version 不支援的錯誤，請先安裝 JDK 17/21。
- 也可以把可攜版 JDK 放在專案根目錄，資料夾名稱可用 `jdk-17`、`jdk-21` 或 `jdk`，腳本會自動優先使用。
- 本專案的腳本會用 `--release 17` 編譯，避免用較新 JDK 編譯後導致同學電腦無法執行。

### 1. 啟動伺服器

開啟第一個 PowerShell 終端機：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-server.ps1
```

伺服器預設使用 Port `7788`。

啟動成功後會看到類似：

```text
Tank server started on port 7788
Same computer: 127.0.0.1 | LAN hint: 192.168.0.22
```

其中 `LAN hint` 是這台電腦在區域網路中的 IP，可給另一台電腦連線使用。

### 2. 啟動 Client

開啟另一個 PowerShell 終端機：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-client.ps1
```

Client 啟動後會先進入大廳畫面。

大廳欄位說明：

```text
名稱  玩家名稱
主機  伺服器 IP
Port  伺服器 Port，預設 7788
房間代碼  同一場遊戲請輸入相同房號，例如 ROOM1
模式  PVE 合作打 AI，或 PVP 紅藍隊對戰
戰車  突擊、輕型、重型、狙擊四種車款
```

戰車能力差異：

```text
突擊坦克      平均能力，適合新手
輕型偵查車    移動快、射速快，但血量較低
重型坦克      血量高、傷害高，但速度慢、裝填慢
狙擊坦克      子彈快、射程遠、傷害高，但射速最慢
```

同一台電腦測試時，主機輸入：

```text
127.0.0.1
```

兩台不同電腦連線時，主機輸入伺服器電腦顯示的 `LAN hint` IP。

## 同機雙人測試

1. 開一個終端機執行 `run-server.ps1`
2. 開第二個終端機執行 `run-client.ps1`
3. 再開第三個終端機執行一次 `run-client.ps1`
4. 兩個 Client 的主機都輸入 `127.0.0.1`
5. 兩個 Client 輸入相同房間代碼，例如 `ROOM1`
6. 模式選 `PVP 紅藍隊對戰`
7. 進房後一位選紅隊、一位選藍隊，兩位都按「準備」後開始

## 期末展示流程建議

1. 先啟動伺服器，讓畫面顯示 Port 與 `LAN hint`
2. 啟動第一個 Client，展示大廳可輸入名稱、主機、Port、房間代碼與模式
3. 選 `PVE 合作打 AI`，加入房間後按「準備」，展示合作打 AI、隨機地圖與道具
4. 打完一局後展示結算畫面，稍後會回到同一個房間準備畫面，不需要重新輸入 IP
5. 啟動第二個 Client，輸入同房號並選 `PVP 紅藍隊對戰`
6. 展示紅隊/藍隊選擇、準備流程、滑鼠瞄準、射擊、命中、爆炸、拾取道具與音效
7. 按 `ESC` 展示暫停選單，再按「返回大廳」展示欄位仍保留上次 IP/Port/房號

## 雙電腦連線測試

### 伺服器電腦

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-server.ps1
```

記下畫面上的 `LAN hint` IP，例如：

```text
192.168.0.22
```

### Client 電腦

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-client.ps1
```

在大廳的主機欄位輸入伺服器電腦的 IP，例如：

```text
192.168.0.22
```

Port 輸入：

```text
7788
```

如果連線失敗，請確認：

- 兩台電腦在同一個 Wi-Fi 或同一個區域網路
- Windows 防火牆允許 Java 存取私人網路
- 主機 IP 輸入的是伺服器電腦的 IPv4 位址
- Server 和 Client 使用相同的 Port

## 操作方式

```text
W A S D      移動坦克
滑鼠移動     瞄準砲塔
滑鼠左鍵     射擊
Space       射擊
ESC         開啟/關閉暫停選單
```

暫停選單提供：

```text
繼續遊戲
返回大廳
```

返回大廳會離開目前對戰並中斷連線，可重新輸入主機與 Port 加入遊戲。

## 圖片素材

遊戲會使用 `image/` 資料夾中的素材：

```text
tankbody.png   坦克車身
tankhead.png   坦克砲塔
Bullet.png     子彈
Shot1.png      開火特效
Shot2.png      命中特效
heal.png       補血道具
boost.png      加速道具
rapid.png      快速射擊道具
+Black.png     瞄準鏡準星
tank_scout_body.png    輕型偵查車車身
tank_scout_head.png    輕型偵查車砲塔
tank_heavy_body.png    重型坦克車身
tank_heavy_head.png    重型坦克砲塔
tank_assault_body.png  突擊坦克車身
tank_assault_head.png  突擊坦克砲塔
tank_sniper_body.png   狙擊坦克車身
tank_sniper_head.png   狙擊坦克砲塔
tank_ai_generated_sheet.png  AI 生成素材來源圖
```

請保持檔名不變，否則遊戲會改用內建 Canvas 繪圖作為備用畫面。

## 隨機地圖

每一局遊戲開始時，Server 會產生新的牆面配置：

- 保留四周邊界牆
- 保留少量中心掩體
- 隨機產生 7 到 10 堵水平或垂直牆
- 避免牆面太靠近出生區
- 避免牆面彼此重疊太多

## 編譯測試

如需確認程式是否能正常編譯：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\build.ps1
```

看到以下訊息代表編譯成功：

```text
Build complete: 專案資料夾\target\classes
```
