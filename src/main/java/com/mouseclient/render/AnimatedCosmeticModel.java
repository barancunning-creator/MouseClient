package com.mouseclient.render;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.Map.Entry;

public final class AnimatedCosmeticModel {
   public final int id;
   public final String name;
   public final int placementIndex;
   public final float scale;
   public final float x;
   public final float y;
   public final float z;
   public final float yaw;
   public final float pitch;
   public final float roll;
   public final BedrockGeometry geometry;
   public final Map<String, AnimatedCosmeticModel.AnimationClip> animations;
   public final Map<String, float[]> initialBonePoses = new HashMap<>();
   private final Map<String, BedrockBone> bonesByName = new HashMap<>();
   private final String[] stateAnimations = new String[16];
   private final boolean[] resolvedStates = new boolean[16];
   public int cubeCount;

   private String findAnimationContainingAny(String... keywords) {
      for (String animationName : this.animations.keySet()) {
         String normalizedName = normalizeAnimationName(animationName);

         for (String keyword : keywords) {
            if (normalizedName.contains(keyword)) {
               return animationName;
            }
         }
      }

      return null;
   }

   private String findAnimationByName(String name) {
      for (String candidateName : this.animations.keySet()) {
         if (normalizeAnimationName(candidateName).equals(name)) {
            return candidateName;
         }
      }

      return this.findAnimationContainingAny(name);
   }

   public String selectAnimation(boolean gliding, boolean swimming, boolean sneaking, boolean moving) {
      if (this.animations.isEmpty()) {
         return null;
      }

      if (gliding) {
         String glidingAnimation = this.findAnimationContainingAny(("elytra"), ("flying"), ("fly"));
         if (glidingAnimation != null) {
            return glidingAnimation;
         }
      }

      if (swimming) {
         if (moving) {
            String movingWaterAnimation = this.findAnimationContainingAll(("moving"), ("water"));
            if (movingWaterAnimation != null) {
               return movingWaterAnimation;
            }
         }

         String waterAnimation = this.findAnimationContainingAny(("swimming"), ("swim"));
         if (waterAnimation != null) {
            return waterAnimation;
         }

         waterAnimation = this.findAnimationContainingAll(("idle"), ("water"));
         if (waterAnimation != null) {
            return waterAnimation;
         }

         waterAnimation = this.findAnimationContainingAll(("main"), ("water"));
         if (waterAnimation != null) {
            return waterAnimation;
         }
      }

      if (sneaking) {
         if (moving) {
            String movingSneakAnimation = this.findAnimationContainingAll(("moving"), ("sneak"));
            if (movingSneakAnimation != null) {
               return movingSneakAnimation;
            }
         }

         String sneakingAnimation = this.findAnimationContainingAll(("idle"), ("sneak"));
         if (sneakingAnimation != null) {
            return sneakingAnimation;
         }

         sneakingAnimation = this.findAnimationContainingAll(("main"), ("sneak"));
         if (sneakingAnimation != null) {
            return sneakingAnimation;
         }

         sneakingAnimation = this.findAnimationContainingAny(("sneak"), ("crouch"));
         if (sneakingAnimation != null) {
            return sneakingAnimation;
         }
      }

      if (moving) {
         String movingAnimation = this.findAnimationContainingAny(("walking"), ("walk"), ("moving"), ("move"), ("run"));
         if (movingAnimation != null && !normalizeAnimationName(movingAnimation).contains(("water")) && !normalizeAnimationName(movingAnimation).contains(("sneak"))) {
            return movingAnimation;
         }
      }

      String idleAnimation = this.findAnimationByName(("idle"));
      if (idleAnimation != null && !normalizeAnimationName(idleAnimation).contains(("water")) && !normalizeAnimationName(idleAnimation).contains(("sneak"))) {
         return idleAnimation;
      }

      String mainAnimation = this.findAnimationByName(("main"));
      if (mainAnimation != null && !normalizeAnimationName(mainAnimation).contains(("water")) && !normalizeAnimationName(mainAnimation).contains(("sneak"))) {
         return mainAnimation;
      }

      for (String fallbackAnimationName : this.animations.keySet()) {
         String normalizedName = normalizeAnimationName(fallbackAnimationName);
         if (!normalizedName.contains(("gui")) && !normalizedName.contains(("preview"))) {
            return fallbackAnimationName;
         }
      }

      return this.animations.keySet().iterator().next();
   }

   private void resetBonePose(BedrockBone bone) {
      float[] initialPose = this.initialBonePoses.get(bone.name);
      if (initialPose != null) {
         bone.setRotationX(initialPose[0]);
         bone.setRotationY(initialPose[1]);
         bone.setRotationZ(initialPose[2]);
         bone.setOffsetX(initialPose[3]);
         bone.setOffsetY(initialPose[4]);
         bone.setOffsetZ(initialPose[5]);
         bone.setScaleX(initialPose[6]);
         bone.setScaleY(initialPose[7]);
         bone.setScaleZ(initialPose[8]);
      }

      for (BedrockBone childBone : bone.children) {
         this.resetBonePose(childBone);
      }
   }

   private String findAnimationContainingAll(String... keywords) {
      for (String animationName : this.animations.keySet()) {
         String normalizedName = normalizeAnimationName(animationName);
         boolean allKeywordsMatch = true;

         for (String keyword : keywords) {
            allKeywordsMatch &= normalizedName.contains(keyword);
         }

         if (allKeywordsMatch) {
            return animationName;
         }
      }

      return null;
   }

