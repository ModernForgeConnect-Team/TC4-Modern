package github.com.gengyoubo.tcmodern.tc4.content;

import github.com.gengyoubo.tcmodern.tc4.Tc4Modern;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Blocks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Tc4Modern.MOD_ID)
public final class GiantSaplingLoot {
    private GiantSaplingLoot() { }
    @SubscribeEvent public static void load(LootTableLoadEvent event) {
        ResourceLocation name = event.getName();
        if (!name.getNamespace().equals("minecraft") || !(name.getPath().equals("chests/simple_dungeon") || name.getPath().equals("chests/stronghold_library"))) return;
        var pool = LootPool.lootPool().name("tc4modern_giant_saplings").setRolls(ConstantValue.exactly(1))
                .when(LootItemRandomChanceCondition.randomChance(0.044F))
                .add(LootItem.lootTableItem(Tc4Blocks.SACRED_GREATWOOD_SAPLING.get()))
                .add(LootItem.lootTableItem(Tc4Blocks.SACRED_SILVERWOOD_SAPLING.get()))
                .add(LootItem.lootTableItem(Tc4Blocks.ENCHANTED_SACRED_GREATWOOD_SAPLING.get()))
                .add(LootItem.lootTableItem(Tc4Blocks.ENCHANTED_SACRED_SILVERWOOD_SAPLING.get())).build();
        event.getTable().addPool(pool);
    }
}
