package com.mouseclient.modules.impl.render;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.modules.impl.combat.ModeSetting;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.network.PlayerTickEvent;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

@ModuleInfo(name = "JumpCircle", description = "Светящийся круг на земле в месте прыжка.", category = ModuleCategory.RENDER)
public final class JumpCircle extends Module {
   private static JumpCircle INSTANCE;
   public DecimalSetting radius;
   public DecimalSetting glow;
   public DecimalSetting distortion;
   public ModeSetting effect;
   public BooleanSetting radiusWave;
   public DecimalSetting lifetime;
   public BooleanSetting useCustomColor;
   public ColorSetting color;
   private final CopyOnWriteArrayList<JumpCircle.JumpCircleEntry> rings = new CopyOnWriteArrayList<>();
   private boolean wasOnGround;
   private final EventListener<PlayerTickEvent> playerTickListener = event -> {
      if (minecraftClient.player != null) {
         if (this.wasOnGround && !minecraftClient.player.isOnGround() && minecraftClient.player.getVelocity().y > (0.3D)) {
            if (this.rings.size() >= 16) {
               this.rings.remove(0);
            }

            float[] wallDistances = new float[32];
            Vec3d position = new Vec3d(minecraftClient.player.getX(), minecraftClient.player.lastRenderY + (0.6D), minecraftClient.player.getZ());
            double rayLength = this.radius.getValue() + this.glow.getValue() * 2.0F + 1.0F;

            for (int sampleIndex = 0; sampleIndex < 32; sampleIndex++) {
               BlockHitResult hitResult = minecraftClient.world
                  .raycast(
                     new RaycastContext(
                        position,
                        new Vec3d(
                           position.x + Math.cos(sampleIndex * (3.141592653589793D) / (16.0D)) * rayLength,
                           position.y,
                           position.z + Math.sin(sampleIndex * (3.141592653589793D) / (16.0D)) * rayLength
                        ),
                        ShapeType.COLLIDER,
                        FluidHandling.NONE,
                        minecraftClient.player
                     )
                  );
               wallDistances[sampleIndex] = hitResult.getType() == Type.MISS ? (999.0F) : (float)hitResult.getPos().distanceTo(position);
            }

            this.rings
               .add(
                  new JumpCircle.JumpCircleEntry(
                     minecraftClient.player.getX(), minecraftClient.player.lastRenderY, minecraftClient.player.getZ(), System.currentTimeMillis(), wallDistances
                  )
               );
         }

         this.wasOnGround = minecraftClient.player.isOnGround();
         this.rings.removeIf(ring -> (float)(System.currentTimeMillis() - ring.bornMs()) > this.lifetime.getValue());
      }
   };

   private JumpCircle() {
      this.addSetting(new SettingSeparator(("Кольцо")));
      this.radius = this.addSetting(new DecimalSetting(("Радиус"), ("Радиус круга в блоках."), (1.5D), (0.5D), (4.0D), (0.1D)));
      this.glow = this.addSetting(new DecimalSetting(("Свечение"), ("Мягкость дистанс-поля кольца."), (0.35D), (0.05D), (1.5D), (0.05D)));
      this.radiusWave = this.addSetting(new BooleanSetting(("Волна радиуса"), ("Радиус расходится волной от точки прыжка, а не статичен."), true));
      this.lifetime = this.addSetting(new DecimalSetting(("Время жизни"), ("Сколько миллисекунд живёт круг."), (600.0D), (300.0D), (3000.0D), (50.0D)));
      this.addSetting(new SettingSeparator(("Искажение")));
      this.distortion = this.addSetting(new DecimalSetting(("Искажение"), ("Сила преломления картинки."), (0.35D), 0.0, 1.0, (0.05D)));
      this.effect = this.addSetting(new ModeSetting(("Эффект"), ("Как искажать картинку: волна вокруг кольца или вода с тенью."), ("Волна"), ("Волна"), ("Вода")));
      this.addSetting(new SettingSeparator(("Цвет")));
      this.useCustomColor = this.addSetting(new BooleanSetting(("Свой цвет"), ("Использовать свой цвет вместо ACCENT."), false));
      this.color = this.addSetting(new ColorSetting(("Цвет"), ("Цвет круга.")));
      this.color.setVisibility(() -> this.useCustomColor.getValue());
   }

   public CopyOnWriteArrayList<JumpCircle.JumpCircleEntry> getRings() {
      return this.rings;
   }

   @Override
   protected void onDisable() {
      this.rings.clear();
   }

   public static JumpCircle getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new JumpCircle();
      }

      return INSTANCE;
   }

   public float lifeProgress(JumpCircle.JumpCircleEntry ring) {
      return Math.min(1.0F, (float)(System.currentTimeMillis() - ring.bornMs()) / this.lifetime.getValue());
   }

   public record JumpCircleEntry(double x, double y, double z, long bornMs, float[] wallDistBlocks) {
   }
}
