package com.blaniel.minecraft.conversation;

import com.blaniel.minecraft.conversation.models.ConversationScript;
import com.blaniel.minecraft.conversation.models.DialogueLine;
import com.blaniel.minecraft.integration.BlanielChatIntegration;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Reproductor de guiones conversacionales
 *
 * Ejecuta un guión localmente con timers propios, sin necesidad de HTTP requests.
 * Avanza líneas automáticamente cada 4-7 segundos con pausas en cambios de fase.
 */
public class ConversationScriptPlayer {

	private static final ScheduledExecutorService scheduler =
		Executors.newScheduledThreadPool(2);
	private static final Random random = new Random();

	private final ConversationScript script;
	private final String groupHash;
	private int currentLineIndex = 0;
	private ScheduledFuture<?> currentTimer;
	private String previousPhase = null;
	private boolean running = false;

	public ConversationScriptPlayer(String groupHash, ConversationScript script) {
		this.groupHash = groupHash;
		this.script = script;
	}

	/**
	 * Iniciar reproducción del guión
	 */
	public void start() {
		if (running) {
			System.out.println("[Script Player] Already running: " + groupHash);
			return;
		}

		currentLineIndex = 0;
		previousPhase = null;
		running = true;

		System.out.println("[Script Player] Started: " + groupHash +
			" - " + script.getTopic() +
			" (" + script.getTotalLines() + " lines)");

		scheduleNextLine();
	}

	/**
	 * Detener reproducción
	 */
	public void stop() {
		if (currentTimer != null) {
			currentTimer.cancel(false);
			currentTimer = null;
		}

		running = false;
		System.out.println("[Script Player] Stopped: " + groupHash);
	}

	/**
	 * Programar siguiente línea
	 */
	private void scheduleNextLine() {
		if (currentLineIndex >= script.getLines().size()) {
			// Conversación completada, programar loop
			scheduleLoop();
			return;
		}

		DialogueLine line = script.getLines().get(currentLineIndex);

		// Calcular delay aleatorio
		int minDelay = script.getTiming().getMinDelayBetweenLines();
		int maxDelay = script.getTiming().getMaxDelayBetweenLines();
		int delay = minDelay + random.nextInt(maxDelay - minDelay + 1);

		// Si cambió la fase, agregar pausa extra
		if (previousPhase != null && !line.getPhase().equals(previousPhase)) {
			delay += script.getTiming().getPauseAtPhaseChange();
			System.out.println("[Script Player] Phase change: " + previousPhase +
				" -> " + line.getPhase() + " (+3s pause)");
		}

		// Programar ejecución
		currentTimer = scheduler.schedule(() -> {
			playLine(line);
		}, delay, TimeUnit.SECONDS);
	}

	/**
	 * Reproducir línea
	 */
	private void playLine(DialogueLine line) {
		if (!running) {
			return;
		}

		try {
			// Mostrar chat bubble del NPC
			BlanielChatIntegration.showChatBubble(
				line.getAgentId(),
				line.getAgentName(),
				line.getMessage()
			);

			System.out.println("[Script Player] [" + line.getPhase() + "] " +
				line.getAgentName() + ": " + line.getMessage());

		} catch (Exception e) {
			System.err.println("[Script Player] Error playing line: " + e.getMessage());
			e.printStackTrace();
		}

		// Actualizar estado
		previousPhase = line.getPhase();
		currentLineIndex++;

		// Programar siguiente línea
		scheduleNextLine();
	}

	/**
	 * Programar reinicio (loop)
	 */
	private void scheduleLoop() {
		int loopDelay = script.getTiming().getLoopDelay();

		System.out.println("[Script Player] Conversation completed: " + groupHash +
			" - Will loop in " + loopDelay + " seconds");

		currentTimer = scheduler.schedule(() -> {
			System.out.println("[Script Player] Looping conversation: " + groupHash);
			start(); // Reiniciar desde el principio
		}, loopDelay, TimeUnit.SECONDS);
	}

	/**
	 * Verificar si está en ejecución
	 */
	public boolean isRunning() {
		return running;
	}

	/**
	 * Obtener groupHash
	 */
	public String getGroupHash() {
		return groupHash;
	}

	/**
	 * Obtener script
	 */
	public ConversationScript getScript() {
		return script;
	}

	/**
	 * Shutdown del scheduler (llamar al cerrar el mod)
	 */
	public static void shutdown() {
		scheduler.shutdown();
		try {
			if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
				scheduler.shutdownNow();
			}
		} catch (InterruptedException e) {
			scheduler.shutdownNow();
		}
		System.out.println("[Script Player] Scheduler shut down");
	}
}
