package com.blaniel.minecraft;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.blaniel.minecraft.entity.BlanielVillagerEntity;
import com.blaniel.minecraft.command.BlanielCommands;
import com.blaniel.minecraft.config.BlanielConfig;

/**
 * Blaniel Minecraft Integration
 *
 * Mod que conecta agentes de IA de Blaniel con aldeanos de Minecraft.
 * Los jugadores pueden hablar con sus personajes de IA en un mundo 3D.
 */
public class BlanielMod implements ModInitializer {

	public static final String MOD_ID = "blaniel-mc";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// Configuración
	public static final BlanielConfig CONFIG = new BlanielConfig();

	// Entity Type para BlanielVillager (se inicializa en onInitialize)
	public static EntityType<BlanielVillagerEntity> BLANIEL_VILLAGER;

	@Override
	public void onInitialize() {
		LOGGER.info("Inicializando Blaniel Minecraft Integration");

		// Registrar EntityType
		BLANIEL_VILLAGER = Registry.register(
			Registries.ENTITY_TYPE,
			new Identifier(MOD_ID, "blaniel_villager"),
			EntityType.Builder.create(BlanielVillagerEntity::new, SpawnGroup.CREATURE)
				.setDimensions(0.6f, 1.95f) // Tamaño de aldeano vanilla
				.maxTrackingRange(10)
				.build("blaniel_villager")
		);

		// Cargar configuración
		CONFIG.load();
		LOGGER.info("API URL: {}", CONFIG.getApiUrl());
		LOGGER.info("API habilitada: {}", CONFIG.isApiEnabled());

		if (CONFIG.isLoggedIn()) {
			var userData = CONFIG.getUserData();
			LOGGER.info("Usuario logueado: {} ({})", userData.name, userData.email);
		} else {
			LOGGER.warn("Usuario no logueado. Se mostrará pantalla de login al iniciar.");
		}

		// Registrar atributos de entidad
		FabricDefaultAttributeRegistry.register(BLANIEL_VILLAGER, BlanielVillagerEntity.createVillagerAttributes());

		// Registrar comandos
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			BlanielCommands.register(dispatcher);
		});

		// Registrar network handlers
		com.blaniel.minecraft.network.NetworkHandler.registerServerReceivers();

		// Registrar lifecycle handlers (inicialización y limpieza)
		com.blaniel.minecraft.server.ServerLifecycleHandler.register();

		// Registrar tick handlers (para grupos sociales)
		com.blaniel.minecraft.server.ServerTickHandler.register();

		LOGGER.info("Blaniel Minecraft Integration cargado exitosamente");
	}
}
