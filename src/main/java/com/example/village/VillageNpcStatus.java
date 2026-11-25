package com.example.village;

/**
 * Trạng thái NPC hiển thị trong Sổ quản lý làng.
 * Thứ tự khai báo = thứ tự sắp xếp: sự cố cần xử lý lên đầu.
 */
public enum VillageNpcStatus {
    STUCK("Bị kẹt", 0xFF5555, true),
    CHEST_FULL("Rương đầy", 0xFF55FF, true),
    MISSING("Thiếu đồ", 0xFFAA00, true),
    ATTENTION("Cần chú ý", 0xFFFF55, true),
    COMBAT("Chiến đấu", 0xFF7755, false),
    WORKING("Đang làm", 0x55FF55, false),
    IDLE("Rảnh", 0xAAAAAA, false),
    SLEEPING("Đang ngủ", 0x55FFFF, false);

    private final String label;
    private final int color;
    private final boolean problem;

    VillageNpcStatus(String label, int color, boolean problem) {
        this.label = label;
        this.color = color;
        this.problem = problem;
    }

    public String label() {
        return label;
    }

    public int color() {
        return color;
    }

    /** Cần người chơi xử lý (dọn rương, bổ sung đồ, mở đường...). */
    public boolean isProblem() {
        return problem;
    }

    public static VillageNpcStatus byId(int id) {
        VillageNpcStatus[] values = values();
        return id >= 0 && id < values.length ? values[id] : ATTENTION;
    }
}
