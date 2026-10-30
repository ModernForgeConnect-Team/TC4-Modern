# 原生研究台 GUI

研究台显示由 TC4: Modern 的现代客户端代码绘制，继续使用原版菜单、库存槽、研究数据和请求包。原始研究台 GUI 只作为作用域内的数据及音效来源；正常显示不调用其 `drawScreen`，也不采集、重放整张研究台界面。

本功能只替换实现，不扩充研究台功能。行为基准为原始 Thaumcraft 4.2.3.5：

| 项目 | 保留的原版表现 |
| --- | --- |
| 要素悬浮 | 原版 `getName()`、`getLocalizedDescription()` 两行，青色标题、灰色描述；不添加当前数量、奖励数量、快捷键说明 |
| 研究者组分 | 达到原版研究条件时在悬浮框下显示两个组分图标，不改成文字配方 |
| 笔记格子 | 不添加格子或未知要素的悬浮提示；使用原版未知要素贴图 |
| 翻页 | 原来的前后按钮、列顺序、页数计算；不添加滚轮翻页 |
| 数量和奖励 | 数量仍画在图标角上；奖励使用原版粒子帧，多于一个时显示数量 |
| 操作音效 | 通过公开 MLA 契约调用原版按钮、选择、组合、写入、擦除和翻页音效方法 |
| 拖拽与笔记 | 使用原版光球、六边形、随机符文的生成和消失规律；已完成笔记不再显示未完成网格 |
| 连线 | 从已发现的固定要素开始遍历，保留青色加法混合连线、相连高亮和未连通要素的灰色显示 |
| 复制与缺墨 | 原位置的复制成本图标及缺墨提示，不增加新的提示内容 |
| 研究通知 | 读取原有通知队列及配置，保留界面中原有的通知层 |

不会重新实现组合成功判定、研究完成判定、研究者随机减免／返还、墨水与要素消耗、复制材料消耗或玩家研究持久化。这些仍由原版代码在服务端执行，MFCL 校验玩家与当前菜单后处理请求。

## 实现边界

- `ResearchTableClient` 检查 MFCL 的 `client-screens` 能力，再装入原生客户端实现。
- `ResearchTableGui` 使用现代 `GuiGraphics`、字体缓冲和顶点批次；其余原版设备界面继续使用现有桥接路径。
- `ResearchTableAtlas` 从已安装的原版及附属资源读取图标、六边形、符文和粒子帧，构建仅属于当前界面的动态图集，关闭或重建时释放。
- `ResearchTableState` 只保留普通 Java 快照。每个游戏刻读取知识和奖励数据；笔记物品未改变时复用格子布局和连接结果。
- `ResearchTableAccessors` 仅引用 MFCL 公开 API，不依赖其 `internal`、`bridge`、`client`、`content` 或 `compat` 实现类。

## 本地 MFCL 依赖

原生界面依赖本轮在 MFCL 新增的公开 `LegacyClientScreens` API。开发编译需要包含该接口的 API JAR；目前默认 Maven `0.2.10` API 不包含它。按仓库现有本地联调机制，在不提交的 `gradle-local.properties` 中设置：

```properties
mfclApiJar=C:/path/to/mfcl-native-api.jar
mfclRuntimeJar=C:/path/to/mfcl-native.jar
```

运行时不报告 `client-screens` 能力时，客户端入口保留原版桥接界面。MFCL API 不打入 TC4: Modern 的模组包。新接口的发布属于 MFCL 的独立发布流程，本次没有上传或发布 Maven 产物。

## 验收

使用备有原版 TC4 与饰品前置的独立测试游戏目录：

```powershell
.\gradlew.bat --offline --project-cache-dir build/native-research-cache `
  -I tools/isolated-build.init.gradle `
  '-PvalidationBuildDir=build/native-research-validation' `
  :tc4-modern:test --tests github.com.gengyoubo.tcmodern.tc4.client.research.ResearchTableLayoutTest `
  :tc4-modern:runClient '-PgameDir=run-native-research-smoke' `
  -PwithCurios=false -PnativeResearchSmokeTest
```

该命令创建自己的临时测试世界，使用真实菜单输入和原版请求包，检查提示文本、翻页按钮、禁用新增滚轮翻页、组合、连续点击、放置、擦除、原版随机减免／返还、墨水、复制、作用域失效、尺寸变化、关闭及存档重载，最后退出测试客户端。报告位于测试目录 `logs/tc4modern/native-research-smoke.json`。

测试模式还会短暂撤下原生视图，在同一菜单数据上使用原版桥接渲染截取 `native-research-original-hover-*.png`，然后恢复原生视图。与 `native-research-hover-*.png` 配对检查悬浮框和组分图标；这种原版对照绘制只发生在验收模式。

截图由测试代码显式绑定主渲染目标并设置 GUI 投影后生成。绘制耗时只测原生背景和要素／格子批次，不代表完整帧时间，也不把测试时的原版对照绘制算作正常界面路径。

2026-10-30 本地验收：6 项专门单元测试通过；`native-research-smoke.json` 为 `PASS`，包含提示两行内容的原版直接比对、按钮翻页与滚轮不翻页、实际要素／墨水／纸张／墨囊消耗、复制次数和存档重载。已查看原生与原版的同菜单悬浮对照截图，确认没有额外提示文字，组分使用图标。此处不声称两个渲染器在所有字体、资源包、随机动画帧上逐像素一致。
