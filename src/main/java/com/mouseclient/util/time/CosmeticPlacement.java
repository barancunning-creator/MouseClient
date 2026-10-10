package com.mouseclient.util.time;

import java.util.ArrayList;

public record CosmeticPlacement(
   String kind,
   String anchor,
   float x,
   float y,
   float z,
   float rotationX,
   float rotationY,
   float rotationZ,
   float scale,
   float alignX,
   float alignY,
   float alignScale,
   boolean flying,
   ArrayList<String> targets
) {
   public boolean onBody() {
      return this.anchor.equals("body") || this.anchor.equals("shoulder");
   }

   public boolean isAnimatedHead() {
      return this.kind.equals("animated_head");
   }

   public boolean isPet() {
      return this.kind.equals("pet");
   }
}
