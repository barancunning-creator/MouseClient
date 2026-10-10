package org.luaj.vm2.lib.jse;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Retains string-based Lua members while Java declarations use semantic names. */
public final class RenamedJavaInstance extends JavaInstance {
    public record MemberAliases(Map<String, String> fields, Map<String, List<String>> methods) {}

    private final Map<Class<?>, MemberAliases> registry;
    private final MemberAliases aliases;
    private final Map<LuaValue, LuaValue> functions = new HashMap<>();

    private RenamedJavaInstance(Object instance, Map<Class<?>, MemberAliases> registry, MemberAliases aliases) {
        super(instance);
        this.registry = registry;
        this.aliases = aliases;
    }

    public static LuaValue coerce(Object instance, Map<Class<?>, MemberAliases> registry) {
        if (instance == null) return LuaValue.NIL;
        Map<String, String> fields = new HashMap<>();
        Map<String, List<String>> methods = new HashMap<>();
        for (Class<?> type = instance.getClass(); type != null; type = type.getSuperclass()) {
            MemberAliases current = registry.get(type);
            if (current == null) continue;
            current.fields().forEach(fields::putIfAbsent);
            current.methods().forEach((name, targets) -> {
                List<String> combined = methods.computeIfAbsent(name, unused -> new ArrayList<>());
                for (String target : targets) if (!combined.contains(target)) combined.add(target);
            });
        }
        if (fields.isEmpty() && methods.isEmpty()) return CoerceJavaToLua.coerce(instance);
        return new RenamedJavaInstance(instance, registry, new MemberAliases(fields, methods));
    }

    private LuaValue wrapResult(LuaValue value) {
        if (value.isuserdata() && !(value instanceof RenamedJavaInstance)) {
            Object instance = value.touserdata();
            for (Class<?> type = instance.getClass(); type != null; type = type.getSuperclass()) {
                if (registry.containsKey(type)) return coerce(instance, registry);
            }
        }
        return value;
    }

    @Override
    public LuaValue get(LuaValue key) {
        String name = key.tojstring();
        String field = aliases.fields().get(name);
        if (field != null) return wrapResult(super.get(LuaValue.valueOf(field)));
        LuaValue cached = functions.get(key);
        if (cached != null) return cached;
        List<String> targets = aliases.methods().get(name);
        LuaValue member;
        if (targets == null) {
            member = super.get(key);
        } else {
            List<JavaMethod> candidates = new ArrayList<>();
            // Use LuaJ's own coercion scores and Java reflection order for overloads.
            for (Method method : m_instance.getClass().getMethods()) {
                if (targets.contains(method.getName())) candidates.add(JavaMethod.forMethod(method));
            }
            if (candidates.isEmpty()) throw new LuaError("Missing Java member alias: " + name);
            member = candidates.size() == 1 ? candidates.getFirst() : JavaMethod.forMethods(candidates.toArray(JavaMethod[]::new));
        }
        if (!member.isfunction()) return wrapResult(member);
        LuaValue target = member;
        LuaValue function = new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                Varargs result = target.invoke(args);
                LuaValue[] values = new LuaValue[result.narg()];
                for (int i = 0; i < values.length; i++) values[i] = wrapResult(result.arg(i + 1));
                return LuaValue.varargsOf(values);
            }
        };
        functions.put(key, function);
        return function;
    }

    @Override
    public void set(LuaValue key, LuaValue value) {
        String field = aliases.fields().get(key.tojstring());
        super.set(field == null ? key : LuaValue.valueOf(field), value);
    }
}
