package com.mouseclient.util.entity;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.gui.HudPanelRenderer;
import com.mouseclient.gui.GuiShapeRenderer;
import com.mouseclient.gui.CornerRadii;
import com.mouseclient.gui.HudRenderContext;
import com.mouseclient.internal.ArgbColor;
import com.mouseclient.internal.GuiScale;
import com.mouseclient.modules.impl.combat.AttackAura;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.modules.impl.render.BooleanSetting;
import com.mouseclient.modules.impl.render.ColorSetting;
import com.mouseclient.modules.impl.render.HudRenderEvent;
import com.mouseclient.network.ClientInitializer;
import com.mouseclient.network.HudStyle;
import com.mouseclient.render.DepthSnapshot;
import com.mouseclient.render.GuiItemRenderer;
import com.mouseclient.render.pipeline.WorldRenderEvent;
import com.mouseclient.util.time.GuiTextRenderer;
import com.mouseclient.util.time.GuiFont;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ChargedProjectilesComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.EggItem;
import net.minecraft.item.EnderPearlItem;
import net.minecraft.item.ExperienceBottleItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.LingeringPotionItem;
import net.minecraft.item.SnowballItem;
import net.minecraft.item.SplashPotionItem;
import net.minecraft.item.TridentItem;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@ModuleInfo(
   name = "ProjectilePrediction",
   description = "Показывает, куда прилетит снаряд из руки: траектория, маркер приземления и радиус действия, а также траектории летящих снарядов.",
   category = ModuleCategory.RENDER
)
public final class ProjectilePrediction extends Module {
   private static ProjectilePrediction INSTANCE;
   public BooleanSetting range;
   public ColorSetting color;
   private static final RenderPipeline RIBBON_PIPELINE = RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
      .withLocation(ClientInitializer.id(("pipeline/projectile_prediction_ribbon")))
      .withVertexShader(Identifier.ofVanilla(("core/position_color")))
      .withFragmentShader(Identifier.ofVanilla(("core/position_color")))
      .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.QUADS)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withDepthWrite(false)
      .withCull(false)
      .build();
   private static final RenderLayer RIBBON_LAYER = RenderLayer.of(
      ("projectile_prediction_ribbon"), RenderSetup.builder(RIBBON_PIPELINE).expectedBufferSize((32768)).build()
   );
   private final float[] heldHitAlphas = new float[8];
   private final ArrayList<ProjectilePrediction.FlyingProjectilePrediction> flyingPredictions = new ArrayList<>();
   private final Int2ObjectOpenHashMap<ProjectilePrediction.SmoothedTrajectory> trajectories = new Int2ObjectOpenHashMap();
   private final Immediate ribbonConsumers;
   private ClientWorld lastWorld;
   private int lastPredictionAge;
   private long lastFrameNanos;
   private Vec3d lastPlayerPosition;
   private final EventListener<WorldRenderEvent> worldRenderListener;
   private String lastCombatTargetName;
   private final EventListener<HudRenderEvent> hudRenderListener;
   private final ArrayList<Vec3d> simulationPath;

   public static ProjectilePrediction getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new ProjectilePrediction();
      }

      return INSTANCE;
   }

   private ProjectilePrediction.ProjectileImpact simulateFlight(
      Vec3d startPosition,
      Vec3d velocity,
      double gravity,
      double airDrag,
      double waterDrag,
      boolean gravityBeforeMovement,
      Entity raycastEntity,
      Entity ignoredEntity,
      ArrayList<ProjectilePrediction.CollisionCandidate> collisionCandidates
   ) {
      this.simulationPath.clear();
      this.simulationPath.add(startPosition);
      Vec3d currentPosition = startPosition;
      Vec3d currentVelocity = velocity;

      for (int simulationTick = 0; simulationTick < 300; simulationTick++) {
         Vec3d previousPosition = currentPosition;
         double drag = minecraftClient.world.getBlockState(BlockPos.ofFloored(previousPosition)).getFluidState().isIn(FluidTags.WATER) ? waterDrag : airDrag;
         if (gravityBeforeMovement) {
            currentVelocity = currentVelocity.subtract(0.0, gravity, 0.0).multiply(drag);
         }

         currentPosition = currentPosition.add(currentVelocity);
         if (!gravityBeforeMovement) {
            currentVelocity = currentVelocity.multiply(drag).subtract(0.0, gravity, 0.0);
         }

         BlockHitResult hitResult = minecraftClient.world.raycast(new RaycastContext(previousPosition, currentPosition, ShapeType.COLLIDER, FluidHandling.NONE, raycastEntity));
         Vec3d segmentEnd = hitResult.getType() != Type.MISS ? hitResult.getPos() : currentPosition;
         this.simulationPath.add(segmentEnd);

         for (ProjectilePrediction.CollisionCandidate candidate : collisionCandidates) {
            if (candidate.target() != ignoredEntity && candidate.box().intersects(previousPosition, segmentEnd)) {
               BlockHitResult entityHit = Box.raycast(List.of(candidate.box()), previousPosition, segmentEnd, BlockPos.ORIGIN);
               ProjectilePrediction.ProjectileImpact impact = entityHit != null
                  ? new ProjectilePrediction.ProjectileImpact(entityHit.getPos(), candidate.target())
                  : new ProjectilePrediction.ProjectileImpact(candidate.box().raycast(previousPosition, segmentEnd).orElse(segmentEnd), candidate.target());
               this.simulationPath.set(this.simulationPath.size() - 1, impact.pos());
               return impact;
            }
         }

         if (hitResult.getType() != Type.MISS) {
            return new ProjectilePrediction.ProjectileImpact(hitResult.getPos(), null);
         }

         if (currentPosition.y < (-128.0D)) {
            break;
         }
      }

      return null;
   }

   private boolean isSpecialServerItem(ItemStack stack) {
      return stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt().contains(("don-item"));
   }

   private void updateTrajectory(int trajectoryId, ArrayList<Vec3d> path, ProjectilePrediction.ProjectileImpact impact, ItemStack icon, float deltaSeconds) {
      ProjectilePrediction.SmoothedTrajectory trajectory = (ProjectilePrediction.SmoothedTrajectory)this.trajectories.get(trajectoryId);
      if (trajectory == null) {
         trajectory = new ProjectilePrediction.SmoothedTrajectory();
         this.trajectories.put(trajectoryId, trajectory);
      }

      trajectory.update(path, impact.pos(), impact.hitTarget() != null, icon, deltaSeconds);
      trajectory.updatedThisFrame = true;
   }

   private void predictHeldProjectile(
      ItemStack stack, Hand hand, float tickDelta, ArrayList<ProjectilePrediction.CollisionCandidate> collisionCandidates, ArrayList<ProjectilePrediction.HeldProjectilePrediction> predictions
   ) {
      Item item = stack.getItem();
      Vec3d position = new Vec3d(minecraftClient.player.getX(), minecraftClient.player.getEyeY() - (0.1D), minecraftClient.player.getZ());
      if (item instanceof BowItem && minecraftClient.player.isUsingItem() && minecraftClient.player.getActiveItem() == stack) {
         float bowPull = BowItem.getPullProgress(minecraftClient.player.getItemUseTime());
         if (bowPull >= (0.1F)) {
            this.addHeldPrediction(
               predictions,
               this.simulateFlight(
                  position,
                  this.initialVelocity(0.0F, bowPull * (3.0F)),
                  (0.05D),
                  (0.9900000095367432D),
                  (0.6000000238418579D),
                  false,
                  minecraftClient.player,
                  minecraftClient.player,
                  collisionCandidates
               ),
               0.0F,
               new ItemStack(Items.ARROW),
               hand,
               tickDelta
            );
         }
      } else if (item instanceof CrossbowItem && CrossbowItem.isCharged(stack)) {
         ChargedProjectilesComponent chargedProjectiles = stack.getOrDefault(DataComponentTypes.CHARGED_PROJECTILES, ChargedProjectilesComponent.DEFAULT);
         java.util.List<net.minecraft.item.ItemStack> projectileStacks = chargedProjectiles.getProjectiles();
         float projectileSpeed = chargedProjectiles.contains(Items.FIREWORK_ROCKET) ? (1.6F) : (3.15F);
         float spreadStep = projectileStacks.size() <= 1 ? 0.0F : (20.0F) / (projectileStacks.size() - 1);
         Vec3d spreadAxis = minecraftClient.player.getOppositeRotationVector(1.0F);

         for (int projectileIndex = 0; projectileIndex < projectileStacks.size(); projectileIndex++) {
            float spreadAngle = (projectileStacks.size() - 1) % 2 * spreadStep / 2.0F + (projectileIndex % 2 == 0 ? 1 : -1) * ((projectileIndex + 1) / 2) * spreadStep;
            Vector3f rotatedDirection = minecraftClient.player
               .getRotationVec(1.0F)
               .toVector3f()
               .rotate(new Quaternionf().setAngleAxis(spreadAngle * (0.017453292F), spreadAxis.x, spreadAxis.y, spreadAxis.z));
            Vec3d shotVelocity = new Vec3d(rotatedDirection.x(), rotatedDirection.y(), rotatedDirection.z()).normalize().multiply(projectileSpeed);
            if (((ItemStack)projectileStacks.get(projectileIndex)).isOf(Items.FIREWORK_ROCKET)) {
               this.addHeldPrediction(
                  predictions,
                  this.simulateFlight(
                     new Vec3d(minecraftClient.player.getX(), minecraftClient.player.getEyeY() - (0.15D), minecraftClient.player.getZ()),
                     shotVelocity,
                     0.0,
                     1.0,
                     1.0,
                     false,
                     minecraftClient.player,
                     minecraftClient.player,
                     collisionCandidates
                  ),
                  0.0F,
                  (ItemStack)projectileStacks.get(projectileIndex),
                  hand,
                  tickDelta
               );
            } else {
               this.addHeldPrediction(
                  predictions,
                  this.simulateFlight(position, shotVelocity, (0.05D), (0.9900000095367432D), (0.6000000238418579D), false, minecraftClient.player, minecraftClient.player, collisionCandidates),
                  0.0F,
                  (ItemStack)projectileStacks.get(projectileIndex),
                  hand,
                  tickDelta
               );
            }
         }
      } else if (!(item instanceof SnowballItem) && !(item instanceof EggItem) && !(item instanceof EnderPearlItem)) {
         if (item instanceof TridentItem
            && minecraftClient.player.isUsingItem()
            && minecraftClient.player.getActiveItem() == stack
            && minecraftClient.player.getItemUseTime() >= 10) {
            this.addHeldPrediction(
               predictions,
               this.simulateFlight(
                  position,
                  this.initialVelocity(0.0F, (2.5F)),
                  (0.05D),
                  (0.9900000095367432D),
                  (0.9900000095367432D),
                  false,
                  minecraftClient.player,
                  minecraftClient.player,
                  collisionCandidates
               ),
               0.0F,
               stack,
               hand,
               tickDelta
            );
         } else if (item instanceof SplashPotionItem || item instanceof LingeringPotionItem) {
            this.addHeldPrediction(
               predictions,
               this.simulateFlight(
                  position,
                  this.initialVelocity((-20.0F), (0.5F)),
                  (0.05D),
                  (0.9900000095367432D),
                  (0.800000011920929D),
                  true,
                  minecraftClient.player,
                  minecraftClient.player,
                  collisionCandidates
               ),
               item instanceof LingeringPotionItem ? (4.0F) : (3.0F),
               ItemStack.EMPTY,
               hand,
               tickDelta
            );
         } else if (item instanceof ExperienceBottleItem) {
            this.addHeldPrediction(
               predictions,
               this.simulateFlight(
                  position,
                  this.initialVelocity((-20.0F), (0.7F)),
                  (0.07D),
                  (0.9900000095367432D),
                  (0.800000011920929D),
                  true,
                  minecraftClient.player,
                  minecraftClient.player,
                  collisionCandidates
               ),
               0.0F,
               ItemStack.EMPTY,
               hand,
               tickDelta
            );
         }
      } else {
         this.addHeldPrediction(
            predictions,
            this.simulateFlight(
               position,
               this.initialVelocity(0.0F, (1.5F)),
               (0.03D),
               (0.9900000095367432D),
               (0.800000011920929D),
               true,
               minecraftClient.player,
               minecraftClient.player,
               collisionCandidates
            ),
            item instanceof SnowballItem && this.isSpecialServerItem(stack) ? (7.0F) : 0.0F,
            item instanceof EnderPearlItem ? stack : ItemStack.EMPTY,
            hand,
            tickDelta
         );
      }
   }

   private Vec3d initialVelocity(float pitchOffset, float speed) {
      float pitchRadians = minecraftClient.player.getPitch() * (0.017453292F);
      float yawRadians = minecraftClient.player.getYaw() * (0.017453292F);
      Vec3d position = new Vec3d(
            -MathHelper.sin(yawRadians) * MathHelper.cos(pitchRadians),
            -MathHelper.sin((minecraftClient.player.getPitch() + pitchOffset) * (0.017453292F)),
            MathHelper.cos(yawRadians) * MathHelper.cos(pitchRadians)
         )
         .normalize()
         .multiply(speed);
      Vec3d playerVelocity = minecraftClient.player.getEntityPos().subtract(minecraftClient.player.lastX, minecraftClient.player.lastY, minecraftClient.player.lastZ);
      return position.add(playerVelocity.x, minecraftClient.player.isOnGround() ? 0.0 : playerVelocity.y, playerVelocity.z);
   }

   private ProjectilePrediction() {
      this.ribbonConsumers = VertexConsumerProvider.immediate(new BufferAllocator((131072)));
      this.lastPredictionAge = (-2147483648);
      this.worldRenderListener = event -> {
         if (minecraftClient.player != null && minecraftClient.world != null) {
            ArrayList collisionCandidates = new ArrayList();

            for (Entity entity : minecraftClient.world.getEntities()) {
               if (entity instanceof LivingEntity candidateEntity && candidateEntity != minecraftClient.player && candidateEntity.isAlive()) {
                  collisionCandidates.add(new ProjectilePrediction.CollisionCandidate(candidateEntity, candidateEntity.getBoundingBox().expand((0.3D))));
               }
            }

            ArrayList<ProjectilePrediction.HeldProjectilePrediction> heldPredictions = new ArrayList();

            for (Hand hand : Hand.values()) {
               this.predictHeldProjectile(minecraftClient.player.getStackInHand(hand), hand, event.getTickDelta(), collisionCandidates, heldPredictions);
            }

            if (minecraftClient.world != this.lastWorld || minecraftClient.player.age != this.lastPredictionAge) {
               this.predictFlyingProjectiles(collisionCandidates);
            }

            this.updateSmoothedTrajectories(heldPredictions, event.getTickDelta());

            for (int heldIndex = 0; heldIndex < heldPredictions.size(); heldIndex++) {
               this.heldHitAlphas[heldIndex % this.heldHitAlphas.length] = this.heldHitAlphas[heldIndex % this.heldHitAlphas.length]
                  + (
                        (
                              ((ProjectilePrediction.HeldProjectilePrediction)heldPredictions.get(heldIndex)).landing().hitTarget() == null
                                    && !this.areaHitsEntity((ProjectilePrediction.HeldProjectilePrediction)heldPredictions.get(heldIndex), collisionCandidates)
                                 ? 0.0F
                                 : 1.0F
                           )
                           - this.heldHitAlphas[heldIndex % this.heldHitAlphas.length]
                     )
                     * (0.3F);
            }

            if (!this.trajectories.isEmpty()) {
               DepthSnapshot.captureAndClearDepth();
               Vec3d position = minecraftClient.gameRenderer.getCamera().getCameraPos();
               double pixelWorldScale = (2.0D)
                  * Math.tan(Math.toRadians(minecraftClient.options.getFov().getValue().intValue()) / (2.0D))
                  / Math.max(1, minecraftClient.getWindow().getFramebufferHeight());
               ObjectIterator entryIterator = this.trajectories.int2ObjectEntrySet().iterator();

               while (entryIterator.hasNext()) {
                  Entry entry = (Entry)entryIterator.next();
                  ProjectilePrediction.SmoothedTrajectory trajectory = (ProjectilePrediction.SmoothedTrajectory)entry.getValue();
                  if (entry.getIntKey() < 0 || this.trajectoryVisible(trajectory)) {
                     int lineColor = entry.getIntKey() < 0 ? this.landingColor(-1 - entry.getIntKey()) : (trajectory.hitsEntity ? (-54742) : HudStyle.accentColor);
                     if (this.prepareRibbon(trajectory, position, pixelWorldScale, lineColor)) {
                        emitRibbon(
                           this.ribbonConsumers.getBuffer(RIBBON_LAYER),
                           event.getMatrices().peek().getPositionMatrix(),
                           trajectory.ribbon
                        );
                     }
                  }
               }

               this.ribbonConsumers.draw();

               for (int markerIndex = 0; markerIndex < heldPredictions.size(); markerIndex++) {
                  ProjectilePrediction.HeldProjectilePrediction heldPrediction = (ProjectilePrediction.HeldProjectilePrediction)heldPredictions.get(markerIndex);
                  ProjectilePrediction.SmoothedTrajectory smoothedPrediction = (ProjectilePrediction.SmoothedTrajectory)this.trajectories.get(-1 - markerIndex);
                  Vec3d displayedLanding = smoothedPrediction != null ? smoothedPrediction.landingPosition : heldPrediction.landing().pos();
                  this.drawLandingMarker(event.getMatrices(), displayedLanding, this.landingColor(markerIndex));
                  if (this.range.getValue() && heldPrediction.areaRadiusBlocks() > 0.0F) {
                     this.drawAreaRadius(event.getMatrices(), displayedLanding, heldPrediction.areaRadiusBlocks(), ArgbColor.multiplyAlpha(this.landingColor(markerIndex), (0.5F)));
                  }
               }

               minecraftClient.getBufferBuilders().getEntityVertexConsumers().draw();
               ArrayList<LivingEntity> hitEntities = new ArrayList();

               for (ProjectilePrediction.HeldProjectilePrediction heldHit : heldPredictions) {
                  if (heldHit.landing().hitTarget() != null && !hitEntities.contains(heldHit.landing().hitTarget())) {
                     hitEntities.add(heldHit.landing().hitTarget());
                  }
               }

               for (ProjectilePrediction.FlyingProjectilePrediction flyingHit : this.flyingPredictions) {
                  if (flyingHit.landing().hitTarget() != null && !hitEntities.contains(flyingHit.landing().hitTarget())) {
                     hitEntities.add(flyingHit.landing().hitTarget());
                  }
               }

               for (LivingEntity hitEntity : hitEntities) {
                  if (WorldProjection.isVisible(hitEntity.getBoundingBox())) {
                     this.drawHitEntity(event.getMatrices(), hitEntity, event.getTickDelta());
                  }
               }

               minecraftClient.getBufferBuilders().getEntityVertexConsumers().draw();
            }
         }
      };
      this.lastCombatTargetName = "";
      this.hudRenderListener = event -> {
         if (minecraftClient.player != null && minecraftClient.world != null) {
            HudRenderContext.setDrawContext(event.getDrawContext());
            float coordinateScale = GuiScale.getWindowScale() / GuiScale.getBaseScale();
            if (AttackAura.getInstance().isEnabled() && AttackAura.getInstance().target() != null) {
               this.lastCombatTargetName = AttackAura.getInstance().target().getName().getString();
            }

            ObjectIterator entryIterator = this.trajectories.int2ObjectEntrySet().iterator();

            while (entryIterator.hasNext()) {
               Entry entry = (Entry)entryIterator.next();
               ProjectilePrediction.SmoothedTrajectory trajectory = (ProjectilePrediction.SmoothedTrajectory)entry.getValue();
               if (!trajectory.icon.isEmpty()) {
                  Vec3d screenPosition = WorldProjection.projectNearViewport(trajectory.landingPosition.add(0.0, (0.25D), 0.0), event.getWidth(), event.getHeight());
                  if (screenPosition != null) {
                     String ownerName = entry.getIntKey() >= 0
                           && minecraftClient.world.getEntityById(entry.getIntKey()) instanceof ProjectileEntity projectile
                           && projectile.getOwner() != null
                           && projectile.getOwner() != minecraftClient.player
                        ? projectile.getOwner().getName().getString()
                        : "";
                     float labelCenterX = (float)screenPosition.x * coordinateScale;
                     float labelCenterY = (float)screenPosition.y * coordinateScale;
                     boolean highlightOwner = !ownerName.isEmpty() && ownerName.equals(this.lastCombatTargetName);
                     GuiFont font = highlightOwner ? GuiFont.BOLD : GuiFont.SEMI;
                     float ownerWidth = ownerName.isEmpty() ? 0.0F : GuiTextRenderer.getWidth(font, ownerName, (6.0F)) + (3.0F);
                     float timeWidth = GuiTextRenderer.getWidth(GuiFont.SEMI, trajectory.flightTimeLabel, (6.0F));
                     float panelWidth = (14.0F) + ownerWidth + timeWidth + (4.0F);
                     float panelHeight = (11.0F);
                     float panelX = Math.round((labelCenterX - panelWidth / 2.0F) * 2.0F) / 2.0F;
                     float panelY = Math.round((labelCenterY - panelHeight / 2.0F) * 2.0F) / 2.0F;
                     event.getDrawContext().createNewRootLayer();
                     HudRenderContext.pushScale(
                        labelCenterX,
                        labelCenterY,
                        Math.max(
                           (0.6F), (1.1F) - (float)minecraftClient.gameRenderer.getCamera().getCameraPos().distanceTo(trajectory.landingPosition) * (0.004F)
                        )
                     );
                     GuiShapeRenderer.drawSoftBlur(
                        GuiShapeRenderer.BlurQuality.LOW,
                        true,
                        panelX,
                        panelY,
                        panelWidth,
                        panelHeight,
                        1.0F,
                        CornerRadii.uniform((3.5F)),
                        HudPanelRenderer.panelTint(),
                        (0.65F),
                        ArgbColor.lerp((-16777216), HudStyle.accentColor, (0.1F))
                     );
                     GuiItemRenderer.render(
                        trajectory.icon,
                        Math.round((panelX + (3.0F)) * 2.0F) / 2.0F,
                        Math.round((panelY + (panelHeight - (8.0F)) / 2.0F) * 2.0F) / 2.0F,
                        (8.0F)
                     );
                     float textY = HudPanelRenderer.rowTextY(panelY);
                     if (!ownerName.isEmpty()) {
                        GuiTextRenderer.draw(
                           font, ownerName, panelX + (14.0F), textY, (6.0F), highlightOwner ? (-42386) : HudPanelRenderer.primaryTextColor(1.0F)
                        );
                     }

                     GuiTextRenderer.draw(GuiFont.SEMI, trajectory.flightTimeLabel, panelX + (14.0F) + ownerWidth, textY, (6.0F), HudPanelRenderer.secondaryTextColor(1.0F));
                     HudRenderContext.popTransform();
                  }
               }
            }
         }
      };
      this.simulationPath = new ArrayList<>();
      this.range = this.addSetting(new BooleanSetting(("Радиус действия"), ("Рисует зону поражения зелий и особых снежков."), true));
      this.color = this.addSetting(new ColorSetting(("Цвет"), ("Цвет маркера приземления.")).withColor((-12417035)));
   }

   private void updateSmoothedTrajectories(ArrayList<ProjectilePrediction.HeldProjectilePrediction> heldPredictions, float tickDelta) {
      Vec3d playerPosition = minecraftClient.player.getLerpedPos(tickDelta);
      Vec3d playerOffset = this.lastPlayerPosition == null ? Vec3d.ZERO : playerPosition.subtract(this.lastPlayerPosition);
      this.lastPlayerPosition = playerPosition;
      if (playerOffset.lengthSquared() < (64.0D)) {
         ObjectIterator entryIterator = this.trajectories.int2ObjectEntrySet().iterator();

         while (entryIterator.hasNext()) {
            Entry entry = (Entry)entryIterator.next();
            if (entry.getIntKey() < 0) {
               ((ProjectilePrediction.SmoothedTrajectory)entry.getValue()).translate(playerOffset);
            }
         }
      }

      float deltaSeconds = this.lastFrameNanos == 0L ? (0.05F) : Math.min((0.1F), (float)(System.nanoTime() - this.lastFrameNanos) / (1.0E9F));
      this.lastFrameNanos = System.nanoTime();
      ObjectIterator trajectoryIterator = this.trajectories.values().iterator();

      while (trajectoryIterator.hasNext()) {
         ProjectilePrediction.SmoothedTrajectory trajectory = (ProjectilePrediction.SmoothedTrajectory)trajectoryIterator.next();
         trajectory.updatedThisFrame = false;
      }

      for (int heldIndex = 0; heldIndex < heldPredictions.size(); heldIndex++) {
         this.updateTrajectory(
            -1 - heldIndex,
            ((ProjectilePrediction.HeldProjectilePrediction)heldPredictions.get(heldIndex)).flightPath(),
            ((ProjectilePrediction.HeldProjectilePrediction)heldPredictions.get(heldIndex)).landing(),
            ((ProjectilePrediction.HeldProjectilePrediction)heldPredictions.get(heldIndex)).icon(),
            deltaSeconds
         );
      }

      for (ProjectilePrediction.FlyingProjectilePrediction flyingPrediction : this.flyingPredictions) {
         this.updateTrajectory(flyingPrediction.entityId(), flyingPrediction.path(), flyingPrediction.landing(), flyingPrediction.icon(), deltaSeconds);
      }

      this.trajectories.values().removeIf(trajectoryEntry -> !trajectoryEntry.updatedThisFrame);
   }

   private void addHeldPrediction(
      ArrayList<ProjectilePrediction.HeldProjectilePrediction> predictions, ProjectilePrediction.ProjectileImpact impact, float areaRadius, ItemStack icon, Hand hand, float tickDelta
   ) {
      if (impact != null) {
         Vec3d interpolationOffset = minecraftClient.player.getLerpedPos(tickDelta).subtract(minecraftClient.player.getEntityPos());
         ArrayList flightPath = new ArrayList(this.simulationPath.size());

         for (Vec3d pathPoint : this.simulationPath) {
            flightPath.add(pathPoint.add(interpolationOffset));
         }

         impact = new ProjectilePrediction.ProjectileImpact(impact.pos().add(interpolationOffset), impact.hitTarget());
         Vec3d forwardDirection = minecraftClient.player.getRotationVec(tickDelta);
         Vec3d handOffset = Vec3d.fromPolar(0.0F, minecraftClient.player.getYaw(tickDelta) + (90.0F))
            .multiply(hand == Hand.OFF_HAND ? (-0.32D) : (0.32D));
         int adjustedPointCount = Math.min(5, flightPath.size());

         for (int pointIndex = 0; pointIndex < adjustedPointCount; pointIndex++) {
            float handOffsetWeight = (1.0F - (float)pointIndex / adjustedPointCount) * (1.0F - (float)pointIndex / adjustedPointCount);
            flightPath.set(
               pointIndex,
               ((Vec3d)flightPath.get(pointIndex))
                  .add(
                     (handOffset.x + forwardDirection.x * (0.25D)) * handOffsetWeight,
                     (handOffset.y + forwardDirection.y * (0.25D) - (0.18D)) * handOffsetWeight,
                     (handOffset.z + forwardDirection.z * (0.25D)) * handOffsetWeight
                  )
            );
         }

         predictions.add(new ProjectilePrediction.HeldProjectilePrediction(impact, areaRadius, flightPath, icon));
      }
   }

   private void emitCircleOutline(VertexConsumer vertices, MatrixStack matrices, float radius, int segments, int color, float lineWidth) {
      for (int segmentIndex = 0; segmentIndex < segments; segmentIndex++) {
         float startAngle = (float)(segmentIndex * (3.141592653589793D) * (2.0D) / segments);
         float endAngle = (float)((segmentIndex + 1) * (3.141592653589793D) * (2.0D) / segments);
         float startX = MathHelper.cos(startAngle) * radius;
         float startZ = MathHelper.sin(startAngle) * radius;
         float endX = MathHelper.cos(endAngle) * radius;
         float endZ = MathHelper.sin(endAngle) * radius;
         float normalX = (endX - startX) / MathHelper.sqrt((endX - startX) * (endX - startX) + (endZ - startZ) * (endZ - startZ));
         float normalZ = (endZ - startZ) / MathHelper.sqrt((endX - startX) * (endX - startX) + (endZ - startZ) * (endZ - startZ));
         vertices.vertex(matrices.peek(), startX, 0.0F, startZ).color(color).normal(matrices.peek(), normalX, 0.0F, normalZ).lineWidth(lineWidth);
         vertices.vertex(matrices.peek(), endX, 0.0F, endZ).color(color).normal(matrices.peek(), normalX, 0.0F, normalZ).lineWidth(lineWidth);
      }
   }

   private static int lightenChannel(int channel, float progress) {
      return channel + (int)((255 - channel) * (0.45F) * progress);
   }

   private void predictFlyingProjectiles(ArrayList<ProjectilePrediction.CollisionCandidate> collisionCandidates) {
      this.flyingPredictions.clear();
      this.lastWorld = minecraftClient.world;
      this.lastPredictionAge = minecraftClient.player.age;

      for (Entity entity : minecraftClient.world.getEntities()) {
         if ((entity instanceof PersistentProjectileEntity || entity instanceof ThrownItemEntity || entity instanceof ItemEntity)
            && !entity.isOnGround()
            && !entity.noClip
            && !(entity.getVelocity().lengthSquared() < (1.0E-4D))) {
            ProjectilePrediction.ProjectileImpact impact = this.simulateFlight(
               entity.getEntityPos(),
               entity.getVelocity(),
               entity.getFinalGravity(),
               (0.9900000095367432D),
               entity instanceof TridentEntity ? (0.9900000095367432D) : (entity instanceof PersistentProjectileEntity ? (0.6000000238418579D) : (0.800000011920929D)),
               entity instanceof ThrownItemEntity,
               entity,
               entity instanceof ProjectileEntity projectile ? projectile.getOwner() : null,
               collisionCandidates
            );
            if (impact != null && this.simulationPath.size() > 2) {
               ItemStack stack = entity instanceof EnderPearlEntity
                  ? new ItemStack(Items.ENDER_PEARL)
                  : (
                     entity instanceof TridentEntity
                        ? new ItemStack(Items.TRIDENT)
                        : (entity instanceof PersistentProjectileEntity ? new ItemStack(Items.ARROW) : ItemStack.EMPTY)
                  );
               this.flyingPredictions.add(new ProjectilePrediction.FlyingProjectilePrediction(entity.getId(), impact, new ArrayList<>(this.simulationPath), stack));
            }
         }
      }
   }

   private int landingColor(int predictionIndex) {
      return ArgbColor.lerp(this.color.getArgb(), (-54742), this.heldHitAlphas[predictionIndex % this.heldHitAlphas.length]);
   }

   @Override
   protected void onDisable() {
      this.flyingPredictions.clear();
      this.trajectories.clear();
      this.lastWorld = null;
      this.lastPredictionAge = (-2147483648);
      this.lastFrameNanos = 0L;
      this.lastPlayerPosition = null;
   }

   private void drawLandingMarker(MatrixStack matrices, Vec3d position, int color) {
      Vec3d cameraPosition = minecraftClient.gameRenderer.getCamera().getCameraPos();
      matrices.push();
      matrices.translate(position.x - cameraPosition.x, position.y - cameraPosition.y, position.z - cameraPosition.z);
      Matrix4f positionMatrix = matrices.peek().getPositionMatrix();
      VertexConsumer vertices = minecraftClient.getBufferBuilders().getEntityVertexConsumers().getBuffer(RenderLayers.debugQuads());
      int sphereColor = color & (16777215) | (771751936);

      for (int latitudeIndex = 0; latitudeIndex < 10; latitudeIndex++) {
         float startPolarAngle = (float)(latitudeIndex * (3.141592653589793D) / (10.0D));
         float endPolarAngle = (float)((latitudeIndex + 1) * (3.141592653589793D) / (10.0D));

         for (int longitudeIndex = 0; longitudeIndex < 14; longitudeIndex++) {
            float startAzimuth = (float)(longitudeIndex * (3.141592653589793D) * (2.0D) / (14.0D));
            float endAzimuth = (float)((longitudeIndex + 1) * (3.141592653589793D) * (2.0D) / (14.0D));
            emitMarkerSphereVertex(vertices, positionMatrix, startPolarAngle, startAzimuth, sphereColor);
            emitMarkerSphereVertex(vertices, positionMatrix, endPolarAngle, startAzimuth, sphereColor);
            emitMarkerSphereVertex(vertices, positionMatrix, endPolarAngle, endAzimuth, sphereColor);
            emitMarkerSphereVertex(vertices, positionMatrix, startPolarAngle, endAzimuth, sphereColor);
         }
      }

      VertexConsumer lineVertices = minecraftClient.getBufferBuilders().getEntityVertexConsumers().getBuffer(RenderLayers.lines());
      this.emitCircleOutline(lineVertices, matrices, (0.1F), 32, (-16777216) | color, 2.0F);
      matrices.push();
      matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((90.0F)));
      this.emitCircleOutline(lineVertices, matrices, (0.1F), 32, (-16777216) | color, 2.0F);
      matrices.pop();
      matrices.multiply(minecraftClient.gameRenderer.getCamera().getRotation());
      matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((90.0F)));
      this.emitCircleOutline(lineVertices, matrices, (0.1F), 32, (-16777216) | color, 2.0F);
      matrices.pop();
   }

   private static void emitColoredVertex(VertexConsumer vertices, Matrix4f matrix, double x, double y, double z, int red, int green, int blue, int alpha) {
      vertices.vertex(matrix, (float)x, (float)y, (float)z).color(red, green, blue, alpha);
   }

   private boolean areaHitsEntity(ProjectilePrediction.HeldProjectilePrediction prediction, ArrayList<ProjectilePrediction.CollisionCandidate> collisionCandidates) {
      if (prediction.areaRadiusBlocks() <= 0.0F) {
         return false;
      }

      for (ProjectilePrediction.CollisionCandidate candidate : collisionCandidates) {
         if (candidate.target().getBoundingBox().getCenter().squaredDistanceTo(prediction.landing().pos()) <= prediction.areaRadiusBlocks() * prediction.areaRadiusBlocks()) {
            return true;
         }
      }

      return false;
   }

   private boolean trajectoryVisible(ProjectilePrediction.SmoothedTrajectory trajectory) {
      double minX = (1.7976931348623157E308D);
      double minY = (1.7976931348623157E308D);
      double minZ = (1.7976931348623157E308D);
      double maxX = (-1.7976931348623157E308D);
      double maxY = (-1.7976931348623157E308D);
      double maxZ = (-1.7976931348623157E308D);

      for (Vec3d position : trajectory.path) {
         minX = Math.min(minX, position.x);
         minY = Math.min(minY, position.y);
         minZ = Math.min(minZ, position.z);
         maxX = Math.max(maxX, position.x);
         maxY = Math.max(maxY, position.y);
         maxZ = Math.max(maxZ, position.z);
      }

      return trajectory.path.isEmpty() || WorldProjection.isVisible(new Box(minX, minY, minZ, maxX, maxY, maxZ).expand((0.5D)));
   }

   private void drawHitEntity(MatrixStack matrices, LivingEntity entity, float tickDelta) {
      Box bounds = entity.getBoundingBox();
      Vec3d interpolationOffset = entity.getLerpedPos(tickDelta).subtract(entity.getEntityPos()).subtract(minecraftClient.gameRenderer.getCamera().getCameraPos());
      matrices.push();
      matrices.translate(bounds.minX + interpolationOffset.x, bounds.minY + interpolationOffset.y, bounds.minZ + interpolationOffset.z);
      Matrix4f positionMatrix = matrices.peek().getPositionMatrix();
      VertexConsumer vertices = minecraftClient.getBufferBuilders().getEntityVertexConsumers().getBuffer(RenderLayers.debugQuads());
      float[] cornerX = new float[]{0.0F, (float)bounds.getLengthX()};
      float[] cornerY = new float[]{0.0F, (float)bounds.getLengthY()};
      float[] cornerZ = new float[]{0.0F, (float)bounds.getLengthZ()};
      int[][] faces = new int[][]{{0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}};

      for (int[] face : faces) {
         for (int cornerIndex : face) {
            vertices.vertex(positionMatrix, cornerX[cornerIndex >> 2], cornerY[cornerIndex >> 1 & 1], cornerZ[cornerIndex & 1]).color((788486766));
         }
      }

      VertexConsumer lineVertices = minecraftClient.getBufferBuilders().getEntityVertexConsumers().getBuffer(RenderLayers.lines());
      VertexRendering.drawOutline(
         matrices,
         lineVertices,
         VoxelShapes.cuboid(new Box(0.0, 0.0, 0.0, bounds.getLengthX(), bounds.getLengthY(), bounds.getLengthZ())),
         0.0,
         0.0,
         0.0,
         (-42386),
         (2.5F)
      );
      matrices.pop();
   }

   private void drawAreaRadius(MatrixStack matrices, Vec3d position, float radius, int color) {
      matrices.push();
      matrices.translate(
         position.x - minecraftClient.gameRenderer.getCamera().getCameraPos().x,
         position.y - minecraftClient.gameRenderer.getCamera().getCameraPos().y + (0.01D),
         position.z - minecraftClient.gameRenderer.getCamera().getCameraPos().z
      );
      this.emitCircleOutline(minecraftClient.getBufferBuilders().getEntityVertexConsumers().getBuffer(RenderLayers.lines()), matrices, radius, 64, color, (1.5F));
      matrices.pop();
   }

   private static void emitMarkerSphereVertex(VertexConsumer vertices, Matrix4f matrix, float polarAngle, float azimuth, int color) {
      vertices.vertex(
            matrix,
            MathHelper.sin(polarAngle) * MathHelper.cos(azimuth) * (0.1F),
            MathHelper.cos(polarAngle) * (0.1F),
            MathHelper.sin(polarAngle) * MathHelper.sin(azimuth) * (0.1F)
         )
         .color(color);
   }

   private boolean prepareRibbon(ProjectilePrediction.SmoothedTrajectory trajectory, Vec3d cameraPosition, double pixelWorldScale, int color) {
      int firstPoint = 0;

      while (trajectory.path.size() - firstPoint > 2 && trajectory.path.get(firstPoint).squaredDistanceTo(cameraPosition) < (2.25D)) {
         firstPoint++;
      }

      if (trajectory.path.size() - firstPoint < 2) {
         return false;
      }

      ProjectilePrediction.TrajectoryRibbon ribbonData = trajectory.ribbon;
      ribbonData.ensureCapacity(trajectory.path.size() - firstPoint);
      ribbonData.pointCount = trajectory.path.size() - firstPoint;
      ribbonData.color = color;

      for (int pointIndex = 0; pointIndex < ribbonData.pointCount; pointIndex++) {
         ribbonData.pointX[pointIndex] = trajectory.path.get(firstPoint + pointIndex).x - cameraPosition.x;
         ribbonData.pointY[pointIndex] = trajectory.path.get(firstPoint + pointIndex).y - cameraPosition.y;
         ribbonData.pointZ[pointIndex] = trajectory.path.get(firstPoint + pointIndex).z - cameraPosition.z;
      }

      double arcLength = 0.0;
      double previousNormalX = 0.0;
      double previousNormalY = 1.0;
      double previousNormalZ = 0.0;

      for (int normalIndex = 0; normalIndex < ribbonData.pointCount; normalIndex++) {
         double tangentX = ribbonData.pointX[Math.min(normalIndex + 1, ribbonData.pointCount - 1)] - ribbonData.pointX[Math.max(normalIndex - 1, 0)];
         double tangentY = ribbonData.pointY[Math.min(normalIndex + 1, ribbonData.pointCount - 1)] - ribbonData.pointY[Math.max(normalIndex - 1, 0)];
         double tangentZ = ribbonData.pointZ[Math.min(normalIndex + 1, ribbonData.pointCount - 1)] - ribbonData.pointZ[Math.max(normalIndex - 1, 0)];
         double normalX = tangentY * ribbonData.pointZ[normalIndex] - tangentZ * ribbonData.pointY[normalIndex];
         double normalY = tangentZ * ribbonData.pointX[normalIndex] - tangentX * ribbonData.pointZ[normalIndex];
         double normalZ = tangentX * ribbonData.pointY[normalIndex] - tangentY * ribbonData.pointX[normalIndex];
         if (normalX * normalX + normalY * normalY + normalZ * normalZ < (1.0E-8D)) {
            normalX = previousNormalX;
            normalY = previousNormalY;
            normalZ = previousNormalZ;
         } else {
            double inverseNormalLength = 1.0 / Math.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
            normalX *= inverseNormalLength;
            normalY *= inverseNormalLength;
            normalZ *= inverseNormalLength;
         }

         previousNormalX = normalX;
         ribbonData.normalX[normalIndex] = normalX;
         previousNormalY = normalY;
         ribbonData.normalY[normalIndex] = normalY;
         previousNormalZ = normalZ;
         ribbonData.normalZ[normalIndex] = normalZ;
         ribbonData.halfWidth[normalIndex] = (float)(
            Math.sqrt(
                  ribbonData.pointX[normalIndex] * ribbonData.pointX[normalIndex]
                     + ribbonData.pointY[normalIndex] * ribbonData.pointY[normalIndex]
                     + ribbonData.pointZ[normalIndex] * ribbonData.pointZ[normalIndex]
               )
               * pixelWorldScale
         );
         if (normalIndex > 0) {
            arcLength += trajectory.path.get(firstPoint + normalIndex).distanceTo(trajectory.path.get(firstPoint + normalIndex - 1));
         }

         ribbonData.pointAlpha[normalIndex] = (float)MathHelper.clamp((arcLength - (0.1D)) / (0.55D), 0.0, 1.0);
      }

      return true;
   }

   private static void emitRibbon(VertexConsumer vertices, Matrix4f matrix, ProjectilePrediction.TrajectoryRibbon ribbon) {
      int red = ribbon.color >> 16 & 0xFF;
      int green = ribbon.color >> 8 & 0xFF;
      int blue = ribbon.color & 0xFF;

      for (int segmentIndex = 0; segmentIndex < ribbon.pointCount - 1; segmentIndex++) {
         int startAlpha = (int)(ribbon.pointAlpha[segmentIndex] * (255.0F));
         int endAlpha = (int)(ribbon.pointAlpha[segmentIndex + 1] * (255.0F));
         if (startAlpha > 0 || endAlpha > 0) {
            float startProgress = (float)segmentIndex / (ribbon.pointCount - 1);
            float endProgress = (float)(segmentIndex + 1) / (ribbon.pointCount - 1);
            int startRed = lightenChannel(red, startProgress);
            int startGreen = lightenChannel(green, startProgress);
            int startBlue = lightenChannel(blue, startProgress);
            int endRed = lightenChannel(red, endProgress);
            int endGreen = lightenChannel(green, endProgress);
            int endBlue = lightenChannel(blue, endProgress);
            double startOffsetX = ribbon.normalX[segmentIndex] * ribbon.halfWidth[segmentIndex];
            double startOffsetY = ribbon.normalY[segmentIndex] * ribbon.halfWidth[segmentIndex];
            double startOffsetZ = ribbon.normalZ[segmentIndex] * ribbon.halfWidth[segmentIndex];
            double endOffsetX = ribbon.normalX[segmentIndex + 1] * ribbon.halfWidth[segmentIndex + 1];
            double endOffsetY = ribbon.normalY[segmentIndex + 1] * ribbon.halfWidth[segmentIndex + 1];
            double endOffsetZ = ribbon.normalZ[segmentIndex + 1] * ribbon.halfWidth[segmentIndex + 1];
            emitColoredVertex(
               vertices,
               matrix,
               ribbon.pointX[segmentIndex] - startOffsetX,
               ribbon.pointY[segmentIndex] - startOffsetY,
               ribbon.pointZ[segmentIndex] - startOffsetZ,
               startRed,
               startGreen,
               startBlue,
               startAlpha
            );
            emitColoredVertex(
               vertices,
               matrix,
               ribbon.pointX[segmentIndex + 1] - endOffsetX,
               ribbon.pointY[segmentIndex + 1] - endOffsetY,
               ribbon.pointZ[segmentIndex + 1] - endOffsetZ,
               endRed,
               endGreen,
               endBlue,
               endAlpha
            );
            emitColoredVertex(
               vertices,
               matrix,
               ribbon.pointX[segmentIndex + 1] + endOffsetX,
               ribbon.pointY[segmentIndex + 1] + endOffsetY,
               ribbon.pointZ[segmentIndex + 1] + endOffsetZ,
               endRed,
               endGreen,
               endBlue,
               endAlpha
            );
            emitColoredVertex(
               vertices,
               matrix,
               ribbon.pointX[segmentIndex] + startOffsetX,
               ribbon.pointY[segmentIndex] + startOffsetY,
               ribbon.pointZ[segmentIndex] + startOffsetZ,
               startRed,
               startGreen,
               startBlue,
               startAlpha
            );
            emitColoredVertex(
               vertices,
               matrix,
               ribbon.pointX[segmentIndex] + startOffsetX,
               ribbon.pointY[segmentIndex] + startOffsetY,
               ribbon.pointZ[segmentIndex] + startOffsetZ,
               startRed,
               startGreen,
               startBlue,
               startAlpha
            );
            emitColoredVertex(
               vertices,
               matrix,
               ribbon.pointX[segmentIndex + 1] + endOffsetX,
               ribbon.pointY[segmentIndex + 1] + endOffsetY,
               ribbon.pointZ[segmentIndex + 1] + endOffsetZ,
               endRed,
               endGreen,
               endBlue,
               endAlpha
            );
            emitColoredVertex(
               vertices,
               matrix,
               ribbon.pointX[segmentIndex + 1] + endOffsetX * (2.5D),
               ribbon.pointY[segmentIndex + 1] + endOffsetY * (2.5D),
               ribbon.pointZ[segmentIndex + 1] + endOffsetZ * (2.5D),
               endRed,
               endGreen,
               endBlue,
               0
            );
            emitColoredVertex(
               vertices,
               matrix,
               ribbon.pointX[segmentIndex] + startOffsetX * (2.5D),
               ribbon.pointY[segmentIndex] + startOffsetY * (2.5D),
               ribbon.pointZ[segmentIndex] + startOffsetZ * (2.5D),
               startRed,
               startGreen,
               startBlue,
               0
            );
            emitColoredVertex(
               vertices,
               matrix,
               ribbon.pointX[segmentIndex] - startOffsetX,
               ribbon.pointY[segmentIndex] - startOffsetY,
               ribbon.pointZ[segmentIndex] - startOffsetZ,
               startRed,
               startGreen,
               startBlue,
               startAlpha
            );
            emitColoredVertex(
               vertices,
               matrix,
               ribbon.pointX[segmentIndex + 1] - endOffsetX,
               ribbon.pointY[segmentIndex + 1] - endOffsetY,
               ribbon.pointZ[segmentIndex + 1] - endOffsetZ,
               endRed,
               endGreen,
               endBlue,
               endAlpha
            );
            emitColoredVertex(
               vertices,
               matrix,
               ribbon.pointX[segmentIndex + 1] - endOffsetX * (2.5D),
               ribbon.pointY[segmentIndex + 1] - endOffsetY * (2.5D),
               ribbon.pointZ[segmentIndex + 1] - endOffsetZ * (2.5D),
               endRed,
               endGreen,
               endBlue,
               0
            );
            emitColoredVertex(
               vertices,
               matrix,
               ribbon.pointX[segmentIndex] - startOffsetX * (2.5D),
               ribbon.pointY[segmentIndex] - startOffsetY * (2.5D),
               ribbon.pointZ[segmentIndex] - startOffsetZ * (2.5D),
               startRed,
               startGreen,
               startBlue,
               0
            );
         }
      }
   }

   private record FlyingProjectilePrediction(int entityId, ProjectilePrediction.ProjectileImpact landing, ArrayList<Vec3d> path, ItemStack icon) {
   }

   private static final class SmoothedTrajectory {
      public ArrayList<Vec3d> path = new ArrayList<>();
      public Vec3d landingPosition = Vec3d.ZERO;
      public int flightTicks = -1;
      public String flightTimeLabel;
      public boolean hitsEntity;
      public boolean updatedThisFrame;
      public ItemStack icon;
      public ProjectilePrediction.TrajectoryRibbon ribbon;
      public double[] oldArcLengths;
      public double[] newArcLengths;
      public double[] resampledX;
      public double[] resampledY;
      public double[] resampledZ;

      public void translate(Vec3d offset) {
         this.path.replaceAll(position -> position.add(offset));
         this.landingPosition = this.landingPosition.add(offset);
      }

      public void update(ArrayList<Vec3d> newPath, Vec3d newLandingPosition, boolean hitsEntity, ItemStack icon, float deltaSeconds) {
         float smoothingProgress = 1.0F - (float)Math.exp(-deltaSeconds * (100.0F));
         if (!this.path.isEmpty() && !(this.landingPosition.squaredDistanceTo(newLandingPosition) > (4096.0D))) {
            this.landingPosition = this.landingPosition.lerp(newLandingPosition, smoothingProgress);
            this.smoothPath(newPath, smoothingProgress);
         } else {
            this.path = new ArrayList<>(newPath);
            this.landingPosition = newLandingPosition;
         }

         if (this.flightTicks != newPath.size() - 1) {
            this.flightTicks = newPath.size() - 1;
            this.flightTimeLabel = Math.max(0, Math.round(this.flightTicks / 2.0F)) / 10 + "." + Math.max(0, Math.round(this.flightTicks / 2.0F)) % 10 + "с";
         }

         this.hitsEntity = hitsEntity;
         this.icon = icon;
      }

      private void smoothPath(ArrayList<Vec3d> newPath, float progress) {
         if (newPath.isEmpty()) {
            this.path.clear();
         } else if (Math.max(this.path.size(), newPath.size()) <= 1) {
            this.path = new ArrayList<>(newPath);
         } else if (this.path.size() == newPath.size()) {
            for (int equalSizeIndex = 0; equalSizeIndex < this.path.size(); equalSizeIndex++) {
               this.path
                  .set(equalSizeIndex, this.path.get(equalSizeIndex).lerp((Vec3d)newPath.get(equalSizeIndex), progress * ((0.15F) + (0.85F) * equalSizeIndex / (this.path.size() - 1))));
            }
         } else {
            int resampledCount = Math.max(this.path.size(), newPath.size());
            if (this.resampledX.length < resampledCount) {
               this.oldArcLengths = new double[Math.max(resampledCount, this.resampledX.length == 0 ? 64 : this.resampledX.length * 2)];
               this.newArcLengths = new double[this.oldArcLengths.length];
               this.resampledX = new double[this.oldArcLengths.length];
               this.resampledY = new double[this.oldArcLengths.length];
               this.resampledZ = new double[this.oldArcLengths.length];
            }

            this.oldArcLengths[0] = 0.0;

            for (int oldLengthIndex = 1; oldLengthIndex < this.path.size(); oldLengthIndex++) {
               this.oldArcLengths[oldLengthIndex] = this.oldArcLengths[oldLengthIndex - 1] + this.path.get(oldLengthIndex - 1).distanceTo(this.path.get(oldLengthIndex));
            }

            this.newArcLengths[0] = 0.0;

            for (int newLengthIndex = 1; newLengthIndex < newPath.size(); newLengthIndex++) {
               this.newArcLengths[newLengthIndex] = this.newArcLengths[newLengthIndex - 1] + ((Vec3d)newPath.get(newLengthIndex - 1)).distanceTo((Vec3d)newPath.get(newLengthIndex));
            }

            int oldSegment = 0;

            for (int oldSampleIndex = 0; oldSampleIndex < resampledCount; oldSampleIndex++) {
               double oldSampleDistance = this.oldArcLengths[this.path.size() - 1] * oldSampleIndex / (resampledCount - 1);

               while (oldSegment + 1 < this.path.size() && this.oldArcLengths[oldSegment + 1] < oldSampleDistance) {
                  oldSegment++;
               }

               if (oldSegment + 1 >= this.path.size()) {
                  this.resampledX[oldSampleIndex] = this.path.get(oldSegment).x;
                  this.resampledY[oldSampleIndex] = this.path.get(oldSegment).y;
                  this.resampledZ[oldSampleIndex] = this.path.get(oldSegment).z;
               } else {
                  double oldSegmentLength = this.oldArcLengths[oldSegment + 1] - this.oldArcLengths[oldSegment];
                  double oldSegmentProgress = oldSegmentLength <= (1.0E-8D) ? 0.0 : (oldSampleDistance - this.oldArcLengths[oldSegment]) / oldSegmentLength;
                  this.resampledX[oldSampleIndex] = MathHelper.lerp(oldSegmentProgress, this.path.get(oldSegment).x, this.path.get(oldSegment + 1).x);
                  this.resampledY[oldSampleIndex] = MathHelper.lerp(oldSegmentProgress, this.path.get(oldSegment).y, this.path.get(oldSegment + 1).y);
                  this.resampledZ[oldSampleIndex] = MathHelper.lerp(oldSegmentProgress, this.path.get(oldSegment).z, this.path.get(oldSegment + 1).z);
               }
            }

            int newSegment = 0;

            for (int newSampleIndex = 0; newSampleIndex < resampledCount; newSampleIndex++) {
               double newSampleDistance = this.newArcLengths[newPath.size() - 1] * newSampleIndex / (resampledCount - 1);

               while (newSegment + 1 < newPath.size() && this.newArcLengths[newSegment + 1] < newSampleDistance) {
                  newSegment++;
               }

               double sampleX = ((Vec3d)newPath.get(newSegment)).x;
               double sampleY = ((Vec3d)newPath.get(newSegment)).y;
               double sampleZ = ((Vec3d)newPath.get(newSegment)).z;
               if (newSegment + 1 < newPath.size()) {
                  double newSegmentLength = this.newArcLengths[newSegment + 1] - this.newArcLengths[newSegment];
                  double newSegmentProgress = newSegmentLength <= (1.0E-8D) ? 0.0 : (newSampleDistance - this.newArcLengths[newSegment]) / newSegmentLength;
                  sampleX = MathHelper.lerp(newSegmentProgress, ((Vec3d)newPath.get(newSegment)).x, ((Vec3d)newPath.get(newSegment + 1)).x);
                  sampleY = MathHelper.lerp(newSegmentProgress, ((Vec3d)newPath.get(newSegment)).y, ((Vec3d)newPath.get(newSegment + 1)).y);
                  sampleZ = MathHelper.lerp(newSegmentProgress, ((Vec3d)newPath.get(newSegment)).z, ((Vec3d)newPath.get(newSegment + 1)).z);
               }

               float sampleBlend = progress * ((0.15F) + (0.85F) * newSampleIndex / (resampledCount - 1));
               Vec3d position = new Vec3d(
                  MathHelper.lerp(sampleBlend, this.resampledX[newSampleIndex], sampleX),
                  MathHelper.lerp(sampleBlend, this.resampledY[newSampleIndex], sampleY),
                  MathHelper.lerp(sampleBlend, this.resampledZ[newSampleIndex], sampleZ)
               );
               if (newSampleIndex < this.path.size()) {
                  this.path.set(newSampleIndex, position);
               } else {
                  this.path.add(position);
               }
            }

            while (this.path.size() > resampledCount) {
               this.path.removeLast();
            }
         }
      }

      private SmoothedTrajectory() {
         this.flightTimeLabel = ("0.0с");
         this.icon = ItemStack.EMPTY;
         this.ribbon = new ProjectilePrediction.TrajectoryRibbon();
         this.oldArcLengths = new double[0];
         this.newArcLengths = new double[0];
         this.resampledX = new double[0];
         this.resampledY = new double[0];
         this.resampledZ = new double[0];
      }
   }

   private static final class TrajectoryRibbon {
      public double[] pointX = new double[0];
      public double[] pointY = new double[0];
      public double[] pointZ = new double[0];
      public double[] normalX = new double[0];
      public double[] normalY = new double[0];
      public double[] normalZ = new double[0];
      public float[] pointAlpha = new float[0];
      public float[] halfWidth = new float[0];
      public int pointCount;
      public int color;

      public void ensureCapacity(int requiredPoints) {
         if (this.pointX.length < requiredPoints) {
            int capacity = Math.max(requiredPoints, this.pointX.length == 0 ? 32 : this.pointX.length * 2);
            this.pointX = new double[capacity];
            this.pointY = new double[capacity];
            this.pointZ = new double[capacity];
            this.normalX = new double[capacity];
            this.normalY = new double[capacity];
            this.normalZ = new double[capacity];
            this.pointAlpha = new float[capacity];
            this.halfWidth = new float[capacity];
         }
      }
   }

   private record HeldProjectilePrediction(ProjectilePrediction.ProjectileImpact landing, float areaRadiusBlocks, ArrayList<Vec3d> flightPath, ItemStack icon) {
   }

   private record CollisionCandidate(LivingEntity target, Box box) {
   }

   private record ProjectileImpact(Vec3d pos, LivingEntity hitTarget) {
   }
}
