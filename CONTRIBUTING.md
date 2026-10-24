# 贡献指南

感谢你愿意花时间改进这个项目。以下是一些约定，能让改动更快被合入。

## 报告问题

提 Issue 前先搜索一下有没有重复。Bug 报告请附上这些信息，否则很难复现：

```bash
adb shell getprop ro.product.model      # 盒子型号
adb shell getprop ro.build.version.release  # Android 版本
adb shell wm size                       # 物理分辨率
adb shell wm density                    # 物理密度（决定 dp 尺寸）
adb logcat -d -s MemClean               # 相关日志
```

电视盒子的固件差异比手机大得多：同样是 1080p 面板，密度报 240dpi 和 320dpi
对应的 dp 尺寸差一倍，布局问题往往只在特定密度下出现。所以 `wm size` / `wm density`
这两行几乎是必需的。

如果是界面问题，附一张 `adb exec-out screencap -p > shot.png` 的截图。

## 提交代码

1. Fork 后从 `main` 切出分支，分支名用 `fix/xxx` 或 `feat/xxx`
2. 改动尽量聚焦，一个 PR 只做一件事
3. 本地跑通 `make build`，确认能编译
4. 涉及界面改动的，跑一遍 `make verify` 确认三档密度都没问题
5. 提 PR，说明**改了什么**和**为什么**

### 提交信息

用 [Conventional Commits](https://www.conventionalcommits.org/zh-hans/)：

```
fix: 修复分栏按钮被焦点搜索跳过
feat: 支持清理记录导出
docs: 补充 8.0+ 适配说明
refactor: 抽出 Cleaner 供服务与界面共用
```

正文说清楚「为什么」比「做了什么」更有价值——代码本身能看出做了什么。

## 代码风格

- 4 空格缩进，UTF-8，LF 换行（见 `.editorconfig`）
- 类名 `PascalCase`，方法和变量 `camelCase`，常量 `UPPER_SNAKE`
- **注释写「为什么」，不写「是什么」**。比如：

  ```java
  // 好：解释了一个不显然的约束
  // 不能因为 fg 在白名单里就跳过：桌面也在白名单（避免被当后台杀掉），
  // 但"回到桌面"恰恰是最该清理的时刻。当前前台由 Cleaner 内部保护。

  // 差：复述代码
  // 如果 fg 在白名单里就返回
  ```

- 不引入第三方依赖。这个项目刻意保持零依赖、无 Gradle，构建链路要能一眼看懂。

## 界面改动

这个项目的 UI 是纯代码构建的（没有 XML 布局），有三条硬约束：

1. **不写死 dp/sp**。所有尺寸走 `res/values*/dimens.xml`，通过 `Ui.dimen()` / `Ui.applyTextSize()` 读取。
   新增尺寸要在三个档位目录里都加上。
2. **不依赖自动焦点搜索**。只要出现「整宽 + 分栏」混排，就必须用 `setNextFocusXxxId` 显式连焦点链，
   原因见 README 的「遥控器焦点必须手写」。
3. **焦点态必须可见**。遥控器操作没有鼠标悬停，用户只能靠焦点高亮判断选到哪了。

## 版本策略

遵循 [语义化版本](https://semver.org/lang/zh-CN/)：

- **MAJOR** — 不兼容的变更（比如改了 SharedPreferences 结构、需要用户重新配置）
- **MINOR** — 向下兼容的新功能
- **PATCH** — 向下兼容的问题修复

版本号只在 `AndroidManifest.xml` 里维护，`build.sh` 会自动读取。发版时同步更新：

1. `AndroidManifest.xml` 的 `versionName` / `versionCode`（`versionCode` 用 `MMmmpp` 形式，如 `10300`）
2. `CHANGELOG.md` 增加对应条目
3. `docs/releases/vX.Y.Z.md` 写发布说明

## License

提交的代码默认以 [MIT](LICENSE) 授权。
