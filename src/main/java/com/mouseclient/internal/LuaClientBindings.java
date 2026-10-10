package com.mouseclient.internal;

import com.mouseclient.modules.impl.combat.LuaRotationRegistry;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.RenamedJavaInstance;
import org.luaj.vm2.lib.jse.RenamedJavaInstance.MemberAliases;

public final class LuaClientBindings {
    private static final Map<Class<?>, MemberAliases> ALIASES = loadAliases();

    private LuaClientBindings() {}

    public static LuaValue coerce(Object instance) {
        return RenamedJavaInstance.coerce(instance, ALIASES);
    }

    private static Map<Class<?>, MemberAliases> loadAliases() {
        try (InputStream input = LuaClientBindings.class.getResourceAsStream("/assets/mouseclient/lua-member-aliases.json")) {
            if (input == null) throw new IllegalStateException("Lua member aliases are missing");
            JsonObject json = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<Class<?>, MemberAliases> registry = new HashMap<>();
            Class<?>[] types = {LuaClientTypes.MatrixView.class, LuaClientTypes.StatusEffectView.class,
                LuaClientTypes.Vector3.class, LuaClientTypes.Timer.class, LuaClientTypes.ColorValue.class,
                LuaClientTypes.LivingEntityView.class, LuaClientTypes.EntityView.class,
                LuaClientTypes.AnimatedValue.class, LuaRotationRegistry.class};
            for (Class<?> type : types) {
                JsonObject entry = json.getAsJsonObject(type.getCanonicalName());
                if (entry == null) throw new IllegalStateException("Lua aliases are missing for " + type.getName());
                Map<String, String> fields = new HashMap<>();
                entry.getAsJsonObject("fields").entrySet().forEach(field -> fields.put(field.getKey(), field.getValue().getAsString()));
                Map<String, List<String>> methods = new HashMap<>();
                entry.getAsJsonObject("methods").entrySet().forEach(method -> {
                    List<String> targets = new ArrayList<>();
                    for (JsonElement target : method.getValue().getAsJsonArray()) targets.add(target.getAsString());
                    methods.put(method.getKey(), List.copyOf(targets));
                });
                registry.put(type, new MemberAliases(Map.copyOf(fields), Map.copyOf(methods)));
            }
            return Map.copyOf(registry);
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Failed to load Lua member aliases", error);
        }
    }
}
