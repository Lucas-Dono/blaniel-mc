package com.blaniel.minecraft.oauth;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;

/**
 * Utilidad para abrir URLs en el navegador del sistema
 *
 * Soporta múltiples plataformas (Windows, macOS, Linux)
 */
public class BrowserLauncher {

    /**
     * Abrir URL en el navegador predeterminado del sistema
     *
     * @param url URL a abrir
     * @throws IOException Si no se puede abrir el navegador
     */
    public static void openURL(String url) throws IOException {
        String os = System.getProperty("os.name").toLowerCase();

        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            // Método 1: Desktop API (Java 6+, funciona en Windows, macOS, Linux con GNOME/KDE)
            try {
                Desktop.getDesktop().browse(URI.create(url));
                System.out.println("[BrowserLauncher] URL abierta con Desktop API: " + url);
                return;
            } catch (Exception e) {
                System.err.println("[BrowserLauncher] Desktop API falló: " + e.getMessage());
                // Continuar con métodos alternativos
            }
        }

        // Método 2: Comandos específicos por sistema operativo
        Runtime runtime = Runtime.getRuntime();

        try {
            if (os.contains("win")) {
                // Windows: rundll32 o cmd /c start
                runtime.exec(new String[]{"rundll32", "url.dll,FileProtocolHandler", url});
                System.out.println("[BrowserLauncher] URL abierta con rundll32 (Windows): " + url);

            } else if (os.contains("mac")) {
                // macOS: open command
                runtime.exec(new String[]{"open", url});
                System.out.println("[BrowserLauncher] URL abierta con open (macOS): " + url);

            } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
                // Linux/Unix: xdg-open (freedesktop.org)
                runtime.exec(new String[]{"xdg-open", url});
                System.out.println("[BrowserLauncher] URL abierta con xdg-open (Linux): " + url);

            } else {
                throw new IOException("Sistema operativo no soportado: " + os);
            }

        } catch (IOException e) {
            System.err.println("[BrowserLauncher] Error abriendo navegador: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Verificar si el sistema soporta abrir URLs
     *
     * @return true si el sistema puede abrir navegador
     */
    public static boolean isSupported() {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            return true;
        }

        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("win") || os.contains("mac") || os.contains("nix") || os.contains("nux") || os.contains("aix");
    }
}
