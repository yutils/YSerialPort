# YSerialPort 串口调试助手 —— 全量 UI 重构设计文档

> 版本目标：v3.0.0
> 平台：Android（主推工控横屏平板 / 竖屏手机自适应）
> 文档日期：2026-10-16

---

## 1. 重构目标

| 目标 | 说明 |
| --- | --- |
| 现代化 UI | 全新视觉设计系统（深色工业风为主 + 浅色主题可切换），Material 3 规范 |
| 横屏优先、竖屏自适应 | 横屏=左侧竖向导航 + 右侧多列内容；竖屏=底部导航 + 单列内容 |
| 功能全覆盖 | 串口全参数、收发配置、历史、快捷指令、自动应答、校验算法、ASCII 码表、文件发送、周期发送、串口转 TCP |
| 性能 | 接收区 2 万字符上限 + 批量刷新；断帧组包沿用库能力；大文件分包发送 |
| 可维护 | Kotlin + ViewBinding 重写，分层清晰（core / ui），去除上帝类 |

---

## 2. 现状分析

### 2.1 现有结构
```
:app           —— 演示 App（本次重构对象）
:yserialport   —— 串口库（保持 api19 兼容，逻辑不改，仅按需小幅增强）
```

现有 App 的问题：
- 4 个 Activity 平铺，无导航体系；`SendActivity` 单页堆叠全部功能，横屏下比例失衡。
- 数据绑定（dataBinding） + Java/Kotlin 混写，布局重复且无设计规范。
- 串口配置只有「串口 + 波特率」，无数据位/停止位/校验位/流控。
- 接收区无字符集选择、无上限控制、无长按复制；发送区无文件/转义/周期发送。
- 无历史记录、快捷指令、自动应答、校验算法、ASCII 码表。

### 2.2 关键环境（已验证）
- JDK：`C:\Program Files\Microsoft\jdk-21.0.9.10-hotspot`
- Android SDK：`C:\Users\yujing\AppData\Local\Android\Sdk`（platforms 至 android-37.2，build-tools 至 36.1.0，NDK 齐全）
- Gradle：9.8.1（wrapper，本地已缓存）；AGP 9.4.1（本地已缓存）
- 依赖缓存（`~/.gradle/caches`）已含：material 1.12/1.13、recyclerview 1.1/1.2.1/1.3.2、fragment-ktx 1.6/1.8.5、constraintlayout 2.2.2、cardview、core-ktx 1.16、coroutines 1.8.1、kotlin-stdlib 2.2.10、gson、yutils 2.2.5、bugly
- 代理：`http://127.0.0.1:7897`（已验证可用，构建慢时启用）
- 测试机：`emulator-5554`，1920×1080 @ 280dpi（横屏约 1097×617dp），Android 9 (API 28)，x86_64，**已 root**
- 串口：`/dev/ttyS0~3`，其中 `/dev/ttyS1` 权限 `crw-rw-rw-`，TX/RX 短接可回环自测

---

## 3. 技术选型决策

| 项 | 决策 | 理由 |
| --- | --- | --- |
| 语言 | 新增代码全 Kotlin | 简洁、空安全、与现有 Kotlin 部分一致 |
| UI 体系 | **XML + ViewBinding**（非 Compose） | 工控设备普遍低算力/老系统，View 体系启动快、体积小、兼容稳 |
| minSdk | **21**（Android 5.0） | 完整支持 Material3 主题、矢量图、ripple；工控板实际均在 5.1+；库模块仍保持 19 |
| 主题 | Material3 `DayNight` + 自定义工业深色配色 | 深色适合长时间监看与工控机柜环境；浅色应对强光车间 |
| 导航 | 自定义 `NavBarView`（自适应当向） | 一套 XML 同时满足横屏左栏 / 竖屏底栏，避免 `layout-land` 全局重复 |
| 数据持久化 | SharedPreferences + `org.json`（系统内置） | 零额外依赖，结构体（快捷指令/应答规则/历史）JSON 序列化 |
| 异步 | `Handler` + 线程池（不引入 coroutines 到 UI 层） | 串口回调已在主线程；避免额外依赖与生命周期复杂度 |
| 图标 | 矢量 drawable（自绘 path） | 无需外部图标库，体积小、可染色 |
| 背景图 | 用户可从相册选择 / 内置 AI 生成壁纸 | 满足个性化需求，配合半透明遮罩保证可读性 |

