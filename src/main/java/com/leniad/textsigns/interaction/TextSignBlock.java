package com.leniad.textsigns.interaction;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.block.BlockEntity;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.leniad.textsigns.TextSigns;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Block entity component holding a sign's text and lock state.
 * Declared on the sign blocks through "BlockEntity" in their item definitions.
 */
public class TextSignBlock implements Component<ChunkStore> {
    public static final BuilderCodec<TextSignBlock> CODEC = BuilderCodec.builder(TextSignBlock.class, TextSignBlock::new)
            .append(new KeyedCodec<>("Text", Codec.STRING), (sign, text) -> sign.text = text, sign -> sign.text)
            .add()
            .<Boolean>append(new KeyedCodec<>("IsLocked", Codec.BOOLEAN), (sign, locked) -> sign.isLocked = locked, sign -> sign.isLocked)
            .add()
            .build();

    private String text = "";
    private boolean isLocked;

    public static ComponentType<ChunkStore, TextSignBlock> getComponentType() {
        return TextSigns.getInstance().getTextSignComponentType();
    }

    public TextSignBlock() {
    }

    public TextSignBlock(String text, boolean isLocked) {
        this.text = text;
        this.isLocked = isLocked;
    }

    public String getSignText() {
        return text != null ? text : "";
    }

    public void setSignText(String text) {
        this.text = text;
    }

    public boolean getIsLocked() {
        return isLocked;
    }

    public void setIsLocked(boolean locked) {
        this.isLocked = locked;
    }

    /**
     * Returns the block entity for the sign at the given position, creating it if the block type declares a
     * TextSign component but has no block entity yet (for example, signs placed by world generation).
     * Must be called on the world thread.
     */
    @Nullable
    public static Ref<ChunkStore> getOrCreateSignRef(@Nonnull World world, int x, int y, int z) {
        ChunkStore chunkStore = world.getChunkStore();
        Ref<ChunkStore> sectionRef = chunkStore.getChunkSectionReferenceAtBlock(x, y, z);
        if (sectionRef == null || !sectionRef.isValid()) {
            return null;
        }

        Store<ChunkStore> store = chunkStore.getStore();
        Ref<ChunkStore> blockRef = BlockModule.getBlockEntity(store, sectionRef, x, y, z);
        if (blockRef == null) {
            BlockSection blockSection = store.getComponent(sectionRef, BlockSection.getComponentType());
            BlockComponentSection componentSection = store.getComponent(sectionRef, BlockComponentSection.getComponentType());
            if (blockSection == null || componentSection == null) {
                return null;
            }

            BlockType blockType = BlockType.getAssetMap().getAsset(blockSection.get(x, y, z));
            if (blockType == null || !BlockEntity.declaresComponent(blockType, getComponentType())) {
                return null;
            }

            int filler = blockSection.getFiller(ChunkUtil.indexBlock(x, y, z));
            blockRef = BlockEntity.ensureBlockEntity(store, sectionRef, componentSection, x, y, z, blockType, filler);
        }

        if (blockRef == null || store.getComponent(blockRef, getComponentType()) == null) {
            return null;
        }

        return blockRef;
    }

    /**
     * Marks the chunk section holding this sign's block entity as needing to be saved.
     */
    public static void markNeedsSaving(@Nonnull Ref<ChunkStore> blockRef) {
        BlockModule.BlockStateInfo info = blockRef.getStore().getComponent(blockRef, BlockModule.BlockStateInfo.getComponentType());
        if (info != null) {
            info.markNeedsSaving();
        }
    }

    @Nonnull
    @Override
    public String toString() {
        return "TextSignBlock{text=" + this.text + ", isLocked=" + this.isLocked + "}";
    }

    @Nonnull
    @Override
    public Component<ChunkStore> clone() {
        return new TextSignBlock(this.text, this.isLocked);
    }
}
