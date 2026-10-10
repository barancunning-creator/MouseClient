package com.mouseclient.render.pipeline;

import com.mouseclient.network.Event;
import lombok.Generated;
import net.minecraft.client.util.math.MatrixStack;

public final class WorldRenderEvent extends Event {
   private final MatrixStack matrixStack;
   private final float tickDelta;

   @Generated
   public float getTickDelta() {
      return this.tickDelta;
   }

   @Generated
   public WorldRenderEvent(MatrixStack matrixStack, float tickDelta) {
      this.matrixStack = matrixStack;
      this.tickDelta = tickDelta;
   }

   @Generated
   public MatrixStack getMatrices() {
      return this.matrixStack;
   }
}