---

## 4. 信息架构

```
App
└── MainActivity（自适应外壳：横屏左导航 / 竖屏底导航）
    ├── 终端   TerminalFragment      ← 核心页：收发一体
    ├── 历史   HistoryFragment       ← 收发记录检索/复制/导出
    ├── 指令   CommandFragment       ← 快捷指令管理 + 一键发送
    ├── 应答   AutoReplyFragment     ← 自动应答规则引擎
    ├── 工具   ToolFragment          ← 校验计算器 + ASCII 码表（Tab 切换）
    ├── 设置   SettingsFragment      ← 串口/接收/发送/外观 全参数
    └── 更多   MoreFragment          ← 关于、日志、串口转 TCP、主题、背景图
```

导航项 7 个，横屏左栏竖排（图标在上文字在下，96dp 宽），竖屏底栏横排。

---

## 5. 页面布局设计（横屏 / 竖屏）

### 5.1 终端页（核心）

**横屏（`layout-land/fragment_terminal.xml`）**
```
┌─────────────────────────────────────────────────────────────────────┐
│ 状态条: [●已连接] /dev/ttyS1 · 115200 · 8N1 · RX 12.3KB TX 1.2KB    │
│                                              [打开串口] [清空] [⚙]  │
├───────────────────────────────┬─────────────────────────────────────┤
│ 接收区   [ASCII|HEX] [字符集▾] │ 发送区  [ASCII|HEX|文件]            │
│          [☑时间戳][☑自动换行] │         [☑转义][☑自动换行]         │
│                               │  ┌───────────────────────────────┐  │
│  [10:21:33.120] RX→ 48 65 6C   │  │ 输入框（多行）                 │  │
│  [10:21:33.560] RX→ 6C 6F 20   │  └───────────────────────────────┘  │
│                               │  历史▾  [周期发送 xx ms] [发送 ▶]   │
│  （可滚动，长按弹菜单：复制全部│                                     │
│     / 保存到文件 / 清空）      │  ─── 发送记录（最近 50 条）───      │
│                               │  [10:21:40] STR→ Hello             │
└───────────────────────────────┴─────────────────────────────────────┘
   接收 : 发送 宽度 ≈ 58 : 42
```

**竖屏（`layout/fragment_terminal.xml`）**
- 状态条同上；下方接收区固定高（约 45%），发送区占其余空间，双向滚动；发送记录折叠为可展开面板，默认收起。

### 5.2 工具页
- 校验计算器：横屏「左输入 / 右结果」，竖屏上下堆叠。
  - 输入类型：ASCII / HEX；输入框；字符集选择。
  - 算法：ChipGroup 分类（常用 / CRC8 / CRC16 / CRC32 / 其他）。
  - 参数：高字节在前、输入反转、输出反转、结果异或（可输入）、结果取反、位宽。
  - 输出：HEX + DEC + 二进制，一键复制 / 追加到发送框。
- ASCII 码表：`RecyclerView` 网格（横屏 8 列 / 竖屏 4 列），单元格显示 `DEC / HEX / CHAR / 名称`，点击复制或插入发送框；支持 0–127 / 128–255 切换、关键字搜索。

### 5.3 设置页
分区卡片式（横屏双列、竖屏单列）：
- 串口参数：串口设备、波特率、数据位(5-8)、停止位(1/1.5/2)、校验位(None/Odd/Even/Mark/Space)、流控(None/RTSCTS/XONXOFF)，打开/关闭按钮。
- 接收配置：显示模式、字符集、时间戳、自动换行、HEX 分隔符、最大字符数、断帧组包时间。
- 发送配置：默认类型、字符集、结束符、转义解析、文件分包延迟。
- 外观：主题（深色/浅色/跟随系统）、背景图（无/内置/相册）、界面密度。

### 5.4 列表类页面（历史/指令/应答）
- 横屏：`RecyclerView` 双列（GridLayoutManager spanCount=2）；左侧可折叠筛选栏。
- 竖屏：单列 + 顶部筛选 Chip。

