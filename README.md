# TC4: Modern

TC4: Modern is an unofficial addon for the original Thaumcraft 4, designed to run alongside it through Modern Forge Connect Loader (MFCL) on Minecraft 1.20.1. It is not a port, fork, or replacement of Thaumcraft 4. The original mod remains a required dependency.

TC4: Modern 是原版 Thaumcraft 4 的非官方附属模组，旨在通过 Modern Forge Connect Loader（MFCL）与原版模组一同运行于 Minecraft 1.20.1。它不是 Thaumcraft 4 的移植版、分支或替代品。原版模组始终是必需依赖。

**Development goal:** Enable genuine modern addons for the original TC4 on Minecraft 1.20.1 without modifying, recompiling, or redistributing the original TC4.

**开发目标：** 在不修改、不重新编译、不重新发布原始 TC4 的前提下，为运行于 Minecraft 1.20.1 的原版 TC4 开发真正的现代附属模组。

TC Modern Lib/API 与 TC4: Modern 共用一个仓库，以两个独立 Gradle 子模块开发。
当前已接通 MFCL 的公开生命周期 API，并提供 JEI 静态配方展示。JEI 展示不改变原版研究与合成判定，具体玩法扩展尚未实现。

| 模块 | 职责 | Forge mod ID |
| --- | --- | --- |
| `tc-modern-lib` | 共享 API、旧 TC4 访问与后续兼容实现 | `tcmodernlib` |
| `tc4-modern` | 玩法、内容与现代侧集成，直接依赖同仓库 Lib | `tc4modern` |

依赖方向：`TC4: Modern → TC Modern Lib/API → MFCL public API`。
MFCL 仍是独立项目，不引用其 `internal`、`bridge` 或 `compat` 实现，不将旧 Minecraft / Forge / TC4 类加入现代编译类路径。

## Disclaimer / 免责声明

### English

**TC4: Modern is an unofficial, independent, community-developed project.**

This project aims to extend the functionality of the original Thaumcraft 4 for Minecraft 1.7.10 through Modern Forge Connect Loader (MFCL), providing modern APIs, compatibility features, and additional gameplay content.

**TC4: Modern is not an official port of Thaumcraft 4 and is not affiliated with, endorsed by, or authorized by the original Thaumcraft developers.**

#### Copyright and Third-Party Content

- Thaumcraft and its original code, textures, models, sounds, and other assets remain the property of their respective copyright holders.
- This repository is intended to contain independently developed code and original project assets.
- Original Thaumcraft 4 source code and copyrighted assets will not be redistributed without appropriate permission.
- Users must obtain the original Thaumcraft 4 mod and its required dependencies separately.
- The project's license, when provided, applies only to content that its contributors have the right to license.

#### Relationship with MFCL

This project uses Modern Forge Connect Loader (MFCL) to interact with the original Thaumcraft 4 runtime.

TC Modern Lib/API provides interfaces for modern addons, while TC4: Modern develops additional functionality based on those interfaces.

Neither module is intended to replace or redistribute the original Thaumcraft 4 mod.

#### Rights Holder Contact

If you are a rights holder and have concerns about any content in this repository, please open a GitHub issue or contact the maintainers through the repository's published contact channels so the concern can be reviewed.

### 中文

**TC4: Modern 是一个非官方、独立、由社区开发的项目。**

本项目旨在通过 Modern Forge Connect Loader（MFCL）扩展 Minecraft 1.7.10 原版 Thaumcraft 4 的功能，提供现代 API、兼容性功能和额外的玩法内容。

**TC4: Modern 并非 Thaumcraft 4 的官方移植项目，与 Thaumcraft 原开发者不存在隶属关系，也未获得其认可或授权。**

#### 版权与第三方内容

- Thaumcraft 及其原始代码、纹理、模型、音效和其他资源仍归各自的版权持有人所有。
- 本仓库旨在收录独立开发的代码和本项目原创资源。
- 未经适当授权，本项目不会重新分发 Thaumcraft 4 的原始源代码及受版权保护的资源。
- 用户必须自行另行获取原版 Thaumcraft 4 模组及其所需的依赖。
- 本项目的许可证（如有）仅适用于贡献者有权授予许可的内容。

