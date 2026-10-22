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
