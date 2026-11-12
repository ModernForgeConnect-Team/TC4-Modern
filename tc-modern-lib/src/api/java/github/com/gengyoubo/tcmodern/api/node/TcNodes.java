package github.com.gengyoubo.tcmodern.api.node;

import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.mla.LegacyMethod;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;
import github.com.gengyoubo.tcmodern.api.essentia.TcContainers;
import java.util.Map;
import java.util.Optional;

/** Read an original INode from a scoped legacy tile handle on its owning game thread. */
public final class TcNodes {
    private TcNodes() { }
    public record Snapshot(String id, String type, String modifier, Map<String,Integer> current, Map<String,Integer> base) {
        public Snapshot { current = Map.copyOf(current); base = Map.copyOf(base); }
    }
    @LegacyType("thaumcraft.api.nodes.INode") public interface Node {
        @LegacyMethod(name="getId", descriptor="()Ljava/lang/String;") String id();
        @LegacyMethod(name="getNodeType", descriptor="()Lthaumcraft/api/nodes/NodeType;") LegacyObject type();
        @LegacyMethod(name="getNodeModifier", descriptor="()Lthaumcraft/api/nodes/NodeModifier;") LegacyObject modifier();
        @LegacyMethod(name="getAspects", descriptor="()Lthaumcraft/api/aspects/AspectList;") LegacyObject current();
        @LegacyMethod(name="getAspectsBase", descriptor="()Lthaumcraft/api/aspects/AspectList;") LegacyObject base();
    }
    @LegacyType("thaumcraft.api.nodes.NodeType") public interface Type {
        @LegacyMethod(name="name", descriptor="()Ljava/lang/String;") String name();
    }
    @LegacyType("thaumcraft.api.nodes.NodeModifier") public interface Modifier {
        @LegacyMethod(name="name", descriptor="()Ljava/lang/String;") String name();
    }
    /** Empty optional indicates that the tile does not implement INode; modifier may be null. */
    public static Optional<Snapshot> snapshot(LegacyRuntime runtime, LegacyObject tile) {
        if (tile == null || !runtime.values().isInstance(tile, "thaumcraft.api.nodes.INode")) return Optional.empty();
        var node = runtime.accessor(Node.class, tile);
        var type = node.type(); var modifier = node.modifier();
        return Optional.of(new Snapshot(node.id(), type == null ? null : runtime.accessor(Type.class, type).name(),
                modifier == null ? null : runtime.accessor(Modifier.class, modifier).name(),
                TcContainers.aspects(runtime, node.current()), TcContainers.aspects(runtime, node.base())));
    }
}