---

## 6. 分层架构

```
com.yujing.chuankou
├── App.kt                          Application：初始化、崩溃采集、主题预载
├── core/
│   ├── serial/
│   │   ├── SerialConfig.kt         串口全参数数据类 + 显示格式 + 收发配置
│   │   ├── SerialManager.kt        全局单例：串口状态机、收发、监听分发、统计
│   │   └── AutoReplyEngine.kt      自动应答规则匹配与应答调度
│   ├── data/
│   │   ├── Prefs.kt                配置持久化（含 JSON 集合读写）
│   │   ├── QuickCommand.kt         快捷指令模型
│   │   ├── AutoReplyRule.kt        自动应答规则模型
│   │   └── HistoryItem.kt          收发历史模型 + 环形存储
│   └── util/
│       ├── Hex.kt                  Hex 解析/格式化/字符集探测
│       ├── Charsets.kt             字符集清单 + 流式解码器（处理多字节断包）
│       ├── Escape.kt               转义字符解析（\r \n \t \xNN \\ ...）
│       ├── Checksum.kt             通用 CRC 引擎 + SUM/XOR/LRC/BCC/Fletcher/Adler
│       └── CrcPresets.kt           CRC 参数预设表
├── ui/
│   ├── MainActivity.kt             自适应外壳
│   ├── nav/NavBarView.kt           自适应当向导航（左侧栏/底部栏）
│   ├── widget/
│   │   ├── TerminalView.kt         高性能接收/发送显示（2万字符、批量刷新、长按菜单）
│   │   ├── SegmentedControl.kt     分段选择器
│   │   ├── SettingCard.kt          通用设置卡片容器
│   │   └── EmptyView.kt            空态视图
│   ├── terminal/TerminalFragment.kt
│   ├── history/HistoryFragment.kt
│   ├── command/CommandFragment.kt
│   ├── autoreply/AutoReplyFragment.kt
│   ├── tool/ToolFragment.kt (+ ChecksumPanel / AsciiTablePanel)
│   ├── settings/SettingsFragment.kt
│   ├── more/MoreFragment.kt
│   └── tcp/TcpFragment.kt          串口转 TCP（服务端/客户端）
└── base/
    ├── BaseFragment.kt             统一视图绑定、沉浸式、主题应用
    └── BaseListAdapter.kt          通用 RecyclerView Adapter（ViewBinding + DiffUtil）
```

**单向数据流**：`SerialManager`（唯一真源）→ 监听回调 → Fragment 更新 UI；Fragment 操作 → `SerialManager` 方法 → 广播状态。页面间无需互相持有引用。

---

## 7. 关键功能设计

### 7.1 串口：全参数打开/关闭
`SerialManager.open(config)` 使用 `SerialPort.newBuilder(File(device), baud).dataBits().parity().stopBits().build()` 直接构建，绕开 `YSerialPort` 只支持 device+baud 的限制（`YSerialPort.start(sp)` 支持外部传入 SerialPort）。

> `YSerialPort.start(SerialPort sp)` 已支持外部 SerialPort，因此可由 SerialManager 自行构建完整参数串口后交给 YSerialPort 管理读取线程与组包。
> 流控（RTSCTS/XONXOFF）需 C 层 flags 支持，本期先提供配置项 + 提示（库层 `flags` 字段预留）。

状态机：`CLOSED → OPENING → OPEN → CLOSING → CLOSED`，`ERROR` 附带原因。

### 7.2 接收显示
- 显示模式：ASCII（字符集可选）/ HEX（分隔符可选）。
- **自动换行**：开 → 每个数据包独立成行并带时间戳前缀；关 → 无限拼接成连续流（不插入任何分隔）。
- 上限：`TerminalView.maxChars = 20000`，超出从头部裁剪（按行裁剪，避免半个汉字/半个字节）。
- 刷新：接收数据先入 `pending` 缓冲，`Handler` 每 60ms 合并刷新一次，避免高频重排。
- 长按：弹出菜单（复制全部 / 保存到文件 / 清空 / 暂停滚动）。
- 字符集流式解码：维护 `CharsetDecoder` 与余留字节，正确处理 UTF-8/GB18030 多字节被断包。

