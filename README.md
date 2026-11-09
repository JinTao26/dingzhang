# 叮账 · DingZhang

自动记账 + 自动分类的 Android 记账 App。**不手输，钱花了就自己记上。**
樱粉 pastel 二次元界面，Q 版精灵陪着你记账。

Kotlin + Jetpack Compose + 原生 SQLite，零第三方依赖，数据只留在这台手机上。

`https://github.com/JinTao26/dingzhang`

---

## 为什么做这个

市面上的开源记账 App（Ivy Wallet、Leaf、Veri Fin 等）都很漂亮，但几乎全部是**手动记账**——
App 只负责记和算，钱从哪儿来、花到哪儿去，还是得你自己填。国内的钱迹、小账本做了自动记账，但不开源。

叮账只解决一件事：**你付完款，账就已经在了。**

---

## 自动记账怎么实现（重要，先看这段）

微信和支付宝**没有面向个人的开放流水 API**。官方的 `downloadbill`、
`alipay.data.dataservice.bill.downloadurl.query` 全是**商户对账单**接口，个人账号拿不到。
所以任何声称"全自动同步微信支付宝"的 App，实际走的是下面几条路。

叮账走五条通道，收口到一条管线：

| 通道 | 原理 | 覆盖 | 稳定性 |
|---|---|---|---|
| **A 通知监听** | 读微信/支付宝的支付结果通知 | 支付宝基本都带金额；部分机型微信只推"支付凭证"无金额 | 中 |
| **B 无障碍服务** | 读支付结果页、账单详情页的控件文本 | **主力通道**，能拿金额+商户+支付方式 | 高（需授权） |
| **C 账单导入** | 导入官方导出的 Excel(xlsx) / CSV / ZIP | 最权威，可补历史、可对账 | 高（手动触发） |
| **D 银行短信** | 读银行动账短信（需一条短信权限） | 工资到账、线下刷卡、银行间转账——**这些钱不经过微信支付宝** | 中（只认动账，账单/额度提醒会被拦掉） |
| **E 手记** | 手动补一笔 | 兜底 | — |

五条通道的输入统一变成 `Captured`，交给 `IngestPipeline.submit()` 做过滤 → 去重 → 分类 → 落库。
不管从哪条路进来，去重和分类的规则是同一套。

### 明确不做的事

- **不 hook 微信**（Xposed / root / 注入）：违法风险、封号风险，还要求 root。
- **不做服务端代抓**：要把你的微信登录态交给别人的服务器，不做。
- **不读聊天数据库**：那是别人的隐私边界。

### 已知限制（不美化）

- 淘宝的商品页**主动屏蔽**无障碍读取（钱迹官方 FAQ 也承认），淘宝订单只能靠账单导入补。
- 微信红包要二次点开详情才能抓到金额。
- 云闪付不弹标准支付结果，覆盖较弱。
- 部分机型微信通知只推"支付凭证"没有金额 → 靠 B 通道兜底。
- **首次使用必须手动导一次历史账单**，否则只有从安装那天起的账。

---

## 自动分类

`MerchantDict` 里 300+ 条商户词表（按词长降序匹配，避免"星巴克"被"巴克"抢先命中），
覆盖 13 个分类：餐饮 / 饮品 / 交通 / 购物 / 家居 / 娱乐 / 健康 / 学习 / 社交 / 宠物 / 数字 / 其他 / 收入。

分类不是一次性的，它会学：

1. 你在流水页把某笔"美团"从「其他」改成「餐饮」；
2. `RuleLearning` 把"这个商户 → 这个分类"记成规则（`rule` 表，带命中次数）；
3. 下次同样的商户直接命中规则，不用再过词表。

规则表能导出、能随备份迁移，换手机不用重新教。

---

## 去重与对账

一笔真实消费往往同时从 A、B、C 三条通道进来，所以去重是核心：

