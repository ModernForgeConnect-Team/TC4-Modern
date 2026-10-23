package github.com.gengyoubo.tcmodern.tc4.client;

import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.mla.LegacyConstructor;
import github.com.gengyoubo.mfcl.api.mla.LegacyField;
import github.com.gengyoubo.mfcl.api.mla.LegacyMethod;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;

/** Exact public contracts of TC4 4.2.3.5 that form multiblocks. No old classes are linked. */
public final class FormedAccessors {
    private FormedAccessors() { }

    @LegacyType("net.minecraft.world.World")
    public interface World {
        @LegacyMethod(name = "func_147438_o", descriptor = "(III)Lnet/minecraft/tileentity/TileEntity;") LegacyObject tile(int x, int y, int z);
    }

    /** The checks and replacements WandManager runs after charging the wand; each takes the structure's origin cell. */
    @LegacyType("thaumcraft.common.items.wands.WandManager")
    public interface Wand {
        @LegacyMethod(name = "fitArcaneFurnace", descriptor = "(Lnet/minecraft/world/World;III)Z", isStatic = true)
        boolean fitsInfernalFurnace(LegacyObject world, int x, int y, int z);
        @LegacyMethod(name = "replaceArcaneFurnace", descriptor = "(Lnet/minecraft/world/World;III)Z", isStatic = true)
        boolean formInfernalFurnace(LegacyObject world, int x, int y, int z);
        @LegacyMethod(name = "fitInfusionAltar", descriptor = "(Lnet/minecraft/world/World;III)Z", isStatic = true)
        boolean fitsInfusionAltar(LegacyObject world, int x, int y, int z);
        @LegacyMethod(name = "replaceInfusionAltar", descriptor = "(Lnet/minecraft/world/World;III)V", isStatic = true)
        void formInfusionAltar(LegacyObject world, int x, int y, int z);
        @LegacyMethod(name = "fitNodeJar", descriptor = "(Lnet/minecraft/world/World;III)Z", isStatic = true)
        boolean fitsNodeJar(LegacyObject world, int x, int y, int z);
        @LegacyMethod(name = "replaceNodeJar", descriptor = "(Lnet/minecraft/world/World;III)V", isStatic = true)
        void formNodeJar(LegacyObject world, int x, int y, int z);
    }

    @LegacyType("thaumcraft.common.tiles.TileThaumatorium")
    public interface Thaumatorium {
        /** The face the clicked side gave it: the output side. */
        @LegacyField(name = "facing", descriptor = "Lnet/minecraftforge/common/util/ForgeDirection;") void facing(LegacyObject direction);
    }

    @LegacyType("net.minecraftforge.common.util.ForgeDirection")
    public interface Directions {
        @LegacyMethod(name = "getOrientation", descriptor = "(I)Lnet/minecraftforge/common/util/ForgeDirection;", isStatic = true)
        LegacyObject of(int id);
    }

    @LegacyType("thaumcraft.api.nodes.INode")
    public interface Node {
        @LegacyMethod(name = "setAspects", descriptor = "(Lthaumcraft/api/aspects/AspectList;)V") void aspects(LegacyObject aspects);
    }

    @LegacyType("thaumcraft.api.aspects.AspectList")
    public interface AspectsFactory {
        @LegacyConstructor(descriptor = "()V") LegacyObject create();
    }

    @LegacyType("thaumcraft.api.aspects.AspectList")
    public interface Aspects {
        @LegacyMethod(name = "add", descriptor = "(Lthaumcraft/api/aspects/Aspect;I)Lthaumcraft/api/aspects/AspectList;") LegacyObject add(LegacyObject aspect, int amount);
    }
}