### 7.3 发送
- `ASCII`：按字符集编码；可选结束符（`\r\n` / `\n` / `\r` / 无）；转义解析可选。
- `HEX`：忽略空白与 `0x`/`,`/`-` 前缀，奇数长度自动补零并告警。
- `文件`：SAF 选择；可选转义；分包大小与包间延时可配；发送进度条与取消。
- `周期发送`：间隔 ms + 次数（0=无限），支持随时停止；显示已发送次数。
- 发送前/后可挂接校验算法追加（工具页一键追加到发送框）。

### 7.4 历史记录
- 记录方向（RX/TX）、时间、原始字节、显示文本、字节数。
- 环形存储，上限可配（默认 2000 条），持久化 JSON。
- 支持：筛选（全部/接收/发送）、关键字搜索、复制单条、导出全部到文件、清空、点击回填发送框。

### 7.5 快捷指令
字段：名称、类型（ASCII/HEX）、内容、字符集、备注。操作：新增/编辑/删除/排序/启用开关/一键发送/长按拖拽排序。

### 7.6 自动应答
规则字段：启用、名称、匹配类型（ASCII/HEX）、匹配模式（包含/完全相等/前缀/后缀/正则）、匹配内容（HEX 支持通配 `??`）、应答类型、应答内容、延时 ms、仅应答一次。
引擎在 `SerialManager` 收到 RX 数据时（主线程）逐条匹配，命中后按延时通过 `Handler` 发送应答，并写入历史。

### 7.7 校验算法
- 通用 CRC 引擎：`width / poly / init / refin / refout / xorout`。
- 预设：CRC8、CRC8/MAXIM-DOW、CRC8/ITU、CRC8/ROHC、CRC8/CDMA2000、CRC16/IBM(ARC)、CRC16/MODBUS、CRC16/CCITT-FALSE、CRC16/XMODEM、CRC16/X25、CRC16/USB、CRC16/MAXIM、CRC16/DNP、CRC32、CRC32/MPEG-2、CRC32/BZIP2。
- 其他：SUM8、SUM16、SUM16 取反、二进制补码(取反+1)、XOR8、XOR16、BCC、LRC、Fletcher16、Fletcher32、Adler32。
- 可调：高字节在前、输入反转、输出反转、结果异或值、结果取反、位宽截取。

### 7.8 ASCII 码表
0–127（可扩展 128–255）：DEC / HEX / OCT / BIN / 字符 / 名称（NUL、SOH…DEL）。搜索、点击复制、插入发送框。

### 7.9 串口转 TCP
- 服务端：监听端口，多客户端接入，串口 ↔ 所有客户端双向透传，显示客户端列表与日志。
- 客户端：连接远端 IP:端口，串口 ↔ 远端双向透传，断线自动重连。

---

## 8. 视觉设计系统

### 8.1 色彩（深色工业风 · 默认）
| 令牌 | 深色 | 浅色 | 用途 |
| --- | --- | --- | --- |
| background | `#0F1419` | `#F5F7FA` | 页面底色 |
| surface | `#1A2029` | `#FFFFFF` | 卡片/面板 |
| surfaceVariant | `#232B36` | `#EDF1F6` | 次级面板/输入框 |
| outline | `#2E3846` | `#D5DCE5` | 描边/分割线 |
| primary | `#2FD8A8` | `#0E9F7E` | 主色（青绿，仪器感） |
| onPrimary | `#06231B` | `#FFFFFF` | 主色上的文字 |
| secondary | `#4A9EFF` | `#2A6FDB` | 次色（蓝，接收） |
| accentRx | `#7BE495` | `#1B8A4B` | 接收数据 |
| accentTx | `#FFB454` | `#C07200` | 发送数据 |
| danger | `#FF5C5C` | `#D93025` | 错误/关闭 |
| textPrimary | `#E6EDF3` | `#14181F` | 主文字 |
| textSecondary | `#8B98A5` | `#5A6673` | 次文字 |

### 8.2 排版 / 尺寸
- 字体：正文 14sp，次要 12sp，标题 16sp/18sp，等宽（接收区）`monospace` 13sp。
- 圆角：卡片 14dp，按钮 10dp，输入框 10dp，小标签 6dp。
- 间距：4 / 8 / 12 / 16 / 24 dp 栅格。
- 触控目标 ≥ 44dp（工控戴手套操作）。

