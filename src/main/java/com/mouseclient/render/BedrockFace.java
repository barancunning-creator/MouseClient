package com.mouseclient.render;

public class BedrockFace {
   public BedrockVertex[] vertices;
   public ModelVector3 normal;

   public BedrockFace(BedrockVertex[] vertices, float normalX, float normalY, float normalZ) {
      this.vertices = vertices;
      this.normal = new ModelVector3(normalX, normalY, normalZ);
   }

   public BedrockFace(BedrockVertex[] vertices, ModelVector3 normal) {
      this.vertices = vertices;
      this.normal = normal;
   }
}
