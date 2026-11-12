# 现代附属：装备、Vis 与要素存储

这些接口服务于通过 MFCL 加载的原版 TC4 4.2.3.5。纯 Java 类型在 `tc-modern-api` 中；Forge capability 和现代装备接口在完整 `tc-modern-lib` 模组中。调用旧运行时应在所属游戏线程进行，不要跨调用缓存旧对象句柄。

## 装备效果

现代护甲物品可实现 `TcEquipmentEffects`。`visDiscount(stack, player, aspect)` 返回整数百分比，`warp(stack, player)` 返回装备扭曲值；负值可降低总扭曲。`player` 是仅在当前回调内有效的旧玩家句柄，`aspect` 是 TC4 标签，可为 null。桥接仅汇总四个护甲槽，不会自动适配 Curios、护目镜或符文护盾。旧版装备效果仍由 TC4 本体计算。装备类与桥接位于 Lib 完整模组，不在独立 API JAR 中。

## 法杖与容器

- `TcWandParts.rods/caps(runtime)` 返回注册表的快照；材料可能为 null。属性遵循 TC4 原始单位。
- `TcWandVis.snapshot(runtime, stack)` 读取法杖，容量和各要素值使用厘 Vis（100 厘 Vis = 1 Vis）。
- `TcWandVis.consume(runtime, serverPlayer, stack, costs, simulate, crafting)` 调用原版消耗与减耗计算。传入和返回的 `LegacyStack` 是副本：实际成功消耗后，调用方必须把结果写回对应的真实物品槽并同步；模拟和失败时不写回。调用方负责在服务器线程调用。这个接口不自动搜索玩家库存。
- `TcContainers.block(runtime, tile)` 和 `item(runtime, legacyStack)` 检查原版对应接口并返回不可变的要素数量快照：`Optional.empty()` 表示对象不支持接口，空 map 表示支持但没有内容。方块版本接受 MFCL 的旧方块实体句柄。它读取的是容器自身内容，而不是物品的扫描要素。
- `TcNodes.snapshot(runtime, tile)` 读取实现 `INode` 的旧方块实体，返回当前/基础要素、类型和修饰；空结果表示不是节点。它不提供节点写入；原版 `getId()` 对尚未初始化 ID 的节点可能会生成 ID。
- `TcRegistrations` 委托 TC4 注册物品/研究扭曲和战利品袋条目，或在服务端玩家事务中调用原版玩家扭曲方法。物品、研究扭曲是覆盖；战利品调用是追加，不要重复注册。注册应在 TC4 已就绪且目标存在时进行。

## 类似 FE 的现代要素能力

`AspectStorage` 提供按 TC4 标签的 `receive`、`extract`、`stored`、总 `capacity` 和 `contents`；`simulate=true` 禁止修改状态。`SimpleAspectStorage` 是共享总容量、独立输入/输出速率上限的内存实现。数量为整数要素单位，**不等同于法杖厘 Vis 或玩家研究点**。`load(snapshot)` 可在不经过传输限额的情况下恢复已保存的数据，并原子校验容量。该实现本身不验证标签是否已在原版注册。

现代 Forge 方块实体或物品可以像暴露 FE 一样通过 `AspectCapabilities.STORAGE` 返回 `LazyOptional<AspectStorage>`，并按侧控制能力暴露。提供方负责 `LazyOptional` 生命周期、存档、服务端同步、线程约束和必要的标签校验；可覆盖 `SimpleAspectStorage.onChanged()` 标记数据变化。Lib 使用 MFCL 重定向让原版 TC4 的源质管道识别相邻侧的该能力并双向传输。普通存储可接收时默认吸力为 64；满容量或禁用接收时为 0，外部吸力更高时可从可提取的存储中拉取。两个相等吸力的端口不会相互传输。实现 `AspectTubePort` 可自定义 0–128 的吸力、吸取过滤要素和最低抽取吸力；过滤值必须是已注册的 TC4 要素。传输在服务端进行，客户端显示及持久化仍由能力提供方负责；仅调用 `simulate=true` 确认可接受后才从旧管道取出。只有 TC4 本体调用站点会被桥接，其他旧附属独立实现的管网不能保证兼容。需要支持 `LegacyFeatures.REDIRECTS` 的 MFCL。

## 验证

准备好原版 TC4 4.2.3.5、Baubles 和 MFCL 旧运行时后，可用独立游戏目录运行 `:tc4-modern:runClient -PgameDir=build/lib-api-smoke -PlibApiSmokeTest -PwithCurios=false -PwithJei=false -PwithFtbQuests=false`。结果写入该游戏目录的 `logs/tc4modern/lib-api-smoke.txt`；它覆盖法杖部件及 Vis 快照、节点/容器识别和一次战利品注册，不覆盖实际装备、Vis 消费或玩家扭曲同步。

真实管道双向验收：准备隔离游戏目录及原版 TC4/Baubles 后运行 `:tc4-modern:runClient -PgameDir=build/aspect-tube-smoke -PaspectTubeSmokeTest -PwithCurios=false -PwithJei=false -PwithFtbQuests=false`，结果写到该目录下 `aspect-tube-smoke.txt`。测试检查按面连接、错误要素过滤、混合要素的选择以及双向传输。
