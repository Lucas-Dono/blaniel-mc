package com.blaniel.minecraft.screen;

import com.blaniel.minecraft.BlanielMod;
import com.blaniel.minecraft.config.BlanielConfig;
import com.blaniel.minecraft.network.BlanielAPIClient;
import com.blaniel.minecraft.oauth.OAuth2Client;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * Pantalla de login para Blaniel MC
 *
 * Permite al usuario iniciar sesión con email y contraseña
 */
public class LoginScreen extends Screen {

	private final Screen parent;
	private TextFieldWidget emailField;
	private TextFieldWidget passwordField;
	private ButtonWidget loginButton;
	private ButtonWidget googleLoginButton;
	private String errorMessage = "";
	private boolean loggingIn = false;
	private boolean firstRender = true;

	public LoginScreen(Screen parent) {
		super(Text.literal("Iniciar Sesión en Blaniel"));
		this.parent = parent;
		BlanielMod.LOGGER.info("LoginScreen constructor llamado (parent: " + (parent != null ? parent.getClass().getSimpleName() : "null") + ")");
	}

	@Override
	protected void init() {
		super.init();
		BlanielMod.LOGGER.info("LoginScreen.init() llamado (width: " + this.width + ", height: " + this.height + ")");

		int centerX = this.width / 2;
		int startY = this.height / 2 - 60;

		// Email field
		this.emailField = new TextFieldWidget(
			this.textRenderer,
			centerX - 150,
			startY,
			300,
			20,
			Text.literal("Email")
		);
		this.emailField.setMaxLength(100);
		this.emailField.setPlaceholder(Text.literal("tu@email.com"));
		this.addSelectableChild(this.emailField);
		this.setInitialFocus(this.emailField);

		// Password field
		this.passwordField = new TextFieldWidget(
			this.textRenderer,
			centerX - 150,
			startY + 40,
			300,
			20,
			Text.literal("Contraseña")
		);
		this.passwordField.setMaxLength(100);
		this.passwordField.setPlaceholder(Text.literal("Contraseña"));
		// TODO: Hacer que muestre asteriscos en lugar de texto
		this.addSelectableChild(this.passwordField);

		// Login button
		this.loginButton = ButtonWidget.builder(
			Text.literal("Iniciar Sesión"),
			(button) -> this.attemptLogin()
		)
		.dimensions(centerX - 100, startY + 80, 200, 20)
		.build();
		this.addDrawableChild(this.loginButton);

		// Google OAuth button
		this.googleLoginButton = ButtonWidget.builder(
			Text.literal("🔐 Iniciar Sesión con Google"),
			(button) -> this.attemptGoogleLogin()
		)
		.dimensions(centerX - 100, startY + 140, 200, 20)
		.build();
		this.addDrawableChild(this.googleLoginButton);

		// Cancel button
		this.addDrawableChild(ButtonWidget.builder(
			Text.literal("Cancelar"),
			(button) -> this.close()
		)
		.dimensions(centerX - 100, startY + 170, 200, 20)
		.build());
	}

	private void attemptLogin() {
		String email = this.emailField.getText().trim();
		String password = this.passwordField.getText();

		// Validación básica
		if (email.isEmpty()) {
			this.errorMessage = "Por favor ingresa tu email";
			return;
		}

		if (password.isEmpty()) {
			this.errorMessage = "Por favor ingresa tu contraseña";
			return;
		}

		if (!email.contains("@")) {
			this.errorMessage = "Email inválido";
			return;
		}

		// Deshabilitar botón mientras se procesa
		this.loggingIn = true;
		this.loginButton.active = false;
		this.errorMessage = "Iniciando sesión...";

		// Llamar a API
		String apiUrl = BlanielMod.CONFIG.getApiUrl();

		BlanielAPIClient.login(apiUrl, email, password).thenAccept(response -> {
			// Ejecutar en thread principal de Minecraft
			if (this.client != null) {
				this.client.execute(() -> {
					if (response.success && response.token != null) {
						// Login exitoso
						BlanielConfig.UserData userData = new BlanielConfig.UserData(
							response.user.id,
							response.user.email,
							response.user.name,
							response.user.plan
						);

						BlanielMod.CONFIG.login(response.token, userData);

						// Mostrar mensaje de éxito
						if (this.client.player != null) {
							this.client.player.sendMessage(
								Text.literal("§a[Blaniel] §fInicio de sesión exitoso. ¡Bienvenido " + response.user.name + "!"),
								false
							);
						}

						// Cerrar pantalla
						this.close();
					} else {
						// Login fallido
						this.errorMessage = response.error != null ? response.error : "Error al iniciar sesión";
						this.loggingIn = false;
						this.loginButton.active = true;
					}
				});
			}
		}).exceptionally(ex -> {
			// Error de conexión
			if (this.client != null) {
				this.client.execute(() -> {
					this.errorMessage = "Error de conexión: " + ex.getMessage();
					this.loggingIn = false;
					this.loginButton.active = true;
				});
			}
			return null;
		});
	}

