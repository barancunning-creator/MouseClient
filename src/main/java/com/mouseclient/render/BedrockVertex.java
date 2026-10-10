package com.mouseclient.render;

public class BedrockVertex {
   public ModelVector3 position;
   public float u;
   public float v;

   public BedrockVertex(ModelVector3 position, float u, float v) {
      this.position = position;
      this.u = u;
      this.v = v;
   }

   public BedrockVertex(float x, float y, float z, float u, float v) {
      this.position = new ModelVector3(x, y, z);
      this.u = u;
      this.v = v;
   }
}
