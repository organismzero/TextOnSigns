package com.leniad.textsigns.render;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Text areas for each supported sign block type, read from textsigns/sign_faces.json.
 */
public final class SignFaces {
    private static final String RESOURCE = "textsigns/sign_faces.json";
    private static final int DEFAULT_MIN_LINES = 2;
    private static final int DEFAULT_MAX_LINES = 5;

    private final Map<String, SignFace> faces;

    private SignFaces(@Nonnull Map<String, SignFace> faces) {
        this.faces = faces;
    }

    @Nonnull
    public static SignFaces parse(@Nonnull Reader reader) {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        Map<String, SignFace> faces = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            if (entry.getKey().startsWith("_")) {
                continue;
            }
            JsonObject face = entry.getValue().getAsJsonObject();
            faces.put(entry.getKey(), new SignFace(
                    face.getAsJsonArray("centre").get(0).getAsDouble(),
                    face.getAsJsonArray("centre").get(1).getAsDouble(),
                    face.getAsJsonArray("size").get(0).getAsDouble(),
                    face.getAsJsonArray("size").get(1).getAsDouble(),
                    face.get("front").getAsDouble(),
                    face.has("back") ? face.get("back").getAsDouble() : null,
                    face.has("roll") ? face.get("roll").getAsDouble() : 0,
                    face.get("ink").getAsString(),
                    face.has("minLines") ? face.get("minLines").getAsInt() : DEFAULT_MIN_LINES,
                    face.has("maxLines") ? face.get("maxLines").getAsInt() : DEFAULT_MAX_LINES
            ));
        }
        return new SignFaces(Map.copyOf(faces));
    }

    @Nonnull
    public static SignFaces load() {
        try (InputStream in = SignFaces.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + RESOURCE);
            }
            return parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + RESOURCE, e);
        }
    }

    @Nullable
    public SignFace get(@Nonnull String blockTypeId) {
        return faces.get(blockTypeId);
    }

    public int size() {
        return faces.size();
    }
}
