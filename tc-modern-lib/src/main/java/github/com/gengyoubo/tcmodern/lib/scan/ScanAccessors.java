package github.com.gengyoubo.tcmodern.lib.scan;

import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.mla.*;

/** Public TC4 operations, invoked only in MFCL's scoped server-player transaction. */
public final class ScanAccessors {
    private ScanAccessors() { }
    // MFCL resolves the whole contract at bind time; factories/static methods must
    // not share a receiver-less contract with instance members.
    @LegacyType("thaumcraft.api.aspects.Aspect") public interface AspectLookup {
        @LegacyMethod(name="getAspect", descriptor="(Ljava/lang/String;)Lthaumcraft/api/aspects/Aspect;", isStatic=true) LegacyObject find(String tag);
    }
    @LegacyType("thaumcraft.api.aspects.Aspect") public interface Aspect {
        @LegacyMethod(name="getTag", descriptor="()Ljava/lang/String;") String tag();
    }
    @LegacyType("thaumcraft.api.aspects.AspectList") public interface AspectsFactory {
        @LegacyConstructor(descriptor="()V") LegacyObject create();
    }
    @LegacyType("thaumcraft.api.aspects.AspectList") public interface Aspects {
        @LegacyMethod(name="add", descriptor="(Lthaumcraft/api/aspects/Aspect;I)Lthaumcraft/api/aspects/AspectList;") LegacyObject add(LegacyObject aspect, int amount);
        @LegacyMethod(name="getAspects", descriptor="()[Lthaumcraft/api/aspects/Aspect;") LegacyObject[] values();
        @LegacyMethod(name="getAmount", descriptor="(Lthaumcraft/api/aspects/Aspect;)I") int amount(LegacyObject aspect);
    }
    @LegacyType("thaumcraft.common.lib.crafting.ThaumcraftCraftingManager") public interface Crafting {
        @LegacyMethod(name="getObjectTags", descriptor="(Lnet/minecraft/item/ItemStack;)Lthaumcraft/api/aspects/AspectList;", isStatic=true) LegacyObject tags(LegacyObject stack);
        @LegacyMethod(name="getBonusTags", descriptor="(Lnet/minecraft/item/ItemStack;Lthaumcraft/api/aspects/AspectList;)Lthaumcraft/api/aspects/AspectList;", isStatic=true) LegacyObject bonus(LegacyObject stack, LegacyObject tags);
    }
    @LegacyType("thaumcraft.api.research.ScanResult") public interface ResultFactory {
        @LegacyConstructor(descriptor="(BIILnet/minecraft/entity/Entity;Ljava/lang/String;)V") LegacyObject create(byte type, int id, int meta, LegacyObject entity, String key);
    }
    @LegacyType("thaumcraft.api.research.ScanResult") public interface Result {
        @LegacyField(name="type", descriptor="B") byte type();
        @LegacyField(name="id", descriptor="I") int id();
        @LegacyField(name="meta", descriptor="I") int meta();
        @LegacyField(name="entity", descriptor="Lnet/minecraft/entity/Entity;") LegacyObject entity();
    }
    @LegacyType("thaumcraft.common.lib.research.ScanManager") public interface Manager {
        @LegacyMethod(name="hasBeenScanned", descriptor="(Lnet/minecraft/entity/player/EntityPlayer;Lthaumcraft/api/research/ScanResult;)Z", isStatic=true) boolean scanned(LegacyObject player, LegacyObject scan);
        @LegacyMethod(name="completeScan", descriptor="(Lnet/minecraft/entity/player/EntityPlayer;Lthaumcraft/api/research/ScanResult;Ljava/lang/String;)Z", isStatic=true) boolean complete(LegacyObject player, LegacyObject scan, String prefix);
    }
    @LegacyType("thaumcraft.common.Thaumcraft") public interface Thaumcraft {
        @LegacyField(name="proxy", descriptor="Lthaumcraft/common/CommonProxy;", isStatic=true) LegacyObject proxy();
    }
    @LegacyType("thaumcraft.api.ThaumcraftApi") public interface Registration {
        @LegacyMethod(name="registerObjectTag", descriptor="(Lnet/minecraft/item/ItemStack;Lthaumcraft/api/aspects/AspectList;)V", isStatic=true) void item(LegacyObject stack, LegacyObject aspects);
        @LegacyMethod(name="registerEntityTag", descriptor="(Ljava/lang/String;Lthaumcraft/api/aspects/AspectList;[Lthaumcraft/api/ThaumcraftApi$EntityTagsNBT;)V", isStatic=true) void entity(String name, LegacyObject aspects, LegacyObject[] nbt);
    }
    @LegacyType("net.minecraft.entity.EntityList") public interface EntityFactory {
        @LegacyMethod(name="func_75620_a", descriptor="(Ljava/lang/String;Lnet/minecraft/world/World;)Lnet/minecraft/entity/Entity;", isStatic=true) LegacyObject create(String name, LegacyObject world);
    }
    @LegacyType("net.minecraft.item.Item") public interface Item {
        @LegacyMethod(name="func_150891_b", descriptor="(Lnet/minecraft/item/Item;)I", isStatic=true) int id(LegacyObject item);
    }
    @LegacyType("net.minecraft.item.ItemStack") public interface Stack {
        @LegacyMethod(name="func_77973_b", descriptor="()Lnet/minecraft/item/Item;") LegacyObject item();
    }
    @LegacyType("thaumcraft.common.items.relics.ItemThaumometer") public interface Thaumometer {
        @LegacyMethod(name="doScan", descriptor="(Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;I)Lthaumcraft/api/research/ScanResult;") LegacyObject target(LegacyObject stack, LegacyObject world, LegacyObject player, int remaining);
    }
    @LegacyType("net.minecraft.entity.Entity") public interface Entity {
        @LegacyField(name="field_70170_p", descriptor="Lnet/minecraft/world/World;") LegacyObject world();
    }
}
