package com.mouseclient.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.HashMap;

public class BedrockGeometryParser {

   private static BedrockBone parseBone(JsonObject json, int textureWidth, int textureHeight) {
      String boneName = json.get(("name")).getAsString();
      BedrockBone bone = new BedrockBone(boneName);
      if (json.has(("pivot"))) {
         JsonArray jsonArray = json.getAsJsonArray(("pivot"));
         bone.pivotX = -jsonArray.get(0).getAsFloat();
         bone.pivotY = jsonArray.get(1).getAsFloat();
         bone.pivotZ = jsonArray.get(2).getAsFloat();
      }

      if (json.has(("rotation"))) {
         JsonArray rotationDegrees = json.getAsJsonArray(("rotation"));
         bone.setRotationX((float)Math.toRadians(-rotationDegrees.get(0).getAsFloat()));
         bone.setRotationY((float)Math.toRadians(-rotationDegrees.get(1).getAsFloat()));
         bone.setRotationZ((float)Math.toRadians(rotationDegrees.get(2).getAsFloat()));
      }

      if (json.has(("cubes"))) {
         for (JsonElement jsonElement : json.getAsJsonArray(("cubes"))) {
            JsonObject cubeJson = jsonElement.getAsJsonObject();
            BedrockCube cube = parseCube(cubeJson, textureWidth, textureHeight);
            bone.cubes.add(cube);
         }
      }

      return bone;
   }

   private static BedrockCube parseCube(JsonObject json, int textureWidth, int textureHeight) {
      float[] origin = readFloatArray(json, ("origin"), new float[]{0.0F, 0.0F, 0.0F});
      float[] size = readFloatArray(json, ("size"), new float[]{1.0F, 1.0F, 1.0F});
      float[] pivot = readFloatArray(json, ("pivot"), (float[])origin.clone());
      float[] rotationDegrees = readFloatArray(json, ("rotation"), new float[]{0.0F, 0.0F, 0.0F});
      float inflate = json.has(("inflate")) ? json.get(("inflate")).getAsFloat() : 0.0F;
      boolean mirror = json.has(("mirror")) && json.get(("mirror")).getAsBoolean();
      BedrockCube cube = new BedrockCube(size[0], size[1], size[2]);
      cube.pivot = new ModelVector3(-pivot[0], pivot[1], pivot[2]);
      cube.rotation = new ModelVector3((float)Math.toRadians(-rotationDegrees[0]), (float)Math.toRadians(-rotationDegrees[1]), (float)Math.toRadians(rotationDegrees[2]));
      cube.inflate = inflate;
      cube.mirror = mirror;
      buildCubeFaces(cube, origin, size, inflate, mirror, json, textureWidth, textureHeight);
      return cube;
   }

   private static float[] readFloatArray(JsonObject json, String property, float[] fallback) {
      if (!json.has(property)) {
         return fallback;
      }

      JsonArray jsonArray = json.getAsJsonArray(property);
      float[] values = new float[jsonArray.size()];

      for (int componentIndex = 0; componentIndex < jsonArray.size(); componentIndex++) {
         values[componentIndex] = jsonArray.get(componentIndex).getAsFloat();
      }

      return values;
   }

   private static BedrockFace parseFaceUv(
      JsonObject json,
      String side,
      float minX,
      float minY,
      float minZ,
      float maxX,
      float maxY,
      float maxZ,
      float normalX,
      float normalY,
      float normalZ,
      int textureWidth,
      int textureHeight
   ) {
      if (!json.has(side)) {
         return null;
      }

      JsonObject faceJson = json.getAsJsonObject(side);
      JsonArray jsonArray = faceJson.getAsJsonArray(("uv"));
      JsonArray uvSize = faceJson.getAsJsonArray(("uv_size"));
      float u = jsonArray.get(0).getAsFloat() / textureWidth;
      float v = jsonArray.get(1).getAsFloat() / textureHeight;
      float uvWidth = uvSize.get(0).getAsFloat() / textureWidth;
      float uvHeight = uvSize.get(1).getAsFloat() / textureHeight;
      BedrockVertex[] vertices = buildFaceVertices(side, minX, minY, minZ, maxX, maxY, maxZ, u, v, uvWidth, uvHeight);
      return new BedrockFace(vertices, normalX, normalY, normalZ);
   }

