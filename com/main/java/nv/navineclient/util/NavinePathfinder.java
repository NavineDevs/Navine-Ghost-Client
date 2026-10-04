package nv.navineclient.util;

import nv.navineclient.util.ClientAccess;
import nv.navineclient.module.Module;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.module.misc.BaritoneModule;
import nv.navineclient.mixin.ClientInputAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.entity.player.Input;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;
import nv.navineclient.util.NavineInput;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

public final class NavinePathfinder {
    private static final int MAX_RANGE = 384;
    private static final int MAX_NODES = 24000;
    private static final int SEGMENT_RANGE = 160;
    private static final double ARRIVAL_DISTANCE = 1.2;
    private static final double BREAK_REACH = 4.5;
    private static final double BREAK_COST = 6.0;
    private static final int MINE_SCAN_RADIUS = 48;
    private static final int MINE_SCAN_VERTICAL = 24;

    public enum Task {
        NONE,
        GOTO,
        MINE,
        FOLLOW
    }

    private static Task task = Task.NONE;
    private static boolean active;
    private static boolean bridgeMode;
    private static boolean allowBreak = true;
    private static boolean controllingInput;
    private static BlockPos target;
    private static final List<BlockPos> path = new ArrayList<>();
    private static int pathIndex;
    private static long lastPathCalc;
    private static long stuckSince;
    private static BlockPos lastProgressPos;
    private static boolean pausedByInput;

    private static BlockPos breakingPos;
    private static long breakingSince;

    private static String mineBlockName;
    private static int mineRemaining;
    private static BlockPos mineTargetBlock;
    private static long lastMineScan;

    private static String followPlayerName;
    private static long lastFollowRepath;

    private NavinePathfinder() {
    }

    public static boolean isActive() {
        return active;
    }

    public static Task getTask() {
        return task;
    }

    public static BlockPos getTarget() {
        return target;
    }

    public static int getRemainingWaypoints() {
        if (!active || path.isEmpty()) {
            return 0;
        }
        return Math.max(0, path.size() - pathIndex);
    }

    public static String getStatus() {
        return switch (task) {
            case GOTO -> target != null
                    ? "Goto " + target.getX() + " " + target.getY() + " " + target.getZ()
                    : "Goto";
            case MINE -> "Mine " + mineBlockName + (mineRemaining > 0 ? " (" + mineRemaining + " left)" : "");
            case FOLLOW -> "Follow " + followPlayerName;
            default -> "Idle";
        };
    }

    public static void setBridgeMode(boolean enabled) {
        bridgeMode = enabled;
    }

    public static void setAllowBreak(boolean enabled) {
        allowBreak = enabled;
    }

