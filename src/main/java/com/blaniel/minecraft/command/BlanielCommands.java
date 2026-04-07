package com.blaniel.minecraft.command;

import com.blaniel.minecraft.BlanielMod;
import com.blaniel.minecraft.entity.BlanielVillagerEntity;
import com.blaniel.minecraft.network.BlanielAPIClient;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;

import java.util.List;

/**
 * Blaniel mod commands
 */
public class BlanielCommands {

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {

		dispatcher.register(CommandManager.literal("blaniel")
			// /blaniel spawn <agentId>
			.then(CommandManager.literal("spawn")
				.then(CommandManager.argument("agentId", StringArgumentType.string())
					.executes(BlanielCommands::spawnVillager)
				)
			)
			// /blaniel list
			.then(CommandManager.literal("list")
				.executes(BlanielCommands::listAgents)
			)
			// /blaniel assign <agentId>
			.then(CommandManager.literal("assign")
				.then(CommandManager.argument("agentId", StringArgumentType.string())
					.executes(BlanielCommands::assignAgent)
				)
			)
			// /blaniel chat <message>
			.then(CommandManager.literal("chat")
				.then(CommandManager.argument("message", StringArgumentType.greedyString())
					.executes(BlanielCommands::sendChatMessage)
				)
			)
			// /blaniel config
			.then(CommandManager.literal("config")
				.executes(BlanielCommands::showConfig)
				.then(CommandManager.literal("apiUrl")
					.then(CommandManager.argument("url", StringArgumentType.string())
						.executes(BlanielCommands::setApiUrl)
					)
				)
			)
		);
	}

	/**
	 * /blaniel spawn <agentId>
	 */
	private static int spawnVillager(CommandContext<ServerCommandSource> context) {
		String agentId = StringArgumentType.getString(context, "agentId");
		ServerCommandSource source = context.getSource();

		try {
			ServerPlayerEntity player = source.getPlayerOrThrow();

			// Create villager at player position
			BlanielVillagerEntity villager = new BlanielVillagerEntity(
				BlanielMod.BLANIEL_VILLAGER,
				player.getWorld()
			);

			villager.refreshPositionAndAngles(
				player.getX(), player.getY(), player.getZ(),
				player.getYaw(), 0.0f
			);

			villager.setBlanielAgentId(agentId);
			villager.setBlanielAgentName("Agente " + agentId.substring(0, Math.min(8, agentId.length())));

			player.getWorld().spawnEntity(villager);

			source.sendFeedback(
				() -> Text.literal("§a[Blaniel] §fAldeano spawneado con agente: " + agentId),
				false
			);

			return 1;

		} catch (Exception e) {
			source.sendError(Text.literal("§c[Blaniel] §fError: " + e.getMessage()));
			return 0;
		}
	}

	/**
	 * /blaniel list
	 */
	private static int listAgents(CommandContext<ServerCommandSource> context) {
		ServerCommandSource source = context.getSource();

		try {
			ServerPlayerEntity player = source.getPlayerOrThrow();

			source.sendFeedback(
				() -> Text.literal("§a[Blaniel] §fObteniendo lista de agentes..."),
				false
			);

			// Llamar a API
			String apiUrl = BlanielMod.CONFIG.getApiUrl();
			String jwtToken = BlanielMod.CONFIG.getJwtToken();
			BlanielAPIClient client = new BlanielAPIClient(apiUrl, jwtToken);

			client.getAvailableAgents().thenAccept(response -> {
				if (response != null && response.agents != null) {
					player.getServer().execute(() -> {
						source.sendFeedback(
							() -> Text.literal("§a[Blaniel] §fAgentes disponibles (" + response.total + "):"),
							false
						);

						for (BlanielAPIClient.AgentData agent : response.agents) {
							source.sendFeedback(
								() -> Text.literal("  §b" + agent.name + " §7(ID: " + agent.id + ")"),
								false
							);
						}
					});
				} else {
					player.getServer().execute(() -> {
						source.sendError(Text.literal("§c[Blaniel] §fNo se pudo obtener la lista de agentes"));
					});
				}
			});

			return 1;

		} catch (Exception e) {
			source.sendError(Text.literal("§c[Blaniel] §fError: " + e.getMessage()));
			return 0;
		}
	}

