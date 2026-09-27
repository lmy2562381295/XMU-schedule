# 厦大课表（xmu-schedule）

厦门大学教务系统课表导入 + 课前提醒的安卓应用。

- **一键导入**：内置 WebView 打开厦大统一身份认证（CAS），登录成功后自动从教务系统（jw.xmu.edu.cn，金智 jwapp 架构）拉取本学期课表
- **课前提醒**：每节课开始前（默认 10 分钟，可选 5/15/30）发送系统通知，含课程名、教室、时间
- **今日 / 周视图**：今日时间线与整周网格，支持前后翻周、点击格子手动增改课程
- **演示模式**：不登录即可载入内置演示课表，完整体验界面与提醒逻辑
- **隐私**：所有数据（课程、会话 cookie、设置）仅保存在手机本地，无任何上报

## 下载安装包

无需本地构建：到 [**Releases**](https://github.com/lmy2562381295/XMU-schedule/releases) 页面下载最新 APK 直接安装（页面内附导入演示视频）。每次推送代码后 Actions 会自动构建新版本。

## 构建步骤

1. 安装 [Android Studio](https://developer.android.com/studio)（Ladybug 或更新版本，需含 JDK 17）
2. `File → Open` 打开本目录（`xmu-schedule`），等待 Gradle Sync 完成（首次会自动下载 Gradle 8.9 与依赖）
3. 手机开启 USB 调试并连接，或启动模拟器（API 26+）
4. 点击 `Run ▶` 直接安装，或 `Build → Build Bundle(s)/APK(s) → Build APK(s)` 生成 APK（输出在 `app/build/outputs/apk/debug/`）

> 本工程未附带 gradlew 命令行脚本，命令行构建请自行安装 Gradle 8.9 或使用 Android Studio。

## 使用流程

1. **导入课表**：首次打开 → 「登录教务系统导入」→ 在 CAS 页面完成登录（支持验证码/双因素，因为是真实浏览器登录）→ 登录成功后自动拉取并解析课表
   - 学期代码一般留空即可自动获取；如果导入为空或想导其他学期，可手动填（如 `2026-2027-1`）
2. **校准学期**：到「设置」选择**学期第一周的周一日期**（必设，否则无法计算周次），核对学期总周数
3. **核对节次时间**：内置的节次表是通用占位值，请对照学校当学期作息在「设置 → 节次时间」逐节修改
4. **提醒权限**：Android 13+ 会请求通知权限；Android 12+ 若提示"精确提醒未开启"，按设置页引导打开「闹钟和提醒」特殊权限
5. 可以用「发送测试通知」验证提醒链路

提醒采用"滚动 7 天窗口 + 每日维护任务"实现：只为未来 7 天设精确闹钟并每天滚动补排，避免整学期数百个闹钟超系统上限；重启手机后会自动重排。

## 导入结果不对？字段适配指引

金智教务各校返回字段命名略有差异。本工程的解析器（`JwScheduleParser`）对课程名/教师/星期/节次/周次/校区/教室各自尝试了多个候选键名（如 `KCM`/`KCZWMC`、`XQJ`/`XQ`、`JC`/`JCDM`、`ZC`/`QSZC` 等），并做了：

- 节次 `0102`（补零）→ `1-2节` 的兼容
- 周次 `1-16周(单)` / `1,3,5-8周` 等写法的解析
- 解析失败的行**不会**丢弃，而是记录下来

排查方法：每次导入的**原始返回 JSON** 都保存在应用私有目录 `files/dumps/schedule_raw_<学期>.json`（Android Studio → Device Explorer → `/data/data/com.zako.xmuschedule/files/dumps/`）。导入条数不对时，把这个 JSON 发给开发者（或自行比对字段名补充进解析器的候选键列表）即可完成适配。

## 工程结构

```
app/src/main/java/com/zako/xmuschedule/
├── MainActivity.kt / XmuScheduleApp.kt
├── data/
│   ├── db/          Room：课程、学期配置、节次时间
│   ├── remote/      JwClient（HTTP + cookie）、JwScheduleParser（容错解析）
│   └── ScheduleRepository.kt   导入编排/演示数据/手动增改
├── reminder/        通知渠道、闹钟排程（滚动窗口）、触发/开机接收器、维护 Worker
├── ui/              Compose 界面：今日 / 周课表 / 导入(WebView) / 设置 / 课程编辑
└── util/            周次/节次文本解析、时间工具
```

## 已知限制

- 未做：桌面小组件、成绩/考试查询、账号密码自动登录（WebView 登录是对验证码最稳的方案）
- 会话过期后（通常数天~数周），导入时会自动要求重新登录
- 教务系统接口无公开文档，若学校调整字段需按上文指引适配
