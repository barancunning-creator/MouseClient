package com.mouseclient.dreamix.protection.api.impl.profile.models;

import com.mouseclient.render.CosmeticEntry;
import com.mouseclient.render.AnimatedCosmeticModel;
import com.mouseclient.render.BedrockGeometryParser;
import com.mouseclient.render.BedrockGeometry;
import com.mouseclient.render.avatar.CosmeticAssetCache;
import com.mouseclient.util.time.MouseClientCosmeticWarmup;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

public final class CosmeticModelCache {
   private static final Map<Integer, AnimatedCosmeticModel> models = new ConcurrentHashMap<>();

   private static float readFloat(JsonObject json, String property, float fallback) {
      try {
         return json.has(property) ? json.get(property).getAsFloat() : fallback;
      } catch (Exception error) {
         return fallback;
      }
   }

   private static int readInt(JsonObject json, String property, int fallback) {
      try {
         return json.has(property) ? json.get(property).getAsInt() : fallback;
      } catch (Exception error) {
         return fallback;
      }
   }

   private static float[] readVector(JsonElement value, float[] fallback) {
      if (value == null || value.isJsonNull()) {
         return (float[])fallback.clone();
      }

      if (value.isJsonPrimitive()) {
         float scalarValue;
         try {
            scalarValue = value.getAsFloat();
         } catch (Exception error) {
            return (float[])fallback.clone();
         }

         return new float[]{scalarValue, scalarValue, scalarValue};
      } else {
         if (!value.isJsonArray()) {
            return (float[])fallback.clone();
         }

         JsonArray jsonArray = value.getAsJsonArray();
         float[] resultVector = (float[])fallback.clone();

         for (int componentIndex = 0; componentIndex < Math.min(3, jsonArray.size()); componentIndex++) {
            try {
               resultVector[componentIndex] = jsonArray.get(componentIndex).getAsFloat();
            } catch (Exception parseFailure) {
            }
         }

         return resultVector;
      }
   }

   private static JsonObject objectProperty(JsonObject json, String property) {
      return json != null && json.has(property) && json.get(property).isJsonObject() ? json.getAsJsonObject(property) : null;
   }

   private static Map<String, AnimatedCosmeticModel.AnimationClip> parseAnimations(JsonObject json) {
      LinkedHashMap parsedAnimations = new LinkedHashMap();
      JsonObject animationRoot = objectProperty(json, ("animation"));
      JsonObject animationsJson = animationRoot == null ? null : objectProperty(animationRoot, ("animations"));
      if (animationsJson == null) {
         return parsedAnimations;
      }

      for (Entry animationEntry : animationsJson.entrySet()) {
         if (((JsonElement)animationEntry.getValue()).isJsonObject()) {
            JsonObject animationJson = ((JsonElement)animationEntry.getValue()).getAsJsonObject();
            boolean looping = readBoolean(animationJson, ("loop"), true);
            float duration = readFloat(animationJson, ("animation_length"), 1.0F);
            LinkedHashMap boneAnimations = new LinkedHashMap();
            JsonObject bonesJson = objectProperty(animationJson, ("bones"));
            if (bonesJson != null) {
               for (Entry boneEntry : bonesJson.entrySet()) {
                  if (((JsonElement)boneEntry.getValue()).isJsonObject()) {
                     JsonObject boneJson = ((JsonElement)boneEntry.getValue()).getAsJsonObject();
                     boneAnimations.put(
                        (String)boneEntry.getKey(),
                        new AnimatedCosmeticModel.BoneAnimation(
                           parseVectorTrack(boneJson.get(("rotation")), new float[]{0.0F, 0.0F, 0.0F}),
                           parseVectorTrack(boneJson.get(("position")), new float[]{0.0F, 0.0F, 0.0F}),
                           parseVectorTrack(boneJson.get(("scale")), new float[]{1.0F, 1.0F, 1.0F})
                        )
                     );
                  }
               }
            }

            parsedAnimations.put((String)animationEntry.getKey(), new AnimatedCosmeticModel.AnimationClip((String)animationEntry.getKey(), looping, duration, boneAnimations));
         }
      }

      return parsedAnimations;
   }

