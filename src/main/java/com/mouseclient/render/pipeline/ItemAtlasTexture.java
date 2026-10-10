package com.mouseclient.render.pipeline;

import net.minecraft.client.texture.TextureSetup;
import net.minecraft.util.Identifier;

public record ItemAtlasTexture(Identifier id, TextureSetup setup, TextureSetup linearSetup) {
}
