package net.hat.gt.entities;

import com.jab125.util.tradehelper.EntityTrades;
import com.jab125.util.tradehelper.TradeManager;
import com.jab125.util.tradehelper.TradeRarity;
import net.hat.gt.GobT;
import net.hat.gt.entities.ai.*;
import net.hat.gt.init.ModSounds;
import net.hat.gt.init.ModStats;
import net.hat.gt.trades.UpgradedTradeOffer;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.goal.LookAtCustomerGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.TemptGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.recipe.Ingredient;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.TradeOffers;
import net.minecraft.world.GameRules;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.jab125.thonkutil.util.Util.secondsToTick;

public abstract class AbstractGoblinEntity extends MerchantEntity implements Npc {
    @Nullable
    private BlockPos wanderTarget;

    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection") // this is popping up as an error incorrectly, this is the fix.
    private final Set<UUID> tradedCustomers = new HashSet<>();

    private int despawnDelay;

    private int stunDelay;
    private int fallCounter;
    private static final TrackedData<? super Float> STUN_ROTATION = DataTracker.registerData(AbstractGoblinEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<? super Boolean> STUNNED = DataTracker.registerData(AbstractGoblinEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<? super Boolean> RAINING = DataTracker.registerData(AbstractGoblinEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<? super ItemStack> FOODS = DataTracker.registerData(AbstractGoblinEntity.class, TrackedDataHandlerRegistry.ITEM_STACK);

    //register Goblin to Exist
    public AbstractGoblinEntity(EntityType<? extends MerchantEntity> entityType, World world) {
        super(entityType, world);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new StunGoal(this));
        this.goalSelector.add(1, new GoblinSwimGoal(this));
        this.goalSelector.add(1, new FirePanicGoal(this, 0.5F));
        this.goalSelector.add(2, new TradeWithPlayerGoal(this));
        this.goalSelector.add(2, new LookAtCustomerGoal(this));
        this.goalSelector.add(2, new AttackRevengeTargetGoal(this));
        this.goalSelector.add(3, new EatFavouriteFoodGoal(this));
        this.goalSelector.add(4, new FindPreferredFoodsGoal(this));
        for (ItemStack food : this.getPreferredFoods())
        this.goalSelector.add(5, new TemptGoal(this, 0.45F, Ingredient.ofStacks(food), false));
        this.goalSelector.add(6, new FollowPotentialCustomerGoal(this));
        this.goalSelector.add(7, new WanderAroundFarGoal(this, 0.35D));
        this.goalSelector.add(8, new LookAtEntityGoal(this, MobEntity.class, 8.0F));
    }

    public int getFallCounter()
    {
        return this.fallCounter;
    }

    @Override
    protected void afterUsing(TradeOffer offer) {
        if (offer.shouldRewardPlayerExperience()) {
            int i = offer instanceof UpgradedTradeOffer ? ((UpgradedTradeOffer) offer).getPlayerExperience() : offer.getMerchantExperience() * 10;
            this.world.spawnEntity(new ExperienceOrbEntity(this.world, this.getX(), this.getY() + 0.5D, this.getZ(), i));
        }
    }

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (this.isAlive() && !this.hasCustomer() && (this.isFireImmune() || !this.isOnFire()) && !isStunned()) {
            if (hand.equals(Hand.MAIN_HAND)) {
                player.incrementStat(ModStats.TRADE_WITH_GOBLIN);
            }
            if (!this.getOffers().isEmpty()) {
                if (!this.world.isClient) {
                    this.setCurrentCustomer(player);
                    this.sendOffers(player, this.getDisplayName(), 1);
                }
            }
            return ActionResult.success(this.world.isClient);
        } else {
            return super.interactMob(player, hand);
        }
    }

    @Override
    public ItemStack eatFood(World level, ItemStack stack) {
        if(stack.getItem() == this.getFavouriteFood().getItem() && stack.getItem().getFoodComponent() != null && this.isAlive())
        {
            this.setHealth(this.getHealth() + stack.getItem().getFoodComponent().getHunger());
        }
        return super.eatFood(level, stack);
    }

    @Override
    protected void consumeItem(){
        Hand hand = this.getActiveHand();
        if (!this.activeItemStack.equals(this.getStackInHand(hand))) {
            this.stopUsingItem();
        } else {
            if (!this.activeItemStack.isEmpty() && this.isUsingItem()) {
                this.spawnConsumptionEffects(getFavouriteFood(), 16);
                ItemStack itemStack = this.activeItemStack.finishUsing(this.world, this);
                if (itemStack != this.activeItemStack) {
                    this.setStackInHand(hand, itemStack);
                }

                this.clearActiveItem();
            }

        }
    }

    @Override
    protected void spawnConsumptionEffects(ItemStack stack, int count) {
        if (!stack.isEmpty() && this.isUsingItem()) {
            if (stack.getUseAction() == UseAction.DRINK) {
                this.playSound(this.getDrinkSound(stack), 0.5F, this.world.random.nextFloat() * 0.1F + 0.9F);
            }

            if (stack.getUseAction() == UseAction.EAT) {
                this.spawnFoodParticles(stack, count);
                this.playSound(this.getEatSound(stack), 0.5F + 0.5F * (float)this.random.nextInt(2), (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
            }

        }
    }

    protected void spawnFoodParticles(ItemStack stack, int count) {
        for(int i = 0; i < count; ++i)
        {
            Vec3d frontPosition = Vec3d.fromPolar(0F, this.bodyYaw).multiply(0.25);
            frontPosition = frontPosition.add(0, 0.35, 0);
            frontPosition = frontPosition.add(this.getPos());
            Vec3d motion = new Vec3d(this.getRandom().nextDouble() * 0.2 - 0.1, 0.1, this.getRandom().nextDouble() * 0.2 - 0.1);
            if(this.world instanceof ServerWorld)
            {
                ((ServerWorld) this.world).spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, stack), frontPosition.x, frontPosition.y, frontPosition.z, 1, motion.x, motion.y + 0.05D, motion.z, 0.0D);
            }
            else
            {
                this.world.addParticle(new ItemStackParticleEffect(ParticleTypes.ITEM, stack), frontPosition.x, frontPosition.y, frontPosition.z, motion.x, motion.y + 0.05D, motion.z);
            }
        }
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("DespawnDelay", this.despawnDelay);
        nbt.putBoolean("IsStunned", this.isStunned());
        nbt.putInt("StunDuration", this.stunDelay);
        nbt.putFloat("StunRotation", this.getStunRotation());
        if (this.wanderTarget != null) {
            nbt.put("WanderTarget", NbtHelper.fromBlockPos(this.wanderTarget));
        }
    }


    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("DespawnDelay", 99)) {
            this.despawnDelay = nbt.getInt("DespawnDelay");
        }
        if (nbt.contains("WanderTarget")) {
            this.wanderTarget = NbtHelper.toBlockPos(nbt.getCompound("WanderTarget"));
        }
        if (nbt.contains("IsStunned")) {
            this.dataTracker.set(STUNNED, nbt.getBoolean("IsStunned"));
        }
        if (nbt.contains("StunDuration")) {
           this.stunDelay = nbt.getInt("StunDuration");
        }
        if (nbt.contains("StunRotation")) {
            this.dataTracker.set(STUN_ROTATION, nbt.getFloat("StunRotation"));
        }

