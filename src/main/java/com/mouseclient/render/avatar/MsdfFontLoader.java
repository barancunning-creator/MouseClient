package com.mouseclient.render.avatar;

import com.mouseclient.network.ClientInitializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.util.HashMap;
import java.util.Optional;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.ResourceTexture;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

public final class MsdfFontLoader {

   private MsdfFontLoader() {
   }

   public static MsdfFont load(String resourcePath) {
      Identifier id = Identifier.of(("mouseclient"), resourcePath + ".json");
      Identifier atlasTexture = Identifier.of(("mouseclient"), resourcePath + ".png");
      MinecraftClient client = MinecraftClient.getInstance();

      try {
         client.getTextureManager().registerTexture(atlasTexture, new ResourceTexture(atlasTexture));
      } catch (Throwable registrationFailure) {
         ClientInitializer.logger().warn(("[MSDF] Failed to register atlas texture {}"), atlasTexture, registrationFailure);
         return null;
      }

      Optional resource = client.getResourceManager().getResource(id);
      if (resource.isEmpty()) {
         ClientInitializer.logger().warn(("[MSDF] Layout JSON not found: {}"), id);
         return null;
      }

      try (BufferedReader reader = ((Resource)resource.get()).getReader()) {
         JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
         JsonObject atlasJson = json.getAsJsonObject(("atlas"));
         int atlasWidth = atlasJson.get(("width")).getAsInt();
         int atlasHeight = atlasJson.get(("height")).getAsInt();
         float pxRange = atlasJson.has(("distanceRange")) ? atlasJson.get(("distanceRange")).getAsFloat() : (4.0F);
         JsonObject metricsJson = json.getAsJsonObject(("metrics"));
         float lineHeight = metricsJson.get(("lineHeight")).getAsFloat();
         float ascender = metricsJson.get(("ascender")).getAsFloat();
         float descender = metricsJson.get(("descender")).getAsFloat();
         HashMap glyphs = new HashMap(512);

         for (JsonElement jsonElement : json.getAsJsonArray(("glyphs"))) {
            JsonObject glyphJson = jsonElement.getAsJsonObject();
            int codePoint = glyphJson.get(("unicode")).getAsInt();
            float advance = glyphJson.has(("advance")) ? glyphJson.get(("advance")).getAsFloat() : 0.0F;
            if (glyphJson.has(("planeBounds")) && glyphJson.has(("atlasBounds"))) {
               JsonObject planeBounds = glyphJson.getAsJsonObject(("planeBounds"));
               JsonObject atlasBounds = glyphJson.getAsJsonObject(("atlasBounds"));
               float planeLeft = planeBounds.get(("left")).getAsFloat();
               float planeBottom = planeBounds.get(("bottom")).getAsFloat();
               float planeRight = planeBounds.get(("right")).getAsFloat();
               float planeTop = planeBounds.get(("top")).getAsFloat();
               float atlasLeft = atlasBounds.get(("left")).getAsFloat();
               float atlasBottom = atlasBounds.get(("bottom")).getAsFloat();
               float atlasRight = atlasBounds.get(("right")).getAsFloat();
               float atlasTop = atlasBounds.get(("top")).getAsFloat();
               if (atlasRight - atlasLeft > 1.0F && atlasTop - atlasBottom > 1.0F) {
                  float planeInsetX = (planeRight - planeLeft) * (0.5F) / (atlasRight - atlasLeft);
                  float planeInsetY = (planeTop - planeBottom) * (0.5F) / (atlasTop - atlasBottom);
                  planeLeft += planeInsetX;
                  planeRight -= planeInsetX;
                  planeBottom += planeInsetY;
                  planeTop -= planeInsetY;
                  atlasLeft += (0.5F);
                  atlasRight -= (0.5F);
                  atlasBottom += (0.5F);
                  atlasTop -= (0.5F);
               }

               float uMin = atlasLeft / atlasWidth;
               float uMax = atlasRight / atlasWidth;
               float vMin = (atlasHeight - atlasTop) / atlasHeight;
               float vMax = (atlasHeight - atlasBottom) / atlasHeight;
               glyphs.put(codePoint, new MsdfGlyph(true, advance, planeLeft, planeTop, planeRight, planeBottom, uMin, vMin, uMax, vMax));
            } else {
               glyphs.put(codePoint, MsdfGlyph.nonDrawable(advance));
            }
         }

         HashMap kerning = new HashMap();
         if (json.has(("kerning"))) {
            for (JsonElement kerningElement : json.getAsJsonArray(("kerning"))) {
               JsonObject kerningJson = kerningElement.getAsJsonObject();
               int leftCodePoint = kerningJson.get(("unicode1")).getAsInt();
               int rightCodePoint = kerningJson.get(("unicode2")).getAsInt();
               float kerningAdvance = kerningJson.get(("advance")).getAsFloat();
               if (kerningAdvance != 0.0F) {
                  kerning.put((long)leftCodePoint << 32 | rightCodePoint & (4294967295L), kerningAdvance);
               }
            }
         }

         ClientInitializer.logger().info(("[MSDF] Loaded font {} ({} glyphs, {}x{} atlas, pxRange {})"), new Object[]{resourcePath, glyphs.size(), atlasWidth, atlasHeight, pxRange});
         return new MsdfFont(atlasTexture, atlasWidth, atlasHeight, pxRange, lineHeight, ascender, descender, glyphs, kerning);
      } catch (Throwable parseFailure) {
         ClientInitializer.logger().warn(("[MSDF] Failed to parse layout {}"), id, parseFailure);
         return null;
      }
   }
}
