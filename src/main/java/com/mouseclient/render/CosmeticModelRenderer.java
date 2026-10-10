package com.mouseclient.render;

import com.mouseclient.dreamix.protection.api.impl.profile.models.CosmeticModelCache;
import java.util.Map.Entry;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class CosmeticModelRenderer {
   
   private static final float[] ZERO_VECTOR = new float[]{0.0F, 0.0F, 0.0F};
   private static final float[] UNIT_VECTOR = new float[]{1.0F, 1.0F, 1.0F};
   private static final float[] IDENTITY_POSE = new float[]{0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F};
   private static final float[] sampledVector = new float[3];
   private static final MatrixStack captureMatrices = new MatrixStack();
   private static final Vector3f transformedNormal = new Vector3f();
   private static final Matrix4f combinedPosition = new Matrix4f();
   private static final Matrix4f capturedPosition = new Matrix4f();
   private static final Matrix3f combinedNormal = new Matrix3f();
   private static final Matrix3f capturedNormal = new Matrix3f();
   private static int capturedCubeCount;

   private static void applyModelTransform(AnimatedCosmeticModel model, MatrixStack matrices, boolean includeTranslation) {
      matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((180.0F)));
      if (includeTranslation) {
         matrices.translate(model.x, model.y, model.z);
      }

      if (model.yaw != 0.0F) {
         matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(model.yaw));
      }

      if (model.pitch != 0.0F) {
         matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(model.pitch));
      }

      if (model.roll != 0.0F) {
         matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(model.roll));
      }

      matrices.scale(model.scale, model.scale, model.scale);
   }

   private static void renderCapturedCubes(BedrockCube[] cubes, float[] transforms, int count, Matrix4f positionMatrix, Matrix3f normalMatrix, VertexConsumer vertices, int light) {
      for (int cubeIndex = 0; cubeIndex < count; cubeIndex++) {
         int transformOffset = cubeIndex * 25;
         combinedPosition.set(positionMatrix).mul(capturedPosition.set(transforms, transformOffset));
         combinedNormal.set(normalMatrix).mul(capturedNormal.set(transforms, transformOffset + 16));
         renderCube(cubes[cubeIndex], combinedPosition, combinedNormal, vertices, light);
      }
   }

   private static AnimatedCosmeticModel prepareModel(CosmeticEntry entry, boolean gliding, boolean swimming, boolean sneaking, boolean moving, float time) {
      AnimatedCosmeticModel model = CosmeticModelCache.getModel(entry);
      if (model != null && entry.textureId() != null) {
         model.resetPose();
         String animationName = model.animationForState(gliding, swimming, sneaking, moving);
         if (animationName != null) {
            AnimatedCosmeticModel.AnimationClip clip = model.animations.get(animationName);
            if (clip != null) {
               applyAnimation(model, clip, time);
            }
         }

         return model;
      } else {
         return null;
      }
   }

   private CosmeticModelRenderer() {
   }

   private static void renderBone(BedrockBone bone, MatrixStack matrices, VertexConsumer vertices, int light) {
      if (!bone.hidden) {
         matrices.push();
         applyBoneTransform(bone, matrices);

         for (BedrockCube cube : bone.cubes) {
            matrices.push();
            applyCubeTransform(cube, matrices);
            renderCube(cube, matrices.peek().getPositionMatrix(), matrices.peek().getNormalMatrix(), vertices, light);
            matrices.pop();
         }

         for (BedrockBone childBone : bone.children) {
            renderBone(childBone, matrices, vertices, light);
         }

         matrices.pop();
      }
   }

   private static void renderCube(BedrockCube cube, Matrix4f positionMatrix, Matrix3f normalMatrix, VertexConsumer vertices, int light) {
      for (BedrockFace face : cube.faces) {
         if (face != null) {
            Vector3f faceNormal = transformedNormal.set(face.normal.getX(), face.normal.getY(), face.normal.getZ());
            normalMatrix.transform(faceNormal);
            float normalX = faceNormal.x();
            float normalY = faceNormal.y();
            float normalZ = faceNormal.z();
            if ((cube.size.getY() == 0.0F || cube.size.getZ() == 0.0F) && normalX < 0.0F) {
               normalX = -normalX;
            }

            if ((cube.size.getX() == 0.0F || cube.size.getZ() == 0.0F) && normalY < 0.0F) {
               normalY = -normalY;
            }

            if ((cube.size.getX() == 0.0F || cube.size.getY() == 0.0F) && normalZ < 0.0F) {
               normalZ = -normalZ;
            }

            for (BedrockVertex vertex : face.vertices) {
               vertices.vertex(positionMatrix, vertex.position.getX(), vertex.position.getY(), vertex.position.getZ())
                  .color(255, 255, 255, 255)
                  .texture(vertex.u, vertex.v)
                  .overlay(OverlayTexture.DEFAULT_UV)
                  .light(light)
                  .normal(normalX, normalY, normalZ);
            }
         }
      }
   }

   private static void captureBoneTransforms(BedrockBone bone, MatrixStack matrices, BedrockCube[] cubes, float[] transforms, Matrix4f maskTransform) {
      if (!bone.hidden) {
         matrices.push();
         applyBoneTransform(bone, matrices);

         for (BedrockCube cube : bone.cubes) {
            matrices.push();
            applyCubeTransform(cube, matrices);
            int capturedIndex = capturedCubeCount++;
            cubes[capturedIndex] = cube;
            matrices.peek().getPositionMatrix().get(transforms, capturedIndex * 25);
            matrices.peek().getNormalMatrix().get(transforms, capturedIndex * 25 + 16);
            if (maskTransform != null) {
               CosmeticMaskGeometry.appendCube(cube, combinedPosition.set(maskTransform).mul(matrices.peek().getPositionMatrix()));
            }

            matrices.pop();
         }

         for (BedrockBone childBone : bone.children) {
            captureBoneTransforms(childBone, matrices, cubes, transforms, maskTransform);
         }

         matrices.pop();
      }
   }

   private static void applyBoneTransform(BedrockBone bone, MatrixStack matrices) {
      BedrockTransforms.translateBoneOffset(bone, matrices);
      BedrockTransforms.translateBonePivot(bone, matrices);
      BedrockTransforms.rotateBone(bone, matrices);
      BedrockTransforms.scaleBone(bone, matrices);
      BedrockTransforms.translateInverseBonePivot(bone, matrices);
   }

   public static void applyAnimation(AnimatedCosmeticModel model, AnimatedCosmeticModel.AnimationClip clip, float time) {
      float animationTime = clip.loop ? time % clip.duration : Math.min(time, clip.duration);

      for (Entry boneAnimationEntry : clip.bones.entrySet()) {
         BedrockBone bone = model.boneByName((String)boneAnimationEntry.getKey());
         if (bone != null) {
            float[] initialPose = model.initialBonePoses.get(boneAnimationEntry.getKey());
            if (initialPose == null) {
               initialPose = IDENTITY_POSE;
            }

            AnimatedCosmeticModel.BoneAnimation animation = (AnimatedCosmeticModel.BoneAnimation)boneAnimationEntry.getValue();
            if (!animation.rotation.isEmpty()) {
               float[] sampledRotation = animation.rotation.sample(animationTime, ZERO_VECTOR, sampledVector);
               bone.setRotationX(initialPose[0] + (float)Math.toRadians(-sampledRotation[0]));
               bone.setRotationY(initialPose[1] + (float)Math.toRadians(-sampledRotation[1]));
               bone.setRotationZ(initialPose[2] + (float)Math.toRadians(sampledRotation[2]));
            }

            if (!animation.position.isEmpty()) {
               float[] sampledPosition = animation.position.sample(animationTime, ZERO_VECTOR, sampledVector);
               bone.setOffsetX(initialPose[3] + sampledPosition[0]);
               bone.setOffsetY(initialPose[4] + sampledPosition[1]);
               bone.setOffsetZ(initialPose[5] + sampledPosition[2]);
            }

            if (!animation.scale.isEmpty()) {
               float[] sampledScale = animation.scale.sample(animationTime, UNIT_VECTOR, sampledVector);
               bone.setScaleX(initialPose[6] * sampledScale[0]);
               bone.setScaleY(initialPose[7] * sampledScale[1]);
               bone.setScaleZ(initialPose[8] * sampledScale[2]);
            }
         }
      }
   }

   private static void applyCubeTransform(BedrockCube cube, MatrixStack matrices) {
      BedrockTransforms.translateCubePivot(cube, matrices);
      BedrockTransforms.rotateCube(cube, matrices);
      BedrockTransforms.translateInverseCubePivot(cube, matrices);
   }

   public static boolean submitModel(
      CosmeticEntry entry, MatrixStack matrices, OrderedRenderCommandQueue queue, int light, boolean gliding, boolean swimming, boolean sneaking, boolean moving, float time
   ) {
      AnimatedCosmeticModel model = prepareModel(entry, gliding, swimming, sneaking, moving, time);
      if (model == null) {
         return false;
      }

      matrices.push();
      applyModelTransform(model, matrices, true);
      RenderLayer renderLayer = RenderLayers.entityCutoutNoCull(entry.textureId());
      boolean captureMask = CosmeticMaskGeometry.shouldCapture();
      if (captureMask) {
         CosmeticMaskGeometry.beginTextureBatch(entry.textureId());
      }

      BedrockCube[] capturedCubes = new BedrockCube[model.cubeCount];
      float[] capturedTransforms = new float[model.cubeCount * 25];
      captureMatrices.peek().getPositionMatrix().identity();
      captureMatrices.peek().getNormalMatrix().identity();
      capturedCubeCount = 0;
      Matrix4f maskPositionMatrix = matrices.peek().getPositionMatrix();

      for (BedrockBone bone : model.geometry.rootBones) {
         captureBoneTransforms(bone, captureMatrices, capturedCubes, capturedTransforms, captureMask ? maskPositionMatrix : null);
      }

      int capturedCount = capturedCubeCount;
      if (capturedCount > 0) {
         queue.submitCustom(matrices, renderLayer, (matrixEntry, vertices) -> renderCapturedCubes(capturedCubes, capturedTransforms, capturedCount, matrixEntry.getPositionMatrix(), matrixEntry.getNormalMatrix(), vertices, light));
      }

      matrices.pop();
      return true;
   }

   public static boolean renderPreview(
      CosmeticEntry entry, MatrixStack matrices, VertexConsumerProvider consumers, int light, boolean gliding, boolean swimming, boolean sneaking, boolean moving, float time
   ) {
      AnimatedCosmeticModel model = prepareModel(entry, gliding, swimming, sneaking, moving, time);
      if (model == null) {
         return false;
      }

      matrices.push();
      applyModelTransform(model, matrices, false);
      if (!model.geometry.rootBones.isEmpty()) {
         BedrockBone bone = model.geometry.rootBones.get(0);
         float pivotX = bone.getPivotX() / (16.0F);
         float pivotY = bone.getPivotY() / (16.0F);
         float pivotZ = bone.getPivotZ() / (16.0F);
         matrices.translate(pivotX, pivotY, pivotZ);
         matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((180.0F)));
         matrices.scale(1.0F, (-1.0F), 1.0F);
         matrices.translate(-pivotX, -pivotY, -pivotZ);
      } else {
         matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((180.0F)));
         matrices.scale(1.0F, (-1.0F), 1.0F);
      }

      VertexConsumer vertices = consumers.getBuffer(RenderLayers.entityCutoutNoCull(entry.textureId()));

      try {
         for (BedrockBone rootBone : model.geometry.rootBones) {
            renderBone(rootBone, matrices, vertices, light);
         }
      } finally {
         matrices.pop();
      }

      return true;
   }
}
