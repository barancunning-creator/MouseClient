package com.mouseclient.render;

public class BedrockCube {
   public BedrockFace[] faces = new BedrockFace[6];
   public ModelVector3 size;
   public ModelVector3 pivot;
   public ModelVector3 rotation;
   public float inflate;
   public boolean mirror;

   public BedrockCube(float width, float height, float depth) {
      this.size = new ModelVector3(width, height, depth);
      this.pivot = new ModelVector3(0.0F, 0.0F, 0.0F);
      this.rotation = new ModelVector3(0.0F, 0.0F, 0.0F);
      this.inflate = 0.0F;
      this.mirror = false;
   }
}
