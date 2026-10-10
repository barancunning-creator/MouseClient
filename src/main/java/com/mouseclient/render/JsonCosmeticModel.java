package com.mouseclient.render;

import com.mouseclient.network.ClientInitializer;
import com.mouseclient.render.avatar.CosmeticAssetCache;
import com.mouseclient.util.time.MouseClientCosmeticWarmup;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.model.json.Transformation;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class JsonCosmeticModel {
   private static final Map<Integer, JsonCosmeticModel> modelsById = new ConcurrentHashMap<>();
   private static final String[] FACE_DIRECTIONS = new String[]{
      ("north"), ("south"), ("east"), ("west"), ("up"), ("down")
   };
   private static final float[] ZERO_VECTOR = new float[]{0.0F, 0.0F, 0.0F};
   private static final float[] UNIT_VECTOR = new float[]{1.0F, 1.0F, 1.0F};
   private final ArrayList<JsonCosmeticModel.JsonTexture> textures;
   private final ArrayList<JsonCosmeticModel.JsonBone> rootBones;
   private final JsonCosmeticModel.JsonAnimationClip idleAnimation;
   private final JsonCosmeticModel.JsonAnimationClip walkAnimation;
   private final float[] headTransform;
   private final HashMap<String, Transformation> displayTransforms;
   private final float[] bounds;
   private final Vector3fc[] boundingVertices;
   private final boolean animatedTexture;
   private static final MatrixStack commandMatrices = new MatrixStack();
   private static final Quaternionf rotationScratch = new Quaternionf();
   private final JsonCosmeticModel.JsonBone[] bonesByIndex;
   private final JsonCosmeticModel.JsonVectorKeyframe[][][] idleTracksByIndex;
   private final JsonCosmeticModel.JsonVectorKeyframe[][][] walkTracksByIndex;
   private final float[] pose;
   private final float[][] idleSample;
   private final float[][] walkSample;
   private final Quaternionf headRotation;
   private float lastTime;
   private float lastMovementBlend;
   private boolean poseValid;
   private int textureColumn;
   private int textureRow;

   private void renderTexture(MatrixStack matrices, VertexConsumer vertices, int light, float time, float movementBlend, int textureIndex) {
      this.updatePose(time, movementBlend);
      JsonCosmeticModel.TextureFrameAnimation animation = this.textures.get(textureIndex).animation();
      if (animation != null) {
         int frame = animation.frameAt((int)(time * (20.0F)));
         this.textureColumn = frame % animation.columns();
         this.textureRow = frame / animation.columns();
      }

      for (JsonCosmeticModel.JsonBone bone : this.rootBones) {
         this.renderBone(bone, matrices, vertices, light, animation, textureIndex);
      }
   }

   private static float[] readFloatArray(JsonArray jsonArray, int length) {
      float[] values = new float[length];

      for (int var3 = 0; var3 < Math.min(length, jsonArray.size()); var3++) {
         values[var3] = jsonArray.get(var3).getAsFloat();
      }

      return values;
   }

   private static void collectBones(JsonCosmeticModel.JsonBone bone, ArrayList<JsonCosmeticModel.JsonBone> output) {
      output.add(bone);

      for (JsonCosmeticModel.JsonBone child : bone.children()) {
         collectBones(child, output);
      }
   }

   private static float[] sampleVector(JsonCosmeticModel.JsonVectorKeyframe[] keyframes, float time, float duration, float[] fallback, float[] output) {
      if (keyframes.length == 0) {
         return fallback;
      }

      if (keyframes.length == 1) {
         return keyframes[0].value();
      }

      JsonCosmeticModel.JsonVectorKeyframe previousKeyframe = keyframes[keyframes.length - 1];
      JsonCosmeticModel.JsonVectorKeyframe nextKeyframe = keyframes[0];
      float previousTime = previousKeyframe.time() - duration;
      float nextTime = nextKeyframe.time();

      for (int var9 = 0; var9 < keyframes.length - 1; var9++) {
         if (time >= keyframes[var9].time() && time <= keyframes[var9 + 1].time()) {
            previousKeyframe = keyframes[var9];
            nextKeyframe = keyframes[var9 + 1];
            previousTime = previousKeyframe.time();
            nextTime = nextKeyframe.time();
            break;
         }
      }

      if (time > keyframes[keyframes.length - 1].time()) {
         previousKeyframe = keyframes[keyframes.length - 1];
         nextKeyframe = keyframes[0];
         previousTime = previousKeyframe.time();
         nextTime = nextKeyframe.time() + duration;
      }

      if (previousKeyframe.step()) {
         return previousKeyframe.value();
      }

      float wrappedTime = time < previousTime ? time + duration : time;
      float progress = nextTime == previousTime ? 0.0F : MathHelper.clamp((wrappedTime - previousTime) / (nextTime - previousTime), 0.0F, 1.0F);
      output[0] = MathHelper.lerp(progress, previousKeyframe.value()[0], nextKeyframe.value()[0]);
      output[1] = MathHelper.lerp(progress, previousKeyframe.value()[1], nextKeyframe.value()[1]);
      output[2] = MathHelper.lerp(progress, previousKeyframe.value()[2], nextKeyframe.value()[2]);
      return output;
   }

   public static boolean load(CosmeticEntry entry) {
      if (modelsById.containsKey(entry.id())) {
         return true;
      }

      try {
         JsonObject json = JsonParser.parseString(new String(CosmeticAssetCache.readAsset(entry.modelResource()), StandardCharsets.UTF_8)).getAsJsonObject();
         ArrayList textures = new ArrayList();

         for (JsonElement jsonElement : json.getAsJsonArray(("textures"))) {
            JsonObject textureJson = jsonElement.getAsJsonObject();
            String file = textureJson.get(("file")).getAsString();
            JsonCosmeticModel.TextureFrameAnimation textureAnimation = null;
            if (textureJson.has(("animation"))) {
               JsonObject animationJson = textureJson.getAsJsonObject(("animation"));
               JsonArray jsonArray = animationJson.getAsJsonArray(("frames"));
               int[] frameIndexes = new int[jsonArray.size()];
               int[] frameTicks = new int[jsonArray.size()];
               int totalTicks = 0;

               for (int var13 = 0; var13 < jsonArray.size(); var13++) {
                  frameIndexes[var13] = jsonArray.get(var13).getAsJsonArray().get(0).getAsInt();
                  frameTicks[var13] = Math.max(1, jsonArray.get(var13).getAsJsonArray().get(1).getAsInt());
                  totalTicks += frameTicks[var13];
               }

               textureAnimation = new JsonCosmeticModel.TextureFrameAnimation(
                  animationJson.get(("columns")).getAsInt(), animationJson.get(("rows")).getAsInt(), animationJson.get(("imageWidth")).getAsInt(), animationJson.get(("imageHeight")).getAsInt(), frameIndexes, frameTicks, totalTicks
               );
            }

            textures.add(
               new JsonCosmeticModel.JsonTexture(
                  Identifier.of(("mouseclient"), "cosmetics/" + file), file, textureJson.get(("uvWidth")).getAsFloat(), textureJson.get(("uvHeight")).getAsFloat(), textureAnimation
               )
            );
         }

         ArrayList rootBones = new ArrayList();
         int[] nextIndex = new int[]{0};

         for (JsonElement var19 : json.getAsJsonArray(("bones"))) {
            rootBones.add(parseBone(var19.getAsJsonObject(), ZERO_VECTOR, textures, nextIndex));
         }

         JsonObject animationsJson = json.has(("animations")) ? json.getAsJsonObject(("animations")) : new JsonObject();
         HashMap displayTransforms = new HashMap();
         if (json.has(("display"))) {
            for (Entry var23 : json.getAsJsonObject(("display")).entrySet()) {
               float[] transformValues = readFloatArray(((JsonElement)var23.getValue()).getAsJsonArray(), 9);
               displayTransforms.put(
                  (String)var23.getKey(),
                  new Transformation(
                     new Vector3f(transformValues[0], transformValues[1], transformValues[2]), new Vector3f(transformValues[3], transformValues[4], transformValues[5]), new Vector3f(transformValues[6], transformValues[7], transformValues[8])
                  )
               );
            }
         }

         JsonCosmeticModel model = new JsonCosmeticModel(
            textures,
            rootBones,
            animationsJson.has(("idle")) ? parseAnimation(animationsJson.getAsJsonObject(("idle"))) : null,
            animationsJson.has(("walk")) ? parseAnimation(animationsJson.getAsJsonObject(("walk"))) : null,
            json.has(("displayHead")) ? readFloatArray(json.getAsJsonArray(("displayHead")), 9) : null,
            displayTransforms
         );

         for (int var24 = 0; var24 < textures.size(); var24++) {
            if (!CosmeticAssetCache.loadTexture(
               ((JsonCosmeticModel.JsonTexture)textures.get(var24)).id(),
               ((JsonCosmeticModel.JsonTexture)textures.get(var24)).file(),
               var24 == textures.size() - 1 ? () -> modelsById.put(entry.id(), model) : null
            )) {
               return false;
            }
         }

         return !textures.isEmpty();
      } catch (Exception error) {
         ClientInitializer.logger().warn(("[Cosmetics] model {} not loaded: {}"), entry.modelResource(), error.toString());
         return false;
      }
   }

   private static void rotateZYX(MatrixStack matrices, float xDegrees, float yDegrees, float zDegrees) {
      if (zDegrees != 0.0F) {
         matrices.multiply(rotationScratch.rotationZ(zDegrees * (0.017453292F)));
      }

      if (yDegrees != 0.0F) {
         matrices.multiply(rotationScratch.rotationY(yDegrees * (0.017453292F)));
      }

      if (xDegrees != 0.0F) {
         matrices.multiply(rotationScratch.rotationX(xDegrees * (0.017453292F)));
      }
   }

   public void applyHeadTransform(MatrixStack matrices) {
      if (this.headTransform != null) {
         matrices.translate(this.headTransform[3], this.headTransform[4], this.headTransform[5]);
         matrices.multiply(this.headRotation);
         matrices.scale(this.headTransform[6], this.headTransform[7], this.headTransform[8]);
      }

      matrices.translate((-0.5F), (-0.5F), (-0.5F));
   }

   public void applyHeldShieldTransform(MatrixStack matrices, ItemDisplayContext displayContext) {
      float width = Math.max(1.0F, this.bounds[3] - this.bounds[0]);
      float height = Math.max(1.0F, this.bounds[4] - this.bounds[1]);
      float scale = Math.min((18.0F) / width, (22.0F) / height);
      matrices.translate(
         (displayContext.isLeftHand() ? (-1.5F) : (1.5F)) / (16.0F), (displayContext.isFirstPerson() ? (1.5F) : 0.0F) / (16.0F), (-0.125F)
      );
      matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(displayContext.isLeftHand() ? (90.0F) : (-90.0F)));
      matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(displayContext.isLeftHand() ? (-8.0F) : (8.0F)));
      matrices.scale(scale, scale, scale);
      matrices.translate(
         -(this.bounds[0] + this.bounds[3]) / (32.0F),
         -(this.bounds[1] + this.bounds[4]) / (32.0F),
         -(this.bounds[2] + this.bounds[5]) / (32.0F)
      );
   }

   private JsonCosmeticModel(
      ArrayList<JsonCosmeticModel.JsonTexture> textures,
      ArrayList<JsonCosmeticModel.JsonBone> rootBones,
      JsonCosmeticModel.JsonAnimationClip idleAnimation,
      JsonCosmeticModel.JsonAnimationClip walkAnimation,
      float[] headTransform,
      HashMap<String, Transformation> displayTransforms
   ) {
      this.bounds = new float[]{(3.4028235E38F), (3.4028235E38F), (3.4028235E38F), (-3.4028235E38F), (-3.4028235E38F), (-3.4028235E38F)};
      this.idleSample = new float[3][3];
      this.walkSample = new float[3][3];
      this.textures = textures;
      this.rootBones = rootBones;
      this.idleAnimation = idleAnimation;
      this.walkAnimation = walkAnimation;
      this.headTransform = headTransform;
      this.displayTransforms = displayTransforms;
      ArrayList<JsonCosmeticModel.JsonBone> allBones = new ArrayList();

      for (JsonCosmeticModel.JsonBone root : rootBones) {
         collectBones(root, allBones);
      }

      int boneCount = 0;

      for (JsonCosmeticModel.JsonBone indexedBone : allBones) {
         boneCount = Math.max(boneCount, indexedBone.index() + 1);
      }

      this.bonesByIndex = new JsonCosmeticModel.JsonBone[boneCount];
      this.idleTracksByIndex = new JsonCosmeticModel.JsonVectorKeyframe[boneCount][][];
      this.walkTracksByIndex = new JsonCosmeticModel.JsonVectorKeyframe[boneCount][][];

      for (JsonCosmeticModel.JsonBone trackedBone : allBones) {
         this.bonesByIndex[trackedBone.index()] = trackedBone;
         this.idleTracksByIndex[trackedBone.index()] = idleAnimation == null ? null : idleAnimation.tracksByBone().get(trackedBone.uuid());
         this.walkTracksByIndex[trackedBone.index()] = walkAnimation == null ? null : walkAnimation.tracksByBone().get(trackedBone.uuid());
      }

      this.pose = new float[boneCount * 9];
      this.headRotation = headTransform == null ? null : new Quaternionf().rotationXYZ(headTransform[0] * (0.017453292F), headTransform[1] * (0.017453292F), headTransform[2] * (0.017453292F));

      for (JsonCosmeticModel.JsonBone boundedBone : rootBones) {
         this.includeBoneBounds(boundedBone);
      }

      if (this.bounds[0] == (3.4028235E38F)) {
         System.arraycopy(new float[]{0.0F, 0.0F, 0.0F, (16.0F), (16.0F), (16.0F)}, 0, this.bounds, 0, 6);
      }

      this.boundingVertices = new Vector3fc[8];

      for (int var15 = 0; var15 < 8; var15++) {
         this.boundingVertices[var15] = new Vector3f(
            this.bounds[(var15 & 1) == 0 ? 0 : 3] / (16.0F),
            this.bounds[(var15 & 2) == 0 ? 1 : 4] / (16.0F),
            this.bounds[(var15 & 4) == 0 ? 2 : 5] / (16.0F)
         );
      }

      this.animatedTexture = textures.stream().anyMatch(textureDefinition -> textureDefinition.animation() != null);
   }

   public boolean hasAnimatedTexture() {
      return this.animatedTexture;
   }

   public Transformation displayTransform(ItemDisplayContext displayContext) {
      return this.displayTransforms.getOrDefault(displayContext.asString(), Transformation.IDENTITY);
   }

   public void submit(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, float time, float movementBlend) {
      for (int var6 = 0; var6 < this.textures.size(); var6++) {
         int textureIndex = var6;
         queue.submitCustom(matrices, RenderLayers.entityCutoutNoCull(this.textures.get(var6).id()), (matrixEntry, vertices) -> {
            MatrixStack drawMatrices = commandMatrices;
            drawMatrices.peek().getPositionMatrix().set(matrixEntry.getPositionMatrix());
            drawMatrices.peek().getNormalMatrix().set(matrixEntry.getNormalMatrix());
            this.renderTexture(drawMatrices, vertices, light, time, movementBlend, textureIndex);
         });
      }
   }

   private static JsonCosmeticModel.JsonAnimationClip parseAnimation(JsonObject json) {
      HashMap tracksByBone = new HashMap();

      for (Entry boneEntry : json.getAsJsonObject(("tracks")).entrySet()) {
         ArrayList<JsonCosmeticModel.JsonVectorKeyframe> positionKeys = new ArrayList<>();
         ArrayList<JsonCosmeticModel.JsonVectorKeyframe> rotationKeys = new ArrayList<>();
         ArrayList<JsonCosmeticModel.JsonVectorKeyframe> scaleKeys = new ArrayList<>();

         for (JsonElement jsonElement : ((JsonElement)boneEntry.getValue()).getAsJsonArray()) {
            JsonObject keyframeJson = jsonElement.getAsJsonObject();
            JsonCosmeticModel.JsonVectorKeyframe keyframe = new JsonCosmeticModel.JsonVectorKeyframe(
               keyframeJson.get(("time")).getAsFloat(), readFloatArray(keyframeJson.getAsJsonArray(("value")), 3), keyframeJson.get(("step")).getAsBoolean()
            );
            switch (keyframeJson.get(("channel")).getAsString()) {
               case ("position"):
                  positionKeys.add(keyframe);
                  break;
               case ("scale"):
                  scaleKeys.add(keyframe);
                  break;
               default:
                  rotationKeys.add(keyframe);
            }
         }

         tracksByBone.put(
            (String)boneEntry.getKey(),
            new JsonCosmeticModel.JsonVectorKeyframe[][]{
               positionKeys.toArray(JsonCosmeticModel.JsonVectorKeyframe[]::new), rotationKeys.toArray(JsonCosmeticModel.JsonVectorKeyframe[]::new), scaleKeys.toArray(JsonCosmeticModel.JsonVectorKeyframe[]::new)
            }
         );
      }

      return new JsonCosmeticModel.JsonAnimationClip(json.get(("length")).getAsFloat(), tracksByBone);
   }

   public void render(MatrixStack matrices, VertexConsumerProvider consumers, int light, float time, float movementBlend) {
      for (int var6 = 0; var6 < this.textures.size(); var6++) {
         VertexConsumer vertices = consumers.getBuffer(RenderLayers.entityCutoutNoCull(this.textures.get(var6).id()));
         this.renderTexture(matrices, vertices, light, time, movementBlend, var6);
      }
   }

   private static void sampleVectorInto(JsonCosmeticModel.JsonVectorKeyframe[] keyframes, float time, float duration, float[] fallback, float[] output) {
      float[] sample = sampleVector(keyframes, time, duration, fallback, output);
      if (sample != output) {
         System.arraycopy(sample, 0, output, 0, 3);
      }
   }

   private void renderBone(JsonCosmeticModel.JsonBone bone, MatrixStack matrices, VertexConsumer vertices, int light, JsonCosmeticModel.TextureFrameAnimation textureAnimation, int textureIndex) {
      float[] poseValues = this.pose;
      int poseOffset = bone.index() * 9;
      matrices.push();
      matrices.translate(poseValues[poseOffset], poseValues[poseOffset + 1], poseValues[poseOffset + 2]);
      rotateZYX(matrices, poseValues[poseOffset + 3], poseValues[poseOffset + 4], poseValues[poseOffset + 5]);
      matrices.scale(poseValues[poseOffset + 6], poseValues[poseOffset + 7], poseValues[poseOffset + 8]);

      for (JsonCosmeticModel.JsonCube cube : bone.cubes()) {
         this.renderCube(cube, matrices, vertices, light, textureAnimation, textureIndex);
      }

      for (JsonCosmeticModel.JsonBone child : bone.children()) {
         this.renderBone(child, matrices, vertices, light, textureAnimation, textureIndex);
      }

      matrices.pop();
   }

   public static JsonCosmeticModel getOrRequest(CosmeticEntry entry) {
      JsonCosmeticModel model = modelsById.get(entry.id());
      if (model == null) {
         MouseClientCosmeticWarmup.warmModel(entry);
      }

      return model;
   }

   private void updatePose(float time, float movementBlend) {
      if (!this.poseValid || this.lastTime != time || this.lastMovementBlend != movementBlend) {
         float idleTime = this.idleAnimation != null && !(this.idleAnimation.length() <= 0.0F)
            ? time % this.idleAnimation.length()
            : 0.0F;
         float walkTime = this.walkAnimation != null && !(this.walkAnimation.length() <= 0.0F)
            ? time % this.walkAnimation.length()
            : 0.0F;
         float[] poseValues = this.pose;
         float[] idlePosition = this.idleSample[0];
         float[] idleRotation = this.idleSample[1];
         float[] idleScale = this.idleSample[2];
         float[] walkPosition = this.walkSample[0];
         float[] walkRotation = this.walkSample[1];
         float[] walkScale = this.walkSample[2];

         for (int var12 = 0; var12 < this.bonesByIndex.length; var12++) {
            JsonCosmeticModel.JsonBone bone = this.bonesByIndex[var12];
            JsonCosmeticModel.JsonVectorKeyframe[][] idleTracks = this.idleTracksByIndex[var12];
            JsonCosmeticModel.JsonVectorKeyframe[][] walkTracks = this.walkTracksByIndex[var12];
            if (idleTracks != null) {
               sampleVectorInto(idleTracks[0], idleTime, this.idleAnimation.length(), ZERO_VECTOR, idlePosition);
               sampleVectorInto(idleTracks[1], idleTime, this.idleAnimation.length(), ZERO_VECTOR, idleRotation);
               sampleVectorInto(idleTracks[2], idleTime, this.idleAnimation.length(), UNIT_VECTOR, idleScale);
            } else {
               System.arraycopy(ZERO_VECTOR, 0, idlePosition, 0, 3);
               System.arraycopy(ZERO_VECTOR, 0, idleRotation, 0, 3);
               System.arraycopy(UNIT_VECTOR, 0, idleScale, 0, 3);
            }

            if (walkTracks != null) {
               sampleVectorInto(walkTracks[0], walkTime, this.walkAnimation.length(), ZERO_VECTOR, walkPosition);
               sampleVectorInto(walkTracks[1], walkTime, this.walkAnimation.length(), ZERO_VECTOR, walkRotation);
               sampleVectorInto(walkTracks[2], walkTime, this.walkAnimation.length(), UNIT_VECTOR, walkScale);
            } else {
               System.arraycopy(ZERO_VECTOR, 0, walkPosition, 0, 3);
               System.arraycopy(ZERO_VECTOR, 0, walkRotation, 0, 3);
               System.arraycopy(UNIT_VECTOR, 0, walkScale, 0, 3);
            }

            int poseOffset = var12 * 9;

            for (int var17 = 0; var17 < 3; var17++) {
               poseValues[poseOffset + var17] = (bone.offset()[var17] + MathHelper.lerp(movementBlend, idlePosition[var17], walkPosition[var17])) / (16.0F);
               poseValues[poseOffset + 3 + var17] = bone.rotation()[var17] + MathHelper.lerp(movementBlend, idleRotation[var17], walkRotation[var17]);
               poseValues[poseOffset + 6 + var17] = MathHelper.lerp(movementBlend, idleScale[var17], walkScale[var17]);
            }
         }

         this.lastTime = time;
         this.lastMovementBlend = movementBlend;
         this.poseValid = true;
      }
   }

   private void includeBoneBounds(JsonCosmeticModel.JsonBone bone) {
      for (JsonCosmeticModel.JsonCube cube : bone.cubes()) {
         for (int var4 = 0; var4 < 3; var4++) {
            this.bounds[var4] = Math.min(this.bounds[var4], Math.min(cube.from()[var4], cube.getTo()[var4]));
            this.bounds[var4 + 3] = Math.max(this.bounds[var4 + 3], Math.max(cube.from()[var4], cube.getTo()[var4]));
         }
      }

      for (JsonCosmeticModel.JsonBone child : bone.children()) {
         this.includeBoneBounds(child);
      }
   }

   public Vector3fc[] boundingVertices() {
      return this.boundingVertices;
   }

   private static JsonCosmeticModel.JsonBone parseBone(JsonObject json, float[] parentOrigin, ArrayList<JsonCosmeticModel.JsonTexture> textures, int[] nextIndex) {
      Object boneIndex = nextIndex[0]++;
      float[] origin = readFloatArray(json.getAsJsonArray(("origin")), 3);
      ArrayList cubes = new ArrayList();

      for (JsonElement jsonElement : json.getAsJsonArray(("cubes"))) {
         JsonObject cubeJson = jsonElement.getAsJsonObject();
         float[] from = readFloatArray(cubeJson.getAsJsonArray(("from")), 3);
         float[] to = readFloatArray(cubeJson.getAsJsonArray(("to")), 3);
         float[] cubeOrigin = readFloatArray(cubeJson.getAsJsonArray(("origin")), 3);
         float[] cubeRotation = readFloatArray(cubeJson.getAsJsonArray(("rotation")), 3);
         float minX = (Math.min(from[0], to[0]) - cubeOrigin[0]) / (16.0F);
         float minY = (Math.min(from[1], to[1]) - cubeOrigin[1]) / (16.0F);
         float minZ = (Math.min(from[2], to[2]) - cubeOrigin[2]) / (16.0F);
         float maxX = (Math.max(from[0], to[0]) - cubeOrigin[0]) / (16.0F);
         float maxY = (Math.max(from[1], to[1]) - cubeOrigin[1]) / (16.0F);
         float maxZ = (Math.max(from[2], to[2]) - cubeOrigin[2]) / (16.0F);
         boolean flatX = maxX - minX < (5.0E-4F);
         boolean flatY = maxY - minY < (5.0E-4F);
         boolean flatZ = maxZ - minZ < (5.0E-4F);
         boolean[] hasTexture = new boolean[textures.size()];
         ArrayList faces = new ArrayList();

         for (JsonElement var26 : cubeJson.getAsJsonArray(("faces"))) {
            JsonObject faceJson = var26.getAsJsonObject();
            float[] uv = readFloatArray(faceJson.getAsJsonArray(("uv")), 4);
            int direction = 0;

            while (direction < 5 && !FACE_DIRECTIONS[direction].equals(faceJson.get(("dir")).getAsString())) {
               direction++;
            }

            int textureIndex = faceJson.get(("texture")).getAsInt();
            boolean validTexture = textureIndex >= 0 && textureIndex < textures.size();
            boolean skipFace = !validTexture || (direction <= 1 ? flatX || flatY : (direction <= 3 ? flatY || flatZ : flatX || flatZ));
            float uvWidth = validTexture ? ((JsonCosmeticModel.JsonTexture)textures.get(textureIndex)).uvWidth() : 1.0F;
            float uvHeight = validTexture ? ((JsonCosmeticModel.JsonTexture)textures.get(textureIndex)).uvHeight() : 1.0F;
            float normalX = 0.0F;
            float normalY = 0.0F;
            float normalZ = 0.0F;
            float[] corners;
            switch (direction) {
               case 0:
                  corners = new float[]{maxX, maxY, minZ, maxX, minY, minZ, minX, minY, minZ, minX, maxY, minZ};
                  normalZ = (-1.0F);
                  break;
               case 1:
                  corners = new float[]{minX, maxY, maxZ, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ};
                  normalZ = 1.0F;
                  break;
               case 2:
                  corners = new float[]{maxX, maxY, maxZ, maxX, minY, maxZ, maxX, minY, minZ, maxX, maxY, minZ};
                  normalX = 1.0F;
                  break;
               case 3:
                  corners = new float[]{minX, maxY, minZ, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ};
                  normalX = (-1.0F);
                  break;
               case 4:
                  corners = new float[]{minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ};
                  normalY = 1.0F;
                  break;
               default:
                  corners = new float[]{minX, minY, maxZ, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ};
                  normalY = (-1.0F);
            }

            if (!skipFace) {
               hasTexture[textureIndex] = true;
            }

            faces.add(
               new JsonCosmeticModel.JsonFace(
                  direction,
                  uv[0] / uvWidth,
                  uv[1] / uvHeight,
                  uv[2] / uvWidth,
                  uv[3] / uvHeight,
                  Math.floorMod(faceJson.get(("rotation")).getAsInt() / 90, 4),
                  textureIndex,
                  skipFace,
                  corners,
                  normalX,
                  normalY,
                  normalZ
               )
            );
         }

         Quaternionf cubeQuaternion = cubeRotation[0] == 0.0F && cubeRotation[1] == 0.0F && cubeRotation[2] == 0.0F
            ? null
            : new Quaternionf().rotationZ(cubeRotation[2] * (0.017453292F)).rotateY(cubeRotation[1] * (0.017453292F)).rotateX(cubeRotation[0] * (0.017453292F));
         float[] cubeOffset = new float[]{(cubeOrigin[0] - origin[0]) / (16.0F), (cubeOrigin[1] - origin[1]) / (16.0F), (cubeOrigin[2] - origin[2]) / (16.0F)};
         cubes.add(new JsonCosmeticModel.JsonCube(from, to, cubeOrigin, cubeRotation, faces, cubeOffset, cubeQuaternion, hasTexture));
      }

      ArrayList children = new ArrayList();

      for (JsonElement var42 : json.getAsJsonArray(("children"))) {
         children.add(parseBone(var42.getAsJsonObject(), origin, textures, nextIndex));
      }

      float[] offset = new float[]{origin[0] - parentOrigin[0], origin[1] - parentOrigin[1], origin[2] - parentOrigin[2]};
      return new JsonCosmeticModel.JsonBone((int)boneIndex, json.get(("uuid")).getAsString(), origin, offset, readFloatArray(json.getAsJsonArray(("rotation")), 3), cubes, children);
   }

   private void renderCube(JsonCosmeticModel.JsonCube cube, MatrixStack matrices, VertexConsumer vertices, int light, JsonCosmeticModel.TextureFrameAnimation textureAnimation, int textureIndex) {
      if (cube.hasTexture()[textureIndex]) {
         matrices.push();
         matrices.translate(cube.offset()[0], cube.offset()[1], cube.offset()[2]);
         if (cube.rotationQuaternion() != null) {
            matrices.multiply(cube.rotationQuaternion());
         }

         net.minecraft.client.util.math.MatrixStack.Entry matrixEntry = matrices.peek();

         for (JsonCosmeticModel.JsonFace face : cube.faces()) {
            if (face.texture() == textureIndex && !face.skip()) {
               float uStart = face.getUStart();
               float vStart = face.getVStart();
               float uEnd = face.getUEnd();
               float vEnd = face.getVEnd();
               if (textureAnimation != null) {
                  uStart = (this.textureColumn + uStart) / textureAnimation.columns();
                  uEnd = (this.textureColumn + uEnd) / textureAnimation.columns();
                  vStart = (this.textureRow + vStart) / textureAnimation.rows();
                  vEnd = (this.textureRow + vEnd) / textureAnimation.rows();
                  float uInset = (0.5F) / textureAnimation.imageWidth() * Math.signum(uEnd - uStart);
                  float vInset = (0.5F) / textureAnimation.imageHeight() * Math.signum(vEnd - vStart);
                  uStart += uInset;
                  uEnd -= uInset;
                  vStart += vInset;
                  vEnd -= vInset;
               }

               float[] corners = face.corners();

               for (int var24 = 0; var24 < 4; var24++) {
                  int uvCorner = (var24 + face.uvTurns()) % 4;
                  float u = uvCorner <= 1 ? uStart : uEnd;
                  float v = uvCorner != 0 && uvCorner != 3 ? vEnd : vStart;
                  vertices.vertex(matrixEntry, corners[var24 * 3], corners[var24 * 3 + 1], corners[var24 * 3 + 2])
                     .color(255, 255, 255, 255)
                     .texture(u, v)
                     .overlay(OverlayTexture.DEFAULT_UV)
                     .light(light)
                     .normal(matrixEntry, face.getNormalX(), face.getNormalY(), face.getNormalZ());
               }
            }
         }

         matrices.pop();
      }
   }

   private record JsonBone(
      int index,
      String uuid,
      float[] origin,
      float[] offset,
      float[] rotation,
      ArrayList<JsonCosmeticModel.JsonCube> cubes,
      ArrayList<JsonCosmeticModel.JsonBone> children
   ) {
   }

   private record JsonAnimationClip(float length, HashMap<String, JsonCosmeticModel.JsonVectorKeyframe[][]> tracksByBone) {
   }

   private record JsonCube(
      float[] from,
      float[] to,
      float[] origin,
      float[] rotation,
      ArrayList<JsonCosmeticModel.JsonFace> faces,
      float[] offset,
      Quaternionf rotationQuaternion,
      boolean[] hasTexture
   ) {
      public float[] getTo() {
         return this.to;
      }
   }

   private record JsonVectorKeyframe(float time, float[] value, boolean step) {
   }

   private record JsonFace(
      int direction,
      float uStart,
      float vStart,
      float uEnd,
      float vEnd,
      int uvTurns,
      int texture,
      boolean skip,
      float[] corners,
      float normalX,
      float normalY,
      float normalZ
   ) {
      public float getUStart() {
         return this.uStart;
      }

      public float getUEnd() {
         return this.uEnd;
      }

      public float getVEnd() {
         return this.vEnd;
      }

      public float getNormalX() {
         return this.normalX;
      }

      public float getVStart() {
         return this.vStart;
      }

      public float getNormalZ() {
         return this.normalZ;
      }

      public float getNormalY() {
         return this.normalY;
      }
   }

   private record JsonTexture(Identifier id, String file, float uvWidth, float uvHeight, JsonCosmeticModel.TextureFrameAnimation animation) {
   }

   private record TextureFrameAnimation(int columns, int rows, int imageWidth, int imageHeight, int[] frameIndexes, int[] frameTicks, int totalTicks) {
      public int frameAt(int tick) {
         int remainingTicks = this.totalTicks == 0 ? 0 : Math.floorMod(tick, this.totalTicks);

         for (int var3 = 0; var3 < this.frameIndexes.length; var3++) {
            if (remainingTicks < this.frameTicks[var3]) {
               return this.frameIndexes[var3];
            }

            remainingTicks -= this.frameTicks[var3];
         }

         return this.frameIndexes.length == 0 ? 0 : this.frameIndexes[this.frameIndexes.length - 1];
      }
   }
}
