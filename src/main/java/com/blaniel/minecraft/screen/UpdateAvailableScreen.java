package com.blaniel.minecraft.screen;

import com.blaniel.minecraft.BlanielMod;
import com.blaniel.minecraft.update.ModUpdateChecker;
import com.blaniel.minecraft.update.ModUpdateDownloader;
import com.blaniel.minecraft.update.ModUpdateInfo;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Pantalla que muestra cuando hay una actualización disponible
 *
 * Permite al usuario:
 * - Actualizar ahora (descargar + reiniciar al cerrar)
 * - Actualizar al cerrar (descargar en background)
 * - Más tarde (cerrar y recordar después)
 */
public class UpdateAvailableScreen extends Screen {

	private final Screen parent;
	private final ModUpdateInfo updateInfo;

	private ButtonWidget updateNowButton;
	private ButtonWidget updateOnCloseButton;
	private ButtonWidget laterButton;

	private boolean downloading = false;
	private boolean downloadComplete = false;
	private File downloadedFile = null;
	private String statusMessage = "";

	private List<String> changelogLines;
	private int scrollOffset = 0;
	private static final int MAX_VISIBLE_LINES = 12;

	public UpdateAvailableScreen(Screen parent, ModUpdateInfo updateInfo) {
		super(Text.literal("Actualización Disponible"));
		this.parent = parent;
		this.updateInfo = updateInfo;

		// Parsear changelog en líneas
		this.changelogLines = parseChangelog(updateInfo.getChangelog());
	}

	/**
	 * Parsear changelog en líneas individuales
	 */
	private List<String> parseChangelog(String changelog) {
		List<String> lines = new ArrayList<>();

		if (changelog == null || changelog.isEmpty()) {
			lines.add("No hay información de cambios disponible.");
			return lines;
		}

		String[] rawLines = changelog.split("\n");

		for (String line : rawLines) {
			// Limitar longitud de líneas
			if (line.length() > 80) {
				// Dividir líneas largas
				int start = 0;
				while (start < line.length()) {
					int end = Math.min(start + 80, line.length());
					lines.add(line.substring(start, end));
					start = end;
				}
			} else {
				lines.add(line);
			}
		}

		return lines;
	}

	@Override
	protected void init() {
		super.init();

		int centerX = this.width / 2;
		int buttonY = this.height - 80;

		// Botón: Actualizar Ahora
		this.updateNowButton = ButtonWidget.builder(
			Text.literal("✓ Actualizar Ahora"),
			(button) -> this.updateNow()
		)
		.dimensions(centerX - 205, buttonY, 130, 20)
		.build();
		this.addDrawableChild(this.updateNowButton);

		// Botón: Actualizar al Cerrar
		this.updateOnCloseButton = ButtonWidget.builder(
			Text.literal("⏱ Actualizar al Cerrar"),
			(button) -> this.updateOnClose()
		)
		.dimensions(centerX - 65, buttonY, 130, 20)
		.build();
		this.addDrawableChild(this.updateOnCloseButton);

		// Botón: Más Tarde
		this.laterButton = ButtonWidget.builder(
			Text.literal("⏭ Más Tarde"),
			(button) -> this.close()
		)
		.dimensions(centerX + 75, buttonY, 130, 20)
		.build();
		this.addDrawableChild(this.laterButton);

		// Si es actualización requerida, deshabilitar "Más Tarde"
		if (updateInfo.isRequired()) {
			this.laterButton.active = false;
		}
	}

