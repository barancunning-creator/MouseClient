package com.mouseclient.modules.impl.render;

import com.mouseclient.annotation.EventPriority;
import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.internal.GuiScale;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.network.PlayerTickEvent;
import com.mouseclient.render.pipeline.SliderSetting;
import com.mouseclient.util.time.GuiTextRenderer;
import com.mouseclient.util.time.GuiFont;
import java.util.Locale;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

@ModuleInfo(name = "FreeCamera", description = "Отвязанная камера для полёта и обзора сквозь блоки.", category = ModuleCategory.RENDER)
public final class FreeCamera extends Module {
   private static FreeCamera INSTANCE;
   public SliderSetting speed;
   private Vec3d cameraPosition;
   private Vec3d previousCameraPosition;
   private float yaw;
   private float pitch;
   private long disabledAtMillis;
   private Vec3d returnStartPosition;
   private float returnStartYaw;
   private float returnStartPitch;
   private ClientPlayerEntity trackedPlayer;
   private final EventListener<PlayerTickEvent> playerTickListener = event -> {
      if (minecraftClient.player != null) {
         if (this.cameraPosition == null || this.trackedPlayer != minecraftClient.player) {
            this.trackedPlayer = minecraftClient.player;
            this.cameraPosition = minecraftClient.player.getEyePos();
            this.previousCameraPosition = this.cameraPosition;
            this.yaw = minecraftClient.player.getYaw();
            this.pitch = minecraftClient.player.getPitch();
         }

         this.previousCameraPosition = this.cameraPosition;
         Vec3d movement = Vec3d.ZERO;
         if (minecraftClient.options.forwardKey.isPressed()) {
            movement = movement.add(Vec3d.fromPolar(0.0F, this.yaw));
         }

         if (minecraftClient.options.backKey.isPressed()) {
            movement = movement.subtract(Vec3d.fromPolar(0.0F, this.yaw));
         }

         if (minecraftClient.options.rightKey.isPressed()) {
            movement = movement.add(Vec3d.fromPolar(0.0F, this.yaw + (90.0F)));
         }

         if (minecraftClient.options.leftKey.isPressed()) {
            movement = movement.subtract(Vec3d.fromPolar(0.0F, this.yaw + (90.0F)));
         }

         if (minecraftClient.options.jumpKey.isPressed()) {
            movement = movement.add(0.0, 1.0, 0.0);
         }

         if (minecraftClient.options.sneakKey.isPressed()) {
            movement = movement.subtract(0.0, 1.0, 0.0);
         }

         if (movement.lengthSquared() > 0.0) {
            this.cameraPosition = this.cameraPosition.add(movement.normalize().multiply(this.speed.floatValue() * (minecraftClient.options.sprintKey.isPressed() ? 2.0F : 1.0F)));
         }
      }
   };
   @EventPriority(100)
   private final EventListener<HudRenderEvent> hudRenderListener = event -> {
      if (minecraftClient.player != null && this.cameraPosition != null) {
         GuiTextRenderer.draw(
            GuiFont.SEMI,
            String.format(Locale.US, ("%.1f, %.1f, %.1f"), this.cameraPosition.x, this.cameraPosition.y, this.cameraPosition.z),
            event.getWidth() * GuiScale.getWindowScale() / GuiScale.getBaseScale() / 2.0F
               - GuiTextRenderer.getWidth(GuiFont.SEMI, String.format(Locale.US, ("%.1f, %.1f, %.1f"), this.cameraPosition.x, this.cameraPosition.y, this.cameraPosition.z), (5.5F)) / 2.0F,
            event.getHeight() * GuiScale.getWindowScale() / GuiScale.getBaseScale() / 2.0F + (8.0F),
            (5.5F),
            (-419430401)
         );
      }
   };

   @Override
   protected void onDisable() {
      if (this.cameraPosition != null) {
         this.disabledAtMillis = System.currentTimeMillis();
         this.returnStartPosition = this.cameraPosition;
         this.returnStartYaw = this.yaw;
         this.returnStartPitch = this.pitch;
      }
   }

   public Vec3d cameraPosition(float tickDelta) {
      return this.isEnabled() ? this.previousCameraPosition.lerp(this.cameraPosition, tickDelta) : this.returnStartPosition.lerp(minecraftClient.player.getCameraPosVec(tickDelta), this.returnProgress());
   }

   public void rotateCamera(double yawDelta, double pitchDelta) {
      this.yaw = this.yaw + (float)yawDelta * (0.15F);
      this.pitch = Math.max((-90.0F), Math.min((90.0F), this.pitch + (float)pitchDelta * (0.15F)));
   }

   @Override
   protected void onEnable() {
      this.disabledAtMillis = 0L;
      this.trackedPlayer = minecraftClient.player;
      this.cameraPosition = minecraftClient.player == null ? null : minecraftClient.gameRenderer.getCamera().getCameraPos();
      this.previousCameraPosition = this.cameraPosition;
      if (minecraftClient.player != null) {
         this.yaw = minecraftClient.player.getYaw();
         this.pitch = minecraftClient.player.getPitch();
      }
   }

   public boolean shouldRenderDetached() {
      return this.isCameraActive() && this.cameraPosition(1.0F).squaredDistanceTo(minecraftClient.player.getEyePos()) > (0.5625D);
   }

   public static FreeCamera getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new FreeCamera();
      }

      return INSTANCE;
   }

   private FreeCamera() {
      this.speed = this.addSetting(new SliderSetting(("Скорость"), ("Блоков за тик полёта камеры.")).withRange((0.1F), 2.0F).withStep((0.1F)).setValue((0.5F)));
   }

   public float cameraPitch() {
      return this.isEnabled() ? this.pitch : this.returnStartPitch + (minecraftClient.player.getPitch() - this.returnStartPitch) * this.returnProgress();
   }

   public boolean isCameraActive() {
      return minecraftClient.player != null && this.cameraPosition != null && (this.isEnabled() || System.currentTimeMillis() - this.disabledAtMillis < (350L));
   }

   private float returnProgress() {
      float elapsedProgress = Math.min(1.0F, (float)(System.currentTimeMillis() - this.disabledAtMillis) / (350.0F));
      return elapsedProgress * elapsedProgress * ((3.0F) - 2.0F * elapsedProgress);
   }

   public float cameraYaw() {
      return this.isEnabled()
         ? this.yaw
         : this.returnStartYaw + MathHelper.wrapDegrees(minecraftClient.player.getYaw() - this.returnStartYaw) * this.returnProgress();
   }
}
