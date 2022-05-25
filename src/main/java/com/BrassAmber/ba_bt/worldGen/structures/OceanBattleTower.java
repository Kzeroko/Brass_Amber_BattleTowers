package com.BrassAmber.ba_bt.worldGen.structures;

import com.BrassAmber.ba_bt.BattleTowersConfig;
import com.BrassAmber.ba_bt.BrassAmberBattleTowers;
import com.BrassAmber.ba_bt.block.tileentity.BTSpawnerBlockEntity;
import com.BrassAmber.ba_bt.util.BTUtil;
import com.BrassAmber.ba_bt.worldGen.BTLandJigsawPlacement;
import com.BrassAmber.ba_bt.worldGen.BTOceanJigsawPlacement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.feature.StructureFeature;
import net.minecraft.world.level.levelgen.feature.configurations.JigsawConfiguration;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.BuiltinStructureSets;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.pieces.PieceGenerator;
import net.minecraft.world.level.levelgen.structure.pieces.PieceGeneratorSupplier;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Predicate;

import static com.BrassAmber.ba_bt.util.BTUtil.horizontalDistanceTo;


// Comments from TelepathicGrunts

public class OceanBattleTower extends StructureFeature<JigsawConfiguration> {

    private static final int firstTowerDistance = BattleTowersConfig.firstTowerDistance.get();
    private static final int minimumSeparation = BattleTowersConfig.oceanMinimumSeperation.get();
    private static final int seperationRange = BattleTowersConfig.oceanAverageSeperationModifier.get();
    private static ChunkPos lastSpawnPosition = ChunkPos.ZERO;
    private static BlockPos SpawnPos;

    public static final Codec<JigsawConfiguration> CODEC = RecordCodecBuilder.create((codec) -> codec.group(StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter(JigsawConfiguration::startPool),
            Codec.intRange(0, 40).fieldOf("size").forGetter(JigsawConfiguration::maxDepth)
    ).apply(codec, JigsawConfiguration::new));

    public OceanBattleTower() {
        super(CODEC, OceanBattleTower::createPiecesGenerator, OceanBattleTower::afterPlace);
    }

    @Override
    public GenerationStep.@NotNull Decoration step() {
        return GenerationStep.Decoration.SURFACE_STRUCTURES;
    }


    public static BlockPos isSpawnableChunk(PieceGeneratorSupplier.Context<JigsawConfiguration> context, WorldgenRandom worldgenRandom) {

        ChunkPos chunkPos = context.chunkPos();
        int seaLevel = context.chunkGenerator().getSeaLevel();
        BlockPos centerOfChunk = context.chunkPos().getMiddleBlockPosition(0);

        List<ResourceKey<StructureSet>> vanillaStructures = new ArrayList<>();
        vanillaStructures.add(BuiltinStructureSets.OCEAN_RUINS);
        vanillaStructures.add(BuiltinStructureSets.OCEAN_MONUMENTS);
        vanillaStructures.add(BuiltinStructureSets.RUINED_PORTALS);
        vanillaStructures.add(BuiltinStructureSets.SHIPWRECKS);


        for (ResourceKey<StructureSet> set : vanillaStructures) {
            // BrassAmberBattleTowers.LOGGER.info(context.chunkGenerator().hasFeatureChunkInRange(set, context.seed(), chunkPos.x, chunkPos.z, 3));

            if (context.chunkGenerator().hasFeatureChunkInRange(set, context.seed(), chunkPos.x, chunkPos.z, 3)) {
                // BrassAmberBattleTowers.LOGGER.info("Has " + set + " Feature in range");
                return BlockPos.ZERO;
            }
        }

        // Test/Check surrounding chunks for possible spawns
        List<BlockPos> testables = new ArrayList<>(List.of(
                centerOfChunk,
                new BlockPos(centerOfChunk.getX(), centerOfChunk.getY(), centerOfChunk.getZ() + 32),
                new BlockPos(centerOfChunk.getX() + 32, centerOfChunk.getY(), centerOfChunk.getZ() + 32),
                new BlockPos(centerOfChunk.getX() + 32, centerOfChunk.getY(), centerOfChunk.getZ()),
                new BlockPos(centerOfChunk.getX() + 32, centerOfChunk.getY(), centerOfChunk.getZ() - 32),
                new BlockPos(centerOfChunk.getX(), centerOfChunk.getY(), centerOfChunk.getZ() - 32),
                new BlockPos(centerOfChunk.getX() - 32, centerOfChunk.getY(), centerOfChunk.getZ() - 32),
                new BlockPos(centerOfChunk.getX() - 32, centerOfChunk.getY(), centerOfChunk.getZ()),
                new BlockPos(centerOfChunk.getX() - 32, centerOfChunk.getY(), centerOfChunk.getZ() + 32)
        ));
        // North, Northeast, East, SouthEast, South, SouthWest, West, NorthWest
        // X = Empty, T = Checked
        // T X T X T
        // X X X X X
        // T X X X T
        // X X X X X
        // T X T X T

        List<BlockPos> usablePositions =  new ArrayList<>();
        int bottomFloorRange = seaLevel - 66;
        int topFloorRange = seaLevel - 55;

        for (BlockPos pos : testables) {
            int testHeight = context.chunkGenerator().getFirstOccupiedHeight(pos.getX(), pos.getZ(), Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor());
            if (testHeight >= bottomFloorRange && testHeight <= topFloorRange) {
                usablePositions.add(pos);
            }
        }

        if (usablePositions.size() > 0) {
            return usablePositions.get(worldgenRandom.nextInt(usablePositions.size()));
        }
        return BlockPos.ZERO;
    }

