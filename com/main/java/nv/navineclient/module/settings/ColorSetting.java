package nv.navineclient.module.settings;

public class ColorSetting extends Setting<Integer> {
    public ColorSetting(String name, String description, int defaultColor) {
        super(name, description, defaultColor);
    }
    
    public int getRed() { return (getValue() >> 16) & 0xFF; }
    public int getGreen() { return (getValue() >> 8) & 0xFF; }
    public int getBlue() { return getValue() & 0xFF; }
    public int getAlpha() { return (getValue() >> 24) & 0xFF; }
    
    public void setRGB(int r, int g, int b) {
        setValue(0xFF000000 | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF));
    }
}
