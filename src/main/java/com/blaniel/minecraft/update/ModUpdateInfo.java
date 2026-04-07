package com.blaniel.minecraft.update;

import com.google.gson.annotations.SerializedName;

/**
 * Información de actualización del mod
 *
 * Representa los datos devueltos por el endpoint de versión
 */
public class ModUpdateInfo {

	@SerializedName("latestVersion")
	private String latestVersion;

	@SerializedName("currentVersion")
	private String currentVersion;

	@SerializedName("hasUpdate")
	private boolean hasUpdate;

	@SerializedName("updateAvailable")
	private boolean updateAvailable;

	@SerializedName("downloadUrl")
	private String downloadUrl;

	@SerializedName("changelog")
	private String changelog;

	@SerializedName("releaseDate")
	private String releaseDate;

	@SerializedName("required")
	private boolean required;

	@SerializedName("minimumVersion")
	private String minimumVersion;

	@SerializedName("fileSize")
	private long fileSize;

	@SerializedName("sha256")
	private String sha256;

	// Getters
	public String getLatestVersion() {
		return latestVersion;
	}

	public String getCurrentVersion() {
		return currentVersion;
	}

	public boolean hasUpdate() {
		return hasUpdate;
	}

	public boolean isUpdateAvailable() {
		return updateAvailable;
	}

	public String getDownloadUrl() {
		return downloadUrl;
	}

	public String getChangelog() {
		return changelog;
	}

	public String getReleaseDate() {
		return releaseDate;
	}

	public boolean isRequired() {
		return required;
	}

	public String getMinimumVersion() {
		return minimumVersion;
	}

	public long getFileSize() {
		return fileSize;
	}

	public String getSha256() {
		return sha256;
	}

	/**
	 * Obtener tamaño formateado legible
	 */
	public String getFormattedFileSize() {
		long bytes = fileSize;

		if (bytes < 1024) {
			return bytes + " B";
		} else if (bytes < 1024 * 1024) {
			return String.format("%.2f KB", bytes / 1024.0);
		} else {
			return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
		}
	}
}
