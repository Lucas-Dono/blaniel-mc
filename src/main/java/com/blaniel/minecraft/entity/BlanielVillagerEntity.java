package com.blaniel.minecraft.entity;

import com.blaniel.minecraft.BlanielMod;
import com.blaniel.minecraft.network.BlanielAPIClient;
import com.mojang.authlib.GameProfile;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/**
 * Entidad de aldeano conectado a agente de Blaniel
 */
public class BlanielVillagerEntity extends PathAwareEntity {

	// DataTracker para sincronización cliente-servidor
	private static final TrackedData<String> AGENT_ID = DataTracker.registerData(
		BlanielVillagerEntity.class, TrackedDataHandlerRegistry.STRING
	);
	private static final TrackedData<String> AGENT_NAME = DataTracker.registerData(
		BlanielVillagerEntity.class, TrackedDataHandlerRegistry.STRING
	);

	// GameProfile personalizado para skin custom
	public GameProfile customGameProfile = null;

	// Cliente de API
	private final BlanielAPIClient apiClient;

	// Tracking de último jugador que interactuó
	private java.util.UUID lastInteractedPlayerId = null;
	private long lastInteractionTime = 0;

	// Chat bubble state
	private boolean chatBubbleVisible = false;
	private long chatBubbleHideTime = 0;

	// Conversation mode state
	private boolean inConversation = false;
	private java.util.UUID conversationPartnerId = null;
	private int conversationTickCounter = 0; // Para movimientos sutiles

	// Group meeting state
	private java.util.UUID groupId = null;
	private com.blaniel.minecraft.ai.MoveToGroupMeetingPointGoal moveToGroupGoal;

	public BlanielVillagerEntity(EntityType<? extends PathAwareEntity> entityType, World world) {
		super(entityType, world);

		// Inicializar cliente de API
		String apiUrl = BlanielMod.CONFIG.getApiUrl();
		String jwtToken = BlanielMod.CONFIG.getJwtToken();
		this.apiClient = new BlanielAPIClient(apiUrl, jwtToken);
	}

	/**
	 * Inicializar DataTracker para sincronización cliente-servidor
	 */
	@Override
	protected void initDataTracker() {
		super.initDataTracker();
		this.dataTracker.startTracking(AGENT_ID, "");
		this.dataTracker.startTracking(AGENT_NAME, "Aldeano");
	}

