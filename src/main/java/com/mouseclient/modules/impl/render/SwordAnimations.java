package com.mouseclient.modules.impl.render;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.modules.impl.combat.AttackAura;
import com.mouseclient.modules.impl.combat.ModeSetting;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.render.pipeline.MouseClientHandOutlineBlur;
import com.mouseclient.util.time.GuiVisuals;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Quaternionf;

@ModuleInfo(name = "SwordAnimations", description = "Позволяет изменять анимацию удара.", category = ModuleCategory.RENDER)
public final class SwordAnimations extends Module {
   private static SwordAnimations INSTANCE;
   public ModeSetting mode;
   public DecimalSetting rightHandScale;
   public DecimalSetting leftHandScale;
   public DecimalSetting tiltStrength;
   public DecimalSetting strength;
   public DecimalSetting swingSpeed;
   public BooleanSetting auraOnly;
   public BooleanSetting applyShader;
   public DecimalSetting outline;
   public DecimalSetting fill;
   public DecimalSetting highlight;
   public BooleanSetting customColor;
   public ColorSetting color;

   public int swingDurationTicks() {
      int baseDuration = (int)this.swingSpeed.getValue();
      if (minecraftClient.player.hasStatusEffect(StatusEffects.HASTE)) {
         return baseDuration - (1 + minecraftClient.player.getStatusEffect(StatusEffects.HASTE).getAmplifier());
      } else {
         return minecraftClient.player.hasStatusEffect(StatusEffects.MINING_FATIGUE)
            ? baseDuration + (1 + minecraftClient.player.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier()) * 2
            : baseDuration;
      }
   }

   @Override
   protected void onDisable() {
      MouseClientHandOutlineBlur.getInstance().close();
   }

   private Quaternionf swingRotation(int armSign, float angleDegrees) {
      return this.swingAxis(armSign).rotationDegrees(angleDegrees);
   }

   public boolean isAuraConditionMet() {
      return !this.auraOnly.getValue() || AttackAura.getInstance().isEnabled();
   }

