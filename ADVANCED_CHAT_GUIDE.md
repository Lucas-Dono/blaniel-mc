# Guía Avanzada de Chat Grupal para Minecraft

## 🎯 Sistema Director Conversacional

El sistema analiza el contexto completo y decide automáticamente:
- ¿Es conversación individual o grupal?
- ¿Qué agentes deben responder?
- ¿Se necesita movimiento?
- ¿Hay ambigüedades que resolver?

**Todo en una sola llamada LLM** para minimizar costos.

## 📊 Reglas de Detección de Contexto

### Conversación Individual (1 agente responde)

1. **Mirando a NPC + < 7 metros**
   ```
   Jugador abre chat con cruceta sobre NPC "Alice" a 5 metros
   → Alice responde (individual)
   ```

2. **Mención explícita de nombre**
   ```
   "Sarah, ¿qué opinas?"
   → Sarah responde (individual)
   ```

3. **Continuidad conversacional (< 1 minuto)**
   ```
   Hace 30 segundos habló con Bob
   Ahora dice: "y eso qué significa?"
   → Bob responde (continuidad)
   ```

4. **Agente más cercano (fallback)**
   ```
   No hay otro contexto claro
   → Responde el NPC más cerca no (individual)
   ```

### Conversación Grupal (2-3 agentes responden)

1. **Palabras clave grupales**
   ```
   "Chicos, ¿qué hacemos?"
   → Responden varios agentes (grupal)

   Keywords: todos, chicos, equipo, grupo, amigos, ustedes
   ```

2. **Menciones múltiples**
   ```
   "Alice y Sarah, vengan acá"
   → Alice y Sarah responden (grupal)
   ```

### Conversación con Redirección

1. **Referencias ambiguas**
   ```
   "¿Y tu amiga qué piensa?"
   → IA principal: "¿Quién, Sarah? Preguntémosle. Sarah! ¿Qué piensas?"
   → Sarah responde

   Referencias: amiga, amigo, ella, él, esa persona, ese
   ```

## 🚶 Sistema de Movimiento Inteligente

### Acercarse (> 4 metros)

```json
{
  "parts": [
    {
      "type": "speech",
      "content": "Espera, déjame acercarme",
      "animationHint": "beckoning"
    },
    {
      "type": "command",
      "command": {
        "type": "move_closer",
        "pauseMessage": true
      }
    },
    {
      "type": "continuation",
      "content": "Ahora sí, cuéntame qué pasó",
      "continuesAfterCommand": true
    }
  ]
}
```

**Comportamiento en Minecraft:**
1. NPC dice "Espera, déjame acercarme"
2. NPC camina hasta quedar a 3 metros del jugador
3. Al llegar, dice "Ahora sí, cuéntame qué pasó"

### Caminar hacia otro agente (< 20 metros)

```json
{
  "parts": [
    {
      "type": "speech",
      "content": "Busquémosla y le preguntamos, sígueme",
      "animationHint": "beckoning"
    },
    {
      "type": "command",
      "command": {
        "type": "walk_to_agent",
        "targetAgentId": "agent_sarah_id",
        "pauseMessage": true
      }
    },
    {
      "type": "continuation",
      "content": "Sarah! Mira, queríamos preguntarte algo",
      "continuesAfterCommand": true
    }
  ]
}
```

**Comportamiento en Minecraft:**
1. NPC dice "Busquémosla y le preguntamos, sígueme"
2. NPC calcula ruta hacia Sarah
3. Sarah se queda quieta (para no recalcular)
4. NPC camina hacia Sarah
5. Al llegar, dice "Sarah! Mira, queríamos preguntarte algo"
6. Sarah responde

### Teletransporte (> 20 metros)

Similar a walk_to_agent pero más rápido (para distancias largas).

## 🎭 Ejemplos de Interacciones Inmersivas

### Ejemplo 1: Conversación Individual con Continuidad

```
Jugador (mirando a Alice a 4m): "Hola Alice, ¿cómo estás?"
Alice: "¡Hola! Bien, gracias. ¿Y tú?"

[30 segundos después, sin mirar a nadie]
Jugador: "También bien, oye..."
Alice (continuidad): "Dime, te escucho"
```

### Ejemplo 2: Redirección por Referencia Ambigua

