package com.blaniel.minecraft.conversation.models;

/**
 * Metadata ligera del script (sin las líneas completas)
 * Usado para verificación de versiones
 */
public class ScriptMetadata {
	private String scriptId;
	private String groupHash;
	private int version;
	private String topic;
	private int totalLines;
	private String updatedAt;
	private String generatedBy;

	public String getScriptId() {
		return scriptId;
	}

	public String getGroupHash() {
		return groupHash;
	}

	public int getVersion() {
		return version;
	}

	public String getTopic() {
		return topic;
	}

	public int getTotalLines() {
		return totalLines;
	}

	public String getUpdatedAt() {
		return updatedAt;
	}

	public String getGeneratedBy() {
		return generatedBy;
	}

	@Override
	public String toString() {
		return "ScriptMetadata{" +
			"scriptId='" + scriptId + '\'' +
			", version=" + version +
			", topic='" + topic + '\'' +
			", totalLines=" + totalLines +
			'}';
	}
}
