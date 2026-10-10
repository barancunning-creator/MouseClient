package com.mouseclient.render.pipeline;

import net.minecraft.util.Identifier;

public record ItemQuad(
   Identifier atlas,
   float x1,
   float y1,
   float u1,
   float v1,
   float x2,
   float y2,
   float u2,
   float v2,
   float x3,
   float y3,
   float u3,
   float v3,
   float x4,
   float y4,
   float u4,
   float v4,
   int tint,
   boolean foil
) {
   public float y2() {
      return this.y2;
   }

   public float v4() {
      return this.v4;
   }

   public float x1() {
      return this.x1;
   }

   public float x2() {
      return this.x2;
   }

   public float u1() {
      return this.u1;
   }

   public float x3() {
      return this.x3;
   }

   public float y4() {
      return this.y4;
   }

   public float v1() {
      return this.v1;
   }

   public float v3() {
      return this.v3;
   }

   public float x4() {
      return this.x4;
   }

   public float u2() {
      return this.u2;
   }

   public float v2() {
      return this.v2;
   }

   public float y3() {
      return this.y3;
   }

   public float y1() {
      return this.y1;
   }

   public float u4() {
      return this.u4;
   }

   public float u3() {
      return this.u3;
   }
}
