# MFCL 公开 API 需求（来自 TC4-Modern / tc-modern-lib）

基于 mfcl-api 0.2.8 与 MFCL 源码（0.2.9 开发中）、Thaumcraft 1.7.10-4.2.3.5 反编译整理。

## 背景：现状与缺口

- 公开 API 只有"主动调用"：`accessor`（方法/字段）、`stack`/`snapshot`、`mappings`、`withPlayer`（仅服务端、作用域内）。
  没有任何"旧版代码来问宿主"的回调入口。
- MFCL 内部其实已有大部分机制，只是不公开：
  - `bridge.LegacyPlayerArmor`：把宿主护甲写进旧版 `armorInventory`，来源由 `ModernContentBridge.wornArmor` 写死为原版 4 个护甲槽，`source(...)` 覆盖式、单一来源。
  - `ModernContentBridge` 已桥接原版 `onArmorTick` / `onUpdate`，并有"宿主移动状态"回写（`LegacyItemUse:364`）。
  - `bridge.LegacyPlayerState.Hooks`：玩家数据 init/save/load/sync。
  - `LegacyModAdapter.registerBytecodeAdapters`：旧版侧字节码改写。
  - `compat/thaumcraft/*`：MFCL 自带的 TC4 适配（护目镜要素悬浮、粒子、法杖按键、扫描等）。
- 旧版玩家对象是**临时 actor**：每次用物品、TESR 渲染等都会新建 `InventoryPlayer` 再 `LegacyPlayerArmor.apply`。
  附属模组拿不到这些 actor，写进去的东西也活不过下一次重建。

## TC4 依赖"玩家穿戴"的机制（需求来源）

| TC4 功能 | 旧版位置 | 读什么 | 端 | 需要的 API |
|---|---|---|---|---|
| 护目镜看节点 | `TileNodeRenderer.func_147500_a`、`FXBeamPower` | `renderViewEntity.inventory.armorItemInSlot(3) instanceof IRevealer` | 客户端 | §1 |
| 护目镜要素悬浮 | `RenderEventHandler.blockHighlight`（MFCL 的 `ThaumcraftGoggles` 在宿主侧复刻） | 头盔 `IGoggles.showIngamePopups` | 客户端 | §1 |
| 神秘减耗 | `WandManager.getTotalVisDiscount` | 护甲 0–3 + Baubles 0–3 的 `IVisDiscountGear` | 服务端+客户端（显示） | §1 |
| 扭曲装备 | `WarpEvents.getWarpFromGear` | 护甲 + Baubles 的 `IWarpingGear` | 服务端 | §1 |
| 符文护盾 | `EventHandlerRunic.livingTick` / `entityHurt` | 护甲 + Baubles 的 `IRunicArmor`，`runicCharge` 以 **entityId** 为键的静态表 | 服务端（同步到客户端） | §1 §2 §4 §5 |
| 视力护符（法杖充能） | `ItemAmuletVis.onWornTick` | 改写主背包里法杖的 NBT | 服务端 | §1 §2 §3 |
| 悬浮腰带 / 悬浮挽具 | `ItemGirdleHover.onWornTick`、`Hover` | 飞行能力、motion | 双端 | §2 §6 |
| 核心袋（Focus Pouch 饰品） | `WandManager`、`REHWandHandler` | Baubles 里的 pouch 背包 | 双端 | §1 §3 |
| 套装/靴子 onArmorTick | 各护甲类 | 移动状态 | 双端 | MFCL 已有，仅需 §1 让 Curios 物品也走这条路 |

## 需要的 API

### §1 穿戴装备来源（核心，护目镜/减耗/扭曲/符文都靠它）

宿主 → 旧版：任何时候 MFCL 构建或刷新旧版玩家（服务端临时 actor、客户端玩家、`withPlayer`），都向已注册的来源询问每个槽位。

```java
public interface LegacyEquipment {
    enum Slot { ARMOR_FEET, ARMOR_LEGS, ARMOR_CHEST, ARMOR_HEAD,   // armorInventory 0..3
                BAUBLE_AMULET, BAUBLE_RING_0, BAUBLE_RING_1, BAUBLE_BELT } // Baubles 0..3
    /** 宿主物品（ItemStack）或 null 表示不提供，交给下一个来源/默认。hostPlayer 为宿主 Player。 */
    interface Source { Object worn(Object hostPlayer, Slot slot, boolean client); }
    /** 多来源按 priority 排序；MFCL 的原版护甲槽是默认来源（priority 0）。返回值关闭即注销。 */
    AutoCloseable register(int priority, Source source);
    /** 宿主侧装备变化（例如 Curios 槽位改变）时通知 MFCL 让客户端缓存失效、服务端重同步。 */
    void invalidate(Object hostPlayer);
}
// LegacyRuntime: default LegacyEquipment equipment()
```

