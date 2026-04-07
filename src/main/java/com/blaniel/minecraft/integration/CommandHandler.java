package com.blaniel.minecraft.integration;

import com.google.gson.*;
import com.blaniel.minecraft.entity.BlanielVillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.concurrent.*;

/**
 * Sistema de ejecución de comandos estructurados
 *
 * Maneja respuestas con múltiples partes (speech, command, continuation)
 * y ejecuta acciones secuencialmente con pausas cuando sea necesario.
 */
public class CommandHandler {

    private static final ScheduledExecutorService scheduler =
        Executors.newScheduledThreadPool(2);

    /**
     * Procesa respuesta estructurada del servidor
     */
    public static void processStructuredResponse(
        BlanielVillagerEntity entity,
        JsonObject response
    ) {
        try {
            // Verificar si tiene estructura de partes
            if (!response.has("parts")) {
                // Fallback: respuesta simple
                String content = response.get("content").getAsString();
                entity.displayChatBubble(content);

                String animationHint = response.has("animationHint")
                    ? response.get("animationHint").getAsString()
                    : "talking";
                entity.playAnimation(animationHint);
                return;
            }

            JsonArray parts = response.getAsJsonArray("parts");
            Queue<ResponseAction> actionQueue = buildActionQueue(entity, parts);

            // Ejecutar queue
            executeActionQueue(entity, actionQueue);

        } catch (Exception e) {
            e.printStackTrace();
            entity.displayChatBubble("§c[Error procesando respuesta]");
        }
    }

    /**
     * Construye queue de acciones desde parts
     */
    private static Queue<ResponseAction> buildActionQueue(
        BlanielVillagerEntity entity,
        JsonArray parts
    ) {
        Queue<ResponseAction> queue = new LinkedList<>();

        for (JsonElement partElem : parts) {
            JsonObject part = partElem.getAsJsonObject();
            String type = part.get("type").getAsString();

            switch (type) {
                case "speech":
                    queue.add(new SpeechAction(
                        part.get("content").getAsString(),
                        part.has("animationHint")
                            ? part.get("animationHint").getAsString()
                            : "talking"
                    ));
                    break;

                case "command":
                    JsonObject command = part.getAsJsonObject("command");
                    ResponseAction action = parseCommand(entity, command);
                    if (action != null) {
                        queue.add(action);
                    }
                    break;

                case "continuation":
                    queue.add(new SpeechAction(
                        part.get("content").getAsString(),
                        "talking"
                    ));
                    break;
            }
        }

        return queue;
    }

    /**
     * Parsea comando y crea acción correspondiente
     */
    private static ResponseAction parseCommand(
        BlanielVillagerEntity entity,
        JsonObject command
    ) {
        String type = command.get("type").getAsString();

        switch (type) {
            case "move_closer":
                UUID playerId = entity.getLastInteractedPlayerId();
                if (playerId != null) {
                    PlayerEntity player = entity.getWorld().getPlayerByUuid(playerId);
                    if (player != null) {
                        return new MoveCloserAction(player.getPos(), 3.0);
                    }
                }
                break;

            case "walk_to_agent":
                String targetId = command.get("targetAgentId").getAsString();
                BlanielVillagerEntity target = findEntityByAgentId(entity, targetId);
                if (target != null) {
                    return new WalkToAgentAction(target);
                }
                break;

            case "teleport_to_agent":
                String teleportId = command.get("targetAgentId").getAsString();
                BlanielVillagerEntity teleportTarget = findEntityByAgentId(entity, teleportId);
                if (teleportTarget != null) {
                    return new TeleportToAgentAction(teleportTarget);
                }
                break;

            case "redirect_question":
                // La redirección se maneja en el servidor
                // Aquí solo mostramos el mensaje
                return null;

            case "look_at_player":
                UUID lookPlayerId = entity.getLastInteractedPlayerId();
                if (lookPlayerId != null) {
                    return new LookAtPlayerAction(lookPlayerId);
                }
                break;

            case "look_at_agent":
                String lookTargetId = command.get("targetAgentId").getAsString();
                BlanielVillagerEntity lookTarget = findEntityByAgentId(entity, lookTargetId);
                if (lookTarget != null) {
                    return new LookAtAgentAction(lookTarget);
                }
                break;
        }

        return null;
    }

