package com.blaniel.minecraft.ai;

import com.blaniel.minecraft.BlanielMod;
import com.blaniel.minecraft.entity.BlanielVillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NPC Social Grouping System
 *
 * Manages dynamic formation of social groups among NPCs,
 * with affinity based on past interactions.
 */
public class SocialGroupingSystem {

    // Active groups: groupId -> set of entity IDs
    private final Map<UUID, Set<Integer>> activeGroups = new ConcurrentHashMap<>();

    // Meeting positions: groupId -> central position
    private final Map<UUID, GroupMeetingPoint> groupMeetingPoints = new ConcurrentHashMap<>();

    // Affinity between NPCs: "npcA_npcB" -> score (0.0-1.0)
    private final Map<String, Float> npcAffinity = new ConcurrentHashMap<>();

    // Group history: hash of NPC combination -> count
    private final Map<String, Integer> groupHistory = new ConcurrentHashMap<>();

    // Cooldown to prevent immediate regrouping
    private final Map<Integer, Long> disbandCooldown = new ConcurrentHashMap<>();

    // Constants
    private static final double PROXIMITY_RADIUS = 8.0; // Blocks to detect nearby NPCs
    private static final double GROUP_FORMATION_RADIUS = 5.0; // Radius of formed group
    private static final int MIN_GROUP_SIZE = 2;
    private static final int MAX_GROUP_SIZE = 8;
    private static final long COOLDOWN_MS = 30000; // 30 seconds before regrouping
    private static final float BASE_GROUP_PROBABILITY = 0.7f; // 70% base probability

    // Singleton
    private static SocialGroupingSystem instance;

    public static SocialGroupingSystem getInstance() {
        if (instance == null) {
            instance = new SocialGroupingSystem();
        }
        return instance;
    }

    /**
     * Evaluate group formation in the world
     * Call every 5 seconds from the server
     */
    public void evaluateGroupFormation(ServerWorld world) {
        List<BlanielVillagerEntity> allNPCs = world.getEntitiesByClass(
            BlanielVillagerEntity.class,
            new Box(-30000000, -64, -30000000, 30000000, 320, 30000000), // Entire world
            npc -> npc.isAlive() && !npc.isInConversation()
        );

        if (allNPCs.size() < MIN_GROUP_SIZE) {
            return; // Not enough NPCs
        }

        // Group NPCs by proximity
        Set<BlanielVillagerEntity> processed = new HashSet<>();

        for (BlanielVillagerEntity npc : allNPCs) {
            if (processed.contains(npc)) {
                continue;
            }

            // Check cooldown
            Long lastDisband = disbandCooldown.get(npc.getId());
            if (lastDisband != null && System.currentTimeMillis() - lastDisband < COOLDOWN_MS) {
                continue;
            }

            // Find nearby NPCs
            List<BlanielVillagerEntity> nearby = findNearbyNPCs(npc, allNPCs, processed);

            if (nearby.size() >= MIN_GROUP_SIZE - 1) { // -1 because it doesn't include itself
                // Decide if forming group
                float probability = calculateGroupProbability(npc, nearby);

                if (world.random.nextFloat() < probability) {
                    // Form group
                    nearby.add(npc);
                    formGroup(world, nearby);
                    processed.addAll(nearby);

                    BlanielMod.LOGGER.info("Group formed: {} NPCs (probability: {}%)",
                        nearby.size(), (int)(probability * 100));
                }
            }
        }
    }

    /**
     * Buscar NPCs cercanos no procesados
     */
    private List<BlanielVillagerEntity> findNearbyNPCs(
        BlanielVillagerEntity origin,
        List<BlanielVillagerEntity> allNPCs,
        Set<BlanielVillagerEntity> processed
    ) {
        List<BlanielVillagerEntity> nearby = new ArrayList<>();

        for (BlanielVillagerEntity npc : allNPCs) {
            if (npc == origin || processed.contains(npc)) {
                continue;
            }

            double distance = origin.distanceTo(npc);
            if (distance <= PROXIMITY_RADIUS) {
                nearby.add(npc);
            }
        }

        // Ordenar por afinidad (mayor primero)
        nearby.sort((a, b) -> Float.compare(
            getAffinity(origin.getId(), b.getId()),
            getAffinity(origin.getId(), a.getId())
        ));

        // Limitar tamaño del grupo (aleatorio entre MIN y MAX)
        int targetSize = MIN_GROUP_SIZE + origin.getWorld().random.nextInt(MAX_GROUP_SIZE - MIN_GROUP_SIZE + 1);
        if (nearby.size() > targetSize - 1) {
            nearby = nearby.subList(0, targetSize - 1);
        }

        return nearby;
    }

    /**
     * Calcular probabilidad de formación de grupo
     * Basado en afinidad y historial
     */
    private float calculateGroupProbability(BlanielVillagerEntity origin, List<BlanielVillagerEntity> nearby) {
        float probability = BASE_GROUP_PROBABILITY;

        // Bonus por afinidad alta
        float avgAffinity = 0;
        for (BlanielVillagerEntity npc : nearby) {
            avgAffinity += getAffinity(origin.getId(), npc.getId());
        }
        avgAffinity /= nearby.size();

        // Afinidad aumenta probabilidad hasta 95%
        probability += avgAffinity * 0.25f;

        // Bonus si este grupo ya se formó antes
        String groupHash = getGroupHash(origin, nearby);
        Integer pastFormations = groupHistory.get(groupHash);
        if (pastFormations != null && pastFormations > 0) {
            probability += 0.1f; // +10% si ya conversaron antes
        }

        return Math.min(probability, 0.95f); // Máximo 95%
    }

