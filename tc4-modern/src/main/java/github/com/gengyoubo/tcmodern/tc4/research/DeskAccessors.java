package github.com.gengyoubo.tcmodern.tc4.research;

import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.mla.*;

/** TC4 4.2.3.5 research-note contracts used by the modern research desk. No legacy classes enter the modern classpath. */
public final class DeskAccessors {
    private DeskAccessors() {}
    public static final String NOTES = "thaumcraft.common.items.ItemResearchNotes";
    public static final String SCRIBE_TOOLS = "thaumcraft.api.IScribeTools";
    /** Original note metadata from which a sheet is a finished discovery. */
    public static final int DISCOVERY = 64;

    @LegacyType("net.minecraft.item.ItemStack") public interface StackView {
        @LegacyMethod(name="func_77973_b",descriptor="()Lnet/minecraft/item/Item;") LegacyObject item();
        @LegacyMethod(name="func_77960_j",descriptor="()I") int damage();
        @LegacyMethod(name="func_77958_k",descriptor="()I") int capacity();
        @LegacyMethod(name="func_77964_b",descriptor="(I)V") void setDamage(int damage);
    }
    @LegacyType("thaumcraft.common.lib.research.ResearchManager") public interface Notes {
        /** Null for a stack without note data. */
        @LegacyMethod(name="getData",isStatic=true,descriptor="(Lnet/minecraft/item/ItemStack;)Lthaumcraft/common/lib/research/ResearchNoteData;") LegacyObject read(LegacyObject stack);
        @LegacyMethod(name="updateData",isStatic=true,descriptor="(Lnet/minecraft/item/ItemStack;Lthaumcraft/common/lib/research/ResearchNoteData;)V") void write(LegacyObject stack,LegacyObject data);
        /** Marks the data complete and prunes unconnected hexes itself when every fixed hex is joined. */
        @LegacyMethod(name="checkResearchCompletion",isStatic=true,descriptor="(Lnet/minecraft/item/ItemStack;Lthaumcraft/common/lib/research/ResearchNoteData;Ljava/lang/String;)Z") boolean finish(LegacyObject stack,LegacyObject data,String player);
        @LegacyMethod(name="isResearchComplete",isStatic=true,descriptor="(Ljava/lang/String;Ljava/lang/String;)Z") boolean known(String player,String key);
        /** Null when no registered aspect combines the pair. */
        @LegacyMethod(name="getCombinationResult",isStatic=true,descriptor="(Lthaumcraft/api/aspects/Aspect;Lthaumcraft/api/aspects/Aspect;)Lthaumcraft/api/aspects/Aspect;") LegacyObject combine(LegacyObject first,LegacyObject second);
    }
    @LegacyType("thaumcraft.common.lib.research.ResearchNoteData") public interface Note {
        @LegacyField(name="key",descriptor="Ljava/lang/String;") String key();
        @LegacyField(name="complete",descriptor="Z") boolean complete();
        @LegacyField(name="copies",descriptor="I") int copies();
        @LegacyField(name="copies",descriptor="I") void copies(int copies);
        /** {@code "q:r"} to {@code HexEntry}; read with {@code LegacyValues#map}. */
        @LegacyField(name="hexEntries",descriptor="Ljava/util/HashMap;") LegacyObject entries();
        /** {@code "q:r"} to {@code HexUtils.Hex}; {@code updateData} writes only these keys. */
        @LegacyField(name="hexes",descriptor="Ljava/util/HashMap;") LegacyObject hexes();
    }
    @LegacyType("thaumcraft.common.lib.research.ResearchManager$HexEntry") public interface Entry {
        @LegacyField(name="aspect",descriptor="Lthaumcraft/api/aspects/Aspect;") LegacyObject aspect();
        /** 0 blank, 1 fixed by the note, 2 written by a player. */
        @LegacyField(name="type",descriptor="I") int type();
    }
    @LegacyType("thaumcraft.common.lib.research.ResearchManager$HexEntry") public interface EntryFactory {
        @LegacyConstructor(descriptor="(Lthaumcraft/api/aspects/Aspect;I)V") LegacyObject create(LegacyObject aspect,int type);
    }
    @LegacyType("thaumcraft.common.lib.utils.HexUtils$Hex") public interface HexFactory {
        @LegacyConstructor(descriptor="(II)V") LegacyObject create(int q,int r);
    }
    @LegacyType("thaumcraft.common.lib.research.PlayerKnowledge") public interface Discovered {
        /** Null for a player the original has not loaded yet. */
        @LegacyMethod(name="getAspectsDiscovered",descriptor="(Ljava/lang/String;)Lthaumcraft/api/aspects/AspectList;") LegacyObject of(String player);
    }
    @LegacyType("thaumcraft.api.research.ResearchItem") public interface ResearchItem {
        @LegacyField(name="tags",descriptor="Lthaumcraft/api/aspects/AspectList;") LegacyObject tags();
    }
}
