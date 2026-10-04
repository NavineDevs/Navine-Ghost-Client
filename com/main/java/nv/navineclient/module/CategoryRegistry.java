package nv.navineclient.module;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CategoryRegistry {
    private static final Map<String, String> customCategories = new LinkedHashMap<>();

    private CategoryRegistry() {
    }

    public static void register(String id, String displayName) {
        if (id == null || id.isBlank()) return;
        customCategories.putIfAbsent(id, displayName != null && !displayName.isBlank() ? displayName : id);
    }

    public static String getDisplayName(String id) {
        return customCategories.getOrDefault(id, id);
    }

    public static List<CategoryKey> orderedKeys() {
        List<CategoryKey> keys = new ArrayList<>();
        for (Module.Category category : Module.Category.orderedValues()) {
            keys.add(CategoryKey.builtin(category));
        }
        for (String id : customCategories.keySet()) {
            keys.add(CategoryKey.custom(id));
        }
        return keys;
    }

    public static void clearCustom() {
        customCategories.clear();
    }
}
