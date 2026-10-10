package com.mouseclient.render.pipeline;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.internal.PacketEvent;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.modules.impl.render.BooleanSetting;
import com.mouseclient.network.ClientInitializer;
import com.mouseclient.util.entity.WorldProjection;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.BufferAllocator.CloseableBuffer;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

@ModuleInfo(name = "Torus", description = "Хромированное кольцо ударной волны из повреждённых сущностей.", category = ModuleCategory.RENDER)
public final class Torus extends Module {
   private static Torus INSTANCE;
   private static final RenderPipeline DEPTH_PIPELINE = RenderPipeline.builder()
      .withLocation(ClientInitializer.id(("pipeline/torus")))
      .withVertexShader(ClientInitializer.id(("core/torus")))
      .withFragmentShader(ClientInitializer.id(("core/torus")))
      .withVertexFormat(VertexFormats.POSITION_COLOR_NORMAL, DrawMode.QUADS)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
      .withDepthWrite(false)
      .withCull(false)
      .withSampler(("Sampler0"))
      .withUniform(("TorusParams"), UniformType.UNIFORM_BUFFER)
      .build();
   private static final RenderPipeline THROUGH_WALLS_PIPELINE = RenderPipeline.builder()
      .withLocation(ClientInitializer.id(("pipeline/torus_through_walls")))
      .withVertexShader(ClientInitializer.id(("core/torus")))
      .withFragmentShader(ClientInitializer.id(("core/torus")))
      .withVertexFormat(VertexFormats.POSITION_COLOR_NORMAL, DrawMode.QUADS)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withDepthWrite(false)
      .withCull(false)
      .withSampler(("Sampler0"))
      .withUniform(("TorusParams"), UniformType.UNIFORM_BUFFER)
      .build();
   public BooleanSetting depth;
   public SliderSetting noiseFrequency;
   public SliderSetting lifetime;
   public SliderSetting size;
   private final ConcurrentLinkedQueue<Integer> damagedEntityIds = new ConcurrentLinkedQueue<>();
   
