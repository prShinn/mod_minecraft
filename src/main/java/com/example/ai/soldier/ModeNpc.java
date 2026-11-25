package com.example.ai.soldier;

public class ModeNpc {
    public enum ModeMove {
        FOLLOW("Đi theo chủ"),
        WANDER("Tự do canh gác");

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
