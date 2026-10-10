package com.mouseclient.util.time;

import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.network.HudStyle;
import java.awt.Color;

public final class ClickGuiTheme {
   public static int accent = new Color(0, 210, 106, 255).getRGB();
   public static int panelBackground = new Color(11, 20, 15, 245).getRGB();
   public static int panelOutline = new Color(0, 210, 106, 35).getRGB();
   public static int headerText = new Color(255, 255, 255, 220).getRGB();
   public static int enabledModuleBackground = new Color(0, 210, 106, 32).getRGB();
   public static int disabledModuleBackground = new Color(255, 255, 255, 5).getRGB();
   public static int mutedText = new Color(138, 154, 144, 255).getRGB();
   public static int disabledControlOutline = new Color(255, 255, 255, 26).getRGB();
   public static int enabledControlOutline = new Color(0, 210, 106, 60).getRGB();
   public static int disabledSettingLabel = new Color(220, 228, 223, 255).getRGB();
   public static int enabledSettingLabel = new Color(245, 250, 247, 255).getRGB();
   public static int disabledValueText = new Color(180, 195, 185, 255).getRGB();
   public static int disabledSettingIcon = new Color(255, 255, 255, 51).getRGB();
   public static int enabledSettingIcon = new Color(0, 210, 106, 80).getRGB();
   public static int disabledModuleCheckbox = new Color(22, 40, 29, 255).getRGB();
   public static int disabledModuleText = new Color(180, 192, 184, 255).getRGB();
   public static int enabledModuleCheckbox = new Color(0, 210, 106, 255).getRGB();
   public static int disabledSwitchTrack = new Color(22, 40, 29, 255).getRGB();
   public static int disabledSwitchOutline = new Color(255, 255, 255, 20).getRGB();
   public static int enabledSwitchTrack = new Color(0, 210, 106, 255).getRGB();
   public static int enabledSwitchOutline = new Color(0, 210, 106, 80).getRGB();
   public static int disabledSwitchThumb = new Color(255, 255, 255, 38).getRGB();
   public static int hoveredChoiceFill = new Color(255, 255, 255, 38).getRGB();
   public static int hoveredChoiceOutline = new Color(255, 255, 255, 20).getRGB();
   public static int choiceFill = new Color(255, 255, 255, 15).getRGB();
   public static int choiceOutline = new Color(255, 255, 255, 5).getRGB();
   public static int activeChoiceText = new Color(255, 255, 255, 153).getRGB();
   public static int choiceText = new Color(255, 255, 255, 51).getRGB();
   public static int disabledSliderTrack = new Color(255, 255, 255, 26).getRGB();
   public static int disabledSliderFill = new Color(255, 255, 255, 153).getRGB();
   public static int enabledSliderTrack = new Color(71, 123, 255, 26).getRGB();
   public static int popupBackground = new Color(255, 255, 255, 5).getRGB();
   public static int popupOutline = new Color(255, 255, 255, 5).getRGB();
   public static int reservedWhiteAlpha38 = new Color(255, 255, 255, 38).getRGB();
   public static int reservedWhiteAlpha20 = new Color(255, 255, 255, 20).getRGB();
   public static int reservedWhiteAlpha15 = new Color(255, 255, 255, 15).getRGB();
   public static int reservedWhiteAlpha5 = new Color(255, 255, 255, 5).getRGB();
   public static int actionButtonOutline = new Color(0, 0, 0, 33).getRGB();
   public static int searchIconBackground = new Color(0, 0, 0, 26).getRGB();
   public static int tooltipText = new Color(188, 188, 188, 255).getRGB();
   public static int basePanelTint = new Color(15, 14, 17, 96).getRGB();
   public static int opaquePanelTint = new Color(15, 14, 17, 245).getRGB();
   public static int flatPanelOverlay = new Color(15, 14, 17, 80).getRGB();
   public static int panelHighlight = new Color(255, 255, 255, 3).getRGB();
   public static int strongPanelTint = new Color(15, 14, 17, 110).getRGB();
   public static int blurTint = new Color(16, 15, 19, 204).getRGB();
   public static int itemPanelFill = new Color(255, 255, 255, 3).getRGB();
   public static int itemHoverFill = new Color(255, 255, 255, 5).getRGB();
   public static int selectedItemFill = new Color(71, 123, 255, 20).getRGB();
   public static final String SEARCH_ICON = "assets/mouseclient/textures/gui/clickgui/search.png";
   public static final String CHEVRON_ICON = "assets/mouseclient/textures/gui/clickgui/chev.png";
   public static final String RIGHT_CHEVRON_ICON = "assets/mouseclient/textures/gui/clickgui/chevright.png";
   public static final String CHECK_ICON = "assets/mouseclient/textures/gui/clickgui/check.png";
   public static final String COLOR_PICKER_ICON = "assets/mouseclient/textures/gui/clickgui/picker.png";
   public static final String BIND_KEY_ICON = "assets/mouseclient/textures/gui/clickgui/bindkey.png";

