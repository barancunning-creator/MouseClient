package com.mouseclient.network;

import com.mouseclient.events.impl.StagedEvent;
import com.mouseclient.gui.HudRenderer;
import com.mouseclient.gui.MouseClientClickGUI;
import com.mouseclient.gui.Render2D;
import com.mouseclient.gui.MainMenuScreen;
import com.mouseclient.internal.RenderPipelineWarmup;
import com.mouseclient.modules.impl.misc.GuiRenderWarmup;
import com.mouseclient.modules.impl.misc.ModuleRegistry;
import com.mouseclient.modules.impl.misc.EventBus;
import com.mouseclient.modules.impl.player.ChatMacroManager;
import com.mouseclient.render.GuiItemRenderer;
import com.mouseclient.render.PlayerCosmeticsFeatureRenderer;
import com.mouseclient.render.pipeline.MouseClientClientPalette;
import com.mouseclient.util.friend.FriendCommand;
import com.mouseclient.util.time.ConfigStore;
import com.mouseclient.util.time.MouseClientCosmeticWarmup;
import com.mouseclient.util.time.AltAccountManager;
import com.mouseclient.util.time.IrcClient;
import com.mouseclient.util.time.WaypointManager;
import com.mouseclient.util.entity.RotationBuilder;
import java.util.ArrayList;
import lombok.Generated;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.KeyBinding.Category;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.InputUtil.Type;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.SynchronousResourceReloader;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.dreamix.protection.api.impl.profile.ProfileAPI;
import ru.dreamix.protection.api.impl.profile.objects.RoleObject;

public class ClientInitializer implements ModInitializer, ClientModInitializer {
   public static final String MOD_ID = "mouseclient";
   private static final ClientInitializer inst = new ClientInitializer();
   private static final Logger logger = LoggerFactory.getLogger(("маусклиент"));
   private long loadTimeMs;
   private boolean loaded;
   private int autosaveTicks;
   private ClientCommandDispatcher commandDispatcher;
   private KeyBinding openGuiKey;
   private final FrameRenderGate frameRenderGate = new FrameRenderGate();

   @Generated
   public boolean loaded() {
      return this.loaded;
   }

   public static Identifier id(String path) {
      return Identifier.of(("mouseclient"), path);
   }

   @Generated
   public KeyBinding openGuiKey() {
      return this.openGuiKey;
   }

