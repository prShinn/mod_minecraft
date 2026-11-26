package com.example.ai.builder;

import com.example.ai.worker.DepositCargoGoal;
import com.example.block.BlueprintBlockEntity;
import com.example.build.BuildMaterial;
import com.example.build.BuildSiteState;
import com.example.build.StructurePlan;
import com.example.config.ModConfig;
import com.example.ai.DeliveryTarget;
import com.example.entity.BuilderNpcEntity;
import com.example.helper.NpcFeedback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Chu trình của Thợ xây trên 1 công trình (block bản vẽ):
 * PLAN (quyết định) → GATHER (lấy vật liệu ở rương) / BUILD (đặt từng block) / WAIT (thiếu vật liệu, đứng chờ).
 * Tiến độ lưu ở BlueprintBlockEntity nên đổi NPC hoặc nạp lại world không mất. Khi xong, block bản vẽ
 * được thay bằng block cuối của công trình và đồ thừa được nộp lại rương.
 */
public class BuildStructureGoal extends Goal {
    private enum Phase { PLAN, GATHER, BUILD, WAIT }

    /** Tầm với khi đặt block (bình phương). */
    /** Tầm đặt block từ chỗ đứng trước công trình — đủ tới nóc phía sau Nhà dân lớn 2 tầng (~16 block). */
    private static final double REACH_SQ = 18.0 * 18.0;
    private static final int LEG_TIMEOUT_TICKS = 20 * 40;
    private static final int SCAN_INTERVAL_TICKS = 40;
    private static final int WAIT_RECHECK_TICKS = 40;
    private static final int DEFER_TICKS = 100;
    /** Ô bị vật cản quá số lần này thì bỏ qua (giống block lạ), tránh kẹt vô hạn. */
    private static final int MAX_DEFERS = 5;

    private final BuilderNpcEntity npc;
    private BlockPos site;
    private Phase phase = Phase.PLAN;
    private int scanCooldown;
    private int legTicks;
    private int placeCooldown;
    private int repathCooldown;
    private long waitUntil;
    private String waitMessage = "";
    private final Map<Integer, Long> deferred = new HashMap<>();
    private final Map<Integer, Integer> deferCount = new HashMap<>();
    /** Đang chờ vì thiếu vật liệu (khác với chờ vì vật cản) → được phép chuyển sang công trình khác. */
    private boolean starved;