要点：
- **按槽位合并**，而不是整组覆盖：Curios 只提供 `ARMOR_HEAD`（头盔槽为空时）和 4 个 Baubles 槽。
- **Baubles 槽要写进原版 Baubles 的存储**（1.0.1.10：`PlayerHandler.getPlayerBaubles(player)` → `InventoryBaubles`，按玩家名缓存），
  这样 `BaublesApi.getBaubles` 的所有调用方（WandManager、WarpEvents、EventHandlerRunic、Hover、REHWandHandler）都自动生效，
  不需要逐个改 TC4。Baubles 未加载时这几个槽位忽略。
- 客户端缓存（现有 `APPLIED`）以"所有来源结果"为键，配合 `invalidate`。
- 来源抛异常时只跳过该来源该槽位，记到 `diagnostics()`。

### §2 穿戴物品 tick

旧版的 `IBauble.onWornTick`、`onArmorTick` 需要每 tick 带一个**持久**旧版玩家被调用。

```java
// LegacyEquipment 追加
/** 每个服务端/客户端 player tick，对 §1 提供的每个非空槽位调用原版 tick（护甲 onArmorTick、饰品 onWornTick）。默认开启。 */
void setTicking(Slot slot, boolean enabled);
```

- 复用 MFCL 已有的 onArmorTick 桥（含移动状态回写），只是扩大到 §1 来源和 Baubles 槽。
- 注意不要和宿主原生 `Inventory.tick` 对护甲的 tick 重复。

### §3 写回（旧版改了穿戴物品/背包 → 宿主）

视力护符改法杖 NBT、符文/核心袋改自身 NBT、`onWornTick` 可能把物品清空。

```java
// Source 追加
/** 旧版在本次调用中修改/替换了该槽位的物品；newHostStack 为 null 表示被移除。由提供者负责写回 Curios。 */
default void changed(Object hostPlayer, Slot slot, Object newHostStack) { }
```

- MFCL 在每次构建/调用结束时比较前后快照（`LegacyStack` equals），只回调变化的槽位。
- 主背包的变化同理需要写回（视力护符充能背包里的法杖）：确认 `withPlayer` / tick 路径对主背包同样做 diff 写回。
- 写回要**保留宿主自有标签**（`Enchantments` / `RepairCost` / `display`、Curios 自身数据）：
  `LegacyMappings` 增加"合并到现有宿主物品"的转换，而不是新建：
  `<H> H applyTo(H existingHostStack, LegacyObject legacyStack)`。

### §4 宿主事件转发到旧版事件总线

TC4 的大量逻辑写在 1.7.10 Forge 事件里，MFCL 目前只按需在内部驱动（粒子、tick 等）。需要通用转发：

```java
public interface LegacyEvents {
    /** 用宿主实体构造（或复用）旧版实体，构造旧版事件并 post 到旧版 MinecraftForge.EVENT_BUS。 */
    Result post(String legacyEventClass, Object hostEntity, Map<String, Object> args);
    record Result(boolean canceled, Map<String, Object> fields) { }  // 例如 LivingHurtEvent 的 ammount
}
```

优先需要：`LivingHurtEvent`（符文护盾吸收伤害）、`LivingEvent.LivingUpdateEvent`（护盾充能、悬浮）、
`LivingFallEvent`、`PlayerEvent.BreakSpeed`、`ItemTooltipEvent`（符文/扭曲 tooltip，客户端）。
更理想的是 MFCL 自己按订阅自动转发：旧版有 `@SubscribeEvent` 订阅某事件类型时，宿主对应事件触发即转发，附属模组只需声明开启。

### §5 持久、身份一致的旧版玩家

- `EventHandlerRunic.runicCharge` 等以 **entityId** 为键：旧版 actor 的 `entityId`（`field_145783_c`）必须等于宿主实体 id，且服务端/客户端一致。
- 需要**客户端**等价物：

