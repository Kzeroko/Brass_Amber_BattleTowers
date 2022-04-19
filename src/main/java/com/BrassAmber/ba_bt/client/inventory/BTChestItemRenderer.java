package com.BrassAmber.ba_bt.client.inventory;

import com.BrassAmber.ba_bt.block.block.GolemChestBlock;
import com.BrassAmber.ba_bt.block.tileentity.GolemChestBlockEntity;
import com.BrassAmber.ba_bt.block.tileentity.TowerChestBlockEntity;
import com.BrassAmber.ba_bt.init.BTBlocks;
import com.BrassAmber.ba_bt.init.BTItems;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public class BTChestItemRenderer extends BlockEntityWithoutLevelRenderer {

    public static BTChestItemRenderer INSTANCE = new BTChestItemRenderer(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());

    private final GolemChestBlockEntity golemChestEntity = new GolemChestBlockEntity(BlockPos.ZERO, BTBlocks.LAND_GOLEM_CHEST.get().defaultBlockState());
    private final TowerChestBlockEntity towerChestEntity = new TowerChestBlockEntity(BlockPos.ZERO, BTBlocks.LAND_CHEST.get().defaultBlockState());
    private final BlockEntityRenderDispatcher dispatcher;

    public BTChestItemRenderer(BlockEntityRenderDispatcher dispatcherIn, EntityModelSet modelSet) {
        super(dispatcherIn, modelSet);
        this.dispatcher = dispatcherIn;
    }

    @Override
    public void renderByItem(ItemStack itemStack, ItemTransforms.TransformType transformType, PoseStack poseStack, MultiBufferSource multiBufferSource, int combinedLightIn, int combineOverLayIn) {
        Item item = itemStack.getItem();
        BlockEntity blockEntity = null;
        if (item instanceof BlockItem) {
            if (item == BTItems.LAND_CHEST.get()) {
                blockEntity = this.towerChestEntity;

            } else if (item == BTItems.LAND_GOLEM_CHEST.get()){
                blockEntity = this.golemChestEntity;
            }
            if (blockEntity == null) {
                blockEntity = this.towerChestEntity;
            }
            this.dispatcher.renderItem(blockEntity, poseStack, multiBufferSource, combinedLightIn, combineOverLayIn);
        }
    }
}