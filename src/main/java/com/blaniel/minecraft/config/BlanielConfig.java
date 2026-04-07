package com.blaniel.minecraft.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Configuración del mod Blaniel MC
 *
 * Se guarda en: .minecraft/config/blaniel-mc.json
 */
public class BlanielConfig {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance()
		.getConfigDir()
		.resolve("blaniel-mc.json");

	// Valores por defecto
	private String apiUrl = "http://localhost:3000";
	private String jwtToken = "";
	private boolean apiEnabled = true;
	private String googleClientId = "1036827882293-s8ofh16rlclnp1t82flhk2o54mnbtdmn.apps.googleusercontent.com"; // Default client ID
	private String googleRefreshToken = ""; // Refresh token de Google para renovar sesión

	// Datos del usuario (cacheados)
	private UserData userData = null;

	// Clase interna para datos del usuario
	public static class UserData {
		public String userId;
		public String email;
		public String name;
		public String plan;

		public UserData() {}

		public UserData(String userId, String email, String name, String plan) {
			this.userId = userId;
			this.email = email;
			this.name = name;
			this.plan = plan;
		}
	}

	/**
	 * Cargar configuración desde archivo
	 */
	public void load() {
		if (Files.exists(CONFIG_PATH)) {
			try {
				String json = Files.readString(CONFIG_PATH);
				BlanielConfig loaded = GSON.fromJson(json, BlanielConfig.class);

				this.apiUrl = loaded.apiUrl;
				this.jwtToken = loaded.jwtToken;
				this.apiEnabled = loaded.apiEnabled;
				this.googleClientId = loaded.googleClientId != null ? loaded.googleClientId : this.googleClientId;
				this.googleRefreshToken = loaded.googleRefreshToken != null ? loaded.googleRefreshToken : this.googleRefreshToken;
				this.userData = loaded.userData;

				System.out.println("[Blaniel] Configuración cargada desde " + CONFIG_PATH);
			} catch (IOException e) {
				System.err.println("[Blaniel] Error al cargar configuración: " + e.getMessage());
				save(); // Guardar valores por defecto
			}
		} else {
			save(); // Crear archivo con valores por defecto
		}
	}

	/**
	 * Guardar configuración a archivo
	 */
	public void save() {
		try {
			String json = GSON.toJson(this);
			Files.writeString(CONFIG_PATH, json);
			System.out.println("[Blaniel] Configuración guardada en " + CONFIG_PATH);
		} catch (IOException e) {
			System.err.println("[Blaniel] Error al guardar configuración: " + e.getMessage());
		}
	}

	// Getters
	public String getApiUrl() {
		return apiUrl;
	}

	public String getJwtToken() {
		return jwtToken;
	}

	public boolean isApiEnabled() {
		return apiEnabled;
	}

	public String getGoogleClientId() {
		return googleClientId;
	}

	public String getGoogleRefreshToken() {
		return googleRefreshToken;
	}

	public UserData getUserData() {
		return userData;
	}

	public boolean isLoggedIn() {
		return jwtToken != null && !jwtToken.isEmpty() && userData != null;
	}

	// Setters
	public void setApiUrl(String apiUrl) {
		this.apiUrl = apiUrl;
		save();
	}

	public void setJwtToken(String jwtToken) {
		this.jwtToken = jwtToken;
		save();
	}

	public void setApiEnabled(boolean apiEnabled) {
		this.apiEnabled = apiEnabled;
		save();
	}

	public void setUserData(UserData userData) {
		this.userData = userData;
		save();
	}

	/**
	 * Login: guarda token y datos del usuario (sin OAuth)
	 */
	public void login(String jwtToken, UserData userData) {
		this.jwtToken = jwtToken;
		this.userData = userData;
		save();
	}

	/**
	 * Login con OAuth: guarda token, datos del usuario Y refresh token de Google
	 */
	public void loginWithOAuth(String jwtToken, UserData userData, String googleRefreshToken) {
		this.jwtToken = jwtToken;
		this.userData = userData;
		this.googleRefreshToken = googleRefreshToken;
		save();
	}

	/**
	 * Verificar si hay refresh token guardado
	 */
	public boolean hasRefreshToken() {
		return googleRefreshToken != null && !googleRefreshToken.isEmpty();
	}

	/**
	 * Logout: limpia token y datos
	 */
	public void logout() {
		this.jwtToken = "";
		this.userData = null;
		this.googleRefreshToken = "";
		save();
	}
}
