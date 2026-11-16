package github.com.gengyoubo.tcmodern.tc4.testing;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.api.LegacyMappings;
import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.LegacyStack;
import github.com.gengyoubo.mfcl.api.mla.LegacyConstructor;
import github.com.gengyoubo.mfcl.api.mla.LegacyMethod;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.tc4.Tc4Modern;
import github.com.gengyoubo.tcmodern.tc4.entity.*;
import github.com.gengyoubo.tcmodern.tc4.entity.item.*;
import github.com.gengyoubo.tcmodern.tc4.entity.projectile.*;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Entities;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Opt-in dedicated-server acceptance for the modern TC4 entities ({@code -PentitySmokeTest}): every type spawns,
 * ticks for ten seconds and survives a save round trip, and originals spawned in the legacy world get modern hosts.
 */
@Mod.EventBusSubscriber(modid = Tc4Modern.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class EntitySmokeProbe {
    private final Map<String, Object> proof = new LinkedHashMap<>();
    private final List<String> failures = new ArrayList<>();
    private final List<Entity> spawned = new ArrayList<>();
    private final Map<String, BlockPos> legacySpawns = new LinkedHashMap<>();
    private MinecraftServer server;
    private int ticks;

    @SubscribeEvent public static void setup(FMLCommonSetupEvent event) {
        if (!Boolean.getBoolean("tc4modern.entitySmokeTest")) return;
        var probe = new EntitySmokeProbe();
        MinecraftForge.EVENT_BUS.addListener(probe::started);
        MinecraftForge.EVENT_BUS.addListener(probe::tick);
    }

    private void started(ServerStartedEvent event) { server = event.getServer(); }

    private void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || server == null || ticks < 0) return;
        ticks++;
        try {
            var level = server.overworld();
            if (ticks == 40) spawnModern(level);
            if (ticks == 60) spawnLegacy(level);
            if (ticks == 63) checkEarly(level);
            if (ticks == 240) { checkLegacy(level); checkModern(level); finish(null); }
        } catch (Throwable failure) {
            finish(failure);
        }
    }

    private BlockPos ground(ServerLevel level, int x, int z) {
        // Without players nothing ticks far from spawn; keep the test chunks loaded and entity-ticking.
        level.setChunkForced(x >> 4, z >> 4, true);
        level.getChunk(x >> 4, z >> 4);
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + 1, z);
    }

    private void spawnModern(ServerLevel level) {
        proof.put("runtime", TcModernApi.runtime().isPresent());
        proof.put("aspects", Tc4Legacy.aspects().size());
        var types = List.<EntityType<?>>of(Tc4Entities.TRAVELING_TRUNK.get(), Tc4Entities.FIRE_BAT.get(), Tc4Entities.ELDRITCH_GUARDIAN.get(),
                Tc4Entities.ELDRITCH_CRAB.get(), Tc4Entities.MIND_SPIDER.get(), Tc4Entities.THAUMIC_SLIME.get(), Tc4Entities.CULTIST_CLERIC.get(),
                Tc4Entities.CULTIST_KNIGHT.get(), Tc4Entities.WISP.get(), Tc4Entities.PECH.get(), Tc4Entities.CULTIST_PORTAL.get(),
                Tc4Entities.CULTIST_LEADER.get(), Tc4Entities.ELDRITCH_GOLEM.get(), Tc4Entities.ELDRITCH_WARDEN.get(),
                Tc4Entities.DART.get(), Tc4Entities.PRIMAL_ARROW.get(), Tc4Entities.FROST_SHARD.get(), Tc4Entities.EXPLOSIVE_ORB.get(),
                Tc4Entities.SHOCK_ORB.get(), Tc4Entities.GOLEM_ORB.get(), Tc4Entities.PECH_BLAST.get(), Tc4Entities.ELDRITCH_ORB.get(),
                Tc4Entities.PRIMAL_ORB.get(), Tc4Entities.BOTTLE_TAINT.get(), Tc4Entities.ALUMENTUM.get(), Tc4Entities.EMBER.get(),
                Tc4Entities.ASPECT_ORB.get(), Tc4Entities.SPECIAL_ITEM.get(), Tc4Entities.PERMANENT_ITEM.get(), Tc4Entities.FOLLOWING_ITEM.get(),
                Tc4Entities.ITEM_GRATE.get());
        int i = 0;
        for (var type : types) {
            var entity = type.create(level);
            if (entity == null) { failures.add("create " + type); continue; }
            var pos = ground(level, 200 + (i % 8) * 24, 200 + (i / 8) * 24);
            entity.moveTo(pos.getX() + 0.5, pos.getY() + (entity instanceof Mob ? 0 : 6), pos.getZ() + 0.5, 0, 0);
            if (entity instanceof Mob mob) mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.COMMAND, null, null);
            if (entity instanceof net.minecraft.world.entity.item.ItemEntity item) item.setItem(new ItemStack(Items.DIAMOND));
            if (entity instanceof net.minecraft.world.entity.projectile.Projectile projectile) projectile.setDeltaMovement(0.3, 0.1, 0.0);
            if (!level.addFreshEntity(entity)) failures.add("add " + type);
            spawned.add(entity);
            i++;
        }
        proof.put("spawnedModern", spawned.size());
        // A thaumaturge's wand is built from original items; build one directly, since equipment is random.
        try {
            var pech = Tc4Entities.PECH.get().create(level);
            var method = Pech.class.getDeclaredMethod("pechWand");
            method.setAccessible(true);
            var wand = (ItemStack) method.invoke(pech);
            proof.put("pechWand", Tc4Legacy.legacy(wand).map(LegacyStack::snbt).orElse("missing"));
            if (wand.isEmpty()) failures.add("pech wand");
        } catch (ReflectiveOperationException failure) { failures.add("pech wand: " + failure); }
        proof.put("wispEssence", Tc4Legacy.legacy(Tc4Legacy.withAspect("Thaumcraft:ItemWispEssence", "ignis", 2)).map(LegacyStack::snbt).orElse("missing"));
    }

    private interface Factory { LegacyObject create(LegacyObject world); }

    private void spawnLegacy(ServerLevel level) {
        var runtime = TcModernApi.runtime().orElseThrow();
        var world = runtime.mappings().toLegacy(LegacyMappings.WORLD, level).orElseThrow();
        Map<String, Function<LegacyObject, LegacyObject>> factories = new LinkedHashMap<>();
        factories.put("Wisp", w -> runtime.accessor(NewWisp.class).create(w));
        factories.put("FireBat", w -> runtime.accessor(NewFireBat.class).create(w));
        factories.put("Pech", w -> runtime.accessor(NewPech.class).create(w));
        factories.put("EldritchCrab", w -> runtime.accessor(NewCrab.class).create(w));
        factories.put("MindSpider", w -> runtime.accessor(NewMindSpider.class).create(w));
        factories.put("ThaumicSlime", w -> runtime.accessor(NewSlime.class).create(w));
        factories.put("CultistKnight", w -> runtime.accessor(NewKnight.class).create(w));
        factories.put("CultistCleric", w -> runtime.accessor(NewCleric.class).create(w));
        factories.put("EldritchGuardian", w -> runtime.accessor(NewGuardian.class).create(w));
        factories.put("TravelingTrunk", w -> runtime.accessor(NewTrunk.class).create(w));
        factories.put("CultistPortal", w -> runtime.accessor(NewPortal.class).create(w));
        factories.put("CultistLeader", w -> runtime.accessor(NewLeader.class).create(w));
        factories.put("EldritchGolem", w -> runtime.accessor(NewEldritchGolem.class).create(w));
        factories.put("EldritchWarden", w -> runtime.accessor(NewWarden.class).create(w));
        factories.put("FrostShard", w -> runtime.accessor(NewFrostShard.class).create(w));
        factories.put("Ember", w -> runtime.accessor(NewEmber.class).create(w));
        factories.put("ShockOrb", w -> runtime.accessor(NewShockOrb.class).create(w));
        factories.put("ExplosiveOrb", w -> runtime.accessor(NewExplosiveOrb.class).create(w));
        factories.put("GolemOrb", w -> runtime.accessor(NewGolemOrb.class).create(w));
        factories.put("PechBlast", w -> runtime.accessor(NewPechBlast.class).create(w));
        factories.put("EldritchOrb", w -> runtime.accessor(NewEldritchOrb.class).create(w));
        factories.put("PrimalOrb", w -> runtime.accessor(NewPrimalOrb.class).create(w));
        factories.put("BottleTaint", w -> runtime.accessor(NewBottleTaint.class).create(w));
        factories.put("Alumentum", w -> runtime.accessor(NewAlumentum.class).create(w));
        factories.put("Dart", w -> runtime.accessor(NewDart.class).create(w));
        factories.put("PrimalArrow", w -> runtime.accessor(NewPrimalArrow.class).create(w));
        factories.put("SpecialItem", w -> runtime.accessor(NewSpecialItem.class).create(w, 0, 0, 0, runtime.stack(new LegacyStack("minecraft:diamond", 0, 1, ""))));
        factories.put("AspectOrb", w -> runtime.accessor(NewAspectOrb.class).create(w, 0, 0, 0, runtime.accessor(AspectOrb.AspectLookup.class).get("ignis"), 2));
        int i = 0;
        for (var entry : factories.entrySet()) {
            var pos = ground(level, -200 - (i % 8) * 48, -200 - (i / 8) * 48);
            try {
                var original = entry.getValue().apply(world);
                var old = runtime.accessor(OldEntity.class, original);
                old.position(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                if (!runtime.accessor(OldWorld.class, world).spawn(original)) failures.add("legacy spawn refused " + entry.getKey());
                legacySpawns.put(entry.getKey(), pos);
            } catch (RuntimeException failure) {
                failures.add("legacy " + entry.getKey() + ": " + failure);
                LogUtils.getLogger().error("Legacy spawn of {} failed", entry.getKey(), failure);
            }
            i++;
        }
    }

    /** Projectiles and item drops do not live long; their hosts are checked right after the original spawned. */
    private void checkEarly(ServerLevel level) {
        var hosted = hosts(level);
        proof.put("legacyHostsEarly", hosted);
        var nearby = new LinkedHashMap<String, String>();
        for (var name : List.of("SpecialItem", "AspectOrb")) {
            var pos = legacySpawns.get(name);
            if (pos == null) continue;
            nearby.put(name, level.getEntities((Entity) null, new net.minecraft.world.phys.AABB(pos).inflate(20), e -> true).stream()
                    .map(e -> net.minecraft.world.entity.EntityType.getKey(e.getType()) + "@" + e.blockPosition().toShortString()).toList().toString());
        }
        proof.put("nearbyEarly", nearby);
        var orbs = new ArrayList<String>();
        for (var e : level.getAllEntities()) if (e instanceof AspectOrb orb) orbs.add(orb.blockPosition().toShortString() + " age " + orb.age() + " ticks " + orb.tickCount + " removed " + orb.getRemovalReason());
        proof.put("aspectOrbsEarly", orbs);
        for (var name : List.of("FrostShard", "Ember", "ShockOrb", "ExplosiveOrb", "GolemOrb", "PechBlast", "EldritchOrb", "PrimalOrb",
                "BottleTaint", "Alumentum", "Dart", "PrimalArrow", "SpecialItem", "AspectOrb"))
            if (!name.equals(hosted.get(name))) failures.add("early legacy host " + name + " -> " + hosted.get(name));
    }

    private Map<String, String> hosts(ServerLevel level) {
        var hosted = new LinkedHashMap<String, String>();
        for (var entry : legacySpawns.entrySet()) {
            var pos = entry.getValue();
            var box = new net.minecraft.world.phys.AABB(pos).inflate(20);
            var centre = net.minecraft.world.phys.Vec3.atCenterOf(pos);
            hosted.put(entry.getKey(), level.getEntities((Entity) null, box, e -> e.getClass().getName().startsWith("github.com.gengyoubo.tcmodern.tc4.entity"))
                    .stream().min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(centre))).map(e -> e.getClass().getSimpleName()).orElse("none"));
        }
        return hosted;
    }

    private void checkLegacy(ServerLevel level) {
        var hosted = new LinkedHashMap<String, String>();
        for (var entry : legacySpawns.entrySet()) {
            var pos = entry.getValue();
            var box = new net.minecraft.world.phys.AABB(pos).inflate(20);
            var centre = net.minecraft.world.phys.Vec3.atCenterOf(pos);
            String found = level.getEntities((Entity) null, box, e -> e.getClass().getName().startsWith("github.com.gengyoubo.tcmodern.tc4.entity"))
                    .stream().min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(centre)))
                    .map(e -> e.getClass().getSimpleName()).orElse("none");
            hosted.put(entry.getKey(), found);
        }
        proof.put("legacyHosts", hosted);
        // Projectiles may already have hit something and gone; creatures must be hosted by the modern class.
        for (var name : List.of("Wisp", "FireBat", "Pech", "EldritchCrab", "MindSpider", "ThaumicSlime", "CultistKnight", "CultistCleric",
                "EldritchGuardian", "TravelingTrunk", "CultistPortal", "CultistLeader", "EldritchGolem", "EldritchWarden")) {
            String found = hosted.get(name);
            if (found == null || !found.equals(name)) failures.add("legacy host " + name + " -> " + found);
        }
    }

    private void checkModern(ServerLevel level) {
        var alive = new LinkedHashMap<String, Object>();
        for (var entity : spawned) {
            String name = entity.getClass().getSimpleName();
            alive.put(name, entity.isAlive() ? "alive" : String.valueOf(entity.getRemovalReason()));
            var tag = entity.saveWithoutId(new CompoundTag());
            var copy = entity.getType().create(level);
            if (copy == null) { failures.add("recreate " + name); continue; }
            copy.load(tag);
            if (entity instanceof ThaumicSlime slime && ((ThaumicSlime) copy).slimeSize() != slime.slimeSize()) failures.add("slime size round trip");
            if (entity instanceof Pech pech && ((Pech) copy).pechType() != pech.pechType()) failures.add("pech type round trip");
            if (entity instanceof Wisp wisp && !((Wisp) copy).aspect().equals(wisp.aspect())) failures.add("wisp aspect round trip");
            if (entity instanceof Wisp wisp && Tc4Legacy.aspect(wisp.aspect()) == null) failures.add("wisp has no aspect: '" + wisp.aspect() + "'");
            if (entity instanceof EldritchCrab crab && ((EldritchCrab) copy).hasHelm() != crab.hasHelm()) failures.add("crab helm round trip");
            if (entity instanceof Cultist cultist && cultist.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty()) failures.add(name + " has no robe");
        }
        proof.put("modern", alive);
        for (var mob : List.of("TravelingTrunk", "EldritchGuardian", "CultistKnight", "CultistCleric", "Pech", "CultistPortal", "CultistLeader",
                "EldritchGolem", "EldritchWarden", "EldritchCrab", "ThaumicSlime"))
            if (!"alive".equals(alive.get(mob))) failures.add(mob + " did not survive: " + alive.get(mob));
    }

    private void finish(Throwable failure) {
        ticks = -1;
        if (failure != null) failures.add(failure.toString());
        proof.put("failures", failures);
        proof.put("status", failures.isEmpty() ? "PASS" : "FAIL");
        try {
            var file = server.getServerDirectory().toPath().resolve("tc4modern/entity-smoke.json");
            Files.createDirectories(file.getParent());
            Files.writeString(file, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(proof));
        } catch (Exception ignored) { }
        LogUtils.getLogger().info("TC4_ENTITY_SMOKE_RESULT: {}", proof);
        if (failure != null) LogUtils.getLogger().error("Entity smoke probe failed", failure);
        server.halt(false);
    }

    @LegacyType("net.minecraft.entity.Entity") public interface OldEntity { @LegacyMethod(name = "func_70107_b", descriptor = "(DDD)V") void position(double x, double y, double z); }
    @LegacyType("net.minecraft.world.World") public interface OldWorld { @LegacyMethod(name = "func_72838_d", descriptor = "(Lnet/minecraft/entity/Entity;)Z") boolean spawn(LegacyObject entity); }
    private static final String W = "(Lnet/minecraft/world/World;)V";
    @LegacyType("thaumcraft.common.entities.monster.EntityWisp") public interface NewWisp { @LegacyConstructor(descriptor = W) LegacyObject create(LegacyObject w); }
    @LegacyType("thaumcraft.common.entities.monster.EntityFireBat") public interface NewFireBat { @LegacyConstructor(descriptor = W) LegacyObject create(LegacyObject w); }
    @LegacyType("thaumcraft.common.entities.monster.EntityPech") public interface NewPech { @LegacyConstructor(descriptor = W) LegacyObject create(LegacyObject w); }
    @LegacyType("thaumcraft.common.entities.monster.EntityEldritchCrab") public interface NewCrab { @LegacyConstructor(descriptor = W) LegacyObject create(LegacyObject w); }
    @LegacyType("thaumcraft.common.entities.monster.EntityMindSpider") public interface NewMindSpider { @LegacyConstructor(descriptor = W) LegacyObject create(LegacyObject w); }
    @LegacyType("thaumcraft.common.entities.monster.EntityThaumicSlime") public interface NewSlime { @LegacyConstructor(descriptor = W) LegacyObject create(LegacyObject w); }
    @LegacyType("thaumcraft.common.entities.monster.EntityCultistKnight") public interface NewKnight { @LegacyConstructor(descriptor = W) LegacyObject create(LegacyObject w); }
    @LegacyType("thaumcraft.common.entities.monster.EntityCultistCleric") public interface NewCleric { @LegacyConstructor(descriptor = W) LegacyObject create(LegacyObject w); }
    @LegacyType("thaumcraft.common.entities.monster.EntityEldritchGuardian") public interface NewGuardian { @LegacyConstructor(descriptor = W) LegacyObject create(LegacyObject w); }
    @LegacyType("thaumcraft.common.entities.golems.EntityTravelingTrunk") public interface NewTrunk { @LegacyConstructor(descriptor = W) LegacyObject create(LegacyObject w); }
    @LegacyType("thaumcraft.common.entities.monster.boss.EntityCultistPortal") public interface NewPortal { @LegacyConstructor(descriptor = W) LegacyObject create(LegacyObject w); }
    @LegacyType("thaumcraft.common.entities.monster.boss.EntityCultistLeader") public interface NewLeader { @LegacyConstructor(descriptor = W) LegacyObject create(LegacyObject w); }
    @LegacyType("thaumcraft.common.entities.monster.boss.EntityEldritchGolem") public interface NewEldritchGolem { @LegacyConstructor(descriptor = W) LegacyObject create(LegacyObject w); }
    @LegacyType("thaumcraft.common.entities.monster.boss.EntityEldritchWarden") public interface NewWarden { @LegacyConstructor(descriptor = W) LegacyObject create(LegacyObject w); }
    @LegacyType("thaumcraft.common.entities.projectile.EntityFrostShard") public interface NewFrostShard { @LegacyConstructor(descriptor = W) LegacyObject create(LegacyObject w); }
    @LegacyType("thaumcraft.common.entities.projectile.EntityEmber") public interface NewEmber { @LegacyConstructor(descriptor = W) LegacyObject create(LegacyObject w); }
    @LegacyType("thaumcraft.common.entities.projectile.EntityShockOrb") public interface NewShockOrb { @LegacyConstructor(descriptor = W) LegacyObject create(LegacyObject w); }
    @LegacyType("thaumcraft.common.entities.projectile.EntityExplosiveOrb") public interface NewExplosiveOrb { @LegacyConstructor(descriptor = W) LegacyObject create(LegacyObject w); }
    @LegacyType("thaumcraft.common.entities.projectile.EntityGolemOrb") public interface NewGolemOrb { @LegacyConstructor(descriptor = W) LegacyObject create(LegacyObject w); }
    @LegacyType("thaumcraft.common.entities.projectile.EntityPechBlast") public interface NewPechBlast { @LegacyConstructor(descriptor = W) LegacyObject create(LegacyObject w); }
    @LegacyType("thaumcraft.common.entities.projectile.EntityEldritchOrb") public interface NewEldritchOrb { @LegacyConstructor(descriptor = W) LegacyObject create(LegacyObject w); }
    @LegacyType("thaumcraft.common.entities.projectile.EntityPrimalOrb") public interface NewPrimalOrb { @LegacyConstructor(descriptor = W) LegacyObject create(LegacyObject w); }
    @LegacyType("thaumcraft.common.entities.projectile.EntityBottleTaint") public interface NewBottleTaint { @LegacyConstructor(descriptor = W) LegacyObject create(LegacyObject w); }
    @LegacyType("thaumcraft.common.entities.projectile.EntityAlumentum") public interface NewAlumentum { @LegacyConstructor(descriptor = W) LegacyObject create(LegacyObject w); }
    @LegacyType("thaumcraft.common.entities.projectile.EntityDart") public interface NewDart { @LegacyConstructor(descriptor = W) LegacyObject create(LegacyObject w); }
    @LegacyType("thaumcraft.common.entities.projectile.EntityPrimalArrow") public interface NewPrimalArrow { @LegacyConstructor(descriptor = W) LegacyObject create(LegacyObject w); }
    @LegacyType("thaumcraft.common.entities.EntitySpecialItem") public interface NewSpecialItem {
        @LegacyConstructor(descriptor = "(Lnet/minecraft/world/World;DDDLnet/minecraft/item/ItemStack;)V") LegacyObject create(LegacyObject w, double x, double y, double z, LegacyObject stack);
    }
    @LegacyType("thaumcraft.common.entities.EntityAspectOrb") public interface NewAspectOrb {
        @LegacyConstructor(descriptor = "(Lnet/minecraft/world/World;DDDLthaumcraft/api/aspects/Aspect;I)V") LegacyObject create(LegacyObject w, double x, double y, double z, LegacyObject aspect, int value);
    }
}