   public void applySwingTransform(MatrixStack matrices, int armSign, float swingProgress) {
      float tiltAmplitude = this.tiltStrength.getValue() * (10.0F);
      float swingSine = MathHelper.sin(swingProgress * (3.1415927F));
      float scaledTilt = tiltAmplitude / (10.0F) / (10.0F) * (40.0F);
      switch (this.mode.getSelectedOption()) {
         case ("Мод 1"):
            matrices.translate(armSign * (0.1F), 0.0F, (-0.2F));
            matrices.translate(armSign * (0.5F), (-0.4F), (-0.82F));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(armSign * (90.0F)));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(armSign * (-60.0F)));
            matrices.multiply(this.swingAxis(armSign).rotationDegrees(armSign * ((-80.0F) - tiltAmplitude * swingSine)));
            break;
         case ("Мод 2"):
            matrices.translate(armSign * (0.56F), (-0.52F), (-0.72F));
            matrices.multiply(
               RotationAxis.POSITIVE_Y.rotationDegrees(armSign * ((45.0F) + MathHelper.sin(swingProgress * swingProgress * (3.1415927F)) / (4.0F) * (-20.0F)))
            );
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(armSign * MathHelper.sin(MathHelper.sqrt(swingProgress) * (3.1415927F)) * (-20.0F)));
            matrices.multiply(this.swingRotation(armSign, armSign * swingSine * -tiltAmplitude));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(armSign * (-45.0F)));
            break;
         case ("Мод 3"):
            matrices.translate(armSign * (0.1F), 0.0F, (-0.2F));
            matrices.translate(armSign * (0.5F), (-0.4F), (-0.82F));
            matrices.multiply(this.swingRotation(armSign, armSign * (-120.0F)));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(armSign * (-swingSine * tiltAmplitude + (30.0F))));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(armSign * (90.0F)));
            break;
         case ("Мод 4"):
            matrices.translate(armSign * (0.1F), 0.0F, (-0.2F));
            matrices.translate(armSign * (0.5F), (-0.4F), (-0.82F));
            matrices.multiply(this.swingRotation(armSign, armSign * (-120.0F)));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(armSign * -swingSine * tiltAmplitude));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(armSign * (90.0F)));
            break;
         case ("Мод 5"):
            matrices.translate(armSign * (0.3F), 0.0F, (-0.3F));
            matrices.translate(armSign * (0.6F), (-0.6F), (-0.9F));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(armSign * (45.0F)));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(armSign * swingSine * -(scaledTilt / 2.0F)));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(armSign * swingSine * -(scaledTilt / 2.0F)));
            matrices.multiply(this.swingRotation(armSign, armSign * swingSine * -scaledTilt));
            matrices.translate(armSign * (-0.5F), (0.2F), 0.0F);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(armSign * (30.0F)));
            matrices.multiply(this.swingRotation(armSign, armSign * (-80.0F)));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(armSign * (60.0F)));
            break;
         case ("Мод 6"):
            matrices.translate(armSign * (0.3F), 0.0F, (-0.3F));
            matrices.translate(armSign * (0.6F), (-0.6F), (-0.9F));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(armSign * (45.0F)));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(armSign * swingSine * scaledTilt));
            matrices.multiply(this.swingRotation(armSign, armSign * swingSine * -scaledTilt));
            matrices.translate(armSign * (-0.5F), (0.2F), 0.0F);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(armSign * (30.0F)));
            matrices.multiply(this.swingRotation(armSign, armSign * (-80.0F)));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(armSign * (60.0F)));
            break;
         case ("Мод 7"):
            matrices.translate(armSign * (0.56F), (-0.52F), (-0.72F));
            matrices.translate(
               0.0F,
               MathHelper.lerp(swingSine, 0.0F, (-0.8F)) * MathHelper.clamp((this.strength.getValue() - 1.0F) / (14.0F), 0.0F, 1.0F),
               0.0F
            );
            break;
         case ("Мод 8"):
            matrices.translate(armSign * (0.72F), (-0.5F), (-1.0F));
            matrices.multiply(this.swingRotation(armSign, armSign * (20.0F)));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(armSign * (-90.0F)));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(armSign * (60.0F)));
            matrices.multiply(this.swingRotation(armSign, armSign * (30.0F)));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(armSign * tiltAmplitude * (0.3F)));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(armSign * tiltAmplitude * swingSine));
      }
   }

   public float handScale(Arm arm) {
      return !this.isEnabled() ? 1.0F : (arm == Arm.RIGHT ? this.rightHandScale : this.leftHandScale).getValue();
   }

   public boolean shouldOverrideSwingDuration() {
      return this.isEnabled() && !this.isAnimationDisabled();
   }

   public boolean isAnimationDisabled() {
      return this.mode.isSelected(("Нету"));
   }

   private RotationAxis swingAxis(int armSign) {
      return armSign == -1 ? RotationAxis.NEGATIVE_X : RotationAxis.POSITIVE_X;
   }

   public boolean isShaderEnabled() {
      return this.isEnabled() && this.applyShader.getValue();
   }

   public int shaderColor() {
      return this.customColor.getValue() ? this.color.getArgb() : GuiVisuals.accentColor;
   }

   public boolean shouldAnimateArm(Arm arm) {
      return this.isEnabled() && !this.isAnimationDisabled() && this.isAuraConditionMet() && minecraftClient.player != null && arm == minecraftClient.player.getMainArm();
   }

   public static SwordAnimations getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new SwordAnimations();
      }

      return INSTANCE;
   }

   private SwordAnimations() {
      this.mode = this.addSetting(
         new ModeSetting(("Тип"), ("Вариант анимации удара."), ("Мод 1"), ("Нету"), ("Мод 1"), ("Мод 2"), ("Мод 3"), ("Мод 4"), ("Мод 5"), ("Мод 6"), ("Мод 7"), ("Мод 8"))
      );
      this.rightHandScale = this.addSetting(new DecimalSetting(("Размер правой руки"), ("Масштаб предмета в правой руке."), 1.0, (0.5D), (1.5D), (0.05D)));
      this.leftHandScale = this.addSetting(new DecimalSetting(("Размер левой руки"), ("Масштаб предмета в левой руке."), 1.0, (0.5D), (1.5D), (0.05D)));
      this.tiltStrength = this.addSetting(new DecimalSetting(("Сила наклона"), ("Амплитуда наклона предмета при ударе."), (3.0D), 1.0, (10.0D), 1.0));
      this.strength = this.addSetting(new DecimalSetting(("Сила"), ("Амплитуда провала руки вниз."), (15.0D), 1.0, (15.0D), (0.05D)));
      this.swingSpeed = this.addSetting(new DecimalSetting(("Скорость удара"), ("Длительность взмаха руки в тиках."), (6.0D), (3.0D), (10.0D), 1.0));
      this.auraOnly = this.addSetting(new BooleanSetting(("Только с аурой"), ("Анимация работает только при включённой AttackAura."), false));
      this.addSetting(new SettingSeparator(("Применить шейдер")));
      this.applyShader = this.addSetting(new BooleanSetting(("Применить шейдер"), ("Обводка и тень вокруг предмета в руке."), false));
      this.outline = this.addSetting(new DecimalSetting(("Оутлайн"), ("Сила обводки."), (0.8D), 0.0, 1.0, (0.05D)));
      this.fill = this.addSetting(new DecimalSetting(("Заливка"), ("Заливка предмета цветом."), 0.0, 0.0, 1.0, (0.05D)));
      this.highlight = this.addSetting(new DecimalSetting(("Подсветка"), ("Сила свечения вокруг предмета."), (0.55D), 0.0, (3.0D), (0.05D)));
      this.customColor = this.addSetting(new BooleanSetting(("Кастомный цвет"), ("Свой цвет вместо акцента темы."), false));
      this.color = this.addSetting(new ColorSetting(("Цвет"), ("Цвет обводки и тени.")));
      this.tiltStrength.setVisibility(() -> !this.mode.isSelected(("Мод 7")) && !this.isAnimationDisabled());
      this.swingSpeed.setVisibility(() -> !this.isAnimationDisabled());
      this.strength.setVisibility(() -> this.mode.isSelected(("Мод 7")));
      this.outline.setVisibility(() -> this.applyShader.getValue());
      this.fill.setVisibility(() -> this.applyShader.getValue());
      this.highlight.setVisibility(() -> this.applyShader.getValue());
      this.customColor.setVisibility(() -> this.applyShader.getValue());
      this.color.setVisibility(() -> this.applyShader.getValue() && this.customColor.getValue());
   }
}
