# MFCL 0.2.10 临时修复补丁

TC4-Modern 依赖 CurseForge 上的 MFCL 0.2.10（`curse.maven:modernforgeconnectloader-1755269:9349233`）。
这里放的是把 MFCL 1.0.8 之前的 bug 修复移植到 0.2.10 后、**有改动的 MFCL 源文件**，在加载时打到未修改的 0.2.10 上。
CurseForge 上有带这些修复的 MFCL 后，删掉本补丁即可（见文末）。

这些文件来自 ModernForgeConnectLoader（同为 MPL-2.0），保留原包名与原许可。

## 来源

基线：MFCL 提交 `fb8cf0e`（发布 0.2.10）。CurseForge 文件 9349233 中 506 个未改动的类与该提交的构建逐字节一致。

在其上 cherry-pick 的提交（只取 `src/main`、`src/legacy1710`、`src/api` 的非探针代码）：

| 提交 | 内容 | 说明 |
| --- | --- | --- |
| `0a35b00` | 发包、生命周期 | |
| `1bbb8f0` | 坩埚 | |
| `a04ba93` | 节点、负高度 | 含 legacy 侧 `WorldHeightTransformer` |
| `b74ebe1` | 桥接 | |
| `f07548b` | 物品建模桥接 | |
| `2074230` | 草透明、液体、渲染、法杖 | 去掉了原生界面和物品拆分相关部分 |
| `922e241` | 反射、魔导手册渲染、节点扫描 | 去掉了启动优化（资源包缓存、计时） |
| `9e30fcc` | 原版树叶 metadata、统计 ID 范围 | 原生方块部分不适用 |
| `d1499fe` `f2c7f11` | #5 #6 | |
| `b433db8` | 物品桥接 | |
| `46b3a22` | 方块 metadata | |
| `448c02c` | 攀爬内存溢出 | |
| `ddc401f` | 魔法树叶群系染色 | |
| `dff194e` | 1.0.4 | |
| `205127a` | materialize 变种索引 | 性能 |
| `555b0f0` | 楼梯模型 | |
| `76c15ed` | 箱子型转换 | |
| `e09663d` | 研究笔记要素（元素拓展） | TC4-Modern 的 12 个自定义研究依赖它 |

未移植（新功能，0.2.10 下 tc4-modern 走降级路径，见 `MfclCompat`）：
`7d91baf` GUI 库（原生界面）、`13dde57` 方块 API、`0089536` 物品拆分、`829ad69` 拆分物品注册别名。
`9078311`（锁死）修的是物品拆分迁移代码，0.2.10 没有这段代码，不适用。

TC4-Modern 额外修复（1.0.8 也有这两个问题，MFCL 上游尚未修复）：

- `LegacyContainers.preparePlayer`：旧客户端玩家是用 `Unsafe` 分配的，没有碰撞箱。TC4 法杖模型会经
  `Entity.getBrightnessForRender` 读取碰撞箱，导致 MFCL 捕获法杖物品渲染时 NPE（每次启动 16 条）。现在会补上碰撞箱。
- `LegacyBlockRendering`：探测放置状态时，物品目录里没有的 metadata 如果原方块自己都画不出来，就不再记为诊断错误
  （TC4 战利品箱/罐只有 metadata 0–2，每次启动 26 条越界），只在 debug 日志汇总。

为在 0.2.10 上编译另做的调整：`ModernContentBridge` 用 `MirroredBlock.entry` 代替 1.0.x 才有的 `reverseBlocks`；
`ClientContentBridge` 去掉了 0.2.10 中不存在的探针。

## 构建与运行

- `src/mfclPatch`：改动/新增的 modern 侧类，针对反混淆后的 0.2.10 编译。
- `src/mfclPatchLegacy`：改动/新增的旧运行时（1.7.10）类，并入 0.2.10 自带的 `mfcl-legacy-runtime.jar`。
- `src/mfclPatchTool`：构建时工具 `MfclPatchTool`。0.2.10 已有的类存为改名副本（`mfclpatch/replaced`，随本模组一起重混淆），
  新增的类改包名到 `mfclpatch/added`。所有补丁类放宽为 public，并去掉 nest/sealed 属性。
  如果未打补丁的 0.2.10 类还会用到补丁删掉的成员，或者补丁按类名反射引用了被改名的类，构建直接失败。
- 运行时：`MfclPatchPlugin`（Mixin 配置插件）把副本成员移进原类；`EmbeddedLegacyRuntimeMixin` 让旧运行时加载打过补丁的 legacy JAR。
  仅当 MFCL 恰为未打补丁的 0.2.10 时启用，否则日志写明原因并跳过。

## 更新或删除

更新：在 MFCL 仓库从 `fb8cf0e` 建分支，cherry-pick 需要的修复，再把相对 `fb8cf0e` 改动/新增的文件复制到这里。

删除：删掉 `src/mfclPatch*` 三个目录、`github.com.gengyoubo.tcmodern.tc4.mfclpatch` 包、
`tc4modern.mfclpatch.mixins.json`，以及 `tc4-modern/build.gradle` 中的 MFCL 补丁段落和 `-mixin.config` 参数。
