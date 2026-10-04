package nv.navineclient.module;

public final class CategoryKey {
    private final Module.Category builtin;
    private final String customId;

    private CategoryKey(Module.Category builtin, String customId) {
        this.builtin = builtin;
        this.customId = customId;
    }

    public static CategoryKey builtin(Module.Category category) {
        return new CategoryKey(category, null);
    }

    public static CategoryKey custom(String customId) {
        return new CategoryKey(null, customId);
    }

    public boolean isCustom() {
        return customId != null;
    }

    public Module.Category getBuiltin() {
        return builtin;
    }

    public String getCustomId() {
        return customId;
    }

    public String storageId() {
        return isCustom() ? "custom:" + customId : builtin.name();
    }

    public String getDisplayName() {
        if (isCustom()) {
            return CategoryRegistry.getDisplayName(customId);
        }
        String name = builtin.name();
        return name.charAt(0) + name.substring(1).toLowerCase();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof CategoryKey other)) return false;
        if (isCustom() != other.isCustom()) return false;
        if (isCustom()) return customId.equals(other.customId);
        return builtin == other.builtin;
    }

    @Override
    public int hashCode() {
        return isCustom() ? customId.hashCode() : builtin.hashCode();
    }
}
