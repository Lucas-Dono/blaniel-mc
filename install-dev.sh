#!/bin/bash

# Script para compilar e instalar el mod en desarrollo
# Usa el JAR de desarrollo que funciona correctamente

set -e

echo "Compilando mod..."
./gradlew clean jar

echo "Copiando JAR al directorio de mods..."
MOD_DIR="$HOME/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/instances/1.20.1/minecraft/mods"
cp build/devlibs/blaniel-mc-0.1.0-alpha-dev.jar "$MOD_DIR/"

echo "✓ Mod instalado exitosamente!"
echo "  Ubicación: $MOD_DIR/blaniel-mc-0.1.0-alpha-dev.jar"
echo ""
echo "IMPORTANTE: Reinicia Minecraft para que el mod se cargue."
