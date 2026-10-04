package nv.navineclient.module.render;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import nv.navineclient.module.Module;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;
import nv.navineclient.module.settings.ModeSetting;
import java.util.HashSet;
import java.util.Set;

public class XRay extends Module {
    public static XRay INSTANCE;
    
    private final ModeSetting mode = new ModeSetting("Mode", "XRay filtering mode", "Whitelist", "Whitelist", "Blacklist");
    private final NumberSetting opacity = new NumberSetting("Opacity", "Block opacity", 1.0, 0.1, 1.0);

    // Ores
    private final BooleanSetting diamonds = new BooleanSetting("Diamonds", "Show diamonds", true);
    private final BooleanSetting gold = new BooleanSetting("Gold", "Show gold", true);
    private final BooleanSetting iron = new BooleanSetting("Iron", "Show iron", true);
    private final BooleanSetting coal = new BooleanSetting("Coal", "Show coal", false);
    private final BooleanSetting redstone = new BooleanSetting("Redstone", "Show redstone", true);
    private final BooleanSetting lapis = new BooleanSetting("Lapis", "Show lapis", true);
    private final BooleanSetting emerald = new BooleanSetting("Emerald", "Show emeralds", true);
    private final BooleanSetting netherite = new BooleanSetting("Netherite", "Show ancient debris", true);
    private final BooleanSetting copper = new BooleanSetting("Copper", "Show copper", true);

    // Blocks
    private final BooleanSetting granite = new BooleanSetting("Granite", "Show granite", false);
    private final BooleanSetting diorite = new BooleanSetting("Diorite", "Show diorite", false);
    private final BooleanSetting andesite = new BooleanSetting("Andesite", "Show andesite", false);

    // Liquids
    private final BooleanSetting lava = new BooleanSetting("Lava", "Show lava", false);
    private final BooleanSetting water = new BooleanSetting("Water", "Show water", false);

    // Effects
    private final BooleanSetting caveSilhouette = new BooleanSetting("CaveSilhouette", "Show cave outlines", true);
    private final BooleanSetting gammaBoost = new BooleanSetting("GammaBoost", "Boost brightness while active", true);

    private final Set<Block> visibleBlocks = new HashSet<>();
    private final Set<Block> hiddenBlocks = new HashSet<>();
    private double originalGamma = 0.5;
    private boolean storedGamma = false;

    public XRay() {
        super("XRay", "See through blocks with whitelist/blacklist", Category.RENDER);
        INSTANCE = this;
        addSetting(mode);
        addSetting(opacity);
        addSetting(diamonds);
        addSetting(gold);
        addSetting(iron);
        addSetting(coal);
        addSetting(redstone);
        addSetting(lapis);
        addSetting(emerald);
        addSetting(netherite);
        addSetting(copper);
        addSetting(granite);
        addSetting(diorite);
        addSetting(andesite);
        addSetting(lava);
        addSetting(water);
        addSetting(caveSilhouette);
        addSetting(gammaBoost);
    }

    @Override
    public void onEnable() {
        updateVisibleBlocks();
        applyGammaBoost();
        if (mc.levelRenderer != null) {
            mc.levelExtractor.allChanged();
        }
    }

    @Override
    public void onDisable() {
        restoreGamma();
        if (mc.levelRenderer != null) {
            mc.levelExtractor.allChanged();
        }
    }

    @Override
    public void onTick() {
        if (gammaBoost.getValue()) {
            applyGammaBoost();
        }
        Set<Block> previousVisible = new HashSet<>(visibleBlocks);
        Set<Block> previousHidden = new HashSet<>(hiddenBlocks);
        updateVisibleBlocks();
        if (mc.levelRenderer != null
                && (!previousVisible.equals(visibleBlocks) || !previousHidden.equals(hiddenBlocks))) {
            mc.levelExtractor.allChanged();
        }
    }