    /**
     * Ejecuta queue de acciones secuencialmente
     */
    private static void executeActionQueue(
        BlanielVillagerEntity entity,
        Queue<ResponseAction> queue
    ) {
        if (queue.isEmpty()) return;

        ResponseAction current = queue.poll();
        current.execute(entity);

        if (current.shouldPause()) {
            // Esperar a que complete antes de continuar
            ScheduledFuture<?> checkTask = scheduler.scheduleAtFixedRate(() -> {
                if (current.isComplete()) {
                    // Ejecutar en main thread de Minecraft
                    if (entity.getWorld().getServer() != null) {
                        entity.getWorld().getServer().execute(() -> {
                            executeActionQueue(entity, queue);
                        });
                    }
                }
            }, 100, 100, TimeUnit.MILLISECONDS);

            // Timeout de 30 segundos
            scheduler.schedule(() -> {
                checkTask.cancel(true);
                System.err.println("Action timeout: " + current.getClass().getSimpleName());
                if (entity.getWorld().getServer() != null) {
                    entity.getWorld().getServer().execute(() -> {
                        executeActionQueue(entity, queue);
                    });
                }
            }, 30, TimeUnit.SECONDS);

        } else {
            // Continuar después del delay normal
            scheduler.schedule(() -> {
                if (entity.getWorld().getServer() != null) {
                    entity.getWorld().getServer().execute(() -> {
                        executeActionQueue(entity, queue);
                    });
                }
            }, 2, TimeUnit.SECONDS);
        }
    }

    /**
     * Encuentra entidad por agentId
     */
    private static BlanielVillagerEntity findEntityByAgentId(
        BlanielVillagerEntity searcher,
        String agentId
    ) {
        List<BlanielVillagerEntity> entities = searcher.getWorld()
            .getEntitiesByClass(
                BlanielVillagerEntity.class,
                searcher.getBoundingBox().expand(50),
                entity -> agentId.equals(entity.getBlanielAgentId())
            );

        return entities.isEmpty() ? null : entities.get(0);
    }

    // ========================================================================
    // Acciones
    // ========================================================================

    interface ResponseAction {
        void execute(BlanielVillagerEntity entity);
        boolean isComplete();
        boolean shouldPause();
    }

    /**
     * Acción de habla (mostrar chat bubble)
     */
    static class SpeechAction implements ResponseAction {
        private final String content;
        private final String animation;
        private boolean completed = false;

        public SpeechAction(String content, String animation) {
            this.content = content;
            this.animation = animation;
        }

        @Override
        public void execute(BlanielVillagerEntity entity) {
            entity.displayChatBubble(content);
            entity.playAnimation(animation);

            // Auto-completar después de 5 segundos
            scheduler.schedule(() -> {
                completed = true;
            }, 5, TimeUnit.SECONDS);
        }

        @Override
        public boolean isComplete() {
            return completed;
        }

        @Override
        public boolean shouldPause() {
            return false;
        }
    }

    /**
     * Acción de acercarse al jugador
     */
    static class MoveCloserAction implements ResponseAction {
        private final Vec3d targetPos;
        private final double targetDistance;
        private BlanielVillagerEntity entity;

        public MoveCloserAction(Vec3d targetPos, double targetDistance) {
            this.targetPos = targetPos;
            this.targetDistance = targetDistance;
        }