   private final ArrayList<Torus.ShockwaveRing> rings = new ArrayList<>();
   private SimpleFramebuffer sceneFramebuffer;
   private GpuBuffer vertexBuffer;
   private GpuBuffer paramsBuffer;
   private BufferAllocator allocator;
   private final Vector3f scratchPosition = new Vector3f();
   private final Vector3f scratchNormal = new Vector3f();
   private static final int[][] QUAD_CORNERS = new int[][]{{0, 0}, {1, 0}, {1, 1}, {0, 1}};
   private static final double[] TUBE_COS = new double[11];
   private static final double[] TUBE_SIN = new double[11];
   private static final double[] RING_COS = new double[31];
   private static final double[] RING_SIN = new double[31];
   private final EventListener<PacketEvent> packetListener = event -> {
      if (event.isIncoming()
         && minecraftClient.player != null
         && event.getPacket() instanceof EntityDamageS2CPacket damagePacket
         && damagePacket.entityId() != minecraftClient.player.getId()) {
         if (damagePacket.sourceCauseId() == minecraftClient.player.getId() || damagePacket.sourceDirectId() == minecraftClient.player.getId()) {
            this.damagedEntityIds.add(damagePacket.entityId());
         }
      }
   };
   private final EventListener<WorldRenderEvent> worldRenderListener = event -> {
      if (minecraftClient.player != null && minecraftClient.world != null && minecraftClient.gameRenderer != null) {
         this.rings.removeIf(expiredRing -> (float)(System.currentTimeMillis() - expiredRing.spawnAtMs()) > expiredRing.lifetimeMs());

         Integer damagedEntityId;
         while ((damagedEntityId = this.damagedEntityIds.poll()) != null) {
            Entity entity = minecraftClient.world.getEntityById(damagedEntityId);
            if (entity != null) {
               Vec3d position = entity == minecraftClient.player
                  ? minecraftClient.player.getEyePos().add(minecraftClient.player.getRotationVector().multiply((1.2D)))
                  : (minecraftClient.crosshairTarget instanceof EntityHitResult hitResult && hitResult.getEntity() == entity ? hitResult.getPos() : entity.getEyePos());
               this.rings
                  .add(
                     new Torus.ShockwaveRing(
                        position,
                        RotationAxis.POSITIVE_Y
                           .rotationDegrees(
                              (float)(
                                 -(Math.toDegrees(Math.atan2(position.z - minecraftClient.player.getZ(), position.x - minecraftClient.player.getX())) - (90.0D))
                              )
                           )
                           .mul(
                              RotationAxis.POSITIVE_X
                                 .rotationDegrees(
                                    (float)(
                                       -Math.toDegrees(
                                          Math.atan2(
                                             position.y - minecraftClient.player.getEyePos().y,
                                             Math.hypot(position.x - minecraftClient.player.getX(), position.z - minecraftClient.player.getZ())
                                          )
                                       )
                                    )
                                 )
                           ),
                        System.currentTimeMillis(),
                        this.lifetime.floatValue(),
                        this.size.floatValue()
                     )
                  );
            }
         }

         if (!this.rings.isEmpty()
            && minecraftClient.getFramebuffer() != null
            && minecraftClient.getFramebuffer().getColorAttachmentView() != null
            && minecraftClient.getFramebuffer().textureWidth > 0
            && minecraftClient.getFramebuffer().textureHeight > 0) {
            boolean anyVisible = false;
            int quadCount = 0;

            for (int ringCount = this.rings.size(); quadCount < ringCount; quadCount++) {
               if (this.isRingVisible(this.rings.get(quadCount))) {
                  anyVisible = true;
                  break;
               }
            }

            if (anyVisible) {
               if (this.sceneFramebuffer == null) {
                  this.sceneFramebuffer = new SimpleFramebuffer(
                     ("mouseclient_torus_scene"), minecraftClient.getFramebuffer().textureWidth, minecraftClient.getFramebuffer().textureHeight, false
                  );
               }

               if (this.sceneFramebuffer.textureWidth != minecraftClient.getFramebuffer().textureWidth
                  || this.sceneFramebuffer.textureHeight != minecraftClient.getFramebuffer().textureHeight) {
                  this.sceneFramebuffer.resize(minecraftClient.getFramebuffer().textureWidth, minecraftClient.getFramebuffer().textureHeight);
               }

               RenderSystem.getDevice()
                  .createCommandEncoder()
                  .copyTextureToTexture(
                     minecraftClient.getFramebuffer().getColorAttachment(),
                     this.sceneFramebuffer.getColorAttachment(),
                     0,
                     0,
                     0,
                     0,
                     0,
                     minecraftClient.getFramebuffer().textureWidth,
                     minecraftClient.getFramebuffer().textureHeight
                  );
               quadCount = 0;
               if (this.allocator == null) {
                  this.allocator = new BufferAllocator(19200 * VertexFormats.POSITION_COLOR_NORMAL.getVertexSize());
               }

               CloseableBuffer previousBuffer = this.allocator.getAllocated();
               if (previousBuffer != null) {
                  previousBuffer.close();
               }

               BufferBuilder builder = new BufferBuilder(this.allocator, DrawMode.QUADS, VertexFormats.POSITION_COLOR_NORMAL);
               Vector3f vertexPosition = this.scratchPosition;
               Vector3f vertexNormal = this.scratchNormal;
               int[][] cornerOffsets = QUAD_CORNERS;
               double cameraX = minecraftClient.gameRenderer.getCamera().getCameraPos().x;
               double cameraY = minecraftClient.gameRenderer.getCamera().getCameraPos().y;
               double cameraZ = minecraftClient.gameRenderer.getCamera().getCameraPos().z;

               for (Torus.ShockwaveRing ring : this.rings) {
                  if (this.isRingVisible(ring)) {
                     float ageProgress = Math.min(1.0F, (float)(System.currentTimeMillis() - ring.spawnAtMs()) / ring.lifetimeMs());
                     float majorRadius = (0.6F) * ring.sizeMul() + 2.0F * ring.sizeMul() * (1.0F - (1.0F - ageProgress) * (1.0F - ageProgress) * (1.0F - ageProgress));
                     float tubeRadius = (0.4F) * ring.sizeMul() * (1.0F - ageProgress * ageProgress * ageProgress);
                     int alpha = (int)((255.0F) * (ageProgress < (0.7F) ? 1.0F : 1.0F - (ageProgress - (0.7F)) / (0.3F)));

                     for (int tubeIndex = 0; tubeIndex < 10; tubeIndex++) {
                        for (int ringIndex = 0; ringIndex < 30; ringIndex++) {
                           for (int[] cornerOffset : cornerOffsets) {
                              double tubeCos = TUBE_COS[tubeIndex + cornerOffset[0]];
                              double tubeSin = TUBE_SIN[tubeIndex + cornerOffset[0]];
                              double ringCos = RING_COS[ringIndex + cornerOffset[1]];
                              double ringSin = RING_SIN[ringIndex + cornerOffset[1]];
                              vertexNormal.set((float)(tubeCos * ringCos), (float)(tubeCos * ringSin), (float)tubeSin);
                              vertexPosition.set((float)((majorRadius + tubeRadius * tubeCos) * ringCos), (float)((majorRadius + tubeRadius * tubeCos) * ringSin), (float)(tubeRadius * tubeSin));
                              ring.facing().transform(vertexNormal);
                              ring.facing().transform(vertexPosition);
                              builder.vertex(
                                    (float)(ring.pos().x - cameraX + vertexPosition.x), (float)(ring.pos().y - cameraY + vertexPosition.y), (float)(ring.pos().z - cameraZ + vertexPosition.z)
                                 )
                                 .color(255, 255, 255, alpha)
                                 .normal(vertexNormal.x, vertexNormal.y, vertexNormal.z);
                           }

                           quadCount++;
                        }
                     }
                  }
               }

               if (this.paramsBuffer == null || this.paramsBuffer.isClosed()) {
                  this.paramsBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_torus_params"), 136, (160L));
               }

               MemoryStack stack = MemoryStack.stackPush();

               try {
                  ByteBuffer paramsData = stack.calloc(160);
                  event.getMatrices().peek().getPositionMatrix().get(0, paramsData);
                  WorldProjection.projectionMatrix().get(64, paramsData);
                  paramsData.putFloat(128, (float)minecraftClient.gameRenderer.getCamera().getCameraPos().x);
                  paramsData.putFloat(132, (float)minecraftClient.gameRenderer.getCamera().getCameraPos().y);
                  paramsData.putFloat(136, (float)minecraftClient.gameRenderer.getCamera().getCameraPos().z);
                  paramsData.putFloat(140, this.noiseFrequency.floatValue() / (100.0F));
                  RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.paramsBuffer.slice(0L, (160L)), paramsData);
               } catch (Throwable uploadFailure) {
                  if (stack != null) {
                     try {
                        stack.close();
                     } catch (Throwable closeFailure) {
                        uploadFailure.addSuppressed(closeFailure);
                     }
                  }

                  throw uploadFailure;
               }

               if (stack != null) {
                  stack.close();
               }

               try (BuiltBuffer meshData = builder.end()) {
                  if (this.vertexBuffer != null && !this.vertexBuffer.isClosed() && this.vertexBuffer.size() >= meshData.getBuffer().remaining()) {
                     RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.vertexBuffer.slice(0L, meshData.getBuffer().remaining()), meshData.getBuffer());
                  } else {
                     if (this.vertexBuffer != null && !this.vertexBuffer.isClosed()) {
                        this.vertexBuffer.close();
                     }

                     this.vertexBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_torus_vertices"), 40, meshData.getBuffer());
                  }

                  try (RenderPass renderPass = RenderSystem.getDevice()
                        .createCommandEncoder()
                        .createRenderPass(
                           () -> ("mouseclient_torus"),
                           minecraftClient.getFramebuffer().getColorAttachmentView(),
                           OptionalInt.empty(),
                           this.depth.getValue() ? minecraftClient.getFramebuffer().getDepthAttachmentView() : null,
                           OptionalDouble.empty()
                        )) {
                     renderPass.setPipeline(this.depth.getValue() ? DEPTH_PIPELINE : THROUGH_WALLS_PIPELINE);
                     renderPass.bindTexture(("Sampler0"), this.sceneFramebuffer.getColorAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
                     renderPass.setUniform(("TorusParams"), this.paramsBuffer);
                     renderPass.setVertexBuffer(0, this.vertexBuffer);
                     renderPass.setIndexBuffer(
                        RenderSystem.getSequentialBuffer(DrawMode.QUADS).getIndexBuffer(quadCount * 6),
                        RenderSystem.getSequentialBuffer(DrawMode.QUADS).getIndexType()
                     );
                     renderPass.drawIndexed(0, 0, quadCount * 6, 1);
                  }
               }
            }
         }
      }
   };

   private Torus() {
      this.depth = this.addSetting(new BooleanSetting(("Глубина"), ("Не позволяет показывать Torus сквозь блоки."), false));
      this.noiseFrequency = this.addSetting(new SliderSetting(("Частота шума"), ("Сила эффекта шума на поверхности.")).withRange(0.0F, (100.0F)).withStep(1.0F).setValue((50.0F)));
      this.lifetime = this.addSetting(new SliderSetting(("Время жизни"), ("Как долго тор остаётся видимым.")).withRange((500.0F), (5000.0F)).withStep((100.0F)).setValue((1000.0F)));
      this.size = this.addSetting(new SliderSetting(("Размер"), ("Общий множитель размера тора.")).withRange((0.2F), 2.0F).withStep((0.1F)).setValue((0.5F)));
   }

   static {
      for (int var0 = 0; var0 <= 10; var0++) {
         TUBE_COS[var0] = Math.cos(var0 * (0.6283185307179586D));
         TUBE_SIN[var0] = Math.sin(var0 * (0.6283185307179586D));
      }

      for (int var1 = 0; var1 <= 30; var1++) {
         RING_COS[var1] = Math.cos(var1 * (0.20943951023931953D));
         RING_SIN[var1] = Math.sin(var1 * (0.20943951023931953D));
      }
   }

   private boolean isRingVisible(Torus.ShockwaveRing ring) {
      return WorldProjection.isSphereVisible(ring.pos().x, ring.pos().y, ring.pos().z, (3.0F) * ring.sizeMul());
   }

   @Override
   protected void onDisable() {
      this.damagedEntityIds.clear();
      this.rings.clear();
      if (this.sceneFramebuffer != null) {
         this.sceneFramebuffer.delete();
         this.sceneFramebuffer = null;
      }

      if (this.vertexBuffer != null && !this.vertexBuffer.isClosed()) {
         this.vertexBuffer.close();
      }

      this.vertexBuffer = null;
      if (this.paramsBuffer != null && !this.paramsBuffer.isClosed()) {
         this.paramsBuffer.close();
      }

      this.paramsBuffer = null;
      if (this.allocator != null) {
         this.allocator.close();
         this.allocator = null;
      }
   }

   public static Torus getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new Torus();
      }

      return INSTANCE;
   }

   private record ShockwaveRing(Vec3d pos, Quaternionf facing, long spawnAtMs, float lifetimeMs, float sizeMul) {
   }
}