- 同 `wallet` + 同金额，落在 `DUP_WINDOW_MS = 20 分钟`内 → 同一笔，合并（保留信息更全的那条）
- `DUP_STRICT_MS = 6 分钟`内 → 直接判重
- 每笔记录有 `fingerprint`（商户 + 金额 + 时间桶），跨通道对账用

导入账单时，库里已有的记录会被标成 `matched`（已对账），不重复记账。

---

## 导入账单的编码坑

微信导出的 CSV 是 **UTF-8 带 BOM**，支付宝的多是 **GBK/GB18030**，两家都是 **zip 里套 csv**。
`BillCsv.decode()` 的顺序：

1. 严格 UTF-8 解码（`CodingErrorAction.REPORT`，解不动会报错而不是静默出乱码）
2. 失败 → 退 GB18030
3. 去掉 BOM；是 zip 就先解压取第一个 csv

数据行判定不用噪声词表（`"共"` 这种词会误杀「共享单车」），而是**看第一列能不能解析成时间**——
能解析的才算数据行，表头、说明、小计都会被挡掉。

---

## v0.4.0（参考开源项目优化）

参照 Ivy Wallet（Compose + MVI、多账户、预算、周期交易）、Leaf（Kotlin + Room + 模块化导航）、
Veri Fin / Fin（数据自主、导出备份、无追踪）的共性，补了三块：

**1. 数据是你的，能带走**（`data/Exporter.kt`）

- 导出明细 CSV（带 BOM，Excel 直接开，可迁到别的记账 App）
- 完整备份 JSON（流水 + 分类规则 + 预算设置）
- 从备份恢复：按 `fingerprint` 跳过已存在的记录，**不会把账翻倍**

**2. 在自动扣的钱**（`data/RecurringDetector.kt`）

同商户 + 同金额、出现两次以上、间隔中位数稳定落在 7/30/90/365 天，才判定为周期扣款。
退订后（超过一个周期 + 宽限期没再扣）自动从列表消失。首页显示"约 ¥x/月"——
用户真正想知道的不是这个月花了多少，而是**哪些钱在自动往外流**。

**3. 应用内检查更新**（`update/UpdateChecker.kt`）

读 GitHub Releases 公开 API，逐段比较版本号（`v0.4.1` > `0.4.0`），入口在「我的」页。
不静默自安装——只告诉你有新版本并给 Release 页，安装由系统安装器确认。

**另外修掉的真实 bug**

- `TxnDao.listBetween()` 把 SQL 片段拼在第二个占位符位置 → 查询直接崩
- `BillParsers` 里 `continue` 写在 `run{}` lambda 内 → 不合法，改成先取值判空
- `Category.color` 是 ARGB `Int`，UI 层按 Compose `Color` 调 `.copy()` → 编译失败，统一走 `Category.ui`
- `StatsScreen` 从 `Column` 的 content lambda 里 `return@Column` → 运行时崩
- `BottomBar` 只有图标可点，占位 Box 是死区

---

## 主题与外观

「我的」页能整套换外观。换的是底色 + 主色 + 副色 + 点缀 + 圆角 + 明暗，
不是只把主色改一下 —— 那种改法会让"原神风"变成粉底上贴一块金黄，一眼就不对。

| 主题 | 性格 |
|---|---|
| 樱粉 | 奶油粉底 + 大圆角，默认 |
| 提瓦特 | 深蓝夜空 + 鎏金描边 + 元素色点缀，圆角收窄。只取「星图 / 金线 / 元素」这套视觉语法，**不含任何官方角色、立绘或 Logo** |
| 薄荷 | 白底薄荷绿，白天最省眼 |
| 夜桜 | 暗紫底 + 樱粉，夜里记账不刺眼 |

实现上只有一个真相源：`ThemeController` 用 Compose snapshot state 存当前主题包，
界面里 `Dz.Sakura` 等 17 个颜色全是「读当前包」的 getter。
所以换主题当场全局生效，两百多处引用一行都不用改；加一套新主题 =
在 `ThemePacks` 里多加一个 `ThemePack`。

---

## 构建

需要 **JDK 17** 和 Android SDK（`compileSdk 35`）。

