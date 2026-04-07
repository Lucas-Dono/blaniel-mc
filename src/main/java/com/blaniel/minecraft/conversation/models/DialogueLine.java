package com.blaniel.minecraft.conversation.models;

/**
 * Línea individual de diálogo en una conversación
 */
public class DialogueLine {
	private String agentId;
	private String agentName;
	private String message;
	private String phase;
	private int lineNumber;

	public String getAgentId() {
		return agentId;
	}

	public String getAgentName() {
		return agentName;
	}

	public String getMessage() {
		return message;
	}

	public String getPhase() {
		return phase;
	}

	public int getLineNumber() {
		return lineNumber;
	}

	@Override
	public String toString() {
		return "DialogueLine{" +
			"agentName='" + agentName + '\'' +
			", message='" + message + '\'' +
			", phase='" + phase + '\'' +
			", lineNumber=" + lineNumber +
			'}';
	}
}
