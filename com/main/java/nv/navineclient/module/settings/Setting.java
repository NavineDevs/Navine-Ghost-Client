package nv.navineclient.module.settings;

public abstract class Setting<T> {
    private final String name;
    private final String description;
    private T value;
    private final T defaultValue;

    public Setting(String name, String description, T defaultValue) {
        this.name = name;
        this.description = description;
        this.value = defaultValue;
        this.defaultValue = defaultValue;
    }

    public String getName() { return this.name; }
    public String getDescription() { return this.description; }
    public T getValue() { return this.value; }
    public T getDefaultValue() { return this.defaultValue; }
    
    public void setValue(T value) {
        this.value = value;
    }
    
    public void reset() {
        this.value = this.defaultValue;
    }
}