    public void updateVisibleBlocks() {
        visibleBlocks.clear();
        hiddenBlocks.clear();

        String filterMode = mode.getValue();

        if (filterMode.equals("Whitelist")) {
            // Only show selected blocks
            if (diamonds.getValue()) { visibleBlocks.add(Blocks.DIAMOND_ORE); visibleBlocks.add(Blocks.DEEPSLATE_DIAMOND_ORE); }
            if (gold.getValue()) { visibleBlocks.add(Blocks.GOLD_ORE); visibleBlocks.add(Blocks.DEEPSLATE_GOLD_ORE); visibleBlocks.add(Blocks.NETHER_GOLD_ORE); }
            if (iron.getValue()) { visibleBlocks.add(Blocks.IRON_ORE); visibleBlocks.add(Blocks.DEEPSLATE_IRON_ORE); }
            if (coal.getValue()) { visibleBlocks.add(Blocks.COAL_ORE); visibleBlocks.add(Blocks.DEEPSLATE_COAL_ORE); }
            if (redstone.getValue()) { visibleBlocks.add(Blocks.REDSTONE_ORE); visibleBlocks.add(Blocks.DEEPSLATE_REDSTONE_ORE); }
            if (lapis.getValue()) { visibleBlocks.add(Blocks.LAPIS_ORE); visibleBlocks.add(Blocks.DEEPSLATE_LAPIS_ORE); }
            if (emerald.getValue()) { visibleBlocks.add(Blocks.EMERALD_ORE); visibleBlocks.add(Blocks.DEEPSLATE_EMERALD_ORE); }
            if (netherite.getValue()) { visibleBlocks.add(Blocks.ANCIENT_DEBRIS); }
            if (copper.getValue()) { visibleBlocks.add(Blocks.COPPER_ORE); visibleBlocks.add(Blocks.DEEPSLATE_COPPER_ORE); }
            if (granite.getValue()) { visibleBlocks.add(Blocks.GRANITE); }
            if (diorite.getValue()) { visibleBlocks.add(Blocks.DIORITE); }
            if (andesite.getValue()) { visibleBlocks.add(Blocks.ANDESITE); }
            if (lava.getValue()) { visibleBlocks.add(Blocks.LAVA); }
            if (water.getValue()) { visibleBlocks.add(Blocks.WATER); }
        } else if (filterMode.equals("Blacklist")) {
            // Hide selected blocks - add common stone blocks to hidden list
            populateBlacklistBlocks();
        }
    }

    private void populateBlacklistBlocks() {
        // Common stone/dirt blocks to hide
        hiddenBlocks.add(Blocks.STONE);
        hiddenBlocks.add(Blocks.DIRT);
        hiddenBlocks.add(Blocks.GRASS_BLOCK);
        hiddenBlocks.add(Blocks.COBBLESTONE);
        hiddenBlocks.add(Blocks.OAK_LOG);
        hiddenBlocks.add(Blocks.OAK_LEAVES);
        hiddenBlocks.add(Blocks.SAND);
        hiddenBlocks.add(Blocks.GRAVEL);
        hiddenBlocks.add(Blocks.OAK_PLANKS);
        hiddenBlocks.add(Blocks.DEEPSLATE);

        // Add toggleable blocks to blacklist
        if (granite.getValue()) { hiddenBlocks.add(Blocks.GRANITE); }
        if (diorite.getValue()) { hiddenBlocks.add(Blocks.DIORITE); }
        if (andesite.getValue()) { hiddenBlocks.add(Blocks.ANDESITE); }
    }

    public boolean isVisible(Block block) {
        String filterMode = mode.getValue();

        if (filterMode.equals("Whitelist")) {
            return visibleBlocks.contains(block);
        } else if (filterMode.equals("Blacklist")) {
            return !hiddenBlocks.contains(block);
        }

        return true;
    }
    
    public boolean showCaveSilhouette() {
        return caveSilhouette.getValue();
    }

    public double getOpacity() {
        return opacity.getValue();
    }
    
    public Set<Block> getVisibleBlocks() {
        return new HashSet<>(visibleBlocks);
    }
    
    public void addBlock(Block block) {
        visibleBlocks.add(block);
        if (mc.levelRenderer != null) {
            mc.levelExtractor.allChanged();
        }
    }
    
    public void removeBlock(Block block) {
        visibleBlocks.remove(block);
        if (mc.levelRenderer != null) {
            mc.levelExtractor.allChanged();
        }
    }

    public static boolean isXRay() {
        Module module = ModuleManager.getModuleByName("XRay");
        return module != null && module.isEnabled();
    }

    private void applyGammaBoost() {
        if (!gammaBoost.getValue() || mc.options == null) {
            return;
        }
        if (!storedGamma) {
            originalGamma = mc.options.gamma().get();
            storedGamma = true;
        }
        mc.options.gamma().set(16.0);
    }

    private void restoreGamma() {
        if (mc.options != null && storedGamma) {
            mc.options.gamma().set(originalGamma);
        }
        storedGamma = false;
    }
}