	/**
	 * /blaniel assign <agentId>
	 */
	private static int assignAgent(CommandContext<ServerCommandSource> context) {
		String agentId = StringArgumentType.getString(context, "agentId");
		ServerCommandSource source = context.getSource();

		try {
			ServerPlayerEntity player = source.getPlayerOrThrow();

			// Buscar aldeano más cercano
			List<Entity> entities = player.getWorld().getOtherEntities(
				player,
				new Box(player.getBlockPos()).expand(10),
				entity -> entity instanceof BlanielVillagerEntity
			);

			if (entities.isEmpty()) {
				source.sendError(Text.literal("§c[Blaniel] §fNo hay aldeanos cerca (radio 10 bloques)"));
				return 0;
			}

			BlanielVillagerEntity villager = (BlanielVillagerEntity) entities.get(0);
			villager.setBlanielAgentId(agentId);
			villager.setBlanielAgentName("Agente " + agentId.substring(0, Math.min(8, agentId.length())));

			source.sendFeedback(
				() -> Text.literal("§a[Blaniel] §fAgente asignado al aldeano cercano"),
				false
			);

			return 1;

		} catch (Exception e) {
			source.sendError(Text.literal("§c[Blaniel] §fError: " + e.getMessage()));
			return 0;
		}
	}

	/**
	 * /blaniel chat <mensaje>
	 */
	private static int sendChatMessage(CommandContext<ServerCommandSource> context) {
		String message = StringArgumentType.getString(context, "message");
		ServerCommandSource source = context.getSource();

		try {
			ServerPlayerEntity player = source.getPlayerOrThrow();

			// Buscar aldeano más cercano
			List<Entity> entities = player.getWorld().getOtherEntities(
				player,
				new Box(player.getBlockPos()).expand(10),
				entity -> entity instanceof BlanielVillagerEntity
			);

			if (entities.isEmpty()) {
				source.sendError(Text.literal("§c[Blaniel] §fNo hay aldeanos cerca (radio 10 bloques)"));
				return 0;
			}

			BlanielVillagerEntity villager = (BlanielVillagerEntity) entities.get(0);

			if (villager.getBlanielAgentId().isEmpty()) {
				source.sendError(Text.literal("§c[Blaniel] §fEl aldeano no tiene agente asignado"));
				return 0;
			}

			// Enviar mensaje
			player.sendMessage(Text.literal("§eTú§f: " + message), false);
			villager.sendMessageToAgent(message, player);

			return 1;

		} catch (Exception e) {
			source.sendError(Text.literal("§c[Blaniel] §fError: " + e.getMessage()));
			return 0;
		}
	}

	/**
	 * /blaniel config
	 */
	private static int showConfig(CommandContext<ServerCommandSource> context) {
		ServerCommandSource source = context.getSource();

		source.sendFeedback(
			() -> Text.literal("§a[Blaniel] §fConfiguración actual:"),
			false
		);
		source.sendFeedback(
			() -> Text.literal("  API URL: §b" + BlanielMod.CONFIG.getApiUrl()),
			false
		);
		source.sendFeedback(
			() -> Text.literal("  JWT Token: §b" + (BlanielMod.CONFIG.getJwtToken().isEmpty() ? "§c(no configurada)" : "***")),
			false
		);
		source.sendFeedback(
			() -> Text.literal("  API Habilitada: §b" + BlanielMod.CONFIG.isApiEnabled()),
			false
		);

		return 1;
	}

	/**
	 * /blaniel config apiUrl <url>
	 */
	private static int setApiUrl(CommandContext<ServerCommandSource> context) {
		String url = StringArgumentType.getString(context, "url");
		ServerCommandSource source = context.getSource();

		BlanielMod.CONFIG.setApiUrl(url);

		source.sendFeedback(
			() -> Text.literal("§a[Blaniel] §fAPI URL configurada: " + url),
			false
		);

		return 1;
	}

}
