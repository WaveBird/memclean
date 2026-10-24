# 更新日志

本项目的所有重要变更都会记录在此文件。

格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [未发布]

### 计划中

- 定时清理（每隔 N 小时兜底清理一次）
- 清理记录导出为 CSV
- 前台服务模式，适配 Android 8.0+ 的后台限制

## [1.3.0] - 2026-10-24

### 修复

- **遥控器无法选中「白名单 / 清理记录」按钮**。焦点从「关闭自动清理」按下键时整行被跳过，
  直接跳到下方的「清理范围」。原因是 `FocusFinder` 的候选打分
  `13 × 纵向距离² + 横向偏移²` 让左右分栏按钮在横向偏移上吃了平方级的亏，
  即使纵向距离近 10 倍也会输给整宽按钮。改用 `setNextFocusXxxId` 显式指定焦点链。
- 列表页右上角的「恢复默认 / 清空记录」按钮与列表之间补上显式上下焦点，
  避免在某些密度下无法回退到按钮。

### 变更

- 版本号改为语义化三段式（`1.3` → `1.3.0`），`versionCode` 同步改为 `10300`。
- `build.sh` 从 `AndroidManifest.xml` 读取版本号作为唯一来源，
  产物改为版本化的 `build/memclean-<version>.apk`。
- `build.sh` 支持 `KEYSTORE` / `KS_PASS` / `KEY_ALIAS` / `KEY_PASS` 环境变量签名正式包。
- `build.sh` 在未显式指定 SDK 时回落到 `ANDROID_SDK_ROOT` / `ANDROID_HOME`，便于 CI 直接复用。
- 新增 GitHub Actions：`ci.yml` 在 push/PR 时构建并上传产物；
  `release.yml` 在推送 `v*` 标签时校验版本一致性、构建、生成 `SHA256SUMS.txt` 并自动创建 Release。
- `Makefile` 增加 `checksum` / `release` / `tag` 目标，`make tag` 会校验工作区与标签唯一性。
- 补充 `CHANGELOG.md`、`CONTRIBUTING.md`、`Makefile`、`.editorconfig` 与 Issue/PR 模板。

### 文档

- README 重写：新增徽章、目录、截图、FAQ，补充「多分辨率自适应」与「遥控器焦点必须手写」两节。
- 新增 `docs/releases/v1.3.0.md` 发布说明。

## [1.2.0] - 2026-10-24

### 新增

- **多分辨率自适应**。布局尺寸全部迁移到 `res/values*/dimens.xml`，
  按 smallest-width 分三档：基础档（< 600dp）、`sw600dp`（基准，实机校准）、`sw900dp`。
  此前一套写死的 dp 在 960×540dp 的设备上会溢出。
- **清理记录**。每次清理记录时间、触发应用、清理个数、内存前后变化，
  新增 `HistoryActivity` 页面展示，支持一键清空，上限 200 条。
- `Cleaner.cleanAndRecord()` 统一服务触发与手动清理的统计口径。
- `tools/deploy.sh` 一键部署，`tools/verify_layouts.sh` 三档密度截图验证（退出时自动还原）。

### 变更

- 主界面「白名单」与「清理记录」并为一行左右分栏，为低分辨率电视省出一行高度。
- 字体尺寸改为 `sp` 资源 + `COMPLEX_UNIT_PX` 写入，避免 `setTextSize(float)` 二次放大。

### 修复

- 720dp 高度下主界面内容溢出屏幕底部。

## [1.1.0] - 2026-10-24

### 新增

- 白名单管理页：列出全部应用（图标 / 名称 / 包名 / 是否系统应用），
  已保护的排最前，其次第三方，最后系统应用。
- 系统关键组件锁定：`com.android.systemui`、`com.android.inputmethod.pinyin`
  强制保护且界面不可取消，防止误操作导致界面重启或输入法失灵。
- 清理范围切换：全部应用 / 仅第三方应用。
- `tools/make_icon.py` 纯 Python 图标生成器（5 档密度，4× 超采样）。
- `README.md`、`LICENSE`（MIT）、`.gitignore`。

### 变更

- 抽出 `Cleaner` 供服务与界面共用，缓存目标包列表与系统包集合。
- `Ui` 统一视觉规范：配色常量、圆角卡片、可聚焦按钮、焦点态高亮。
- 按钮在权限已授予时自动隐藏，避免占用屏幕空间。

### 修复

- **回到桌面不触发清理**。此前 `Prefs.isWhitelisted(fg)` 直接 return，
  而桌面本身在白名单里，导致最该清理的时机被跳过。
  改为白名单只表示「不被杀」，当前前台由 `Cleaner` 内部的 `pkg.equals(fg)` 单独保护。
- 每次构建签名不同导致 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`。
  keystore 原先放在会被 `rm -rf build` 清掉的目录里，已移至 `keystore/`。

## [1.0.0] - 2026-10-24

### 新增

- 首次发布。`MemCleanService` 每 2 秒轮询 `UsageStatsManager` 的前台应用变化，
  切换时调用 `ActivityManager.killBackgroundProcesses()` 回收其他应用的后台进程。
- 不依赖 root，不需要无障碍服务，不修改任何第三方应用。
- 白名单、清理开关、手动清理、实时状态（可用内存 / 累计次数 / 上次触发来源）。
- 开机自启（`BOOT_COMPLETED`）与 15 分钟 `AlarmManager` 看门狗。
- 1.5 秒节流，避免连续快速切换时重复触发。

[未发布]: https://github.com/WaveBird/memclean/compare/v1.3.0...HEAD
[1.3.0]: https://github.com/WaveBird/memclean/compare/v1.2.0...v1.3.0
[1.2.0]: https://github.com/WaveBird/memclean/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/WaveBird/memclean/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/WaveBird/memclean/releases/tag/v1.0.0
