package github.com.gengyoubo.tcmodern.tc4.content;

import github.com.gengyoubo.tcmodern.tc4.Tc4Modern;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Items;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.TierSortingRegistry;

/**
 * Ancient thaumium tools: thaumium tools upgraded at a smithing table, a step above netherite. Each tool keeps
 * netherite's damage and speed modifiers; the tier adds the rest.
 */
public final class AncientThaumiumTools {
    /** Blocks only this tier (or better) can harvest; none yet. */
    public static final TagKey<Block> NEEDS_TOOL = BlockTags.create(ResourceLocation.fromNamespaceAndPath(Tc4Modern.MOD_ID, "needs_ancient_thaumium_tool"));

    /** Netherite's mining level and more of everything else: 2500 uses (netherite 2031), speed 10 (9), +5 damage (4). */
    public static final Tier TIER = new Tier() {
        @Override public int getUses() { return 2500; }
        @Override public float getSpeed() { return 10F; }
        @Override public float getAttackDamageBonus() { return 5F; }
        @Override @SuppressWarnings("deprecation") public int getLevel() { return 4; }
        @Override public int getEnchantmentValue() { return 25; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.of(Tc4Items.ANCIENT_THAUMIUM_INGOT.get()); }
        @Override public TagKey<Block> getTag() { return NEEDS_TOOL; }
    };

    private AncientThaumiumTools() { }

    /** Sorts the tier right after netherite, so it harvests everything netherite does. Call from mod construction. */
    public static void registerTier() {
        TierSortingRegistry.registerTier(TIER, ResourceLocation.fromNamespaceAndPath(Tc4Modern.MOD_ID, "ancient_thaumium"),
                List.of(Tiers.NETHERITE), List.of());
    }
}
