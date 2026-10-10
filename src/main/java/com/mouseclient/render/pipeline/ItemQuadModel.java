package com.mouseclient.render.pipeline;

import java.util.List;

public record ItemQuadModel(List<ItemQuad> quads, boolean animated, boolean specialRenderer) {
}