    /**
     * Formar un grupo social
     */
    private void formGroup(ServerWorld world, List<BlanielVillagerEntity> members) {
        UUID groupId = UUID.randomUUID();
        Set<Integer> memberIds = new HashSet<>();

        // Calcular punto de reunión (centro del grupo)
        double centerX = 0, centerY = 0, centerZ = 0;
        for (BlanielVillagerEntity npc : members) {
            centerX += npc.getX();
            centerY += npc.getY();
            centerZ += npc.getZ();
            memberIds.add(npc.getId());
        }
        centerX /= members.size();
        centerY /= members.size();
        centerZ /= members.size();

        net.minecraft.util.math.Vec3d meetingPoint = new net.minecraft.util.math.Vec3d(centerX, centerY, centerZ);

        // Registrar grupo
        activeGroups.put(groupId, memberIds);
        groupMeetingPoints.put(groupId, new GroupMeetingPoint(centerX, centerY, centerZ, GROUP_FORMATION_RADIUS));

        // Notificar a cada NPC para que se una al grupo
        for (BlanielVillagerEntity npc : members) {
            npc.joinGroup(groupId, meetingPoint);
        }

        // Incrementar historial
        String groupHash = getGroupHash(members);
        groupHistory.merge(groupHash, 1, Integer::sum);

        // Incrementar afinidad entre todos los miembros
        for (int i = 0; i < members.size(); i++) {
            for (int j = i + 1; j < members.size(); j++) {
                increaseAffinity(members.get(i).getId(), members.get(j).getId(), 0.1f);
            }
        }

        BlanielMod.LOGGER.info("Grupo {} formado en ({}, {}, {}) con {} miembros",
            groupId, (int)centerX, (int)centerY, (int)centerZ, members.size());
    }

    /**
     * Disolver un grupo
     */
    public void disbandGroup(UUID groupId, ServerWorld world) {
        Set<Integer> memberIds = activeGroups.remove(groupId);
        groupMeetingPoints.remove(groupId);

        if (memberIds != null) {
            // Notificar a cada NPC para que salga del grupo
            for (Integer memberId : memberIds) {
                var entity = world.getEntityById(memberId);
                if (entity instanceof BlanielVillagerEntity) {
                    ((BlanielVillagerEntity) entity).leaveGroup();
                }

                // Aplicar cooldown
                disbandCooldown.put(memberId, System.currentTimeMillis());
            }

            BlanielMod.LOGGER.info("Grupo {} disuelto ({} miembros)", groupId, memberIds.size());
        }
    }

    /**
     * Obtener o inicializar afinidad entre dos NPCs
     */
    private float getAffinity(int npcA, int npcB) {
        String key = getAffinityKey(npcA, npcB);
        return npcAffinity.getOrDefault(key, 0.0f);
    }

    /**
     * Aumentar afinidad entre dos NPCs
     */
    private void increaseAffinity(int npcA, int npcB, float amount) {
        String key = getAffinityKey(npcA, npcB);
        float current = npcAffinity.getOrDefault(key, 0.0f);
        npcAffinity.put(key, Math.min(current + amount, 1.0f)); // Máximo 1.0
    }

    /**
     * Generar key de afinidad (ordenado para que A-B = B-A)
     */
    private String getAffinityKey(int npcA, int npcB) {
        int min = Math.min(npcA, npcB);
        int max = Math.max(npcA, npcB);
        return min + "_" + max;
    }

    /**
     * Generar hash de grupo para historial
     */
    private String getGroupHash(BlanielVillagerEntity origin, List<BlanielVillagerEntity> nearby) {
        List<Integer> ids = new ArrayList<>();
        ids.add(origin.getId());
        nearby.forEach(npc -> ids.add(npc.getId()));
        Collections.sort(ids);
        return ids.toString();
    }

    private String getGroupHash(List<BlanielVillagerEntity> members) {
        List<Integer> ids = new ArrayList<>();
        members.forEach(npc -> ids.add(npc.getId()));
        Collections.sort(ids);
        return ids.toString();
    }

    /**
     * Verificar si un jugador está cerca de algún grupo
     */
    public UUID findNearbyGroup(PlayerEntity player) {
        for (Map.Entry<UUID, GroupMeetingPoint> entry : groupMeetingPoints.entrySet()) {
            GroupMeetingPoint point = entry.getValue();
            double distance = Math.sqrt(
                Math.pow(player.getX() - point.x, 2) +
                Math.pow(player.getY() - point.y, 2) +
                Math.pow(player.getZ() - point.z, 2)
            );

            if (distance <= point.radius + 3.0) { // +3 bloques de margen
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Obtener miembros de un grupo
     */
    public Set<Integer> getGroupMembers(UUID groupId) {
        return activeGroups.get(groupId);
    }

    /**
     * Punto de reunión de un grupo
     */
    private static class GroupMeetingPoint {
        final double x, y, z;
        final double radius;

        GroupMeetingPoint(double x, double y, double z, double radius) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
        }
    }
}
