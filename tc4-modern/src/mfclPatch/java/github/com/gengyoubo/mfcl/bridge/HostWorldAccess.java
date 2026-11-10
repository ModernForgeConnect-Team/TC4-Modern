package github.com.gengyoubo.mfcl.bridge;

/** Synchronous host world contract. The host remains the only owner of world state. */
public interface HostWorldAccess {
    record Block(String legacyKey, int metadata) {}
    /** False for a mod's auxiliary level (LittleTiles sub-levels) that shares a dimension key with the real level; it never stands in for that dimension. */
    default boolean dimensionWorld(){return true;}
    /** Compressed, read-only native block entity NBT for diagnostics through a legacy proxy tile. */
    default byte[] nativeTileData(int x,int y,int z){return null;}
    /** Compressed native entity NBT for read-only inspection; null when the entity cannot be saved. */
    default byte[] nativeEntityData(java.util.UUID id){return null;}
    /** A block hit from the host ray trace, in old World/Item coordinates. */
    record Hit(int x, int y, int z, int side, double hitX, double hitY, double hitZ) {}
    record Sound(double x,double y,double z,String id,float volume,float pitch,boolean distanceDelay,java.util.UUID excludedPlayer) {}
    /** A legacy projectile reduced to host-neutral data so the modern side can spawn an equivalent entity. */
    record Projectile(double x,double y,double z,double motionX,double motionY,double motionZ,double damage,boolean burning,java.util.UUID owner) {}
    record Abilities(boolean creative,boolean invulnerable) {}
    record Food(int level,float saturation) {}
    record Flight(boolean allowed,boolean flying) {}
    record Modifier(java.util.UUID id,String name,double amount,int operation,boolean permanent) {}
    record Attribute(String name,double base,java.util.List<Modifier> modifiers) {}
    default java.util.List<Attribute> attributes(java.util.UUID player){return null;}
    default void attributes(java.util.UUID player,java.util.List<Attribute> before,java.util.List<Attribute> after){throw new UnsupportedOperationException("Host attributes are not available");}
    default String entityData(java.util.UUID entity){return null;}
    default void removeEntity(java.util.UUID entity){throw new UnsupportedOperationException("Host entity removal is not available");}
    default boolean restoreEntity(String data,double x,double y,double z,String name){throw new UnsupportedOperationException("Host entity restoration is not available");}
    record Sheep(int color,boolean sheared){}
    default Sheep sheep(java.util.UUID id){return null;}
    default void sheared(java.util.UUID id,boolean value){throw new UnsupportedOperationException("Host shearing is not available");}
    default void forceChunk(java.util.UUID ticket,int x,int z,boolean force){throw new UnsupportedOperationException("Host chunk tickets are not available");}
    default Flight flight(java.util.UUID player){return null;}
    default void flight(java.util.UUID player,Flight flight){throw new UnsupportedOperationException("Host flight mutation is not available");}
    /** Null means this binding has no authoritative player with that identity. */
    default Food food(java.util.UUID player) { return null; }
    default void setFood(java.util.UUID player,Food food) { throw new UnsupportedOperationException("Host player food mutation is not available"); }
    default void addExhaustion(java.util.UUID player,float amount) { throw new UnsupportedOperationException("Host player exhaustion is not available"); }
    record Entity(java.util.UUID id,String legacyClass,String name,double x,double y,double z,double motionX,double motionY,double motionZ,float fallDistance,float width,float height,boolean sneaking,boolean pushable,int entityId,LegacyClientData.Stack item,int age,int love,Abilities abilities,Float health,byte[] entityData,int pickupDelay) {
        public Entity(java.util.UUID id,String legacyClass,String name,double x,double y,double z,double motionX,double motionY,double motionZ,float fallDistance,float width,float height,boolean sneaking,boolean pushable,int entityId,LegacyClientData.Stack item,int age,int love,Abilities abilities,Float health,byte[] entityData) {
            this(id,legacyClass,name,x,y,z,motionX,motionY,motionZ,fallDistance,width,height,sneaking,pushable,entityId,item,age,love,abilities,health,entityData,0);
        }
        public Entity(java.util.UUID id,String legacyClass,String name,double x,double y,double z,double motionX,double motionY,double motionZ,float fallDistance,float width,float height,boolean sneaking,boolean pushable,int entityId,LegacyClientData.Stack item,int age,int love,Abilities abilities,Float health) {
            this(id,legacyClass,name,x,y,z,motionX,motionY,motionZ,fallDistance,width,height,sneaking,pushable,entityId,item,age,love,abilities,health,null);
        }
        public Entity(java.util.UUID id,String legacyClass,String name,double x,double y,double z,double motionX,double motionY,double motionZ,float fallDistance,float width,float height,boolean sneaking,boolean pushable,int entityId,LegacyClientData.Stack item,int age,int love,Abilities abilities) {
            this(id,legacyClass,name,x,y,z,motionX,motionY,motionZ,fallDistance,width,height,sneaking,pushable,entityId,item,age,love,abilities,null);
        }
        public Entity(java.util.UUID id,String legacyClass,String name,double x,double y,double z,double motionX,double motionY,double motionZ,float fallDistance,float width,float height,boolean sneaking,boolean pushable,int entityId,LegacyClientData.Stack item,int age,int love) {
            this(id,legacyClass,name,x,y,z,motionX,motionY,motionZ,fallDistance,width,height,sneaking,pushable,entityId,item,age,love,null);
        }
        public Entity(java.util.UUID id,String legacyClass,String name,double x,double y,double z,double motionX,double motionY,double motionZ,float fallDistance,float width,float height,boolean sneaking,boolean pushable,int entityId,LegacyClientData.Stack item) {
            this(id,legacyClass,name,x,y,z,motionX,motionY,motionZ,fallDistance,width,height,sneaking,pushable,entityId,item,0,0);
        }
        public Entity(java.util.UUID id,String legacyClass,String name,double x,double y,double z,double motionX,double motionY,double motionZ,float fallDistance,float width,float height,boolean sneaking,boolean pushable) {
            this(id,legacyClass,name,x,y,z,motionX,motionY,motionZ,fallDistance,width,height,sneaking,pushable,0,null);
        }
        public Entity relativeTo(double originX,double originY,double originZ) {
            return new Entity(id,legacyClass,name,x-originX,y-originY,z-originZ,motionX,motionY,motionZ,fallDistance,width,height,sneaking,pushable,entityId,item,age,love,abilities,health,entityData,pickupDelay);
        }
    }
    /** {@code love} is the animal breeding timer (EntityAnimal.inLove), 0 for anything else. */
    record Motion(java.util.UUID id,double x,double y,double z,float fallDistance,int love) {}
    String dimensionKey();
    int legacyDimensionId();
    boolean clientSide();
    default int difficulty() { return 2; }
    /** Server tracking distance in chunks, used by original packet range checks. */
    default int viewDistance() { return 10; }
    long gameTime();
    long dayTime();
    void setDayTime(long time);
    default String biome(int x,int z) { return "minecraft:plains"; }
    default void setBiome(int x,int z,String key) { throw new UnsupportedOperationException("Host biome mutation is not available"); }
    Block block(int x, int y, int z);
    boolean setBlock(int x, int y, int z, Block block, int flags);
    default boolean canPlaceBlock(Block block, int x, int y, int z) { return true; }
    default boolean powered(int x, int y, int z) { return false; }
    default int power(int x, int y, int z, int side) { return 0; }
    default boolean loaded(int x, int y, int z) { return true; }
    default void playSound(Sound sound) { }
    /** A modern level event whose payload has already been translated by the legacy world bridge. */
    default void levelEvent(java.util.UUID excludedPlayer,int event,int x,int y,int z,int data) { }
    /** Visual effect only; the original explosion retains authority over damage and block destruction. */
    default void explosionEffect(double x,double y,double z) { }
    /** Old World.spawnParticle by 1.7.10 particle name; only client worlds draw particles. */
    default void particle(String name,double x,double y,double z,double velocityX,double velocityY,double velocityZ) { }
    /** An original adapter's encoded client packet, delivered over the authenticated host connection. */
    default void sendPacket(java.util.UUID recipient,LegacyPackets.Message packet) { }
    /** Send through the actual server player list, including clients in other dimensions. */
    default void broadcastPacket(LegacyPackets.Message packet){throw new UnsupportedOperationException("Host packet broadcast is not available");}
    default void spawnProjectile(Projectile projectile) { }
    /** An old entity with no host translation; the host keeps it alive and runs its original onUpdate. */
    default boolean spawnLegacyEntity(Object entity) { return false; }
    /** The original object behind a host entity that hosts an old entity, so old queries see the real thing. */
    default Object hostedLegacyEntity(java.util.UUID id) { return null; }
    /**
     * Old attackEntityFrom on a mirrored host entity. {@code type} is the old damageType; {@code direct} and
     * {@code attacker} are host entity ids (old getSourceOfDamage / getEntity). Returns whether the hit is expected to land.
     */
    record Damage(java.util.UUID target,String type,float amount,boolean fire,boolean projectile,boolean magic,boolean explosion,boolean bypassArmor,java.util.UUID direct,java.util.UUID attacker) {}
    default boolean damage(Damage damage) { return false; }
    default void ignite(java.util.UUID target, int seconds) { }
    /** Old addPotionEffect; {@code potion} is the 1.7.10 potion id. */
    default void addEffect(java.util.UUID target, int potion, int duration, int amplifier, boolean ambient) { }
    default void addEffect(java.util.UUID target,LegacyPlayerBody.Effect effect) { addEffect(target,effect.id(),effect.duration(),effect.amplifier(),effect.ambient()); }
    default java.util.List<LegacyPlayerBody.Effect> effects(java.util.UUID target) { return java.util.List.of(); }
    default void heal(java.util.UUID target,float amount) { }
    default void removeEffect(java.util.UUID target,int potion) { }
    default void clearEffects(java.util.UUID target) { }
    default Integer statistic(java.util.UUID player,String originalId) { return null; }
    default void statistic(java.util.UUID player,String originalId,int value) { }
    default void swing(java.util.UUID entity) { }
    /** Server PvP and team friendly-fire rules for an old player's canAttackPlayer callback. */
    default boolean canAttackPlayer(java.util.UUID attacker,java.util.UUID target) { return false; }
    default java.util.List<Entity> entities(double minX,double minY,double minZ,double maxX,double maxY,double maxZ) { return java.util.List.of(); }
    /** Refresh a target an original entity retained after its initial AABB query; null after it leaves this world. */
    default Entity entity(java.util.UUID id) { return null; }
    default Object existingEntity(java.util.UUID id) { return null; }
    /** Every player in this world (old World.playerEntities). */
    default java.util.List<Entity> players() { return java.util.List.of(); }
    /** Old World.newExplosion; the host explosion damages real entities and breaks blocks with host rules. */
    default void explode(double x, double y, double z, float power, boolean fire, boolean breakBlocks) { }
    default void applyMotion(Motion motion) { }
    /** What old code left of a host dropped item it changed through its mirror; null removes the item. */
    default void droppedItem(java.util.UUID id,LegacyClientData.Stack stack) { }
    default void updateComparators(int x, int y, int z) { }
    default void blockEvent(int x, int y, int z, String block, int event, int data) { throw new UnsupportedOperationException("Host block events are not available"); }
    default void schedule(int x, int y, int z, String block, int delay) { throw new UnsupportedOperationException("Host block scheduling is not available"); }
    default Hit rayTrace(double startX, double startY, double startZ, double endX, double endY, double endZ) { return null; }
    /** Real world bindings can run the original per-block ray test against their mirrored blocks. */
    default boolean originalBlockRayTrace() { return false; }
    /** Positions {x,y,z} of the original tiles in a loaded host chunk; old code reads them through Chunk.chunkTileEntityMap. */
    default java.util.List<int[]> legacyTiles(int chunkX, int chunkZ) { return java.util.List.of(); }
    default Object tileEntity(int x, int y, int z) { throw new UnsupportedOperationException("Host TileEntity lookup is not available"); }
    /** Original dungeon/mineshaft spawner selection, applied to the authoritative native spawner. */
    default void spawnerType(int x,int y,int z,Object logic,String entity) { throw new UnsupportedOperationException("Host spawner mutation is not available"); }
    default void setTileEntity(int x, int y, int z, Object tile) { throw new UnsupportedOperationException("Host TileEntity assignment is not available"); }
    default void removeTileEntity(int x, int y, int z) { throw new UnsupportedOperationException("Host TileEntity removal is not available"); }
    default void markTileDirty(int x, int y, int z, Object tile) { throw new UnsupportedOperationException("Host TileEntity dirty tracking is not available"); }
    /** Combined sky/block light as 1.7.10 getBlockLightValue saw it (sky already darkened by time of day). */
    default int light(int x, int y, int z) { return 15; }
    /** First free y above the motion-blocking surface (1.7.10 getHeightValue). */
    default int height(int x, int z) { return 64; }
    default int minBuildHeight() { return 0; }
    default int maxBuildHeight() { return 256; }
    default boolean canSeeSky(int x, int y, int z) { return true; }
    /** Notifies host blocks around the position; original blocks there are notified by the legacy side. */
    default void neighborsChanged(int x, int y, int z) { }
    default void blockUpdated(int x, int y, int z) { throw new UnsupportedOperationException("Host block update is not available"); }
    /** World seed (old World.getSeed); worldgen and Teleporter randoms derive from it. */
    default long seed() { return 0; }
    /** Directory standing in for the old world save folder (ISaveHandler.getWorldDirectory), or null without saves. */
    default java.io.File legacySaveDirectory() { return null; }
    /** Old transferPlayerToDimension, resolved: the player goes to the host level of {@code legacyDimension}. */
    record Transfer(java.util.UUID player, int legacyDimension, double x, double y, double z, float yaw, float pitch, Object teleporter) {}
    default void transferPlayer(Transfer transfer) { throw new UnsupportedOperationException("Host dimension travel is not available"); }
    /** Old EntityPlayerMP.timeUntilPortal, kept on the host player between mirrored actors. */
    default void portalCooldown(java.util.UUID player, int ticks) { }
    default int portalCooldown(java.util.UUID player) { return 0; }
    /** Nearest block of {@code legacyKey} in host chunks within {@code radius} columns, as {x,y,z}, or null. */
    default int[] findBlock(String legacyKey, int x, int y, int z, int radius) { return null; }
    /** Variant-aware search: locked/unlocked portals may share a type while destinations must remain distinct. */
    default int[] findBlock(String legacyKey,int mask,int metadata,int x,int y,int z,int radius) {
        int[] found=findBlock(legacyKey,x,y,z,radius);
        return found!=null&&(block(found[0],found[1],found[2]).metadata()&mask)==(metadata&mask)?found:null;
    }
}