   private ClickGuiTheme() {
   }

   public static String categoryIcon(ModuleCategory category) {
      return switch (category) {
         case COMBAT -> ("assets/mouseclient/textures/gui/clickgui/cat_combat.png");
         case MOVEMENT -> ("assets/mouseclient/textures/gui/clickgui/cat_movement.png");
         case RENDER -> ("assets/mouseclient/textures/gui/clickgui/cat_visuals.png");
         case PLAYER -> ("assets/mouseclient/textures/gui/clickgui/cat_player.png");
         case MISC -> ("assets/mouseclient/textures/gui/clickgui/cat_misc.png");
         case FUN -> ("assets/mouseclient/textures/gui/mainmenu/icons/settings.png");
      };
   }

   public static void updateAccent(int color) {
      int accentColor = (-16777216) | color & (16777215);
      accent = accentColor;
      panelBackground = withAlpha(GuiVisuals.lerpColor((-16777216), accentColor, (0.126F)), 194);
      panelOutline = withAlpha(accentColor, 15);
      enabledModuleBackground = withAlpha(accentColor, 13);
      enabledControlOutline = withAlpha(accentColor, 51);
      enabledSettingLabel = withAlpha(accentColor, 128);
      enabledSettingIcon = withAlpha(accentColor, 51);
      enabledModuleCheckbox = withAlpha(accentColor, 26);
      enabledSwitchTrack = withAlpha(accentColor, 26);
      enabledSwitchOutline = withAlpha(accentColor, 41);
      enabledSliderTrack = withAlpha(accentColor, 26);
      selectedItemFill = withAlpha(accentColor, 20);
      GuiVisuals.accentColor = accentColor;
      GuiVisuals.accentGradientColor = GuiVisuals.lerpColor(accentColor, -1, (0.14F));
      GuiVisuals.accentLightColor = GuiVisuals.lerpColor(accentColor, -1, (0.28F));
      GuiVisuals.accentOutlineColor = GuiVisuals.lerpColor(accentColor, -1, (0.17F));
      GuiVisuals.accentAlpha20 = withAlpha(accentColor, 20);
      HudStyle.accentColor = accentColor;
   }

   public static int accentOpaque() {
      return accent;
   }

   public static int accent(int alpha) {
      return withAlpha(accent, alpha);
   }

   public static int surface(float accentAmount, int alpha) {
      return withAlpha(GuiVisuals.lerpColor(-16777216, accent, accentAmount), alpha);
   }

   public static int onAccent() {
      int red = accent >> 16 & 255;
      int green = accent >> 8 & 255;
      int blue = accent & 255;
      float luminance = (0.2126F * red + 0.7152F * green + 0.0722F * blue) / 255.0F;
      return luminance > 0.62F ? 0xFF101410 : 0xFFFFFFFF;
   }

   public static int withAlpha(int color, int alpha) {
      return alpha << 24 | color & (16777215);
   }
}
