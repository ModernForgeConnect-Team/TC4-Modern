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
    }
}
