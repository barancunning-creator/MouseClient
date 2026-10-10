package com.mouseclient.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BedrockGeometry {
   public List<BedrockBone> rootBones = new ArrayList<>();
   public int textureWidth = 64;
   public int textureHeight = 64;

   private BedrockBone findBoneRecursive(String name, BedrockBone bone) {
      if (bone.name.equals(name)) {
         return bone;
      }

      for (BedrockBone childBone : bone.children) {
         BedrockBone matchingBone = this.findBoneRecursive(name, childBone);
         if (matchingBone != null) {
            return matchingBone;
         }
      }

      return null;
   }

   public Optional<BedrockBone> findBone(String name) {
      for (BedrockBone bone : this.rootBones) {
         BedrockBone matchingBone = this.findBoneRecursive(name, bone);
         if (matchingBone != null) {
            return Optional.of(matchingBone);
         }
      }

      return Optional.empty();
   }
}