```bash
# 仓库没提交 gradlew（二进制文件），先生成
gradle wrapper --gradle-version 8.9
./gradlew :app:assembleDebug
```

国内网络已在 `settings.gradle.kts` 配了阿里云 maven 镜像，
`gradle-wrapper.properties` 指向腾讯云镜像。

### 发版与版本更新闭环

```bash
# 1. 改 app/build.gradle.kts 的 versionCode + versionName
# 2. 打 tag 推上去，GitHub Actions 自动构建并建 Release
git tag v0.4.0
git push origin v0.4.0
```

`.github/workflows/release.yml` 跑 `assembleRelease`，把 `dingzhang-<tag>.apk` 挂到 Release。
App 内「检查更新」读的就是这个 Release 的 `latest`。

> Release 包用 SDK 自带的 debug keystore 签名，方便侧载。要上架请换成自己的 keystore。
> 仓库没提交 gradlew，CI 用 `gradle/actions/setup-gradle` 指定 8.9。

---

## 授权（首次使用三步）

1. **通知使用权**：设置 → 通知与状态栏 → 通知使用权 → 勾选「叮账」
2. **无障碍服务**：设置 → 无障碍 → 已下载的服务 → 叮账 → 开启
3. **电池优化白名单**（可选）：防止后台服务被杀

前两步在「我的」页有直达按钮。

---

## 导入历史账单

**微信**：我 → 服务 → 钱包 → 账单 → 常见问题 → 下载账单 → 用于个人对账 → 选时间 → 邮箱收 zip
**支付宝**：我的 → 账单 → 右上角 → 开具交易流水证明 → 用于个人对账 → 邮箱收 zip

拿到 zip 或 csv 后，「我的」页 → 账单导入 → 选文件。单文件上限 20MB。

---

## 隐私

- 不联网上传（只有「检查更新」读 GitHub 公开接口，不发任何账目）
- 无账号、无广告、无埋点
- 数据库在应用私有目录，卸载即消失
- 无障碍服务只订阅 `com.tencent.mm` 和 `com.eg.android.AlipayGphone` 两个包
- 导出的 CSV/JSON 由你选存哪儿，App 不主动往外发

---

## 代码结构

```
app/src/main/java/com/dingzhang/app/
├── core/model/        Models / Categories / Money（金额用 Long 分，BigDecimal 解析）
├── data/              DbHelper / TxnDao / Settings / Exporter / RecurringDetector
├── classify/          MerchantDict（300+ 词表）/ CategoryEngine（含 RuleLearning）
├── ingest/            Captured → IngestPipeline ← 五条采集通道
│   ├── PaymentNotificationService   (A 通道)
│   ├── PaymentAccessibilityService  (B 通道)
│   ├── BillImporter / BillCsv / BillParsers / XlsxReader  (C 通道)
│   ├── BankSmsReceiver / BankSmsParser  (D 通道)
├── update/            UpdateChecker（GitHub Releases）
└── ui/                theme / components / screens（5 个业务界面）
```

金额全程 `Long` 分存储（`amount_cents`），只在展示层格式化，避免浮点误差。

---

## 待办

- [ ] 分类预算（按分类设上限，超支在统计页标红）
- [ ] 忽略名单的手动增删入口（目前只能靠自动学习）
- [ ] 规则页手动新增说法
- [ ] 流水页搜索 / 下拉刷新
- [ ] 用真实微信个人账单校准列候选（当前按商户对账单推的）
- [ ] 用真实银行短信校准 `BankSmsParser` 的动词表（各家措辞差别不小）

---

## 致谢

- [Ivy Wallet](https://github.com/Ivy-Apps/ivy-wallet) — Compose + MVI 的教科书
- [Leaf](https://github.com/vinaynalavade/leaf) — 模块化导航分区
- Veri Fin / Fin — 数据自主、无追踪的产品态度

以及钱迹的公开文档——它诚实地写出了自动记账的真实边界，少走了很多弯路。

## License

MIT
