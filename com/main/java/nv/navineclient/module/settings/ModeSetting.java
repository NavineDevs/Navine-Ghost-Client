package nv.navineclient.module.settings;

import java.util.Arrays;
import java.util.List;

public class ModeSetting extends Setting<String> {
    private final List<String> modes;

    public ModeSetting(String name, String description, String defaultValue, String... modes) {
        super(name, description, defaultValue);
        this.modes = Arrays.asList(modes);
    }

    public List<String> getModes() { return modes; }
    
    public void cycle() {
        int index = modes.indexOf(getValue());
        int next = (index + 1) % modes.size();
        setValue(modes.get(next));
    }
    
    public void cycleBack() {
        int index = modes.indexOf(getValue());
        int prev = (index - 1 + modes.size()) % modes.size();
        setValue(modes.get(prev));
    }
}
