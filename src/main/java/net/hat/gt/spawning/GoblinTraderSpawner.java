package net.hat.gt.spawning;

import net.hat.gt.entities.AbstractGoblinEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.GameRules;
import net.minecraft.world.SpawnHelper;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Reworked for fabric
 */
public class GoblinTraderSpawner
{
    private final GoblinData data;
    private final EntityType<? extends AbstractGoblinEntity> entityType;
    private int delayBeforeSpawnLogic;
    private final int traderSpawnDelay;
    private final int traderSpawnChance;
    private int currentTraderSpawnDelay;
    private int currentTraderSpawnChance;
    private int minLevel;
    private int maxLevel;

    public GoblinTraderSpawner(MinecraftServer server, String key, EntityType<? extends AbstractGoblinEntity> entityType, AbstractGoblinEntity entity) {
        this.data = GoblinTraderData.get(server).getGoblinData(key);
        this.entityType = entityType;
        this.delayBeforeSpawnLogic = 600;
        this.currentTraderSpawnDelay = this.data.getGoblinTraderSpawnDelay();
        this.currentTraderSpawnChance = this.data.getGoblinTraderSpawnChance();
        this.traderSpawnChance = entity.canSpawn() ? 0 : entity.spawnChance();
        this.traderSpawnDelay = entity.spawnDelay();
        this.minLevel = Math.min(entity.minSpawnHeight(), entity.maxSpawnHeight());
        if (Objects.requireNonNull(server.getWorld(World.OVERWORLD)).getDimension().getMinimumY() >= minLevel) {
            this.minLevel = Objects.requireNonNull(server.getWorld(World.OVERWORLD)).getDimension().getMinimumY();
        }
        this.maxLevel = Math.max(entity.minSpawnHeight(), entity.maxSpawnHeight());
        if(this.currentTraderSpawnDelay == 0 && this.currentTraderSpawnChance == 0)
        {
            this.currentTraderSpawnDelay = this.traderSpawnDelay;
            this.currentTraderSpawnChance = this.traderSpawnChance;
            this.data.setGoblinTraderSpawnDelay(this.currentTraderSpawnDelay);
            this.data.setGoblinTraderSpawnChance(this.currentTraderSpawnChance);
        }
    }

    public int tick(World level)
    {
        if(true)
        {
            if(--this.delayBeforeSpawnLogic <= 0)
            {
                int delay = Math.max(this.traderSpawnDelay / 20, 1);
                this.delayBeforeSpawnLogic = delay;
                this.currentTraderSpawnDelay -= delay;
                this.data.setGoblinTraderSpawnDelay(this.currentTraderSpawnDelay);
                if(this.currentTraderSpawnDelay <= 0)
                {
                    this.currentTraderSpawnDelay = this.traderSpawnDelay;
                    if(level.getGameRules().getBoolean(GameRules.DO_MOB_SPAWNING))
                    {
                        int spawnChance = this.currentTraderSpawnChance;
                        this.currentTraderSpawnChance = MathHelper.clamp(this.currentTraderSpawnChance + this.traderSpawnChance, this.traderSpawnChance, 100);
                        this.data.setGoblinTraderSpawnChance(this.currentTraderSpawnChance);
                        if(level.getRandom().nextInt(100) <= spawnChance)
                        {
                            if(this.spawnTrader(level))
                            {
                                this.currentTraderSpawnChance = this.traderSpawnChance;
                            }
                        }
                    }
                }
            }
        }
        return 0;
    }

    private boolean spawnTrader(World level)
    {
        List<? extends PlayerEntity> players = level.getPlayers();
        if(players.isEmpty())
        {
            return false;
        }
        PlayerEntity randomPlayer = players.get(level.getRandom().nextInt(players.size()));
        if(randomPlayer == null)
        {
            return true;
        }
        else
        {
            BlockPos blockpos = randomPlayer.getLandingPos();
            BlockPos safestPos = this.getSafePositionAroundPlayer(randomPlayer.world, blockpos, 10);
            if(safestPos != null && this.isEmptyCollision(randomPlayer.world, safestPos))
            {
                if(safestPos.getY() < this.minLevel || safestPos.getY() >= this.maxLevel)
                {
                    return false;
                }
                AbstractGoblinEntity goblin = this.entityType.spawn((ServerWorld) randomPlayer.world, null, null, null, safestPos, SpawnReason.EVENT, false, false);
                if(goblin != null)
                {
                    goblin.setDespawnCounter(this.traderSpawnDelay);
                    goblin.setPositionTarget(safestPos, 16);
                    return true;
                }
            }
            return false;
        }
    }

    @Nullable
    private BlockPos getSafePositionAroundPlayer(World level, BlockPos pos, int range)
    {
        if(range == 0)
        {
            return null;
        }
        BlockPos safestPos = null;
        for(int attempts = 0; attempts < 50; attempts++)
        {
            int posX = pos.getX() + level.getRandom().nextInt(range * 2) - range;
            int posY = pos.getY() + level.getRandom().nextInt(range) - range / 2;
            int posZ = pos.getZ() + level.getRandom().nextInt(range * 2) - range;
            BlockPos testPos = this.findGround(level, new BlockPos(posX, posY, posZ), range);
            if(testPos != null && SpawnHelper.canSpawn(SpawnRestriction.Location.ON_GROUND, level, testPos, this.entityType))
            {
                safestPos = testPos;
                break;
            }
        }
        return safestPos != null ? safestPos : this.getSafePositionAroundPlayer(level, pos, range / 2);
    }

    @Nullable
    private BlockPos findGround(World level, BlockPos pos, int maxDistance)
    {
        if(level.getBlockState(pos).isAir())
        {
            BlockPos downPos = pos;
            while(World.isValid(downPos.down()) && level.getBlockState(downPos.down()).isAir() && downPos.down().isWithinDistance(pos, maxDistance))
            {
                downPos = downPos.down();
            }
            if(!level.getBlockState(downPos.down()).isAir())
            {
                return downPos;
            }
        }
        else
        {
            BlockPos upPos = pos;
            while(World.isValid(upPos.up()) && !level.getBlockState(upPos.up()).isAir() && upPos.up().isWithinDistance(pos, maxDistance))
            {
                upPos = upPos.up();
            }
            if(!level.getBlockState(upPos.up()).isAir())
            {
                return upPos;
            }
        }
        return null;
    }

    private boolean isEmptyCollision(World level, BlockPos pos)
    {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }
}