	/**
	 * Actualizar ahora (descargar + programar instalación + cerrar)
	 */
	private void updateNow() {
		if (downloading) {
			return;
		}

		this.downloading = true;
		this.statusMessage = "Descargando actualización...";
		this.updateNowButton.active = false;
		this.updateOnCloseButton.active = false;

		ModUpdateDownloader.downloadUpdate(updateInfo)
			.thenAccept(file -> {
				if (this.client != null) {
					this.client.execute(() -> {
						this.downloadedFile = file;
						this.downloadComplete = true;
						this.statusMessage = "✓ Descarga completa. Instalando al cerrar...";

						try {
							// Programar instalación
							ModUpdateDownloader.installUpdate(file);

							// Notificar al usuario
							if (this.client.player != null) {
								this.client.player.sendMessage(
									Text.literal("§a[Blaniel] §fActualización descargada. " +
										"Se instalará al cerrar Minecraft."),
									false
								);
							}

							// Cerrar pantalla después de 2 segundos
							new Thread(() -> {
								try {
									Thread.sleep(2000);
									if (this.client != null) {
										this.client.execute(this::close);
									}
								} catch (InterruptedException e) {
									e.printStackTrace();
								}
							}).start();

						} catch (Exception e) {
							this.statusMessage = "§c✗ Error al programar instalación: " + e.getMessage();
							this.updateNowButton.active = true;
							this.updateOnCloseButton.active = true;
						}
					});
				}
			})
			.exceptionally(ex -> {
				if (this.client != null) {
					this.client.execute(() -> {
						this.statusMessage = "§c✗ Error al descargar: " + ex.getMessage();
						this.downloading = false;
						this.updateNowButton.active = true;
						this.updateOnCloseButton.active = true;
					});
				}
				return null;
			});
	}

