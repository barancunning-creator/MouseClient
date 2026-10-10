package com.mouseclient.modules.impl.render;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.internal.ArgbColor;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.network.HudStyle;
import com.mouseclient.render.DepthSnapshot;
import com.mouseclient.render.pipeline.WorldRenderEvent;
import com.mouseclient.util.entity.RotationAngles;
import com.mouseclient.util.entity.EntityRaycast;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import org.joml.Matrix4f;

@ModuleInfo(name = "BlockOverlay", description = "Выделяет грань блока под прицелом, видно сквозь блоки.", category = ModuleCategory.RENDER)
public final class BlockOverlay extends Module {
   private static BlockOverlay INSTANCE;
   public BooleanSetting fill;
   public DecimalSetting fillOpacity;
   public DecimalSetting lineWidth;
   public BooleanSetting animation;
   public DecimalSetting speed;
   public BooleanSetting useCustomColor;
   public ColorSetting color;
   private Box animatedFaceBounds;
   private float visibility;
   private long lastRenderMillis;
   private final EventListener<WorldRenderEvent> worldRenderListener = event -> {
      if (minecraftClient.player != null && minecraftClient.world != null) {
         float frameScale = Math.max(1.0F, Math.min((float)(System.currentTimeMillis() - this.lastRenderMillis), (100.0F))) / (16.666666F);
         this.lastRenderMillis = System.currentTimeMillis();
         BlockHitResult hitResult = minecraftClient.world
            .raycast(
               new RaycastContext(
                  minecraftClient.player.getCameraPosVec(event.getTickDelta()),
                  minecraftClient.player
                     .getCameraPosVec(event.getTickDelta())
                     .add(new RotationAngles(RotationAngles.cameraYaw(), RotationAngles.cameraPitch()).direction().multiply(minecraftClient.player.getBlockInteractionRange())),
                  ShapeType.OUTLINE,
                  FluidHandling.NONE,
                  minecraftClient.player
               )
            );
         Box targetBounds = hitResult.getType() == Type.BLOCK && !minecraftClient.world.getBlockState(hitResult.getBlockPos()).isAir()
            ? (
               minecraftClient.world.getBlockState(hitResult.getBlockPos()).getOutlineShape(minecraftClient.world, hitResult.getBlockPos()).isEmpty()
                  ? new Box(hitResult.getBlockPos()).expand((0.002D))
                  : minecraftClient.world
                     .getBlockState(hitResult.getBlockPos())
                     .getOutlineShape(minecraftClient.world, hitResult.getBlockPos())
                     .getBoundingBox()
                     .offset(hitResult.getBlockPos())
                     .expand((0.002D))
            )
            : null;
         if (targetBounds != null && this.isEntityOccluding(minecraftClient.player.getCameraPosVec(event.getTickDelta()), hitResult.getPos())) {
            targetBounds = null;
         }

         if (targetBounds != null) {
            targetBounds = this.faceBounds(targetBounds, hitResult.getSide());
         }

         if (targetBounds != null) {
            this.animatedFaceBounds = this.animatedFaceBounds != null && !(this.visibility < (0.003921569F)) && this.animation.getValue()
               ? new Box(
                  MathHelper.lerp(Math.min(1.0F, this.speed.getValue() * frameScale), this.animatedFaceBounds.minX, targetBounds.minX),
                  MathHelper.lerp(Math.min(1.0F, this.speed.getValue() * frameScale), this.animatedFaceBounds.minY, targetBounds.minY),
                  MathHelper.lerp(Math.min(1.0F, this.speed.getValue() * frameScale), this.animatedFaceBounds.minZ, targetBounds.minZ),
                  MathHelper.lerp(Math.min(1.0F, this.speed.getValue() * frameScale), this.animatedFaceBounds.maxX, targetBounds.maxX),
                  MathHelper.lerp(Math.min(1.0F, this.speed.getValue() * frameScale), this.animatedFaceBounds.maxY, targetBounds.maxY),
                  MathHelper.lerp(Math.min(1.0F, this.speed.getValue() * frameScale), this.animatedFaceBounds.maxZ, targetBounds.maxZ)
               )
               : targetBounds;
         }

         this.visibility = MathHelper.clamp(this.visibility + (targetBounds == null ? (-0.22F) : (0.22F)) * frameScale, 0.0F, 1.0F);
         if (this.animatedFaceBounds != null && !(this.visibility < (0.003921569F))) {
            DepthSnapshot.captureAndClearDepth();
            event.getMatrices().push();
            event.getMatrices()
               .translate(
                  this.animatedFaceBounds.minX - minecraftClient.gameRenderer.getCamera().getCameraPos().x,
                  this.animatedFaceBounds.minY - minecraftClient.gameRenderer.getCamera().getCameraPos().y,
                  this.animatedFaceBounds.minZ - minecraftClient.gameRenderer.getCamera().getCameraPos().z
               );
            Matrix4f positionMatrix = event.getMatrices().peek().getPositionMatrix();
            if (this.fill.getValue() && this.fillOpacity.getValue() > (0.001F)) {
               VertexConsumer vertices = minecraftClient.getBufferBuilders().getEntityVertexConsumers().getBuffer(RenderLayers.debugQuads());
               float[] xCoordinates = new float[]{0.0F, (float)(this.animatedFaceBounds.maxX - this.animatedFaceBounds.minX)};
               float[] yCoordinates = new float[]{0.0F, (float)(this.animatedFaceBounds.maxY - this.animatedFaceBounds.minY)};
               float[] zCoordinates = new float[]{0.0F, (float)(this.animatedFaceBounds.maxZ - this.animatedFaceBounds.minZ)};
               int[][] faceVertices = new int[][]{{0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}};
               int flatAxis = xCoordinates[1] < (0.01F) ? 0 : (yCoordinates[1] < (0.01F) ? 1 : (zCoordinates[1] < (0.01F) ? 2 : -1));

               for (int faceIndex = 0; faceIndex < faceVertices.length; faceIndex++) {
                  if (flatAxis < 0 || faceIndex == flatAxis * 2) {
                     for (int vertexIndex : faceVertices[faceIndex]) {
                        vertices.vertex(positionMatrix, xCoordinates[vertexIndex >> 2], yCoordinates[vertexIndex >> 1 & 1], zCoordinates[vertexIndex & 1])
                           .color(
                              ArgbColor.multiplyAlpha(
                                 this.useCustomColor.getValue() ? this.color.getArgb() : HudStyle.accentColor,
                                 this.fillOpacity.getValue() * this.visibility
                              )
                           );
                     }
                  }
               }
            }

            VertexRendering.drawOutline(
               event.getMatrices(),
               minecraftClient.getBufferBuilders().getEntityVertexConsumers().getBuffer(RenderLayers.lines()),
               VoxelShapes.cuboid(new Box(0.0, 0.0, 0.0, this.animatedFaceBounds.maxX - this.animatedFaceBounds.minX, this.animatedFaceBounds.maxY - this.animatedFaceBounds.minY, this.animatedFaceBounds.maxZ - this.animatedFaceBounds.minZ)),
               0.0,
               0.0,
               0.0,
               ArgbColor.multiplyAlpha(this.useCustomColor.getValue() ? this.color.getArgb() : HudStyle.accentColor, this.visibility),
               this.lineWidth.getValue()
            );
            minecraftClient.getBufferBuilders().getEntityVertexConsumers().draw();
            event.getMatrices().pop();
         }
      }
   };

