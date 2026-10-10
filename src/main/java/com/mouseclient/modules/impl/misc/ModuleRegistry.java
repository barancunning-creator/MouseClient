package com.mouseclient.modules.impl.misc;

import com.mouseclient.modules.impl.combat.AimAssist;
import com.mouseclient.modules.impl.combat.AnchorAura;
import com.mouseclient.modules.impl.combat.AttackAura;
import com.mouseclient.modules.impl.combat.AutoSwap;
import com.mouseclient.modules.impl.combat.BackTrack;
import com.mouseclient.modules.impl.combat.BlockSlot;
import com.mouseclient.modules.impl.combat.CrystalAura;
import com.mouseclient.modules.impl.combat.ElytraTarget;
import com.mouseclient.modules.impl.combat.HitBoxes;
import com.mouseclient.modules.impl.combat.ItemHelper;
import com.mouseclient.modules.impl.combat.MaceHelper;
import com.mouseclient.modules.impl.combat.NoEntityTrace;
import com.mouseclient.modules.impl.combat.NoServerPack;
import com.mouseclient.modules.impl.combat.TriggerBot;
import com.mouseclient.modules.impl.combat.Velocity;
import com.mouseclient.modules.impl.fun.FunModules;
import com.mouseclient.modules.impl.movement.AirStuck;
import com.mouseclient.modules.impl.movement.AutoSprint;
import com.mouseclient.modules.impl.movement.Flight;
import com.mouseclient.modules.impl.movement.NoSlow;
import com.mouseclient.modules.impl.movement.ScreenWalk;
import com.mouseclient.modules.impl.movement.Spider;
import com.mouseclient.modules.impl.player.AutoExp;
import com.mouseclient.modules.impl.player.ClickAction;
import com.mouseclient.modules.impl.player.ElytraHelper;
import com.mouseclient.modules.impl.player.ItemScroller;
import com.mouseclient.modules.impl.player.NoPlayerDelay;
import com.mouseclient.modules.impl.player.NoPush;
import com.mouseclient.modules.impl.player.PotionThrower;
import com.mouseclient.modules.impl.player.ServerHelper;
import com.mouseclient.modules.impl.player.UseTracker;
import com.mouseclient.modules.impl.render.ArmorBar;
import com.mouseclient.modules.impl.render.EntityDirectionArrows;
import com.mouseclient.modules.impl.render.AspectRatio;
import com.mouseclient.modules.impl.render.AutoTotem;
import com.mouseclient.modules.impl.render.BlockOverlay;
import com.mouseclient.modules.impl.render.Brightness;
import com.mouseclient.modules.impl.render.CameraFeature;
import com.mouseclient.modules.impl.render.Chams;
import com.mouseclient.modules.impl.render.Crosshair;
import com.mouseclient.modules.impl.render.FreeCamera;
import com.mouseclient.modules.impl.render.HudSettingsModule;
import com.mouseclient.modules.impl.render.JumpCircle;
import com.mouseclient.modules.impl.render.NoRender;
import com.mouseclient.modules.impl.render.SeeInvisible;
import com.mouseclient.modules.impl.render.ShulkerPreview;
import com.mouseclient.modules.impl.render.SmoothFeature;
import com.mouseclient.modules.impl.render.StructureInfo;
import com.mouseclient.modules.impl.render.SwordAnimations;
import com.mouseclient.modules.impl.render.Tags;
import com.mouseclient.modules.impl.render.ViewModel;
import com.mouseclient.modules.impl.render.WardenEsp;
import com.mouseclient.network.AutoAppleFarm;
import com.mouseclient.network.AutoBuyModule;
import com.mouseclient.network.AutoMine;
import com.mouseclient.network.AutoVillager;
import com.mouseclient.network.AutoWarden;
import com.mouseclient.render.pipeline.BlockEsp;
import com.mouseclient.render.pipeline.CustomWorld;
import com.mouseclient.render.pipeline.MotionBlur;
import com.mouseclient.render.pipeline.Particles;
import com.mouseclient.render.pipeline.SkeletonEsp;
import com.mouseclient.render.pipeline.TargetEsp;
import com.mouseclient.render.pipeline.Torus;
import com.mouseclient.render.pipeline.TotemGhost;
import com.mouseclient.util.entity.ProjectilePrediction;
import com.mouseclient.util.time.AutoDeliver;
import java.util.ArrayList;
import java.util.List;
import ru.dreamix.protection.api.impl.profile.ProfileAPI;
import ru.dreamix.protection.api.impl.profile.objects.RoleObject;

public final class ModuleRegistry {
   private static final List<Module> modules = new ArrayList<>();
   private static boolean initialized;

   public static Module findByName(String name) {
      for (Module module : modules) {
         if (module.name.equalsIgnoreCase(name)) {
            return module;
         }
      }

      return null;
   }

