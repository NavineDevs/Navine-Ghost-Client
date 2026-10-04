package nv.navineclient.module.settings;

public class NumberSetting extends Setting<Double> {
    private final double min;
    private final double max;
    private final double increment;

    public NumberSetting(String name, String description, double defaultValue, double min, double max, double increment) {
        super(name, description, defaultValue);
        this.min = min;
        this.max = max;
        this.increment = increment;
    }
    
    public NumberSetting(String name, String description, double defaultValue, double min, double max) {
        this(name, description, defaultValue, min, max, Math.max(0.01, (max - min) / 100.0));
    }

    public double getMin() { return min; }
    public double getMax() { return max; }
    public double getIncrement() { return increment; }
    
    @Override
    public void setValue(Double value) {
        double rounded = Math.round(value / increment) * increment;
        super.setValue(Math.max(min, Math.min(max, rounded)));
    }
}
