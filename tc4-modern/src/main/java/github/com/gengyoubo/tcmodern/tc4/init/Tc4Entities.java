package github.com.gengyoubo.tcmodern.tc4.init;

import github.com.gengyoubo.tcmodern.tc4.Tc4Modern;
import github.com.gengyoubo.tcmodern.tc4.entity.BrainyZombie;
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

    private Tc4Entities() { }
    public static void register(IEventBus bus) {
        TYPES.register(bus);
        bus.addListener(Tc4Entities::attributes);
    }
    private static void attributes(EntityAttributeCreationEvent event) {
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
    }
}