   public static void register(Module module) {
      if (module != null && !modules.contains(module)) {
         if (!(module instanceof AutoMine)
               && !(module instanceof AutoBuyModule)
               && !(module instanceof AutoVillager)
               && !(module instanceof AutoAppleFarm)
               && !(module instanceof AutoWarden)
            || ProfileAPI.get().getModel().getRole().priorityHigherOrEquals(RoleObject.BETA)) {
            if (!(module instanceof YoutubeModeModule) || ProfileAPI.get().getModel().getRole().priorityHigherOrEquals(RoleObject.YOUTUBE)) {
               if (!(module instanceof AdminShard) || ProfileAPI.get().getModel().getRole().priorityHigherOrEquals(RoleObject.ADMIN)) {
                  if (!(module instanceof AdminRecord) || ProfileAPI.get().getModel().getRole().priorityHigherOrEquals(RoleObject.ADMIN)) {
                     modules.add(module);
                  }
               }
            }
         }
      }
   }

   public static List<Module> modules() {
      return modules;
   }

   private ModuleRegistry() {
   }

   public static void unregister(Module module) {
      modules.remove(module);
   }

   public static List<Module> byCategory(ModuleCategory category) {
      ArrayList matches = new ArrayList();

      for (Module module : modules) {
         if (module.category == category) {
            matches.add(module);
         }
      }

      return matches;
   }

   public static void initialize() {
      if (!initialized) {
         initialized = true;
         register(AttackAura.getInstance());
         register(CrystalAura.getInstance());
         register(AnchorAura.getInstance());
         register(ElytraTarget.getInstance());
         register(MaceHelper.getInstance());
         register(AutoSwap.getInstance());
         register(AutoTotem.getInstance());
         register(TriggerBot.getInstance());
         register(ItemHelper.getInstance());
         register(AimAssist.getInstance());
         register(NoEntityTrace.getInstance());
         register(NoServerPack.getInstance());
         register(HitBoxes.getInstance());
         register(BackTrack.getInstance());
         register(AutoRegister.getInstance());
         register(AuctionHelper.getInstance());
         register(AutoLeave.getInstance());
         register(AutoTool.getInstance());
         register(AutoEat.getInstance());
         register(AutoAccept.getInstance());
         register(AutoDeliver.getInstance());
         register(ClanUpgrader.getInstance());
         if (ProfileAPI.get().getModel().getRole().priorityHigherOrEquals(RoleObject.BETA)) {
            register(AutoMine.getInstance());
            register(AutoBuyModule.getInstance());
            register(AutoVillager.getInstance());
            register(AutoAppleFarm.getInstance());
            register(AutoWarden.getInstance());
         }

         register(AutoSell.getInstance());
         register(PotionCombiner.getInstance());
         register(ChestStealer.getInstance());
         register(InfoReplace.getInstance());
         register(StreamerMod.getInstance());
         register(YoutubeModeModule.getInstance());
         register(NameProtect.getInstance());
         register(Globals.getInstance());
         register(PartyModule.getInstance());
         register(DuelHelper.getInstance());
         register(AutoMessage.getInstance());
         register(AirStuck.getInstance());
         register(AutoSprint.getInstance());
         register(Flight.getInstance());
         register(ScreenWalk.getInstance());
         register(Velocity.getInstance());
         register(NoSlow.getInstance());
         register(Spider.getInstance());
         register(ItemScroller.getInstance());
         register(NoPush.getInstance());
         register(BlockSlot.getInstance());
         register(NoRender.getInstance());
         ProjectilePrediction.getInstance();
         register(Tags.getInstance());
         register(TargetEsp.getInstance());
         register(ArmorBar.getInstance());
         register(ShulkerPreview.getInstance());
         register(SkeletonEsp.getInstance());
         register(Chams.getInstance());
         register(BlockEsp.getInstance());
         register(SeeInvisible.getInstance());
         register(Torus.getInstance());
         TotemGhost.getInstance();
         register(Crosshair.getInstance());
         register(EntityDirectionArrows.getInstance());
         register(BassBoost.getInstance());
         register(ClickAction.getInstance());
         register(AutoExp.getInstance());
         register(ElytraHelper.getInstance());
         register(NoPlayerDelay.getInstance());
         register(FreeCamera.getInstance());
         register(CameraFeature.getInstance());
         register(SwordAnimations.getInstance());
         register(ViewModel.getInstance());
         register(HudSettingsModule.getInstance());
         FunModules.registerAll();
         SmoothFeature.getInstance();
         register(AspectRatio.getInstance());
         register(StructureInfo.getInstance());
         register(WardenEsp.getInstance());
         register(JumpCircle.getInstance());
         register(BlockOverlay.getInstance());
         register(Particles.getInstance());
         register(MotionBlur.getInstance());
         register(CustomWorld.getInstance());
         register(Brightness.getInstance());
         register(ServerHelper.getInstance());
         register(PotionThrower.getInstance());
         register(UseTracker.getInstance());
      }
   }
}
