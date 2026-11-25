package com.example.ai.soldier;

public class ModeNpc {
    public enum ModeMove {
        FOLLOW("Đi theo chủ"),
        WANDER("Tự do canh gác"),
        /** Đi tuần giữa các Cờ Tuần Tra của chủ; thêm ở cuối để giữ ordinal cũ trong NBT. */
        PATROL("Đi tuần tra");

        private final String displayName;

        ModeMove(String displayName) {
            this.displayName = displayName;
        }

        /** Tên hiển thị tiếng Việt (KHÔNG đổi thứ tự hằng: ordinal được lưu NBT/gửi packet). */
        public String displayName() {
            return displayName;
        }
    }
}