```
Jugador: "¿Y tu amiga qué piensa de esto?"
Alice: "¿Quién, Sarah? Preguntémosle. Sarah! ¿Qué piensas de esto?"
Sarah: "Hmm, creo que es una buena idea..."
```

### Ejemplo 3: Movimiento para Acercarse

```
[Alice está a 6 metros]
Jugador: "Alice, necesito hablar contigo"
Alice: "Claro, espera que me acerco"
[Alice camina hasta 3 metros]
Alice: "Ya estoy aquí, dime"
```

### Ejemplo 4: Ir a Buscar a Alguien

```
[Sarah está a 15 metros, no visible]
Jugador: "¿Dónde está Sarah?"
Alice: "No sé, busquémosla. Sígueme"
[Alice camina hacia Sarah, jugador la sigue]
[Al llegar]
Alice: "Sarah! Te estábamos buscando"
Sarah: "¿Qué pasa?"
```

### Ejemplo 5: Conversación Grupal

```
Jugador: "Chicos, ¿qué hacemos ahora?"
Alice: "Yo propongo ir a explorar"
Bob (después de 2s): "Sí, buena idea"
Charlie (después de 2s más): "Vamos!"
```

## 💻 Implementación en Java

### Estructura de Response con Comandos

```java
JsonObject response = /* respuesta del servidor */;
JsonArray parts = response.getAsJsonArray("parts");

Queue<ResponseAction> actionQueue = new LinkedList<>();

for (JsonElement partElem : parts) {
    JsonObject part = partElem.getAsJsonObject();
    String type = part.get("type").getAsString();

    switch (type) {
        case "speech":
            actionQueue.add(new SpeechAction(
                part.get("content").getAsString(),
                part.has("animationHint")
                    ? part.get("animationHint").getAsString()
                    : "talking"
            ));
            break;

        case "command":
            JsonObject command = part.getAsJsonObject("command");
            actionQueue.add(parseCommand(command));
            break;

        case "continuation":
            actionQueue.add(new SpeechAction(
                part.get("content").getAsString(),
                "talking"
            ));
            break;
    }
}

// Ejecutar acciones secuencialmente
executeActionQueue(entity, actionQueue);
```

### Parser de Comandos

```java
private ResponseAction parseCommand(JsonObject command) {
    String type = command.get("type").getAsString();

    switch (type) {
        case "move_closer":
            return new MoveCloserAction(
                player.getPos(),
                3.0 // distancia objetivo
            );

        case "walk_to_agent":
            String targetId = command.get("targetAgentId").getAsString();
            BlanielVillagerEntity target = findEntityByAgentId(targetId);
            return new WalkToAgentAction(target);

        case "redirect_question":
            String question = command.get("question").getAsString();
            String redirectId = command.get("targetAgentId").getAsString();
            return new RedirectQuestionAction(redirectId, question);

        default:
            return null;
    }
}
```

### Sistema de Acciones

```java
interface ResponseAction {
    void execute(BlanielVillagerEntity entity);
    boolean isComplete();
    boolean shouldPause(); // ¿Pausar hasta completar?
}

class SpeechAction implements ResponseAction {
    private String content;
    private String animation;
    private boolean completed = false;

    public void execute(BlanielVillagerEntity entity) {
        entity.displayChatBubble(content);
        entity.playAnimation(animation);

        // Programar ocultar después de 5 segundos
        scheduler.schedule(() -> {
            completed = true;
        }, 5, TimeUnit.SECONDS);
    }

    public boolean shouldPause() { return false; }
}

class MoveCloserAction implements ResponseAction {
    private Vec3d targetPos;
    private double targetDistance;
    private boolean completed = false;

    public void execute(BlanielVillagerEntity entity) {
        // Calcular posición a 3 metros del jugador
        Vec3d direction = targetPos.subtract(entity.getPos()).normalize();
        Vec3d finalPos = targetPos.subtract(direction.multiply(targetDistance));

        // Iniciar pathfinding
        entity.getNavigation().startMovingTo(
            finalPos.x,
            finalPos.y,
            finalPos.z,
            1.0 // velocidad
        );
    }

    public boolean isComplete() {
        return entity.getNavigation().isIdle();
    }

    public boolean shouldPause() { return true; }
}

class WalkToAgentAction implements ResponseAction {
    private BlanielVillagerEntity target;
    private boolean completed = false;

    public void execute(BlanielVillagerEntity entity) {
        // Target se queda quieto
        target.getNavigation().stop();
        target.setAiDisabled(true);

        // Entity camina hacia target
        entity.getNavigation().startMovingTo(
            target.getX(),
            target.getY(),
            target.getZ(),
            1.0
        );
    }

    public boolean isComplete() {
        if (entity.getNavigation().isIdle()) {
            // Re-habilitar AI del target
            target.setAiDisabled(false);
            return true;
        }
        return false;
    }

    public boolean shouldPause() { return true; }
}
```

