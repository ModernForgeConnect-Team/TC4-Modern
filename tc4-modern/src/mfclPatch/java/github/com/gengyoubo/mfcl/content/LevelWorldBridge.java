package github.com.gengyoubo.mfcl.content;

import github.com.gengyoubo.mfcl.bridge.HostWorldAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.LevelEvent;
import java.util.*;

/** Server levels own all authoritative mutations; the old World façade keeps its owner-thread checks. */
public final class LevelWorldBridge {
    private static final net.minecraft.server.level.TicketType<java.util.UUID> LEGACY_TICKET=net.minecraft.server.level.TicketType.create("mfcl_legacy",java.util.Comparator.<java.util.UUID>naturalOrder());
    private final ModernContentBridge content;
    private final LegacyDimensionHost dimensions;
    /**
     * Old scheduled updates on host vanilla blocks. 1.7.10 ran BlockCrops/BlockSapling growth from updateTick for both
     * scheduled and random ticks, so the lamp of growth schedules crops; the host only grows them in randomTick.
     */
    private final Map<ServerLevel, Map<BlockPos, Long>> hostTicks = new WeakHashMap<>();
    private LevelWorldBridge(ModernContentBridge content) { this.content = content; this.dimensions = new LegacyDimensionHost(content); }
    /** Original tiles of a loaded host chunk, for old chunkTileEntityMap scans; unloaded chunks report none. */
    public static List<int[]> legacyTiles(net.minecraft.world.level.Level level, int chunkX, int chunkZ) {
        if (!level.hasChunk(chunkX, chunkZ)) return List.of();
        var result = new ArrayList<int[]>();
        for (var entry : level.getChunk(chunkX, chunkZ).getBlockEntities().entrySet())
            if (entry.getValue() instanceof LegacyBlockEntity tile && !tile.isRemoved()) { var pos = entry.getKey(); result.add(new int[]{pos.getX(), pos.getY(), pos.getZ()}); }
        return result;
    }
    public static void install(ModernContentBridge content) {
        var bridge = new LevelWorldBridge(content);
        MinecraftForge.EVENT_BUS.addListener(bridge::load); MinecraftForge.EVENT_BUS.addListener(bridge::unload);
        MinecraftForge.EVENT_BUS.addListener(bridge::tick);
    }
    private void load(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        String key = level.dimension().location().toString();
        int dimension = content.services().dimensions().legacyId(key);
        try { content.services().worlds().bind(level, access(level, dimension)); }
        catch (Exception error) { throw new IllegalStateException("Cannot bind legacy server world " + key, error); }
        dimensions.loaded(level);
    }
    private void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            dimensions.unloading(level);
            // Other mods may unload levels that never loaded: Moonlight's FakeServerLevel only ever posts Unload.
            if (content.services().worlds().findBound(level) == null) return;
            try {content.services().worlds().unloaded(level);}
            catch(Exception failure){throw new IllegalStateException("Cannot unload original world",failure);}
            content.services().worlds().unbind(level); hostTicks.remove(level);
        }
    }
    private void tick(net.minecraftforge.event.TickEvent.LevelTickEvent event) {
        if (!(event.level instanceof ServerLevel level)) return;
        try {content.services().worlds().tick(level,event.phase==net.minecraftforge.event.TickEvent.Phase.START);}
        catch(Exception failure){throw new IllegalStateException("Cannot tick original world "+level.dimension().location(),failure);}
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        var pending = hostTicks.get(level); if (pending == null || pending.isEmpty()) return;
        long now = level.getGameTime(); var due = new ArrayList<BlockPos>();
        pending.entrySet().removeIf(entry -> { if (entry.getValue() > now) return false; due.add(entry.getKey()); return true; });
        for (var pos : due) {
            if (!level.isLoaded(pos)) continue;
            var state = level.getBlockState(pos);
            if (state.isRandomlyTicking()) state.randomTick(level, pos, level.random);
        }
    }
    /** Old DamageSource by damageType first, then by its flags (fire, projectile, magic, explosion) and attacker. */
    private static net.minecraft.world.damagesource.DamageSource damageSource(ServerLevel level, HostWorldAccess.Damage damage, net.minecraft.world.entity.Entity direct, net.minecraft.world.entity.Entity attacker) {
        net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> key = switch (damage.type()) {
            case "inFire" -> net.minecraft.world.damagesource.DamageTypes.IN_FIRE;
            case "onFire" -> net.minecraft.world.damagesource.DamageTypes.ON_FIRE;
            case "lava" -> net.minecraft.world.damagesource.DamageTypes.LAVA;
            case "inWall" -> net.minecraft.world.damagesource.DamageTypes.IN_WALL;
            case "drown" -> net.minecraft.world.damagesource.DamageTypes.DROWN;
            case "starve" -> net.minecraft.world.damagesource.DamageTypes.STARVE;
            case "cactus" -> net.minecraft.world.damagesource.DamageTypes.CACTUS;
            case "fall" -> net.minecraft.world.damagesource.DamageTypes.FALL;
            case "outOfWorld" -> net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD;
            case "wither" -> net.minecraft.world.damagesource.DamageTypes.WITHER;
            case "anvil" -> net.minecraft.world.damagesource.DamageTypes.FALLING_ANVIL;
            case "fallingBlock" -> net.minecraft.world.damagesource.DamageTypes.FALLING_BLOCK;
            case "thorns" -> net.minecraft.world.damagesource.DamageTypes.THORNS;
            case "fireball" -> net.minecraft.world.damagesource.DamageTypes.FIREBALL;
            case "arrow" -> net.minecraft.world.damagesource.DamageTypes.ARROW;
            case "thrown" -> net.minecraft.world.damagesource.DamageTypes.THROWN;
            case "indirectMagic" -> net.minecraft.world.damagesource.DamageTypes.INDIRECT_MAGIC;
            case "magic" -> net.minecraft.world.damagesource.DamageTypes.MAGIC;
            default -> null;
        };
        boolean player = attacker instanceof net.minecraft.world.entity.player.Player;
        // Armor-piercing original damage (TC4 air/order arrows) maps to the host's armor-bypassing magic types.
        if (key == null && damage.bypassArmor() && !damage.explosion() && !damage.fire())
            key = attacker != null ? net.minecraft.world.damagesource.DamageTypes.INDIRECT_MAGIC : net.minecraft.world.damagesource.DamageTypes.MAGIC;
        if (key == null) key = damage.explosion() ? (player ? net.minecraft.world.damagesource.DamageTypes.PLAYER_EXPLOSION : net.minecraft.world.damagesource.DamageTypes.EXPLOSION)
                : damage.projectile() ? (damage.fire() ? net.minecraft.world.damagesource.DamageTypes.FIREBALL : net.minecraft.world.damagesource.DamageTypes.THROWN)
                : damage.magic() ? (attacker != null ? net.minecraft.world.damagesource.DamageTypes.INDIRECT_MAGIC : net.minecraft.world.damagesource.DamageTypes.MAGIC)
                : damage.fire() ? net.minecraft.world.damagesource.DamageTypes.ON_FIRE
                : player ? net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK
                : attacker instanceof net.minecraft.world.entity.LivingEntity ? net.minecraft.world.damagesource.DamageTypes.MOB_ATTACK
                : net.minecraft.world.damagesource.DamageTypes.GENERIC;
        var type = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(key);
        return new net.minecraft.world.damagesource.DamageSource(type, direct, attacker);
    }
    private HostWorldAccess access(ServerLevel level, int dimension) {
        var energyTiles=new HostEnergyTiles(content,level);
        var spawnerTiles=new HostSpawnerTiles(content,level);
        var inventoryTiles=new HostInventoryTiles(content,level);
        return new HostWorldAccess() {
            private void check() {
                if (!level.getServer().isSameThread()) throw new IllegalStateException("Legacy server world requires the server thread");
            }
            @Override public String dimensionKey() { return level.dimension().location().toString(); }
            @Override public boolean dimensionWorld() { return level.getServer().getLevel(level.dimension()) == level; }
            @Override public Flight flight(java.util.UUID id){
                check();var player=level.getServer().getPlayerList().getPlayer(id);
                if(player==null||player.serverLevel()!=level)return null;
                return new Flight(player.getAbilities().mayfly,player.getAbilities().flying);
            }
            @Override public void flight(java.util.UUID id,Flight flight){
                check();var player=level.getServer().getPlayerList().getPlayer(id);
                if(player==null||player.serverLevel()!=level)return;
                content.afterCommit(level,()->{
                    if(player.serverLevel()!=level)return;
                    var abilities=player.getAbilities();boolean allowed=flight.allowed()||abilities.instabuild||player.isSpectator();
                    boolean flying=allowed&&flight.flying();
                    if(abilities.mayfly==allowed&&abilities.flying==flying)return;
                    abilities.mayfly=allowed;abilities.flying=flying;player.onUpdateAbilities();
                });
            }
            @Override public void broadcastPacket(github.com.gengyoubo.mfcl.bridge.LegacyPackets.Message packet){
                check();content.afterCommit(level,()->level.getServer().getPlayerList().getPlayers().forEach(player->ScanSyncChannel.sendToPlayer(player,packet)));
            }
            @Override public int legacyDimensionId() { return dimension; }
            @Override public boolean clientSide() { return false; }
            @Override public int difficulty() { check(); return level.getDifficulty().getId(); }
            @Override public int viewDistance() { check(); return level.getServer().getPlayerList().getViewDistance(); }
            @Override public Food food(UUID id) {
                check();var player=level.getPlayerByUUID(id);if(player==null)return null;
                var food=player.getFoodData();return new Food(food.getFoodLevel(),food.getSaturationLevel());
            }
            @Override public void setFood(UUID id,Food value) {
                check();var player=level.getPlayerByUUID(id);if(player!=null)content.afterCommit(level,()->{
                    player.getFoodData().setFoodLevel(value.level());player.getFoodData().setSaturation(value.saturation());
                });
            }
            @Override public void addExhaustion(UUID id,float amount) {
                check();var player=level.getPlayerByUUID(id);if(player!=null)content.afterCommit(level,()->player.causeFoodExhaustion(amount));
            }
            @Override public String biome(int x,int z) {check();return HostBiomeQueries.key(level,x,z);}
            @Override public void setBiome(int x,int z,String key) {check();content.afterCommit(level,()->HostBiomeQueries.set(level,x,z,key));}
            @Override public void playSound(Sound sound) {
                check();var id=net.minecraft.resources.ResourceLocation.parse(github.com.gengyoubo.mfcl.bridge.LegacySounds.modernId(sound.id()));
                var excluded=sound.excludedPlayer()==null?null:level.getServer().getPlayerList().getPlayer(sound.excludedPlayer());
                content.afterCommit(level,()->level.playSound(excluded,sound.x(),sound.y(),sound.z(),net.minecraft.sounds.SoundEvent.createVariableRangeEvent(id),net.minecraft.sounds.SoundSource.MASTER,sound.volume(),sound.pitch()));
            }
            @Override public long gameTime() { check(); return level.getGameTime(); }
            @Override public void sendPacket(UUID recipient,github.com.gengyoubo.mfcl.bridge.LegacyPackets.Message message) {
                check();var player=level.getPlayerByUUID(recipient);
                if(player instanceof net.minecraft.server.level.ServerPlayer server)content.afterCommit(level,()->ScanSyncChannel.sendToPlayer(server,message));
            }
            @Override public void explosionEffect(double x,double y,double z) {
                check();content.afterCommit(level,()->level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER,x,y,z,1,0,0,0,0));
            }
            @Override public void levelEvent(java.util.UUID excluded,int event,int x,int y,int z,int data) {
                check(); var player=excluded==null?null:level.getServer().getPlayerList().getPlayer(excluded);
                content.afterCommit(level,()->level.levelEvent(player,event,new BlockPos(x,y,z),data));
            }
            @Override public java.util.List<HostWorldAccess.Entity> entities(double a,double b,double c,double d,double e,double f) {check();return HostEntityQueries.query(level,a,b,c,d,e,f);}
            @Override public java.util.List<HostWorldAccess.Attribute> attributes(java.util.UUID id){check();var p=level.getPlayerByUUID(id);return p==null?null:HostPlayerAttributes.read(p);}
            @Override public void attributes(java.util.UUID id,java.util.List<HostWorldAccess.Attribute> before,java.util.List<HostWorldAccess.Attribute> after){
                check();var p=level.getPlayerByUUID(id);if(p!=null)content.afterCommit(level,()->HostPlayerAttributes.apply(p,before,after));
            }
            @Override public HostWorldAccess.Entity entity(java.util.UUID id) {check();var entity=level.getEntity(id);return entity==null||!entity.isAlive()?null:HostEntityQueries.snapshot(level,entity);}
            @Override public Sheep sheep(java.util.UUID id){check();return level.getEntity(id) instanceof net.minecraft.world.entity.animal.Sheep sheep?new Sheep(sheep.getColor().getId(),sheep.isSheared()):null;}
            @Override public void sheared(java.util.UUID id,boolean value){check();if(level.getEntity(id) instanceof net.minecraft.world.entity.animal.Sheep sheep)content.afterCommit(level,()->sheep.setSheared(value));}
            @Override public void forceChunk(java.util.UUID id,int x,int z,boolean force){
                check();var pos=new net.minecraft.world.level.ChunkPos(x,z);
                content.afterCommit(level,()->{if(force)level.getChunkSource().addRegionTicket(LEGACY_TICKET,pos,2,id,true);else level.getChunkSource().removeRegionTicket(LEGACY_TICKET,pos,2,id,true);});
            }
            @Override public byte[] nativeEntityData(java.util.UUID id){
                check();var entity=level.getEntity(id);if(entity==null)return null;
                var tag=new net.minecraft.nbt.CompoundTag();if(!entity.save(tag))return null;
                try(var output=new java.io.ByteArrayOutputStream()) {
                    net.minecraft.nbt.NbtIo.writeCompressed(tag,output);return output.toByteArray();
                }catch(java.io.IOException failure){throw new IllegalStateException("Cannot snapshot native entity",failure);}
            }
            @Override public String entityData(java.util.UUID id){
                check();var entity=level.getEntity(id);
                if(!(entity instanceof net.minecraft.world.entity.Mob mob)||entity.isPassenger()||entity.isVehicle()||mob.getTarget()!=null)return null;
                var tag=new net.minecraft.nbt.CompoundTag();return entity.save(tag)?tag.toString():null;
            }
            @Override public void removeEntity(java.util.UUID id){check();var entity=level.getEntity(id);if(entity!=null)content.afterCommit(level,entity::discard);}
            @Override public boolean restoreEntity(String data,double x,double y,double z,String name){
                check();
                try {
                    var tag=net.minecraft.nbt.TagParser.parseTag(data);
                    var entity=net.minecraft.world.entity.EntityType.loadEntityRecursive(tag,level,e->e);
                    if(!(entity instanceof net.minecraft.world.entity.Mob)||level.getEntity(entity.getUUID())!=null)return false;
                    entity.moveTo(x,y,z,entity.getYRot(),entity.getXRot());entity.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);entity.fallDistance=0;
                    if(!name.isEmpty()&&(entity.getCustomName()==null||!entity.getCustomName().getString().equals(name)))entity.setCustomName(net.minecraft.network.chat.Component.literal(name));
                    content.afterCommit(level,()->{if(!level.addFreshEntity(entity))throw new IllegalStateException("Captured entity could not join host world");});return true;
                }catch(com.mojang.brigadier.exceptions.CommandSyntaxException failure){throw new IllegalArgumentException("Invalid captured host entity data",failure);}
            }
            @Override public void applyMotion(HostWorldAccess.Motion motion) {check();content.afterCommit(level,()->HostEntityQueries.apply(level,motion));}
            @Override public void droppedItem(java.util.UUID id,github.com.gengyoubo.mfcl.bridge.LegacyClientData.Stack stack) {
                check();content.afterCommit(level,()->{
                    if(!(level.getEntity(id) instanceof net.minecraft.world.entity.item.ItemEntity drop)||drop.isRemoved())return;
                    if(stack==null)drop.discard();else drop.setItem(content.applyTo(drop.getItem(),stack));
                });
            }
            @Override public java.util.List<HostWorldAccess.Entity> players() {
                check(); return level.players().stream().map(player -> HostEntityQueries.snapshot(level, player)).filter(java.util.Objects::nonNull).toList();
            }
            @Override public void explode(double x, double y, double z, float power, boolean fire, boolean breakBlocks) {
                check();
                content.afterCommit(level,() -> level.explode(null, x, y, z, power, fire, breakBlocks ? net.minecraft.world.level.Level.ExplosionInteraction.BLOCK : net.minecraft.world.level.Level.ExplosionInteraction.NONE));
            }
            @Override public Object existingEntity(java.util.UUID id) {
                check();var entity=level.getEntity(id);
                if(entity instanceof net.minecraft.server.level.ServerPlayer player) {
                    try {
                        Object actor=player.containerMenu instanceof LegacyDeviceMenu menu?menu.originalPlayer():content.itemUse().clientActor(content.services().worlds().bound(level),ModernContentBridge.placer(player),null);
                        return LegacyTilePlayers.prepare(content,player,dimensions.portalState(actor,player));
                    }
                    catch(Exception failure){throw new IllegalStateException("Cannot resolve original player for entity query",failure);}
                }
                return null;
            }
            @Override public void spawnProjectile(Projectile projectile) {
                check();
                var arrow = new net.minecraft.world.entity.projectile.Arrow(level, projectile.x(), projectile.y(), projectile.z());
                if (projectile.owner() != null) {
                    var owner = level.getServer().getPlayerList().getPlayer(projectile.owner());
                    if (owner != null) arrow.setOwner(owner);
                }
                arrow.setDeltaMovement(projectile.motionX(), projectile.motionY(), projectile.motionZ());
                if (projectile.damage() > 0) arrow.setBaseDamage(projectile.damage());
                if (projectile.burning()) arrow.setSecondsOnFire(100);
                content.afterCommit(level, () -> level.addFreshEntity(arrow));
            }
            @Override public boolean spawnLegacyEntity(Object entity) {
                check();
                try {
                    if(content.services().loader().loadClass("net.minecraft.entity.item.EntityItem").isInstance(entity)) {
                        var drop=LegacyItemDrops.create(content,level,entity);
                        if(drop!=null)content.afterCommit(level,()->level.addFreshEntity(drop));return drop!=null;
                    }
                } catch(Exception failure){throw new IllegalStateException("Cannot translate original dropped item",failure);}
                try { if(content.villagers().spawn(entity,level))return true;var hosted = LegacyHostedEntity.host(content, level, entity); content.afterCommit(level, () -> level.addFreshEntity(hosted)); return true; }
                catch (Exception failure) { throw new IllegalStateException("Cannot host original entity " + entity.getClass().getName(), failure); }
            }
            @Override public Object hostedLegacyEntity(java.util.UUID id) {
                check(); return level.getEntity(id) instanceof LegacyHostedEntity hosted ? hosted.legacyOrNull() : null;
            }
            @Override public boolean damage(Damage damage) {
                check(); var target = level.getEntity(damage.target());
                if (target == null || !target.isAlive()) return false;
                var attacker = damage.attacker() == null ? null : level.getEntity(damage.attacker());
                var direct = damage.direct() == null ? attacker : level.getEntity(damage.direct());
                var source = damageSource(level, damage, direct == null ? attacker : direct, attacker);
                if (target.isInvulnerableTo(source)) return false;
                content.afterCommit(level, () -> target.hurt(source, damage.amount()));
                return true;
            }
            @Override public void ignite(java.util.UUID id, int seconds) {
                check(); var target = level.getEntity(id); if (target != null) content.afterCommit(level, () -> target.setSecondsOnFire(seconds));
            }
            @Override public void swing(java.util.UUID id) {
                check();var entity=level.getEntity(id);
                if(entity instanceof net.minecraft.world.entity.LivingEntity living)content.afterCommit(level,()->living.swing(net.minecraft.world.InteractionHand.MAIN_HAND));
            }
            @Override public boolean canAttackPlayer(java.util.UUID attacker,java.util.UUID target) {
                check();
                var source=level.getPlayerByUUID(attacker);var victim=level.getPlayerByUUID(target);
                return level.getServer().isPvpAllowed()&&source!=null&&victim!=null&&source.canHarmPlayer(victim);
            }
            @Override public void addEffect(java.util.UUID id, int potion, int duration, int amplifier, boolean ambient) {
                check();
                if (!(level.getEntity(id) instanceof net.minecraft.world.entity.LivingEntity target)) return;
                var effect = content.potions().effect(potion);
                if (effect == null) { github.com.gengyoubo.mfcl.bridge.LegacyDiagnostics.missingMapping("potion " + potion, "original potion has no host effect"); return; }
                content.afterCommit(level, () -> target.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect, duration, amplifier, ambient, true)));
            }
            @Override public java.util.List<github.com.gengyoubo.mfcl.bridge.LegacyPlayerBody.Effect> effects(UUID id) {
                check();return level.getEntity(id) instanceof net.minecraft.world.entity.LivingEntity entity?content.potions().snapshot(entity):java.util.List.of();
            }
            @Override public void addEffect(UUID id,github.com.gengyoubo.mfcl.bridge.LegacyPlayerBody.Effect state) {
                check();if(level.getEntity(id) instanceof net.minecraft.world.entity.LivingEntity target)content.afterCommit(level,()->content.potions().apply(target,state));
            }
            @Override public Integer statistic(UUID id,String originalId){check();var player=level.getPlayerByUUID(id);return player instanceof net.minecraft.server.level.ServerPlayer server?content.statistic(server,originalId):null;}
            @Override public void statistic(UUID id,String originalId,int value){check();var player=level.getPlayerByUUID(id);if(player instanceof net.minecraft.server.level.ServerPlayer server)content.statistic(server,originalId,value);}
            @Override public void heal(UUID id,float amount) {
                check();if(level.getEntity(id) instanceof net.minecraft.world.entity.LivingEntity target)content.afterCommit(level,()->target.heal(amount));
            }
            @Override public void removeEffect(UUID id,int potion) {
                check();var type=content.potions().effect(potion);if(type!=null&&level.getEntity(id) instanceof net.minecraft.world.entity.LivingEntity target)content.afterCommit(level,()->target.removeEffect(type));
            }
            @Override public void clearEffects(UUID id) {
                check();if(level.getEntity(id) instanceof net.minecraft.world.entity.LivingEntity target)content.afterCommit(level,target::removeAllEffects);
            }
            @Override public long dayTime() { check(); return level.getDayTime(); }
            @Override public void setDayTime(long time) { check(); level.setDayTime(time); }
            @Override public boolean powered(int x, int y, int z) { check(); return level.hasNeighborSignal(new BlockPos(x,y,z)); }
            @Override public int power(int x, int y, int z, int side) { check(); return level.getSignal(new BlockPos(x,y,z),net.minecraft.core.Direction.from3DDataValue(side)); }
            @Override public boolean loaded(int x, int y, int z) { check(); return level.isInWorldBounds(new BlockPos(x,y,z)) && level.hasChunkAt(new BlockPos(x,y,z)); }
            @Override public void updateComparators(int x, int y, int z) {
                check(); var pos = new BlockPos(x,y,z); var block = level.getBlockState(pos).getBlock();
                content.afterCommit(level,() -> level.updateNeighbourForOutputSignal(pos,block));
            }
            @Override public void blockEvent(int x, int y, int z, String key, int event, int data) {
                check(); var pos = new BlockPos(x,y,z); var block = level.getBlockState(pos).getBlock();
                if (!content.legacyBlock(level.getBlockState(pos)).legacyKey().equals(key)) return;
                content.afterCommit(level,() -> level.blockEvent(pos,block,event,data));
            }
            @Override public void schedule(int x, int y, int z, String key, int delay) {
                check(); var pos = new BlockPos(x,y,z);
                if (content.legacyBlock(level.getBlockState(pos)).legacyKey().equals(key)) {
                    var state = level.getBlockState(pos); var block = state.getBlock();
                    if (!state.hasProperty(ModernContentBridge.METADATA) && state.isRandomlyTicking()) {
                        long due = level.getGameTime() + Math.max(1, delay);
                        content.afterCommit(level,() -> hostTicks.computeIfAbsent(level, ignored -> new HashMap<>()).putIfAbsent(pos.immutable(), due));
                        return;
                    }
                    content.afterCommit(level,() -> level.scheduleTick(pos,block,Math.max(1,delay)));
                }
            }
            @Override public Block block(int x, int y, int z) {
                check(); if(dimensions.generating())dimensions.terrain(level,x>>4,z>>4);
                var state = level.getBlockState(new BlockPos(x, y, z));
                // Post-1.7 neighbours (deepslate, copper, mangrove...) must not abort and suspend original tile ticks.
                var known = content.hostRepresentations().knownOpaque(state);
                if (known != null) return known;
                try { return content.legacyBlock(state); }
                catch (github.com.gengyoubo.mfcl.bridge.UnmappedLegacyBlockException absent) { return content.hostRepresentations().opaque(state); }
            }
            @Override public boolean setBlock(int x, int y, int z, Block block, int flags) {
                check(); dimensions.terrain(level,x>>4,z>>4);
                var pos = new BlockPos(x,y,z); content.beforeMutation(level,pos);
                net.minecraft.world.level.block.state.BlockState state;
                try { state = content.modernBlock(block); }
                catch (UnsupportedOperationException unsupported) { if (dimensions.skipUnsupported(block, unsupported)) return false; throw unsupported; }
                // Block entity creation runs other mods' code under this call. Forge's event transformer resolves classes they
                // load for the first time through the context loader, which here would still be the legacy runtime's.
                boolean changed;
                Thread thread = Thread.currentThread(); ClassLoader previous = thread.getContextClassLoader();
                try { thread.setContextClassLoader(LevelWorldBridge.class.getClassLoader()); changed = level.setBlock(pos, state, flags); }
                finally { thread.setContextClassLoader(previous); }
                if ((block.legacyKey().equals("minecraft:leaves") || block.legacyKey().equals("minecraft:leaves2"))
                        && (block.metadata() & 8) != 0 && state.getBlock() instanceof net.minecraft.world.level.block.LeavesBlock) {
                    var leaf = state.getBlock();
                    content.afterCommit(level, () -> {
                        if (level.getBlockState(pos).is(leaf)) level.scheduleTick(pos, leaf, 1);
                    });
                }
                return changed;
            }
            @Override public boolean canPlaceBlock(Block block, int x, int y, int z) {
                check(); var candidate = content.blocks().get(block.legacyKey());
                var state = candidate == null ? content.modernBlock(block) : candidate.defaultBlockState();
                return level.isInWorldBounds(new BlockPos(x,y,z)) && level.isUnobstructed(state,new BlockPos(x,y,z),net.minecraft.world.phys.shapes.CollisionContext.empty());
            }
            @Override public HostWorldAccess.Hit rayTrace(double sx, double sy, double sz, double ex, double ey, double ez) {
                check();
                var from = new net.minecraft.world.phys.Vec3(sx, sy, sz); var to = new net.minecraft.world.phys.Vec3(ex, ey, ez);
                var result = level.clip(new net.minecraft.world.level.ClipContext(from, to, net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, null));
                if (result.getType() == net.minecraft.world.phys.HitResult.Type.MISS) return null;
                var pos = result.getBlockPos();
                return new HostWorldAccess.Hit(pos.getX(), pos.getY(), pos.getZ(), result.getDirection().get3DDataValue(), result.getLocation().x, result.getLocation().y, result.getLocation().z);
            }
            @Override public boolean originalBlockRayTrace() { check(); return true; }
            @Override public java.util.List<int[]> legacyTiles(int chunkX, int chunkZ) {
                check(); return LevelWorldBridge.legacyTiles(level, chunkX, chunkZ);
            }
            @Override public byte[] nativeTileData(int x,int y,int z) {
                check();var pos=new BlockPos(x,y,z);if(!level.hasChunkAt(pos))return null;
                var tile=level.getBlockEntity(pos);if(tile==null||tile.isRemoved()||tile instanceof LegacyBlockEntity)return null;
                try(var output=new java.io.ByteArrayOutputStream()) {
                    net.minecraft.nbt.NbtIo.writeCompressed(tile.saveWithFullMetadata(),output);return output.toByteArray();
                }catch(java.io.IOException failure){throw new IllegalStateException("Cannot snapshot native block entity",failure);}
            }
            @Override public Object tileEntity(int x, int y, int z) {
                check();var pos=new BlockPos(x,y,z);if(!level.hasChunkAt(pos))return null;
                content.beforeMutation(level,pos); var tile = level.getBlockEntity(pos);
                if (tile == null) return null;
                if (tile instanceof LegacyBlockEntity legacy) return legacy.legacy();
                if(tile instanceof net.minecraft.world.level.block.entity.SpawnerBlockEntity spawner)return spawnerTiles.find(spawner);
                // A native machine with FE keeps its RF view; plain storage (chests, barrels...) becomes an old IInventory.
                var energy=energyTiles.find(pos);
                return energy!=null?energy:inventoryTiles.find(pos);
            }
            @Override public void setTileEntity(int x, int y, int z, Object old) {
                check(); var tile = level.getBlockEntity(new BlockPos(x, y, z));
                if (!(tile instanceof LegacyBlockEntity legacy)) throw new UnsupportedOperationException("No legacy tile envelope at requested position");
                legacy.replace(old); legacy.synchronize();
            }
            @Override public void spawnerType(int x,int y,int z,Object logic,String entity){check();spawnerTiles.type(new BlockPos(x,y,z),logic,entity);}
            @Override public void removeTileEntity(int x, int y, int z) { check(); level.removeBlockEntity(new BlockPos(x, y, z)); }
            @Override public void markTileDirty(int x, int y, int z, Object old) {
                check(); var tile = level.getBlockEntity(new BlockPos(x, y, z));
                if(tile instanceof net.minecraft.world.level.block.entity.SpawnerBlockEntity spawner&&spawnerTiles.owns(spawner,old)){spawner.setChanged();return;}
                if (!(tile instanceof LegacyBlockEntity legacy) || !legacy.owns(old)) throw new IllegalStateException("Legacy tile identity mismatch");
                legacy.markLegacyDirty();
            }
            @Override public int light(int x, int y, int z) { check(); return level.getMaxLocalRawBrightness(new BlockPos(x,y,z)); }
            @Override public int height(int x, int z) { check(); return level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z); }
            @Override public int minBuildHeight() { check(); return level.getMinBuildHeight(); }
            @Override public int maxBuildHeight() { check(); return level.getMaxBuildHeight(); }
            @Override public boolean canSeeSky(int x, int y, int z) { check(); return level.canSeeSky(new BlockPos(x,y,z)); }
            @Override public void neighborsChanged(int x, int y, int z) {
                check(); var pos = new BlockPos(x,y,z); var block = level.getBlockState(pos).getBlock();
                content.afterCommit(level,() -> level.updateNeighborsAt(pos,block));
            }
            @Override public long seed() { return level.getSeed(); }
            @Override public java.io.File legacySaveDirectory() { return LegacyDimensionHost.saveDirectory(level); }
            // Queued only when the original tick commits; the move itself happens after the host tick.
            @Override public void transferPlayer(Transfer transfer) { check(); content.afterCommit(level, () -> dimensions.queue(transfer)); }
            @Override public void portalCooldown(java.util.UUID id, int ticks) {
                check(); var player = level.getServer().getPlayerList().getPlayer(id); if (player != null) content.afterCommit(level, () -> player.setPortalCooldown(ticks));
            }
            @Override public int portalCooldown(java.util.UUID id) {
                check(); var player = level.getServer().getPlayerList().getPlayer(id); return player == null ? 0 : player.getPortalCooldown();
            }
            @Override public int[] findBlock(String legacyKey, int x, int y, int z, int radius) { check(); return dimensions.findBlock(level, legacyKey, x, y, z, radius); }
            @Override public int[] findBlock(String legacyKey,int mask,int metadata,int x,int y,int z,int radius) {check();return dimensions.findBlock(level,legacyKey,mask,metadata,x,y,z,radius);}
            @Override public void blockUpdated(int x, int y, int z) {
                check(); BlockPos pos = new BlockPos(x, y, z); var state = level.getBlockState(pos);
                if (level.getBlockEntity(pos) instanceof LegacyBlockEntity tile) tile.markLegacyDirty();
                else level.sendBlockUpdated(pos, state, state, 3);
            }
        };
    }
}

