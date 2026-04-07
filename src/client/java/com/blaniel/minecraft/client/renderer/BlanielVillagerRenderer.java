package com.blaniel.minecraft.client.renderer;

import com.blaniel.minecraft.BlanielMod;
import com.blaniel.minecraft.entity.BlanielVillagerEntity;
import com.blaniel.minecraft.skin.BlanielSkinManager;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.util.Identifier;

import java.util.HashSet;
import java.util.Set;

/**
 * Renderer para BlanielVillagerEntity
 *
 * Usa el modelo de jugador (PlayerEntityModel) y aplica skins personalizadas
 * descargadas desde la API de Blaniel.
 */
public class BlanielVillagerRenderer extends MobEntityRenderer<BlanielVillagerEntity, PlayerEntityModel<BlanielVillagerEntity>> {

	// Skin por defecto (Steve) - Ruta correcta para Minecraft 1.20.1
	// Las skins de jugador por defecto están en entity/player/
	private static final Identifier DEFAULT_TEXTURE = new Identifier("minecraft", "textures/entity/player/wide/steve.png");

	// Set de agentes cuyas skins ya están siendo descargadas (evitar duplicados)
	private static final Set<String> LOADING_SKINS = new HashSet<>();

	public BlanielVillagerRenderer(EntityRendererFactory.Context context) {
		super(context, new PlayerEntityModel<>(context.getPart(EntityModelLayers.PLAYER), false), 0.5f);
	}

	/**
	 * Obtener textura de la entidad
	 *
	 * Retorna la skin custom si está disponible, sino la de Steve.
	 * Lazy-load: carga la skin solo cuando se renderiza por primera vez.
	 */
	@Override
	public Identifier getTexture(BlanielVillagerEntity entity) {
		String agentId = entity.getBlanielAgentId();

		// DEBUG: Log cada vez que se renderiza
		if (agentId == null || agentId.isEmpty()) {
			BlanielMod.LOGGER.warn("getTexture() llamado con agentId vacío para aldeano: {}", entity.getBlanielAgentName());
			return DEFAULT_TEXTURE;
		}

		// DEBUG: Log primera vez que se ve este aldeano
		if (!LOADING_SKINS.contains(agentId) && !BlanielSkinManager.isTextureLoaded(agentId)) {
			BlanielMod.LOGGER.info("getTexture() llamado por primera vez para: {} (agentId={})",
				entity.getBlanielAgentName(), agentId);
		}

		// Intentar obtener Identifier de textura del SkinManager
		Identifier skinTexture = BlanielSkinManager.getTextureIdentifier(agentId);

		// Si la textura está cargada y registrada, usarla
		if (skinTexture != null) {
			return skinTexture;
		}

		// Si no está cargada y no está descargando, iniciar descarga (lazy load)
		if (!LOADING_SKINS.contains(agentId)) {
			LOADING_SKINS.add(agentId);

			String apiUrl = BlanielMod.CONFIG.getApiUrl();
			String jwtToken = BlanielMod.CONFIG.getJwtToken();

			if (!apiUrl.isEmpty() && !jwtToken.isEmpty()) {
				BlanielMod.LOGGER.info("Iniciando carga de skin para: {} ({})", entity.getBlanielAgentName(), agentId);

				BlanielSkinManager.loadSkin(agentId, entity.getBlanielAgentName(), apiUrl, jwtToken)
					.thenAccept(profile -> {
						entity.customGameProfile = profile;
						LOADING_SKINS.remove(agentId);
						BlanielMod.LOGGER.info("Skin completamente cargada y registrada: {}", entity.getBlanielAgentName());
					})
					.exceptionally(ex -> {
						BlanielMod.LOGGER.error("Error cargando skin para {}: {}", entity.getBlanielAgentName(), ex.getMessage());
						LOADING_SKINS.remove(agentId);
						return null;
					});
			} else {
				BlanielMod.LOGGER.warn("API no configurada, usando textura default para {}", entity.getBlanielAgentName());
				LOADING_SKINS.remove(agentId);
			}
		}

		// Mientras se descarga y registra, usar Steve como fallback
		return DEFAULT_TEXTURE;
	}
}
