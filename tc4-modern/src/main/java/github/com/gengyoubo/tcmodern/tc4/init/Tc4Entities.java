package github.com.gengyoubo.tcmodern.tc4.init;

import github.com.gengyoubo.tcmodern.tc4.Tc4Modern;
import github.com.gengyoubo.tcmodern.tc4.entity.BrainyZombie;
import github.com.gengyoubo.tcmodern.tc4.entity.*;
import github.com.gengyoubo.tcmodern.tc4.entity.golem.GolemBobber;
import github.com.gengyoubo.tcmodern.tc4.entity.item.*;
import github.com.gengyoubo.tcmodern.tc4.entity.projectile.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class Tc4Entities {
    private static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Tc4Modern.MOD_ID);
    public static final RegistryObject<EntityType<BrainyZombie>> BRAINY_ZOMBIE = TYPES.register("brainy_zombie",
            () -> EntityType.Builder.of(BrainyZombie::new, MobCategory.MONSTER).sized(0.6F, 1.95F)
                    .clientTrackingRange(8).build("tc4modern:brainy_zombie"));

    public static final RegistryObject<EntityType<github.com.gengyoubo.tcmodern.tc4.entity.GiantBrainyZombie>> GIANT_BRAINY_ZOMBIE = TYPES.register("giant_brainy_zombie",
            () -> EntityType.Builder.of(github.com.gengyoubo.tcmodern.tc4.entity.GiantBrainyZombie::new, MobCategory.MONSTER)
                    .sized(1.32F, 3.96F).clientTrackingRange(8).build("tc4modern:giant_brainy_zombie"));

    public static final RegistryObject<EntityType<github.com.gengyoubo.tcmodern.tc4.entity.TaintSpider>> TAINT_SPIDER = TYPES.register("taint_spider",
            () -> EntityType.Builder.of(github.com.gengyoubo.tcmodern.tc4.entity.TaintSpider::new, MobCategory.MONSTER)
                    .sized(0.4F, 0.3F).clientTrackingRange(8).build("tc4modern:taint_spider"));

    public static final RegistryObject<EntityType<github.com.gengyoubo.tcmodern.tc4.entity.InhabitedZombie>> INHABITED_ZOMBIE = TYPES.register("inhabited_zombie",
            () -> EntityType.Builder.of(github.com.gengyoubo.tcmodern.tc4.entity.InhabitedZombie::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F).clientTrackingRange(8).build("tc4modern:inhabited_zombie"));

    public static final RegistryObject<EntityType<github.com.gengyoubo.tcmodern.tc4.entity.TaintCow>> TAINT_COW = TYPES.register("taint_cow",
            () -> EntityType.Builder.of(github.com.gengyoubo.tcmodern.tc4.entity.TaintCow::new, MobCategory.MONSTER)
                    .sized(0.9F, 1.3F).clientTrackingRange(8).build("tc4modern:taint_cow"));

    public static final RegistryObject<EntityType<github.com.gengyoubo.tcmodern.tc4.entity.TaintPig>> TAINT_PIG = TYPES.register("taint_pig",
            () -> EntityType.Builder.of(github.com.gengyoubo.tcmodern.tc4.entity.TaintPig::new, MobCategory.MONSTER)
                    .sized(0.9F, 0.9F).clientTrackingRange(8).build("tc4modern:taint_pig"));
    public static final RegistryObject<EntityType<github.com.gengyoubo.tcmodern.tc4.entity.TaintChicken>> TAINT_CHICKEN = TYPES.register("taint_chicken",
            () -> EntityType.Builder.of(github.com.gengyoubo.tcmodern.tc4.entity.TaintChicken::new, MobCategory.MONSTER)
                    .sized(0.5F, 0.8F).clientTrackingRange(8).build("tc4modern:taint_chicken"));

    public static final RegistryObject<EntityType<github.com.gengyoubo.tcmodern.tc4.entity.TaintSheep>> TAINT_SHEEP = TYPES.register("taint_sheep",
            () -> EntityType.Builder.of(github.com.gengyoubo.tcmodern.tc4.entity.TaintSheep::new, MobCategory.MONSTER)
                    .sized(0.9F, 1.3F).clientTrackingRange(8).build("tc4modern:taint_sheep"));

    public static final RegistryObject<EntityType<github.com.gengyoubo.tcmodern.tc4.entity.TaintVillager>> TAINT_VILLAGER = TYPES.register("taint_villager",
            () -> EntityType.Builder.of(github.com.gengyoubo.tcmodern.tc4.entity.TaintVillager::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F).clientTrackingRange(8).build("tc4modern:taint_villager"));

    public static final RegistryObject<EntityType<github.com.gengyoubo.tcmodern.tc4.entity.TaintCreeper>> TAINT_CREEPER = TYPES.register("taint_creeper",
            () -> EntityType.Builder.of(github.com.gengyoubo.tcmodern.tc4.entity.TaintCreeper::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.7F).clientTrackingRange(8).build("tc4modern:taint_creeper"));

    public static final RegistryObject<EntityType<github.com.gengyoubo.tcmodern.tc4.entity.TaintSpore>> TAINT_SPORE = TYPES.register("taint_spore",
            () -> EntityType.Builder.of(github.com.gengyoubo.tcmodern.tc4.entity.TaintSpore::new, MobCategory.MONSTER)
                    .sized(0.5F, 0.5F).clientTrackingRange(8).build("tc4modern:taint_spore"));

    public static final RegistryObject<EntityType<github.com.gengyoubo.tcmodern.tc4.entity.TaintSporeNest>> TAINT_SPORE_NEST = TYPES.register("taint_spore_nest",
            () -> EntityType.Builder.of(github.com.gengyoubo.tcmodern.tc4.entity.TaintSporeNest::new, MobCategory.MONSTER)
                    .sized(1.0F, 1.0F).clientTrackingRange(8).build("tc4modern:taint_spore_nest"));

    public static final RegistryObject<EntityType<github.com.gengyoubo.tcmodern.tc4.entity.TaintSwarm>> TAINT_SWARM = TYPES.register("taint_swarm",
            () -> EntityType.Builder.of(github.com.gengyoubo.tcmodern.tc4.entity.TaintSwarm::new, MobCategory.MONSTER)
                    .sized(2.0F, 2.0F).clientTrackingRange(8).build("tc4modern:taint_swarm"));

    public static final RegistryObject<EntityType<github.com.gengyoubo.tcmodern.tc4.entity.TaintTentacle>> TAINT_TENTACLE = TYPES.register("taint_tentacle",
            () -> EntityType.Builder.of(github.com.gengyoubo.tcmodern.tc4.entity.TaintTentacle::new, MobCategory.MONSTER)
                    .sized(0.66F, 3.0F).clientTrackingRange(8).build("tc4modern:taint_tentacle"));
    public static final RegistryObject<EntityType<github.com.gengyoubo.tcmodern.tc4.entity.SmallTaintTentacle>> SMALL_TAINT_TENTACLE = TYPES.register("small_taint_tentacle",
            () -> EntityType.Builder.of(github.com.gengyoubo.tcmodern.tc4.entity.SmallTaintTentacle::new, MobCategory.MONSTER)
                    .sized(0.22F, 1.0F).clientTrackingRange(8).build("tc4modern:small_taint_tentacle"));

    public static final RegistryObject<EntityType<github.com.gengyoubo.tcmodern.tc4.entity.GiantTaintTentacle>> GIANT_TAINT_TENTACLE = TYPES.register("giant_taint_tentacle",
            () -> EntityType.Builder.of(github.com.gengyoubo.tcmodern.tc4.entity.GiantTaintTentacle::new, MobCategory.MONSTER)
                    .sized(1.1F, 6.0F).clientTrackingRange(12).build("tc4modern:giant_taint_tentacle"));

    public static final RegistryObject<EntityType<github.com.gengyoubo.tcmodern.tc4.entity.FallingTaint>> FALLING_TAINT = TYPES.register("falling_taint",
            () -> EntityType.Builder.<github.com.gengyoubo.tcmodern.tc4.entity.FallingTaint>of(github.com.gengyoubo.tcmodern.tc4.entity.FallingTaint::new, MobCategory.MISC)
                    .sized(0.98F, 0.98F).clientTrackingRange(10).updateInterval(1).build("tc4modern:falling_taint"));

    public static final java.util.Map<github.com.gengyoubo.tcmodern.tc4.entity.golem.GolemMaterial, RegistryObject<EntityType<github.com.gengyoubo.tcmodern.tc4.entity.golem.ModernGolem>>> GOLEMS = registerGolems();
    private static java.util.Map<github.com.gengyoubo.tcmodern.tc4.entity.golem.GolemMaterial, RegistryObject<EntityType<github.com.gengyoubo.tcmodern.tc4.entity.golem.ModernGolem>>> registerGolems() {
        var result = new java.util.EnumMap<github.com.gengyoubo.tcmodern.tc4.entity.golem.GolemMaterial, RegistryObject<EntityType<github.com.gengyoubo.tcmodern.tc4.entity.golem.ModernGolem>>>(github.com.gengyoubo.tcmodern.tc4.entity.golem.GolemMaterial.class);
        for (var material : github.com.gengyoubo.tcmodern.tc4.entity.golem.GolemMaterial.values()) {
            String id = material.id() + "_golem";
            result.put(material, TYPES.register(id, () -> EntityType.Builder.<github.com.gengyoubo.tcmodern.tc4.entity.golem.ModernGolem>of(
                    (type, level) -> new github.com.gengyoubo.tcmodern.tc4.entity.golem.ModernGolem(type, level, material), MobCategory.MISC)
                    .sized(0.4F, 0.95F).clientTrackingRange(8).build("tc4modern:" + id)));
        }
        return java.util.Collections.unmodifiableMap(result);
    }


    // --- Creatures and bosses ---------------------------------------------------------------------------------------
    public static final RegistryObject<EntityType<TravelingTrunk>> TRAVELING_TRUNK = entity("traveling_trunk", TravelingTrunk::new, MobCategory.MISC, 0.8F, 0.8F, 8, 3, true);
    public static final RegistryObject<EntityType<FireBat>> FIRE_BAT = entity("fire_bat", FireBat::new, MobCategory.MONSTER, 0.5F, 0.9F, 8, 3, true);
    public static final RegistryObject<EntityType<EldritchGuardian>> ELDRITCH_GUARDIAN = entity("eldritch_guardian", EldritchGuardian::new, MobCategory.MONSTER, 0.8F, 2.25F, 8, 3, false);
    public static final RegistryObject<EntityType<EldritchCrab>> ELDRITCH_CRAB = entity("eldritch_crab", EldritchCrab::new, MobCategory.MONSTER, 0.8F, 0.6F, 8, 3, false);
    public static final RegistryObject<EntityType<MindSpider>> MIND_SPIDER = entity("mind_spider", MindSpider::new, MobCategory.MONSTER, 0.3F, 0.3F, 8, 3, false);
    public static final RegistryObject<EntityType<ThaumicSlime>> THAUMIC_SLIME = entity("thaumic_slime", ThaumicSlime::new, MobCategory.MONSTER, 0.5F, 0.5F, 8, 3, false);
    public static final RegistryObject<EntityType<CultistCleric>> CULTIST_CLERIC = entity("cultist_cleric", CultistCleric::new, MobCategory.MONSTER, 0.6F, 1.8F, 8, 3, false);
    public static final RegistryObject<EntityType<CultistKnight>> CULTIST_KNIGHT = entity("cultist_knight", CultistKnight::new, MobCategory.MONSTER, 0.6F, 1.8F, 8, 3, false);
    public static final RegistryObject<EntityType<Wisp>> WISP = entity("wisp", Wisp::new, MobCategory.MONSTER, 0.9F, 0.9F, 8, 3, false);
    public static final RegistryObject<EntityType<Pech>> PECH = entity("pech", Pech::new, MobCategory.MONSTER, 0.6F, 1.8F, 8, 3, false);
    public static final RegistryObject<EntityType<CultistPortal>> CULTIST_PORTAL = entity("cultist_portal", CultistPortal::new, MobCategory.MONSTER, 1.5F, 3.0F, 8, 20, true);
    public static final RegistryObject<EntityType<CultistLeader>> CULTIST_LEADER = entity("cultist_leader", CultistLeader::new, MobCategory.MONSTER, 0.75F, 2.25F, 8, 3, false);
    public static final RegistryObject<EntityType<EldritchGolem>> ELDRITCH_GOLEM = entity("eldritch_golem", EldritchGolem::new, MobCategory.MONSTER, 1.75F, 3.5F, 8, 3, true);
    public static final RegistryObject<EntityType<EldritchWarden>> ELDRITCH_WARDEN = entity("eldritch_warden", EldritchWarden::new, MobCategory.MONSTER, 1.5F, 3.5F, 8, 3, false);

    // --- Projectiles ------------------------------------------------------------------------------------------------
    public static final RegistryObject<EntityType<Dart>> DART = entity("dart", Dart::new, MobCategory.MISC, 0.5F, 0.5F, 4, 20, false);
    public static final RegistryObject<EntityType<PrimalArrow>> PRIMAL_ARROW = entity("primal_arrow", PrimalArrow::new, MobCategory.MISC, 0.5F, 0.5F, 4, 20, false);
    public static final RegistryObject<EntityType<FrostShard>> FROST_SHARD = entity("frost_shard", FrostShard::new, MobCategory.MISC, 0.3F, 0.3F, 4, 3, false);
    public static final RegistryObject<EntityType<ExplosiveOrb>> EXPLOSIVE_ORB = entity("explosive_orb", ExplosiveOrb::new, MobCategory.MISC, 0.25F, 0.25F, 4, 5, false);
    public static final RegistryObject<EntityType<ShockOrb>> SHOCK_ORB = entity("shock_orb", ShockOrb::new, MobCategory.MISC, 0.25F, 0.25F, 4, 5, false);
    public static final RegistryObject<EntityType<GolemOrb>> GOLEM_ORB = entity("golem_orb", GolemOrb::new, MobCategory.MISC, 0.25F, 0.25F, 4, 2, false);
    public static final RegistryObject<EntityType<PechBlast>> PECH_BLAST = entity("pech_blast", PechBlast::new, MobCategory.MISC, 0.25F, 0.25F, 4, 5, false);
    public static final RegistryObject<EntityType<EldritchOrb>> ELDRITCH_ORB = entity("eldritch_orb", EldritchOrb::new, MobCategory.MISC, 0.25F, 0.25F, 4, 5, false);
    public static final RegistryObject<EntityType<PrimalOrb>> PRIMAL_ORB = entity("primal_orb", PrimalOrb::new, MobCategory.MISC, 0.25F, 0.25F, 8, 2, false);
    public static final RegistryObject<EntityType<BottleTaint>> BOTTLE_TAINT = entity("bottle_taint", BottleTaint::new, MobCategory.MISC, 0.25F, 0.25F, 4, 10, false);
    public static final RegistryObject<EntityType<Alumentum>> ALUMENTUM = entity("alumentum", Alumentum::new, MobCategory.MISC, 0.25F, 0.25F, 4, 10, false);
    public static final RegistryObject<EntityType<Ember>> EMBER = entity("ember", Ember::new, MobCategory.MISC, 0.25F, 0.25F, 4, 2, false);

    // --- Items, orbs and helpers ------------------------------------------------------------------------------------
    public static final RegistryObject<EntityType<AspectOrb>> ASPECT_ORB = entity("aspect_orb", AspectOrb::new, MobCategory.MISC, 0.125F, 0.125F, 8, 3, false);
    public static final RegistryObject<EntityType<SpecialItem>> SPECIAL_ITEM = entity("special_item", SpecialItem::new, MobCategory.MISC, 0.25F, 0.25F, 6, 20, false);
    public static final RegistryObject<EntityType<PermanentItem>> PERMANENT_ITEM = entity("permanent_item", PermanentItem::new, MobCategory.MISC, 0.25F, 0.25F, 6, 20, false);
    public static final RegistryObject<EntityType<FollowingItem>> FOLLOWING_ITEM = entity("following_item", FollowingItem::new, MobCategory.MISC, 0.25F, 0.25F, 6, 3, false);
    public static final RegistryObject<EntityType<ItemGrate>> ITEM_GRATE = entity("item_grate", ItemGrate::new, MobCategory.MISC, 0.25F, 0.25F, 6, 20, false);
    public static final RegistryObject<EntityType<GolemBobber>> GOLEM_BOBBER = entity("golem_bobber", GolemBobber::new, MobCategory.MISC, 0.25F, 0.25F, 4, 5, false);

    private static <T extends Entity> RegistryObject<EntityType<T>> entity(String id, EntityType.EntityFactory<T> factory, MobCategory category,
            float width, float height, int range, int interval, boolean fireImmune) {
        return TYPES.register(id, () -> {
            var builder = EntityType.Builder.of(factory, category).sized(width, height).clientTrackingRange(range).updateInterval(interval);
            if (fireImmune) builder.fireImmune();
            return builder.build("tc4modern:" + id);
        });
    }

    private Tc4Entities() { }
    public static void register(IEventBus bus) {
        TYPES.register(bus);
        bus.addListener(Tc4Entities::attributes);
    }
    private static void attributes(EntityAttributeCreationEvent event) {
        GOLEMS.forEach((material, type) -> event.put(type.get(), github.com.gengyoubo.tcmodern.tc4.entity.golem.ModernGolem.attributes(material).build()));
        event.put(BRAINY_ZOMBIE.get(), BrainyZombie.attributes().build());
        event.put(GIANT_BRAINY_ZOMBIE.get(), github.com.gengyoubo.tcmodern.tc4.entity.GiantBrainyZombie.giantAttributes().build());
        event.put(TAINT_SPIDER.get(), github.com.gengyoubo.tcmodern.tc4.entity.TaintSpider.attributes().build());
        event.put(INHABITED_ZOMBIE.get(), github.com.gengyoubo.tcmodern.tc4.entity.InhabitedZombie.inhabitedAttributes().build());
        event.put(TAINT_COW.get(), github.com.gengyoubo.tcmodern.tc4.entity.TaintCow.attributes().build());
        event.put(TAINT_PIG.get(), github.com.gengyoubo.tcmodern.tc4.entity.TaintPig.pigAttributes().build());
        event.put(TAINT_CHICKEN.get(), github.com.gengyoubo.tcmodern.tc4.entity.TaintChicken.chickenAttributes().build());
        event.put(TAINT_SHEEP.get(), github.com.gengyoubo.tcmodern.tc4.entity.TaintSheep.sheepAttributes().build());
        event.put(TAINT_VILLAGER.get(), github.com.gengyoubo.tcmodern.tc4.entity.TaintVillager.villagerAttributes().build());
        event.put(TAINT_CREEPER.get(), github.com.gengyoubo.tcmodern.tc4.entity.TaintCreeper.creeperAttributes().build());
        event.put(TAINT_SPORE.get(), github.com.gengyoubo.tcmodern.tc4.entity.TaintSpore.sporeAttributes().build());
        event.put(TAINT_SPORE_NEST.get(), github.com.gengyoubo.tcmodern.tc4.entity.TaintSporeNest.nestAttributes().build());
        event.put(TAINT_SWARM.get(), github.com.gengyoubo.tcmodern.tc4.entity.TaintSwarm.swarmAttributes().build());
        event.put(TAINT_TENTACLE.get(), github.com.gengyoubo.tcmodern.tc4.entity.TaintTentacle.tentacleAttributes().build());
        event.put(SMALL_TAINT_TENTACLE.get(), github.com.gengyoubo.tcmodern.tc4.entity.SmallTaintTentacle.smallAttributes().build());
        event.put(GIANT_TAINT_TENTACLE.get(), github.com.gengyoubo.tcmodern.tc4.entity.GiantTaintTentacle.giantAttributes().build());
        event.put(TRAVELING_TRUNK.get(), TravelingTrunk.attributes().build());
        event.put(FIRE_BAT.get(), FireBat.attributes().build());
        event.put(ELDRITCH_GUARDIAN.get(), EldritchGuardian.attributes().build());
        event.put(ELDRITCH_CRAB.get(), EldritchCrab.attributes().build());
        event.put(MIND_SPIDER.get(), MindSpider.attributes().build());
        event.put(THAUMIC_SLIME.get(), ThaumicSlime.attributes().build());
        event.put(CULTIST_CLERIC.get(), CultistCleric.attributes().build());
        event.put(CULTIST_KNIGHT.get(), CultistKnight.attributes().build());
        event.put(WISP.get(), Wisp.attributes().build());
        event.put(PECH.get(), Pech.attributes().build());
        event.put(CULTIST_PORTAL.get(), CultistPortal.attributes().build());
        event.put(CULTIST_LEADER.get(), CultistLeader.attributes().build());
        event.put(ELDRITCH_GOLEM.get(), EldritchGolem.attributes().build());
        event.put(ELDRITCH_WARDEN.get(), EldritchWarden.attributes().build());
    }
}