    public static @NotNull Optional<PieceGenerator<JigsawConfiguration>> createPiecesGenerator(PieceGeneratorSupplier.Context<JigsawConfiguration> context) {
        // Check if the spot is valid for our structure. This is just as another method for cleanness.
        // Returning an empty optional tells the game to skip this spot as it will not generate the structure. -- TelepathicGrunt

        Predicate<Holder<Biome>> predicate = context.validBiome();
        Optional<PieceGenerator<JigsawConfiguration>> piecesGenerator;

        ChunkPos chunkPos = context.chunkPos();
        WorldgenRandom worldgenRandom = new WorldgenRandom(new LegacyRandomSource(0L));
        worldgenRandom.setLargeFeatureSeed(context.seed(), chunkPos.x, chunkPos.z);

        boolean firstTowerDistanceCheck = (int) Mth.absMax(chunkPos.x, chunkPos.z) >= firstTowerDistance;
        // BrassAmberBattleTowers.LOGGER.info("current distance " + (int) Mth.absMax(chunkPos.x, chunkPos.z) + "  config f distance " + BattleTowersConfig.firstTowerDistance.get());

        int nextSeperation =  minimumSeparation + worldgenRandom.nextInt(seperationRange);
        int spawnDistance = Math.min(Mth.abs(chunkPos.x-lastSpawnPosition.x), Mth.abs(chunkPos.z-lastSpawnPosition.z));

        BlockPos chunkCenter= context.chunkPos().getMiddleBlockPosition(0);
        int x = chunkCenter.getX();
        int z = chunkCenter.getZ();
        int y =  context.chunkGenerator().getFirstFreeHeight(chunkCenter.getX(), chunkCenter.getZ(), Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor());

        Holder<Biome> biome = context.chunkGenerator().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z));

        if (firstTowerDistanceCheck && spawnDistance > nextSeperation && predicate.test(biome)) {
            SpawnPos = isSpawnableChunk(context, worldgenRandom);
            SpawnPos = SpawnPos.atY(context.chunkGenerator().getSeaLevel() + 8);
        }
        else {
            SpawnPos = BlockPos.ZERO;
        }
        // BrassAmberBattleTowers.LOGGER.info("distance from last " + spawnDistance + "  config distance allowed " + nextSeperation);

        if (SpawnPos != BlockPos.ZERO) {
            // Moved Biome check in JigsawPlacement outside
            int i;
            int j;
            int k;
            i = SpawnPos.getX();
            j = SpawnPos.getZ();
            k = SpawnPos.getY() + context.chunkGenerator().getFirstFreeHeight(i, j, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor());

            if (!predicate.test(context.chunkGenerator().getNoiseBiome(QuartPos.fromBlock(i), QuartPos.fromBlock(k), QuartPos.fromBlock(j)))) {
                BrassAmberBattleTowers.LOGGER.info("Ocean tower incorrect biome " + biome);
                piecesGenerator = Optional.empty();
            } else {

                // All a structure has to do is call this method to turn it into a jigsaw based structure!
                piecesGenerator =
                        BTOceanJigsawPlacement.addPieces(
                                context, // Used for JigsawPlacement to get all the proper behaviors done.
                                PoolElementStructurePiece::new, // Needed in order to create a list of jigsaw pieces when making the structure's layout.
                                SpawnPos
                        );
                // Return the pieces generator that is now set up so that the game runs it when it needs to create the layout of structure pieces
            }

            if (piecesGenerator.isPresent()) {
                // I use to debug and quickly find out if the structure is spawning or not and where it is.
                // This is returning the coordinates of the center starting piece.
                lastSpawnPosition = context.chunkPos();
            }

            return piecesGenerator;
        }
        return Optional.empty();
    }

    public static void afterPlace(WorldGenLevel worldGenLevel, StructureFeatureManager featureManager, ChunkGenerator chunkGenerator, Random random, BoundingBox boundingBox, ChunkPos chunkPos, PiecesContainer piecesContainer) {
        BoundingBox boundingbox = piecesContainer.calculateBoundingBox();
        int bbYStart = boundingbox.minY();

        BlockPos chunckCenter = chunkPos.getMiddleBlockPosition(bbYStart);

        BrassAmberBattleTowers.LOGGER.info("Post Processing: In chunk: " + chunkPos + " " + chunckCenter);

        BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();
        blockpos$mutableblockpos.setY(bbYStart);
        // get start and end postions for x/z, using min/max to account for the MinBlock being -25 and the MaxBlock being -27
        int startX = chunckCenter.getX() - 8;
        int endX = chunckCenter.getX() + 8;
        // BrassAmberBattleTowers.LOGGER.info("X start: " + startX + " end: " + endX);

        int startZ = chunckCenter.getZ() - 8;
        int endZ = chunckCenter.getZ() + 8;
        // BrassAmberBattleTowers.LOGGER.info("X start: " + startZ + " end: " + endZ);

        ArrayList<BlockPos> startPositions = new ArrayList<>();

        for (int x = startX; x <= endX; x++) {
            for (int z = startZ; z <= endZ; z++) {
                blockpos$mutableblockpos.set(x, bbYStart, z);
                // BrassAmberBattleTowers.LOGGER.info("Block at: " + blockpos$mutableblockpos + " is: " + worldGenLevel.getBlockState(blockpos$mutableblockpos));
                if (worldGenLevel.getBlockState(blockpos$mutableblockpos) == Blocks.PRISMARINE_BRICKS.defaultBlockState()) {
                    BrassAmberBattleTowers.LOGGER.info("Block is acceptable: " + blockpos$mutableblockpos + " "+ worldGenLevel.getBlockState(blockpos$mutableblockpos));
                    startPositions.add(new BlockPos(x, bbYStart - 1, z));
                }
            }
        }

        for (BlockPos startPos: startPositions) {
            for (int y = startPos.getY(); y > worldGenLevel.getMinBuildHeight() ; y--) {
                blockpos$mutableblockpos.set(startPos.getX(), y, startPos.getZ());
                // BrassAmberBattleTowers.LOGGER.info("Block to check: " + blockpos$mutableblockpos + " is: " + worldGenLevel.getBlockState(blockpos$mutableblockpos));
                if (worldGenLevel.isEmptyBlock(blockpos$mutableblockpos) || worldGenLevel.isWaterAt(blockpos$mutableblockpos)
                        || worldGenLevel.getBlockState(blockpos$mutableblockpos).getBlock() instanceof SeagrassBlock
                        || worldGenLevel.getBlockState(blockpos$mutableblockpos).getBlock() instanceof TallSeagrassBlock) {
                    worldGenLevel.setBlock(blockpos$mutableblockpos, Blocks.PRISMARINE_BRICKS.defaultBlockState(), 2);
                } else {
                    // Add two blocks into this ground level as well.
                    worldGenLevel.setBlock(blockpos$mutableblockpos, Blocks.PRISMARINE_BRICKS.defaultBlockState(), 2);
                    worldGenLevel.setBlock(blockpos$mutableblockpos.below(), Blocks.PRISMARINE_BRICKS.defaultBlockState(), 2);
                    break;
                }
            }
        }

        Direction trenchDirection = random.nextInt(2) == 0 ? Direction.WEST : Direction.NORTH;
        int trenchLength = 32 + random.nextInt(24);
        BlockPos bbCenter = boundingbox.getCenter();

        if (trenchDirection == Direction.WEST) {
            startX = bbCenter.getX() - trenchLength;
            endX = bbCenter.getX() + trenchLength;
            startZ = bbCenter.getZ() - 24;
            endZ = bbCenter.getZ() + 24;
        }
        else {
            startX = bbCenter.getX() - 24;
            endX = bbCenter.getX() + 24;
            startZ = bbCenter.getZ()  - trenchLength;
            endZ = bbCenter.getZ() + trenchLength;
        }

        for (int x = startX; x <= endX; x++) {
            for (int z = startZ; z <= endZ; z++) {
                for (int y = bbYStart; y < bbYStart + 23; y++) {
                    blockpos$mutableblockpos.set(x, bbYStart, z);
                    boolean insideChunk = (Mth.absMax(x - chunckCenter.getX(), z - chunckCenter.getZ()) < 9);
                    if (insideChunk && horizontalDistanceTo(bbCenter, blockpos$mutableblockpos) > 12.5D && !worldGenLevel.isWaterAt(blockpos$mutableblockpos)) {
                        worldGenLevel.setBlock(blockpos$mutableblockpos, Blocks.WATER.defaultBlockState(), 2);
                    }
                }
            }
        }
    }
}
