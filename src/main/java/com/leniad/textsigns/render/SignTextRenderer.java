package com.leniad.textsigns.render;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.NonSerialized;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.Phobia;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.leniad.textsigns.interaction.TextSignBlock;
import org.joml.Vector3d;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Draws sign text on the sign face as one model entity per glyph. Each glyph entity uses the shared glyph quad model
 * with that character's texture. Glyph entities are never saved: they are spawned when a sign is placed or loaded and
 * removed when it is broken or unloaded.
 * <p>
 * All entity changes run on the world thread.
 */
public final class SignTextRenderer {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static final String GLYPH_MODEL_ASSET_ID = "TextSigns_Glyph";
    public static final String GLYPH_MODEL = "Items/TextSigns/Glyph.blockymodel";
    public static final String FONT = "Lexend";

    // Model units per block for entity models. Vanilla creature models are about 64 units per block of hitbox height,
    // while block models use 32. Confirm in the client: if glyphs are drawn at double or half size, change this.
    static final double ENTITY_MODEL_UNITS_PER_BLOCK = 64.0;
    // Size of the glyph quad in model units, matching the glyph cell size in Glyph.blockymodel
    static final double GLYPH_QUAD_UNITS = 32.0;
    // How far glyphs float in front of the face, in model pixels, so they don't z-fight with it
    static final double FACE_OFFSET = 0.25;
    // Extra depth added per glyph, in model pixels, so overlapping transparent glyph cells never share a plane
    static final double GLYPH_DEPTH_STEP = 0.01;
    // The server culls entities whose hitbox is smaller than 3.5e-5 * distance squared. Glyph hitboxes are a thin column
    // at least this many blocks tall, so text stays visible to about 50 blocks.
    static final double MIN_VISIBILITY_SIZE = 0.09;
    // Half the width of the glyph hitbox column. Hitboxes must have some width, but this keeps glyphs hard to target.
    static final double HITBOX_HALF_WIDTH = 0.001;
    // Most glyphs drawn on one face. Text beyond this is not drawn.
    static final int MAX_GLYPHS_PER_FACE = 150;
    // Delay before redrawing after an edit, so typing in the sign editor doesn't respawn glyphs on every keystroke
    static final long REFRESH_DELAY_MS = 150;

    private static SignTextRenderer instance;

    private final GlyphFont font;
    private final SignFaces faces;
    private final Map<Ref<ChunkStore>, List<Ref<EntityStore>>> glyphsBySign = new ConcurrentHashMap<>();
    private final Map<Ref<ChunkStore>, Long> pendingRefreshes = new ConcurrentHashMap<>();
    private final AtomicLong refreshCounter = new AtomicLong();

    private SignTextRenderer(@Nonnull GlyphFont font, @Nonnull SignFaces faces) {
        this.font = font;
        this.faces = faces;
    }

    @Nonnull
    public static SignTextRenderer init() {
        instance = new SignTextRenderer(GlyphFont.load(FONT), SignFaces.load());
        return instance;
    }

    @Nonnull
    public static SignTextRenderer get() {
        return instance;
    }

    public int faceCount() {
        return faces.size();
    }

    /**
     * Redraws a sign's text after a short delay. Further requests for the same sign within the delay replace this one.
     * Safe to call from any thread.
     */
    public void requestRefresh(@Nonnull World world, @Nonnull Ref<ChunkStore> signRef) {
        long token = refreshCounter.incrementAndGet();
        pendingRefreshes.put(signRef, token);
        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            if (!pendingRefreshes.remove(signRef, token) || !world.isAlive()) {
                return;
            }
            world.execute(() -> render(world, signRef));
        }, REFRESH_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Removes a sign's glyphs and draws its current text. Must be called on the world thread.
     */
    public void render(@Nonnull World world, @Nonnull Ref<ChunkStore> signRef) {
        clear(signRef);
        if (!signRef.isValid()) {
            return;
        }

        Store<ChunkStore> chunkStore = signRef.getStore();
        TextSignBlock sign = chunkStore.getComponent(signRef, TextSignBlock.getComponentType());
        BlockModule.BlockStateInfo info = chunkStore.getComponent(signRef, BlockModule.BlockStateInfo.getComponentType());
        if (sign == null || info == null || sign.getSignText().isBlank()) {
            return;
        }

        Vector3i blockPos = new Vector3i();
        if (!info.fillWorldPos(chunkStore, blockPos)) {
            return;
        }
        BlockSection blockSection = chunkStore.getComponent(info.getSectionRef(), BlockSection.getComponentType());
        if (blockSection == null) {
            return;
        }
        BlockType blockType = BlockType.getAssetMap().getAsset(blockSection.get(info.getIndex()));
        if (blockType == null) {
            return;
        }
        SignFace face = faces.get(blockType.getId());
        if (face == null) {
            LOGGER.atWarning().log("No text area defined for sign block type %s", blockType.getId());
            return;
        }
        RotationTuple rotation = blockSection.getRotation(info.getIndex());

        TextLayout.Result layout = TextLayout.layout(sign.getSignText(), font, face.width(), face.height(), face.minLines(), face.maxLines());
        if (layout.glyphs().isEmpty()) {
            return;
        }

        Store<EntityStore> entityStore = world.getEntityStore().getStore();
        List<Ref<EntityStore>> spawned = new ArrayList<>();
        spawnFace(entityStore, spawned, layout, face, blockPos, rotation, false);
        if (face.backZ() != null) {
            spawnFace(entityStore, spawned, layout, face, blockPos, rotation, true);
        }
        glyphsBySign.put(signRef, spawned);
    }