        @Override
        public void execute(BlanielVillagerEntity entity) {
            this.entity = entity;

            // Calcular posición final (a targetDistance del jugador)
            Vec3d entityPos = entity.getPos();
            Vec3d direction = targetPos.subtract(entityPos).normalize();
            Vec3d finalPos = targetPos.subtract(direction.multiply(targetDistance));

            // Iniciar navegación
            entity.getNavigation().startMovingTo(
                finalPos.x,
                finalPos.y,
                finalPos.z,
                1.0 // velocidad
            );
        }

        @Override
        public boolean isComplete() {
            return entity != null && entity.getNavigation().isIdle();
        }

        @Override
        public boolean shouldPause() {
            return true;
        }
    }

    /**
     * Acción de caminar hacia otro agente
     */
    static class WalkToAgentAction implements ResponseAction {
        private final BlanielVillagerEntity target;
        private BlanielVillagerEntity entity;
        private boolean aiWasDisabled = false;

        public WalkToAgentAction(BlanielVillagerEntity target) {
            this.target = target;
        }

        @Override
        public void execute(BlanielVillagerEntity entity) {
            this.entity = entity;

            // Target se queda quieto (pausar AI)
            target.getNavigation().stop();
            target.setAiDisabled(true);
            aiWasDisabled = true;

            // Entity camina hacia target
            entity.getNavigation().startMovingTo(
                target.getX(),
                target.getY(),
                target.getZ(),
                1.0
            );
        }

        @Override
        public boolean isComplete() {
            if (entity != null && entity.getNavigation().isIdle()) {
                // Re-habilitar AI del target
                if (aiWasDisabled) {
                    target.setAiDisabled(false);
                }
                return true;
            }
            return false;
        }

        @Override
        public boolean shouldPause() {
            return true;
        }
    }

    /**
     * Acción de teletransporte (para distancias > 20m)
     */
    static class TeleportToAgentAction implements ResponseAction {
        private final BlanielVillagerEntity target;
        private boolean completed = false;

        public TeleportToAgentAction(BlanielVillagerEntity target) {
            this.target = target;
        }

        @Override
        public void execute(BlanielVillagerEntity entity) {
            // Efecto de partículas
            entity.getWorld().addParticle(
                ParticleTypes.PORTAL,
                entity.getX(),
                entity.getY() + 1,
                entity.getZ(),
                0, 0, 0
            );

            // Teletransporte
            entity.teleport(
                target.getX() + 2, // Offset para no solaparse
                target.getY(),
                target.getZ()
            );

            // Efecto de partículas en destino
            entity.getWorld().addParticle(
                ParticleTypes.PORTAL,
                entity.getX(),
                entity.getY() + 1,
                entity.getZ(),
                0, 0, 0
            );

            completed = true;
        }

        @Override
        public boolean isComplete() {
            return completed;
        }

        @Override
        public boolean shouldPause() {
            return true;
        }
    }

    /**
     * Acción de mirar al jugador
     */
    static class LookAtPlayerAction implements ResponseAction {
        private final UUID playerId;
        private boolean completed = false;

        public LookAtPlayerAction(UUID playerId) {
            this.playerId = playerId;
        }

        @Override
        public void execute(BlanielVillagerEntity entity) {
            PlayerEntity player = entity.getWorld().getPlayerByUuid(playerId);
            if (player != null) {
                entity.lookAt(
                    net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES,
                    player.getEyePos()
                );
            }
            completed = true;
        }

        @Override
        public boolean isComplete() {
            return completed;
        }

        @Override
        public boolean shouldPause() {
            return false;
        }
    }

    /**
     * Acción de mirar a otro agente
     */
    static class LookAtAgentAction implements ResponseAction {
        private final BlanielVillagerEntity target;
        private boolean completed = false;

        public LookAtAgentAction(BlanielVillagerEntity target) {
            this.target = target;
        }

        @Override
        public void execute(BlanielVillagerEntity entity) {
            entity.lookAt(
                net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES,
                target.getEyePos()
            );
            completed = true;
        }

        @Override
        public boolean isComplete() {
            return completed;
        }

        @Override
        public boolean shouldPause() {
            return false;
        }
    }
}