```java
/** 客户端线程上，以当前旧版客户端玩家（与 Minecraft.thePlayer / renderViewEntity 同一对象）执行。 */
<T> T withClientPlayer(Function<LegacyObject, T> operation);
```

- `withPlayer` 说明里写明：同一宿主玩家在同一世界内返回同一旧版对象（还是每次新建）。

### §6 玩家状态双向同步

onWornTick / onArmorTick 改的不只是物品：`capabilities.allowFlying/isFlying`、`motionX/Y/Z`、`fallDistance`、
`stepHeight`、药水效果、`getEntityData()`（TC4 用于扭曲/符文等持久数据）。
MFCL 已有部分"移动状态"回写，需要：明确清单、对 §2 路径同样生效、`getEntityData` 与宿主 `getPersistentData` 映射。

### §7 MLA 能力补全

- **数组**：目前无法读写 `ItemStack[]` 之类的旧版数组。增加 `LegacyArrays`：`length` / `get` / `set`（元素为 `LegacyObject` 或基本类型）。
- **JDK 集合**：现在 JDK 集合类不能作 MLA 目标，lib 只能借 Guava `ImmutableMap` 绕行。提供 `snapshotMap/snapshotList/iterate` 之类只读快照。
- **instanceof / 接口查询**：`boolean isInstance(LegacyObject, String legacyType)`，避免靠 `typeName()` 前缀猜（例如判断 `IGoggles` / `IRevealer` / `IBauble`）。
- **静态单例便捷入口**：`Minecraft.getMinecraft()`、`MinecraftServer` 等可用 `@LegacyMethod(isStatic=true)` 达到，文档里给出示例即可。

### §8 拦截/重定向（兜底，可选）

上面覆盖不到的硬编码检查（例如别的模组写死 `armorItemInSlot(3) instanceof X`）只能改字节码。
把内部的 `registerBytecodeAdapters` 以受限形式公开：

```java
/** 在旧版类加载前，把 owner.name(desc) 的调用重定向到附属提供的静态处理器（处理器运行在宿主侧，参数为 LegacyObject）。 */
AutoCloseable redirectCall(String targetClass, String owner, String name, String descriptor, Object handler);
```

只在 `whenReady` 之前（构造阶段）可用；必须是可选能力，失败不影响加载。

### §9 能力探测与版本

- 现在新功能用 `default ... throw UnsupportedOperationException` 表示不支持，调用方只能 try/catch。
  增加 `Set<String> LegacyRuntime.features()`（如 `"equipment"`, `"equipment.baubles"`, `"events"`, `"client-player"`），
  并在 `MfclApi.API_VERSION` 上递增。
- 所有新回调：注明调用线程（服务端线程 / 客户端渲染线程 / 客户端 tick）、是否在运行时锁内、能否在回调里再调用 API。

## TC4-Modern 侧落地顺序（拿到 API 后）

1. §1：Curios 来源（`head` 槽护目镜 + amulet/ring/belt），扫描时按接口（`IGoggles`/`IRevealer`/`IBauble`）分类，不再只看 `baubles` 包。
   → 护目镜、减耗、扭曲装备、核心袋生效。
2. §2 + §3：饰品 tick 与写回 → 视力护符、悬浮腰带。
3. §4 + §5：符文护盾。
4. §6：悬浮飞行等状态细节。

---

# 补充需求（API v2 之后）：JSON 配方注册（只新增）

§1–§9 已在 MFCL 0.2.9-dev1 实现。以下为 TC4-Modern 用 JSON 新增奥术（有序/无序）、坩埚、注魔配方时缺的部分。

## 目标做法

启动时（两端各一次）读取各模组 jar 内 `data/<ns>/tc4_recipes/**.json` 与 `config/` 下的整合包目录，
通过 MLA 直接调用 TC4 自己的注册方法，配方进入原版 `ThaumcraftApi.craftingRecipes`，匹配、JEI 快照全部复用原版：

