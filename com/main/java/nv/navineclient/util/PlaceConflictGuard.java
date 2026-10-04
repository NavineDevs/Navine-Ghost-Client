package nv.navineclient.util;

public final class PlaceConflictGuard {
    private static boolean suppressVanillaUse;
    private static boolean modInitiated;

    private PlaceConflictGuard() {
    }

    public static void beginModPlacement() {
        modInitiated = true;
    }

    public static void endModPlacement() {
        if (!modInitiated) {
            return;
        }
        modInitiated = false;
        suppressVanillaUse = true;
    }

    public static void markHandled() {
        suppressVanillaUse = true;
    }

    public static boolean shouldSuppressVanillaUse() {
        return suppressVanillaUse && !modInitiated;
    }

    public static boolean isModInitiated() {
        return modInitiated;
    }

    public static void reset() {
        suppressVanillaUse = false;
        modInitiated = false;
    }
}
