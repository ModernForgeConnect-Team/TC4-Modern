package github.com.gengyoubo.mfcl.content;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Host persistence and synchronization envelope around one real old TileEntity per side and position. */
public final class LegacyBlockEntity extends BlockEntity {
    private final ModernContentBridge content;
    private Object legacy;
    private java.util.List<github.com.gengyoubo.mfcl.bridge.LegacyClientData.Stack> removedInventory=java.util.List.of();
    private volatile byte[] saved = new byte[0];
    public byte[] renderData() { return saved.clone(); }
    private String savedBlock;
    private boolean updatePending, logicSuspended;
    private long successfulTicks;
    private final java.util.Map<net.minecraft.core.Direction,net.minecraftforge.common.util.LazyOptional<net.minecraftforge.items.IItemHandler>> inventories = new java.util.HashMap<>();
    private final java.util.Map<net.minecraft.core.Direction,net.minecraftforge.common.util.LazyOptional<net.minecraftforge.energy.IEnergyStorage>> energy = new java.util.HashMap<>();
    private final java.util.Map<net.minecraft.core.Direction,net.minecraftforge.common.util.LazyOptional<net.minecraftforge.fluids.capability.IFluidHandler>> fluids = new java.util.HashMap<>();
    public ModernContentBridge content() { return content; }
    public boolean owns(Object original) { return legacy == original; }
    public boolean logicSuspended() { return logicSuspended; }
    public long successfulTicks() { return successfulTicks; }
    public LegacyBlockEntity(ModernContentBridge content, BlockPos position, BlockState state) {
        super(content.tileType(), position, state); this.content = content;
    }
    private String key() { return content.legacyBlock(getBlockState()).legacyKey(); }
    public Object legacy() {
        if (level == null || isRemoved()) throw new IllegalStateException("Legacy tile has no live host level");
        try {
            Object world = content.services().worlds().bound(level);
            if (legacy == null) {
                if (savedBlock != null && !savedBlock.equals(key())) throw new IllegalStateException("Saved legacy tile belongs to " + savedBlock + ", not " + key());
                legacy = saved.length == 0 ? content.services().tiles().create(key(), getBlockState().getValue(ModernContentBridge.METADATA), world, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ())
                        : content.services().tiles().snapshotInstance(null, saved);
                if (legacy != null && saved.length != 0) {
                    content.services().tiles().attach(legacy, key(), getBlockState().getValue(ModernContentBridge.METADATA), world, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
                    content.services().tiles().load(legacy, saved);
                    content.services().tiles().attach(legacy, key(), getBlockState().getValue(ModernContentBridge.METADATA), world, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
                }
            }
            return legacy;
        } catch (Exception failure) { legacy = null; throw new IllegalStateException("Cannot create/load original TileEntity " + key() + " at " + worldPosition, failure); }
    }
    public Object legacyOrNull() {
        var entry = content.declarations().blocks().stream().filter(block -> block.key().equals(key())).findFirst().orElse(null);
        if (entry == null || !entry.hasTile(getBlockState().getValue(ModernContentBridge.METADATA))) return null;
        return legacy();
    }
    /** Native loot is evaluated after setRemoved; never revive the invalidated original tile for its inventory. */
    public java.util.List<github.com.gengyoubo.mfcl.bridge.LegacyClientData.Stack> inventoryDrops() throws Exception {
        return isRemoved()?removedInventory:content.services().tiles().inventory(legacyOrNull());
    }
    public void replace(Object value) {
        try {
            Object world = content.services().worlds().bound(level);
            content.services().tiles().attach(value, key(), getBlockState().getValue(ModernContentBridge.METADATA), world, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
            legacy = value; saved = new byte[0]; setChanged();
        } catch (Exception error) { throw new IllegalStateException("Cannot replace old TileEntity", error); }
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        saved = tag.getByteArray("mfcl_legacy_nbt"); savedBlock = tag.contains("mfcl_legacy_block") ? tag.getString("mfcl_legacy_block") : null;
        if(legacy!=null) {
            if(level!=null&&level.isClientSide&&!isRemoved()&&saved.length!=0&&(savedBlock==null||savedBlock.equals(key())))try {
                // Retain open GUI references for same-type updates, but honor authoritative subtype changes.
                Object updated = content.services().tiles().snapshotInstance(legacy,saved);
                if(updated!=legacy) { invalidateLegacy(); legacy=updated; }
                content.services().tiles().attach(legacy,key(),getBlockState().getValue(ModernContentBridge.METADATA),content.services().worlds().bound(level),worldPosition.getX(),worldPosition.getY(),worldPosition.getZ());
                content.services().tiles().load(legacy,saved);
                content.services().tiles().attach(legacy,key(),getBlockState().getValue(ModernContentBridge.METADATA),content.services().worlds().bound(level),worldPosition.getX(),worldPosition.getY(),worldPosition.getZ());
            }catch(Exception failure){throw new IllegalStateException("Cannot update original client TileEntity",failure);}
            else invalidateLegacy();
        }
        if (level != null && level.isClientSide) {
            requestModelDataUpdate(); level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        try { if (legacy != null) saved = content.services().tiles().save(legacy); }
        catch (Exception error) { throw new IllegalStateException("Cannot save original TileEntity " + key(), error); }
        tag.putString("mfcl_legacy_block", key()); tag.putByteArray("mfcl_legacy_nbt", saved);
    }
    private Object boundsOwner; private long boundsTime; private net.minecraft.world.phys.AABB bounds;
    /**
     * Host culling uses this box; the default is the cell's own collision box. Old TESRs that draw across several
     * cells (TC4 research table, infusion pillars) declare their extent here, so the model must not vanish with its anchor.
     */
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        if (level == null || !level.isClientSide || isRemoved()) return super.getRenderBoundingBox();
        var cell = content.legacyBlock(getBlockState()); var rendering = content.blockRendering();
        if (rendering == null || !rendering.hasTileRenderer(cell.legacyKey(), cell.metadata())) return super.getRenderBoundingBox();
        try {
            Object tile = legacyOrNull();
            if (tile == null) return super.getRenderBoundingBox();
            // Some tiles grow their box with state (stacked jars, active altars); refresh about once a second.
            if (tile != boundsOwner || bounds == null || level.getGameTime() - boundsTime >= 20) {
                double[] old = content.services().tiles().renderBounds(tile);
                bounds = old == null ? INFINITE_EXTENT_AABB : new net.minecraft.world.phys.AABB(old[0], old[1], old[2], old[3], old[4], old[5]);
                boundsOwner = tile; boundsTime = level.getGameTime();
            }
            return bounds;
        } catch (Exception | LinkageError failure) { return super.getRenderBoundingBox(); }
    }
    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    public void synchronize() {
        captureChanges(true);
    }
    public void captureChanges(boolean immediate) {
        try {
            if (legacy != null) {
                byte[] next = content.services().tiles().save(legacy);
                if (!java.util.Arrays.equals(next,saved)) { saved = next; updatePending = true; setChanged(); }
            }
        }
        catch (Exception error) { throw new IllegalStateException("Cannot snapshot legacy tile for rendering", error); }
        if (updatePending && level != null && !level.isClientSide && (immediate || level.getGameTime()%5 == 0)) {
            updatePending = false; level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }
    public void markLegacyDirty() { setChanged(); updatePending = true; }
    private static final java.util.Set<String> clientFailures = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** key@metadata of original tiles whose logic failed and was paused this session; the item tooltip reports these. */
    private static final java.util.Set<String> pausedLogic = java.util.concurrent.ConcurrentHashMap.newKeySet();
    public static boolean logicPaused(String key, int metadata) { return pausedLogic.contains(key + "@" + metadata); }
    static void logicFailed(String key, int metadata) { pausedLogic.add(key + "@" + metadata); }
    /**
     * The original updateEntity also runs on the client, where machines draw their effects (crucible bubbles and
     * froth...). It sees the client world only; a tile whose client logic fails is paused locally, the server is unaffected.
     */
    public void tickClient() {
        if (logicSuspended || isRemoved() || level == null || !level.isClientSide) return;
        try { content.services().logic().tick(legacyOrNull()); }
        catch (Exception | LinkageError failure) {
            logicSuspended = true;
            logicFailed(key(), getBlockState().getValue(ModernContentBridge.METADATA));
            if (clientFailures.add(key())) com.mojang.logging.LogUtils.getLogger().warn("MFCL paused client-side original tile logic for {}", key(), failure);
        }
    }
    public void tickServer() {
        if (logicSuspended || isRemoved() || level == null || level.isClientSide) return;
        try {
            boolean ticked;
            // Old tiles message nearby players directly (eldritch lock); the actors have no connection, so deliver here.
            try (var chat = new github.com.gengyoubo.mfcl.bridge.LegacyBlockLogic.AddressedChatCapture()) {
                ticked = LegacyTilePlayers.tick(content,(net.minecraft.server.level.ServerLevel)level,worldPosition,legacyOrNull());
                for (var line : chat.lines()) {
                    var id = content.services().entityId(line.recipient());
                    var player = id == null ? null : level.getPlayerByUUID(id);
                    if (player != null) player.sendSystemMessage(net.minecraft.network.chat.Component.literal(line.text()));
                }
            }
            if (ticked) successfulTicks++;
        } catch (Exception | LinkageError failure) {
            logicSuspended = true;
            logicFailed(key(), getBlockState().getValue(ModernContentBridge.METADATA));
            github.com.gengyoubo.mfcl.bridge.LegacyDiagnostics.legacyFailure("tile tick " + key(), failure);
            if (level.getBlockEntity(worldPosition) instanceof LegacyBlockEntity current) current.logicSuspended = true;
            com.mojang.logging.LogUtils.getLogger().error("MFCL paused original tile logic for {} at {} after rollback",key(),worldPosition,failure);
        }
    }
    @Override public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(net.minecraftforge.common.capabilities.Capability<T> capability, net.minecraft.core.Direction side) {
        if(capability==net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY&&level!=null&&!isRemoved()) {
            try {
                var access=content.services().energy();
                if(access.connected(legacyOrNull(),side==null?null:side.get3DDataValue()))return energy.computeIfAbsent(side,key->
                        net.minecraftforge.common.util.LazyOptional.of(()->new LegacyEnergyHandler(this,key))).cast();
            }catch(Exception failure){throw new IllegalStateException("Cannot expose original RF energy",failure);}
        }
        if(capability==net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER&&level!=null&&!isRemoved()) {
            try {
                if(content.services().fluids().supports(legacyOrNull()))return fluids.computeIfAbsent(side,key->
                        net.minecraftforge.common.util.LazyOptional.of(()->new LegacyFluidHandler(this,key))).cast();
            }catch(Exception failure){throw new IllegalStateException("Cannot expose original fluid tanks",failure);}
        }
        if (capability == net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER && level != null && !isRemoved()) {
            try {
                if (content.services().inventories().supports(legacyOrNull())) return inventories.computeIfAbsent(side,key ->
                        net.minecraftforge.common.util.LazyOptional.of(() -> new LegacyInventoryHandler(this,key))).cast();
            } catch (Exception failure) { throw new IllegalStateException("Cannot expose original inventory",failure); }
        }
        return super.getCapability(capability,side);
    }
    @Override public void invalidateCaps() {
        super.invalidateCaps(); inventories.values().forEach(net.minecraftforge.common.util.LazyOptional::invalidate); inventories.clear();
        energy.values().forEach(net.minecraftforge.common.util.LazyOptional::invalidate);energy.clear();
        fluids.values().forEach(net.minecraftforge.common.util.LazyOptional::invalidate);fluids.clear();
    }
    @Override public void setBlockState(BlockState state) {
        super.setBlockState(state);
        if (legacy != null) try { content.services().tiles().metadata(legacy, state.getValue(ModernContentBridge.METADATA)); }
        catch (Exception error) { throw new IllegalStateException("Cannot synchronize old tile metadata", error); }
    }
    private void invalidateLegacy() {
        try { content.services().tiles().invalidate(legacy); }
        catch (Exception error) { throw new IllegalStateException("Cannot invalidate original TileEntity " + key(), error); }
        finally { legacy = null; }
    }
    @Override public void setRemoved() {
        // A saved inventory may not have been materialized yet (break immediately after loading a chunk).
        if(legacy==null&&saved.length>0&&level!=null&&!isRemoved())legacyOrNull();
        if (legacy != null) {
            try { removedInventory=content.services().tiles().inventory(legacy); saved = content.services().tiles().save(legacy); savedBlock = key(); }
            catch (Exception error) { throw new IllegalStateException("Cannot retain old tile data during removal", error); }
            invalidateLegacy();
        }
        super.setRemoved();
    }
}