	/**
	 * Iniciar flujo de Google OAuth
	 */
	private void attemptGoogleLogin() {
		// Deshabilitar botones mientras se procesa
		this.loggingIn = true;
		this.loginButton.active = false;
		this.googleLoginButton.active = false;
		this.errorMessage = "Abriendo navegador para autorizar con Google...";

		// Configuración OAuth
		String clientId = BlanielMod.CONFIG.getGoogleClientId();
		String redirectUri = "http://127.0.0.1:8888/callback";
		String apiUrl = BlanielMod.CONFIG.getApiUrl();

		// Crear cliente OAuth
		OAuth2Client oauthClient = new OAuth2Client(clientId, redirectUri, apiUrl);

		// Iniciar flujo de autorización (asíncrono)
		oauthClient.authorize().thenAccept(response -> {
			// Ejecutar en thread principal de Minecraft
			if (this.client != null) {
				this.client.execute(() -> {
					if (response.success && response.token != null) {
						// Login exitoso
						BlanielConfig.UserData userData = new BlanielConfig.UserData(
							response.user.id,
							response.user.email,
							response.user.name,
							response.user.plan
						);

						// Guardar JWT, userData Y refresh_token de Google
						BlanielMod.CONFIG.loginWithOAuth(
							response.token,
							userData,
							response.googleRefreshToken != null ? response.googleRefreshToken : ""
						);

						// Mostrar mensaje de éxito
						if (this.client.player != null) {
							this.client.player.sendMessage(
								Text.literal("§a[Blaniel] §fInicio de sesión exitoso con Google. ¡Bienvenido " + response.user.name + "!"),
								false
							);
						}

						// Cerrar pantalla
						this.close();
					} else {
						// Login fallido
						this.errorMessage = response.error != null ? response.error : "Error al iniciar sesión con Google";
						this.loggingIn = false;
						this.loginButton.active = true;
						this.googleLoginButton.active = true;
					}
				});
			}
		}).exceptionally(ex -> {
			// Error de conexión
			if (this.client != null) {
				this.client.execute(() -> {
					this.errorMessage = "Error OAuth: " + ex.getMessage();
					this.loggingIn = false;
					this.loginButton.active = true;
					this.googleLoginButton.active = true;
				});
			}
			return null;
		});
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		if (firstRender) {
			BlanielMod.LOGGER.info("LoginScreen.render() llamado por primera vez - la pantalla se está renderizando!");
			firstRender = false;
		}
		// Fondo oscuro
		this.renderBackground(context);

		// Título
		context.drawCenteredTextWithShadow(
			this.textRenderer,
			this.title,
			this.width / 2,
			20,
			0xFFFFFF
		);

		// Subtítulo
		context.drawCenteredTextWithShadow(
			this.textRenderer,
			Text.literal("Ingresa tus credenciales o usa Google"),
			this.width / 2,
			40,
			0xAAAAAA
		);

		// Labels
		context.drawTextWithShadow(
			this.textRenderer,
			Text.literal("Email:"),
			this.width / 2 - 150,
			this.height / 2 - 72,
			0xFFFFFF
		);

		context.drawTextWithShadow(
			this.textRenderer,
			Text.literal("Contraseña:"),
			this.width / 2 - 150,
			this.height / 2 - 32,
			0xFFFFFF
		);

		// Renderizar widgets
		this.emailField.render(context, mouseX, mouseY, delta);
		this.passwordField.render(context, mouseX, mouseY, delta);
		super.render(context, mouseX, mouseY, delta);

		// Mensaje de error/estado
		if (!this.errorMessage.isEmpty()) {
			int color = this.loggingIn ? 0xFFFF00 : 0xFF5555;
			context.drawCenteredTextWithShadow(
				this.textRenderer,
				Text.literal(this.errorMessage),
				this.width / 2,
				this.height / 2 + 50,
				color
			);
		}
	}

	@Override
	public void close() {
		BlanielMod.LOGGER.info("LoginScreen.close() llamado - cerrando pantalla");
		if (this.client != null) {
			this.client.setScreen(this.parent);
		}
	}

	@Override
	public void removed() {
		BlanielMod.LOGGER.info("LoginScreen.removed() llamado - pantalla eliminada");
		super.removed();
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		// Enter para login
		if (keyCode == 257 || keyCode == 335) { // ENTER o NUMPAD_ENTER
			if (!this.loggingIn) {
				this.attemptLogin();
				return true;
			}
		}

		// ESC para cerrar
		if (keyCode == 256) { // ESC
			this.close();
			return true;
		}

		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean shouldPause() {
		return false; // No pausar el juego
	}
}
