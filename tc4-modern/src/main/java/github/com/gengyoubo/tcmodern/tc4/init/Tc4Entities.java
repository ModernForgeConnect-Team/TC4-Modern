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

    private Tc4Entities() { }
    public static void register(IEventBus bus) {
        TYPES.register(bus);
        bus.addListener(Tc4Entities::attributes);
    }
    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(BRAINY_ZOMBIE.get(), BrainyZombie.attributes().build());
    }
}
