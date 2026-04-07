package com.blaniel.minecraft.conversation.models;

import java.util.List;

/**
 * Guión conversacional completo con versionado
 */
public class ConversationScript {
	private String scriptId;
	private int version;
	private String topic;
	private String location;
	private String contextHint;
	private List<DialogueLine> lines;
	private int totalLines;
	private int duration;
	private String createdAt;
	private String updatedAt;
	private String generatedBy;
	private TimingConfig timing;

	public String getScriptId() {
		return scriptId;
	}

	public int getVersion() {
		return version;
	}

	public String getTopic() {
		return topic;
	}

	public String getLocation() {
		return location;
	}

	public String getContextHint() {
		return contextHint;
	}

	public List<DialogueLine> getLines() {
		return lines;
	}

	public int getTotalLines() {
		return totalLines;
	}

	public int getDuration() {
		return duration;
	}

	public String getCreatedAt() {
		return createdAt;
	}

	public String getUpdatedAt() {
		return updatedAt;
	}

	public String getGeneratedBy() {
		return generatedBy;
	}

	public TimingConfig getTiming() {
		return timing;
	}

	@Override
	public String toString() {
		return "ConversationScript{" +
			"scriptId='" + scriptId + '\'' +
			", version=" + version +
			", topic='" + topic + '\'' +
			", totalLines=" + totalLines +
			", generatedBy='" + generatedBy + '\'' +
			'}';
	}
}
