package com.BrassAmber.ba_bt.entity.block;

import com.BrassAmber.ba_bt.BrassAmberBattleTowers;
import com.BrassAmber.ba_bt.block.block.BTSpawnerBlock;
import com.BrassAmber.ba_bt.block.tileentity.TowerChestBlockEntity;
import com.BrassAmber.ba_bt.entity.hostile.golem.BTAbstractGolem;
import com.BrassAmber.ba_bt.init.BTBlocks;
import com.BrassAmber.ba_bt.sound.BTMusics;
import com.BrassAmber.ba_bt.util.GolemType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.sounds.MusicManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.BrassAmber.ba_bt.util.BTUtil.*;

@SuppressWarnings("DanglingJavadoc")
public class BTAbstractObelisk extends Entity {
    // Parameters that must be saved
    private static final EntityDataAccessor<Integer> TOWER = SynchedEntityData.defineId(BTAbstractObelisk.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SPAWNERS_DESTROYED = SynchedEntityData.defineId(BTAbstractObelisk.class, EntityDataSerializers.INT);

    @SuppressWarnings("FieldMayBeFinal")
    private List<BlockPos> CHESTS = new ArrayList<>(9);
    private List<List<BlockPos>> SPAWNERS;
    private List<Integer> keySpawnerAmounts;

    //Other Parameters
    private boolean initialized;
    private int checkLayer;
    private int currentFloorY;
    private boolean createSpawnerList;
    private boolean doCheck;

    private MusicManager music;
    private Minecraft mc;

    private GolemType golemType;
    private boolean justSpawnedKey;

    public Music TOWER_MUSIC = BTMusics.LAND_TOWER;
    public Music BOSS_MUSIC = BTMusics.LAND_GOLEM_FIGHT;

    // Data Strings
    private final String towerName = "Tower";
    private final String spawnersDestroyedName = "SpawnersDestroyed";

    private boolean musicPlaying;
    private boolean canCheck;
    private Class<? extends Entity> specialEnemy;

    protected int floorDistance;

    public BTAbstractObelisk(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.initialized = false;
        this.checkLayer = 1;
        // this.blocksBuilding = true;
        this.musicPlaying = false;
        this.createSpawnerList = true;
        this.doCheck = true;
        this.floorDistance = 11;

    }

    public BTAbstractObelisk(GolemType golemType, Level level) {
        this(GolemType.getObeliskFor(golemType), level);
        this.golemType = golemType;
    }

    public void findChestsAndSpawners(Level level) {
        // Monoliths are always centered on their floor
        BlockPos center = this.getOnPos();
        int nextFloorY = this.currentFloorY + this.floorDistance;

        BrassAmberBattleTowers.LOGGER.info("Floor y: " + this.currentFloorY + " Top y: " + nextFloorY);

        // Get corners of tower area.
        BlockPos corner = center.offset(-15, 0, -15);
        BlockPos oppositeCorner = center.offset(15, 0, 15);

        // Check all blocks, not needed fo land technically (could just check inside tower) but will be useful for
        // Nether/End Towers
        for (int x = corner.getX(); x < oppositeCorner.getX(); x++) {
            for (int z = corner.getZ(); z < oppositeCorner.getZ(); z++) {
                for (int y = currentFloorY; y <= nextFloorY; y++) {
                    this.checkPos(new BlockPos(x, y, z), level);
                    this.extraCheck(new BlockPos(x, y, z), level);
                }
            }
        }

        // In case a chest has previously been removed (tower partially completed) fill empty spot with null value
        // Ensures that Index of chest in chest-list matches the floor its on
        // I.E. chest for floor 6 is ID 5 (-1 for list index)
        if (this.CHESTS.size() != this.checkLayer) {
            this.CHESTS.add(null);
        }

        if (this.checkLayer == 8) {
            this.initialized = true;
        }
        else {
            this.checkLayer += 1;
            this.currentFloorY = nextFloorY;
        }

    }

    public void initialize() {
        if (this.createSpawnerList) {
            List<Integer> spawnerAmounts = towerSpawnerAmounts.get(GolemType.getNumForType(this.golemType));
            this.SPAWNERS = Arrays.asList(new ArrayList<>(spawnerAmounts.get(0)), new ArrayList<>(spawnerAmounts.get(1)),
                    new ArrayList<>(spawnerAmounts.get(2)), new ArrayList<>(spawnerAmounts.get(3)),
                    new ArrayList<>(spawnerAmounts.get(4)), new ArrayList<>(spawnerAmounts.get(5)),
                    new ArrayList<>(spawnerAmounts.get(6)), new ArrayList<>(spawnerAmounts.get(7)));

            this.keySpawnerAmounts = towerChestUnlocking.get(GolemType.getNumForType(this.golemType));
            this.createSpawnerList = false;
            this.specialEnemy = GolemType.getSpecialEnemyClass(this.golemType);
            switch (golemType) {
                default -> this.currentFloorY = this.getBlockY() - 1;
                case OCEAN -> this.currentFloorY = this.getBlockY() - 3;
                case NETHER -> this.currentFloorY = this.getBlockY() -4;
            }

        }
        this.findChestsAndSpawners(this.level);
    }


    public void checkPos(BlockPos toCheck, Level level) {
        try {
            Block block = level.getBlockState(toCheck).getBlock();
            if (block == BTBlocks.LAND_CHEST.get()) {
                this.CHESTS.add(toCheck);
                // BrassAmberBattleTowers.LOGGER.info("Found chest");
            } else if (block == BTBlocks.BT_LAND_SPAWNER.get()) {
                this.SPAWNERS.get(this.checkLayer-1).add(toCheck);
                // BrassAmberBattleTowers.LOGGER.info("Found spawner: " + this.checkLayer + " " + this.spawnersFound);
                BrassAmberBattleTowers.LOGGER.info(this.SPAWNERS.get(this.checkLayer-1).size());
            }
        } catch (Exception e) {
            BrassAmberBattleTowers.LOGGER.info("Exception in Obelisk class, not a chest or spawner: " + level.getBlockState(toCheck).getBlock());
            e.printStackTrace();

        }
    }

    public void extraCheck(BlockPos toUpdate, Level level) {}

    @Override
    public void tick() {
        super.tick();

        if (this.level.isClientSide()) {
            ClientLevel client = (ClientLevel)this.level;
            if (!this.initialized) {
                this.mc = Minecraft.getInstance();
                this.music = this.mc.getMusicManager();
            }
            if (client.players().size() == 0) {
                return;
            }

            // Make sure we have a player within range.
            boolean hasClientPlayer = client.hasNearbyAlivePlayer(this.getX(), this.getY(), this.getZ(), 100D);
            boolean playerInTowerRange;
            boolean playerInMusicRange;

            if (hasClientPlayer) {
                //noinspection ConstantConditions
                playerInTowerRange = horizontalDistanceTo(this, client.getNearestPlayer(this, 100D)) <= 30;
                //noinspection ConstantConditions
                playerInMusicRange = horizontalDistanceTo(this, client.getNearestPlayer(this, 100D)) < 17;
            } else {
                playerInTowerRange = false;
                playerInMusicRange = false;

            }

            if (!this.music.isPlayingMusic(this.BOSS_MUSIC)) {
                if (playerInTowerRange) {
                    // BrassAmberBattleTowers.LOGGER.info("Player: " + true + "  In Music Range: " + playerInMusicRange + " Tower music playing?: " + this.musicPlaying);
                    if (playerInMusicRange && !this.musicPlaying) {
                        this.music.stopPlaying();
                        this.music.nextSongDelay = this.TOWER_MUSIC.getMinDelay();
                        this.music.startPlaying(this.TOWER_MUSIC);
                        this.musicPlaying = true;
                    }
                } else if (this.musicPlaying) {
                    this.music.nextSongDelay = 500;
                    this.music.stopPlaying();
                    this.musicPlaying = false;
                }
            }
            return;
        }

        if (!this.initialized) {
            // BrassAmberBattleTowers.LOGGER.info("Finding Chests for layer: " + this.checkLayer + "  At block level: " + this.currentFloorY);
            this.initialize();
            return;
        }

        if (this.doCheck) {
            try {
                List<?> list = this.level.getEntitiesOfClass(BTMonolith.class, this.getBoundingBox().inflate(15, 110, 15));
                this.canCheck = list.size() != 0;
                if (!this.canCheck) {
                    try {
                        List<?> list2 = this.level.getEntitiesOfClass(BTAbstractGolem.class, this.getBoundingBox().inflate(15, 110, 15));
                        this.canCheck = list2.size() != 0;
                    } catch (Exception f) {
                        BrassAmberBattleTowers.LOGGER.info("Exception finding Golem: " + f);
                    }
                }
            } catch (Exception e) {
                BrassAmberBattleTowers.LOGGER.info("Exception finding Monolith: " + e);
            }
        }

        if (canCheck) {
            List<ServerPlayer> players = Objects.requireNonNull(this.level.getServer()).getPlayerList().getPlayers();
            List<Boolean> playersClose = new ArrayList<>();
            for (ServerPlayer player : players
            ) {
                if (horizontalDistanceTo(this, player) < 30) {
                    playersClose.add(Boolean.TRUE);
                    // BrassAmberBattleTowers.LOGGER.info("Player " +  this.horizontalDistanceTo(player) + " blocks away");
                } else {
                    playersClose.add(Boolean.FALSE);
                }

            }

            boolean hasPlayer = Collections.frequency(playersClose, Boolean.TRUE) > 0;

            int timeCheck = (this.random.nextInt(2) + 4) * 10;

            if (this.tickCount % timeCheck == 0) {
                List<? extends Entity> specialEnemies = this.level.getEntitiesOfClass(this.specialEnemy, this.getBoundingBox().inflate(15, 110, 15));
                if (specialEnemies.size() < 10) {
                    int floor = this.blockPosition().getY() + this.random.nextInt(8) * 11;
                    int x = this.blockPosition().getX() + this.random.nextInt(24) - 12;
                    int y = floor + this.random.nextInt(9);
                    int z = this.blockPosition().getZ() + this.random.nextInt(24) - 12;

                    ServerLevel serverWorld = (ServerLevel) this.level;

                    switch (this.golemType) {
                        case LAND -> this.spawnSpecialEnemy(serverWorld, new BlockPos(x, y, z),
                                0D, 11.5D, true);
                        case OCEAN -> this.spawnSpecialEnemy(serverWorld, new BlockPos(x, y, z),
                                12.5D, 17.5D, false);
                        case CORE -> this.spawnSpecialEnemy(serverWorld, new BlockPos(x, y, z),
                                12.5D, 17.5D, true);
                    }
                }
            }

            if (this.tickCount % 20 == 0 && hasPlayer) {
                // BrassAmberBattleTowers.LOGGER.info("Checking Spawners");
                this.checkSpawners(this.level);
            }
        }

    }

    protected void spawnSpecialEnemy(ServerLevel serverWorld, BlockPos spawn, double lowerRadiusBound,
                                     double upperRadiusBound, boolean onGround) {
        // BrassAmberBattleTowers.LOGGER.info("Trying to spawn cultist at: " + spawn);
        double distance = horizontalDistanceTo(this, spawn.getX(), spawn.getZ());
        boolean canSpawn = SpawnPlacements.checkSpawnRules(GolemType.getSpecialEnemyType(this.golemType), serverWorld, MobSpawnType.STRUCTURE, spawn, this.random);
        boolean acceptableDistance = lowerRadiusBound < distance && distance < upperRadiusBound;

        if (canSpawn && acceptableDistance && serverWorld.getBlockState(spawn.above()).isAir()) {
            Entity entity = GolemType.getSpecialEnemy(this.golemType, serverWorld);
            if (entity instanceof Mob mob) {
                mob.setPos(spawn.getX(), spawn.getY(), spawn.getZ());
                mob.finalizeSpawn(serverWorld, serverWorld.getCurrentDifficultyAt(this.blockPosition()), MobSpawnType.TRIGGERED, null, null);
                serverWorld.addFreshEntity(entity);
                // BrassAmberBattleTowers.LOGGER.info("Success");
            }
        }
    }

    private void checkSpawners(Level level) {
        // Make sure there are chests && spawners in the tower (tower has not been cleared)
        if (this.SPAWNERS.size() == 0 || this.CHESTS.size() == 0) {
            this.doCheck = false;
            this.canCheck = false;
        } else {
            // Main loop to iterate over each 'floor' contained in the spawners list
            for (int i = 0; i < this.SPAWNERS.size(); i++) {
                if (this.SPAWNERS.get(i).size() == 0) {
                    // If no spawners left on the floor unlock the chest.
                    if (this.CHESTS.get(i) != null && level.getBlockEntity(this.CHESTS.get(i)) instanceof TowerChestBlockEntity chestBlockEntity) {
                        if (!chestBlockEntity.isUnlocked()) {
                            chestBlockEntity.setUnlocked(true);
                            this.chestUnlockingSound(level);
                            this.CHESTS.set(i, null);
                        }
                    }
                } else {
                    List<BlockPos> poss = this.SPAWNERS.get(i);
                    for (int x = 0; x < poss.size(); x++) {
                        BlockPos blockPos = poss.get(x);
                        if (!(level.getBlockState(blockPos).getBlock() instanceof BTSpawnerBlock)) {
                            this.SPAWNERS.get(i).remove(blockPos);
                            this.setSpawnersDestroyed(this.getSpawnersDestroyed() + 1);
                            BrassAmberBattleTowers.LOGGER.info(this.getSpawnersDestroyed());

                            if (this.justSpawnedKey) {
                                this.justSpawnedKey = false;
                            }
                        }
                    }
                    if (this.keySpawnerAmounts.contains(this.getSpawnersDestroyed()) && !justSpawnedKey) {
                        if (this.CHESTS.get(i) != null && level.getBlockEntity(this.CHESTS.get(i)) instanceof TowerChestBlockEntity chest) {
                            // chest.setLootTable(BrassAmberBattleTowers.locate("chests/" + GolemType.getNameForNum(this.getTower())+ "_tower/" + (i+1) + "key"), this.random.nextLong());
                            chest.unpackLootTable(null);
                            NonNullList<ItemStack> stack = chest.getItems();
                            stack.set(13, GolemType.getKeyFor(this.golemType).getDefaultInstance());
                            chest.setItems(stack);
                        }
                        else if (this.CHESTS.get(i) != null) {
                            doNoOutputPostionedCommand(this, "give @p ba_bt:" + GolemType.getKeyFor(this.golemType).getRegistryName(), new Vec3(this.blockPosition().getX(), this.blockPosition().getY() + (11 * i), this.blockPosition().getZ()));
                            this.CHESTS.set(i, null);
                        }
                        this.justSpawnedKey = true;
                    }
                }
            }
        }

    }

    private void chestUnlockingSound(Level level) {
        List<ServerPlayer> players = Objects.requireNonNull(level.getServer()).getPlayerList().getPlayers();
        for (ServerPlayer player: players) {
            if (horizontalDistanceTo(this, player) < 30) {
                level.playSound(null, player.blockPosition(), SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1f, 1.5f);
            }
        }
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(SPAWNERS_DESTROYED, 0);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.golemType = GolemType.getTypeForName(tag.getString(this.towerName));
        this.setSpawnersDestroyed(tag.getInt(this.spawnersDestroyedName));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString(this.towerName, this.golemType.getSerializedName());
        tag.putInt(this.spawnersDestroyedName, this.getSpawnersDestroyed());
        if (this.level.isClientSide()) {
            ((ClientLevel) this.level).minecraft.getMusicManager().stopPlaying();
        }
    }
    /*************************************** Characteristics & Properties *******************************************/

    /**
     * Called when a user uses the creative pick block button on this entity.
     * @return An ItemStack to add to the player's inventory, empty ItemStack if nothing should be added.
     * (Empty ItemStack is an ItemStack of '(Item) null')
     */
    @Override
    public ItemStack getPickedResult(HitResult target) {
        return new ItemStack((Item) null);
    }


    /**
     * {@link PushReaction.IGNORE} is the only valid option for an entity I think to stop piston interaction
     * TODO I want this to Block the pistons movement
     *
     * Used in: {@link PistonTileEntity.moveCollidedEntities method}
     */
    @SuppressWarnings("JavadocReference")
    @Override
    public @NotNull PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    @Override
    public boolean ignoreExplosion() {
        return true;
    }

    /**
     * Returns true if other Entities should be prevented from moving through this Entity.
     * (Like arrows and stuff.)
     */
    @Override
    public boolean isPickable() {
        return this.isAlive();
    }

    /**
     * Block movement through this entity
     */
    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    /***************************************************** Breaking *************************************************/

    /**
     * Called by the /kill command.
     */
    @Override
    public void kill() {
        Player player = this.level.getNearestPlayer(this.getX(), this.getY(), this.getZ(), 50, EntitySelector.NO_SPECTATORS);

        if (player != null && player.isCreative()) {
            BrassAmberBattleTowers.LOGGER.info("Item: " + player.getItemInHand(InteractionHand.MAIN_HAND).getItem());
            if (player.getItemInHand(InteractionHand.MAIN_HAND).getItem() == Items.CLAY_BALL) {
                this.remove(RemovalReason.KILLED);
            } else {
                // Do nothing to prevent people deleting a Monolith by accident.
                BrassAmberBattleTowers.LOGGER.info("Used the /kill command. However, an Obelisk has been saved at: " + Math.round(this.getX()) + "X " + Math.round(this.getY()) + "Y " + Math.round(this.getZ()) + "Z.");
            }
        }
        else {
            // Do nothing to prevent people deleting a Monolith by accident.
            BrassAmberBattleTowers.LOGGER.info("Used the /kill command. However, an Obelisk has been saved at: " + Math.round(this.getX()) + "X " + Math.round(this.getY()) + "Y " + Math.round(this.getZ()) + "Z.");
        }

    }

    /**
     * Called when the entity is attacked.
     */
    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) {
            return false;
        } else if (!(source.getMsgId().equals("player"))) {
            return false;
        } else {
            if (this.isAlive() && !this.level.isClientSide() && source.isCreativePlayer()) {
                Player player = (Player) source.getEntity();
                if (player.getItemInHand(InteractionHand.MAIN_HAND).getItem() == Items.CLAY_BALL) {
                    this.remove(RemovalReason.KILLED);
                }
            }
            return true;
        }
    }

    @Override
    public MobCategory getClassification(boolean forSpawnCount) {
        return MobCategory.AMBIENT;
    }

    /************************************************** DATA SET/GET **************************************************/

    public void setSpawnersDestroyed(int num) {
        this.entityData.set(SPAWNERS_DESTROYED, num);
    }

    public int getSpawnersDestroyed() {
        return this.entityData.get(SPAWNERS_DESTROYED);
    }

    /************************************************** COMMANDS **************************************************/



    @Override
    public @NotNull Packet<?> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}