    public static void gotoPosition(int x, int y, int z) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            ChatUtils.error("Cannot pathfind right now.");
            return;
        }
        resetState();
        target = new BlockPos(x, y, z);
        lastProgressPos = mc.player.blockPosition();
        List<BlockPos> computed = smoothPath(computeLongPath(mc, mc.player.blockPosition(), target));
        if (computed.isEmpty()) {
            resetState();
            ChatUtils.error("No path found to " + x + " " + y + " " + z);
            return;
        }
        path.addAll(computed);
        task = Task.GOTO;
        active = true;
        lastPathCalc = System.currentTimeMillis();
        enablePathfinderModule();
        ChatUtils.success("Pathfinding to " + x + " " + y + " " + z);
    }

    public static void mine(String blockName, int count) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            ChatUtils.error("Cannot mine right now.");
            return;
        }
        String normalized = blockName.toLowerCase(Locale.ROOT).replace("minecraft:", "").trim();
        resetState();
        mineBlockName = normalized;
        mineRemaining = Math.max(count, 0);
        task = Task.MINE;
        active = true;
        lastMineScan = 0;
        enablePathfinderModule();
        ChatUtils.success("Mining " + normalized + (count > 0 ? " x" + count : "") + ".");
    }

    public static void follow(String playerName) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            ChatUtils.error("Cannot follow right now.");
            return;
        }
        AbstractClientPlayer found = findPlayer(mc, playerName);
        if (found == null) {
            ChatUtils.error("Player not found: " + playerName);
            return;
        }
        resetState();
        followPlayerName = found.getGameProfile().name();
        task = Task.FOLLOW;
        active = true;
        enablePathfinderModule();
        ChatUtils.success("Following " + followPlayerName + ".");
    }

    public static void stop() {
        boolean wasActive = active;
        resetState();
        releaseMovementKeys();
        stopBreaking();
        if (wasActive) {
            controllingInput = false;
        }
    }

    private static void resetState() {
        task = Task.NONE;
        active = false;
        controllingInput = false;
        target = null;
        path.clear();
        pathIndex = 0;
        pausedByInput = false;
        stuckSince = 0;
        breakingPos = null;
        mineBlockName = null;
        mineRemaining = 0;
        mineTargetBlock = null;
        followPlayerName = null;
    }

    public static boolean isControllingInput() {
        return controllingInput;
    }

    private static void enablePathfinderModule() {
        Module module = ModuleManager.getModuleByName("Pathfinder");
        if (module != null && !module.isEnabled()) {
            module.setEnabled(true);
        }
    }

    public static void applyInput(LocalPlayer player) {
        if (!active || player == null || path.isEmpty() || pathIndex >= path.size()) {
            controllingInput = false;
            return;
        }
        if (pausedByInput || breakingPos != null) {
            controllingInput = false;
            return;
        }

        BlockPos waypoint = path.get(Math.min(pathIndex, path.size() - 1));
        double dx = waypoint.getX() + 0.5 - player.getX();
        double dz = waypoint.getZ() + 0.5 - player.getZ();
        double dy = waypoint.getY() - player.getY();

        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        AcBypassUtil.applySmoothLook(player, yaw, 18.0f);

        boolean forward = true;
        boolean jump = (dy > 0.4 && player.onGround()) || (player.isInWater() && dy > -0.2);
        boolean shift = dy < -1.2 && player.onGround() && !isSafeDrop(player, waypoint);

        ClientInputAccessor inputAccessor = (ClientInputAccessor) player.input;
        inputAccessor.navine$setKeyPresses(new Input(forward, false, false, false, jump, shift, true));
        inputAccessor.navine$setMoveVector(new Vec2(0.0f, 1.0f));
        controllingInput = true;

        if (!player.isSprinting() && !player.isCrouching() && !player.isInWater()) {
            BaritoneModule pathfinder = BaritoneModule.INSTANCE;
            boolean allowSprint = pathfinder == null || !pathfinder.isEnabled() || pathfinder.shouldSprint();
            if (AcBypassUtil.isLegitActive() && nv.navineclient.module.misc.LegitMode.getProfile()
                    == nv.navineclient.module.misc.LegitMode.Profile.STRICT) {
                allowSprint = false;
            }
            if (allowSprint) {
                player.setSprinting(true);
            }
        }
    }

    private static boolean isSafeDrop(LocalPlayer player, BlockPos waypoint) {
        return player.getY() - waypoint.getY() <= 3.5;
    }

    public static void tick() {
        if (!active) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options == null) {
            stop();
            return;
        }

        releaseMovementKeys();
        if (isManualInputActive(mc)) {
            pausedByInput = true;
            controllingInput = false;
            stopBreaking();
            return;
        }
        if (pausedByInput) {
            pausedByInput = false;
            lastPathCalc = 0;
        }

        switch (task) {
            case GOTO -> tickGoto(mc);
            case MINE -> tickMine(mc);
            case FOLLOW -> tickFollow(mc);
            default -> stop();
        }
    }

    private static void tickGoto(Minecraft mc) {
        if (target == null || path.isEmpty()) {
            stop();
            return;
        }

        double distToTarget = mc.player.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(target));
        if (distToTarget <= ARRIVAL_DISTANCE || pathIndex >= path.size()) {
            stop();
            ChatUtils.success("Arrived at destination.");
            return;
        }

        tickMovement(mc);
    }

    private static void tickMine(Minecraft mc) {
        if (mineBlockName == null) {
            stop();
            return;
        }

        if (mineTargetBlock != null && !blockMatches(mc, mineTargetBlock)) {
            mineTargetBlock = null;
            stopBreaking();
            if (mineRemaining > 0) {
                mineRemaining--;
                if (mineRemaining == 0) {
                    stop();
                    ChatUtils.success("Finished mining " + mineBlockName + ".");
                    return;
                }
            }
        }

        if (mineTargetBlock == null) {
            if (System.currentTimeMillis() - lastMineScan < 500) {
                return;
            }
            lastMineScan = System.currentTimeMillis();
            mineTargetBlock = scanForBlock(mc);
            if (mineTargetBlock == null) {
                stop();
                ChatUtils.error("No " + mineBlockName + " found within " + MINE_SCAN_RADIUS + " blocks.");
                return;
            }
            target = mineTargetBlock;
            path.clear();
            pathIndex = 0;
        }

        Vec3 eye = mc.player.getEyePosition();
        double dist = eye.distanceTo(Vec3.atCenterOf(mineTargetBlock));
        if (dist <= BREAK_REACH) {
            path.clear();
            pathIndex = 0;
            breakBlockAt(mc, mineTargetBlock);
            return;
        }

        if (path.isEmpty() || System.currentTimeMillis() - lastPathCalc > 3000) {
            List<BlockPos> computed = smoothPath(computeLongPath(mc, mc.player.blockPosition(), mineTargetBlock));
            lastPathCalc = System.currentTimeMillis();
            if (computed.isEmpty()) {
                mineTargetBlock = null;
                return;
            }
            path.clear();
            path.addAll(computed);
            pathIndex = 0;
        }

        tickMovement(mc);
    }

    private static void tickFollow(Minecraft mc) {
        AbstractClientPlayer followed = findPlayer(mc, followPlayerName);
        if (followed == null) {
            stop();
            ChatUtils.error("Lost sight of " + followPlayerName + ".");
            return;
        }

        double dist = mc.player.distanceTo(followed);
        if (dist < 2.5) {
            path.clear();
            pathIndex = 0;
            controllingInput = false;
            return;
        }

        if (System.currentTimeMillis() - lastFollowRepath > 1200 || path.isEmpty()) {
            lastFollowRepath = System.currentTimeMillis();
            target = followed.blockPosition();
            List<BlockPos> computed = smoothPath(computeLongPath(mc, mc.player.blockPosition(), target));
            if (!computed.isEmpty()) {
                path.clear();
                path.addAll(computed);
                pathIndex = 0;
            }
        }

        tickMovement(mc);
    }

    private static void tickMovement(Minecraft mc) {
        if (path.isEmpty() || pathIndex >= path.size()) {
            return;
        }

        BlockPos currentPos = mc.player.blockPosition();
        if (!currentPos.equals(lastProgressPos)) {
            lastProgressPos = currentPos;
            stuckSince = 0;
        } else if (breakingPos == null) {
            if (stuckSince == 0) {
                stuckSince = System.currentTimeMillis();
            } else if (System.currentTimeMillis() - stuckSince > 2000) {
                repath(mc);
                stuckSince = 0;
            }
        }

        if (task == Task.GOTO && System.currentTimeMillis() - lastPathCalc > 4000) {
            repath(mc);
            lastPathCalc = System.currentTimeMillis();
        }

        BlockPos waypoint = path.get(pathIndex);
        double dx = waypoint.getX() + 0.5 - mc.player.getX();
        double dz = waypoint.getZ() + 0.5 - mc.player.getZ();
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        if (horizontalDist < 0.75 && Math.abs(waypoint.getY() - mc.player.getY()) < 1.6) {
            pathIndex++;
            if (pathIndex >= path.size()) {
                return;
            }
            waypoint = path.get(pathIndex);
            dx = waypoint.getX() + 0.5 - mc.player.getX();
            dz = waypoint.getZ() + 0.5 - mc.player.getZ();
            horizontalDist = Math.sqrt(dx * dx + dz * dz);
        }

        if (allowBreak && handleObstruction(mc, waypoint)) {
            return;
        }
        if (breakingPos != null) {
            stopBreaking();
        }

        if (bridgeMode && mc.player.onGround() && horizontalDist > 0.8) {
            tryBridgeGap(mc, waypoint);
        }
    }

    private static boolean handleObstruction(Minecraft mc, BlockPos waypoint) {
        BlockPos feet = waypoint;
        BlockPos head = waypoint.above();
        BlockPos obstruction = null;
        if (isBreakable(mc, head) && !isPassable(mc, head)) {
            obstruction = head;
        } else if (isBreakable(mc, feet) && !isPassable(mc, feet)) {
            obstruction = feet;
        } else {
            BlockPos playerHead = mc.player.blockPosition().above();
            if (isBreakable(mc, playerHead) && !isPassable(mc, playerHead)) {
                obstruction = playerHead;
            }
        }
        if (obstruction == null) {
            return false;
        }
        Vec3 eye = mc.player.getEyePosition();
        if (eye.distanceTo(Vec3.atCenterOf(obstruction)) > AcBypassUtil.clampBlockReach(BREAK_REACH)) {
            return false;
        }
        breakBlockAt(mc, obstruction);
        return true;
    }

    private static void breakBlockAt(Minecraft mc, BlockPos pos) {
        int delay = AcBypassUtil.legitMiningDelayTicks();
        if (delay > 0 && mc.player.tickCount % (delay + 1) != 0) {
            return;
        }
        lookAt(mc.player, Vec3.atCenterOf(pos));
        Direction face = getFacingDirection(mc.player.getEyePosition(), pos);
        if (breakingPos == null || !breakingPos.equals(pos)) {
            breakingPos = pos;
            breakingSince = System.currentTimeMillis();
            mc.gameMode.startDestroyBlock(pos, face);
        } else {
            mc.gameMode.continueDestroyBlock(pos, face);
        }
        ClientAccess.swing(mc.player, InteractionHand.MAIN_HAND);

        if (System.currentTimeMillis() - breakingSince > 15000) {
            stopBreaking();
            if (task == Task.MINE && pos.equals(mineTargetBlock)) {
                mineTargetBlock = null;
            }
        }
    }

    private static void stopBreaking() {
        if (breakingPos != null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gameMode != null) {
                mc.gameMode.stopDestroyBlock();
            }
            breakingPos = null;
        }
    }

    private static void lookAt(LocalPlayer player, Vec3 pos) {
        Vec3 eye = player.getEyePosition();
        double dx = pos.x - eye.x;
        double dy = pos.y - eye.y;
        double dz = pos.z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        float[] rotated = AcBypassUtil.smoothRotation(player.getYRot(), player.getXRot(), yaw, pitch, 22.0f);
        player.setYRot(rotated[0]);
        player.setXRot(rotated[1]);
    }

    private static Direction getFacingDirection(Vec3 eye, BlockPos pos) {
        Vec3 diff = Vec3.atCenterOf(pos).subtract(eye);
        return Direction.getApproximateNearest(diff.x, diff.y, diff.z);
    }

    private static boolean blockMatches(Minecraft mc, BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        String name = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return name.equals(mineBlockName) || name.contains(mineBlockName);
    }

    private static BlockPos scanForBlock(Minecraft mc) {
        BlockPos center = mc.player.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        int minY = Math.max(mc.level.getMinY(), center.getY() - MINE_SCAN_VERTICAL);
        int maxY = Math.min(mc.level.getMaxY(), center.getY() + MINE_SCAN_VERTICAL);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int r = 0; r <= MINE_SCAN_RADIUS; r += 8) {
            for (int x = center.getX() - r - 8; x <= center.getX() + r + 8; x++) {
                for (int z = center.getZ() - r - 8; z <= center.getZ() + r + 8; z++) {
                    for (int y = minY; y <= maxY; y++) {
                        cursor.set(x, y, z);
                        if (!blockMatches(mc, cursor)) {
                            continue;
                        }
                        double dist = cursor.distSqr(center);
                        if (dist < bestDist) {
                            bestDist = dist;
                            best = cursor.immutable();
                        }
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        return best;
    }

    private static AbstractClientPlayer findPlayer(Minecraft mc, String name) {
        if (name == null || mc.level == null) {
            return null;
        }
        for (AbstractClientPlayer player : mc.level.players()) {
            if (player == mc.player) {
                continue;
            }
            if (player.getGameProfile().name().equalsIgnoreCase(name)) {
                return player;
            }
        }
        return null;
    }

    private static List<BlockPos> computeLongPath(Minecraft mc, BlockPos start, BlockPos goal) {
        if (start.distManhattan(goal) <= MAX_RANGE) {
            return computePath(mc, start, goal);
        }
        List<BlockPos> fullPath = new ArrayList<>();
        BlockPos current = start;
        int safety = 0;
        while (current.distManhattan(goal) > MAX_RANGE && safety++ < 32) {
            BlockPos segmentGoal = stepToward(current, goal, SEGMENT_RANGE);
            List<BlockPos> segment = computePath(mc, current, segmentGoal);
            if (segment.isEmpty()) {
                break;
            }
            fullPath.addAll(segment);
            current = segment.get(segment.size() - 1);
        }
        List<BlockPos> finalSegment = computePath(mc, current, goal);
        if (!finalSegment.isEmpty()) {
            fullPath.addAll(finalSegment);
        }
        return fullPath;
    }

    private static BlockPos stepToward(BlockPos from, BlockPos to, int maxStep) {
        int dx = Integer.signum(to.getX() - from.getX());
        int dy = Integer.signum(to.getY() - from.getY());
        int dz = Integer.signum(to.getZ() - from.getZ());
        int x = from.getX();
        int y = from.getY();
        int z = from.getZ();
        for (int i = 0; i < maxStep; i++) {
            if (x == to.getX() && y == to.getY() && z == to.getZ()) {
                break;
            }
            if (x != to.getX()) {
                x += dx;
            }
            if (y != to.getY()) {
                y += dy;
            }
            if (z != to.getZ()) {
                z += dz;
            }
        }
        return new BlockPos(x, y, z);
    }

    private static void repath(Minecraft mc) {
        BlockPos goal = task == Task.MINE && mineTargetBlock != null ? mineTargetBlock : target;
        if (goal == null) {
            return;
        }
        List<BlockPos> recomputed = smoothPath(computeLongPath(mc, mc.player.blockPosition(), goal));
        if (!recomputed.isEmpty()) {
            path.clear();
            path.addAll(recomputed);
            pathIndex = 0;
        }
    }

    private static boolean isManualInputActive(Minecraft mc) {
        if (controllingInput) {
            return false;
        }
        long handle = mc.getWindow().handle();
        return NavineInput.isKeyDown(NavineInput.KEY_S);
    }

    private static void tryBridgeGap(Minecraft mc, BlockPos waypoint) {
        BlockPos below = mc.player.blockPosition().below();
        BlockState belowState = mc.level.getBlockState(below);
        if (!belowState.isAir() && !belowState.canBeReplaced()) {
            return;
        }
        for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN}) {
            BlockPos neighbor = below.relative(dir);
            BlockState neighborState = mc.level.getBlockState(neighbor);
            if (!neighborState.isAir() && !neighborState.canBeReplaced()) {
                int slot = findBlockSlot(mc);
                if (slot == -1) {
                    return;
                }
                int oldSlot = mc.player.getInventory().getSelectedSlot();
                mc.player.getInventory().setSelectedSlot(slot);
                Vec3 hitVec = Vec3.atCenterOf(neighbor).add(
                        dir.getOpposite().getStepX() * 0.5,
                        dir.getOpposite().getStepY() * 0.5,
                        dir.getOpposite().getStepZ() * 0.5
                );
                BlockHitResult hit = new BlockHitResult(hitVec, dir.getOpposite(), neighbor, false);
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
                mc.player.getInventory().setSelectedSlot(oldSlot);
                return;
            }
        }
    }

    private static int findBlockSlot(Minecraft mc) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() instanceof BlockItem) {
                return i;
            }
        }
        return -1;
    }

    private static List<BlockPos> smoothPath(List<BlockPos> raw) {
        if (raw.size() <= 2) {
            return raw;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return raw;
        }
        List<BlockPos> smoothed = new ArrayList<>();
        smoothed.add(raw.get(0));
        int anchor = 0;
        for (int i = 2; i < raw.size(); i++) {
            if (!hasWalkableLine(mc, raw.get(anchor), raw.get(i))) {
                smoothed.add(raw.get(i - 1));
                anchor = i - 1;
            }
        }
        smoothed.add(raw.get(raw.size() - 1));
        return smoothed;
    }

    private static boolean hasWalkableLine(Minecraft mc, BlockPos from, BlockPos to) {
        int steps = from.distManhattan(to);
        if (steps <= 1) {
            return true;
        }
        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            BlockPos sample = new BlockPos(
                    (int) Math.round(from.getX() + (to.getX() - from.getX()) * t),
                    (int) Math.round(from.getY() + (to.getY() - from.getY()) * t),
                    (int) Math.round(from.getZ() + (to.getZ() - from.getZ()) * t)
            );
            if (!isWalkable(mc, sample)) {
                return false;
            }
        }
        return true;
    }

    private static void releaseMovementKeys() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) {
            return;
        }
        mc.options.keyUp.setDown(false);
        mc.options.keyDown.setDown(false);
        mc.options.keyLeft.setDown(false);
        mc.options.keyRight.setDown(false);
        mc.options.keyJump.setDown(false);
        mc.options.keyShift.setDown(false);
    }

    private static List<BlockPos> computePath(Minecraft mc, BlockPos start, BlockPos goal) {
        if (start.distManhattan(goal) > MAX_RANGE * 8) {
            return Collections.emptyList();
        }

        BlockPos alignedGoal = findWalkableNear(mc, goal);
        if (alignedGoal == null) {
            alignedGoal = goal;
        }

        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(n -> n.fScore));
        Map<BlockPos, Node> allNodes = new HashMap<>();
        Set<BlockPos> closed = new HashSet<>();

        Node startNode = new Node(start, null, 0, heuristic(start, alignedGoal));
        open.add(startNode);
        allNodes.put(start, startNode);

        int explored = 0;
        while (!open.isEmpty() && explored < MAX_NODES) {
            Node current = open.poll();
            if (current == null) {
                break;
            }
            if (current.pos.equals(alignedGoal) || current.pos.distManhattan(alignedGoal) <= 1) {
                return reconstruct(current);
            }
            if (closed.contains(current.pos)) {
                continue;
            }
            closed.add(current.pos);
            explored++;

            for (Neighbor neighbor : getNeighbors(mc, current.pos)) {
                if (closed.contains(neighbor.pos)) {
                    continue;
                }
                if (neighbor.pos.distManhattan(start) > MAX_RANGE) {
                    continue;
                }
                double tentative = current.gScore + neighbor.cost;
                Node existing = allNodes.get(neighbor.pos);
                if (existing == null || tentative < existing.gScore) {
                    Node next = new Node(neighbor.pos, current, tentative, tentative + heuristic(neighbor.pos, alignedGoal));
                    allNodes.put(neighbor.pos, next);
                    open.add(next);
                }
            }
        }
        return Collections.emptyList();
    }

    private static double heuristic(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz) + Math.abs(dy) * 1.2;
    }

    private static List<BlockPos> reconstruct(Node node) {
        List<BlockPos> result = new ArrayList<>();
        Node current = node;
        while (current != null) {
            result.add(current.pos);
            current = current.parent;
        }
        Collections.reverse(result);
        if (result.size() > 1) {
            result.remove(0);
        }
        return result;
    }

    private static List<Neighbor> getNeighbors(Minecraft mc, BlockPos pos) {
        List<Neighbor> neighbors = new ArrayList<>(28);
        int[][] horizontal = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] offset : horizontal) {
            BlockPos same = pos.offset(offset[0], 0, offset[1]);
            if (isWalkable(mc, same)) {
                neighbors.add(new Neighbor(same, 1.0));
            } else if (allowBreak && isTraversableByBreaking(mc, same)) {
                neighbors.add(new Neighbor(same, 1.0 + breakCostFor(mc, same)));
            }

            BlockPos up = pos.offset(offset[0], 1, offset[1]);
            if (isWalkable(mc, up) && hasSolidBelow(mc, up) && isPassable(mc, pos.above(2))) {
                neighbors.add(new Neighbor(up, 2.0));
            }

            BlockPos down = pos.offset(offset[0], -1, offset[1]);
            if (isWalkable(mc, down)) {
                neighbors.add(new Neighbor(down, 1.5));
            }

            BlockPos gap = pos.offset(offset[0] * 2, 0, offset[1] * 2);
            BlockPos mid = pos.offset(offset[0], -1, offset[1]);
            if (isPassable(mc, mid) && isPassable(mc, gap.below()) && isWalkable(mc, gap) && hasSolidBelow(mc, gap)) {
                neighbors.add(new Neighbor(gap, 2.5));
            }

            for (int drop = 2; drop <= 3; drop++) {
                BlockPos landing = pos.offset(offset[0], -drop, offset[1]);
                if (isWalkable(mc, landing) && hasSolidBelow(mc, landing) && columnClear(mc, pos.offset(offset[0], 0, offset[1]), drop)) {
                    neighbors.add(new Neighbor(landing, 2.0 + drop * 0.5));
                }
            }
        }

        int[][] diagonal = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        for (int[] offset : diagonal) {
            BlockPos diag = pos.offset(offset[0], 0, offset[1]);
            BlockPos sideA = pos.offset(offset[0], 0, 0);
            BlockPos sideB = pos.offset(0, 0, offset[1]);
            if (isWalkable(mc, diag) && isPassable(mc, sideA) && isPassable(mc, sideA.above())
                    && isPassable(mc, sideB) && isPassable(mc, sideB.above())) {
                neighbors.add(new Neighbor(diag, 1.42));
            }
        }

        return neighbors;
    }

    private static boolean columnClear(Minecraft mc, BlockPos top, int depth) {
        for (int i = 0; i < depth; i++) {
            if (!isPassable(mc, top.below(i)) || !isPassable(mc, top.above().below(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isTraversableByBreaking(Minecraft mc, BlockPos pos) {
        boolean feetOk = isPassable(mc, pos) || isBreakable(mc, pos);
        boolean headOk = isPassable(mc, pos.above()) || isBreakable(mc, pos.above());
        return feetOk && headOk && hasSolidBelow(mc, pos);
    }

    private static double breakCostFor(Minecraft mc, BlockPos pos) {
        double cost = 0;
        if (!isPassable(mc, pos)) {
            cost += BREAK_COST;
        }
        if (!isPassable(mc, pos.above())) {
            cost += BREAK_COST;
        }
        return cost;
    }

    private static boolean isBreakable(Minecraft mc, BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir() || state.liquid()) {
            return false;
        }
        float hardness = state.getDestroySpeed(mc.level, pos);
        return hardness >= 0 && hardness < 50;
    }

    private static BlockPos findWalkableNear(Minecraft mc, BlockPos goal) {
        if (isWalkable(mc, goal)) {
            return goal;
        }
        for (int radius = 1; radius <= 4; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -3; dy <= 3; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        BlockPos candidate = goal.offset(dx, dy, dz);
                        if (isWalkable(mc, candidate)) {
                            return candidate;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static boolean isWalkable(Minecraft mc, BlockPos pos) {
        if (!isPassable(mc, pos) || !isPassable(mc, pos.above())) {
            return false;
        }
        return hasSolidBelow(mc, pos) || isPassable(mc, pos.below());
    }

    private static boolean hasSolidBelow(Minecraft mc, BlockPos pos) {
        BlockState below = mc.level.getBlockState(pos.below());
        return !below.isAir() && !below.canBeReplaced() && !below.liquid();
    }

    private static boolean isPassable(Minecraft mc, BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        if (state.liquid()) {
            return true;
        }
        return state.isAir() || state.canBeReplaced();
    }

    private static final class Node {
        final BlockPos pos;
        final Node parent;
        final double gScore;
        final double fScore;

        Node(BlockPos pos, Node parent, double gScore, double fScore) {
            this.pos = pos;
            this.parent = parent;
            this.gScore = gScore;
            this.fScore = fScore;
        }
    }

    private record Neighbor(BlockPos pos, double cost) {
    }
}
