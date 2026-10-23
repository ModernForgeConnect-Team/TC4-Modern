package github.com.gengyoubo.tcmodern.api.research;

import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.mla.LegacyField;
import github.com.gengyoubo.mfcl.api.mla.LegacyMethod;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;

/** Exact public contracts of TC4 4.2.3.5 research. No old classes are linked. */
public final class TcResearchAccessors {
    private TcResearchAccessors() { }

    // Factories and static lookups must not share a receiver-less contract with instance members.
    @LegacyType("thaumcraft.api.research.ResearchCategories")
    public interface Categories {
        /** Every category by key, in registration order; read with {@code LegacyValues#map}. */
        @LegacyField(name = "researchCategories", descriptor = "Ljava/util/LinkedHashMap;", isStatic = true) LegacyObject categories();
        /** Null when no category holds that key. */
        @LegacyMethod(name = "getResearch", descriptor = "(Ljava/lang/String;)Lthaumcraft/api/research/ResearchItem;", isStatic = true)
        LegacyObject find(String key);
    }

    @LegacyType("thaumcraft.api.research.ResearchCategoryList")
    public interface Category {
        /** The category's research by key; read with {@code LegacyValues#map}. */
        @LegacyField(name = "research", descriptor = "Ljava/util/Map;") LegacyObject research();
    }

    @LegacyType("thaumcraft.api.research.ResearchItem")
    public interface Item {
        @LegacyField(name = "key", descriptor = "Ljava/lang/String;") String key();
        /** Null when there are none; the same holds for the other requisite arrays. */
        @LegacyField(name = "parents", descriptor = "[Ljava/lang/String;") String[] parents();
        @LegacyField(name = "parentsHidden", descriptor = "[Ljava/lang/String;") String[] parentsHidden();
        /** Research the original unlocks together with this one. */
        @LegacyField(name = "siblings", descriptor = "[Ljava/lang/String;") String[] siblings();
    }

    @LegacyType("thaumcraft.common.Thaumcraft")
    public interface Thaumcraft {
        @LegacyField(name = "proxy", descriptor = "Lthaumcraft/common/CommonProxy;", isStatic = true) LegacyObject proxy();
    }

    @LegacyType("thaumcraft.common.CommonProxy")
    public interface Proxy {
        @LegacyMethod(name = "getResearchManager", descriptor = "()Lthaumcraft/common/lib/research/ResearchManager;") LegacyObject manager();
    }

    @LegacyType("thaumcraft.common.lib.research.ResearchManager")
    public interface Progress {
        @LegacyMethod(name = "isResearchComplete", descriptor = "(Ljava/lang/String;Ljava/lang/String;)Z", isStatic = true)
        boolean complete(String player, String key);
    }

    /** Completing applies the research's warp and schedules the save, exactly as finishing a research note does. */
    @LegacyType("thaumcraft.common.lib.research.ResearchManager")
    public interface Manager {
        @LegacyMethod(name = "completeResearch", descriptor = "(Lnet/minecraft/entity/player/EntityPlayer;Ljava/lang/String;)V")
        void complete(LegacyObject player, String key);
    }

    @LegacyType("net.minecraft.entity.player.EntityPlayer")
    public interface Player {
        @LegacyMethod(name = "func_70005_c_", descriptor = "()Ljava/lang/String;") String name();
    }
}