   private static BedrockGeometry parseGeometry(JsonObject json) {
      JsonArray jsonArray = json.getAsJsonArray(("minecraft:geometry"));
      if (jsonArray != null && !jsonArray.isEmpty()) {
         JsonObject geometryJson = jsonArray.get(0).getAsJsonObject();
         JsonObject description = geometryJson.getAsJsonObject(("description"));
         int textureWidth = description.has(("texture_width")) ? description.get(("texture_width")).getAsInt() : 64;
         int textureHeight = description.has(("texture_height")) ? description.get(("texture_height")).getAsInt() : 64;
         BedrockGeometry geometry = new BedrockGeometry();
         geometry.textureWidth = textureWidth;
         geometry.textureHeight = textureHeight;
         JsonArray bonesJson = geometryJson.getAsJsonArray(("bones"));
         if (bonesJson != null) {
            HashMap bonesByName = new HashMap();

            for (JsonElement jsonElement : bonesJson) {
               JsonObject boneJson = jsonElement.getAsJsonObject();
               BedrockBone bone = parseBone(boneJson, textureWidth, textureHeight);
               bonesByName.put(bone.name, bone);
            }

            for (JsonElement boneEntry : bonesJson) {
               JsonObject hierarchyJson = boneEntry.getAsJsonObject();
               String boneName = hierarchyJson.get(("name")).getAsString();
               BedrockBone childBone = (BedrockBone)bonesByName.get(boneName);
               if (hierarchyJson.has(("parent"))) {
                  String parentName = hierarchyJson.get(("parent")).getAsString();
                  BedrockBone parent = (BedrockBone)bonesByName.get(parentName);
                  if (parent != null) {
                     parent.children.add(childBone);
                     childBone.parent = parent;
                  }
               } else {
                  geometry.rootBones.add(childBone);
               }
            }
         }

         return geometry;
      } else {
         System.err.println(("[Cosmetics] No minecraft:geometry found in model"));
         return null;
      }
   }

