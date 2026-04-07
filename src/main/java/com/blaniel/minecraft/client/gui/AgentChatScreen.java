package com.blaniel.minecraft.client.gui;

import com.blaniel.minecraft.entity.BlanielVillagerEntity;
import com.blaniel.minecraft.network.NetworkHandler;
import com.blaniel.minecraft.network.packet.ChatMessagePacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * Pantalla para chatear con un aldeano
 */
public class AgentChatScreen extends Screen {

    private final BlanielVillagerEntity villager;
    private TextFieldWidget messageField;

    public AgentChatScreen(BlanielVillagerEntity villager) {
        super(Text.literal("Chat con " + villager.getBlanielAgentName()));
        this.villager = villager;
    }

    @Override
    protected void init() {
        super.init();

        // Campo de texto para el mensaje
        this.messageField = new TextFieldWidget(
            this.textRenderer,
            this.width / 2 - 150,
            this.height - 60,
            300,
            20,
            Text.literal("Escribe tu mensaje...")
        );
        this.messageField.setMaxLength(500);
        this.messageField.setFocused(true);
        this.addSelectableChild(this.messageField);

        // Botón enviar
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("Enviar"),
            button -> sendMessage()
        )
        .dimensions(this.width / 2 - 50, this.height - 35, 100, 20)
        .build());
    }

    /**
     * Enviar mensaje al agente
     */
    private void sendMessage() {
        String message = this.messageField.getText().trim();

        if (message.isEmpty()) {
            return;
        }

        // Enviar packet al servidor
        ClientPlayNetworking.send(
            NetworkHandler.CHAT_MESSAGE_PACKET,
            ChatMessagePacket.create(villager.getId(), message)
        );

        // Limpiar campo
        this.messageField.setText("");

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

        // Información del agente
        context.drawCenteredTextWithShadow(
            this.textRenderer,
            Text.literal("Agente ID: " + villager.getBlanielAgentId()),
            this.width / 2,
            40,
            0xAAAAAA
        );

        // Instrucciones
        context.drawCenteredTextWithShadow(
            this.textRenderer,
            Text.literal("Escribe tu mensaje y presiona Enviar"),
            this.width / 2,
            this.height - 85,
            0xFFFFFF
        );

        // Dibujar campo de texto
        this.messageField.render(context, mouseX, mouseY, delta);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Enter para enviar
        if (keyCode == 257 || keyCode == 335) { // Enter o Keypad Enter
            sendMessage();
            return true;
        }

        // ESC para cerrar
        if (keyCode == 256) { // ESC
            this.close();
            return true;
        }

        // Pasar eventos al campo de texto
        if (this.messageField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (this.messageField.charTyped(chr, modifiers)) {
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.messageField.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean shouldPause() {
        return false; // No pausar el juego
    }
}
