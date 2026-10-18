package com.leniad.textsigns.render;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.leniad.textsigns.interaction.TextSignBlock;

import javax.annotation.Nonnull;

/**
 * Draws sign text when a sign's block entity is placed or loaded, and removes it when the sign is broken or unloaded.
 */
public class SignTextSystem extends RefSystem<ChunkStore> {
    private final Query<ChunkStore> query = Query.and(TextSignBlock.getComponentType(), BlockModule.BlockStateInfo.getComponentType());

    @Nonnull
    @Override
    public Query<ChunkStore> getQuery() {
        return query;
    }

    @Override
    public void onEntityAdded(@Nonnull Ref<ChunkStore> ref, @Nonnull AddReason reason, @Nonnull Store<ChunkStore> store, @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
        World world = store.getExternalData().getWorld();
        // Entity changes from a chunk store system are deferred to the world's task queue, as vanilla block systems do
        world.execute(() -> SignTextRenderer.get().render(world, ref));
    }

    @Override
    public void onEntityRemove(@Nonnull Ref<ChunkStore> ref, @Nonnull RemoveReason reason, @Nonnull Store<ChunkStore> store, @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
        SignTextRenderer.get().forget(store.getExternalData().getWorld(), ref);
    }
}
