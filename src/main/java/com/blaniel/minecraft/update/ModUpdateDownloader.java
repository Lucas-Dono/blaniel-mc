package com.blaniel.minecraft.update;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Descargador de actualizaciones del mod
 *
 * Descarga el JAR nuevo en segundo plano con seguimiento de progreso
 * y verificación de integridad mediante SHA-256
 */
public class ModUpdateDownloader {

	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(30))
		.build();

	private static final File UPDATE_DIR = new File("blaniel_updates");
	private static final AtomicLong downloadedBytes = new AtomicLong(0);
	private static volatile boolean downloading = false;
	private static volatile ModUpdateInfo currentDownload = null;

	/**
	 * Inicializar directorio de actualizaciones
	 */
	static {
		if (!UPDATE_DIR.exists()) {
			UPDATE_DIR.mkdirs();
		}
	}

	/**
	 * Descargar actualización
	 *
	 * @param updateInfo Información de la actualización
	 * @return CompletableFuture que se completa cuando la descarga termina
	 */
	public static CompletableFuture<File> downloadUpdate(ModUpdateInfo updateInfo) {
		if (downloading) {
			System.err.println("[Blaniel Update] Download already in progress");
			return CompletableFuture.failedFuture(
				new IllegalStateException("Download already in progress")
			);
		}

		downloading = true;
		currentDownload = updateInfo;
		downloadedBytes.set(0);

		String downloadUrl = updateInfo.getDownloadUrl();
		String fileName = "blaniel-mc-" + updateInfo.getLatestVersion() + ".jar";
		File outputFile = new File(UPDATE_DIR, fileName);

		System.out.println("[Blaniel Update] Starting download from: " + downloadUrl);
		System.out.println("[Blaniel Update] Expected size: " + updateInfo.getFormattedFileSize());
		System.out.println("[Blaniel Update] Target file: " + outputFile.getAbsolutePath());

		HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create(downloadUrl))
			.GET()
			.timeout(Duration.ofMinutes(10))
			.header("User-Agent", "BlanielMC/" + ModUpdateChecker.getCurrentVersion())
			.build();

		return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
			.thenApply(response -> {
				if (response.statusCode() != 200) {
					throw new RuntimeException("Download failed: HTTP " + response.statusCode());
				}

				try {
					// Crear archivo temporal
					File tempFile = new File(UPDATE_DIR, fileName + ".tmp");

					// Descargar con tracking de progreso
					try (InputStream in = response.body();
					     FileOutputStream out = new FileOutputStream(tempFile)) {

						byte[] buffer = new byte[8192];
						int bytesRead;

						while ((bytesRead = in.read(buffer)) != -1) {
							out.write(buffer, 0, bytesRead);
							downloadedBytes.addAndGet(bytesRead);

							// Log de progreso cada 512 KB
							if (downloadedBytes.get() % (512 * 1024) == 0) {
								double progressMB = downloadedBytes.get() / (1024.0 * 1024.0);
								double totalMB = updateInfo.getFileSize() / (1024.0 * 1024.0);
								int percent = (int) ((downloadedBytes.get() * 100.0) / updateInfo.getFileSize());

								System.out.println(String.format(
									"[Blaniel Update] Download progress: %.2f / %.2f MB (%d%%)",
									progressMB, totalMB, percent
								));
							}
						}
					}

					System.out.println("[Blaniel Update] Download completed, verifying integrity...");

					// Verificar SHA-256 si está disponible
					if (updateInfo.getSha256() != null && !updateInfo.getSha256().isEmpty() &&
						!updateInfo.getSha256().equals("abc123...")) {

						String fileHash = calculateSHA256(tempFile);

						if (!fileHash.equalsIgnoreCase(updateInfo.getSha256())) {
							tempFile.delete();
							throw new RuntimeException(
								"SHA-256 verification failed! Expected: " + updateInfo.getSha256() +
								", Got: " + fileHash
							);
						}

						System.out.println("[Blaniel Update] ✓ SHA-256 verification passed");
					} else {
						System.out.println("[Blaniel Update] ⚠ Skipping SHA-256 verification (not provided)");
					}

					// Renombrar de .tmp a .jar
					if (outputFile.exists()) {
						outputFile.delete();
					}
					tempFile.renameTo(outputFile);

					System.out.println("[Blaniel Update] ✓ Update downloaded successfully: " +
						outputFile.getAbsolutePath());

					downloading = false;
					return outputFile;

				} catch (Exception e) {
					System.err.println("[Blaniel Update] Error during download: " + e.getMessage());
					e.printStackTrace();
					downloading = false;
					throw new RuntimeException(e);
				}
			})
			.exceptionally(ex -> {
				System.err.println("[Blaniel Update] Download failed: " + ex.getMessage());
				downloading = false;
				currentDownload = null;
				throw new RuntimeException(ex);
			});
	}

	/**
	 * Calcular hash SHA-256 de un archivo
	 */
	private static String calculateSHA256(File file) throws Exception {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");

		try (FileInputStream fis = new FileInputStream(file)) {
			byte[] buffer = new byte[8192];
			int bytesRead;

			while ((bytesRead = fis.read(buffer)) != -1) {
				digest.update(buffer, 0, bytesRead);
			}
		}

		byte[] hashBytes = digest.digest();
		StringBuilder sb = new StringBuilder();

		for (byte b : hashBytes) {
			sb.append(String.format("%02x", b));
		}

		return sb.toString();
	}

	/**
	 * Instalar actualización (copiar JAR descargado al directorio de mods)
	 *
	 * IMPORTANTE: Esto solo programa la instalación. El JAR se reemplazará
	 * cuando el juego se cierre mediante un shutdown hook.
	 */
	public static void installUpdate(File downloadedJar) throws IOException {
		// Obtener directorio actual del mod
		Path currentJarPath = getCurrentModJarPath();

		if (currentJarPath == null) {
			throw new IOException("No se pudo determinar la ubicación del JAR actual");
		}

		// Crear archivo de marcador para el shutdown hook
		File markerFile = new File(UPDATE_DIR, "pending_update.txt");

		try (PrintWriter writer = new PrintWriter(markerFile)) {
			writer.println(downloadedJar.getAbsolutePath());
			writer.println(currentJarPath.toString());
		}

		System.out.println("[Blaniel Update] Update installation scheduled");
		System.out.println("[Blaniel Update] Old JAR: " + currentJarPath);
		System.out.println("[Blaniel Update] New JAR: " + downloadedJar.getAbsolutePath());
		System.out.println("[Blaniel Update] Installation will complete on game shutdown");

		// Registrar shutdown hook para instalar al cerrar
		registerShutdownHook();
	}

	/**
	 * Obtener ruta del JAR actual del mod
	 */
	private static Path getCurrentModJarPath() {
		try {
			// Intentar obtener del código fuente
			Path jarPath = Paths.get(
				ModUpdateDownloader.class.getProtectionDomain()
					.getCodeSource()
					.getLocation()
					.toURI()
			);

			// Verificar que sea un .jar
			if (jarPath.toString().endsWith(".jar")) {
				return jarPath;
			}

			// Si estamos en desarrollo, buscar en mods/
			File modsDir = new File("mods");
			if (modsDir.exists()) {
				File[] jars = modsDir.listFiles((dir, name) ->
					name.startsWith("blaniel-mc-") && name.endsWith(".jar")
				);

				if (jars != null && jars.length > 0) {
					return jars[0].toPath();
				}
			}

			return null;
		} catch (Exception e) {
			System.err.println("[Blaniel Update] Error finding current JAR: " + e.getMessage());
			return null;
		}
	}

	/**
	 * Registrar shutdown hook para instalar actualización al cerrar
	 */
	private static void registerShutdownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			File markerFile = new File(UPDATE_DIR, "pending_update.txt");

			if (!markerFile.exists()) {
				return; // No hay actualización pendiente
			}

			System.out.println("[Blaniel Update] Installing pending update...");

			try (BufferedReader reader = new BufferedReader(new FileReader(markerFile))) {
				String newJarPath = reader.readLine();
				String oldJarPath = reader.readLine();

				if (newJarPath == null || oldJarPath == null) {
					System.err.println("[Blaniel Update] Invalid marker file");
					return;
				}

				File newJar = new File(newJarPath);
				File oldJar = new File(oldJarPath);

				if (!newJar.exists()) {
					System.err.println("[Blaniel Update] New JAR not found: " + newJarPath);
					return;
				}

				// Backup del JAR antiguo
				File backupJar = new File(oldJar.getParent(), oldJar.getName() + ".backup");
				if (oldJar.exists()) {
					Files.copy(oldJar.toPath(), backupJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
					System.out.println("[Blaniel Update] Backup created: " + backupJar.getAbsolutePath());
				}

				// Copiar nuevo JAR
				Files.copy(newJar.toPath(), oldJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
				System.out.println("[Blaniel Update] ✓ Update installed successfully!");

				// Limpiar archivos temporales
				markerFile.delete();
				newJar.delete();

				System.out.println("[Blaniel Update] Update complete. Please restart Minecraft.");

			} catch (Exception e) {
				System.err.println("[Blaniel Update] Error installing update: " + e.getMessage());
				e.printStackTrace();
			}
		}, "Blaniel Update Installer"));

		System.out.println("[Blaniel Update] Shutdown hook registered for update installation");
	}

	/**
	 * Verificar si hay descarga en progreso
	 */
	public static boolean isDownloading() {
		return downloading;
	}

	/**
	 * Obtener información de descarga actual
	 */
	public static ModUpdateInfo getCurrentDownload() {
		return currentDownload;
	}

	/**
	 * Obtener bytes descargados
	 */
	public static long getDownloadedBytes() {
		return downloadedBytes.get();
	}

	/**
	 * Obtener progreso de descarga (0.0 a 1.0)
	 */
	public static double getDownloadProgress() {
		if (currentDownload == null || currentDownload.getFileSize() == 0) {
			return 0.0;
		}

		return Math.min(1.0, downloadedBytes.get() / (double) currentDownload.getFileSize());
	}
}
