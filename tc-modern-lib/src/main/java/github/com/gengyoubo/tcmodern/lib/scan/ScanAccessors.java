package github.com.gengyoubo.tcmodern.lib.scan;

import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.mla.*;

/** Public TC4 operations, invoked only in MFCL's scoped server-player transaction. */
public final class ScanAccessors {
    private ScanAccessors() { }
    @LegacyType("thaumcraft.api.aspects.Aspect") public interface Aspect {
        @LegacyMethod(name="getAspect", descriptor="(Ljava/lang/String;)Lthaumcraft/api/aspects/Aspect;", isStatic=true) LegacyObject find(String tag);
        @LegacyMethod(name="getTag", descriptor="()Ljava/lang/String;") String tag();
    }
    @LegacyType("thaumcraft.api.aspects.AspectList") public interface Aspects {
        @LegacyConstructor(descriptor="()V") LegacyObject create();
        @LegacyMethod(name="add", descriptor="(Lthaumcraft/api/aspects/Aspect;I)Lthaumcraft/api/aspects/AspectList;") LegacyObject add(LegacyObject aspect, int amount);
        @LegacyMethod(name="getAspects", descriptor="()[Lthaumcraft/api/aspects/Aspect;") LegacyObject[] values();
        @LegacyMethod(name="getAmount", descriptor="(Lthaumcraft/api/aspects/Aspect;)I") int amount(LegacyObject aspect);
    }
    @LegacyType("thaumcraft.common.lib.crafting.ThaumcraftCraftingManager") public interface Crafting {
        @LegacyMethod(name="getObjectTags", descriptor="(Lnet/minecraft/item/ItemStack;)Lthaumcraft/api/aspects/AspectList;", isStatic=true) LegacyObject tags(LegacyObject stack);
        @LegacyMethod(name="getBonusTags", descriptor="(Lnet/minecraft/item/ItemStack;Lthaumcraft/api/aspects/AspectList;)Lthaumcraft/api/aspects/AspectList;", isStatic=true) LegacyObject bonus(LegacyObject stack, LegacyObject tags);
    }
    @LegacyType("thaumcraft.api.research.ScanResult") public interface Result {
        @LegacyConstructor(descriptor="(BIILnet/minecraft/entity/Entity;Ljava/lang/String;)V") LegacyObject create(byte type, int id, int meta, LegacyObject entity, String key);
        @LegacyField(name="phenomena", descriptor="Ljava/lang/String;") LegacyObject source();
    }
    @LegacyType("thaumcraft.common.lib.research.ScanManager") public interface Manager {
        @LegacyMethod(name="hasBeenScanned", descriptor="(Lnet/minecraft/entity/player/EntityPlayer;Lthaumcraft/api/research/ScanResult;)Z", isStatic=true) boolean scanned(LegacyObject player, LegacyObject scan);
        @LegacyMethod(name="validScan", descriptor="(Lthaumcraft/api/aspects/AspectList;Lnet/minecraft/entity/player/EntityPlayer;)Z", isStatic=true) boolean valid(LegacyObject aspects, LegacyObject player);
        @LegacyMethod(name="checkAndSyncAspectKnowledge", descriptor="(Lnet/minecraft/entity/player/EntityPlayer;Lthaumcraft/api/aspects/Aspect;I)I", isStatic=true) int award(LegacyObject player, LegacyObject aspect, int amount);
    }
    @LegacyType("thaumcraft.common.Thaumcraft") public interface Thaumcraft {
        @LegacyField(name="proxy", descriptor="Lthaumcraft/common/CommonProxy;", isStatic=true) LegacyObject proxy();
    }
    @LegacyType("thaumcraft.common.CommonProxy") public interface Proxy {
        @LegacyMethod(name="getResearchManager", descriptor="()Lthaumcraft/common/lib/research/ResearchManager;") LegacyObject research();
    }
    @LegacyType("thaumcraft.common.lib.research.ResearchManager") public interface Research {
        @LegacyMethod(name="completeScannedPhenomena", descriptor="(Lnet/minecraft/entity/player/EntityPlayer;Ljava/lang/String;)V") void mark(LegacyObject player, String key);
        @LegacyMethod(name="createClue", descriptor="(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;Ljava/lang/Object;Lthaumcraft/api/aspects/AspectList;)Z", isStatic=true) boolean clue(LegacyObject world, LegacyObject player, LegacyObject source, LegacyObject rewards);
    }
    @LegacyType("net.minecraft.entity.Entity") public interface Entity {
        @LegacyField(name="field_70170_p", descriptor="Lnet/minecraft/world/World;") LegacyObject world();
    }
}
