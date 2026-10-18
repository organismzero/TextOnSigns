package com.leniad.textsigns;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.leniad.textsigns.interaction.SignInteraction;
import com.leniad.textsigns.interaction.TextSignBlock;
import com.leniad.textsigns.render.SignTextRenderer;
import com.leniad.textsigns.render.SignTextSystem;
import com.leniad.textsigns.signui.SignUISupplier;



import javax.annotation.Nonnull;

/**
 * @author Daniel Novaes Dias
 * @version 2.0.0
 */
public class TextSigns extends JavaPlugin {

    private static TextSigns instance;

    private ComponentType<ChunkStore, TextSignBlock> textSignComponentType;

    public TextSigns(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
        var logger = getLogger();
        logger.atInfo().log("[SignText] Loaded");
    }

    @Override
    protected void setup() {
        super.setup();

        this.textSignComponentType = this.getChunkStoreRegistry().registerComponent(TextSignBlock.class, "TextSign", TextSignBlock.CODEC);
        SignTextRenderer renderer = SignTextRenderer.init();
        getLogger().atInfo().log("[SignText] Text areas defined for %d sign types", renderer.faceCount());
        this.getChunkStoreRegistry().registerSystem(new SignTextSystem());
        this.getCodecRegistry(OpenCustomUIInteraction.PAGE_CODEC).register("TextSign_UI", SignUISupplier.class, SignUISupplier.CODEC);
        this.getCodecRegistry(Interaction.CODEC).register("SignInteraction", SignInteraction.class, SignInteraction.CODEC);
    }

    /**
     * Get plugin instance.
     */
    public static TextSigns getInstance() {
        return instance;
    }

    public ComponentType<ChunkStore, TextSignBlock> getTextSignComponentType() {
        return textSignComponentType;
    }

}
