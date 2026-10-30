# TC4: Modern

TC4: Modern is an unofficial addon for the original Thaumcraft 4, designed to run alongside it through Modern Forge Connect Loader (MFCL) on Minecraft 1.20.1. It is not a port, fork, or replacement of Thaumcraft 4. The original mod remains a required dependency.

TC4: Modern 是原版 Thaumcraft 4 的非官方附属模组，旨在通过 Modern Forge Connect Loader（MFCL）与原版模组一同运行于 Minecraft 1.20.1。它不是 Thaumcraft 4 的移植版、分支或替代品。原版模组始终是必需依赖。

**Development goal:** Enable genuine modern addons for the original TC4 on Minecraft 1.20.1 without modifying, recompiling, or redistributing the original TC4.

**开发目标：** 在不修改、不重新编译、不重新发布原始 TC4 的前提下，为运行于 Minecraft 1.20.1 的原版 TC4 开发真正的现代附属模组。

TC Modern Lib/API 与 TC4: Modern 共用一个仓库，以两个独立 Gradle 子模块开发。
当前已接通 MFCL 的公开生命周期 API，并提供 JEI 静态配方展示、元素练习册等扩展内容。JEI 展示不改变原版研究与合成判定。

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
游戏目录需按 [MFCL 文档](https://github.com/gengyoubo/ModernForgeConnectLoader) 准备旧运行时，并将原始 Thaumcraft 4.2.3.5 放入 `mods/1.7.10/`；原版文件不由本项目下载或分发。
饰品前置二选一：现代侧 `mods/` 安装 Curios 5.9.1+1.20.1，或旧运行时安装 Baubles 1.0.1.10。**只装 Curios 时无需安装旧版 Baubles**。两者同时安装时使用 Curios。开发运行时默认包含 Curios，可用 `-PwithCurios=false` 关闭以验证 Baubles 路径。
如需分离旧运行时目录，可额外指定 `-PlegacyRuntimeDir=...`。服务端任务仅提供入口，专用服务器兼容性尚未验证。

默认 `gameDir=run` 时，原始 TC4 JAR 放在本仓库的 `run/mods/1.7.10/`；未使用 Curios 时还须放入原版 Baubles。私有运行时位于 `run/mfcl/runtime/1.7.10/`（含 `runtime-manifest.json`、`minecraft-forge-1.7.10-srg.jar` 和清单中的依赖）。`legacyRuntimeDir` 只改变私有运行时位置，不改变旧模组扫描目录。已有 MFCL 联调环境时，可将其原始旧模组复制到本项目 `run/mods/1.7.10/`，并通过本地配置的 `legacyRuntimeDir` 指向已准备的运行时。

若 MFCL 报旧前置未成功加载，先检查当前游戏目录的 `logs/mfcl/legacy-1.7.10.json`：`NO_LEGACY_MODS` / `0 legacy archives` 表示旧模组扫描目录为空，并非未安装 MFCL。存在旧模组但未启动时，再查看该报告的 `diagnostics` 和 `logs/mfcl/runtime-1.7.10.json` 中的运行时启动错误。

正式安装时在现代 `mods/` 中放入完整 MFCL、`tc-modern-lib` 和 `tc4-modern` 模组 JAR；Curios 为可选，安装后优先于旧版 Baubles。
`implementation project(':tc-modern-lib')` 仅建立工程依赖，不将 Lib 合并进 TC4 JAR。

### 前置 / Prerequisites

- 现代侧：Minecraft 1.20.1、Forge 47.4.28、完整 MFCL 0.2.9+（低于 0.3.0，必需；使用开发者 API v2）、Curios 5.9.1+（可选，安装后优先于旧版 Baubles）。
- 旧运行时（`mods/1.7.10/`）：原版 Thaumcraft 4.2.3.5（必需）；Baubles 1.0.1.10（未安装 Curios 时必需）。

原版 TC4 与 Baubles API 提供方属于 MFCL 旧运行时，**不会进入现代 Forge 的模组列表**，因此不能写入普通 `[[dependencies.tc4modern]]`。`mods.toml` 的 `[[mfclDependencies.tc4modern]]` 校验 Thaumcraft `4.2.3.5` 及 Baubles ABI `1.0.1.10`；后者可由原版 Baubles 或本项目独立实现的内嵌兼容层提供。兼容层只在安装 Curios 且不存在原版 Baubles 时启用。缺少两种饰品前置、版本不符或旧运行时启动失败会阻止启动。要素与配方快照通过公开 API `LegacyValues` 读取旧版集合。

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
- 页面仿照原版神秘学典籍（`GuiResearchRecipe`）的配方页：羊皮纸背景、奥术网格、坩埚、注魔符文与法杖图标均在运行时读取已安装 TC4 的 GUI 贴图（不重新分发），按原版坐标缩放为 9/16；研究条件显示研究名称，注魔显示原版风险等级。缺少贴图时退回为普通格子。
- 要素是 JEI 成分：配方中、成分列表中的要素均可按 R 查看其组成（「要素组合」），按 U 查看消耗它的配方及以它为组分的复合要素。
- 「炼金炉」页列出原版炼金炉接受的每个物品及其产出的源质（TC4 物品要素加奖励要素，与探测仪一致），可从物品或要素双向查询。
- 「物品丢入流体」页展示净化浴盐 ×1 + 一格静止水源 → 净化之水 ×1，提示等待约 10 秒；支持浴盐、水／水桶的用途与净化之水的配方查询。整组浴盐丢入也只转换一格水源并消耗整组。
- 材料保留数量、metadata、原始 NBT 与矿物词典候选；通配 metadata 从 JEI 已知物品变体展开，无法映射的必需材料或产物会使该配方跳过。
- 页面显示基础魔力／源质消耗、研究 key，以及注魔不稳定性。基础魔力消耗未计算法杖减免。
- 注册实际旧工作台、坩埚和注魔矩阵的工作站入口，并为 TC4 物品注册 metadata／原始 NBT 子类型身份。
- 六种多方块结构提供搭建材料、魔力消耗、研究要求和成型前后预览；对成型结构的代表方块按 R、对材料按 U 可查询搭建方式。结果槽只是结构入口，不会将多方块作为物品合成出来。缸中节点忽略节点自身的要素和 ID，以便携带不同节点数据的物品也能查询同一搭建配方。
- 仅展示配方，不提供自动填充，不绕过研究，不注册可执行的现代 TC4 特殊配方。普通工作台配方继续由 MFCL 提供，避免重复添加。

当前仅支持 TC4 `4.2.3.5` 的标准静态配方类。动态注魔附魔、修改输入 NBT 的配方、自定义配方子类及无法无损映射的内容不推断为静态配方。
单页支持至多 16 种要素和 16 个注魔周边材料。跳过数量会写入 `TC4 JEI` 日志摘要，具体原因在 debug 日志中。
除缸中节点仅按结构类型查询外，NBT 子类型目前保留完整的原始 NBT；充能等状态也可能区分为不同的 JEI 身份，尚未实现所有物品的语义化 NBT 匹配。

JEI is optional. The plugin displays original TC4 shaped/shapeless arcane crafting, crucible alchemy, and static infusion recipes through public MFCL accessors. Ingredients retain metadata, original NBT, counts, and ore alternatives. Pages show base aspect costs, research keys, and infusion instability. Recipe viewing does not unlock research or implement crafting/recipe transfer. Dynamic recipes, custom subclasses, and unrepresentable required ingredients/outputs are omitted with diagnostics. Full original NBT currently participates in subtype identity, including state such as stored charge.

默认开发客户端包含 JEI。可通过 `-PwithJei=false` 关闭开发运行时依赖；实际安装时自行放入 JEI 模组 JAR。

开发验收（须先准备完整 MFCL 和原版 TC4/Baubles 环境）：

```powershell
.\gradlew.bat :tc4-modern:runClient '-PmfclRuntimeJar=C:/path/to/mfcl-0.1.0.jar' '-PgameDir=C:/path/to/test-game' -PjeiSmokeTest
```

该选项会新建独立测试世界，检查三类配方、浴盐流体转换及六种多方块结构的 JEI 注册、布局和查询（包括携带节点 NBT 的缸中节点），保存各页面截图到测试游戏目录 `screenshots/`，生成 `logs/tc4modern/jei-smoke.json`，然后退出测试客户端。仅在专用测试游戏目录使用。

多方块功能验收（测试目录须准备原始 TC4 与饰品前置）：

```powershell
.\gradlew.bat :tc4-modern:runClient '-PgameDir=build/multiblock-smoke-game' -PmultiblockSmokeTest -PjeiSmokeTest
```

`-PmultiblockSmokeTest` 在新建生存测试世界中完成研究，通过玩家右键成型六种结构，逐项检查研究台库存/界面、完整静态注魔、炼狱熔炉烧炼出料、荒古炼金炉消耗内部魔力并产出源质、炼金塔库存/界面/原始坩埚配方出料及缸中节点要素保留。机器更新使用方块的现代 ticker，经过 MFCL 的掉落、同步和持久化流程。每种结构单独记录结果，一种失败仍会测试其余结构，报告为 `logs/tc4modern/multiblock-smoke.json`。荒古炼金炉的内部魔力和炼金塔的内部源质由测试提供，不涵盖充能节点网络与外部管道供给。两个选项一起使用时先完成多方块验收，再新建 JEI 验收世界。

多方块页面可切换“成型后”，用 MFCL 0.2.9-dev3 的预览场景（`LegacyFeatures.SCENES`）显示原版成型代码生成的结构；成型前视图中的原版方块（符文矩阵、台桌等由 TESR 绘制的方块）也通过预览场景绘制。看向只存在于成型结构中的方块时，Jade 图标显示整个成型结构。`-PformedPreviewSmokeTest` 会在测试世界中绘制全部成型结构和几个 TESR 单方块，截图为 `screenshots/tc4modern-formed-previews.png`，报告为 `logs/tc4modern/formed-preview-smoke.json`。

Lib 的 `TcRecipes.snapshot(runtime)` 返回不持有旧对象引用的独立配方数据及诊断，第三方现代附属可复用，不依赖 JEI 或任一版本的 Minecraft 编译类。

## Curios 兼容 / Curios integration

Curios `5.9.1`（Minecraft 1.20.1 / Forge）与旧版 Baubles 是二选一的**可选**前置，`mods.toml` 中 Curios 声明为 `mandatory=false`。**安装 Curios 时优先使用 Curios，旧版 Baubles JAR 可以不安装；未安装 Curios 时保持原版 Baubles 路径**（不加载 Curios 集成类）。

TC4 原始二进制仍引用旧 Baubles API。本项目独立实现了一层最小 ABI 兼容：通过 MFCL 公开的启动前 SPI，在旧模组扫描前把内嵌的 `tc4modern-baubles-bridge-1.7.10.jar` 写入游戏目录 `mods/1.7.10/`。它提供旧接口与四格装备视图，名称显示为「TC4 Modern: Baubles API compatibility」，版本 `1.0.1.10` 表示所兼容的 ABI；不包含原版 Baubles 代码、GUI 或素材。重新安装原版 Baubles 或移除 Curios 后，下次启动自动移除本项目生成的带标记兼容层；不会修改原版 JAR。Curios 和旧 Baubles 都不安装时无法运行 TC4。

- 注册与旧版 Baubles 对应的现代 Curios 槽位：`amulet`（1 格）、`ring`（2 格）、`belt`（1 格），并授予玩家。
- 扫描所有旧模组中实现 `baubles.api.IBauble` 的物品，包含 TC4 附属的腰带、戒指和护身符，不限制注册命名空间或实现类所在包。通过 MFCL 公开 MLA API，使用实际物品堆的 metadata/NBT 调用 `getBaubleType`，避免把同一物品 ID 的不同变种都判为默认槽位。
- 原版 TC4 物品在 MFCL 现代注册表中使用非透明 ID（大写 `Thaumcraft:` 会编码为 `mfcl:legacy/<hex>`），无法用静态 `curios:` 物品标签引用，因此改用 Curios 谓词校验（`tc4modern:thaumcraft_bauble`）。
- 揭示护目镜（`ItemGoggles`）可放入 Curios 自带的 `head` 槽：通过 MFCL 0.2.9 的装备来源 API（`LegacyEquipment`）在原版头盔槽为空时作为“佩戴的头盔”交给旧运行时，因此看节点、要素悬浮、神秘减耗等原版判断都会生效。原版头盔槽有头盔时以头盔为准。神秘头盔、虚空兜帽等真正的头盔不进入 Curios 槽。
- 护身符、两格戒指、腰带通过 `LegacyEquipment.Source` 映射到旧 `BaublesApi.getBaubles`，TC4 与附属的装备判定可读取实际装备。旧逻辑修改/移除饰品会写回 Curios，保留旧物品 metadata/NBT 和现代侧标签。
- Curios 负责存档、同步和死亡掉落；旧层只提供装备视图。佩戴 tick 通过 MFCL 的事件转发执行一次，穿脱回调与可穿脱检查调用原物品的 `IBauble`；饰品不能放入装饰槽，额外扩容不增加 TC4 可用格数。

使用专用测试目录运行 `:tc4-modern:runClient -PgameDir=build/curios-smoke-game -PcuriosSmokeTest`，可验证四格映射、NBT、悬浮腰带每 tick 只生效一次、旧库存修改写回与 Curios 序列化。测试会创建世界，写入 `logs/tc4modern/curios-smoke.json` 后退出。只放原版 TC4 可验证无旧 Baubles 路径；加入原版 Baubles 可验证共存路径；再加 `-PwithCurios=false` 可验证回退启动。

加 `-PcuriosSmokeFixture` 会构建并放入一个仅用于测试的旧附属，验证同一物品随 metadata/NBT 分别进入 ring、belt、amulet；该测试 JAR 不打入发布产物。测试目录另行放入本地 Tainted Magic 8.2.0 时，还会检查 Voidwalker Sash、Lumos Ring 的实际 Curios 槽位校验与旧 Baubles API 装备读取。

Curios takes precedence when installed; original Baubles is then optional. An independently implemented embedded ABI provider satisfies TC4's old binary API when original Baubles is absent. Every legacy IBauble is supported across mod namespaces, including addons; slot type is queried from the actual stack to retain metadata/NBT-dependent variants. MFCL equipment sources mirror Curios amulet, two rings and belt into the old inventory, write back original mutations and forward worn effects exactly once. Curios owns persistence and drops. Without Curios, original Baubles is required and its path is left unchanged. The generated provider is removed on the next launch when original Baubles is installed or Curios is removed. Original TC4 and Baubles binaries/assets are not redistributed. Goggles in the Curios head slot retain the existing helmet fallback integration.

## 深板岩变体 / Deepslate variants

在 `tc4modern` 命名空间下新增原版 TC4 矿石与元素蕴魔石的现代深板岩变体方块，共 8 种：朱砂矿、风/火/水/地/秩序/混沌蕴魔石、琥珀矿。每种方块含对应物品、方块状态、模型与材质。

- 模型以原版深板岩材质为底，叠加矿石层。矿石层不随模组分发，而是在启动时从已安装的 TC4 中提取（朱砂、琥珀只保留 TC4 在 1.7.10 石头上绘制的像素；蕴魔石使用 TC4 原版动画矿脉层，按元素颜色着色并发光），因此不分发 TC4 材质。方块数据生成脚本见 `tools/generate_deepslate_assets.ps1`。
- 深板岩朱砂矿石普通挖掘直接掉落自身（时运不增加数量），可在普通熔炉烧炼为 1 个原版水银（`Thaumcraft:ItemResource` meta 3），耗时 200 tick、经验 1.0，与原版 TC4 朱砂烧炼一致。水银产物通过 MFCL 公开映射解析，保留原始 metadata/NBT，并使用标准熔炉配方同步。
- 蕴魔石与琥珀的采集掉落通过 MFCL 公开物品映射在运行时解析：对应元素碎片（`Thaumcraft:ItemShard` meta 0–5，受幸运影响数量）、琥珀（`Thaumcraft:ItemResource` meta 6，受幸运影响数量）。
- 精准采集保留深板岩方块本身；需要对应等级镐（朱砂、琥珀需要铁镐及以上），与原版一致。
- 不复制原版 TC4 特殊工具（元素镐 / 原初粉碎者 / 探索焦点）触发的原生碎片簇特殊采集结果。
- 世界生成仿照原版 TC4 `ThaumcraftWorldGenerator.generateOres`，每区块运行一次，只替换深板岩（`#minecraft:deepslate_ore_replaceables`），仅限主世界；原版石头矿石不在此生成：
  - 朱砂：每区块 18 次单方块尝试，高度为世界底部 + `random(世界高度/5)`（主世界即 -64～12）。
  - 琥珀：每区块 20 次单方块尝试，高度为地表 - `random(25)`；深板岩很少接近地表，因此深板岩琥珀很稀有。
  - 蕴魔石：每区块 8 个大小为 6 的矿脉，高度为世界底部 + `random(max(5, 地表 - 5))`；元素随机，1/3 概率按群系标签取元素（原版 BiomeDictionary 类型对应到 1.20 群系标签）。
  - 遵循 Thaumcraft.cfg 的 `genCinnibar` / `genAmber` / `genInfusedStone` 开关；原版按 1.7.10 群系 ID 的群系黑名单不适用。

Modern deepslate variants of the original TC4 ores and elemental infused stones are registered under `tc4modern` (Cinnabar, the six elemental infused stones, and Amber) with matching items, block states and models. Models layer an ore overlay on vanilla deepslate; the overlays are derived at startup from the installed Thaumcraft (cinnabar/amber keep only the pixels TC4 painted over 1.7.10 stone; infused stones use TC4's animated vein layer, tinted and glowing), so no TC4 textures are redistributed. Deepslate cinnabar drops itself and smelts into one original Quicksilver (200 ticks, 1.0 XP). Shard/amber drops and the smelting output use MFCL's public item mapping; Silk Touch preserves the deepslate block. World generation follows the original `ThaumcraftWorldGenerator.generateOres` once per overworld chunk but only replaces deepslate: cinnabar 18 single-block attempts at bottom + `random(height/5)`, amber 20 attempts at surface - `random(25)` (rare, since deepslate seldom reaches the surface), infused stone 8 size-6 veins at bottom + `random(max(5, surface - 5))` with the original 1/3 biome-aspect element choice mapped to 1.20 biome tags. The Thaumcraft.cfg toggles apply; original stone ores are not generated by this mod.

资料参考：[MC 百科 TC4 朱砂矿石](https://www.mcmod.cn/item/5223.html)、[水银](https://www.mcmod.cn/item/5329.html)（朱砂矿石与水银 1:1 熔炉配方）。

在准备好旧运行时和 TC4/Baubles 的专用测试游戏目录中，执行 `:tc4-modern:runClient -PgameDir=run-cinnabar-smoke -PcinnabarSmokeTest` 可验证普通挖掘、时运、精准采集、低等级工具、实际熔炉烧炼和客户端配方同步。该选项会创建测试世界，输出 `logs/tc4modern/cinnabar-smoke.json`，然后退出客户端。

## 群系与地形 / Biomes and terrain

MFCL 负责放置 TC4 群系（魔法森林、腐化之地，以及仅能由旧逻辑改写得到的怪异群系），但群系内容取自原版森林/平原模板。TC4: Modern 参考原版 `BiomeGenMagicalForest`、`BiomeGenTaint` 与 `ThaumcraftWorldGenerator.generateVegetation`，用 1.20.1 的 configured/placed feature 与 Forge biome modifier 重建这些装饰，并去掉 MFCL 复制进来的原版树木、花草：

- 魔法森林：每区块 2 棵树（1/10 概率 3 棵），其中 1/14 银木（高 8+random(5)），其余 1/10 宏伟之木，余下为加高的大型橡树；苔石堆、巨型蘑菇、9 种小花各 10 簇、草与蕨、睡莲、蘑菇，以及长在原木旁草方块上的魔力菇。额外生成马和女巫。
- 腐化之地：树木（1/8 大型橡树，其余为默认橡树/大橡树）、少量草、结壳腐化方块堆，以及草方块上和贴附在实心表面上的腐化纤维。
- 怪异群系：补充原版刷怪表（含额外的女巫与末影人）。
- 全局植被（遵循 Thaumcraft.cfg 的 `generate_trees`，超平坦世界不生成）：每区块 1/60 尝试在魔法类群系生成银木，1/25 尝试按原版 BiomeHandler 群系概率生成宏伟之木（森林 1.0、繁茂 0.5、针叶林/热带草原/平原/沼泽 0.2），1/30 在炎热沙质群系生成烈焰花。
- 宏伟之木有 1/8 为蜘蛛树：下方埋有洞穴蜘蛛刷怪笼和地牢战利品箱，周围布满蛛网。银木根部生成水银花。
- 原版 BiomeDictionary 类型以 1.20 群系标签表示：TC4 群系加入 `forge:is_magical`、`minecraft:is_forest`、`forge:is_wasteland`、`forge:is_spooky`。

尚未包含：灵气节点（含银木节结中的纯净节点、图腾）、遗迹土丘 / 黑曜石图腾 / 山顶石圈等结构、魔力豆荚，以及佩奇、精灵等 TC4 自有生物的刷新——MFCL 公开 API 目前还无法从现代世界生成写入节点元素或生成旧实体。1.7.10 中的 forest hills / birch forest hills 在 1.20 中没有对应群系，因此原版在其中生成的银木不再出现。

Using MFCL's TC4 biomes, the original magical forest, tainted land and global vegetation decorators are rebuilt as 1.20.1 features and biome modifiers (greatwood, silverwood and big magic trees ported as features; vanilla template vegetation removed). Aura nodes, structures, mana pods and TC4 mob spawns are not included yet.

## 扫描 / Scanning

所有扫描均由原版 TC4 探测仪执行：原版 `doScan` 选择目标，原版持续使用回调控制计时与取消，原版 `ScanManager.completeScan` 处理前置条件、扫描记录、研究点、线索和提示，透镜内的显示也使用原版绘制器。现代侧不运行第二套扫描会话、完成请求或 HUD。

Lib 将新版方块、物品和实体映射为 MFCL 的独立只读旧版身份，并通过 TC4 原有的要素注册 API 提供数据。深板岩矿石等变体可以继承对应旧方块的要素，但扫描身份相互独立。此映射不向已封闭的旧 Forge 注册表追加内容；旧版已有目标保留原始身份。

需要包含 `LegacyRuntime.hostRepresentations()` 及只读身份查询桥接的 MFCL 构建；本地开发配置指向重新构建的 MFCL 运行时和 API JAR。现代扫描规则在登录及数据包重载时由服务端同步，实际扫描完成仍使用 TC4 原包经 MFCL 转发，并由服务端重新检查目标。

执行 `:tc4-modern:runClient -PgameDir=run-modern-scanner-smoke -PmodernScannerSmokeTest`，可在独立测试世界验证原版探测仪扫描新版方块、掉落物和实体，以及重复扫描和存档重载。结果写入 `logs/tc4modern/modern-scanner-smoke.json`；应以其中的 `status: PASS` 判断成功，客户端正常退出本身不代表测试通过。

## 元素练习册 / Element Workbook

用一本书、一张纸和一个墨囊无序合成「元素练习册」，也可以在本模组创造标签页取得。手持右键打开六边形要素连线练习，选择 1～7 级后随机出题。

- 元要素计为 0 阶；复合要素阶数为其两个组分的最高阶数加一。从实际运行时读取要素及组成，因此也支持附属添加的合法要素。所选等级同时限制固定要素、可选要素和参考解；每题至少包含一个该阶固定要素。
- 相邻要素中，一个是另一个的直接合成组分时可以连接。将所有金边固定要素连成一个网络即完成，不要求填满棋盘，也不要求与生成器的参考解相同。
- 左键选择左侧要素，再左键填入六边形；右键擦除非固定要素。悬停查看要素阶数与组成；左侧翻页或滚轮浏览要素。「清空」保留本题，「提示」逐步填入或修正参考解，「换一题」重新生成。
- 生成器先构造连接所有格子的合法要素树，再移除非固定叶格并隐藏中间要素，因此每题都有不超过所选阶数的解。
- 这是免费客户端练习，不消耗研究点或墨水，不解锁研究，也不提供奖励。关闭后再次打开会生成新题；没有可用要素的等级会显示提示。

验收：`:tc4-modern:test --tests '*practice*'` 覆盖 1～7 级、随机题目可解性、等级上限、固定格保护及提示修正。准备原版 TC4 的独立游戏目录后，执行 `:tc4-modern:runClient -PgameDir=run-workbook-smoke -PworkbookSmokeTest`，会创建专用测试世界，用实际要素表检查 700 题并验证物品打开、等级按钮、填入、擦除、清空和提示；截图及结果分别保存到 `screenshots/tc4modern-workbook-*.png` 和 `logs/tc4modern/workbook-smoke.json`。

## JSON 配方 / JSON recipes

可以用 JSON **新增**奥术（有序/无序）、坩埚、注魔配方。它们通过原版 `ThaumcraftApi.add*` 加入原版配方表，因此合成匹配、研究门槛和 JEI 显示都与原版配方完全一致。原版配方不会被删除或覆盖。

- 位置：任意模组 JAR 的 `data/<namespace>/tc4_recipes/**.json`；整合包可放在 `config/tc4modern/tc4_recipes/<namespace>/**.json`，与 JAR 内同 ID（`namespace:路径`）的文件以配置目录为准。
- 时机：每个物理端在加载完成（`FMLLoadCompleteEvent`）时读取一次。**不是数据包**：不支持 `/reload`，客户端与服务端必须拥有相同的文件（JAR 内文件自然一致；配置目录需要整合包两端同时分发）。
- 出错的文件会在日志中写明原因并跳过（`TC4 recipes: skipped ...`），不影响其他配方；研究、要素、物品、矿物词典名都会在注册前校验。
- 需要 MFCL 0.2.9-dev2+（`mla.object-arguments`）；更早的构建会记录警告并不加载。

| `type` | 字段 |
|---|---|
| `tc4modern:arcane_shaped` | `research`、`pattern`（1–3 行，等宽，最多 3 列，空格为空槽）、`key`、`aspects`、`result`、可选 `mirrored`（默认 `true`） |
| `tc4modern:arcane_shapeless` | `research`、`ingredients`（1–9 个）、`aspects`、`result` |
| `tc4modern:crucible` | `research`、`catalyst`、`aspects`（必填）、`result` |
| `tc4modern:infusion` | `research`、`input`、`components`（仅物品，不支持矿物词典，与原版一致）、`aspects`、`instability`（默认 0）、`result` |

物品写法：
- 原版物品：`{"legacy_item": "Thaumcraft:ItemResource", "metadata": 2}`；`metadata` 可写 `"*"` 表示任意（仅限材料）；`nbt` 为 1.7.10 SNBT。
- 新版物品：`{"item": "minecraft:amethyst_shard"}`，通过 MFCL 的宿主物品身份进入旧版，数量与 `nbt`（新版 SNBT）完整保留。
- 矿物词典（仅材料）：`{"ore": "ingotIron"}`。
- `count` 只用于 `result`。

```json
{
  "type": "tc4modern:arcane_shaped",
  "research": "BASICARTIFACE",
  "pattern": ["G G", " I ", "G G"],
  "key": {
    "G": { "ore": "blockGlass" },
    "I": { "legacy_item": "minecraft:iron_ingot" }
  },
  "aspects": { "aer": 5, "ordo": 5 },
  "result": { "legacy_item": "Thaumcraft:ItemResource", "metadata": 2, "count": 1 }
}
```

其他附属可直接使用 Lib API：`TcRecipeRegistrar.register(runtime, TcRecipeDefinition)`。神秘学典籍书页不会自动包含新配方。

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