    /**
     * Removes a sign's glyphs. Must be called on the world thread.
     */
    public void clear(@Nonnull Ref<ChunkStore> signRef) {
        List<Ref<EntityStore>> glyphs = glyphsBySign.remove(signRef);
        if (glyphs == null) {
            return;
        }
        for (Ref<EntityStore> glyph : glyphs) {
            if (glyph.isValid()) {
                glyph.getStore().removeEntity(glyph, RemoveReason.REMOVE);
            }
        }
    }

    /**
     * Forgets a sign that is going away, cancelling any pending redraw, and removes its glyphs on the world thread.
     */
    public void forget(@Nonnull World world, @Nonnull Ref<ChunkStore> signRef) {
        pendingRefreshes.remove(signRef);
        if (glyphsBySign.containsKey(signRef)) {
            world.execute(() -> clear(signRef));
        }
    }

    private void spawnFace(
            @Nonnull Store<EntityStore> store,
            @Nonnull List<Ref<EntityStore>> spawned,
            @Nonnull TextLayout.Result layout,
            @Nonnull SignFace face,
            @Nonnull Vector3i blockPos,
            @Nonnull RotationTuple rotation,
            boolean back
    ) {
        double roll = Math.toRadians(face.roll());
        double cos = Math.cos(roll);
        double sin = Math.sin(roll);
        // Seen from behind, the reader's right is the model's -X and the board's tilt is mirrored
        double mirror = back ? -1 : 1;
        double faceZ = back ? face.backZ() : face.frontZ();

        double cellBlocks = font.getCellWidth() * layout.scale() / SignFace.MODEL_PIXELS_PER_BLOCK;
        float entityScale = (float) (cellBlocks * ENTITY_MODEL_UNITS_PER_BLOCK / GLYPH_QUAD_UNITS);
        double visibility = Math.max(cellBlocks, MIN_VISIBILITY_SIZE);
        Box hitbox = new Box(-HITBOX_HALF_WIDTH, 0, -HITBOX_HALF_WIDTH, HITBOX_HALF_WIDTH, visibility, HITBOX_HALF_WIDTH);

        float yaw = (float) (rotation.yaw().getRadians() + (back ? Math.PI : 0));
        Rotation3f glyphRotation = new Rotation3f(0, yaw, (float) (roll * mirror));

        List<TextLayout.PlacedGlyph> glyphs = layout.glyphs();
        int count = Math.min(glyphs.size(), MAX_GLYPHS_PER_FACE);
        for (int i = 0; i < count; i++) {
            TextLayout.PlacedGlyph glyph = glyphs.get(i);
            double u = glyph.x() * mirror;
            double v = glyph.y();
            double depth = FACE_OFFSET + (i % 2) * GLYPH_DEPTH_STEP;
            Vector3d local = new Vector3d(
                    face.centreX() + u * cos - v * sin,
                    face.centreY() + u * sin + v * cos,
                    faceZ + (back ? -depth : depth)
            ).div(SignFace.MODEL_PIXELS_PER_BLOCK);
            Vector3d position = rotation.rotatedVector(local).add(blockPos.x + 0.5, blockPos.y, blockPos.z + 0.5);

            Model model = glyphModel(font.texture(face.ink(), glyph.character()), entityScale, hitbox);
            spawned.add(spawnGlyph(store, model, position, glyphRotation));
        }
    }

    @Nonnull
    private static Ref<EntityStore> spawnGlyph(@Nonnull Store<EntityStore> store, @Nonnull Model model, @Nonnull Vector3d position, @Nonnull Rotation3f rotation) {
        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
        holder.addComponent(NetworkId.getComponentType(), new NetworkId(store.getExternalData().takeNextNetworkId()));
        holder.addComponent(EntityStore.REGISTRY.getNonSerializedComponentType(), NonSerialized.get());
        holder.addComponent(Intangible.getComponentType(), Intangible.INSTANCE);
        holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(position, rotation));
        holder.addComponent(HeadRotation.getComponentType(), new HeadRotation(rotation));
        holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
        return store.addEntity(holder, AddReason.SPAWN);
    }

    @Nonnull
    private static Model glyphModel(@Nonnull String texture, float scale, @Nullable Box hitbox) {
        return new Model(
                GLYPH_MODEL_ASSET_ID, scale, null, null, hitbox, GLYPH_MODEL, texture, null, null,
                0, 0, 0, 0, null, null, null, null, null, null, null, Phobia.None, null
        );
    }
}
