package com.mouseclient.internal;

import com.mouseclient.util.entity.MinecraftAccess;
import com.mouseclient.util.time.Stopwatch;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.RotationAxis;

public final class LuaClientTypes {
   private LuaClientTypes() {
   }

   public static final class MatrixView {
      private final MatrixStack matrixStack;

      public void translate(double x, double y, double z) {
         this.matrixStack.translate(x, y, z);
      }

      public void rotate(float xDegrees, float yDegrees, float zDegrees) {
         this.rotateX(xDegrees);
         this.rotateY(yDegrees);
         this.rotateZ(zDegrees);
      }

      public MatrixStack getMatrices() {
         return this.matrixStack;
      }

      public MatrixView(MatrixStack matrixStack) {
         this.matrixStack = matrixStack;
      }

      public void rotateY(float degrees) {
         this.matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(degrees));
      }

      public void scale(float x, float y, float z) {
         this.matrixStack.scale(x, y, z);
      }

      public void rotateZ(float degrees) {
         this.matrixStack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(degrees));
      }

      public void rotateX(float degrees) {
         this.matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(degrees));
      }
   }

   public static final class StatusEffectView {
      private final StatusEffectInstance effect;
      private final String primaryLabel;
      private final String secondaryLabel;

      public StatusEffectView(StatusEffectInstance effect, String primaryLabel, String secondaryLabel) {
         this.effect = effect;
         this.secondaryLabel = secondaryLabel;
         this.primaryLabel = primaryLabel;
      }

      public StatusEffectInstance getEffect() {
         return this.effect;
      }

      public String getSecondaryLabel() {
         return this.secondaryLabel;
      }

      public String getPrimaryLabel() {
         return this.primaryLabel;
      }
   }

   public static final class Vector3 {
      public double x;
      public double y;
      public double z;

      public void set(double x, double y, double z) {
         this.x = x;
         this.y = y;
         this.z = z;
      }

      public Vector3(double x, double y, double z) {
         this.x = x;
         this.y = y;
         this.z = z;
      }
   }

   public static final class Timer {
      private final Stopwatch stopwatch = new Stopwatch();

      public boolean hasElapsed(long durationMillis) {
         return this.stopwatch.hasElapsed(durationMillis);
      }

      public void reset() {
         this.stopwatch.reset();
      }

      public long elapsedMillis() {
         return this.stopwatch.getElapsedMillis();
      }
   }

   public static final class ColorValue {
      public static final LuaClientTypes.ColorValue WHITE = new LuaClientTypes.ColorValue(-1);
      public static final LuaClientTypes.ColorValue LIGHT_GRAY = new LuaClientTypes.ColorValue((-4144960));
      public static final LuaClientTypes.ColorValue GRAY = new LuaClientTypes.ColorValue((-8355712));
      public static final LuaClientTypes.ColorValue DARK_GRAY = new LuaClientTypes.ColorValue((-12566464));
      public static final LuaClientTypes.ColorValue BLACK = new LuaClientTypes.ColorValue((-16777216));
      public static final LuaClientTypes.ColorValue RED = new LuaClientTypes.ColorValue((-65536));
      public static final LuaClientTypes.ColorValue PINK = new LuaClientTypes.ColorValue(-20561);
      public static final LuaClientTypes.ColorValue ORANGE = new LuaClientTypes.ColorValue(-14336);
      public static final LuaClientTypes.ColorValue YELLOW = new LuaClientTypes.ColorValue(-256);
      public static final LuaClientTypes.ColorValue GREEN = new LuaClientTypes.ColorValue((-16711936));
      public static final LuaClientTypes.ColorValue MAGENTA = new LuaClientTypes.ColorValue((-65281));
      public static final LuaClientTypes.ColorValue CYAN = new LuaClientTypes.ColorValue((-16711681));
      public static final LuaClientTypes.ColorValue BLUE = new LuaClientTypes.ColorValue((-16776961));
      private int red;
      private int green;
      private int blue;
      private int alpha;

      public ColorValue(double red, double green, double blue, double alpha) {
         this.red = (int)(red * (255.0D));
         this.green = (int)(green * (255.0D));
         this.blue = (int)(blue * (255.0D));
         this.alpha = (int)(alpha * (255.0D));
      }

      public float blue() {
         return this.blue;
      }

      public float green() {
         return this.green;
      }

      public int argb() {
         return (this.alpha & 0xFF) << 24 | (this.red & 0xFF) << 16 | (this.green & 0xFF) << 8 | this.blue & 0xFF;
      }

      public ColorValue(double red, double green, double blue) {
         this(red, green, blue, 1.0);
      }

      public float red() {
         return this.red;
      }

      public ColorValue(int argb) {
         this.alpha = argb >> 24 & 0xFF;
         this.red = argb >> 16 & 0xFF;
         this.green = argb >> 8 & 0xFF;
         this.blue = argb & 0xFF;
      }

      public float alpha() {
         return this.alpha;
      }
   }

   public static final class LivingEntityView extends LuaClientTypes.EntityView {
      private final LivingEntity livingEntity;

      public LivingEntity getLivingEntity() {
         return this.livingEntity;
      }

      public boolean isBlocking() {
         return this.livingEntity.isBlocking();
      }

      public int hurtTime() {
         return this.livingEntity.hurtTime;
      }

      public float health() {
         return this.livingEntity.getHealth();
      }

      public float maxHealth() {
         return this.livingEntity.getMaxHealth();
      }

      public float absorption() {
         return this.livingEntity.getAbsorptionAmount();
      }

      public float bodyYaw() {
         return this.livingEntity.bodyYaw;
      }

      public float armor() {
         return this.livingEntity.getArmor();
      }

      public String offhandItemKey() {
         return this.livingEntity.getOffHandStack().getItem().getTranslationKey();
      }

      public LivingEntityView(LivingEntity livingEntity) {
         super(livingEntity);
         this.livingEntity = livingEntity;
      }

      public String mainHandItemKey() {
         return this.livingEntity.getMainHandStack().getItem().getTranslationKey();
      }

      public int hurtTimeTicks() {
         return this.livingEntity.hurtTime;
      }

      public boolean isUsingItem() {
         return this.livingEntity.isUsingItem();
      }
   }

   public static class EntityView implements MinecraftAccess {
      private final Entity entity;

      public boolean isTouchingWater() {
         return this.entity.isTouchingWater();
      }

      public boolean isGlowing() {
         return this.entity.isGlowing();
      }

      public LuaClientTypes.Vector3 position() {
         return new LuaClientTypes.Vector3(this.entity.getX(), this.entity.getY(), this.entity.getZ());
      }

      public float fallDistance() {
         return (float)this.entity.fallDistance;
      }

      public EntityView(Entity entity) {
         this.entity = entity;
      }

      public double distanceTo(LuaClientTypes.EntityView other) {
         return other == null ? (-1.0D) : this.entity.distanceTo(other.getEntity());
      }

      public boolean isSneaking() {
         return this.entity.isSneaking();
      }

      public LuaClientTypes.Vector3 velocity() {
         return new LuaClientTypes.Vector3(this.entity.getVelocity().x, this.entity.getVelocity().y, this.entity.getVelocity().z);
      }

      public boolean isPlayer() {
         return this.entity instanceof PlayerEntity;
      }

      public boolean isSprinting() {
         return this.entity.isSprinting();
      }

      public Entity getEntity() {
         return this.entity;
      }

      public boolean isAlive() {
         return this.entity.isAlive();
      }

      public int id() {
         return this.entity.getId();
      }

      public boolean isInLava() {
         return this.entity.isInLava();
      }

      public String name() {
         return this.entity.getName().getString();
      }

      public float pitch() {
         return this.entity.getPitch();
      }

      public float yaw() {
         return this.entity.getYaw();
      }

      public boolean isOnGround() {
         return this.entity.isOnGround();
      }

      public boolean isInvisible() {
         return this.entity.isInvisible();
      }

      public String typeKey() {
         return this.entity.getType().getTranslationKey();
      }
   }

   public static final class AnimatedValue {
      private float startValue;
      private float targetValue;
      private long startTime;
      private long durationMillis = 1L;

      public void update(float targetValue, int durationMillis) {
         if (this.targetValue != targetValue) {
            this.startValue = this.value();
            this.targetValue = targetValue;
            this.startTime = System.currentTimeMillis();
            this.durationMillis = Math.max(1, durationMillis);
         }
      }

      public boolean isFinished() {
         return System.currentTimeMillis() - this.startTime >= this.durationMillis;
      }

      public boolean hasFinished() {
         return this.isFinished();
      }

      public float value() {
         float var1 = Math.min(1.0F, (float)(System.currentTimeMillis() - this.startTime) / (float)this.durationMillis);
         return this.startValue + (this.targetValue - this.startValue) * (1.0F - (1.0F - var1) * (1.0F - var1));
      }

      public float getValue() {
         return this.value();
      }
   }
}
