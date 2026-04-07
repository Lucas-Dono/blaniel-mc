package com.blaniel.minecraft.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * Screen personalizado para chat de Blaniel
 *
 * Permite al usuario escribir un mensaje y enviarlo
 * al sistema de chat grupal avanzado.
 */
public class BlanielChatScreen extends Screen {

    private final Consumer<String> onSend;
    private TextFieldWidget messageField;

    public BlanielChatScreen(Consumer<String> onSend) {
        super(Text.literal("Chat de Blaniel"));
        this.onSend = onSend;
    }

    @Override
    protected void init() {
        super.init();

        // Crear campo de texto para el mensaje
        this.messageField = new TextFieldWidget(
            this.textRenderer,
            this.width / 2 - 150,
            this.height - 60,
            300,
            20,
            Text.literal("Mensaje")
        );

        this.messageField.setMaxLength(256);
        this.messageField.setFocused(true);
        this.messageField.setPlaceholder(Text.literal("Escribe tu mensaje..."));

        this.addSelectableChild(this.messageField);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Fondo semi-transparente
        context.fillGradient(0, 0, this.width, this.height, 0x80000000, 0x80000000);

        // Título
        context.drawCenteredTextWithShadow(
            this.textRenderer,
            "§bChat de Blaniel",
            this.width / 2,
            this.height - 90,
            0xFFFFFF
        );

        // Instrucciones
        context.drawCenteredTextWithShadow(
            this.textRenderer,
            "§7Presiona Enter para enviar, ESC para cancelar",
            this.width / 2,
            this.height - 75,
            0xAAAAAA
        );

        // Campo de texto
        this.messageField.render(context, mouseX, mouseY, delta);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            // Enviar mensaje
            String message = this.messageField.getText();
            if (!message.trim().isEmpty()) {
                this.onSend.accept(message);
            }
            this.close();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            // Cancelar
            this.close();
            return true;
        }

        // Delegar al campo de texto
        return this.messageField.keyPressed(keyCode, scanCode, modifiers)
            || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return this.messageField.charTyped(chr, modifiers)
            || super.charTyped(chr, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return this.messageField.mouseClicked(mouseX, mouseY, button)
            || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void tick() {
        this.messageField.tick();
        super.tick();
    }

    @Override
    public boolean shouldPause() {
        return false; // No pausar el juego
    }
}
