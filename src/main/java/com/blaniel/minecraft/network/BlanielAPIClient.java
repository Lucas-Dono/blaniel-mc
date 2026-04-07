package com.blaniel.minecraft.network;

import com.blaniel.minecraft.conversation.models.ConversationScript;
import com.blaniel.minecraft.conversation.models.ScriptMetadata;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Cliente HTTP para comunicarse con la API de Blaniel
 *
 * Usa java.net.http.HttpClient (Java 11+) - sin dependencias externas
 */
public class BlanielAPIClient {

	private static final Gson GSON = new Gson();

	// Cliente HTTP compartido con configuración optimizada
	// Usamos HTTP/1.1 para compatibilidad con servidores de desarrollo (localhost sin TLS)
	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
		.version(HttpClient.Version.HTTP_1_1)
		.connectTimeout(Duration.ofSeconds(10))
		.build();

	private final String baseUrl;
	private final String jwtToken;

	public BlanielAPIClient(String baseUrl, String jwtToken) {
		this.baseUrl = baseUrl;
		this.jwtToken = jwtToken;
	}

	/**
	 * Login con email y password
	 * Endpoint: POST /api/auth/minecraft-login
	 */
	public static CompletableFuture<LoginResponse> login(String baseUrl, String email, String password) {
		try {
			// Construir body
			JsonObject body = new JsonObject();
			body.addProperty("email", email);
			body.addProperty("password", password);

			String url = baseUrl + "/api/auth/minecraft-login";
			System.out.println("[Blaniel API] Login request to: " + url);
			System.out.println("[Blaniel API] Request body: " + body.toString());

			// Construir request
			HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.header("Content-Type", "application/json")
				.header("Accept", "application/json")
				.header("User-Agent", "BlanielMinecraft/0.1.0")
				.POST(HttpRequest.BodyPublishers.ofString(body.toString()))
				.timeout(Duration.ofSeconds(30))
				.build();

			// Ejecutar request (async nativo)
			return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenApply(response -> {
					System.out.println("[Blaniel API] Response status: " + response.statusCode());
					String responseBody = response.body();
					System.out.println("[Blaniel API] Response body: " + responseBody);

					if (response.statusCode() != 200) {
						System.err.println("[Blaniel API] Login failed with status: " + response.statusCode());
						LoginResponse errorResponse = new LoginResponse();
						errorResponse.success = false;
						errorResponse.error = parseErrorMessage(responseBody);
						return errorResponse;
					}

					LoginResponse loginResponse = GSON.fromJson(responseBody, LoginResponse.class);
					loginResponse.success = true;
					System.out.println("[Blaniel API] Login successful for: " + loginResponse.user.email);
					return loginResponse;
				})
				.exceptionally(e -> {
					System.err.println("[Blaniel API] Login error: " + e.getMessage());
					e.printStackTrace();

					LoginResponse errorResponse = new LoginResponse();
					errorResponse.success = false;
					errorResponse.error = "Error de conexión: " + e.getMessage();
					return errorResponse;
				});

		} catch (Exception e) {
			// Si falla crear el request, retornar error inmediato
			LoginResponse errorResponse = new LoginResponse();
			errorResponse.success = false;
			errorResponse.error = "Error al crear request: " + e.getMessage();
			return CompletableFuture.completedFuture(errorResponse);
		}
	}

	private static String parseErrorMessage(String responseBody) {
		try {
			JsonObject json = GSON.fromJson(responseBody, JsonObject.class);
			if (json.has("error")) {
				return json.get("error").getAsString();
			}
		} catch (Exception e) {
			// Ignore parsing errors
		}
		return "Error desconocido";
	}

