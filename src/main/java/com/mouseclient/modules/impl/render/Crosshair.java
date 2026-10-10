package com.mouseclient.modules.impl.render;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.gui.Render2D;
import com.mouseclient.internal.ArgbColor;
import com.mouseclient.internal.GuiScale;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import net.minecraft.util.hit.HitResult.Type;

@ModuleInfo(
   name = "Crosshair",
   description = "Свой прицел вместо ванильного: крест с плавным кулдауном удара и краснением по цели.",
   category = ModuleCategory.RENDER
)
public final class Crosshair extends Module {
   private static Crosshair INSTANCE;
   public ColorSetting color;
   public DecimalSetting gap;
   public DecimalSetting length;
   public DecimalSetting thickness;
   public BooleanSetting cooldown;
   public DecimalSetting cooldownStrength;
   public BooleanSetting outline;
   public BooleanSetting tShape;
   public BooleanSetting centerDot;
   public DecimalSetting dotSize;
   private float cooldownGapProgress;
   private float targetColorProgress;
   private final EventListener<HudRenderEvent> hudRenderListener = event -> {
      if (minecraftClient.player != null
         && minecraftClient.world != null
         && minecraftClient.options.getPerspective().isFirstPerson()
         && !CameraFeature.getInstance().isFreeLookHeld()) {
         float centerX = event.getWidth() / 2.0F * GuiScale.getWindowScale() / GuiScale.getBaseScale();
         float centerY = event.getHeight() / 2.0F * GuiScale.getWindowScale() / GuiScale.getBaseScale();
         this.cooldownGapProgress = this.cooldownGapProgress
            + ((this.cooldown.getValue() ? 1.0F - minecraftClient.player.getAttackCooldownProgress(event.getTickProgress()) : 0.0F) - this.cooldownGapProgress)
               * (0.3F);
         this.targetColorProgress = this.targetColorProgress
            + ((minecraftClient.crosshairTarget != null && minecraftClient.crosshairTarget.getType() == Type.ENTITY ? 1.0F : 0.0F) - this.targetColorProgress)
               * (0.3F);
         int crosshairColor = ArgbColor.lerp(this.color.getArgb(), (-51401), this.targetColorProgress);
         float animatedGap = this.gap.getValue() + this.cooldownStrength.getValue() * this.cooldownGapProgress;
         if (!this.tShape.getValue()) {
            this.drawBar(
               centerX - this.thickness.getValue() / 2.0F,
               centerY - animatedGap - this.length.getValue(),
               this.thickness.getValue(),
               this.length.getValue(),
               crosshairColor
            );
         }

         this.drawBar(
            centerX - this.thickness.getValue() / 2.0F, centerY + animatedGap, this.thickness.getValue(), this.length.getValue(), crosshairColor
         );
         this.drawBar(
            centerX - animatedGap - this.length.getValue(),
            centerY - this.thickness.getValue() / 2.0F,
            this.length.getValue(),
            this.thickness.getValue(),
            crosshairColor
         );
         this.drawBar(
            centerX + animatedGap, centerY - this.thickness.getValue() / 2.0F, this.length.getValue(), this.thickness.getValue(), crosshairColor
         );
         if (this.centerDot.getValue()) {
            this.drawBar(
               centerX - this.dotSize.getValue() / 2.0F,
               centerY - this.dotSize.getValue() / 2.0F,
               this.dotSize.getValue(),
               this.dotSize.getValue(),
               crosshairColor
            );
         }
      }
   };

   private Crosshair() {
      this.color = this.addSetting(new ColorSetting(("Цвет"), ("Цвет прицела, при наведении на энтити краснеет.")));
      this.gap = this.addSetting(new DecimalSetting(("Отступ"), ("Просвет от центра до линий."), (2.0D), 0.0, (20.0D), 1.0));
      this.length = this.addSetting(new DecimalSetting(("Длина"), ("Длина линий креста."), (5.0D), 1.0, (20.0D), 1.0));
      this.thickness = this.addSetting(new DecimalSetting(("Толщина"), ("Толщина линий."), (2.0D), 1.0, (5.0D), 1.0));
      this.cooldown = this.addSetting(new BooleanSetting(("Кулдаун"), ("Показывать перезарядку удара: крест плавно расходится и сходится."), true));
      this.cooldownStrength = this.addSetting(new DecimalSetting(("Сила кулдауна"), ("Насколько крест расходится во время перезарядки."), (8.0D), 1.0, (20.0D), 1.0));
      this.cooldownStrength.setVisibility(() -> this.cooldown.getValue());
      this.outline = this.addSetting(new BooleanSetting(("Обводка"), ("Тёмная обводка вокруг линий."), true));
      this.tShape = this.addSetting(new BooleanSetting(("T-режим"), ("Не рисовать верхнюю линию."), false));
      this.centerDot = this.addSetting(new BooleanSetting(("Точка в центре"), ("Точка в центре прицела."), false));
      this.dotSize = this.addSetting(new DecimalSetting(("Размер точки"), ("Размер центральной точки."), (2.0D), 1.0, (10.0D), (0.5D)));
      this.dotSize.setVisibility(() -> this.centerDot.getValue());
   }

   public static Crosshair getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new Crosshair();
      }

      return INSTANCE;
   }

   private void drawBar(float x, float y, float width, float height, int color) {
      if (this.outline.getValue()) {
         Render2D.fillRectangle(x - (0.5F), y - (0.5F), width + 1.0F, height + 1.0F, (-1275068416));
      }

      Render2D.fillRectangle(x, y, width, height, color);
   }
}