   public String animationForState(boolean gliding, boolean swimming, boolean sneaking, boolean moving) {
      int stateMask = (gliding ? 1 : 0) | (swimming ? 2 : 0) | (sneaking ? 4 : 0) | (moving ? 8 : 0);
      if (!this.resolvedStates[stateMask]) {
         this.stateAnimations[stateMask] = this.selectAnimation(gliding, swimming, sneaking, moving);
         this.resolvedStates[stateMask] = true;
      }

      return this.stateAnimations[stateMask];
   }

   private static String normalizeAnimationName(String name) {
      return name.toLowerCase(Locale.ROOT);
   }

   private void indexBone(BedrockBone bone) {
      this.initialBonePoses
         .put(
            bone.name,
            new float[]{
               bone.getRotationX(),
               bone.getRotationY(),
               bone.getRotationZ(),
               bone.getOffsetX(),
               bone.getOffsetY(),
               bone.getOffsetZ(),
               bone.getScaleX(),
               bone.getScaleY(),
               bone.getScaleZ()
            }
         );
      this.bonesByName.putIfAbsent(bone.name, bone);
      this.cubeCount = this.cubeCount + bone.cubes.size();

      for (BedrockBone childBone : bone.children) {
         this.indexBone(childBone);
      }
   }

   public AnimatedCosmeticModel(
      int id,
      String name,
      int placementIndex,
      float scale,
      float x,
      float y,
      float z,
      float yaw,
      float pitch,
      float roll,
      BedrockGeometry geometry,
      Map<String, AnimatedCosmeticModel.AnimationClip> animations
   ) {
      this.id = id;
      this.name = name;
      this.placementIndex = placementIndex;
      this.scale = scale;
      this.x = x;
      this.y = y;
      this.z = z;
      this.yaw = yaw;
      this.pitch = pitch;
      this.roll = roll;
      this.geometry = geometry;
      this.animations = animations;

      for (BedrockBone bone : geometry.rootBones) {
         this.indexBone(bone);
      }
   }

   public void resetPose() {
      for (BedrockBone bone : this.geometry.rootBones) {
         this.resetBonePose(bone);
      }
   }

   public BedrockBone boneByName(String name) {
      return this.bonesByName.get(name);
   }

   public static final class AnimationClip {
      public final String name;
      public final boolean loop;
      public final float duration;
      public final Map<String, AnimatedCosmeticModel.BoneAnimation> bones;

      public AnimationClip(String name, boolean loop, float duration, Map<String, AnimatedCosmeticModel.BoneAnimation> bones) {
         this.name = name;
         this.loop = loop;
         this.duration = Math.max((0.001F), duration);
         this.bones = bones;
      }
   }

   public static final class VectorAnimationTrack {
      public final NavigableMap<Float, float[]> keyframes = new TreeMap<>();
      public float[] constant;
      private float[] keyframeTimes;
      private float[][] keyframeValues;

      public boolean isEmpty() {
         return this.constant == null && this.keyframes.isEmpty();
      }

      public float[] sample(float time, float[] fallback) {
         return this.sample(time, fallback, new float[3]);
      }

      public float[] sample(float time, float[] fallback, float[] result) {
         if (this.constant != null) {
            return this.constant;
         }

         if (this.keyframes.isEmpty()) {
            return fallback;
         }

         if (this.keyframes.size() == 1) {
            return this.keyframes.firstEntry().getValue();
         }

         if (this.keyframeTimes == null || this.keyframeTimes.length != this.keyframes.size()) {
            float[] times = new float[this.keyframes.size()];
            float[][] values = new float[this.keyframes.size()][];
            int index = 0;

            for (Entry keyframe : this.keyframes.entrySet()) {
               times[index] = (Float)keyframe.getKey();
               values[index++] = (float[])keyframe.getValue();
            }

            this.keyframeValues = values;
            this.keyframeTimes = times;
         }

         float[] cachedTimes = this.keyframeTimes;
         int count = cachedTimes.length;
         if (time <= cachedTimes[0]) {
            return this.keyframeValues[0];
         }

         if (time >= cachedTimes[count - 1]) {
            return this.keyframeValues[count - 1];
         }

         int lowerIndex = 0;
         int upperIndex = count - 1;

         while (upperIndex - lowerIndex > 1) {
            int middleIndex = lowerIndex + upperIndex >>> 1;
            if (cachedTimes[middleIndex] <= time) {
               lowerIndex = middleIndex;
            } else {
               upperIndex = middleIndex;
            }
         }

         if (cachedTimes[lowerIndex] == time) {
            return this.keyframeValues[lowerIndex];
         }

         float progress = (time - cachedTimes[lowerIndex]) / (cachedTimes[upperIndex] - cachedTimes[lowerIndex]);
         progress = Math.max(0.0F, Math.min(1.0F, progress));
         float[] lowerValue = this.keyframeValues[lowerIndex];
         float[] upperValue = this.keyframeValues[upperIndex];
         result[0] = lowerValue[0] + progress * (upperValue[0] - lowerValue[0]);
         result[1] = lowerValue[1] + progress * (upperValue[1] - lowerValue[1]);
         result[2] = lowerValue[2] + progress * (upperValue[2] - lowerValue[2]);
         return result;
      }
   }

   public static final class BoneAnimation {
      public final AnimatedCosmeticModel.VectorAnimationTrack rotation;
      public final AnimatedCosmeticModel.VectorAnimationTrack position;
      public final AnimatedCosmeticModel.VectorAnimationTrack scale;

      public BoneAnimation(AnimatedCosmeticModel.VectorAnimationTrack rotation, AnimatedCosmeticModel.VectorAnimationTrack position, AnimatedCosmeticModel.VectorAnimationTrack scale) {
         this.rotation = rotation;
         this.position = position;
         this.scale = scale;
      }
   }
}
