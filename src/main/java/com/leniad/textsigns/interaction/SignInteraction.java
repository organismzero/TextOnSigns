package com.leniad.textsigns.interaction;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.leniad.textsigns.signui.SignUISupplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3i;


public class SignInteraction extends SimpleBlockInteraction {
    public static final BuilderCodec<SignInteraction> CODEC;

    @Override
    protected void interactWithBlock(@NotNull World world, @NotNull CommandBuffer<EntityStore> commandBuffer, @NotNull InteractionType interactionType, @NotNull InteractionContext interactionContext, @Nullable ItemStack itemStack, @NotNull Vector3i pos, @NotNull CooldownHandler cooldownHandler) {
        // Creates the block entity for signs that predate the plugin or came from world generation
        Ref<ChunkStore> signRef = TextSignBlock.getOrCreateSignRef(world, pos.x, pos.y, pos.z);
        if (signRef == null) {
            return;
        }

        Ref<EntityStore> ref = interactionContext.getEntity();
        Store<EntityStore> store = ref.getStore();
        Player playerComponent = commandBuffer.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType());

        if (playerRef != null && playerComponent != null) {
            SignUISupplier signPageSupplier = new SignUISupplier();
            Vector3i pos_sign = new Vector3i(pos.x, pos.y, pos.z);
            CustomUIPage page = signPageSupplier.tryCreateWithPos(ref, commandBuffer, playerRef, interactionContext, pos_sign);
            playerComponent.getPageManager().openCustomPage(ref, store, page);
        }
    }

    @Override
    protected void simulateInteractWithBlock(@NotNull InteractionType interactionType, @NotNull InteractionContext interactionContext, @Nullable ItemStack itemStack, @NotNull World world, @NotNull Vector3i vector3i) {

    }

    static {
        CODEC = ((BuilderCodec.Builder)BuilderCodec.builder(SignInteraction.class, SignInteraction::new, SimpleBlockInteraction.CODEC).documentation("Opens the Sign UI.")).build();
    }

}