### 8.3 背景图
- 支持「无 / 内置壁纸 / 相册选择」；图片加载后覆盖 60%–80% 深色遮罩保证文字可读。
- 内置壁纸由 AI 生成（电路板 / 工业纹理，深色低对比度），放入 `res/drawable-nodpi`。

---

## 9. 实施步骤

1. 更新 Gradle（minSdk 21、viewBinding、新依赖）→ 验证空壳可编译。
2. 建立设计系统资源（colors/themes/dimens/drawables/strings）。
3. 实现 core 层（SerialConfig / SerialManager / Prefs / 模型 / 工具）。
4. 实现自适应外壳（NavBarView + MainActivity + BaseFragment）。
5. 实现 TerminalView 与终端页（横竖双布局）。
6. 实现设置页 → 历史页 → 指令页 → 应答页 → 工具页 → 更多页 → TCP 页。
7. 删除旧 Activity / 布局 / 冗余资源。
8. 编译 → 安装 → 逐页点击测试 → 截图分析 → 修复 → 回归。
9. 背景图生成与接入、最终视觉打磨、横竖屏双轮验证。
10. 更新 README 与版本号。

---

## 10. 测试方案

| 项 | 方法 |
| --- | --- |
| 编译 | `./gradlew :app:assembleDebug`（慢时启用代理 `-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7897`） |
| 安装 | `adb install -r <apk>` |
| 串口回环 | ttyS1 TX/RX 短接：发送 → 应收到相同数据 |
| 串口开关 | 反复打开/关闭，检查不崩溃、状态正确、无泄漏 |
| 参数组合 | 数据位 8/7、停止位 1/2、校验 None/Odd/Even 逐一切换并收发 |
| 显示 | ASCII/HEX 切换、字符集切换（中文 UTF-8/GB18030）、自动换行开关、2 万字符上限 |
| 发送 | ASCII/HEX/文件/转义/周期发送（10ms、100ms）连续运行观察稳定性 |
| 自动应答 | 配置规则后发送触发内容，验证自动应答与会话记录 |
| 校验 | 与标准向量比对（CRC16/MODBUS 对 "123456789" = 0x4B37，CRC32 = 0xCBF43926 等） |
| 布局 | 横屏 + 竖屏逐页截图，检查溢出/遮挡/重叠/触控尺寸 |
| 稳定性 | 后台/前台切换、旋转、低内存，观察是否崩漏 |

---

## 11. 风险与对策

