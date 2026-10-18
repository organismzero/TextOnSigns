package com.leniad.textsigns.textvisualizer;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import com.hypixel.hytale.server.core.ui.PatchStyle;

import javax.annotation.Nonnull;

public class TextVisualizer extends CustomUIHud {
    private String text = "";
    private boolean shouldDisplay = false;
    private final PatchStyle bg = new PatchStyle().setBorder(Value.of(20)).setTexturePath(Value.of("Common/TooltipDefaultBackground.png"));

    public TextVisualizer(@Nonnull PlayerRef playerRef, String text, boolean shouldDisplay) {
        super(playerRef, "TextSigns_HUD");
        this.text = text;
        this.shouldDisplay = shouldDisplay;
    }

    public void updateUI() {
        UICommandBuilder newUi = new UICommandBuilder();
        this.setCommands(newUi);
        this.update(false, newUi);
    }

    public void setDisplay(String text, boolean active) {
        this.text = text;
        this.shouldDisplay = active;
        this.updateUI();
    }

    public boolean getShouldDisplay() {
        return shouldDisplay;
    }

    protected void build(@Nonnull UICommandBuilder ui) {
        ui.append("Pages/TextSigns/TextVisualizer.ui");
        this.setCommands(ui);
    }

    private void setCommands(@Nonnull UICommandBuilder ui) {
        if (!this.shouldDisplay) {
            ui.setObject("#TXTSContent.Background", new PatchStyle().setColor(Value.of("#ffffff00")));
        } else {
            ui.setObject("#TXTSContent.Background", this.bg);
        }

        ui.set("#TXTSSignContent.Text", text);
    }
}
