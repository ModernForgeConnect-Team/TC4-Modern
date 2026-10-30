package github.com.gengyoubo.tcmodern.tc4.client.research;

import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.mla.*;

/** TC4 4.2.3.5 data and original request contracts. No legacy classes enter the modern classpath. */
public final class ResearchTableAccessors {
    private ResearchTableAccessors() {}
    @LegacyType("thaumcraft.api.aspects.Aspect") public interface AspectText {
        @LegacyMethod(name="getName",descriptor="()Ljava/lang/String;") String name();
        @LegacyMethod(name="getLocalizedDescription",descriptor="()Ljava/lang/String;") String description();
    }
    @LegacyType("net.minecraft.item.ItemStack") public interface Ink {
        @LegacyMethod(name="func_77960_j",descriptor="()I") int damage();
        @LegacyMethod(name="func_77958_k",descriptor="()I") int capacity();
    }
    @LegacyType("thaumcraft.common.config.Config") public interface Options {
        @LegacyField(name="colorBlind",descriptor="Z",isStatic=true) boolean colorBlind();
    }
    @LegacyType("thaumcraft.client.gui.GuiResearchTable") public interface Gui {
        @LegacyField(name="tileEntity",descriptor="Lthaumcraft/common/tiles/TileResearchTable;") LegacyObject tile();
        @LegacyField(name="player",descriptor="Lnet/minecraft/entity/player/EntityPlayer;") LegacyObject player();
    }
    @LegacyType("thaumcraft.client.gui.GuiResearchTable") public interface Sounds {
        @LegacyMethod(name="playButtonClick",descriptor="()V") void button();
        @LegacyMethod(name="playButtonAspect",descriptor="()V") void aspect();
        @LegacyMethod(name="playButtonCombine",descriptor="()V") void combine();
        @LegacyMethod(name="playButtonWrite",descriptor="()V") void write();
        @LegacyMethod(name="playButtonErase",descriptor="()V") void erase();
        @LegacyMethod(name="playButtonScroll",descriptor="()V") void page();
    }
    @LegacyType("thaumcraft.client.gui.GuiResearchTable") public interface Flags {
        @LegacyField(name="RESEARCHER_1",descriptor="Z",isStatic=true) boolean researcher();
        @LegacyField(name="RESEARCHER_2",descriptor="Z",isStatic=true) boolean expert();
        @LegacyField(name="RESEARCHDUPE",descriptor="Z",isStatic=true) boolean duplicate();
    }
    @LegacyType("thaumcraft.common.tiles.TileResearchTable") public interface Table {
        @LegacyMethod(name="func_70301_a",descriptor="(I)Lnet/minecraft/item/ItemStack;") LegacyObject stack(int slot);
        @LegacyField(name="bonusAspects",descriptor="Lthaumcraft/api/aspects/AspectList;") LegacyObject bonus();
        @LegacyField(name="field_145851_c",descriptor="I") int x();
        @LegacyField(name="field_145848_d",descriptor="I") int y();
        @LegacyField(name="field_145849_e",descriptor="I") int z();
    }
    @LegacyType("thaumcraft.common.lib.research.PlayerKnowledge") public interface Knowledge {
        @LegacyMethod(name="getAspectsDiscovered",descriptor="(Ljava/lang/String;)Lthaumcraft/api/aspects/AspectList;") LegacyObject discovered(String name);
    }
    @LegacyType("thaumcraft.api.aspects.AspectList") public interface AspectList {
        @LegacyMethod(name="getAspectsSorted",descriptor="()[Lthaumcraft/api/aspects/Aspect;") LegacyObject[] sorted();
        @LegacyMethod(name="getAmount",descriptor="(Lthaumcraft/api/aspects/Aspect;)I") int amount(LegacyObject aspect);
    }
    @LegacyType("thaumcraft.common.lib.research.ResearchManager") public interface Research {
        @LegacyMethod(name="getData",descriptor="(Lnet/minecraft/item/ItemStack;)Lthaumcraft/common/lib/research/ResearchNoteData;",isStatic=true) LegacyObject note(LegacyObject stack);
        @LegacyMethod(name="isResearchComplete",descriptor="(Ljava/lang/String;Ljava/lang/String;)Z",isStatic=true) boolean complete(String name,String key);
    }
    @LegacyType("thaumcraft.common.lib.research.ResearchNoteData") public interface Note {
        @LegacyField(name="key",descriptor="Ljava/lang/String;") String key();
        @LegacyField(name="color",descriptor="I") int color();
        @LegacyField(name="complete",descriptor="Z") boolean complete();
        @LegacyField(name="copies",descriptor="I") int copies();
        @LegacyField(name="hexEntries",descriptor="Ljava/util/HashMap;") LegacyObject entries();
    }
    @LegacyType("thaumcraft.api.research.ResearchItem") public interface ResearchItem {
        @LegacyField(name="tags",descriptor="Lthaumcraft/api/aspects/AspectList;") LegacyObject tags();
    }
    @LegacyType("thaumcraft.common.lib.research.ResearchManager$HexEntry") public interface Entry {
        @LegacyField(name="aspect",descriptor="Lthaumcraft/api/aspects/Aspect;") LegacyObject aspect();
        @LegacyField(name="type",descriptor="I") int type();
    }
    @LegacyType("thaumcraft.common.lib.network.PacketHandler") public interface Network {
        @LegacyField(name="INSTANCE",descriptor="Lcpw/mods/fml/common/network/simpleimpl/SimpleNetworkWrapper;",isStatic=true) LegacyObject channel();
    }
    @LegacyType("thaumcraft.common.lib.network.playerdata.PacketAspectPlaceToServer") public interface Placement {
        @LegacyConstructor(descriptor="(Lnet/minecraft/entity/player/EntityPlayer;BBIIILthaumcraft/api/aspects/Aspect;)V")
        LegacyObject create(LegacyObject player,byte q,byte r,int x,int y,int z,LegacyObject aspect);
    }
    @LegacyType("thaumcraft.common.lib.network.playerdata.PacketAspectCombinationToServer") public interface Combination {
        @LegacyConstructor(descriptor="(Lnet/minecraft/entity/player/EntityPlayer;IIILthaumcraft/api/aspects/Aspect;Lthaumcraft/api/aspects/Aspect;ZZZ)V")
        LegacyObject create(LegacyObject player,int x,int y,int z,LegacyObject first,LegacyObject second,boolean firstBonus,boolean secondBonus,boolean discover);
    }
}