### Ejecutor de Queue

```java
private void executeActionQueue(
    BlanielVillagerEntity entity,
    Queue<ResponseAction> queue
) {
    if (queue.isEmpty()) return;

    ResponseAction current = queue.poll();
    current.execute(entity);

    if (current.shouldPause()) {
        // Esperar a que complete antes de continuar
        scheduler.scheduleAtFixedRate(() -> {
            if (current.isComplete()) {
                executeActionQueue(entity, queue);
                return;
            }
        }, 100, 100, TimeUnit.MILLISECONDS);
    } else {
        // Continuar inmediatamente
        scheduler.schedule(() -> {
            executeActionQueue(entity, queue);
        }, 2, TimeUnit.SECONDS); // Delay normal entre mensajes
    }
}
```

## 🎨 Animaciones por Tipo de Comando

```java
private void playAnimation(String hint) {
    switch (hint) {
        case "waving":
            this.setArmSwinging(true);
            scheduler.schedule(() -> this.setArmSwinging(false), 1, SECONDS);
            break;

        case "beckoning": // "sígueme"
            this.setArmSwinging(true);
            this.setJumping(true);
            scheduler.schedule(() -> {
                this.setArmSwinging(false);
                this.setJumping(false);
            }, 1, SECONDS);
            break;

        case "pointing": // señalando
            this.setArmSwinging(true);
            this.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, targetPos);
            scheduler.schedule(() -> this.setArmSwinging(false), 2, SECONDS);
            break;

        case "thinking":
            this.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES,
                player.getPos().add(0, 2, 0)); // Mirar arriba
            this.headYaw += 10;
            break;

        case "happy":
            this.setJumping(true);
            playSound(SoundEvents.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
            break;

        // ... más animaciones
    }
}
```

## 📈 Optimización de Costos

El sistema genera **TODO en una sola llamada LLM**:

```json
// UNA SOLA REQUEST al LLM:
{
  "parts": [
    {"type": "speech", "content": "mensaje 1"},
    {"type": "command", "command": {...}},
    {"type": "continuation", "content": "mensaje 2"}
  ]
}

// En lugar de DOS REQUESTS:
// 1. "mensaje 1"
// 2. [ejecuta comando]
// 3. "mensaje 2" ← esto requeriría otra llamada
```

**Ahorro:** ~50% en costos de LLM por interacción con comandos.

## 🐛 Debugging

### Ver análisis del Director

```java
JsonObject metadata = response.getAsJsonObject("metadata");
JsonObject analysis = metadata.getAsJsonObject("directorAnalysis");

System.out.println("Tipo de conversación: " +
    analysis.get("conversationType").getAsString());
System.out.println("Razón: " +
    analysis.get("reasoning").getAsString());
```

### Log de comandos ejecutados

```java
for (ResponseAction action : actionQueue) {
    System.out.println("Ejecutando: " + action.getClass().getSimpleName());
    if (action.shouldPause()) {
        System.out.println("  → Pausando hasta completar");
    }
}
```

## 🚀 Próximas Mejoras

- [ ] Sistema de emociones completo (OCC)
- [ ] Pathfinding más sofisticado (evitar obstáculos)
- [ ] Gestos más complejos (mod Emotecraft)
- [ ] TTS con ElevenLabs (voz real)
- [ ] Memoria a largo plazo mejorada
- [ ] Eventos emergentes grupales

---

**Versión:** 2.0.0 (Director Conversacional)
**Fecha:** 2026-01-28
**Autor:** Sistema Blaniel
