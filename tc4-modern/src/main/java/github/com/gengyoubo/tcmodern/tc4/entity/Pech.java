package github.com.gengyoubo.tcmodern.tc4.entity;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.api.LegacyMappings;
import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.LegacyStack;
import github.com.gengyoubo.mfcl.api.mla.LegacyField;
import github.com.gengyoubo.mfcl.api.mla.LegacyMethod;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.aspect.TcItemAspects;
import github.com.gengyoubo.tcmodern.tc4.entity.item.AspectOrb;
import github.com.gengyoubo.tcmodern.tc4.entity.projectile.PechBlast;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Entities;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.NameTagItem;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.jetbrains.annotations.Nullable;

/**
 * The pech of magical forests: a hoarder that gathers valuables into its pack and trades with players it trusts.
 * Foragers fight hand to hand, stalkers with bows and thaumaturges with a wand of pech curses. Hitting one turns
 * every pech nearby against you.
 */
public final class Pech extends Monster implements RangedAttackMob {
    static final String LEGACY_CLASS = "thaumcraft.common.entities.monster.EntityPech";
    public static final int FORAGER = 0, THAUMATURGE = 1, STALKER = 2;
    private static final EntityDataAccessor<Integer> TYPE = SynchedEntityData.defineId(Pech.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ANGER = SynchedEntityData.defineId(Pech.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> TAMED = SynchedEntityData.defineId(Pech.class, EntityDataSerializers.BOOLEAN);
    private static final String[] PRIMALS = { "aer", "terra", "ignis", "aqua", "ordo", "perditio" };
    private final LegacyHost legacy = new LegacyHost(LEGACY_CLASS);
    public final NonNullList<ItemStack> loot = NonNullList.withSize(9, ItemStack.EMPTY);
    private final RangedAttackGoal arrowAttack = new RangedAttackGoal(this, 0.6, 20, 50, 15.0F);
    private final RangedAttackGoal blastAttack = new RangedAttackGoal(this, 0.6, 20, 30, 15.0F);
    private final MeleeAttackGoal meleeAttack = new MeleeAttackGoal(this, 0.6, false);
    private final AvoidEntityGoal<Player> avoidPlayer = new AvoidEntityGoal<>(this, Player.class, 8.0F, 0.5, 0.6);
    private boolean updateCombat = true;
    private int chargeCount;
    public float mumble;

    public Pech(EntityType<? extends Pech> type, Level level) { super(type, level); setDropChance(EquipmentSlot.MAINHAND, 0.2F); }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 30).add(Attributes.ATTACK_DAMAGE, 6).add(Attributes.MOVEMENT_SPEED, 0.5);
    }

