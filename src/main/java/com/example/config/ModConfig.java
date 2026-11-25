package com.example.config;

import com.example.ExampleMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Cấu hình mod, đọc từ config/datnc_mod.json.
 * File được tự tạo (hoặc bổ sung key mới) khi khởi động game.
 */
public final class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ModConfig instance = new ModConfig();

    /** Khoảng cách (block) người chơi đứng gần thì hiện trạng thái trên đầu NPC. */
    public double statusViewDistance = 6.0;
    /** Bật/tắt hạt + âm thanh khi NPC hoàn thành việc hoặc gặp sự cố. */
    public boolean feedbackEffects = true;

    /** Bán kính (block) quanh rương mà Thợ chăn nuôi làm việc. */
    public int rancherWorkRadius = 12;
    /** Số con tối đa mỗi loài trong vùng; đạt ngưỡng thì ngừng cho sinh sản. */
    public int rancherMaxAnimalsPerType = 8;

    /** Bán kính (block) Đầu bếp tìm rương nguyên liệu và rương thức ăn. */
    public int cookWorkRadius = 16;
    /** Số tick để nấu 1 món (20 tick = 1 giây). */
    public int cookTicksPerItem = 60;
    /** Số món nấu được cho mỗi 1 nhiên liệu (than, gỗ...). */
    public int cookItemsPerFuel = 8;
    /** Số lượng tối thiểu để lại trong rương nghề khác khi Đầu bếp lấy nguyên liệu. */
    public int cookLeaveInSupplierChest = 8;

    /** Bán kính (block) quanh Rương Thợ xây mà Thợ xây tìm công trình (bản vẽ) để xây. */
    public int builderWorkRadius = 40;
    /** Số tick giữa hai block Thợ xây đặt (20 tick = 1 giây). */
    public int builderPlaceIntervalTicks = 6;

    public static ModConfig get() {
        return instance;
    }

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(ExampleMod.MODID + ".json");
        try {
            if (Files.exists(path)) {
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
                    if (loaded != null) instance = loaded;
                }
            }
            instance.sanitize();
            // Ghi lại để file luôn có đủ key (kể cả key mới thêm ở bản sau)
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(instance, writer);
            }
        } catch (IOException | JsonParseException e) {
            ExampleMod.LOGGER.warn("Không đọc được {}, dùng cấu hình mặc định: {}", path, e.getMessage());
            instance = new ModConfig();
        }
    }

    private void sanitize() {
        statusViewDistance = Math.max(1.0, Math.min(32.0, statusViewDistance));
        rancherWorkRadius = Math.max(4, Math.min(32, rancherWorkRadius));
        rancherMaxAnimalsPerType = Math.max(2, Math.min(64, rancherMaxAnimalsPerType));
        cookWorkRadius = Math.max(4, Math.min(32, cookWorkRadius));
        cookTicksPerItem = Math.max(5, Math.min(1200, cookTicksPerItem));
        cookItemsPerFuel = Math.max(1, Math.min(64, cookItemsPerFuel));
        cookLeaveInSupplierChest = Math.max(0, Math.min(64, cookLeaveInSupplierChest));
        // Tối đa 40: WorkerNpcEntity quên rương khi NPC cách rương > 3 × 16 = 48 block
        builderWorkRadius = Math.max(8, Math.min(40, builderWorkRadius));
        builderPlaceIntervalTicks = Math.max(1, Math.min(100, builderPlaceIntervalTicks));
    }
}
