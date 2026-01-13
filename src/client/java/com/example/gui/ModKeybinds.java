package com.example.gui;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class ModKeybinds {

    public static KeyBinding OPEN_NPC_GUI;

    public static void register() {
        OPEN_NPC_GUI = KeyBindingHelper.registerKeyBinding(
                new KeyBinding(
                        "key.soldiernpc.open_gui",
                        InputUtil.Type.KEYSYM,
//                        GLFW.GLFW_KEY_G,
                        GLFW.GLFW_KEY_TAB,
                        "category.soldiernpc"
                )
        );
    }
}