    public BuildStructureGoal(BuilderNpcEntity npc) {
        this.npc = npc;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    // ===== Vòng đời goal =====

    @Override
    public boolean canStart() {
        if (!(npc.getWorld() instanceof ServerWorld world) || npc.sleeping.isSleeping()) return false;
        if (scanCooldown-- > 0) return false;
        scanCooldown = SCAN_INTERVAL_TICKS;

        BlockPos chest = npc.findWorkChest();
        if (chest == null) return false;
        BlockPos found = claimSite(world, chest);
        if (found == null) {
            // Không còn công trình (xong / bị phá): nộp lại vật liệu đang mang
            if (!npc.getCargo().isEmpty() && !npc.needsToDeposit()) npc.setNeedsToDeposit(true);
            return false;
        }
        site = found;
        return true;
    }

    @Override
    public boolean shouldContinue() {
        return site != null && !npc.sleeping.isSleeping();
    }

    @Override
    public void start() {
        phase = Phase.PLAN;
        legTicks = 0;
        placeCooldown = 0;
        waitUntil = 0;
        starved = false;
        deferred.clear();
        deferCount.clear();
    }

    @Override
    public void stop() {
        npc.getNavigation().stop();
        if (site != null && npc.getWorld().isChunkLoaded(site)
                && npc.getWorld().getBlockEntity(site) instanceof BlueprintBlockEntity bp) {
            bp.release(npc.getUuid());
        }
        site = null;
        deferred.clear();
        deferCount.clear();
    }

    /** Nhãn hiển thị trên đầu NPC. */
    public String label() {
        return switch (phase) {
            case GATHER -> "Đang lấy vật liệu";
            case WAIT -> waitMessage.isEmpty() ? "Đang chờ vật liệu" : waitMessage;
            default -> "Đang xây dựng";
        };
    }

    /** Công trình gần rương nhất của chủ mà chưa NPC nào khác giữ; nhận claim luôn. */
    private BlockPos claimSite(ServerWorld world, BlockPos chest) {
        List<BlockPos> sites = BuildSiteState.get(world)
                .sitesFor(world, npc.getOwnerUUID(), chest, ModConfig.get().builderWorkRadius);
        long now = world.getTime();
        for (BlockPos pos : sites) {
            if (world.isChunkLoaded(pos) && world.getBlockEntity(pos) instanceof BlueprintBlockEntity bp
                    && bp.claim(npc.getUuid(), now)) {
                return pos;
            }
        }
        return null;
    }

    // ===== Tick =====

    @Override
    public void tick() {
        if (site == null || !(npc.getWorld() instanceof ServerWorld world)) return;
        if (!world.isChunkLoaded(site) || !(world.getBlockEntity(site) instanceof BlueprintBlockEntity bp)) {
            site = null; // công trình bị phá hoặc chunk chưa nạp → kết thúc, canStart sẽ tìm lại
            return;
        }
        if (!bp.claim(npc.getUuid(), world.getTime())) {
            site = null; // NPC khác đã nhận
            return;
        }
        switch (phase) {
            case PLAN -> plan(world, bp);
            case GATHER -> gather(world, bp);
            case BUILD -> build(world, bp);
            case WAIT -> waitForMaterials(world, bp);
        }
    }

    private void plan(ServerWorld world, BlueprintBlockEntity bp) {
        if (bp.remaining() == 0) { // công trình không có block ở ô bản vẽ → dọn ô bản vẽ
            finish(world, bp, Blocks.AIR.getDefaultState());
            return;
        }
        BlockPos chest = npc.findWorkChest();
        if (chest == null) {
            enterWait(world, "Không tìm thấy rương thợ xây", WAIT_RECHECK_TICKS * 2);
            return;
        }
        if (pickStep(world, bp, false) >= 0) {
            phase = Phase.BUILD;
            legTicks = 0;
            return;
        }
        int[] need = bp.remainingNeed();
        int[] have = carried();
        // Lấy thêm vật liệu trước: có thể mở khoá các bước khác trong khi ô vướng vật cản đang chờ
        if (world.getBlockEntity(chest) instanceof Inventory inv && chestHasNeeded(inv, need, have)) {
            phase = Phase.GATHER;
            legTicks = 0;
            repathCooldown = 0;
            return;
        }
        if (pickStep(world, bp, true) >= 0) {
            // Còn việc nhưng ô đang bị vật cản (người/mob/giá đỡ...) → chờ ngắn, báo lý do
            enterWait(world, "Có vật cản trong công trình", 20);
            return;
        }
        enterWait(world, "Thiếu vật liệu: " + missingText(need, have), WAIT_RECHECK_TICKS);
        starved = true;
    }

    private void gather(ServerWorld world, BlueprintBlockEntity bp) {
        BlockPos chest = npc.findWorkChest();
        if (chest == null || !(world.getBlockEntity(chest) instanceof Inventory inv)) {
            phase = Phase.PLAN;
            return;
        }
        if (DeliveryTarget.canReach(npc, chest)) {
            npc.getNavigation().stop();
            npc.getLookControl().lookAt(chest.getX() + 0.5, chest.getY() + 0.5, chest.getZ() + 0.5);
            int moved = withdraw(inv, bp.remainingNeed(), carried());
            if (moved == 0) {
                enterWait(world, "Túi đồ đầy, không lấy thêm được vật liệu", WAIT_RECHECK_TICKS * 2);
            } else {
                NpcFeedback.success(npc);
                phase = Phase.PLAN;
            }
            return;
        }
        if (++legTicks > LEG_TIMEOUT_TICKS) {
            enterWait(world, "Không có đường tới rương thợ xây", WAIT_RECHECK_TICKS * 2);
            return;
        }
        if (repathCooldown > 0) repathCooldown--;
        if (npc.getNavigation().isIdle() && repathCooldown == 0) moveTo(chest);
    }

    private void build(ServerWorld world, BlueprintBlockEntity bp) {
        if (placeCooldown > 0) {
            placeCooldown--;
            return;
        }
        int idx = pickStep(world, bp, false);
        if (idx < 0) {
            phase = Phase.PLAN;
            return;
        }
        StructurePlan plan = bp.plan();
        StructurePlan.Step step = plan.steps().get(idx);
        BlockPos cell = plan.toWorld(site, bp.facing(), step);

        if (npc.squaredDistanceTo(Vec3d.ofCenter(cell)) > REACH_SQ) {
            // Đi tới điểm đứng ở mặt trước công trình
            if (++legTicks > LEG_TIMEOUT_TICKS) {
                npc.display.report(npc, "Không tới được công trình");
                enterWait(world, "Không tới được công trình", WAIT_RECHECK_TICKS * 3);
                return;
            }
            if (repathCooldown > 0) repathCooldown--;
            if (npc.getNavigation().isIdle() && repathCooldown == 0) moveTo(plan.standPos(site, bp.facing()));
            return;
        }
        legTicks = 0;
        if (npc.getBoundingBox().intersects(new Box(cell))) {
            // NPC đang đứng trong ô cần đặt → bước ra điểm đứng ngoài công trình, làm ô khác trước
            defer(world, idx);
            moveTo(plan.standPos(site, bp.facing()));
            return;
        }
        if (!npc.getNavigation().isIdle() && npc.squaredDistanceTo(Vec3d.ofCenter(cell)) < REACH_SQ * 0.5) {
            npc.getNavigation().stop();
        }
        npc.getLookControl().lookAt(cell.getX() + 0.5, cell.getY() + 0.5, cell.getZ() + 0.5);
        placeStep(world, bp, idx, step, cell);
        placeCooldown = ModConfig.get().builderPlaceIntervalTicks;
    }

    private void waitForMaterials(ServerWorld world, BlueprintBlockEntity bp) {
        BlockPos chest = npc.findWorkChest();
        if (chest != null && !DeliveryTarget.canReach(npc, chest) && npc.getNavigation().isIdle()
                && repathCooldown-- <= 0) {
            moveTo(chest); // đứng chờ cạnh rương
        }
        long now = world.getTime();
        if (!waitMessage.isEmpty() && now % 60 == 0) npc.display.report(npc, waitMessage);
        if (now < waitUntil) return;
        if (starved && chest != null) {
            // Công trình này thiếu vật liệu: nếu có công trình khác làm được ngay thì chuyển sang đó
            BlockPos alt = findFeasibleSite(world, chest);
            if (alt != null) {
                bp.release(npc.getUuid());
                site = alt;
                deferred.clear();
                deferCount.clear();
            }
        }
        starved = false;
        phase = Phase.PLAN;
    }

    /** Công trình khác của chủ mà túi đồ hoặc rương có vật liệu để làm tiếp; nhận claim luôn. */
    private BlockPos findFeasibleSite(ServerWorld world, BlockPos chest) {
        if (!(world.getBlockEntity(chest) instanceof Inventory inv)) return null;
        boolean[] inChest = new boolean[BuildMaterial.values().length];
        for (int i = 0; i < inv.size(); i++) {
            BuildMaterial m = BuildMaterial.of(inv.getStack(i));
            if (m != null) inChest[m.ordinal()] = true;
        }
        int[] have = carried();
        long now = world.getTime();
        for (BlockPos pos : BuildSiteState.get(world)
                .sitesFor(world, npc.getOwnerUUID(), chest, ModConfig.get().builderWorkRadius)) {
            if (pos.equals(site) || !world.isChunkLoaded(pos)
                    || !(world.getBlockEntity(pos) instanceof BlueprintBlockEntity other)) continue;
            int[] need = other.remainingNeed();
            boolean feasible = false;
            for (int m = 0; m < need.length; m++) {
                if (need[m] > 0 && (have[m] > 0 || inChest[m])) {
                    feasible = true;
                    break;
                }
            }
            if (feasible && other.claim(npc.getUuid(), now)) return pos;
        }
        return null;
    }

    // ===== Đặt block =====

    private void placeStep(ServerWorld world, BlueprintBlockEntity bp, int idx, StructurePlan.Step step, BlockPos cell) {
        SimpleInventory cargo = npc.getCargo();
        int slot = findSlot(cargo, step.material());
        if (slot < 0) {
            phase = Phase.PLAN;
            return;
        }
        ItemStack stack = cargo.getStack(slot);
        BlockState place = BuildMaterial.stateFor(stack);
        if (place == null) {
            defer(world, idx);
            return;
        }
        place = StructurePlan.orient(place, step, bp.facing()); // cầu thang/cửa/phiến/thân gỗ đúng hướng
        boolean door = place.getBlock() instanceof net.minecraft.block.DoorBlock;
        boolean isOrigin = idx == bp.plan().originIndex();
        if (!world.isChunkLoaded(cell)) {
            defer(world, idx);
            return;
        }
        if (!world.isInBuildLimit(cell) || !world.getWorldBorder().contains(cell)) {
            if (!isOrigin) bp.markDone(idx); // ngoài giới hạn thế giới → bỏ qua, không tốn vật liệu
            return;
        }
        if (!isOrigin) {
            BlockState existing = world.getBlockState(cell);
            if (existing.isOf(place.getBlock())) { // đã đúng loại → không tốn vật liệu
                bp.markDone(idx);
                return;
            }
            if (!existing.isAir() && !existing.isReplaceable()) { // block của người chơi/địa hình → bỏ qua, không phá
                bp.markDone(idx);
                return;
            }
        }
        if (!world.canPlace(place, cell, ShapeContext.absent())) {
            // Có entity đứng chắn: thử lại vài lần, quá số lần thì bỏ qua ô (riêng ô bản vẽ thì đặt đè)
            if (deferCount.merge(idx, 1, Integer::sum) < MAX_DEFERS) {
                defer(world, idx);
                return;
            }
            if (!isOrigin) {
                bp.markDone(idx);
                return;
            }
        }
        if (isOrigin) {
            stack.decrement(1);
            if (!finish(world, bp, place)) {
                stack.increment(1);
                defer(world, idx);
            }
            return;
        }
        if (door) {
            // Cửa 2 ô: ô trên phải trống, không thì bỏ qua (không tốn cửa)
            BlockState above = world.getBlockState(cell.up());
            if (!above.isAir() && !above.isReplaceable()) {
                bp.markDone(idx);
                return;
            }
        }
        if (!world.setBlockState(cell, place, Block.NOTIFY_ALL)) {
            defer(world, idx); // đặt thất bại → giữ nguyên vật liệu, thử lại sau
            return;
        }
        if (door) {
            world.setBlockState(cell.up(), place.with(net.minecraft.state.property.Properties.DOUBLE_BLOCK_HALF,
                    net.minecraft.block.enums.DoubleBlockHalf.UPPER), Block.NOTIFY_ALL);
        }
        stack.decrement(1);
        bp.markDone(idx);
        world.playSound(null, cell, place.getSoundGroup().getPlaceSound(), SoundCategory.BLOCKS, 0.8F, 0.9F);
        npc.swingHand(Hand.MAIN_HAND);
    }

    /** Xong công trình: thay block bản vẽ bằng block cuối, báo chủ, nộp đồ thừa. */
    private boolean finish(ServerWorld world, BlueprintBlockEntity bp, BlockState finalState) {
        BlockPos pos = site;
        String name = bp.plan().name();
        if (!world.setBlockState(pos, finalState, Block.NOTIFY_ALL)) return false;
        BuildSiteState.get(world).remove(pos);
        world.playSound(null, pos, finalState.getSoundGroup().getPlaceSound(), SoundCategory.BLOCKS, 1.0F, 0.8F);
        NpcFeedback.success(npc);
        if (npc.getOwnerUUID() != null) {
            PlayerEntity owner = world.getPlayerByUuid(npc.getOwnerUUID());
            if (owner != null) owner.sendMessage(Text.literal("Thợ xây đã hoàn thành: " + name), true);
        }
        site = null; // shouldContinue() kết thúc goal
        if (!npc.getCargo().isEmpty()) npc.setNeedsToDeposit(true);
        return true;
    }

    // ===== Chọn bước / vật liệu =====

    /**
     * Bước chưa xong, có vật liệu trong túi, thấp nhất trước. Ô bản vẽ chỉ khi mọi bước khác đã xong.
     * ignoreDeferred=true: bỏ qua việc "bị vật cản tạm hoãn" để biết còn việc hay không.
     */
    private int pickStep(ServerWorld world, BlueprintBlockEntity bp, boolean ignoreDeferred) {
        StructurePlan plan = bp.plan();
        List<StructurePlan.Step> steps = plan.steps();
        int[] have = carried();
        int origin = plan.originIndex();
        boolean onlyOriginLeft = bp.remaining() == 1;
        long now = world.getTime();
        for (int i = 0; i < steps.size(); i++) {
            if (bp.isDone(i)) continue;
            if (i == origin && !onlyOriginLeft) continue;
            if (have[steps.get(i).material().ordinal()] <= 0) continue;
            if (!ignoreDeferred && deferred.getOrDefault(i, 0L) > now) continue;
            return i;
        }
        return -1;
    }

    private void defer(ServerWorld world, int idx) {
        deferred.put(idx, world.getTime() + DEFER_TICKS);
    }

    private int[] carried() {
        int[] have = new int[BuildMaterial.values().length];
        SimpleInventory cargo = npc.getCargo();
        for (int i = 0; i < cargo.size(); i++) {
            ItemStack stack = cargo.getStack(i);
            BuildMaterial m = BuildMaterial.of(stack);
            if (m != null) have[m.ordinal()] += stack.getCount();
        }
        return have;
    }

    private static int findSlot(SimpleInventory cargo, BuildMaterial material) {
        for (int i = 0; i < cargo.size(); i++) {
            if (material.matches(cargo.getStack(i))) return i;
        }
        return -1;
    }

    private static boolean chestHasNeeded(Inventory chest, int[] need, int[] have) {
        for (int i = 0; i < chest.size(); i++) {
            BuildMaterial m = BuildMaterial.of(chest.getStack(i));
            if (m != null && need[m.ordinal()] - have[m.ordinal()] > 0) return true;
        }
        return false;
    }

    /** Lấy từ rương phần vật liệu còn thiếu; trả về tổng số món đã chuyển. */
    private int withdraw(Inventory chest, int[] need, int[] have) {
        int movedTotal = 0;
        for (int slot = 0; slot < chest.size(); slot++) {
            ItemStack stack = chest.getStack(slot);
            BuildMaterial m = BuildMaterial.of(stack);
            if (m == null) continue;
            int want = Math.min(need[m.ordinal()] - have[m.ordinal()], stack.getCount());
            if (want <= 0) continue;
            ItemStack rest = DepositCargoGoal.insert(npc.getCargo(), stack.copyWithCount(want));
            int moved = want - rest.getCount();
            if (moved > 0) {
                chest.removeStack(slot, moved);
                have[m.ordinal()] += moved;
                movedTotal += moved;
            }
        }
        return movedTotal;
    }

    private static String missingText(int[] need, int[] have) {
        StringBuilder sb = new StringBuilder();
        for (BuildMaterial m : BuildMaterial.values()) {
            int missing = need[m.ordinal()] - have[m.ordinal()];
            if (missing <= 0) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(m.displayName()).append(" ×").append(missing);
        }
        return sb.toString();
    }

    // ===== Tiện ích =====

    private void enterWait(ServerWorld world, String message, int ticks) {
        phase = Phase.WAIT;
        waitMessage = message;
        waitUntil = world.getTime() + ticks;
        repathCooldown = 0;
        npc.display.report(npc, message);
    }

    private void moveTo(BlockPos pos) {
        repathCooldown = 20;
        npc.getNavigation().startMovingTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1.1);
    }
}
