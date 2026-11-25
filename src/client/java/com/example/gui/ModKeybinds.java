package com.example.gui;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class ModKeybinds {

    public static KeyBinding OPEN_NPC_GUI;
    public static KeyBinding OPEN_VILLAGE_OVERVIEW;

    public static void register() {
        OPEN_NPC_GUI = KeyBindingHelper.registerKeyBinding(
                new KeyBinding(
                        "key.soldiernpc.open_gui",
                        InputUtil.Type.KEYSYM,
                        GLFW.GLFW_KEY_G, // TAB trùng phím Danh sách người chơi
                        "category.soldiernpc"
                )
        );
        OPEN_VILLAGE_OVERVIEW = KeyBindingHelper.registerKeyBinding(
                new KeyBinding(
                        "key.datnc_mod.village_overview",
                        InputUtil.Type.KEYSYM,
                        GLFW.GLFW_KEY_K,
                        "category.soldiernpc"
                )
        );
    }
}

