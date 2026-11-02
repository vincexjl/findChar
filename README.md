# 编码查字（内网独立部署版）

复刻 [姓名生僻字处理平台 - 编码查字](https://name.vurls.cn/ui/index/uchar/) 功能的内网查字服务。

- 后端：Spring Boot 2.7.6 / JDK 8 / Maven **（无数据库）**
- 前端：Vue 3 + Vite（打包后随 Spring Boot 一起发布，单 jar 部署）
- 页面样式与原站逐项复刻（#AC0227 主题、大字卡、结果表格；除编码查字外的节点导航已移除）

## 功能（与原站一致）

| 能力 | 说明 |
|---|---|
| 汉字 → 编码 | 输入法录入汉字，同时给出全部 9 种编码 |
| Unicode 码位 → 汉字 | 如 `20BB7` |
| UTF-16BE 字节 → 汉字 | 如 `D842DFB7` |
| UTF-8 字节 → 汉字 | 如 `F0A0AEB7` |
| GB18030 字节 → 汉字 | 2/4 字节均可，如 `D2BB`、`81398730` |
| GBK 字节 → 汉字 | 如 `E3C6` |
| EBCDIC(IBM-1388) 字节 → 汉字 | 如 `59BA` |
| 伪码 | GBK / IBM-1388 表示不了的汉字显示 `[U+XXXX]`（与原站一致） |
| 无效输入 | 所有列显示为空、大字区 `U+`（与原站一致） |
| 使用计数 | 纯文件持久化（`data/uchar-count.txt`），重启不丢 |

编码输出列：Unicode、UTF-8、GB18030、UTF-16LE/BE、UTF-32LE/BE、GBK、IBM-1388。

## 与原站对齐的关键技术点（均经原站逐点实测校准）

1. **GB18030-2022**：JDK8 的 `GB18030` 字符集是 2005 版。经与 ICU `gb18030-2022.ucm` 全量差分 +
   原站实测（PUA 区 U+E7C0–U+E864 全 165 点 + 抽样），只需 **28 条编码覆盖 + 28 条解码覆盖**
   即可与 GB18030-2022 完全一致（主要是 PUA 双码字迁移、`U+E7C7`/`U+1E3F` 换码、竖排符号区）。
   覆盖表见 `backend/src/main/resources/tables/gb18030-2022-overrides.tsv`。
2. **IBM-1388**：JDK8 没有该字符集。内置 ICU 的 `ibm-1388_P100-2024.ucm`
   （32,967 条映射）在内存建表正/反查，与原站逐点一致（`一`→`59 BA`、`闫`→`62 66`、
   `㐀`→`CF 41`、`一`的欧元等 SBCS/DBCS 均验证）。
3. **GBK**：`0x80 ↔ U+20AC(€)` 特例与原站一致；JDK8 `GBK` 其余行为与原站一致。
4. 补充平面（扩展 B~F 等）走 GB18030 四字节算法映射，JDK8 与原站天然一致，无需查表。
5. UTF-16 增补平面字符按**代理对**输出（`42 D8 B7 DF`），与原站一致。

所有结论都来自对原站真实往返探测，不是猜测。回归用例见 `tools/api-e2e-test.mjs`（41 条）。

## 目录结构

```
findChar/
├── build.bat / build.ps1        # 一键构建
├── LICENSE                      # MIT
├── backend/                     # Spring Boot 2.7.6 (JDK8)
│   ├── pom.xml
│   └── src/main/
│       ├── java/cn/findchar/
│       │   ├── UcharApplication.java
│       │   ├── service/
│       │   │   ├── CharEncodingService.java   # 核心：查字/编码矩阵
│       │   │   └── CodeTables.java            # IBM-1388 表 + GB18030-2022 覆盖表加载
│       │   └── web/
│       │       ├── UcharController.java       # /api/uchar/query /count /health
│       │       └── VisitCounter.java          # 纯文件计数
│       └── resources/
│           ├── tables/                        # .ucm 映射表 + 覆盖表（纯文本资源，非数据库）
│           └── static/                        # 前端构建产物（构建时自动拷入）
├── frontend/                    # Vue 3 + Vite
│   └── src/
│       ├── App.vue              # 页面（样式复刻原站）
│       ├── api.js
│       └── assets/custom.css    # 原站自定义样式复刻
└── tools/api-e2e-test.mjs       # API 回归脚本（node 运行）
```

## 构建与部署（内网）

```bat
:: 需要：JDK8、Maven、Node.js（仅在构建机需要）
build.bat
:: 产物：backend\target\findchar-uchar.jar
```

部署到内网机器（目标机只需 JDK8，无需 Node/数据库）：

```bat
cd 部署目录
mkdir data
java -jar findchar-uchar.jar
```

- 服务端口 `10030`（如需改动 `application.yml` 后重新 `mvn package`）
- 直接访问 `http://<host>:10030/` 即为查字页面；API 前缀 `/api/uchar/*`

### 日志（log4j2 + slf4j）

- 实现：`spring-boot-starter-log4j2` + `src/main/resources/log4j2.xml`，代码中统一用 slf4j API
- 位置：**jar 包同级目录**的 `appLogs/uchar.log`（启动时由 `UcharApplication#initLogDir`
  定位，IDE 直接运行则退回为工作目录下的 `appLogs`）
- 滚动策略：**按天滚动 + 单文件超过 10MB 滚动**，滚动产物自动压缩为
  `uchar-yyyy-MM-dd-N.log.gz`，最多保留 60 个滚动文件
- 记录内容：启动信息（表加载规模/计数）、每次查询审计（输入、方式、结果各列）、
  表解析告警、计数文件读写告警；Spring 框架日志同步写入
- 计数文件 `data/uchar-count.txt`（相对运行目录）；删除即从 0 开始
- 建议用 Windows 服务 / NSSM / systemd / `javaw -jar` 等方式后台常驻

## API

```
POST /api/uchar/query
{ "input": "20BB7", "searchType": "Unicode" }
→ { found, ch, codePoint, unicodeHex, utf8, gb18030, utf16le, utf16be, utf32le, utf32be, gbk, ibm1388 }

searchType ∈ char | Unicode | utf_16_be | UTF-8 | GB18030 | GBK | IBM1388

GET  /api/uchar/count   使用计数
GET  /api/uchar/health  健康检查（含表加载规模）
```

## 生僻字显示字体（可选）

内网终端通常没有扩展区字库，页面默认回退到系统字体栈
（`Microsoft YaHei / SimSun / SimSun-ExtB / MingLiU-ExtB`）。
若希望与原站一样按单字渲染，把按码位命名的字体文件放到运行的 `fonts/` 目录：

```
fonts/u20bb7.ttf
fonts/u4e00.ttf
```

页面会自动请求 `/api/uchar/fontcss?cps=...`，存在文件的码位自动套用 `@font-face`，
不存在则回退本机字库。该机制不影响查字功能本身。

## 与原站的刻意差异

- 页脚/备案/站长信息均改为内网占位文案；除「编码查字」外的所有平台节点导航已整个移除（内网部署只保留本功能）。
- 原站「卓码云输入法」面板按钮在原页面已被注释掉，故未复刻。
- 计数为独立部署版自身计数（原站数字不可迁移）。

## 回归测试

服务启动后（默认端口 `10030`），运行：

```bat
node tools\api-e2e-test.mjs
```

41 条用例覆盖 7 种查询方向、GB18030-2022 关键差异点（`FEA0→U+9FBB`、`82359134→U+E864` 等）、
GBK `0x80↔€`、IBM-1388 四字节/代理对/伪码/无效输入等行为，全部 PASS 才视为与原站行为一致。

## 许可证

[MIT](LICENSE)