#### 与 MFCL 的关系

本项目使用 Modern Forge Connect Loader（MFCL）与原版 Thaumcraft 4 运行时进行交互。

TC Modern Lib/API 为现代附属模组提供接口，TC4: Modern 则基于这些接口开发额外功能。

这两个模块均不旨在替代或重新分发原版 Thaumcraft 4 模组。

#### 权利人联系渠道

如果您是相关权利人，并对本仓库中的任何内容存在疑虑，请提交 GitHub issue，或通过本仓库公布的联系渠道联系维护者，以便对相关问题进行审查。

## 环境与首次构建

- JDK 17，Minecraft 1.20.1，Forge 47.4.28，Gradle Wrapper 8.8。
- Python 3.10+ 用于首次准备独立 MFCL API；已有官方 API JAR 时可跳过 Python。
- IntelliJ IDEA 打开根目录，Gradle JVM 选择 JDK 17。

Windows PowerShell：

```powershell
$env:JAVA_HOME = 'C:/path/to/jdk-17'
python tools/prepare_mfcl_api.py
.\gradlew.bat build
```

Linux/macOS：

```sh
export JAVA_HOME=/path/to/jdk-17
python3 tools/prepare_mfcl_api.py
bash gradlew build
```

准备脚本只编译 MFCL 的 11 个公开 API 源文件，校验固定提交源归档的 SHA-256，并在本地 JAR 保留许可证和来源。
固定参考提交：[`2c3a2e2`](https://github.com/gengyoubo/ModernForgeConnectLoader/tree/2c3a2e224c90925cf5f6458e825799b80e7b1deb)。`libs/` 下的 API JAR 不提交版本库，也不打进两个模组产物。
如果自行在 MFCL 仓库执行 `apiJar`，可改用：

```powershell
.\gradlew.bat build '-PmfclApiJar=C:/path/to/mfcl-0.1.0-api.jar'
```

## 联调与独立发布

完整 MFCL 模组（含旧运行时加载器与 Developer API）由 Gradle 从 MFCL Maven 仓库解析（版本见 `gradle.properties` 的 `mfcl_version`），无需手动放入 `libs/`。MFCL 模组不包含原版 TC4、Baubles 或私有 Minecraft/Forge 1.7.10 运行时；这些输入仍须单独准备。
复制 `gradle-local.properties.example` 为 `gradle-local.properties`，按需填写 `gameDir`、`legacyRuntimeDir`；该本地配置不提交 Git。刷新 Gradle 后即可执行 `:tc4-modern:runClient`。

```powershell
.\gradlew.bat :tc4-modern:runClient '-PgameDir=C:/path/to/prepared-game'
.\gradlew.bat :tc4-modern:runServer '-PgameDir=C:/path/to/prepared-server'
```

开发运行会加载两个模块的源码。
游戏目录需按 [MFCL 文档](https://github.com/gengyoubo/ModernForgeConnectLoader) 准备旧运行时，并将原始 Thaumcraft 4.2.3.5、Baubles 1.0.1.10 放入 `mods/1.7.10/`；这些文件不由本项目下载或分发。
现代侧 `mods/` 可选放入 Curios 5.9.1+1.20.1；未安装时保持旧版 Baubles。开发运行时默认包含 Curios，可用 `-PwithCurios=false` 关闭以验证 Baubles 路径。
如需分离旧运行时目录，可额外指定 `-PlegacyRuntimeDir=...`。服务端任务仅提供入口，专用服务器兼容性尚未验证。

默认 `gameDir=run` 时，原始 TC4/Baubles JAR 必须直接位于本仓库的 `run/mods/1.7.10/`，私有运行时位于 `run/mfcl/runtime/1.7.10/`（含 `runtime-manifest.json`、`minecraft-forge-1.7.10-srg.jar` 和清单中的依赖）。`legacyRuntimeDir` 只改变私有运行时位置，不改变旧模组扫描目录。已有 MFCL 联调环境时，可将其原始旧模组复制到本项目 `run/mods/1.7.10/`，并通过本地配置的 `legacyRuntimeDir` 指向已准备的运行时。

若 MFCL 报旧前置未成功加载，先检查当前游戏目录的 `logs/mfcl/legacy-1.7.10.json`：`NO_LEGACY_MODS` / `0 legacy archives` 表示旧模组扫描目录为空，并非未安装 MFCL。存在旧模组但未启动时，再查看该报告的 `diagnostics` 和 `logs/mfcl/runtime-1.7.10.json` 中的运行时启动错误。

正式安装时在现代 `mods/` 中放入完整 MFCL、`tc-modern-lib` 和 `tc4-modern` 模组 JAR；Curios 为可选，安装后优先于旧版 Baubles。
`implementation project(':tc-modern-lib')` 仅建立工程依赖，不将 Lib 合并进 TC4 JAR。

### 前置 / Prerequisites

- 现代侧：Minecraft 1.20.1、Forge 47.4.28、完整 MFCL 0.1.4+（低于 0.2.0，必需）、Curios 5.9.1+（可选，安装后优先于旧版 Baubles）。
- 旧运行时（`mods/1.7.10/`）：原版 Thaumcraft 4.2.3.5（必需）及其强制前置 Baubles 1.0.1.10。

原版 TC4 与 Baubles 属于 MFCL 旧运行时中的旧模组，**不会进入现代 Forge 的模组列表**，因此不能写入普通 `[[dependencies.tc4modern]]`。本模组通过 `mods.toml` 的 `[[mfclDependencies.tc4modern]]` 声明原版 Thaumcraft `4.2.3.5` 和 Baubles `1.0.1.10`，由 MFCL 0.1.4+ 在自身构造阶段校验实际加载结果及版本；缺失、版本不符或旧运行时启动失败均会阻止启动。附属端不再重复执行 `FMLLoadCompleteEvent` 检查。当前使用的公共 API 接口未改变，编译用 `mfcl_api_version` 保持 0.1.3。

| 产物 | 路径 |
| --- | --- |
| Lib 完整模组 | `tc-modern-lib/build/libs/tc-modern-lib-0.1.0-SNAPSHOT.jar` |
| Lib 独立 API（仅编译用） | `tc-modern-lib/build/libs/tc-modern-lib-0.1.0-SNAPSHOT-api.jar` |
| TC4 完整模组 | `tc4-modern/build/libs/tc4-modern-0.1.0-SNAPSHOT.jar` |

分别构建：`:tc-modern-lib:build`、`:tc4-modern:build`。根 `build` 构建并检查两个模块。
`publishToMavenLocal` 可发布本地开发产物；独立 API 坐标为 `github.com.gengyoubo.tcmodern:tc-modern-api:0.1.0-SNAPSHOT`，使用者还需显式添加 MFCL API 的 `compileOnly` 依赖。
目前没有配置公共 Maven 发布服务。

## JEI 兼容 / JEI integration

JEI 是可选的客户端集成，当前固定开发版本为 `15.20.0.106`（Minecraft 1.20.1 / Forge）。完整 JEI 不打入项目 JAR；未安装 JEI 时，公共模组入口不加载 JEI 插件类。

- 显示原版 TC4 的有序／无序奥术合成、坩埚炼金与静态注魔配方，支持 JEI 的配方／用途查询。
- 材料保留数量、metadata、原始 NBT 与矿物词典候选；通配 metadata 从 JEI 已知物品变体展开，无法映射的必需材料或产物会使该配方跳过。
- 页面显示基础魔力／源质消耗、研究 key，以及注魔不稳定性。基础魔力消耗未计算法杖减免。
- 注册实际旧工作台、坩埚和注魔矩阵的工作站入口，并为 TC4 物品注册 metadata／原始 NBT 子类型身份。
- 仅展示配方，不提供自动填充，不绕过研究，不注册可执行的现代 TC4 特殊配方。普通工作台配方继续由 MFCL 提供，避免重复添加。

当前仅支持 TC4 `4.2.3.5` 的标准静态配方类。动态注魔附魔、修改输入 NBT 的配方、自定义配方子类及无法无损映射的内容不推断为静态配方。
单页支持至多 16 种要素和 16 个注魔周边材料。跳过数量会写入 `TC4 JEI` 日志摘要，具体原因在 debug 日志中。
NBT 子类型目前保留完整的原始 NBT；充能等状态也可能区分为不同的 JEI 身份，尚未实现所有物品的语义化 NBT 匹配。

JEI is optional. The plugin displays original TC4 shaped/shapeless arcane crafting, crucible alchemy, and static infusion recipes through public MFCL accessors. Ingredients retain metadata, original NBT, counts, and ore alternatives. Pages show base aspect costs, research keys, and infusion instability. Recipe viewing does not unlock research or implement crafting/recipe transfer. Dynamic recipes, custom subclasses, and unrepresentable required ingredients/outputs are omitted with diagnostics. Full original NBT currently participates in subtype identity, including state such as stored charge.

默认开发客户端包含 JEI。可通过 `-PwithJei=false` 关闭开发运行时依赖；实际安装时自行放入 JEI 模组 JAR。

开发验收（须先准备完整 MFCL 和原版 TC4/Baubles 环境）：

```powershell
.\gradlew.bat :tc4-modern:runClient '-PmfclRuntimeJar=C:/path/to/mfcl-0.1.0.jar' '-PgameDir=C:/path/to/test-game' -PjeiSmokeTest
```

该选项会新建独立测试世界，检查三类配方的 JEI 注册与全部布局，保存各页面截图到测试游戏目录 `screenshots/`，生成 `logs/tc4modern/jei-smoke.json`，然后退出测试客户端。仅在专用测试游戏目录使用。
Lib 的 `TcRecipes.snapshot(runtime)` 返回不持有旧对象引用的独立配方数据及诊断，第三方现代附属可复用，不依赖 JEI 或任一版本的 Minecraft 编译类。

## Curios 兼容 / Curios integration

Curios `5.9.1`（Minecraft 1.20.1 / Forge）是与旧版 Baubles 二选一的**可选**前置，`mods.toml` 中声明为 `mandatory=false`。**安装了 Curios 时优先使用 Curios，本模块不再驱动旧版 Baubles 饰品槽；未安装 Curios 时本模块完全不介入，保持旧版 Baubles 原样**（此时不加载任何 Curios 类）。

> 原版 TC4 硬依赖 Baubles（`required-after:Baubles`），因此只要运行 TC4，旧版 Baubles 必然存在于旧运行时；“二选一”指由哪一套系统提供现代侧的饰品槽位。

- 注册与旧版 Baubles 对应的现代 Curios 槽位：`amulet`（1 格）、`ring`（2 格）、`belt`（1 格），并授予玩家。
- 通过 MFCL 公开 MLA API 读取旧版 `baubles.api.IBauble#getBaubleType`，把原版 TC4 饰品按类型归入上述槽位。
- 原版 TC4 物品在 MFCL 现代注册表中使用非透明 ID（大写 `Thaumcraft:` 会编码为 `mfcl:legacy/<hex>`），无法用静态 `curios:` 物品标签引用，因此改用 Curios 谓词校验（`tc4modern:thaumcraft_bauble`）。
- 仅桥接现代侧的装备归属：旧运行时中的 Baubles 饰品背包与佩戴效果仍由原版逻辑处理，当前不与其同步，也不复制或替换原版 Baubles 逻辑。

Curios is an optional, mutually exclusive alternative to the original Baubles slots: when Curios is installed it takes precedence and this mod no longer drives the legacy Baubles slots; when Curios is absent the mod does not intervene at all and no Curios class is loaded. Original TC4 hard-depends on Baubles, so both always coexist on the legacy runtime; the choice is only about which system provides the modern slots. Legacy TC4 item ids are opaque (`mfcl:legacy/<hex>`), so a Curios predicate is used instead of static item tags. Only modern-side placement is bridged: the legacy Baubles inventory and worn-item effects are not synchronized.

## 深板岩变体 / Deepslate variants

在 `tc4modern` 命名空间下新增原版 TC4 矿石与元素蕴魔石的现代深板岩变体方块，共 8 种：朱砂矿、风/火/水/地/秩序/混沌蕴魔石、琥珀矿。每种方块含对应物品、方块状态、模型与材质。

- 材质为程序生成的原创占位贴图，不使用或重新分发原版 TC4 材质；生成脚本见 `tools/generate_deepslate_assets.ps1`。
- 深板岩朱砂矿石普通挖掘直接掉落自身（时运不增加数量），可在普通熔炉烧炼为 1 个原版水银（`Thaumcraft:ItemResource` meta 3），耗时 200 tick、经验 1.0，与原版 TC4 朱砂烧炼一致。水银产物通过 MFCL 公开映射解析，保留原始 metadata/NBT，并使用标准熔炉配方同步。
- 蕴魔石与琥珀的采集掉落通过 MFCL 公开物品映射在运行时解析：对应元素碎片（`Thaumcraft:ItemShard` meta 0–5，受幸运影响数量）、琥珀（`Thaumcraft:ItemResource` meta 6，受幸运影响数量）。
- 精准采集保留深板岩方块本身；需要对应等级镐（朱砂、琥珀需要铁镐及以上），与原版一致。
- 不复制原版 TC4 特殊工具（元素镐 / 原初粉碎者 / 探索焦点）触发的原生碎片簇特殊采集结果。
- 暂不包含世界生成，方块仅在创造物品栏（`TC4: Modern`）提供。

Modern deepslate variants of the original TC4 ores and elemental infused stones are registered under `tc4modern` (Cinnabar, the six elemental infused stones, and Amber) with matching items, block states, models, and procedurally generated placeholder textures. Deepslate cinnabar drops itself and smelts into one original Quicksilver (200 ticks, 1.0 XP). Shard/amber drops and the smelting output use MFCL's public item mapping; Silk Touch preserves the deepslate block. World generation is not included.

资料参考：[MC 百科 TC4 朱砂矿石](https://www.mcmod.cn/item/5223.html)、[水银](https://www.mcmod.cn/item/5329.html)（朱砂矿石与水银 1:1 熔炉配方）。

在准备好旧运行时和 TC4/Baubles 的专用测试游戏目录中，执行 `:tc4-modern:runClient -PgameDir=run-cinnabar-smoke -PcinnabarSmokeTest` 可验证普通挖掘、时运、精准采集、低等级工具、实际熔炉烧炼和客户端配方同步。该选项会创建测试世界，输出 `logs/tc4modern/cinnabar-smoke.json`，然后退出客户端。

## API 起点与边界

```java
TcModernApi.whenReady(runtime -> {
    // 已确认旧运行时开放且实际加载了 Thaumcraft；此时不保证世界或玩家存在。
    // 通过 runtime.accessor(...) / runtime.mappings() 使用 MFCL 公开接口。
});
```

`TcModernApi.runtime()` 每次查询当前 MFCL，关闭后不会返回缓存的旧引用。
就绪回调沿用 MFCL 的线程契约；需要修改世界或玩家时，仍须在所属游戏线程执行。
未加载 TC4 时返回空且不触发 TC4 回调，不伪造游戏兼容成功。

MFCL 中的知识保存、动态物品绘制 SPI、扫描和扫描数据包专用路径尚未全部成为独立公开 API，因此本仓库暂不复制、替换或重复安装这些逻辑。
后续先确定稳定的公开扩展点，再逐步迁移至 Lib；具体玩法留在 `tc4-modern`。
详见上游 [Developer API 与适配边界](https://github.com/gengyoubo/ModernForgeConnectLoader/blob/2c3a2e224c90925cf5f6458e825799b80e7b1deb/docs/developer-api.md)。

## 许可证

本仓库原创代码暂保留所有权利，待仓库所有者选择开源许可证。MFCL API 属于上游 MPL-2.0；本项目不改变第三方文件的许可证。
