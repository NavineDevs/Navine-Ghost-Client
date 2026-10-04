package nv.navineclient.util.input;

public record NavineKeyEvent(int key, int scancode, int modifiers) {
    public int keyCode() {
        return this.key;
    }
}
