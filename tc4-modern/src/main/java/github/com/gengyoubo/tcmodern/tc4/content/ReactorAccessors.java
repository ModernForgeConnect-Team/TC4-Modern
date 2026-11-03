package github.com.gengyoubo.tcmodern.tc4.content;

import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.mla.*;

/** Public TC4 interfaces and the node's internal timers in 4.2.3.5. */
public final class ReactorAccessors {
    private ReactorAccessors() { }
    @LegacyType("net.minecraft.world.World") public interface World {
        @LegacyMethod(name="func_147438_o", descriptor="(III)Lnet/minecraft/tileentity/TileEntity;") LegacyObject tile(int x, int y, int z);
        @LegacyMethod(name="func_147471_g", descriptor="(III)V") void update(int x, int y, int z);
    }
    @LegacyType("thaumcraft.common.tiles.TileNode") public interface Node {
        @LegacyMethod(name="getId", descriptor="()Ljava/lang/String;") String id();
        @LegacyField(name="id", descriptor="Ljava/lang/String;") void id(String value);
        @LegacyField(name="count", descriptor="I") void count(int value);
        @LegacyField(name="regeneration", descriptor="I") void regeneration(int value);
        @LegacyField(name="catchUp", descriptor="Z") void catchUp(boolean value);
        @LegacyMethod(name="getAspects", descriptor="()Lthaumcraft/api/aspects/AspectList;") LegacyObject aspects();
        @LegacyMethod(name="func_70296_d", descriptor="()V") void dirty();
    }
    @LegacyType("thaumcraft.api.aspects.AspectList") public interface Aspects {
        @LegacyMethod(name="getAmount", descriptor="(Lthaumcraft/api/aspects/Aspect;)I") int amount(LegacyObject aspect);
        @LegacyMethod(name="add", descriptor="(Lthaumcraft/api/aspects/Aspect;I)Lthaumcraft/api/aspects/AspectList;") LegacyObject add(LegacyObject aspect, int amount);
    }
    @LegacyType("thaumcraft.common.tiles.TileAlembic") public interface Alembic {
        @LegacyField(name="aspect",descriptor="Lthaumcraft/api/aspects/Aspect;") LegacyObject aspect();
        @LegacyField(name="aspectFilter",descriptor="Lthaumcraft/api/aspects/Aspect;") LegacyObject filter();
        @LegacyField(name="amount",descriptor="I") int amount();
        @LegacyField(name="maxAmount",descriptor="I") int capacity();
        @LegacyMethod(name="addToContainer",descriptor="(Lthaumcraft/api/aspects/Aspect;I)I") int add(LegacyObject aspect,int amount);
    }
    @LegacyType("net.minecraftforge.common.util.ForgeDirection") public interface Directions {
        @LegacyMethod(name="getOrientation", descriptor="(I)Lnet/minecraftforge/common/util/ForgeDirection;", isStatic=true) LegacyObject of(int direction);
    }
    @LegacyType("thaumcraft.api.aspects.IEssentiaTransport") public interface Transport {
        @LegacyMethod(name="canOutputTo", descriptor="(Lnet/minecraftforge/common/util/ForgeDirection;)Z") boolean canOutput(LegacyObject side);
        @LegacyMethod(name="getEssentiaType", descriptor="(Lnet/minecraftforge/common/util/ForgeDirection;)Lthaumcraft/api/aspects/Aspect;") LegacyObject type(LegacyObject side);
        @LegacyMethod(name="getEssentiaAmount", descriptor="(Lnet/minecraftforge/common/util/ForgeDirection;)I") int amount(LegacyObject side);
        @LegacyMethod(name="takeEssentia", descriptor="(Lthaumcraft/api/aspects/Aspect;ILnet/minecraftforge/common/util/ForgeDirection;)I") int take(LegacyObject aspect, int amount, LegacyObject side);
        @LegacyMethod(name="canInputFrom", descriptor="(Lnet/minecraftforge/common/util/ForgeDirection;)Z") boolean canInput(LegacyObject side);
        @LegacyMethod(name="getSuctionAmount", descriptor="(Lnet/minecraftforge/common/util/ForgeDirection;)I") int suction(LegacyObject side);
        @LegacyMethod(name="getSuctionType", descriptor="(Lnet/minecraftforge/common/util/ForgeDirection;)Lthaumcraft/api/aspects/Aspect;") LegacyObject suctionType(LegacyObject side);
        @LegacyMethod(name="addEssentia", descriptor="(Lthaumcraft/api/aspects/Aspect;ILnet/minecraftforge/common/util/ForgeDirection;)I") int add(LegacyObject aspect, int amount, LegacyObject side);
    }
    @LegacyType("net.minecraft.tileentity.TileEntityFurnace") public interface FurnaceFuel {
        @LegacyMethod(name="func_145952_a", descriptor="(Lnet/minecraft/item/ItemStack;)I", isStatic=true) int burnTime(LegacyObject stack);
    }
}