| 风险 | 对策 |
| --- | --- |
| 高频接收导致 UI 卡顿 | 批量刷新（60ms 合并）+ 字符上限裁剪 + 暂停滚动 |
| 多字节字符被断包显示乱码 | 流式 `CharsetDecoder` + 余留字节缓存 |
| 流控参数 C 层不支持 | 配置项保留并给出提示，不影响其他参数 |
| AGP/Gradle 版本较新，依赖缺失 | 本地已全量缓存；必要时启用 7897 代理联网 |
| 旧 API 19 设备不兼容 | App minSdk 21；库模块保持 19，互不影响 |
| 旋转后串口状态丢失 | `SerialManager` 单例跨配置变更持有连接，Fragment 重新订阅 |
```


---

## 12. 实施结果（2026-10-16 完成）

全部 15 项任务已完成，交付物：

| 交付物 | 路径 |
| --- | --- |
| 新版 App 源码（Kotlin + ViewBinding） | `app/src/main/java/com/yujing/chuankou/` |
| 新设计系统资源 | `app/src/main/res/{values,values-night,color,drawable,layout,anim}/` |
| 内置背景壁纸 | `app/src/main/res/drawable-nodpi/bg_builtin.jpg` |
| 库增强（流控 / MARK / SPACE / 1.5 停止位） | `yserialport/src/main/cpp/YSerialPort.c`、`.../serialport/SerialPort.java` |
| 调试 APK | `app/build/outputs/apk/debug/YSerialPort_v3.0.0_30_*_debug.apk` |
| 发布 APK（已混淆，3.6MB） | `app/build/outputs/apk/release/YSerialPort_v3.0.0_30_*_release.apk` |
| 测试脚本 | `docs/tools/t.sh`、`docs/tools/ui.py` |
| 校验算法交叉验证脚本 | `docs/crc_check.py` |
| 过程截图 | `docs/screenshots/` |

### 与原计划的偏差
- `minSdk` 19 → 21：换取 Material 3 完整支持与矢量图能力；库模块保持 19。
- 主题切换采用「重建 Activity」方案（移除 `uiMode` configChanges），配合 `TerminalSnapshot` 保留接收内容。
- 分栏方向改为按容器自身宽高比自适应，而非依赖 `layout-land`。
- 额外新增：串口 ↔ TCP 透传页、内置壁纸、轻量 UI 测试脚本。


---

## 13. 缺陷修复记录（用户实测反馈）

### 13.1 接收区自动滚动
原实现在"内容首次超出一屏"后就不再跟随，且滚动发生在绘制之后会出现"最后一行被裁一半"的中间帧。
现改为：`TerminalView` 通过 `ViewTreeObserver.OnPreDrawListener` 在布局完成、绘制之前完成对齐；
用 `OnScrollChangedListener` 区分"用户手动上滑"与"程序滚动"——上滑时暂停跟随，滑回底部自动恢复；
并新增悬浮「回到底部」按钮，在用户离开底部时出现。

### 13.2 关闭自动换行时的包分隔
关闭「自动换行」后，两包数据不再首尾相连：若当前内容最后一个字符不是空白，则在包前补一个空格。
`AB CD EF` + `AB CD EF` → `AB CD EF AB CD EF`（见 `TerminalView.endsWithWhitespace()`）。

### 13.3 高速循环应答时无法关闭单条规则
三个叠加原因与对策：
1. 规则卡片开关被设为 `android:minHeight="0dp"`，实际触控高度仅 32dp，稍偏即触发卡片点击（打开编辑弹窗）→ 触控区放大为 64×44dp，并在「更多操作」菜单新增显式「停用此规则 / 启用此规则」。
2. 每次命中都 `notifyDataSetChanged()`，高频时列表反复重绑 → 命中刷新限流 400ms，且开关值未变化时不重复赋值。
3. 无延时应答走 `handler.post`，关闭后主线程队列中仍有待执行项 → 无延时规则改为直接发送，发送前复核 `rule.enabled` 与总开关。

### 13.4 高频收发的性能加固
- 统计回调限流至 200ms（原每包一次）
- 历史列表刷新限流至 150ms
- 打开/关闭串口、清零统计时立即刷新

---

## 14. 收尾全面检查与清理（2026-10-16）

### 14.1 检查手段

| 手段 | 结果 |
| --- | --- |
| `./gradlew clean assembleDebug assembleRelease` 从零构建 | 通过（179 任务，含 NDK 全新编译） |
| `./gradlew lintDebug` | 307 → 22 条告警 |
| Kotlin 编译告警 | 2 → **0** |
| 模拟器全页面 + 全功能回归 | 无崩溃 |

### 14.2 删除的无用资源（125 项）

Lint `UnusedResources` 权威判定（已核实不会误伤 `build.gradle` 中 `manifestPlaceholders`
引用的 `app_name_debug` 等边界情况）：

- **整文件 36 个**：26 个未使用的图标与背景 drawable、4 个未接线的转场动画、
  3 个颜色状态选择器、`mipmap/back.png`、`xml/file_paths.xml`
- **条目 103 条**：颜色 15、尺寸 25、字符串 37、样式 14

`res/` 文件数 **158 → 84**。

### 14.3 修复的实质问题

| 问题 | 说明 | 处理 |
| --- | --- | --- |
| `bg_bar.xml` 在 API 21/22 上分割线铺满整条 | `<item android:height>` 需 API 23，minSdk 21 会被忽略 | 改用 shape 内 `<size>` + `gravity="bottom"` |
| 导出 TSV 列错位 | 更多页导出漏写「类型」列，与表头 6 列不符 | 抽公共 `Store.buildExportText()`，两页共用 |
| `SimpleDateFormat` 线程安全隐患 | 历史列表在 UI 线程、导出在 IO 线程共用同一实例（非线程安全） | 加锁访问 + `Locale.ROOT`，导出改用局部实例 |
| `Store.exportHistory()` / `AppFiles.timestampName()` | 从未被调用的死代码 | 删除 |
| `roundIcon` 指向非圆形图标 | Lint `IconLauncherShape` | 移除 `roundIcon` 属性 |
| ASCII 码表 9/10sp 偏小 | 工控屏可读性 | 统一提升到 11sp |
| `fragment_more` 分组标题缺 `paddingEnd` | RTL 对称性 | 补齐；`baselineAligned` 移到正确的横向容器 |

### 14.4 构建配置调整

- release 开启 `shrinkResources`（原为 false），并修正 `zipAlignEnabled`
- release APK：**3.6 MB → 2.8 MB（−23%）**
- 新增 `app/lint.xml`：仅抑制「本项目有意为之」的 4 类检查（中文硬编码文案、离线锁定依赖版本、
  minSdk 21 下不适用 TextView Compat 检查），其余保持开启
- `.gitignore` 补充 `.kotlin/`、`res_screen/`、`docs/ui.xml`

### 14.5 临时文件清理

| 路径 | 说明 |
| --- | --- |
| `yserialport/.cxx/` | 43 MB 的 CMake 缓存，含 61 个历史残留目录 |
| `.kotlin/` | Kotlin 编译器空会话目录 |
| `docs/ui.xml` | uiautomator 层级 dump |
| `docs/screenshots/` | 66 张 → 21 张（16 MB → 4.2 MB），统一为 `NN-描述.png` |
| 空的 `res/anim/` 目录 | 动画文件的 4 个文件已删，目录随之移除 |

清理前已把整个 `res/` 打包备份到项目外：
`D:/Projects/AndroidStudioProjects/lib/YSerialPort3_res_backup_20261016.tar.gz`

### 14.6 保留告警（均为有意为之 / 第三方 / 架构级）

| 项 | 数量 | 说明 |
| --- | --- | --- |
| `Aligned16KB` | 3 | 第三方 `bugly-crashreport:4.1.9.3` 的 `libBugly_Native.so` 未做 16 KB 页对齐。当前工控机为 Android 9，不受影响；若日后要在 Android 15+ 16 KB 页设备上运行，需升级 Bugly 或按 ABI 剔除该 so |
| `NestedWeights` / `NotifyDataSetChanged` / `UseCompoundDrawables` / `Overdraw` | 16 | 布局与列表刷新的微优化项，当前规模下无实际影响，改动收益低于回归风险 |
| `OldTargetApi` | 1 | targetSdk 34。提升到 35/36 会引入 Android 15+ 强制边到边等行为变更，需整机回归，不在本次清理范围 |
| `UnusedAttribute` | 1 | `networkSecurityConfig` 本身即 API 24+ 属性，低版本自动忽略，属预期 |
| `InsecureBaseConfiguration` | 1 | 调试工具需要连明文调试端，已在 `network_config.xml` 中注明原因 |

### 14.7 回归验证结果（emulator-5554，Android 9 / 1920×1080 / ttyS1 短接）

- 终端 HEX 回环：发 7 B `55 AA 01 02 03 04 05` → 收 7 B · 1 包
- TCP 透传：`TCP-TRANSPARENT-TEST`（20 B）原样回环
- 历史导出 / 更多页导出：6 列与表头一致（修掉列错位）
- 校验算法：CRC-16/MODBUS(`01 03 00 00 00 0A`) = `0xCDC5`，与独立实现一致；标准校验值 `0x4B37`
- 主题切换（浅色/深色）后接收区内容完整保留（`TerminalSnapshot` 生效）
- 竖屏 1080×1920：底部导航 + 上下分栏正确
- release 包（资源压缩后）安装、回环、全页遍历均正常，0 崩溃

### 14.8 已知观察（非缺陷，供后续决策）

接收区处于 ASCII 模式时，非所选字符集可解码的字节（如 `0xA1`）会显示为替换字符 `�`。
这是标准终端行为，二进制数据应使用 HEX 模式查看。如需在 ASCII 模式下也逐字节标注
不可解码字节（显示为 `\xA1`），可后续按需增强。
