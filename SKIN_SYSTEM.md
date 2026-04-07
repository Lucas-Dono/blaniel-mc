# Sistema de Skins de Blaniel

El mod de Blaniel MC incluye un sistema completo de skins personalizadas que descarga y aplica las apariencias de los agentes de IA directamente desde la API de Blaniel.

## Arquitectura

```
┌─────────────────────────────────────────────────────┐
│          BlanielVillagerEntity (Entidad)            │
│  - blanielAgentId: String                           │
│  - customGameProfile: GameProfile                   │
│  - loadCustomSkin(): Descarga skin al asignar agent │
└────────────────┬────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────┐
│         BlanielSkinManager (Gestor)                 │
│  - loadSkin(): Descarga y caché de skins           │
│  - Caché RAM: Map<agentId, GameProfile>            │
│  - Caché Disco: .minecraft/blaniel-skins/*.png     │
│  - registerTexture(): Registra en TextureManager   │
└────────────────┬────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────┐
│           API de Blaniel (Backend)                  │
│  GET /api/v1/minecraft/agents/:id/skin              │
│  - Genera PNG 64x64 desde traits                   │
│  - Cache: 1 año (immutable)                         │
└─────────────────────────────────────────────────────┘
```

## Flujo de Carga de Skin

### 1. Asignación de Agente
```java
BlanielVillagerEntity villager = new BlanielVillagerEntity(...);
villager.setBlanielAgentId("abc123"); // Trigger automático
```

### 2. Verificación de Caché
```
1. Caché RAM → GameProfile ya en memoria?
2. Caché Disco → Archivo .minecraft/blaniel-skins/abc123.png existe?
3. Descarga desde API → GET /api/v1/minecraft/agents/abc123/skin
```

### 3. Aplicación de Skin
```java
// Registro en TextureManager
Identifier skinId = new Identifier("blaniel", "skins/agent_abc123");
NativeImage image = NativeImage.read(skinData);
client.getTextureManager().registerTexture(skinId, new NativeImageBackedTexture(image));

// Aplicación al GameProfile
GameProfile profile = new GameProfile(uuid, agentName);
profile.getProperties().put("textures", ...);
```

### 4. Renderizado
```java
// BlanielVillagerRenderer obtiene la textura
@Override
public Identifier getTexture(BlanielVillagerEntity entity) {
    return BlanielSkinManager.getTextureIdentifier(entity.getBlanielAgentId());
}
```

## Comandos

### Limpiar caché de skin específica
```
/blaniel clearcache <agentId>
```

### Limpiar todo el caché de skins
```
/blaniel clearcache
```

Útil para testing cuando se actualiza la skin en el servidor.

## Formato de Skin

Las skins deben seguir el formato estándar de Minecraft:
- Tamaño: 64x64 píxeles
- Formato: PNG con transparencia
- Layout: Steve/Alex compatible (MCA-Reborn usa modelo de jugador)

El backend genera estas skins automáticamente desde los `MinecraftSkinTraits` almacenados en la base de datos.

## Caché

### Caché en RAM
- **Ubicación**: `BlanielSkinManager.PROFILE_CACHE`
- **Tipo**: `Map<String, GameProfile>`
- **Duración**: Hasta reinicio del juego
- **Ventaja**: Acceso instantáneo

### Caché en Disco
- **Ubicación**: `.minecraft/blaniel-skins/`
- **Formato**: `{agentId}.png`
- **Duración**: Persistente (hasta limpiar manualmente)
- **Ventaja**: Evita descargas en sesiones futuras

### Headers de Caché HTTP
El endpoint de la API retorna:
```
Cache-Control: public, max-age=31536000, immutable
ETag: "{agentId}-{generatedAt}"
```

Esto permite al navegador/cliente HTTP cachear agresivamente.

## Performance

### Tiempo de Carga
```
Primera vez (sin caché):     ~200-500ms (descarga + registro)
Caché disco:                 ~20-50ms (lectura + registro)
Caché RAM:                   <1ms (lookup directo)
```

### Consumo de Memoria
```
Por skin en RAM:             ~5KB (GameProfile + Properties)
Por skin en disco:           ~300 bytes (PNG 64x64 optimizado)
```

### Concurrencia
El sistema usa `CompletableFuture` para:
- Descargas asíncronas (no bloquea el juego)
- Registro de texturas en thread principal (requerido por Minecraft)

## Troubleshooting

### La skin no carga
1. Verificar que el agente tenga `metadata.minecraft.skinTraits` en BD
2. Probar endpoint manualmente: `curl http://localhost:3000/api/v1/minecraft/agents/{id}/skin`
3. Revisar logs del servidor: `[Minecraft Skin]`
4. Revisar logs del cliente: `[Blaniel Skin Manager]`

### Skin desactualizada
```
/blaniel clearcache <agentId>
```
Luego respawnear el aldeano o recargar el mundo.

### Error de permisos en directorio de caché
El mod debe tener permisos de escritura en `.minecraft/blaniel-skins/`

### Textura no se registra
Asegurarse de que el código de registro se ejecuta en el thread principal:
```java
MinecraftClient.getInstance().execute(() -> {
    client.getTextureManager().registerTexture(...);
});
```

## Extensiones Futuras

### Editor de Skins In-Game
GUI para editar `MinecraftSkinTraits` directamente desde Minecraft:
```
/blaniel editskin
- Color pickers para skin/hair/eyes
- Dropdown para hairStyle, clothingStyle
- Preview 3D en tiempo real
- PATCH /api/v1/minecraft/agents/:id/skin/edit
```

### Generación Procedural
Generar variaciones de skins para NPCs genéricos sin agente asignado.

### Animaciones de Emociones
Cambiar expresión facial según emoción actual del agente:
```
emotions.primary = "happy" → Textura con sonrisa
emotions.primary = "sad"   → Textura con ceño fruncido
```

### Slim vs Classic Model
Auto-detectar si usar modelo slim (Alex) o classic (Steve) basado en `gender` del agente.

## Referencias

- **Backend Skin System**: `/lib/minecraft/skin-trait-analyzer.ts`, `/lib/minecraft/skin-renderer.ts`
- **API Endpoint**: `/app/api/v1/minecraft/agents/[id]/skin/route.ts`
- **Mod Client**: `BlanielSkinManager.java`, `BlanielVillagerRenderer.java`
- **Database Schema**: `Agent.metadata.minecraft.skinTraits`