        this.setBreedingAge(Math.max(0, this.getBreedingAge()));
    }

    @Nullable
    @Override
    public PassiveEntity createChild(ServerWorld world, PassiveEntity entity) {
        return null;
    }

    @Override
    public boolean canRefreshTrades() {
        return super.canRefreshTrades();
    }

    @Override
    public void sendOffers(PlayerEntity player, Text test, int levelProgress) {
        super.sendOffers(player, test, levelProgress);
    }

    @Override
    public boolean isLeveledMerchant() {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.IDLE_GRUNT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.IDLE_GRUNT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.IDLE_GRUNT;
    }

    @Override
    protected SoundEvent getTradingSound(boolean sold) {
        return (sold ? ModSounds.IDLE_GRUNT : ModSounds.ANNOYED_GRUNT);
    }

    public boolean isPreviousCustomer(PlayerEntity player)
    {
        return !this.tradedCustomers.contains(player.getUuid());
    }

    @Override
    public boolean damage(DamageSource source, float amount)
    {
        boolean attacked = super.damage(source, amount);
        if(attacked && source.getAttacker() instanceof PlayerEntity && GobT.config.ALL_GOBLIN_TRADERS_CONFIG.FALL)
        {
            this.getNavigation().stop();
            this.dataTracker.set(STUNNED, true);
            if (!isStunned())
            this.dataTracker.set(STUN_ROTATION, this.getStunRotation(source.getAttacker()));
            this.stunDelay = Math.max(GobT.config.ALL_GOBLIN_TRADERS_CONFIG.CAN_GET_KNOCKED_OUT ?
                    amount > this.getMaxHealth() - 5 ?
                            secondsToTick(60)
                            : Math.min(secondsToTick(30), 20 + (int) (amount * 2))
                    : Math.min(secondsToTick(30), 20 + (int) (amount * 2)), this.stunDelay);
        }
        return attacked;
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(STUNNED, false);
        this.dataTracker.startTracking(FOODS, ItemStack.EMPTY);
        this.dataTracker.startTracking(STUN_ROTATION, 0F);
        this.dataTracker.startTracking(RAINING, false);
    }

    @Override
    public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason, @Nullable EntityData entityData, @Nullable NbtCompound entityNbt) {
        addToInventoryOnSpawn();
        return super.initialize(world, difficulty, spawnReason, entityData, entityNbt);
    }

    private float getStunRotation(@Nullable Entity entity)
    {
        return entity != null ? entity.getYaw() : 0F;
    }

    public boolean isStunned()
    {
        return (boolean) this.dataTracker.get(STUNNED);
    }

    public float getStunRotation()
    {
        return (float) this.dataTracker.get(STUN_ROTATION);
    }

    public boolean isRaining()
    {
        return (boolean) this.dataTracker.get(RAINING);
    }

    public boolean isHoldingItem(Hand hand) {
        return !this.getStackInHand(hand).isEmpty();
    }

    public void tick() {
        if (this.isStunned()) this.resetCustomer();
        if (this.stunDelay > 0) {
            this.stunDelay--;
            if (this.stunDelay == 0) {
                this.dataTracker.set(STUNNED, false);
                this.world.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.ANNOYED_GRUNT, SoundCategory.NEUTRAL, 1.0F, 0.9F + this.getRandom().nextFloat() * 0.2F);
                if (GobT.config.ALL_GOBLIN_TRADERS_CONFIG.NO_ATTACK_CREATIVE) {
                    try {
                        if (this.getAttacker() != null) {
                            if (this.getAttacker().isPlayer()) {
                                if (((PlayerEntity) Objects.requireNonNull(this.getAttacker())).isCreative()) {
                                    this.setAttacker(null);
                                }
                            }
                        }
                    } catch(NullPointerException ignored) {}
                }
            }
        }
        if ((boolean) this.dataTracker.get(STUNNED)) {
            if (this.fallCounter < 10) {
                this.fallCounter++;
            }
        } else {
            this.fallCounter = 0;
        }
        if (this.world.isRaining() && this.isBeingRainedOn()){
            this.dataTracker.set(RAINING, true);
        }
        else
        {this.dataTracker.set(RAINING, false);}
        if(!this.world.isClient && !this.isPersistent())
        {
            this.handleDespawn();
        }

        super.tick();
    }

    private boolean isBeingRainedOn() {
        BlockPos blockPos = this.getBlockPos();
        return this.world.hasRain(blockPos) || this.world.hasRain(new BlockPos(blockPos.getX(), this.getBoundingBox().maxY, blockPos.getZ()));
    }

    private void handleDespawn()
    {
        if(this.despawnDelay > 0 && !this.hasCustomer() && --this.despawnDelay == 0)
        {
            this.remove(RemovalReason.KILLED);
        }
    }

    @Override
    protected float getActiveEyeHeight(EntityPose pose, EntityDimensions dimensions) {
        return 0.62F;
    }

    public abstract ItemStack getFavouriteFood();

    public Collection<ItemStack> getPreferredFoods() {
        Collection<ItemStack> preferredFoods = new ArrayList<>();
        preferredFoods.add(getFavouriteFood());
        return preferredFoods;
    }

    public abstract boolean canAttackBack();

    public abstract boolean canSwimToFood();
    public void addFoodToStorage(ItemStack food) {
        this.getInventory().addStack(food);
    }

    @Override
    protected void dropInventory() {
        if (this.world.getGameRules().getBoolean(GameRules.DO_MOB_LOOT)) {
            List<ItemStack> inventory = this.getInventory().clearToList();
            for (ItemStack currentItem : inventory) {
                this.dropStack(currentItem);
            }

        }
    }

    protected void addToInventoryOnSpawn() {
        for (int i = Math.min((int) (Math.random() * 5) + 1, this.getFavouriteFood().getMaxCount()); i > 0; i--)
            this.getInventory().addStack(this.getFavouriteFood().copy());
    }
    @Override
    public TradeOfferList getOffers()
    {
        if(this.offers == null)
        {
            this.offers = new TradeOfferList();
            this.populateTradeData();
        }
        return this.offers;
    }

    public void populateTradeData() {
        TradeOfferList offers = this.getOffers();
        @SuppressWarnings("unchecked")
        EntityTrades entityTrades = TradeManager.instance().getTrades((EntityType<? extends AbstractGoblinEntity>) this.getType());
        if(entityTrades != null)
        {
            Map<TradeRarity, List<TradeOffers.Factory>> tradeMap = entityTrades.getTradeMap();
            for(TradeRarity rarity : TradeRarity.values())
            {
                List<TradeOffers.Factory> trades = tradeMap.get(rarity);
                int min = rarity.getMinimum().apply(trades, this.getRandom());
                int max = rarity.getMaximum().apply(trades, this.getRandom());
                this.addTrades(offers, trades, Math.max(min, max), rarity.shouldShuffle());
            }
        }
    }
    protected void addTrades(TradeOfferList offers, @Nullable List<TradeOffers.Factory> trades, int max, boolean shuffle)
    {
        if(trades == null)
            return;
        List<Integer> randomIndexes = IntStream.range(0, trades.size()).boxed().collect(Collectors.toList());
        if(shuffle) Collections.shuffle(randomIndexes);
        randomIndexes = randomIndexes.subList(0, Math.min(trades.size(), max));
        for(Integer index : randomIndexes)
        {
            TradeOffers.Factory trade = trades.get(index);
            TradeOffer offer = trade.create(this, this.getRandom());
            if(offer != null)
            {
                offers.add(offer);
            }
        }
    }
    @Override
    protected void fillRecipes() {

    }

    public abstract int minSpawnHeight();

    public abstract int maxSpawnHeight();

    public abstract int spawnDelay();

    public abstract int spawnChance();

    public abstract boolean canSpawn();
}