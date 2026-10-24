---
name: Bug 报告
about: 界面错位、清理不生效、服务被杀等问题
title: '[Bug] '
labels: bug
assignees: ''
---

## 现象

<!-- 实际发生了什么，期望发生什么 -->

## 复现步骤

1.
2.
3.

## 环境信息

**这几项几乎必需**——电视盒子的固件差异比手机大得多，同样是 1080p 面板，
密度报 240dpi 和 320dpi 对应的 dp 尺寸差一倍，布局问题往往只在特定密度下出现。

```bash
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
adb shell wm size
adb shell wm density
```

- 盒子型号：
- Android 版本：
- `wm size`：
- `wm density`：
- 当前桌面（launcher）：
- 内存守护版本：

## 日志

```bash
adb logcat -d -s MemClean
```

```
粘贴在这里
```

## 截图

界面问题请附截图：

```bash
adb exec-out screencap -p > shot.png
```

## 已尝试

<!-- 比如：换过清理范围、重置过白名单、重装过 -->
