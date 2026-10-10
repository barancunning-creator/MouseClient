package com.mouseclient.render;

import com.mouseclient.modules.impl.render.Tags;
import java.util.ArrayList;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class CosmeticMaskGeometry {
   public static final int VERTEX_STRIDE = 5;
   private static float[] vertexData = new float[7680];
   private static int componentCount;
   private static final ArrayList<Identifier> textures = new ArrayList<>(4);
   private static final ArrayList<int[]> quadRanges = new ArrayList<>(4);
   private static final Vector3f transformedPosition = new Vector3f();

   public static Identifier textureAt(int batchIndex) {
      return textures.get(batchIndex);
   }

   public static void clear() {
      componentCount = 0;
      textures.clear();
      quadRanges.clear();
   }

   public static void appendCube(BedrockCube cube, Matrix4f transform) {
      if (!quadRanges.isEmpty()) {
         for (BedrockFace face : cube.faces) {
            if (face != null) {
               if (componentCount + 20 > vertexData.length) {
                  float[] expandedVertexData = new float[Math.max(vertexData.length * 2, componentCount + 20)];
                  System.arraycopy(vertexData, 0, expandedVertexData, 0, componentCount);
                  vertexData = expandedVertexData;
               }

               for (BedrockVertex vertex : face.vertices) {
                  transform.transformPosition(transformedPosition.set(vertex.position.getX(), vertex.position.getY(), vertex.position.getZ()));
                  vertexData[componentCount++] = transformedPosition.x;
                  vertexData[componentCount++] = transformedPosition.y;
                  vertexData[componentCount++] = transformedPosition.z;
                  vertexData[componentCount++] = vertex.u;
                  vertexData[componentCount++] = vertex.v;
               }

               quadRanges.get(quadRanges.size() - 1)[1]++;
            }
         }
      }
   }

   private CosmeticMaskGeometry() {
   }

   public static int firstQuad(int batchIndex) {
      return quadRanges.get(batchIndex)[0];
   }

   public static float vertexComponent(int componentIndex) {
      return vertexData[componentIndex];
   }

   public static void beginTextureBatch(Identifier texture) {
      textures.add(texture);
      quadRanges.add(new int[]{componentCount / 20, 0});
   }

   public static int quadCount(int batchIndex) {
      return quadRanges.get(batchIndex)[1];
   }

   public static int textureBatchCount() {
      return textures.size();
   }

   public static boolean shouldCapture() {
      return Tags.getInstance().isEnabled()
         && Tags.getInstance().applyShader.getValue()
         && MinecraftClient.getInstance().player != null
         && Tags.getInstance().shouldDisplayPlayer(MinecraftClient.getInstance().player);
   }

   public static int totalQuadCount() {
      return componentCount / 20;
   }
}