   private Box faceBounds(Box bounds, Direction face) {
      return switch (face) {
         case DOWN -> new Box(bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.minY, bounds.maxZ);
         case UP -> new Box(bounds.minX, bounds.maxY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ);
         case NORTH -> new Box(bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.minZ);
         case SOUTH -> new Box(bounds.minX, bounds.minY, bounds.maxZ, bounds.maxX, bounds.maxY, bounds.maxZ);
         case WEST -> new Box(bounds.minX, bounds.minY, bounds.minZ, bounds.minX, bounds.maxY, bounds.maxZ);
         case EAST -> new Box(bounds.maxX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ);
      };
   }

   private boolean isEntityOccluding(Vec3d start, Vec3d end) {
      return EntityRaycast.raycast(
            minecraftClient.player,
            start,
            end,
            minecraftClient.player.getBoundingBox().stretch(end.subtract(start)).expand(1.0, 1.0, 1.0),
            entity -> !entity.isSpectator() && entity.canHit(),
            start.distanceTo(end)
         )
         != null;
   }

   private BlockOverlay() {
      this.addSetting(new SettingSeparator(("Бокс")));
      this.fill = this.addSetting(new BooleanSetting(("Заливка"), ("Заливать грань цветом."), true));
      this.fillOpacity = this.addSetting(new DecimalSetting(("Прозрачность заливки"), ("Насколько плотно залиты грани."), (0.25D), 0.0, 1.0, (0.05D)));
      this.lineWidth = this.addSetting(new DecimalSetting(("Толщина линии"), ("Толщина линий оутлайна."), (2.0D), (0.5D), (6.0D), (0.1D)));
      this.addSetting(new SettingSeparator(("Анимация")));
      this.animation = this.addSetting(new BooleanSetting(("Анимация"), ("Бокс плавно переезжает по x, y, z на новый блок вместо мгновенного прыжка."), true));
      this.speed = this.addSetting(new DecimalSetting(("Скорость"), ("Как быстро бокс догоняет новый блок."), (0.35D), (0.05D), 1.0, (0.05D)));
      this.addSetting(new SettingSeparator(("Цвет")));
      this.useCustomColor = this.addSetting(new BooleanSetting(("Свой цвет"), ("Использовать свой цвет вместо ACCENT."), false));
      this.color = this.addSetting(new ColorSetting(("Цвет"), ("Цвет бокса.")));
      this.fillOpacity.setVisibility(() -> this.fill.getValue());
      this.speed.setVisibility(() -> this.animation.getValue());
      this.color.setVisibility(() -> this.useCustomColor.getValue());
   }

   @Override
   protected void onDisable() {
      this.animatedFaceBounds = null;
      this.visibility = 0.0F;
   }

   public static BlockOverlay getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new BlockOverlay();
      }

      return INSTANCE;
   }
}
