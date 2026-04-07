package com.blaniel.minecraft.client.gui;

import com.blaniel.minecraft.BlanielMod;
import com.blaniel.minecraft.network.NetworkHandler;
import com.blaniel.minecraft.network.packet.SpawnAgentPacket;
import com.blaniel.minecraft.network.BlanielAPIClient;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Pantalla para seleccionar y spawnear agentes
 */
public class AgentSelectionScreen extends Screen {

    private List<BlanielAPIClient.AgentData> agents = new ArrayList<>();
    private int scrollOffset = 0;
    private static final int AGENTS_PER_PAGE = 8;
    private boolean loading = true;
    private String errorMessage = null;

    public AgentSelectionScreen() {
        super(Text.literal("Seleccionar Agente"));
    }

    @Override
    protected void init() {
        super.init();
        loadAgents();
    }

    /**
     * Cargar agentes desde la API
     */
    private void loadAgents() {
        String apiUrl = BlanielMod.CONFIG.getApiUrl();
        String jwtToken = BlanielMod.CONFIG.getJwtToken();
        BlanielAPIClient client = new BlanielAPIClient(apiUrl, jwtToken);

        client.getAvailableAgents().thenAccept(response -> {
            if (response != null && response.agents != null) {
                this.agents = java.util.Arrays.asList(response.agents);
                this.loading = false;
                // Reconstruir botones en el thread principal
                if (this.client != null) {
                    this.client.execute(this::rebuildWidgets);
                }
            } else {
                this.errorMessage = "No se pudo cargar la lista de agentes";
                this.loading = false;
            }
        }).exceptionally(ex -> {
            this.errorMessage = "Error: " + ex.getMessage();
            this.loading = false;
            return null;
        });
    }

    /**
     * Reconstruir widgets después de cargar agentes
     */
    private void rebuildWidgets() {
        this.clearChildren();

        if (!agents.isEmpty()) {
            // Botones de scroll
            if (scrollOffset > 0) {
                this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("↑ Anterior"),
                    button -> {
                        scrollOffset = Math.max(0, scrollOffset - AGENTS_PER_PAGE);
                        rebuildWidgets();
                    }
                )
                .dimensions(this.width / 2 - 100, 40, 200, 20)
                .build());
            }

            if (scrollOffset + AGENTS_PER_PAGE < agents.size()) {
                this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("↓ Siguiente"),
                    button -> {
                        scrollOffset = Math.min(agents.size() - AGENTS_PER_PAGE, scrollOffset + AGENTS_PER_PAGE);
                        rebuildWidgets();
                    }
                )
                .dimensions(this.width / 2 - 100, this.height - 40, 200, 20)
                .build());
            }

            // Botones de agentes
            int y = 70;
            int endIndex = Math.min(scrollOffset + AGENTS_PER_PAGE, agents.size());

            for (int i = scrollOffset; i < endIndex; i++) {
                BlanielAPIClient.AgentData agent = agents.get(i);

                String displayText = agent.name;
                if (agent.age != null) {
                    displayText += " (" + agent.age + " años)";
                }
                if (agent.profession != null && !agent.profession.equals("VILLAGER")) {
                    displayText += " - " + agent.profession;
                }

                final String agentId = agent.id;
                final String agentName = agent.name;

                this.addDrawableChild(ButtonWidget.builder(
                    Text.literal(displayText),
                    button -> spawnAgent(agentId, agentName)
                )
                .dimensions(this.width / 2 - 150, y, 300, 20)
                .build());

                y += 25;
            }
        }

        // Botón cerrar
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("Cerrar"),
            button -> this.close()
        )
        .dimensions(this.width / 2 - 50, this.height - 30, 100, 20)
        .build());
    }

    /**
     * Spawnear un agente
     */
    private void spawnAgent(String agentId, String agentName) {
        if (this.client == null || this.client.player == null) {
            BlanielMod.LOGGER.error("No se puede spawnear: client o player es null");
            return;
        }

        BlanielMod.LOGGER.info("Enviando SPAWN_AGENT_PACKET al servidor: agentId={}, agentName={}", agentId, agentName);

        // Enviar packet al servidor
        ClientPlayNetworking.send(
            NetworkHandler.SPAWN_AGENT_PACKET,
            SpawnAgentPacket.create(agentId, agentName)
        );

        BlanielMod.LOGGER.info("SPAWN_AGENT_PACKET enviado exitosamente");

        // Cerrar GUI
        this.close();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Fondo oscuro
        this.renderBackground(context);

        // Título
        context.drawCenteredTextWithShadow(
            this.textRenderer,
            this.title,
            this.width / 2,
            20,
            0xFFFFFF
        );

        if (loading) {
            context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("Cargando agentes..."),
                this.width / 2,
                this.height / 2,
                0xFFFFFF
            );
        } else if (errorMessage != null) {
            context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal(errorMessage),
                this.width / 2,
                this.height / 2,
                0xFF5555
            );
        } else if (agents.isEmpty()) {
            context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("No hay agentes disponibles"),
                this.width / 2,
                this.height / 2,
                0xFFFFFF
            );
        } else {
            // Mostrar contador
            String counter = "Mostrando " + (scrollOffset + 1) + "-" +
                Math.min(scrollOffset + AGENTS_PER_PAGE, agents.size()) +
                " de " + agents.size();
            context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal(counter),
                this.width / 2,
                55,
                0xAAAAAA
            );
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false; // No pausar el juego
    }
}