    public static void host(LegacyRuntime runtime) {
        Tc4Legacy.host(runtime, LEGACY_CLASS, (level, original) -> {
            var pech = Tc4Entities.PECH.get().create(level);
            if (pech == null) return null;
            var old = runtime.accessor(Old.class, original);
            if (Tc4Legacy.copyEquipment(runtime, original, pech)) {
                pech.setPechType(old.type());
                pech.setAnger(old.anger());
                pech.setTamed(old.tamed());
            } else pech.equip(level.getCurrentDifficultyAt(pech.blockPosition()));
            Tc4Legacy.copyName(runtime, original, pech);
            return pech;
        });
    }

    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(TYPE, FORAGER); entityData.define(ANGER, 0); entityData.define(TAMED, false); }
    public int pechType() { return entityData.get(TYPE); }
    public void setPechType(int type) { entityData.set(TYPE, Mth.clamp(type, 0, 2)); }
    public int anger() { return entityData.get(ANGER); }
    public void setAnger(int anger) { entityData.set(ANGER, Math.max(0, anger)); }
    public boolean isTamed() { return entityData.get(TAMED); }
    public void setTamed(boolean tamed) { entityData.set(TAMED, tamed); updateCombat = true; }

    @Override protected Component getTypeName() {
        return switch (pechType()) {
            case THAUMATURGE -> net.minecraft.network.chat.Component.translatable("entity.tc4modern.pech.thaumaturge");
            case STALKER -> net.minecraft.network.chat.Component.translatable("entity.tc4modern.pech.stalker");
            default -> super.getTypeName();
        };
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new TradeGoal());
        goalSelector.addGoal(3, new CollectGoal());
        goalSelector.addGoal(5, new OpenDoorGoal(this, true));
        goalSelector.addGoal(6, new MoveTowardsRestrictionGoal(this, 0.5));
        goalSelector.addGoal(9, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 3.0F, 1.0F));
        goalSelector.addGoal(10, new LookAtPlayerGoal(this, Mob.class, 8.0F));
        goalSelector.addGoal(11, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    /** setCombatTask: the held item decides how the pech fights; untamed pech keep away from players. */
    private void setCombatTask() {
        goalSelector.removeGoal(meleeAttack); goalSelector.removeGoal(arrowAttack); goalSelector.removeGoal(blastAttack); goalSelector.removeGoal(avoidPlayer);
        var held = getMainHandItem();
        if (held.is(Items.BOW)) goalSelector.addGoal(2, arrowAttack);
        else if (Tc4Legacy.is(held, "Thaumcraft:WandCasting")) goalSelector.addGoal(2, blastAttack);
        else goalSelector.addGoal(2, meleeAttack);
        if (!isTamed()) goalSelector.addGoal(4, avoidPlayer);
    }

    @Override public void setItemSlot(EquipmentSlot slot, ItemStack stack) { super.setItemSlot(slot, stack); if (slot == EquipmentSlot.MAINHAND) updateCombat = true; }

    private void equip(DifficultyInstance difficulty) {
        ItemStack held = switch (random.nextInt(20)) {
            case 0, 12 -> pechWand();
            case 1 -> new ItemStack(Items.STONE_SWORD);
            case 2, 4, 10, 11, 13 -> new ItemStack(Items.BOW);
            case 3 -> new ItemStack(Items.STONE_AXE);
            case 5 -> new ItemStack(Items.IRON_SWORD);
            case 6 -> new ItemStack(Items.IRON_AXE);
            case 7 -> new ItemStack(Items.FISHING_ROD);
            case 8 -> new ItemStack(Items.STONE_PICKAXE);
            case 9 -> new ItemStack(Items.IRON_PICKAXE);
            default -> ItemStack.EMPTY;
        };
        setItemSlot(EquipmentSlot.MAINHAND, held);
        if (Tc4Legacy.is(held, "Thaumcraft:WandCasting")) { setPechType(THAUMATURGE); setDropChance(EquipmentSlot.MAINHAND, 0.1F); }
        else if (!held.isEmpty()) {
            if (held.is(Items.BOW)) setPechType(STALKER);
            float f = difficulty.getSpecialMultiplier();
            if (random.nextFloat() < 0.25F * f)
                setItemSlot(EquipmentSlot.MAINHAND, EnchantmentHelper.enchantItem(random, held, (int) (5.0F + f * random.nextInt(18)), false));
        }
        setCanPickUpLoot(random.nextFloat() < 0.75F * difficulty.getSpecialMultiplier());
    }

    /** A wand with a pech focus and a little earth, entropy and water vis, made by the original wand item. */
    private ItemStack pechWand() {
        var runtime = TcModernApi.runtime().orElse(null);
        if (runtime == null) return ItemStack.EMPTY;
        try {
            var wand = runtime.stack(new LegacyStack("Thaumcraft:WandCasting", 0, 1, ""));
            var item = runtime.accessor(AspectOrb.OldStack.class, wand).item();
            var wands = runtime.accessor(WandItem.class, item);
            wands.setFocus(wand, runtime.stack(new LegacyStack("Thaumcraft:FocusPech", 0, 1, "")));
            var aspects = runtime.accessor(AspectOrb.AspectLookup.class);
            int[] amounts = { 2 + random.nextInt(6), 2 + random.nextInt(6), 2 + random.nextInt(6), random.nextInt(4), random.nextInt(4), random.nextInt(4) };
            String[] tags = { "terra", "perditio", "aqua", "aer", "ignis", "ordo" };
            for (int i = 0; i < tags.length; i++) if (amounts[i] > 0) wands.addVis(wand, aspects.get(tags[i]), amounts[i], true);
            return runtime.mappings().toHost(LegacyMappings.ITEM_STACK, wand, ItemStack.class).orElse(ItemStack.EMPTY);
        } catch (RuntimeException failure) {
            LogUtils.getLogger().warn("Could not make a pech thaumaturge's wand", failure);
            return ItemStack.EMPTY;
        }
    }

    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
            @Nullable SpawnGroupData group, @Nullable CompoundTag tag) {
        var result = super.finalizeSpawn(level, difficulty, reason, group, tag);
        equip(difficulty);
        return result;
    }

    @Override protected void populateDefaultEquipmentSlots(net.minecraft.util.RandomSource random, DifficultyInstance difficulty) { }
    @Override public int getArmorValue() { return Math.min(20, super.getArmorValue() + 2); }

    @Override public void tick() {
        if (mumble > 0) mumble *= 0.75F;
        if (!level().isClientSide) {
            if (anger() > 0) setAnger(anger() - 1);
            if (anger() > 0 && getTarget() == null) {
                Player player = level().getNearestPlayer(this, 16.0);
                if (player != null && !player.isCreative() && !player.isSpectator()) {
                    setTarget(player);
                    if (chargeCount > 0) chargeCount--;
                    if (chargeCount == 0) { chargeCount = 100; playSound(Tc4Legacy.sound("pech_charge"), getSoundVolume(), getVoicePitch()); }
                    level().broadcastEntityEvent(this, (byte) 17);
                }
            }
            if (anger() == 0 && getTarget() instanceof Player && getLastHurtByMob() != getTarget()) setTarget(null);
        } else {
            if (random.nextInt(15) == 0 && anger() > 0) particle(ParticleTypes.ANGRY_VILLAGER);
            if (random.nextInt(25) == 0 && isTamed()) particle(ParticleTypes.HAPPY_VILLAGER);
        }
        super.tick();
        legacy.tick(this);
    }

    private void particle(ParticleOptions type) {
        level().addParticle(type, getX() + random.nextFloat() * getBbWidth() * 2 - getBbWidth(), getY() + 0.5 + random.nextFloat() * getBbHeight(),
                getZ() + random.nextFloat() * getBbWidth() * 2 - getBbWidth(), random.nextGaussian() * 0.02, random.nextGaussian() * 0.02, random.nextGaussian() * 0.02);
    }

    @Override protected void customServerAiStep() {
        if (updateCombat) { updateCombat = false; setCombatTask(); }
        super.customServerAiStep();
        if (tickCount % 40 == 0) heal(1.0F);
    }

    @Override public void handleEntityEvent(byte event) {
        switch (event) {
            case 16 -> mumble = Mth.PI;
            case 17 -> mumble = Mth.TWO_PI;
            case 18 -> { for (int i = 0; i < 5; i++) particle(ParticleTypes.HAPPY_VILLAGER); }
            case 19 -> { for (int i = 0; i < 5; i++) particle(ParticleTypes.ANGRY_VILLAGER); mumble = Mth.TWO_PI; }
            default -> super.handleEntityEvent(event);
        }
    }

    @Override public void playAmbientSound() {
        if (!level().isClientSide) {
            boolean company = random.nextInt(3) == 0 && !level().getEntitiesOfClass(Pech.class, getBoundingBox().inflate(4.0, 2.0, 4.0), p -> p != this).isEmpty();
            if (company) { level().broadcastEntityEvent(this, (byte) 17); playSound(Tc4Legacy.sound("pech_trade"), getSoundVolume(), getVoicePitch()); return; }
            level().broadcastEntityEvent(this, (byte) 16);
        }
        super.playAmbientSound();
    }

    @Override public void performRangedAttack(LivingEntity target, float power) {
        if (pechType() == STALKER) {
            var arrow = new Arrow(level(), this);
            double dx = target.getX() - getX(), dy = target.getY(0.3333) - arrow.getY(), dz = target.getZ() - getZ();
            arrow.shoot(dx, dy + Math.sqrt(dx * dx + dz * dz) * 0.2, dz, 1.6F, 14 - level().getDifficulty().getId() * 4);
            arrow.setBaseDamage(power * 2.0F + random.nextGaussian() * 0.25 + level().getDifficulty().getId() * 0.11F);
            int powerLevel = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.POWER_ARROWS, getMainHandItem());
            int punch = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PUNCH_ARROWS, getMainHandItem());
            if (powerLevel > 0) arrow.setBaseDamage(arrow.getBaseDamage() + powerLevel * 0.5 + 0.5);
            if (punch > 0) arrow.setKnockback(punch);
            playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (random.nextFloat() * 0.4F + 0.8F));
            level().addFreshEntity(arrow);
        } else if (pechType() == THAUMATURGE) {
            var blast = PechBlast.create(level(), this, 1, 0, random.nextFloat() < 0.1F);
            var motion = target.getDeltaMovement();
            double dx = target.getX() + motion.x - getX(), dy = target.getEyeY() - 1.5 - getY(), dz = target.getZ() + motion.z - getZ();
            blast.heading(dx, dy + Math.sqrt(dx * dx + dz * dz) * 0.1, dz, 1.5F, 4.0F);
            playSound(Tc4Legacy.sound("ice"), 0.4F, 1.0F + random.nextFloat() * 0.1F);
            level().addFreshEntity(blast);
        }
        swing(InteractionHand.MAIN_HAND);
    }

    private void becomeAngryAt(LivingEntity attacker) {
        if (anger() <= 0) { level().broadcastEntityEvent(this, (byte) 19); playSound(Tc4Legacy.sound("pech_charge"), getSoundVolume(), getVoicePitch()); }
        setTarget(attacker);
        setAnger(400 + random.nextInt(400));
        setTamed(false);
    }

    @Override public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source)) return false;
        if (!level().isClientSide && source.getEntity() instanceof Player player) {
            for (var pech : level().getEntitiesOfClass(Pech.class, getBoundingBox().inflate(32.0, 16.0, 32.0))) pech.becomeAngryAt(player);
        }
        return super.hurt(source, amount);
    }

    // --- Collecting -------------------------------------------------------------------------------------------------

    private static int listedValue(ItemStack stack) {
        if (Tc4Legacy.is(stack, "Thaumcraft:ItemManaBean")) return 1;
        if (stack.is(Items.GOLD_INGOT) || stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE)) return 2;
        if (stack.is(Items.ENDER_PEARL)) return 3;
        if (stack.is(Items.DIAMOND)) return 4;
        if (stack.is(Items.EMERALD)) return 5;
        return 0;
    }

    private static int greed(ItemStack stack) {
        var runtime = TcModernApi.runtime().orElse(null);
        var legacy = Tc4Legacy.legacy(stack).orElse(null);
        if (runtime == null || legacy == null) return 0;
        try { return TcItemAspects.of(runtime, legacy).map(v -> v.amounts().getOrDefault("lucrum", 0)).orElse(0); }
        catch (RuntimeException failure) { return 0; }
    }

    public static boolean isValued(ItemStack stack) { return !stack.isEmpty() && (listedValue(stack) > 0 || greed(stack) > 0); }
    public static int value(ItemStack stack) { int listed = listedValue(stack); return listed > 0 ? listed : Math.min(32, greed(stack)); }

    public boolean canCollect(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (!isTamed() && isValued(stack)) return true;
        for (var slot : loot) if (slot.isEmpty() || ItemStack.isSameItemSameTags(slot, stack) && slot.getCount() + stack.getCount() <= slot.getMaxStackSize()) return true;
        return false;
    }

    /** pickupItem: valuables may win its trust; anything else goes into the pack. Returns what is left over. */
    public ItemStack collect(ItemStack stack) {
        if (stack.isEmpty()) return stack;
        if (!isTamed() && isValued(stack)) {
            if (random.nextInt(10) < value(stack)) { setTamed(true); level().broadcastEntityEvent(this, (byte) 18); }
            stack.shrink(1);
            return stack;
        }
        for (var slot : loot) {
            if (stack.isEmpty()) break;
            if (!slot.isEmpty() && ItemStack.isSameItemSameTags(slot, stack) && slot.getCount() < slot.getMaxStackSize()) {
                int moved = Math.min(stack.getCount(), slot.getMaxStackSize() - slot.getCount());
                slot.grow(moved); stack.shrink(moved);
            }
        }
        for (int i = 0; i < loot.size() && !stack.isEmpty(); i++) if (loot.get(i).isEmpty()) { loot.set(i, stack.copy()); stack.setCount(0); }
        return stack;
    }

    private boolean hasPackLoot() { return loot.stream().anyMatch(s -> !s.isEmpty()); }

    @Override public boolean removeWhenFarAway(double distance) { return loot.stream().filter(s -> !s.isEmpty()).count() < 5; }

    /** AIPechItemEntityGoto: walks to dropped items it wants and picks them up. */
    private final class CollectGoal extends Goal {
        @Nullable private ItemEntity target;
        private int recheck;
        CollectGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean canUse() {
            if (--recheck > 0 || getTarget() != null) return false;
            recheck = 20;
            target = null;
            double best = Double.MAX_VALUE;
            for (var item : level().getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(16.0, 8.0, 16.0))) {
                if (!item.isAlive() || item.hasPickUpDelay() || !canCollect(item.getItem())) continue;
                double d = distanceToSqr(item);
                if (d < best) { best = d; target = item; }
            }
            return target != null;
        }
        @Override public boolean canContinueToUse() { return target != null && target.isAlive() && getTarget() == null && !getNavigation().isDone(); }
        @Override public void start() { if (target != null) getNavigation().moveTo(target, 0.6); }
        @Override public void tick() {
            if (target == null || !target.isAlive()) return;
            getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (distanceToSqr(target) < 2.25) {
                var rest = collect(target.getItem().copy());
                playSound(SoundEvents.ITEM_PICKUP, 0.2F, ((random.nextFloat() - random.nextFloat()) * 0.7F + 1.0F) * 2.0F);
                if (rest.isEmpty()) target.discard(); else target.setItem(rest);
                target = null;
            }
        }
    }

    // --- Trading ----------------------------------------------------------------------------------------------------

    @Nullable private Player trader;

    /** AIPechTradePlayer: a trading pech stops and faces its customer. */
    private final class TradeGoal extends Goal {
        TradeGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP)); }
        @Override public boolean canUse() { return trader != null && isTamed() && trader.isAlive() && distanceToSqr(trader) < 16.0; }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public void start() { getNavigation().stop(); }
        @Override public void tick() { if (trader != null) getLookControl().setLookAt(trader, 30.0F, 30.0F); }
    }

    /**
     * ContainerPech's trade, without the trade window: a valued item handed to a tame pech is exchanged for goods of
     * matching worth. Greedy pech may lose interest and turn wild again.
     */
    @Override protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() || stack.getItem() instanceof NameTagItem || !isTamed()) return super.mobInteract(player, hand);
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer && (trader == null || trader == player)) {
            trader = player;
            net.minecraftforge.network.NetworkHooks.openScreen(serverPlayer, new net.minecraft.world.SimpleMenuProvider(
                    (id, inventory, who) -> new github.com.gengyoubo.tcmodern.tc4.menu.PechMenu(id, inventory, this), getDisplayName()), buf -> buf.writeVarInt(getId()));
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    public boolean isTradingWith(Player player) { return trader == player; }
    public void stopTrading(Player player) { if (trader == player) trader = null; }
    public List<ItemStack> trade(ServerLevel server, Player player, int value) {
        var result = new ArrayList<ItemStack>();
        if (random.nextInt(100) <= value / 2) { setTamed(false); playSound(Tc4Legacy.sound("pech_trade"), 0.4F, 1.0F); }
        if (random.nextInt(5) == 0) value += random.nextInt(3);
        else if (random.nextBoolean()) value -= random.nextInt(3);
        var table = TRADES.get(pechType());
        while (value > 0) {
            int amount = Math.min(5, Math.max((value + 1) / 2, random.nextInt(value) + 1));
            value -= amount;
            if (amount == 1 && random.nextBoolean() && hasPackLoot()) {
                var filled = new ArrayList<Integer>();
                for (int i = 0; i < loot.size(); i++) if (!loot.get(i).isEmpty()) filled.add(i);
                var slot = loot.get(filled.get(random.nextInt(filled.size())));
                result.add(slot.split(1));
            } else if (amount >= 4 && random.nextBoolean()) {
                var params = new LootParams.Builder(server).withParameter(LootContextParams.ORIGIN, position()).withLuck(player.getLuck())
                        .withParameter(LootContextParams.THIS_ENTITY, player).create(LootContextParamSets.CHEST);
                var rolled = server.getServer().getLootData().getLootTable(BuiltInLootTables.SIMPLE_DUNGEON).getRandomItems(params);
                if (rolled.isEmpty()) value += amount; else result.add(rolled.get(random.nextInt(rolled.size())));
            } else {
                final int tier = amount;
                var choices = table.stream().filter(t -> t.tier == tier).toList();
                if (choices.isEmpty()) continue;
                var reward = choices.get(random.nextInt(choices.size())).stack.get();
                if (!reward.isEmpty()) result.add(reward);
            }
        }
        return result;
    }

    private record Trade(int tier, Supplier<ItemStack> stack) { }
    private static Trade tc(int tier, String key, int meta) { return new Trade(tier, () -> Tc4Legacy.stack(key, meta, 1)); }
    private static Trade item(int tier, net.minecraft.world.item.Item item) { return new Trade(tier, () -> new ItemStack(item)); }
    private static Trade potion(int tier, net.minecraft.world.item.alchemy.Potion potion) { return new Trade(tier, () -> PotionUtils.setPotion(new ItemStack(Items.POTION), potion)); }
    private static Trade book(int tier, net.minecraft.world.item.enchantment.Enchantment enchantment) {
        return new Trade(tier, () -> EnchantedBookItem.createForEnchantment(new EnchantmentInstance(enchantment, 1)));
    }
    private static Trade originalBook(int tier, String description) {
        return new Trade(tier, () -> net.minecraftforge.registries.ForgeRegistries.ENCHANTMENTS.getValues().stream()
                .filter(enchantment -> enchantment.getDescriptionId().equals(description)).findFirst()
                .map(enchantment -> EnchantedBookItem.createForEnchantment(new EnchantmentInstance(enchantment, 1)))
                .orElse(ItemStack.EMPTY));
    }

    /** EntityPech.tradeInventory: what foragers, thaumaturges and stalkers offer, by worth. */
    private static final List<List<Trade>> TRADES = buildTrades();
    private static List<List<Trade>> buildTrades() {
        var forager = new ArrayList<Trade>(List.of(tc(1, "Thaumcraft:ItemManaBean", 0), tc(1, "Thaumcraft:ItemNugget", 16), tc(1, "Thaumcraft:ItemNugget", 31),
                tc(1, "Thaumcraft:ItemNugget", 21), item(2, Items.BLAZE_ROD), tc(2, "Thaumcraft:blockCustomPlant", 0), potion(2, Potions.STRENGTH),
                potion(2, Potions.SWIFTNESS), item(3, Items.EXPERIENCE_BOTTLE), tc(3, "Thaumcraft:ItemResource", 9), item(3, Items.GOLDEN_APPLE),
                potion(3, Potions.LONG_STRENGTH), potion(3, Potions.LONG_NIGHT_VISION), item(5, Items.ENCHANTED_GOLDEN_APPLE), tc(4, "Thaumcraft:ItemPickThaumium", 0),
                tc(5, "Thaumcraft:blockCustomPlant", 1), tc(5, "Thaumcraft:blockCustomPlant", 1)));
        forager.add(originalBook(3, "enchantment.haste"));
        forager.add(originalBook(5, "enchantment.repair"));
        var thaumaturge = new ArrayList<Trade>(List.of(tc(1, "Thaumcraft:ItemManaBean", 0)));
        for (int a = 0; a < 6; a++) thaumaturge.add(tc(1, "Thaumcraft:ItemShard", a));
        thaumaturge.addAll(List.of(tc(1, "Thaumcraft:ItemResource", 9), tc(2, "Thaumcraft:ItemResource", 9), potion(2, Potions.REGENERATION),
                potion(2, Potions.HEALING), item(3, Items.GOLDEN_APPLE), potion(3, Potions.STRONG_REGENERATION), potion(3, Potions.STRONG_HEALING)));
        for (int a = 0; a < 7; a++) thaumaturge.add(tc(4, "Thaumcraft:blockCrystal", a));
        thaumaturge.addAll(List.of(item(5, Items.ENCHANTED_GOLDEN_APPLE), tc(5, "Thaumcraft:FocusPouch", 0), tc(5, "Thaumcraft:FocusPech", 0),
                tc(5, "Thaumcraft:ItemAmuletVis", 0)));
        var stalker = new ArrayList<Trade>(List.of(tc(1, "Thaumcraft:ItemManaBean", 0)));
        for (int a = 0; a < 15; a++) stalker.add(tc(1, "Thaumcraft:blockCandle", a));
        stalker.addAll(List.of(item(2, Items.GHAST_TEAR), potion(2, Potions.SWIFTNESS), potion(2, Potions.STRENGTH), book(2, Enchantments.POWER_ARROWS),
                item(3, Items.EXPERIENCE_BOTTLE), tc(3, "Thaumcraft:ItemResource", 9), potion(3, Potions.LONG_INVISIBILITY), potion(3, Potions.STRONG_REGENERATION),
                item(3, Items.GOLDEN_APPLE), item(5, Items.ENCHANTED_GOLDEN_APPLE), tc(4, "Thaumcraft:ItemBootsThaumium", 0), tc(5, "Thaumcraft:ItemRingRunic", 0),
                book(5, Enchantments.FLAMING_ARROWS), book(5, Enchantments.INFINITY_ARROWS)));
        return List.of(List.copyOf(forager), List.copyOf(thaumaturge), List.copyOf(stalker));
    }

    // --- Drops, sounds, saving --------------------------------------------------------------------------------------

    @Override protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        for (var stack : loot) if (!stack.isEmpty() && random.nextFloat() < 0.88F) Tc4Legacy.drop(this, stack.copy(), 1.5F);
        for (int a = 0; a < 1 + looting; a++)
            if (random.nextBoolean()) Tc4Legacy.drop(this, Tc4Legacy.withAspect("Thaumcraft:ItemManaBean", PRIMALS[random.nextInt(PRIMALS.length)], 1), 1.5F);
        if (random.nextInt(10) < 1 + looting) Tc4Legacy.drop(this, Tc4Legacy.stack("Thaumcraft:ItemResource", 18, 1), 1.5F);
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        if (recentlyHit && random.nextFloat() < 0.025F + looting * 0.01F) Tc4Legacy.drop(this, Tc4Legacy.stack("Thaumcraft:ItemResource", 9, 1), 1.5F);
    }

    @Override public float getStandingEyeHeight(net.minecraft.world.entity.Pose pose, net.minecraft.world.entity.EntityDimensions size) { return size.height * 0.66F; }
    @Override public int getAmbientSoundInterval() { return 120; }
    @Override protected float getSoundVolume() { return 0.4F; }
    @Override protected SoundEvent getAmbientSound() { return Tc4Legacy.sound("pech_idle"); }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return Tc4Legacy.sound("pech_hit"); }
    @Override protected SoundEvent getDeathSound() { return Tc4Legacy.sound("pech_death"); }
    @Override public boolean isAlliedTo(Entity other) { return other instanceof Pech || super.isAlliedTo(other); }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("PechType", (byte) pechType()); tag.putShort("Anger", (short) anger()); tag.putBoolean("Tamed", isTamed());
        tag.put("Tc4Loot", ContainerHelper.saveAllItems(new CompoundTag(), loot, true));
        legacy.save(this, tag);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("PechType")) setPechType(tag.getByte("PechType"));
        setAnger(tag.getShort("Anger")); setTamed(tag.getBoolean("Tamed"));
        loot.replaceAll(s -> ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag.getCompound("Tc4Loot"), loot);
        updateCombat = true;
        legacy.load(tag);
    }

    @LegacyType(LEGACY_CLASS) public interface Old {
        @LegacyMethod(name = "getPechType", descriptor = "()I") int type();
        @LegacyMethod(name = "getAnger", descriptor = "()I") int anger();
        @LegacyMethod(name = "isTamed", descriptor = "()Z") boolean tamed();
    }
    @LegacyType("thaumcraft.common.items.wands.ItemWandCasting") public interface WandItem {
        @LegacyMethod(name = "setFocus", descriptor = "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemStack;)V") void setFocus(LegacyObject wand, LegacyObject focus);
        @LegacyMethod(name = "addVis", descriptor = "(Lnet/minecraft/item/ItemStack;Lthaumcraft/api/aspects/Aspect;IZ)I")
        int addVis(LegacyObject stack, LegacyObject aspect, int amount, boolean doit);
    }
}