   private static void buildCubeFaces(BedrockCube cube, float[] origin, float[] size, float inflate, boolean mirror, JsonObject json, int textureWidth, int textureHeight) {
      float originX = origin[0];
      float originY = origin[1];
      float originZ = origin[2];
      float width = size[0];
      float height = size[1];
      float depth = size[2];
      originX -= inflate;
      originY -= inflate;
      originZ -= inflate;
      width += inflate * 2.0F;
      height += inflate * 2.0F;
      depth += inflate * 2.0F;
      float textureU = 0.0F;
      float textureV = 0.0F;
      boolean perFaceUv = false;
      JsonObject faceUvJson = null;
      if (json.has(("uv"))) {
         JsonElement jsonElement = json.get(("uv"));
         if (jsonElement.isJsonArray()) {
            JsonArray jsonArray = jsonElement.getAsJsonArray();
            textureU = jsonArray.get(0).getAsFloat();
            textureV = jsonArray.get(1).getAsFloat();
         } else if (jsonElement.isJsonObject()) {
            perFaceUv = true;
            faceUvJson = jsonElement.getAsJsonObject();
         }
      }

      float minX = -(originX + width) / (16.0F);
      float minY = originY / (16.0F);
      float minZ = originZ / (16.0F);
      float maxX = -originX / (16.0F);
      float maxY = (originY + height) / (16.0F);
      float maxZ = (originZ + depth) / (16.0F);
      if (perFaceUv && faceUvJson != null) {
         cube.faces[0] = parseFaceUv(faceUvJson, ("west"), minX, minY, minZ, minX, maxY, maxZ, (-1.0F), 0.0F, 0.0F, textureWidth, textureHeight);
         cube.faces[1] = parseFaceUv(faceUvJson, ("east"), maxX, minY, minZ, maxX, maxY, maxZ, 1.0F, 0.0F, 0.0F, textureWidth, textureHeight);
         cube.faces[2] = parseFaceUv(faceUvJson, ("down"), minX, minY, minZ, maxX, minY, maxZ, 0.0F, (-1.0F), 0.0F, textureWidth, textureHeight);
         cube.faces[3] = parseFaceUv(faceUvJson, ("up"), minX, maxY, minZ, maxX, maxY, maxZ, 0.0F, 1.0F, 0.0F, textureWidth, textureHeight);
         cube.faces[4] = parseFaceUv(faceUvJson, ("north"), minX, minY, minZ, maxX, maxY, minZ, 0.0F, 0.0F, (-1.0F), textureWidth, textureHeight);
         cube.faces[5] = parseFaceUv(faceUvJson, ("south"), minX, minY, maxZ, maxX, maxY, maxZ, 0.0F, 0.0F, 1.0F, textureWidth, textureHeight);
      } else {
         float atlasWidth = textureWidth;
         float atlasHeight = textureHeight;
         cube.faces[0] = buildBoxUvFace(
            minX, minY, minZ, minX, maxY, maxZ, (-1.0F), 0.0F, 0.0F, textureU, textureV, depth, height, width, atlasWidth, atlasHeight, ("west")
         );
         cube.faces[1] = buildBoxUvFace(
            maxX, minY, minZ, maxX, maxY, maxZ, 1.0F, 0.0F, 0.0F, textureU, textureV, depth, height, width, atlasWidth, atlasHeight, ("east")
         );
         cube.faces[2] = buildBoxUvFace(
            minX, minY, minZ, maxX, minY, maxZ, 0.0F, (-1.0F), 0.0F, textureU, textureV, depth, height, width, atlasWidth, atlasHeight, ("down")
         );
         cube.faces[3] = buildBoxUvFace(
            minX, maxY, minZ, maxX, maxY, maxZ, 0.0F, 1.0F, 0.0F, textureU, textureV, depth, height, width, atlasWidth, atlasHeight, ("up")
         );
         cube.faces[4] = buildBoxUvFace(
            minX, minY, minZ, maxX, maxY, minZ, 0.0F, 0.0F, (-1.0F), textureU, textureV, depth, height, width, atlasWidth, atlasHeight, ("north")
         );
         cube.faces[5] = buildBoxUvFace(
            minX, minY, maxZ, maxX, maxY, maxZ, 0.0F, 0.0F, 1.0F, textureU, textureV, depth, height, width, atlasWidth, atlasHeight, ("south")
         );
      }
   }

   public static BedrockGeometry parse(String source) {
      try {
         JsonObject json = JsonParser.parseString(source).getAsJsonObject();
         return parseGeometry(json);
      } catch (Exception error) {
         System.err.println("[Cosmetics] Failed to parse Bedrock model: " + error);
         return null;
      }
   }