	/**
	 * Actualizar al cerrar (descargar en background)
	 */
	private void updateOnClose() {
		if (downloading) {
			return;
		}

		this.downloading = true;
		this.statusMessage = "Descargando en segundo plano...";
		this.updateNowButton.active = false;
		this.updateOnCloseButton.active = false;

		ModUpdateDownloader.downloadUpdate(updateInfo)
			.thenAccept(file -> {
				if (this.client != null) {
					this.client.execute(() -> {
						try {
							// Programar instalación
							ModUpdateDownloader.installUpdate(file);

							if (this.client.player != null) {
								this.client.player.sendMessage(
									Text.literal("§a[Blaniel] §fActualización descargada. " +
										"Se instalará al cerrar Minecraft."),
									false
								);
							}

							// Cerrar pantalla
							this.close();

						} catch (Exception e) {
							this.statusMessage = "§c✗ Error: " + e.getMessage();
							this.downloading = false;
							this.updateNowButton.active = true;
							this.updateOnCloseButton.active = true;
						}
					});
				}
			})
			.exceptionally(ex -> {
				if (this.client != null) {
					this.client.execute(() -> {
						this.statusMessage = "§c✗ Error al descargar: " + ex.getMessage();
						this.downloading = false;
						this.updateNowButton.active = true;
						this.updateOnCloseButton.active = true;
					});
				}
				return null;
			});
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		// Fondo oscuro
		this.renderBackground(context);

		int centerX = this.width / 2;

		// Título
		context.drawCenteredTextWithShadow(
			this.textRenderer,
			Text.literal("🚀 Nueva Actualización Disponible").formatted(Formatting.BOLD),
			centerX,
			20,
			0x55FF55
		);

		// Información de versión
		String versionText = String.format("v%s → v%s",
			ModUpdateChecker.getCurrentVersion(),
			updateInfo.getLatestVersion()
		);

		context.drawCenteredTextWithShadow(
			this.textRenderer,
			Text.literal(versionText),
			centerX,
			40,
			0xFFFFFF
		);

		// Tamaño de descarga
		context.drawCenteredTextWithShadow(
			this.textRenderer,
			Text.literal("Tamaño: " + updateInfo.getFormattedFileSize()),
			centerX,
			55,
			0xAAAAAA
		);

		// Indicador de actualización requerida
		if (updateInfo.isRequired()) {
			context.drawCenteredTextWithShadow(
				this.textRenderer,
				Text.literal("⚠ ACTUALIZACIÓN OBLIGATORIA").formatted(Formatting.BOLD),
				centerX,
				70,
				0xFF5555
			);
		}

		// Changelog
		int changelogY = updateInfo.isRequired() ? 95 : 80;

		context.drawCenteredTextWithShadow(
			this.textRenderer,
			Text.literal("─── Novedades ───").formatted(Formatting.UNDERLINE),
			centerX,
			changelogY,
			0xFFFF55
		);

		// Renderizar líneas del changelog con scroll
		int lineY = changelogY + 20;
		int maxY = this.height - 100;

		for (int i = scrollOffset; i < changelogLines.size() && lineY < maxY; i++) {
			String line = changelogLines.get(i);

			// Colorear según contenido
			int color = 0xCCCCCC;

			if (line.startsWith("#")) {
				color = 0xFFFF55; // Encabezados amarillos
			} else if (line.startsWith("-") || line.startsWith("•") || line.startsWith("✨")) {
				color = 0x55FF55; // Bullets verdes
			} else if (line.startsWith("**")) {
				color = 0xFFFFFF; // Texto importante blanco
			}

			// Centrar si es encabezado, alinear a izquierda si no
			if (line.startsWith("#")) {
				context.drawCenteredTextWithShadow(
					this.textRenderer,
					Text.literal(line.replace("#", "").trim()),
					centerX,
					lineY,
					color
				);
			} else {
				context.drawTextWithShadow(
					this.textRenderer,
					Text.literal(line),
					centerX - 200,
					lineY,
					color
				);
			}

			lineY += 10;
		}

		// Indicador de scroll si hay más contenido
		if (scrollOffset > 0) {
			context.drawCenteredTextWithShadow(
				this.textRenderer,
				Text.literal("▲ Scroll arriba para ver más"),
				centerX,
				changelogY + 15,
				0x888888
			);
		}

		if (scrollOffset + MAX_VISIBLE_LINES < changelogLines.size()) {
			context.drawCenteredTextWithShadow(
				this.textRenderer,
				Text.literal("▼ Scroll abajo para ver más"),
				centerX,
				maxY - 15,
				0x888888
			);
		}

		// Estado de descarga
		if (downloading) {
			double progress = ModUpdateDownloader.getDownloadProgress();
			int percent = (int) (progress * 100);

			// Barra de progreso
			int barWidth = 400;
			int barHeight = 20;
			int barX = centerX - barWidth / 2;
			int barY = this.height - 55;

			// Fondo de barra
			context.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF333333);

			// Progreso
			int progressWidth = (int) (barWidth * progress);
			context.fill(barX, barY, barX + progressWidth, barY + barHeight, 0xFF55FF55);

			// Texto de progreso
			context.drawCenteredTextWithShadow(
				this.textRenderer,
				Text.literal(percent + "%"),
				centerX,
				barY + 5,
				0xFFFFFF
			);
		}

		// Mensaje de estado
		if (!statusMessage.isEmpty()) {
			int color = statusMessage.contains("✗") ? 0xFF5555 : 0x55FF55;

			context.drawCenteredTextWithShadow(
				this.textRenderer,
				Text.literal(statusMessage),
				centerX,
				this.height - 30,
				color
			);
		}

		// Renderizar botones
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		// Scroll del changelog
		if (amount > 0) {
			// Scroll arriba
			scrollOffset = Math.max(0, scrollOffset - 1);
		} else {
			// Scroll abajo
			int maxScroll = Math.max(0, changelogLines.size() - MAX_VISIBLE_LINES);
			scrollOffset = Math.min(maxScroll, scrollOffset + 1);
		}

		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		// ESC para cerrar (solo si no es requerida o ya descargó)
		if (keyCode == 256) { // ESC
			if (!updateInfo.isRequired() || downloadComplete) {
				this.close();
				return true;
			}
		}

		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void close() {
		if (this.client != null) {
			this.client.setScreen(this.parent);
		}
	}

	@Override
	public boolean shouldPause() {
		return true; // Pausar el juego mientras se muestra
	}
}