   private void registerOpenKey() {
      this.openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(("key.mouseclient.clickgui"), Type.KEYSYM, 344, Category.GAMEPLAY));
      ClientTickEvents.END_CLIENT_TICK.register(client -> {
         while (this.openGuiKey.wasPressed()) {
            if (client.currentScreen == null) {
               client.setScreen(MouseClientClickGUI.instance());
            }
         }
      });
      if (Boolean.getBoolean(("mouseclient.openGui")) || System.getenv(("MOUSECLIENT_OPEN_GUI")) != null || Boolean.getBoolean(("mouseclient.openGui")) || System.getenv(("DELUXE_OPEN_GUI")) != null) {
         ClientTickEvents.END_CLIENT_TICK.register(openGuiClient -> {
            if (openGuiClient.currentScreen instanceof MainMenuScreen) {
               openGuiClient.setScreen(MouseClientClickGUI.instance());
            }
         });
      }
   }

   @Generated
   public ClientCommandDispatcher commandDispatcher() {
      return this.commandDispatcher;
   }

   private void registerTicks() {
      ClientTickEvents.START_CLIENT_TICK.register(client -> {
         if (EventBus.getInstance().hasListeners(ClientTickEvent.class)) {
            EventBus.getInstance().post(new ClientTickEvent(StagedEvent.EventStage.PRE));
         }
      });
      ClientTickEvents.END_CLIENT_TICK.register(tickClient -> {
         if (EventBus.getInstance().hasListeners(ClientTickEvent.class)) {
            EventBus.getInstance().post(new ClientTickEvent(StagedEvent.EventStage.POST));
         }

         if (this.loaded && ++this.autosaveTicks >= 1200) {
            this.autosaveTicks = 0;
            ConfigStore.saveIfChanged();
         }
      });
   }

   @Generated
   public static Logger logger() {
      return logger;
   }

   @Generated
   @Override
   public String toString() {
      return "маусклиент(loadTimeMs="
         + this.loadTimeMs()
         + ", loaded="
         + this.loaded()
         + ", autosaveTicks="
         + this.autosaveTicks()
         + ", commandDispatcher="
         + this.commandDispatcher()
         + ", openGuiKey="
         + this.openGuiKey()
         + ", framelibrary="
         + this.frameRenderGate()
         + ")";
   }

   @Generated
   public int autosaveTicks() {
      return this.autosaveTicks;
   }

   private void registerCosmetics() {
      MouseClientCosmeticWarmup.warmCatalog();
      MouseClientCosmeticWarmup.warmEquipped();
      LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, renderer, registrationHelper, context) -> {
         if (renderer instanceof PlayerEntityRenderer playerRenderer) {
            registrationHelper.register(new PlayerCosmeticsFeatureRenderer(playerRenderer));
         }
      });
      PetCosmeticsInitializer.initialize();
   }

   @Generated
   @Override
   public boolean equals(Object other) {
      if (other == this) {
         return true;
      } else if (!(other instanceof ClientInitializer initializer)) {
         return false;
      } else {
         if (!initializer.canEqual(this)) {
            return false;
         }

         if (this.loadTimeMs() != initializer.loadTimeMs()) {
            return false;
         }

         if (this.loaded() != initializer.loaded()) {
            return false;
         }

         if (this.autosaveTicks() != initializer.autosaveTicks()) {
            return false;
         }

         ClientCommandDispatcher dispatcher = this.commandDispatcher();
         ClientCommandDispatcher otherDispatcher = initializer.commandDispatcher();
         if (dispatcher == null ? otherDispatcher == null : dispatcher.equals(otherDispatcher)) {
            KeyBinding guiKey = this.openGuiKey();
            KeyBinding otherGuiKey = initializer.openGuiKey();
            if (guiKey == null ? otherGuiKey == null : guiKey.equals(otherGuiKey)) {
               FrameRenderGate renderGate = this.frameRenderGate();
               FrameRenderGate otherRenderGate = initializer.frameRenderGate();
               return renderGate == null ? otherRenderGate == null : renderGate.equals(otherRenderGate);
            } else {
               return false;
            }
         } else {
            return false;
         }
      }
   }

   @Generated
   public ClientInitializer commandDispatcher(ClientCommandDispatcher commandDispatcher) {
      this.commandDispatcher = commandDispatcher;
      return this;
   }

   public void unload() {
      ConfigStore.saveActive();
      GuiItemRenderer.close();
      Render2D.close();
      this.loaded = false;
      logger.info(("shutdown"));
   }

   public void load() {
      ProfileAPI.create(("user"), 1, RoleObject.ADMIN);
      this.loadTimeMs = System.currentTimeMillis();
      System.setProperty(("sun.font.layout.ffm"), ("false"));
      Render2D.initialize();
      GuiItemRenderer.initialize();
      MouseClientClientPalette.updateAndUpload();
      GuiRenderWarmup.subscribe();
      HudRenderer.register();
      ModuleRegistry.initialize();
      EventBus.getInstance().subscribe(AltAccountManager.getInstance());
      EventBus.getInstance().subscribe(WaypointManager.getInstance());
      EventBus.getInstance().subscribe(ChatMacroManager.getInstance());
      EventBus.getInstance().subscribe(IrcClient.getInstance());
      this.registerCosmetics();
      this.initCommands();
      this.registerOpenKey();
      this.registerTicks();
      ConfigStore.loadLast();
      ClientLifecycleEvents.CLIENT_STOPPING.register(client -> this.unload());
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
         if (this.loaded) {
            ConfigStore.saveActive();
         }
      }));
      ResourceLoader.get(ResourceType.CLIENT_RESOURCES).registerReloader(id(("render_warmup")), (SynchronousResourceReloader)resources -> {
         MinecraftClient reloadClient = MinecraftClient.getInstance();
         if (reloadClient != null) {
            reloadClient.execute(() -> {
               Render2D.reloadTextures();
               RenderPipelineWarmup.reset();
               GuiRenderWarmup.restart();
            });
         }
      });
      this.loaded = true;
   }

   @Generated
   public ClientInitializer loaded(boolean loaded) {
      this.loaded = loaded;
      return this;
   }

   @Generated
   public ClientInitializer loadTimeMs(long loadTimeMs) {
      this.loadTimeMs = loadTimeMs;
      return this;
   }

   @Generated
   public ClientInitializer autosaveTicks(int autosaveTicks) {
      this.autosaveTicks = autosaveTicks;
      return this;
   }

   @Override
   public void onInitializeClient() {
      inst.load();
   }

   @Generated
   public FrameRenderGate frameRenderGate() {
      return this.frameRenderGate;
   }

   @Generated
   public ClientInitializer openGuiKey(KeyBinding openGuiKey) {
      this.openGuiKey = openGuiKey;
      return this;
   }

   @Generated
   @Override
   public int hashCode() {
      byte unusedPrime = 59;
      int result = 1;
      long loadTime = this.loadTimeMs();
      result = result * 59 + (int)(loadTime >>> 32 ^ loadTime);
      result = result * 59 + (this.loaded() ? 79 : 97);
      result = result * 59 + this.autosaveTicks();
      ClientCommandDispatcher dispatcher = this.commandDispatcher();
      result = result * 59 + (dispatcher == null ? 43 : dispatcher.hashCode());
      KeyBinding guiKey = this.openGuiKey();
      result = result * 59 + (guiKey == null ? 43 : guiKey.hashCode());
      FrameRenderGate renderGate = this.frameRenderGate();
      return result * 59 + (renderGate == null ? 43 : renderGate.hashCode());
   }

   @Generated
   public static ClientInitializer inst() {
      return inst;
   }

   private void initCommands() {
      ArrayList commands = new ArrayList();
      commands.add(new ResourcePackCommand());
      commands.add(new ConfigCommand());
      commands.add(new FriendCommand());
      commands.add(new WaypointCommand());
      commands.add(new MacroCommand());
      commands.add(new StaffCommand());
      commands.add(new BlockEspCommand());
      commands.add(new AutoWardenCommand());
      commands.add(new ReconnectCommand());
      commands.add(new IrcCommand());
      commands.add(new PartyCommand());
      commands.add(new BuilderCommand());
      commands.add(new HelpCommand(commands));
      this.commandDispatcher = new ClientCommandDispatcher(commands, ("."));
      RotationBuilder.getInstance().bind();
   }

   @Override
   public void onInitialize() {
      logger.info(("маусклиент initialized"));
   }

   @Generated
   public long loadTimeMs() {
      return this.loadTimeMs;
   }

   @Generated
   protected boolean canEqual(Object other) {
      return other instanceof ClientInitializer;
   }
}