	/**
	 * Crear atributos por defecto del aldeano
	 */
	public static DefaultAttributeContainer.Builder createVillagerAttributes() {
		return MobEntity.createMobAttributes()
			.add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
			.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.5)
			.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0);
	}

	/**
	 * Inicializar AI goals (comportamiento)
	 */
	@Override
	protected void initGoals() {
		// Goal de reunión grupal (prioridad alta)
		this.moveToGroupGoal = new com.blaniel.minecraft.ai.MoveToGroupMeetingPointGoal(this, 0.7);
		this.goalSelector.add(1, this.moveToGroupGoal);

		// Pathfinding básico
		this.goalSelector.add(2, new SwimGoal(this));
		this.goalSelector.add(3, new EscapeDangerGoal(this, 1.4));
		this.goalSelector.add(4, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
		this.goalSelector.add(5, new WanderAroundFarGoal(this, 0.6));
		this.goalSelector.add(6, new LookAroundGoal(this));

		// Targets: huir de zombies
		this.targetSelector.add(1, new ActiveTargetGoal<>(this, net.minecraft.entity.mob.ZombieEntity.class, true));
	}

	/**
	 * Interacción del jugador (click derecho)
	 */
	@Override
	public ActionResult interactMob(PlayerEntity player, Hand hand) {
		if (!this.getWorld().isClient) {
			// Trackear última interacción
			this.lastInteractedPlayerId = player.getUuid();
			this.lastInteractionTime = System.currentTimeMillis();

			// Lado del servidor
			String agentId = this.getBlanielAgentId();
			String agentName = this.getBlanielAgentName();

			if (agentId.isEmpty()) {
				player.sendMessage(Text.literal("§c[Blaniel] §fEste aldeano no tiene un agente asignado"), false);
				player.sendMessage(Text.literal("§7Abre la UI con tecla K para asignar un agente"), false);
			} else {
				// Enviar packet al cliente para abrir GUI
				BlanielMod.LOGGER.info("Click derecho en aldeano con agente: " + agentName);

				if (player instanceof net.minecraft.server.network.ServerPlayerEntity) {
					net.minecraft.server.network.ServerPlayerEntity serverPlayer =
						(net.minecraft.server.network.ServerPlayerEntity) player;

					BlanielMod.LOGGER.info("Enviando OPEN_CHAT_PACKET al cliente...");

					net.minecraft.network.PacketByteBuf byteBuf =
						net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
					byteBuf.writeInt(this.getId());
					byteBuf.writeString(agentId);
					byteBuf.writeString(agentName);

					net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
						serverPlayer,
						com.blaniel.minecraft.network.NetworkHandler.OPEN_CHAT_PACKET,
						byteBuf
					);

					BlanielMod.LOGGER.info("OPEN_CHAT_PACKET enviado exitosamente");
				}
			}
		}

		return ActionResult.SUCCESS;
	}

	/**
	 * Enviar mensaje al agente de Blaniel
	 */
	public void sendMessageToAgent(String message, PlayerEntity player) {
		String agentId = this.getBlanielAgentId();
		String agentName = this.getBlanielAgentName();

		if (agentId.isEmpty() || !BlanielMod.CONFIG.isApiEnabled()) {
			player.sendMessage(Text.literal("§c[Blaniel] §fAPI no configurada o agente no asignado"), false);
			return;
		}

		// Construir contexto
		var context = new BlanielAPIClient.MinecraftContext();
		context.activity = "talking";
		context.timeOfDay = (int) this.getWorld().getTimeOfDay();
		context.weather = this.getWorld().isRaining() ?
			(this.getWorld().isThundering() ? "thunder" : "rain") : "clear";

		var pos = new BlanielAPIClient.Position();
		pos.x = this.getX();
		pos.y = this.getY();
		pos.z = this.getZ();
		pos.world = this.getWorld().getRegistryKey().getValue().toString();
		context.position = pos;

		// Enviar mensaje a API (async)
		BlanielMod.LOGGER.info("Enviando mensaje a agente {}: {}", agentId, message);

		apiClient.sendMessage(agentId, message, context).thenAccept(response -> {
			if (response != null && response.response != null) {
				// Ejecutar en thread principal de Minecraft
				if (this.getWorld().getServer() != null) {
					this.getWorld().getServer().execute(() -> {
						// Mostrar respuesta al jugador
						player.sendMessage(Text.literal("§b" + agentName + "§f: " + response.response), false);

						// Log de emoción
						if (response.emotions != null) {
							BlanielMod.LOGGER.info("Emoción: {} (intensidad: {})",
								response.emotions.primary, response.emotions.intensity);
						}
					});
				}
			}
		}).exceptionally(ex -> {
			BlanielMod.LOGGER.error("Error al procesar mensaje: {}", ex.getMessage());
			if (this.getWorld().getServer() != null) {
				this.getWorld().getServer().execute(() -> {
					player.sendMessage(Text.literal("§c[Blaniel] §fError al comunicarse con el servidor"), false);
				});
			}
			return null;
		});
	}

	/**
	 * Guardar datos adicionales en NBT (persistencia en disco)
	 */
	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putString("BlanielAgentId", this.getBlanielAgentId());
		nbt.putString("BlanielAgentName", this.getBlanielAgentName());
	}

	/**
	 * Cargar datos adicionales desde NBT (persistencia en disco)
	 */
	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		if (nbt.contains("BlanielAgentId")) {
			this.setBlanielAgentId(nbt.getString("BlanielAgentId"));
		}
		if (nbt.contains("BlanielAgentName")) {
			this.setBlanielAgentName(nbt.getString("BlanielAgentName"));
		}
	}

	// Getters y Setters (usando DataTracker para sincronización cliente-servidor)
	public String getBlanielAgentId() {
		return this.dataTracker.get(AGENT_ID);
	}

	public void setBlanielAgentId(String blanielAgentId) {
		this.dataTracker.set(AGENT_ID, blanielAgentId);
		BlanielMod.LOGGER.info("AgentId establecido: {} para aldeano {}", blanielAgentId, this.getBlanielAgentName());
	}

	public String getBlanielAgentName() {
		return this.dataTracker.get(AGENT_NAME);
	}

	public void setBlanielAgentName(String blanielAgentName) {
		this.dataTracker.set(AGENT_NAME, blanielAgentName);
		BlanielMod.LOGGER.info("AgentName establecido: {}", blanielAgentName);
	}

	/**
	 * Obtener GameProfile personalizado (para renderer)
	 */
	public GameProfile getCustomGameProfile() {
		return customGameProfile;
	}

	/**
	 * Obtener UUID del último jugador que interactuó
	 */
	public java.util.UUID getLastInteractedPlayerId() {
		return lastInteractedPlayerId;
	}

	/**
	 * Mostrar chat bubble sobre la entidad
	 */
	public void displayChatBubble(String message) {
		// Mostrar usando custom name
		this.setCustomName(Text.literal("§f" + message));
		this.setCustomNameVisible(true);
		this.chatBubbleVisible = true;

		// Programar ocultar después de 5 segundos
		this.chatBubbleHideTime = System.currentTimeMillis() + 5000;
	}

	/**
	 * Ocultar chat bubble
	 */
	public void hideChatBubble() {
		this.setCustomNameVisible(false);
		this.chatBubbleVisible = false;
	}

	/**
	 * Aplicar animación basada en hint
	 */
	public void playAnimation(String hint) {
		if (hint == null || hint.isEmpty()) {
			hint = "idle";
		}

		switch (hint) {
			case "waving":
				// Hacer que el brazo se mueva (swing hand)
				this.swingHand(Hand.MAIN_HAND);
				break;

			case "beckoning":
				// Señalar "ven acá"
				this.swingHand(Hand.MAIN_HAND);
				this.jump();
				break;

			case "happy":
				// Saltar de felicidad
				this.jump();
				break;

			case "pointing":
				// Señalar (swing hand)
				this.swingHand(Hand.MAIN_HAND);
				break;

			case "thinking":
				// Mirar hacia arriba
				this.headYaw += 10;
				break;

			case "surprised":
				// Paso atrás pequeño
				this.addVelocity(0, 0.1, 0);
				this.velocityModified = true;
				break;

			case "sad":
				// Mirar hacia abajo
				this.headYaw -= 10;
				break;

			case "angry":
				// Agitar la cabeza
				this.headYaw += 20;
				break;

			case "talking":
			case "idle":
			default:
				// Animación por defecto: nada especial
				break;
		}
	}

	/**
	 * Entrar en modo conversación con un jugador
	 */
	public void enterConversationMode(PlayerEntity player) {
		this.inConversation = true;
		this.conversationPartnerId = player.getUuid();

		// Cancelar movimiento actual
		this.getNavigation().stop();

		// Deshabilitar los AI goals de movimiento temporalmente
		this.goalSelector.getGoals().forEach(goal -> {
			if (goal.getGoal() instanceof WanderAroundFarGoal ||
				goal.getGoal() instanceof LookAroundGoal) {
				goal.getGoal().stop();
			}
		});

		BlanielMod.LOGGER.info("{} entró en modo conversación con {}", this.getBlanielAgentName(), player.getName().getString());
	}

	/**
	 * Salir del modo conversación
	 */
	public void exitConversationMode() {
		this.inConversation = false;
		this.conversationPartnerId = null;
		this.conversationTickCounter = 0;

		BlanielMod.LOGGER.info("{} salió del modo conversación", this.getBlanielAgentName());
	}

	/**
	 * Verificar si está en modo conversación
	 */
	public boolean isInConversation() {
		return this.inConversation;
	}

	/**
	 * Unirse a un grupo social
	 */
	public void joinGroup(java.util.UUID groupId, net.minecraft.util.math.Vec3d meetingPoint) {
		this.groupId = groupId;
		if (this.moveToGroupGoal != null) {
			this.moveToGroupGoal.setMeetingPoint(meetingPoint);
		}
		BlanielMod.LOGGER.info("{} se unió al grupo {}", this.getBlanielAgentName(), groupId);
	}

	/**
	 * Salir de un grupo social
	 */
	public void leaveGroup() {
		if (this.groupId != null) {
			BlanielMod.LOGGER.info("{} salió del grupo {}", this.getBlanielAgentName(), this.groupId);
			this.groupId = null;
			if (this.moveToGroupGoal != null) {
				this.moveToGroupGoal.clearMeetingPoint();
			}
		}
	}

	/**
	 * Obtener ID del grupo actual
	 */
	public java.util.UUID getGroupId() {
		return this.groupId;
	}

	/**
	 * Verificar si está en un grupo
	 */
	public boolean isInGroup() {
		return this.groupId != null;
	}

	/**
	 * Tick override para manejar chat bubbles y modo conversación
	 */
	@Override
	public void tick() {
		super.tick();

		// Auto-ocultar chat bubble después del tiempo
		if (chatBubbleVisible && System.currentTimeMillis() >= chatBubbleHideTime) {
			hideChatBubble();
		}

		// Manejar modo conversación
		if (inConversation) {
			conversationTickCounter++;

			// Buscar al jugador con el que está conversando
			PlayerEntity partner = this.getWorld().getPlayerByUuid(conversationPartnerId);
			if (partner != null) {
				// Verificar distancia (si el jugador se aleja mucho, terminar conversación)
				double distance = this.distanceTo(partner);
				if (distance > 16.0) {
					BlanielMod.LOGGER.info("{} - jugador muy lejos, terminando conversación", this.getBlanielAgentName());
					exitConversationMode();
					return;
				}

				// Mirar al jugador constantemente (con pequeñas variaciones sutiles)
				float yawVariation = (float) (Math.sin(conversationTickCounter * 0.03) * 2.0); // ±2 grados
				float pitchVariation = (float) (Math.cos(conversationTickCounter * 0.05) * 1.0); // ±1 grado

				this.getLookControl().lookAt(
					partner.getX() + yawVariation * 0.1,
					partner.getEyeY() + pitchVariation * 0.1,
					partner.getZ(),
					30.0f,
					30.0f
				);

				// Movimientos corporales sutiles cada cierto tiempo
				if (conversationTickCounter % 60 == 0) { // Cada 3 segundos
					// Pequeño cambio de peso (shift de postura)
					double random = this.random.nextDouble();
					if (random < 0.3) {
						// Ocasionalmente, pequeño movimiento de cabeza
						this.headYaw += (this.random.nextFloat() - 0.5f) * 4.0f; // ±2 grados
					} else if (random < 0.5) {
						// Pequeño ajuste de brazos (swing sutil)
						if (this.random.nextBoolean()) {
							this.handSwingProgress = 0.1f; // Movimiento muy sutil
						}
					}
				}

				// Pequeños movimientos de respiración
				if (conversationTickCounter % 40 == 0) { // Cada 2 segundos
					// Simular respiración con un pequeño "bounce"
					this.addVelocity(0, 0.01, 0);
					this.velocityModified = true;
				}

				// Asegurar que no se mueva demasiado
				if (!this.getNavigation().isIdle()) {
					this.getNavigation().stop();
				}
			} else {
				// Jugador no encontrado (desconectado o cambió de dimensión), terminar conversación
				BlanielMod.LOGGER.info("{} - jugador no encontrado, terminando conversación", this.getBlanielAgentName());
				exitConversationMode();
			}
		}
	}
}