   private static AnimatedCosmeticModel.VectorAnimationTrack parseVectorTrack(JsonElement value, float[] fallback) {
      AnimatedCosmeticModel.VectorAnimationTrack track = new AnimatedCosmeticModel.VectorAnimationTrack();
      if (value == null || value.isJsonNull()) {
         return track;
      }

      if (value.isJsonArray() || value.isJsonPrimitive()) {
         track.constant = readVector(value, fallback);
         return track;
      }

      if (!value.isJsonObject()) {
         return track;
      }

      JsonObject json = value.getAsJsonObject();

      for (Entry keyframeEntry : json.entrySet()) {
         try {
            float keyframeTime = Float.parseFloat((String)keyframeEntry.getKey());
            track.keyframes.put(keyframeTime, readKeyframeVector((JsonElement)keyframeEntry.getValue(), fallback));
         } catch (NumberFormatException error) {
         }
      }

      return track;
   }

   private static boolean readBoolean(JsonObject json, String property, boolean fallback) {
      try {
         return json.has(property) ? json.get(property).getAsBoolean() : fallback;
      } catch (Exception error) {
         return fallback;
      }
   }

   private CosmeticModelCache() {
   }

   private static AnimatedCosmeticModel loadModel(CosmeticEntry entry) {
      try (ByteArrayInputStream modelInput = new ByteArrayInputStream(CosmeticAssetCache.readAsset(entry.modelResource()))) {
         if (modelInput == null) {
            return null;
         }

         JsonObject json = JsonParser.parseReader(new InputStreamReader(modelInput, StandardCharsets.UTF_8)).getAsJsonObject();
         JsonObject modelJson = objectProperty(json, ("model"));
         if (modelJson == null) {
            return null;
         }

         BedrockGeometry geometry = BedrockGeometryParser.parse(modelJson.toString());
         return geometry == null
            ? null
            : new AnimatedCosmeticModel(
               entry.id(),
               readString(json, ("name"), entry.name()),
               readInt(json, ("pos"), 1),
               readFloat(json, ("scale"), 1.0F),
               readFloat(json, ("x"), 0.0F),
               readFloat(json, ("y"), 0.0F),
               readFloat(json, ("z"), 0.0F),
               readFloat(json, ("yaw"), 0.0F),
               readFloat(json, ("pitch"), 0.0F),
               readFloat(json, ("roll"), 0.0F),
               geometry,
               parseAnimations(json)
            );
      } catch (Exception error) {
         System.err.println("[Cosmetics] failed to load " + entry.name() + ": " + error);
         return null;
      }
   }

   public static AnimatedCosmeticModel getModel(CosmeticEntry entry) {
      if (entry != null && entry.modelResource() != null) {
         AnimatedCosmeticModel model = models.get(entry.id());
         if (model == null) {
            MouseClientCosmeticWarmup.warmModel(entry);
         }

         return model;
      } else {
         return null;
      }
   }

   public static CosmeticModelCache.ModelOffset modelOffset(CosmeticEntry entry) {
      AnimatedCosmeticModel model = getModel(entry);
      return model == null ? null : new CosmeticModelCache.ModelOffset(model.x, model.y, model.z);
   }

   private static float[] readKeyframeVector(JsonElement value, float[] fallback) {
      if (value != null && value.isJsonObject()) {
         JsonObject json = value.getAsJsonObject();
         if (json.has(("post"))) {
            return readVector(json.get(("post")), fallback);
         }

         if (json.has(("pre"))) {
            return readVector(json.get(("pre")), fallback);
         }

         if (json.has(("vector"))) {
            return readVector(json.get(("vector")), fallback);
         }
      }

      return readVector(value, fallback);
   }

   private static String readString(JsonObject json, String property, String fallback) {
      try {
         return json.has(property) ? json.get(property).getAsString() : fallback;
      } catch (Exception error) {
         return fallback;
      }
   }

   public static boolean warmup(CosmeticEntry entry) {
      if (models.containsKey(entry.id())) {
         return true;
      }

      AnimatedCosmeticModel model = loadModel(entry);
      return model != null && CosmeticAssetCache.loadTexture(entry.textureId(), entry.textureResource(), () -> models.put(entry.id(), model));
   }

   public record ModelOffset(float x, float y, float z) {
   }
}