   private static BedrockFace buildBoxUvFace(
      float minX,
      float minY,
      float minZ,
      float maxX,
      float maxY,
      float maxZ,
      float normalX,
      float normalY,
      float normalZ,
      float textureU,
      float textureV,
      float depth,
      float height,
      float width,
      float textureWidth,
      float textureHeight,
      String side
   ) {
      float u;
      float v;
      float uvWidth;
      float uvHeight;
      switch (side) {
         case ("north"):
            u = (textureU + depth + width) / textureWidth;
            v = (textureV + depth) / textureHeight;
            uvWidth = width / textureWidth;
            uvHeight = height / textureHeight;
            break;
         case ("south"):
            u = (textureU + depth + width + depth) / textureWidth;
            v = (textureV + depth) / textureHeight;
            uvWidth = width / textureWidth;
            uvHeight = height / textureHeight;
            break;
         case ("east"):
            u = textureU / textureWidth;
            v = (textureV + depth) / textureHeight;
            uvWidth = depth / textureWidth;
            uvHeight = height / textureHeight;
            break;
         case ("west"):
            u = (textureU + depth + width) / textureWidth;
            v = (textureV + depth) / textureHeight;
            uvWidth = depth / textureWidth;
            uvHeight = height / textureHeight;
            break;
         case ("up"):
            u = (textureU + depth) / textureWidth;
            v = textureV / textureHeight;
            uvWidth = width / textureWidth;
            uvHeight = depth / textureHeight;
            break;
         case ("down"):
            u = (textureU + depth + width) / textureWidth;
            v = textureV / textureHeight;
            uvWidth = width / textureWidth;
            uvHeight = depth / textureHeight;
            break;
         default:
            u = 0.0F;
            v = 0.0F;
            uvWidth = 0.0F;
            uvHeight = 0.0F;
      }

      BedrockVertex[] vertices = buildFaceVertices(side, minX, minY, minZ, maxX, maxY, maxZ, u, v, uvWidth, uvHeight);
      return new BedrockFace(vertices, normalX, normalY, normalZ);
   }

   private static BedrockVertex[] buildFaceVertices(
      String side, float minX, float minY, float minZ, float maxX, float maxY, float maxZ, float u, float v, float uvWidth, float uvHeight
   ) {
      BedrockVertex[] vertices = new BedrockVertex[4];
      float uMax = u + uvWidth;
      float vMax = v + uvHeight;
      switch (side) {
         case ("north"):
            vertices[0] = new BedrockVertex(maxX, maxY, minZ, u, v);
            vertices[1] = new BedrockVertex(minX, maxY, minZ, uMax, v);
            vertices[2] = new BedrockVertex(minX, minY, minZ, uMax, vMax);
            vertices[3] = new BedrockVertex(maxX, minY, minZ, u, vMax);
            break;
         case ("south"):
            vertices[0] = new BedrockVertex(minX, maxY, maxZ, u, v);
            vertices[1] = new BedrockVertex(maxX, maxY, maxZ, uMax, v);
            vertices[2] = new BedrockVertex(maxX, minY, maxZ, uMax, vMax);
            vertices[3] = new BedrockVertex(minX, minY, maxZ, u, vMax);
            break;
         case ("east"):
            vertices[0] = new BedrockVertex(maxX, maxY, maxZ, u, v);
            vertices[1] = new BedrockVertex(maxX, maxY, minZ, uMax, v);
            vertices[2] = new BedrockVertex(maxX, minY, minZ, uMax, vMax);
            vertices[3] = new BedrockVertex(maxX, minY, maxZ, u, vMax);
            break;
         case ("west"):
            vertices[0] = new BedrockVertex(minX, maxY, minZ, u, v);
            vertices[1] = new BedrockVertex(minX, maxY, maxZ, uMax, v);
            vertices[2] = new BedrockVertex(minX, minY, maxZ, uMax, vMax);
            vertices[3] = new BedrockVertex(minX, minY, minZ, u, vMax);
            break;
         case ("up"):
            vertices[0] = new BedrockVertex(minX, maxY, minZ, u, v);
            vertices[1] = new BedrockVertex(minX, maxY, maxZ, u, vMax);
            vertices[2] = new BedrockVertex(maxX, maxY, maxZ, uMax, vMax);
            vertices[3] = new BedrockVertex(maxX, maxY, minZ, uMax, v);
            break;
         case ("down"):
            vertices[0] = new BedrockVertex(maxX, minY, minZ, u, v);
            vertices[1] = new BedrockVertex(maxX, minY, maxZ, u, vMax);
            vertices[2] = new BedrockVertex(minX, minY, maxZ, uMax, vMax);
            vertices[3] = new BedrockVertex(minX, minY, minZ, uMax, v);
      }

      return vertices;
   }
}
