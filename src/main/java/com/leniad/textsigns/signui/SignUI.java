package com.leniad.textsigns.signui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.interaction.components.PlacedByInteractionComponent;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.leniad.textsigns.interaction.TextSignBlock;
import com.leniad.textsigns.render.SignTextRenderer;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

public class SignUI extends InteractiveCustomUIPage<SignUI.PageData> {
    private static final int MAX_TEXT_LENGTH = 200;

    private static final String ACTION_SAVE = "Save";

    private Vector3i interactedBlock;
    // Text typed since the editor opened, shown on the sign as a preview until it is saved with Enter
    @Nullable
    private String pendingText;
    private boolean saved;

    public SignUI(@Nonnull PlayerRef playerRef, @Nonnull CustomPageLifetime lifetime, Vector3i pos) {
        super(playerRef, lifetime, PageData.CODEC);

        this.interactedBlock = pos;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder, @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/TextSigns/TextSign.ui");
        Ref<ChunkStore> signRef = getSignRef(store);
        TextSignBlock currentState = getSignMetaData(signRef);

        if (currentState == null) {
            return;
        }

        if (!canChangeLock(ref, store, signRef)) {
            uiCommandBuilder.set("#TXTSCheckBox.Disabled", true);
        }

        uiCommandBuilder.set("#TXTSSignInput.Value", currentState.getSignText());
        uiCommandBuilder.set("#TXTSCheckBox.Value", currentState.getIsLocked());

        if (currentState.getIsLocked()) {
            uiCommandBuilder.set("#TXTSSignInput.IsReadOnly", true);
        }

        uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#TXTSSignInput", EventData.of("@SignText", "#TXTSSignInput.Value"), false);
        // Enter in the text field saves and closes the editor
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Validating, "#TXTSSignInput", EventData.of(PageData.KEY_ACTION, ACTION_SAVE).append("@SignText", "#TXTSSignInput.Value"), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#TXTSCheckBox", EventData.of("@SignLocked", "#TXTSCheckBox.Value"), false);

    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull SignUI.PageData data) {
        super.handleDataEvent(ref, store, data);
        setSignMetaData(ref, store, data);
    }



    public void setSignMetaData(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, SignUI.PageData data) {
        Ref<ChunkStore> signRef = getSignRef(store);
        TextSignBlock signState = getSignMetaData(signRef);

        if (signState == null) {
            return;
        }

        if (data.signText != null && !signState.getIsLocked()) {
            this.pendingText = data.signText.length() > MAX_TEXT_LENGTH ? data.signText.substring(0, MAX_TEXT_LENGTH) : data.signText;
        }

        if (ACTION_SAVE.equals(data.action)) {
            savePendingText(signRef, signState);
            this.saved = true;
            this.close();
            return;
        }

        if (data.signText != null) {
            if (this.pendingText != null) {
                SignTextRenderer.get().setPreview(store.getExternalData().getWorld(), signRef, this.pendingText);
            }
            return;
        }

        if (data.isLocked != signState.getIsLocked() && canChangeLock(ref, store, signRef)) {
            // Locking keeps what has been typed so far, since the text can't be changed once locked
            if (data.isLocked) {
                savePendingText(signRef, signState);
            }
            signState.setIsLocked(data.isLocked);
            TextSignBlock.markNeedsSaving(signRef);
            UICommandBuilder update = new UICommandBuilder();
            update.set("#TXTSSignInput.IsReadOnly", data.isLocked);
            this.sendUpdate(update, false);
        }
    }

    /**
     * Closing the editor any other way than with Enter, such as with Esc, discards the text typed since it opened.
     */
    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        super.onDismiss(ref, store);
        if (this.saved) {
            return;
        }
        Ref<ChunkStore> signRef = getSignRef(store);
        if (signRef != null) {
            SignTextRenderer.get().clearPreview(store.getExternalData().getWorld(), signRef);
        }
    }

    private void savePendingText(@Nonnull Ref<ChunkStore> signRef, @Nonnull TextSignBlock signState) {
        World world = signRef.getStore().getExternalData().getWorld();
        if (this.pendingText != null && !signState.getIsLocked()) {
            signState.setSignText(this.pendingText);
            TextSignBlock.markNeedsSaving(signRef);
        }
        this.pendingText = null;
        SignTextRenderer.get().clearPreview(world, signRef);
    }

    @Nullable
    private Ref<ChunkStore> getSignRef(@Nonnull Store<EntityStore> store) {
        return BlockModule.getBlockEntity(store.getExternalData().getWorld(), this.interactedBlock.x, this.interactedBlock.y, this.interactedBlock.z);
    }

    @Nullable
    public TextSignBlock getSignMetaData(@Nullable Ref<ChunkStore> signRef) {
        if (signRef == null) {
            return null;
        }

        return signRef.getStore().getComponent(signRef, TextSignBlock.getComponentType());
    }

    /**
     * Only the player who placed the sign may lock or unlock it. Signs with no recorded owner, such as
     * generated ones, can be locked by anyone.
     */
    private boolean canChangeLock(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Ref<ChunkStore> signRef) {
        PlacedByInteractionComponent placedByComp = signRef.getStore().getComponent(signRef, PlacedByInteractionComponent.getComponentType());
        if (placedByComp == null) {
            return true;
        }

        UUID playerOwnerID = placedByComp.getWhoPlacedUuid();
        if (playerOwnerID == null) {
            return true;
        }

        PlayerRef currentPlayer = store.getComponent(ref, PlayerRef.getComponentType());
        return currentPlayer != null && playerOwnerID.equals(currentPlayer.getUuid());
    }

    public static class PageData {
        static final String KEY_SIGN_TEXT = "@SignText";
        static final String KEY_LOCKED_TEXT = "@SignLocked";
        static final String KEY_ACTION = "Action";

        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
                .addField(new KeyedCodec<>(KEY_SIGN_TEXT, Codec.STRING), (data, s) -> data.signText = s, data -> data.signText)
                .addField(new KeyedCodec<>(KEY_LOCKED_TEXT, Codec.BOOLEAN), (data, b) -> data.isLocked = b, data -> data.isLocked)
                .addField(new KeyedCodec<>(KEY_ACTION, Codec.STRING), (data, a) -> data.action = a, data -> data.action)
                .build();


        private String signText;
        private boolean isLocked;
        private String action;
    }

}
