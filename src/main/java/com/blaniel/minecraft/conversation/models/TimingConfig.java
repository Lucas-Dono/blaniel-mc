package com.blaniel.minecraft.conversation.models;

/**
 * Configuración de timing para reproducción de guiones
 */
public class TimingConfig {
	private int minDelayBetweenLines = 4;
	private int maxDelayBetweenLines = 7;
	private int pauseAtPhaseChange = 3;
	private int loopDelay = 180;

	public int getMinDelayBetweenLines() {
		return minDelayBetweenLines;
	}

	public int getMaxDelayBetweenLines() {
		return maxDelayBetweenLines;
	}

	public int getPauseAtPhaseChange() {
		return pauseAtPhaseChange;
	}

	public int getLoopDelay() {
		return loopDelay;
	}
}