| 类型 | 原版入口 | 参数里需要跨边界的值 |
|---|---|---|
| 有序奥术 | `ThaumcraftApi.addArcaneCraftingRecipe(String, ItemStack, AspectList, Object...)` | `Object[]` 内混有 **String**（图案行、矿物词典名）、**Character**（键）、ItemStack |
| 无序奥术 | `addShapelessArcaneCraftingRecipe(String, ItemStack, AspectList, Object...)` | `Object[]` 内混有 **String**（矿物词典名）、ItemStack |
| 坩埚 | `addCrucibleRecipe(String, ItemStack, Object, AspectList)` | `Object` 参数是 ItemStack 或 **String**（矿物词典名） |
| 注魔 | `addInfusionCraftingRecipe(String, Object, int, AspectList, ItemStack, ItemStack[])` | `Object` 结果是 ItemStack；`ItemStack[]` 已可用 `LegacyObject[]` 传 |

`AspectList` 用 `@LegacyConstructor` + `add(Aspect,int)` 构造，`Aspect` 用 `Aspect.getAspect(String)`，均已可行。

## §10 MLA 参数：允许把 String / 装箱基本类型传给 `Object` 形参（阻塞项）

现状（`LegacyApiRuntime.argument`）：目标形参是 `Object`、或 `Object[]` 的元素时，只接受 `LegacyObject`；
`String` 只在形参恰好是 `String` 时放行，`Character`/`Integer` 等装箱值只在形参是基本类型时放行。
因此上表四个入口里三个无法调用。

需要：形参类型（或数组元素类型）**能接受该值**时（`expected.isInstance(value)`），直接传入
`String`、`Character`、`Boolean`、`Number` 的装箱值；对 `LegacyObject` 引用的处理不变。
同样适用于 `@LegacyConstructor` 和 `LegacyValues.set`（后者实现已放行，文档写的是"装箱值只用于基本类型数组"，需对齐）。

验收用例：

```java
@LegacyType("thaumcraft.api.ThaumcraftApi")
interface Recipes {
    @LegacyMethod(name = "addArcaneCraftingRecipe", isStatic = true,
        descriptor = "(Ljava/lang/String;Lnet/minecraft/item/ItemStack;Lthaumcraft/api/aspects/AspectList;[Ljava/lang/Object;)Lthaumcraft/api/crafting/ShapedArcaneRecipe;")
    LegacyObject arcane(String research, LegacyObject result, LegacyObject aspects, Object[] recipe);
}
// recipe = { "G G", " I ", "G G", 'G', "blockGlass", 'I', ironStack }
```

注意：接口形参写成 `Object[]` 时 `infer` 无法推断描述符，必须显式给 descriptor（现状即如此，文档里写明即可）。

## §11 宿主物品作为配方材料/产物（非阻塞，按需）

JSON 里只写旧版物品（`Thaumcraft:ItemResource`、`minecraft:iron_ingot`）时不需要新东西。
若要用**只有宿主才有**的物品（1.13+ 物品、`tc4modern:*` 自己的物品）：

- 材料：`hostRepresentations().item(id)` 给出只读旧版身份。需要 MFCL **保证**：玩家放进奥术工作台/注魔祭坛的宿主物品，映射成的旧版物品与这个身份相同，
  使原版 `matches` 能成立。
- 产物：配方产出该只读身份时，需要保证写回宿主时还原成原来的宿主物品（含数量、NBT）。

请在 `LegacyHostRepresentations` 文档里说明这两点是否成立；不成立时 JSON 只允许旧版物品。

## §12 数组构造（可选，便利）

`LegacyValues.newArray(String componentType, int length)`：在旧运行时里建指定元素类型的空数组，配合 `set` 使用。
§10 实现后配方注册不再需要它；只在需要把一个旧数组交给多个旧方法、或元素类型不能从形参推出时有用。

## 不需要 MFCL 的部分（TC4-Modern 自己做）

- 资源发现：Forge 的 `ModList` / `IModFile` 读各 jar 的 `data/<ns>/tc4_recipes/`，`FMLPaths.CONFIGDIR` 读整合包目录。
- 时机：在 `FMLLoadCompleteEvent`（所有 `whenReady`、自定义要素注册之后，JEI 读取之前）注册。
- 单人游戏只注册一次：集成服务端与客户端共用同一个旧运行时，静态配方表只有一份。
- 矿物词典：原版构造函数自己展开字符串；需要额外别名时可调用 `OreDictionary.registerOre(String, ItemStack)`（形参就是 `String`，现在已可用）。
- 研究门槛沿用 `research` 字段；典籍书页不在本期范围。

## 以后（只新增阶段不需要）

删除/覆盖原版配方、`/reload`、数据包同步：需要修改旧版 JDK 集合（`ArrayList.remove` 等）的能力，届时另提。
