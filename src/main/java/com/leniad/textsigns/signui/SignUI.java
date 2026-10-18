package com.leniad.textsigns.signui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
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
import com.leniad.textsigns.render.SignColours;
import com.leniad.textsigns.render.SignTextRenderer;
import com.leniad.textsigns.render.TextLayout;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

public class SignUI extends InteractiveCustomUIPage<SignUI.PageData> {
    private static final int MAX_TEXT_LENGTH = 200;

    private static final String ACTION_SAVE = "Save";
    private static final String ACTION_COLOUR = "Colour";
    // Colour event value for the sign type's default ink
    private static final String COLOUR_DEFAULT = "Default";

    private Vector3i interactedBlock;
    // Text typed since the editor opened, shown on the sign as a preview until it is saved with Enter
    @Nullable
    private String pendingText;
    // Sign colour chosen since the editor opened, previewed and saved the same way as the text
    @Nullable
    private SignColours pendingColour;
    private boolean colourChanged;
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
        uiCommandBuilder.set("#TXTSColourName.Text", colourName(currentState.getColour()));
        uiCommandBuilder.set("#TXTSLineBreakCode.Text", Message.translation("customUI.textSign.lineBreakCode").param("code", TextLayout.LINE_BREAK_CODE));

        if (currentState.getIsLocked()) {
            uiCommandBuilder.set("#TXTSSignInput.IsReadOnly", true);
        }

        uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#TXTSSignInput", EventData.of("@SignText", "#TXTSSignInput.Value"), false);
        // Enter in the text field or the Save button saves and closes the editor
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Validating, "#TXTSSignInput", EventData.of(PageData.KEY_ACTION, ACTION_SAVE).append("@SignText", "#TXTSSignInput.Value"), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TXTSSaveButton", EventData.of(PageData.KEY_ACTION, ACTION_SAVE).append("@SignText", "#TXTSSignInput.Value"), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#TXTSCheckBox", EventData.of("@SignLocked", "#TXTSCheckBox.Value"), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TXTSColourDefault", EventData.of(PageData.KEY_ACTION, ACTION_COLOUR).append(PageData.KEY_COLOUR, COLOUR_DEFAULT), false);
        for (SignColours colour : SignColours.values()) {
            uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TXTSColour" + colour.ink(), EventData.of(PageData.KEY_ACTION, ACTION_COLOUR).append(PageData.KEY_COLOUR, colour.ink()), false);
        }

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

        if (ACTION_COLOUR.equals(data.action)) {
            if (!signState.getIsLocked()) {
                this.pendingColour = SignColours.fromInk(data.colour);
                this.colourChanged = true;
                UICommandBuilder update = new UICommandBuilder();
                update.set("#TXTSColourName.Text", colourName(this.pendingColour));
                this.sendUpdate(update, false);
                showPreview(signRef, signState);
            }
            return;
        }

        if (data.signText != null) {
            if (this.pendingText != null) {
                showPreview(signRef, signState);
            }
            return;
        }

        if (data.isLocked != null && data.isLocked != signState.getIsLocked() && canChangeLock(ref, store, signRef)) {
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
     * Closing the editor any other way than with Enter or Save, such as with Esc, discards the text and colour chosen
     * since it opened.
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
        if (!signState.getIsLocked()) {
            if (this.pendingText != null) {
                signState.setSignText(this.pendingText);
            }
            if (this.colourChanged) {
                signState.setColour(this.pendingColour);
            }
            if (this.pendingText != null || this.colourChanged) {
                TextSignBlock.markNeedsSaving(signRef);
            }
        }
        this.pendingText = null;
        this.pendingColour = null;
        this.colourChanged = false;
        SignTextRenderer.get().clearPreview(world, signRef);
    }

    /**
     * Shows the unsaved text and colour on the sign.
     */
    private void showPreview(@Nonnull Ref<ChunkStore> signRef, @Nonnull TextSignBlock signState) {
        String text = this.pendingText != null ? this.pendingText : signState.getSignText();
        SignColours colour = this.colourChanged ? this.pendingColour : signState.getColour();
        SignTextRenderer.get().setPreview(signRef.getStore().getExternalData().getWorld(), signRef, text, colour);
    }

    @Nonnull
    private static Message colourName(@Nullable SignColours colour) {
        if (colour == null) {
            return Message.translation("customUI.textSign.colourSelected").param("colour", Message.translation("customUI.textSign.colourDefault"));
        }
        return Message.translation("customUI.textSign.colourSelectedCode")
                .param("colour", Message.translation("customUI.textSign.colours." + colour.ink()))
                .param("code", String.valueOf(colour.code()));
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
        static final String KEY_COLOUR = "Colour";

        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
                .addField(new KeyedCodec<>(KEY_SIGN_TEXT, Codec.STRING), (data, s) -> data.signText = s, data -> data.signText)
                .addField(new KeyedCodec<>(KEY_LOCKED_TEXT, Codec.BOOLEAN), (data, b) -> data.isLocked = b, data -> data.isLocked)
                .addField(new KeyedCodec<>(KEY_ACTION, Codec.STRING), (data, a) -> data.action = a, data -> data.action)
                .addField(new KeyedCodec<>(KEY_COLOUR, Codec.STRING), (data, c) -> data.colour = c, data -> data.colour)
                .build();


        private String signText;
        // Null when the event isn't from the lock checkbox
        private Boolean isLocked;
        private String action;
        private String colour;
    }

}