	/**
	 * Enviar mensaje al agente
	 * Endpoint: POST /api/v1/minecraft/agents/{id}/chat
	 */
	public CompletableFuture<ChatResponse> sendMessage(String agentId, String message, MinecraftContext context) {
		try {
			// Construir body
			JsonObject body = new JsonObject();
			body.addProperty("message", message);

			JsonObject contextObj = new JsonObject();
			if (context != null) {
				contextObj.addProperty("activity", context.activity);
				contextObj.addProperty("timeOfDay", context.timeOfDay);
				contextObj.addProperty("weather", context.weather);

				if (context.position != null) {
					JsonObject pos = new JsonObject();
					pos.addProperty("x", context.position.x);
					pos.addProperty("y", context.position.y);
					pos.addProperty("z", context.position.z);
					pos.addProperty("world", context.position.world);
					contextObj.add("position", pos);
				}
			}
			body.add("context", contextObj);

			// Construir request
			HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(baseUrl + "/api/v1/minecraft/agents/" + agentId + "/chat"))
				.header("Authorization", "Bearer " + jwtToken)
				.header("Content-Type", "application/json")
				.header("Accept", "application/json")
				.header("User-Agent", "BlanielMinecraft/0.1.0")
				.POST(HttpRequest.BodyPublishers.ofString(body.toString()))
				.timeout(Duration.ofSeconds(30))
				.build();

			// Ejecutar request (async nativo)
			return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenApply(response -> {
					if (response.statusCode() != 200) {
						throw new RuntimeException("HTTP " + response.statusCode());
					}
					return GSON.fromJson(response.body(), ChatResponse.class);
				})
				.exceptionally(e -> {
					System.err.println("[Blaniel API] Error: " + e.getMessage());
					e.printStackTrace();

					// Fallback response
					ChatResponse fallback = new ChatResponse();
					fallback.response = "Lo siento, no puedo conectarme al servidor ahora.";
					fallback.emotions = new EmotionData();
					fallback.emotions.primary = "neutral";
					fallback.emotions.intensity = 0.5;
					fallback.emotions.animation = "idle";
					return fallback;
				});

		} catch (Exception e) {
			System.err.println("[Blaniel API] Error creating request: " + e.getMessage());
			e.printStackTrace();

			// Fallback response
			ChatResponse fallback = new ChatResponse();
			fallback.response = "Error al crear la petición.";
			fallback.emotions = new EmotionData();
			fallback.emotions.primary = "neutral";
			fallback.emotions.intensity = 0.5;
			fallback.emotions.animation = "idle";
			return CompletableFuture.completedFuture(fallback);
		}
	}

	/**
	 * Obtener lista de agentes disponibles
	 * Endpoint: GET /api/v1/minecraft/agents
	 */
	public CompletableFuture<AgentListResponse> getAvailableAgents() {
		try {
			HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(baseUrl + "/api/v1/minecraft/agents"))
				.header("Authorization", "Bearer " + jwtToken)
				.header("Accept", "application/json")
				.header("User-Agent", "BlanielMinecraft/0.1.0")
				.GET()
				.timeout(Duration.ofSeconds(15))
				.build();

			return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenApply(response -> {
					if (response.statusCode() != 200) {
						throw new RuntimeException("HTTP " + response.statusCode());
					}
					return GSON.fromJson(response.body(), AgentListResponse.class);
				})
				.exceptionally(e -> {
					System.err.println("[Blaniel API] Error: " + e.getMessage());
					e.printStackTrace();
					return null;
				});

		} catch (Exception e) {
			System.err.println("[Blaniel API] Error creating request: " + e.getMessage());
			e.printStackTrace();
			return CompletableFuture.completedFuture(null);
		}
	}

	/**
	 * Obtener metadata del script (solo versión, sin líneas completas)
	 * Endpoint: GET /api/v1/minecraft/conversation-script/metadata?groupHash=xxx
	 */
	public CompletableFuture<ScriptMetadata> getScriptMetadata(String groupHash) {
		try {
			String url = baseUrl + "/api/v1/minecraft/conversation-script/metadata?groupHash=" + groupHash;

			HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.header("Accept", "application/json")
				.header("User-Agent", "BlanielMinecraft/0.1.0")
				.GET()
				.timeout(Duration.ofSeconds(10))
				.build();

			return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenApply(response -> {
					if (response.statusCode() == 404) {
						// No existe script para este grupo
						return null;
					}

					if (response.statusCode() != 200) {
						System.err.println("[Blaniel API] Failed to get metadata: " + response.statusCode());
						return null;
					}

					return GSON.fromJson(response.body(), ScriptMetadata.class);
				})
				.exceptionally(e -> {
					System.err.println("[Blaniel API] Error getting metadata: " + e.getMessage());
					return null;
				});

		} catch (Exception e) {
			System.err.println("[Blaniel API] Error creating metadata request: " + e.getMessage());
			return CompletableFuture.completedFuture(null);
		}
	}

	/**
	 * Obtener guión conversacional completo
	 * Endpoint: POST /api/v1/minecraft/conversation-script
	 */
	public CompletableFuture<ConversationScript> getConversationScript(
		List<String> agentIds,
		String location,
		String contextHint,
		String groupHash,
		boolean forceNew
	) {
		try {
			// Construir body
			JsonObject body = new JsonObject();
			body.add("agentIds", GSON.toJsonTree(agentIds));
			body.addProperty("location", location);
			if (contextHint != null) {
				body.addProperty("contextHint", contextHint);
			}
			body.addProperty("groupHash", groupHash);
			body.addProperty("forceNew", forceNew);

			HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(baseUrl + "/api/v1/minecraft/conversation-script"))
				.header("Content-Type", "application/json")
				.header("Accept", "application/json")
				.header("User-Agent", "BlanielMinecraft/0.1.0")
				.POST(HttpRequest.BodyPublishers.ofString(body.toString()))
				.timeout(Duration.ofSeconds(30))
				.build();

			return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenApply(response -> {
					if (response.statusCode() != 200) {
						System.err.println("[Blaniel API] Failed to get script: " + response.statusCode());
						System.err.println("[Blaniel API] Response: " + response.body());
						return null;
					}

					ConversationScript script = GSON.fromJson(response.body(), ConversationScript.class);
					System.out.println("[Blaniel API] Script downloaded: " + script.getTopic() +
						" (v" + script.getVersion() + ", " + script.getTotalLines() + " lines)");
					return script;
				})
				.exceptionally(e -> {
					System.err.println("[Blaniel API] Error getting script: " + e.getMessage());
					e.printStackTrace();
					return null;
				});

		} catch (Exception e) {
			System.err.println("[Blaniel API] Error creating script request: " + e.getMessage());
			e.printStackTrace();
			return CompletableFuture.completedFuture(null);
		}
	}

	// ===== Data Classes =====

	public static class ChatResponse {
		public String response;
		public EmotionData emotions;
		public ActionData action;
		public RelationshipData relationship;
	}

	public static class EmotionData {
		public String primary;
		public double intensity;
		public String animation;
	}

	public static class ActionData {
		public String type;
	}

	public static class RelationshipData {
		public String stage;
		public double trust;
		public double affinity;
	}

	public static class AgentListResponse {
		public AgentData[] agents;
		public int total;
		public String plan;
	}

	public static class AgentData {
		public String id;
		public String name;
		public String gender;
		public Integer age; // Nullable - puede no estar disponible
		public String profession;
		public String currentEmotion;
	}

	public static class MinecraftContext {
		public String activity;
		public int timeOfDay;
		public String weather;
		public Position position;
	}

	public static class Position {
		public double x;
		public double y;
		public double z;
		public String world;
	}

	public static class LoginResponse {
		public boolean success;
		public String error;
		public String token;
		public int expiresIn;
		public UserDataResponse user;
		public AgentData[] agents;
		public String message;
	}

	public static class UserDataResponse {
		public String id;
		public String email;
		public String name;
		public String image;
		public String plan;
	}
}
