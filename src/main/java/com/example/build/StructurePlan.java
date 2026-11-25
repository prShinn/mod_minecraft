package com.example.build;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bản vẽ công trình bất biến: danh sách bước đặt block theo toạ độ cục bộ.
 * x = sang phải, y = lên, z = về phía trước (theo hướng người đặt bản vẽ); (0,0,0) = ô của block bản vẽ.
 * Bước được sắp theo y → z → x để xây từ dưới lên; bước tại (0,0,0) luôn nằm CUỐI
 * vì block bản vẽ phải còn đó (giữ tiến độ) tới khi xong.
 */
public final class StructurePlan {
    public record Step(int x, int y, int z, BuildMaterial material) {}

    private final String id;
    private final String name;
    private final List<Step> steps;
    private final int width;
    private final int depth;
    private final int height;
    private final int[] cost = new int[BuildMaterial.values().length];
    private final int originIndex;

    private StructurePlan(String id, String name, List<Step> steps) {
        this.id = id;
        this.name = name;
        this.steps = List.copyOf(steps);
        int maxX = 0, maxY = 0, maxZ = 0, origin = -1;
        for (int i = 0; i < steps.size(); i++) {
            Step s = steps.get(i);
            maxX = Math.max(maxX, s.x());
            maxY = Math.max(maxY, s.y());
            maxZ = Math.max(maxZ, s.z());
            cost[s.material().ordinal()]++;
            if (s.x() == 0 && s.y() == 0 && s.z() == 0) origin = i;
        }
        this.width = maxX + 1;
        this.height = maxY + 1;
        this.depth = maxZ + 1;
        this.originIndex = origin;
    }

    public String id() { return id; }
    public String name() { return name; }
    public List<Step> steps() { return steps; }
    public int width() { return width; }
    public int depth() { return depth; }
    public int height() { return height; }
    /** Chỉ số bước tại ô bản vẽ, -1 nếu công trình không có block ở ô đó. */
    public int originIndex() { return originIndex; }

    public int cost(BuildMaterial material) {
        return cost[material.ordinal()];
    }

    public Set<BuildMaterial> materials() {
        Set<BuildMaterial> set = EnumSet.noneOf(BuildMaterial.class);
        for (BuildMaterial m : BuildMaterial.values()) {
            if (cost[m.ordinal()] > 0) set.add(m);
        }
        return set;
    }

    /** "Gỗ ×62, Đá ×38, ..." */
    public String costText() {
        StringBuilder sb = new StringBuilder();
        for (BuildMaterial m : BuildMaterial.values()) {
            if (cost[m.ordinal()] == 0) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(m.displayName()).append(" ×").append(cost[m.ordinal()]);
        }
        return sb.toString();
    }

    /** Toạ độ thế giới của ô cục bộ (x, y, z) khi bản vẽ đặt tại origin, hướng facing. */
    public static BlockPos toWorld(BlockPos origin, Direction facing, int x, int y, int z) {
        return origin.offset(facing.rotateYClockwise(), x).offset(facing, z).up(y);
    }

    public BlockPos toWorld(BlockPos origin, Direction facing, Step step) {
        return toWorld(origin, facing, step.x(), step.y(), step.z());
    }

    /** Chỗ NPC đứng xây: chính giữa, cách mặt trước 2 block (luôn ở ngoài công trình). */
    public BlockPos standPos(BlockPos origin, Direction facing) {
        return toWorld(origin, facing, width / 2, 0, -2);
    }

    public static Builder builder(String id, String name) {
        return new Builder(id, name);
    }

    public static final class Builder {
        private final String id;
        private final String name;
        private final Map<Integer, BuildMaterial> cells = new LinkedHashMap<>();

        private Builder(String id, String name) {
            this.id = id;
            this.name = name;
        }

        private static int key(int x, int y, int z) {
            if (x < 0 || y < 0 || z < 0 || x > 255 || y > 255 || z > 255) {
                throw new IllegalArgumentException("Toạ độ bản vẽ ngoài [0,255]: " + x + "," + y + "," + z);
            }
            return (x << 16) | (y << 8) | z;
        }

        public Builder set(int x, int y, int z, BuildMaterial material) {
            cells.put(key(x, y, z), material);
            return this;
        }

        public Builder clear(int x, int y, int z) {
            cells.remove(key(x, y, z));
            return this;
        }

        public Builder fill(int x0, int y0, int z0, int x1, int y1, int z1, BuildMaterial material) {
            for (int y = y0; y <= y1; y++)
                for (int z = z0; z <= z1; z++)
                    for (int x = x0; x <= x1; x++) set(x, y, z, material);
            return this;
        }

        /** Chu vi hình chữ nhật (x0,z0)-(x1,z1) qua các tầng y0..y1. */
        public Builder ring(int x0, int z0, int x1, int z1, int y0, int y1, BuildMaterial material) {
            for (int y = y0; y <= y1; y++)
                for (int z = z0; z <= z1; z++)
                    for (int x = x0; x <= x1; x++) {
                        if (x == x0 || x == x1 || z == z0 || z == z1) set(x, y, z, material);
                    }
            return this;
        }

        public StructurePlan build() {
            List<Step> steps = new ArrayList<>();
            for (Map.Entry<Integer, BuildMaterial> e : cells.entrySet()) {
                int k = e.getKey();
                steps.add(new Step(k >> 16 & 255, k >> 8 & 255, k & 255, e.getValue()));
            }
            steps.sort(Comparator.comparingInt(Step::y).thenComparingInt(Step::z).thenComparingInt(Step::x));
            // Ô bản vẽ (0,0,0) xây sau cùng
            for (int i = 0; i < steps.size(); i++) {
                Step s = steps.get(i);
                if (s.x() == 0 && s.y() == 0 && s.z() == 0) {
                    steps.remove(i);
                    steps.add(s);
                    break;
                }
            }
            return new StructurePlan(id, name, steps);
        }
    }
}
