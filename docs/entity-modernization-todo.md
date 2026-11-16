# TC4 实体现代化 TODO

本清单跟踪 TC4 原版实体在现代侧的实现。这里的“未实现”指尚未注册对应的现代实体；原版实体仍可能由 MFCL 托管。完成时应同时检查原版生成路径、已有存档、行为、模型、动画、声音和掉落物，而不只是注册实体类型。

## 尚未改写的生物和 Boss

- [x] 箱子宝宝（`EntityTravelingTrunk`）。
- [x] 九狱焱蝠（`EntityFireBat`）。
- [x] 邪术守卫（`EntityEldritchGuardian`）。
- [x] 邪术爪牙（`EntityEldritchCrab`）；行尸走肉死亡时直接释放现代爪牙（带胸甲外壳），现代实体不可用时才回退到原版生成。
- [x] 心魔蜘蛛（`EntityMindSpider`）。
- [x] 神秘史莱姆（`EntityThaumicSlime`）。
- [x] 血腥教徒（`EntityCultistCleric`）。
- [x] 血腥骑士（`EntityCultistKnight`）。
- [x] 精灵（`EntityWisp`）。
- [x] 岩精强盗（`EntityPech`），包括岩精法师、岩精猎手等变种。
- [x] 血腥传送门（`EntityCultistPortal`）。
- [x] 血腥主教（`EntityCultistLeader`）。
- [x] 邪术构建体（`EntityEldritchGolem`）。
- [x] 邪术领主（`EntityEldritchWarden`）。
- [x] 核对 `EntityWatcher` 的原版生成与用途，并决定是否需要现代实体：原版 `ConfigEntities` 从未注册或生成它，只出现在勇士词条白名单中，因此不建立现代实体。

## 投射物和其他实体

- [x] 傀儡飞镖（`EntityDart`）。
- [x] 霜星（`EntityFrostShard`）、爆炎（`EntityExplosiveOrb`）、电球（`EntityShockOrb`）。
- [x] 能珠（`EntityGolemOrb`）、岩精咒术球（`EntityPechBlast`）、邪术球（`EntityEldritchOrb`）。
- [x] 元始能量球和元始箭（`EntityPrimalOrb`、`EntityPrimalArrow`）。
- [x] 腐化瓶（`EntityBottleTaint`）、源动之焰（`EntityAlumentum`）、`EntityEmber`。
- [x] 要素球、跟随物品、魔力物品（`EntityAspectOrb`、`EntityFollowingItem`、`EntitySpecialItem`）。
- [x] 核对 `EntityItemGrate`、`EntityPermanentItem` 等辅助实体的原版用途及现代化需求：格栅物品（穿过奥术格栅时不被推出）和永久物品（永不消失）都已建立现代实体。
- [x] 将钓鱼傀儡当前的浮子渲染完善为实际的现代浮子实体，并检查鱼线、碰撞及生命周期（`EntityGolemBobber`）：现代傀儡投出 `golem_bobber` 实体，咬钩时下沉，停止钓鱼、傀儡死亡、落地或 4000 tick 后消失，不写入存档。原版 `EntityGolemBobber` 仍交给 MFCL 托管，因为原版傀儡的钓鱼 AI 依赖它。

## 已有现代实体，仍需完善和验证

- [x] 逐项核对红眼僵尸、红眼僵尸巨人、行尸走肉的原版行为、装备、动画和掉落。
- [ ] 核对腐化动物、腐化魔蛛、腐化孢子、孢子巢穴、孢子群、大小触手及巨型触手的模型、碰撞、音效、生成和掉落。
- [x] 在游戏中验证孢子群攻击、受伤和死亡的原版音效，以及触手在不同群系中的受伤行为。
- [x] 核对八种材质傀儡的外观、攻击动画、核心显示、生成和铃铛回收；检查原版已有装饰和升级傀儡的兼容路径。
- [x] 测试全部傀儡核心的安装、保存、卸载、回收、重新放置和多人同步。
- [x] 完善核心的多个标记、颜色及物品过滤、矿物词典匹配和原版升级功能。
- [ ] 完善收获后的补种、复杂树形砍伐、液体与源质容器兼容，以及使用核心的更多原版选项。
- [ ] 实测排序核心的容器变动与满载处理，炼金核心的源质管道和容器交互，以及钓鱼核心的战利品、鱼竿和浮子显示。
- [ ] 确认刷怪蛋只出现在合适的物品栏中；傀儡通过 TC4 原版物品生成，不增加傀儡刷怪蛋。
- [x] 在客户端逐个查看新实体的模型、贴图、动画和特效：渲染器按原版 Render 类移植，但尚未在游戏画面中核对（尤其是邪术构建体前襟 114 像素宽的贴图、领主眼部发光、精灵与传送门的帧动画、元始箭的光晕）。
- [ ] 岩精交易没有原版交易界面：驯服的岩精在玩家手持其看重的物品右键时按原版 `ContainerPech` 规则直接给出交易品；带 TC4 自定义附魔（急速、修复）的附魔书未加入交易表。
- [ ] 箱子宝宝用潜行右键切换“停留/跟随”（原版在界面中切换）；尚未移植主人跨维度后箱子跟随传送的行为。
- [ ] 邪术守卫在主世界带来的暗雾由客户端按距离自行判断（原版由服务端发 `PacketMiscEvent`），需要与原版的雾浓度对照。
- [x] Boss 不会获得原版勇士词条及其名称，也没有原版按玩家人数加成生命和攻击的部分；血腥主教与邪术领主的称号已保留。
- [ ] 要素球拾取、岩精法师法杖、元始能量球生成节点和精灵的要素都调用原版代码，需要在有 TC4 运行时的实际存档中逐项验证。
- [ ] 部分原版粒子（灵气、闪电弧、烟雾螺旋等）以原版或现代近似粒子代替，需要与原版效果对照。

已有现代实体注册可参见 `tc4-modern/src/main/java/github/com/gengyoubo/tcmodern/tc4/init/Tc4Entities.java`；原版实体类到现代宿主的注册集中在 `entity/Tc4EntityHosts.java`。服务端验收可用 `./gradlew :tc4-modern:runServer -PentitySmokeTest -PgameDir=run-entity-smoke`（需在该目录的 `mods/1.7.10` 放入原版 TC4 与 Baubles），结果写入 `run-entity-smoke/tc4modern/entity-smoke.json`。基础实现不等同于完整复刻；尚未实际测试的行为需要保留为待验证。
