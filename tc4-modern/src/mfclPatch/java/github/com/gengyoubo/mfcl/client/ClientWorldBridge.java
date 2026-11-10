package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.bridge.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import java.util.*;

/** Binds the actual host level to a typed old client world. Client bindings never mutate server authority. */
public final class ClientWorldBridge {
    private static ClientWorldBridge active;
    private final LegacyServices services;
    private Level current;
    private ClientWorldBridge(LegacyServices services) { this.services = services; }
    public static void install(LegacyServices services) {
        var bridge = new ClientWorldBridge(services); active=bridge;MinecraftForge.EVENT_BUS.addListener(bridge::tick);
    }
    /** A loaded level can render before the first client tick, especially after reopening a save. */
    static Object world(LegacyServices services) throws Exception {
        if(active==null||active.services!=services)throw new IllegalStateException("Client world bridge is not installed");
        return services.items().inLegacy(()->{active.refresh();return services.worlds().bound(active.current);});
    }
    private void tick(TickEvent.ClientTickEvent event) {
        try {services.items().inLegacy(()->{
            refresh();
            if(current!=null&&!Minecraft.getInstance().isPaused())services.worlds().tick(current,event.phase==TickEvent.Phase.START);
            return null;
        });}
        catch(Exception failure){throw new IllegalStateException("Cannot refresh legacy client world",failure);}
    }
    private void refresh() {
        Level next = Minecraft.getInstance().level;
        if (next == current) return;
        try {
            if (current != null) {services.worlds().unloaded(current);services.worlds().unbind(current);}
            current = null;
            Object oldWorld = null;
            if (next != null) {
                String dimension = next.dimension().location().toString();
                int oldDimension = services.dimensions().legacyId(dimension);
                oldWorld = services.worlds().bind(next, new HostWorldAccess() {
                    @Override public String dimensionKey() { return dimension; }
                    @Override public int legacyDimensionId() { return oldDimension; }
                    @Override public boolean clientSide() { return true; }
                    @Override public int difficulty() { return next.getDifficulty().getId(); }
                    @Override public Food food(java.util.UUID id) {
                        var player=next.getPlayerByUUID(id);if(player==null)return null;
                        var food=player.getFoodData();return new Food(food.getFoodLevel(),food.getSaturationLevel());
                    }
                    @Override public String biome(int x,int z) {return github.com.gengyoubo.mfcl.content.HostBiomeQueries.key(next,x,z);}
                    @Override public void playSound(Sound sound) {
                        if(sound.excludedPlayer()!=null&&Minecraft.getInstance().player!=null&&sound.excludedPlayer().equals(Minecraft.getInstance().player.getUUID()))return;
                        var id=net.minecraft.resources.ResourceLocation.parse(LegacySounds.modernId(sound.id()));
                        next.playLocalSound(sound.x(),sound.y(),sound.z(),net.minecraft.sounds.SoundEvent.createVariableRangeEvent(id),net.minecraft.sounds.SoundSource.MASTER,sound.volume(),sound.pitch(),sound.distanceDelay());
                    }
                    @Override public long gameTime() { return next.getGameTime(); }
                    @Override public void explosionEffect(double x,double y,double z) {
                        next.addParticle(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER,x,y,z,0,0,0);
                    }
                    @Override public void particle(String name,double x,double y,double z,double vx,double vy,double vz) {
                        LegacyParticles.spawn(next,name,x,y,z,vx,vy,vz);
                    }
                    @Override public void levelEvent(java.util.UUID excluded,int event,int x,int y,int z,int data) {
                        if(excluded!=null&&Minecraft.getInstance().player!=null&&excluded.equals(Minecraft.getInstance().player.getUUID()))return;
                        next.levelEvent(event,new BlockPos(x,y,z),data);
                    }
                    @Override public java.util.List<HostWorldAccess.Entity> entities(double a,double b,double c,double d,double e,double f) {return github.com.gengyoubo.mfcl.content.HostEntityQueries.query(next,a,b,c,d,e,f);}
                    @Override public long dayTime() { return next.getDayTime(); }
                    @Override public boolean powered(int x, int y, int z) { return next.hasNeighborSignal(new BlockPos(x,y,z)); }
                    @Override public int power(int x, int y, int z, int side) { return next.getSignal(new BlockPos(x,y,z),net.minecraft.core.Direction.from3DDataValue(side)); }
                    @Override public boolean loaded(int x, int y, int z) { return next.isInWorldBounds(new BlockPos(x,y,z)) && next.hasChunkAt(new BlockPos(x,y,z)); }
                    @Override public void setDayTime(long time) { throw new UnsupportedOperationException("Client world cannot change authoritative time"); }
                    @Override public Block block(int x, int y, int z) {
                        var state = next.getBlockState(new BlockPos(x, y, z));
                        var content = github.com.gengyoubo.mfcl.content.ModernContentBridge.active();
                        if (content != null) {
                            // Post-1.7 blocks (tuff, deepslate...) read as stand-ins like on the server, so TESRs that
                            // look at neighbours (item shadows on the deconstruction table) keep rendering.
                            var known = content.hostRepresentations().knownOpaque(state);
                            if (known != null) return known;
                            try { return content.legacyBlock(state); }
                            catch (UnmappedLegacyBlockException absent) { return content.hostRepresentations().opaque(state); }
                        }
                        if (!state.equals(state.getBlock().defaultBlockState())) throw new UnsupportedOperationException("Block state requires a legacy metadata mapping: " + state);
                        String key = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                        if (key.equals("minecraft:grass_block")) key = "minecraft:grass";
                        return new Block(key, 0);
                    }
                    @Override public boolean setBlock(int x, int y, int z, Block block, int flags) { throw new LegacyClientMutationException("Client world cannot change authoritative blocks"); }
                    @Override public java.util.List<HostWorldAccess.Entity> players() {
                        return next.players().stream().map(player -> github.com.gengyoubo.mfcl.content.HostEntityQueries.snapshot(next, player)).filter(java.util.Objects::nonNull).toList();
                    }
                    @Override public int light(int x, int y, int z) { return next.getMaxLocalRawBrightness(new BlockPos(x,y,z)); }
                    @Override public int height(int x, int z) { return next.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z); }
                    @Override public int minBuildHeight() { return next.getMinBuildHeight(); }
                    @Override public int maxBuildHeight() { return next.getMaxBuildHeight(); }
                    @Override public boolean canSeeSky(int x, int y, int z) { return next.canSeeSky(new BlockPos(x,y,z)); }
                    @Override public HostWorldAccess.Hit rayTrace(double sx, double sy, double sz, double ex, double ey, double ez) {
                        var from = new net.minecraft.world.phys.Vec3(sx, sy, sz); var to = new net.minecraft.world.phys.Vec3(ex, ey, ez);
                        var result = next.clip(new net.minecraft.world.level.ClipContext(from, to, net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, null));
                        if (result.getType() == net.minecraft.world.phys.HitResult.Type.MISS) return null;
                        var pos = result.getBlockPos();
                        return new HostWorldAccess.Hit(pos.getX(), pos.getY(), pos.getZ(), result.getDirection().get3DDataValue(), result.getLocation().x, result.getLocation().y, result.getLocation().z);
                    }
                    @Override public Integer statistic(java.util.UUID id,String originalId){var player=Minecraft.getInstance().player;var content=github.com.gengyoubo.mfcl.content.ModernContentBridge.active();var stat=content==null?null:content.statistics().statistic(originalId);return player==null||stat==null||!player.getUUID().equals(id)?null:player.getStats().getValue(stat);}
                    @Override public java.util.List<LegacyPlayerBody.Effect> effects(java.util.UUID id){
                        var player=next.getPlayerByUUID(id);var content=github.com.gengyoubo.mfcl.content.ModernContentBridge.active();
                        return player==null||content==null?java.util.List.of():content.potions().snapshot(player);
                    }
                    @Override public boolean originalBlockRayTrace() { return true; }
                    @Override public HostWorldAccess.Entity entity(java.util.UUID id){var player=next.getPlayerByUUID(id);return player==null?null:github.com.gengyoubo.mfcl.content.HostEntityQueries.snapshot(next,player);}
                    @Override public java.util.List<int[]> legacyTiles(int chunkX, int chunkZ) {
                        return github.com.gengyoubo.mfcl.content.LevelWorldBridge.legacyTiles(next, chunkX, chunkZ);
                    }
                    @Override public Object tileEntity(int x, int y, int z) {
                        var tile = next.getBlockEntity(new BlockPos(x, y, z));
                        // A host block entity without an old counterpart is simply invisible to legacy code.
                        if (tile instanceof github.com.gengyoubo.mfcl.content.LegacyBlockEntity legacy) return legacy.legacy();
                        return null;
                    }
                    @Override public void blockEvent(int x,int y,int z,String key,int event,int data) {
                        // Vanilla client World applies these to the local block/tile, including chest lid counts.
                        try {services.logic().event(key,services.worlds().bound(next),x,y,z,event,data);}
                        catch(Exception failure){throw new IllegalStateException("Cannot apply original client block event",failure);}
                    }
                    @Override public void markTileDirty(int x, int y, int z, Object old) {
                        // readCustomNBT may mark a client tile dirty while its host envelope is still loading.
                        // This updates the local model only; it must not request server mutation or recurse into legacy().
                        if (next.getBlockEntity(new BlockPos(x,y,z)) instanceof github.com.gengyoubo.mfcl.content.LegacyBlockEntity tile) {
                            tile.setChanged(); tile.requestModelDataUpdate();
                        }
                    }
                    @Override public void blockUpdated(int x, int y, int z) {
                        var pos = new BlockPos(x,y,z); var state = next.getBlockState(pos);
                        next.sendBlockUpdated(pos,state,state,3);
                    }
                });
                current = next;
            }
            var mc = services.loader().loadClass("net.minecraft.client.Minecraft");
            mc.getField("field_71441_e").set(mc.getMethod("func_71410_x").invoke(null), oldWorld);
            LegacyEffects.bind(services, oldWorld);
        } catch (Exception failure) { throw new IllegalStateException("Cannot bind legacy client world", failure); }
    }
}

