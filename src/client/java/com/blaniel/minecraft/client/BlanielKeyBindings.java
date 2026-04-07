package com.blaniel.minecraft.client;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Keybindings del mod Blaniel
 */
public class BlanielKeyBindings {

	/**
	 * Keybinding para abrir la UI de Blaniel (tecla K por defecto)
	 */
	public static KeyBinding openUIKey;

	/**
	 * Keybinding para abrir el chat de Blaniel (tecla C por defecto)
	 */
	public static KeyBinding openChatKey;

	/**
	 * Registrar todos los keybindings
	 */
	public static void register() {
		openUIKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.blaniel.openui",                         // Clave de traducción
			InputUtil.Type.KEYSYM,                        // Tipo de input
			GLFW.GLFW_KEY_K,                              // Tecla K por defecto
			"category.blaniel"                            // Categoría en el menú de controles
		));

		openChatKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.blaniel.openchat",                       // Clave de traducción
			InputUtil.Type.KEYSYM,                        // Tipo de input
			GLFW.GLFW_KEY_C,                              // Tecla C por defecto
			"category.blaniel"                            // Categoría en el menú de controles
		));
	}
}
