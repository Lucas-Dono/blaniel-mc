# Guía de Configuración - Blaniel Minecraft Mod

## 🎮 Sistema de Chat Avanzado con IA Conversacional

El mod ahora incluye un sistema de chat grupal avanzado con IA conversacional que detecta contexto espacial y gestiona interacciones inteligentes.

---

## 📋 Requisitos Previos

1. **Cuenta en Blaniel.com**
   - Regístrate en https://blaniel.com/registro
   - Crea al menos un personaje IA

2. **Minecraft 1.20.1 con Fabric**
   - Fabric Loader instalado
   - Fabric API mod instalado

---

## ⚙️ Primer Uso - Login Automático

### 1. Instalar el mod

1. Descarga el archivo `.jar` del mod
2. Colócalo en la carpeta `mods` de tu instalación de Minecraft
3. Inicia Minecraft con el perfil de Fabric

### 2. Login automático en el juego

Cuando entres a un mundo por primera vez, **aparecerá automáticamente** una pantalla de inicio de sesión.

#### **Opción A: Inicio de Sesión con Google (Recomendado) 🔐**

**¿Por qué usar Google?**
- ✅ **Más rápido**: Un solo clic, sin escribir nada
- ✅ **Más seguro**: Google maneja tu autenticación
- ✅ **Renovación automática**: Solo autenticas una vez, el mod renueva automáticamente la sesión
- ✅ **Sin contraseñas**: No necesitas recordar tu contraseña de Blaniel

**Pasos:**
1. Haz clic en el botón **"🔐 Iniciar Sesión con Google"**
2. Se abrirá tu navegador con la página de Google
3. Selecciona tu cuenta de Google (o inicia sesión si no lo estás)
4. Haz clic en **"Permitir"** para autorizar la aplicación
5. Verás una página de confirmación: **"¡Login Exitoso!"**
6. Vuelve a Minecraft, la sesión ya está activa ✅

**Primera vez:**
```
[Blaniel] Inicio de sesión exitoso con Google. ¡Bienvenido [Tu Nombre]!
```

**Siguientes veces (automático):**
```
[Blaniel] Sesión renovada automáticamente. ¡Bienvenido de vuelta [Tu Nombre]!
```

Después del primer login con Google, **el mod renovará automáticamente tu sesión cada vez que inicies Minecraft**. No necesitas volver a hacer el flujo de OAuth, todo es transparente.

#### **Opción B: Email y Contraseña (Tradicional)**

Si prefieres no usar Google, puedes iniciar sesión con tu email y contraseña de Blaniel:

**Pantalla de Login:**
- **Email**: Tu email registrado en Blaniel.com
- **Contraseña**: Tu contraseña de Blaniel.com
- Presiona `Enter` o haz clic en **"Iniciar Sesión"**

**Nota:** Con este método, tu sesión expira después de 30 días y deberás volver a iniciar sesión.

### 3. Configuración guardada

Después del login, el mod crea automáticamente:
```
.minecraft/config/blaniel-mc.json
```

Este archivo contiene:
- **Token JWT** de sesión (expira en 30 días)
- **Refresh Token** de Google (solo si usaste OAuth, permite renovación automática)
- **URL del servidor** (por defecto: https://blaniel.com)
- **Datos básicos del usuario** (nombre, email, plan)

**No necesitas editar este archivo manualmente.**

**Importante sobre seguridad:**
- Este archivo contiene tokens de autenticación
- Solo es accesible por tu usuario del sistema operativo
- Se limpia completamente al cerrar sesión (`/blaniel logout`)
- No compartas este archivo con nadie

---

## 🎯 Uso del Sistema de Chat

### Acceso a Personajes

El mod te da acceso a:
- ✅ **Todos tus personajes privados** (creados por ti)
- ✅ **Todos los personajes públicos** (creados por otros usuarios)
- ✅ **Personajes destacados** (featured)

Esto significa que puedes invocar **cualquier personaje de Blaniel** en tu mundo de Minecraft, no solo los tuyos.

### Invocar personajes en el mundo

Usa el comando en el chat de Minecraft:

```
/blaniel list
```
Muestra todos los personajes disponibles (públicos + privados)

```
/blaniel spawn <nombre_o_id>
```
Invoca un personaje en tu ubicación

**Ejemplo:**
```
/blaniel list
> Mostrando 45 agentes disponibles:
> - Tus agentes (3): Alice, Bob, Charlie
> - Agentes públicos (42): Einstein, Marilyn Monroe, Sherlock Holmes...

/blaniel spawn Einstein
> ✓ Einstein invocado en tu posición
```

### Controles de Teclado

**Teclas principales:**
- **`K`** → Abre la interfaz de Blaniel (login o selección de agentes)
- **`C`** → Abre el chat para hablar con los agentes

### Abrir el chat avanzado

**Presiona la tecla `C`** para abrir el chat de Blaniel.

Se abrirá una interfaz donde puedes escribir tu mensaje.

### Enviar mensajes

1. Presiona `C`
2. Escribe tu mensaje
3. Presiona `Enter` para enviar
4. Presiona `ESC` para cancelar

---

## 🤖 Características del Sistema

### Detección de Contexto

El sistema detecta automáticamente si es una conversación **individual** o **grupal**:

#### Conversación Individual (1 agente responde)
- Estás mirando a un NPC (cruceta sobre el NPC) y estás a menos de 7 metros
- Mencionas explícitamente el nombre de un agente ("Sarah, ¿qué opinas?")
- Continuidad conversacional (< 1 minuto desde última interacción)
- Agente más cercano (fallback)

#### Conversación Grupal (2-3 agentes responden)
- Usas palabras clave grupales: "todos", "chicos", "equipo", "grupo", "amigos", "ustedes"
- Mencionas múltiples nombres ("Alice y Sarah, vengan acá")

### Sistema de Movimiento Inteligente

Los NPCs pueden:
- **Acercarse** si estás a más de 4 metros (se posiciona a 3m)
- **Caminar** hacia otro agente si lo llamas (< 20 metros)
- **Teletransportarse** para distancias largas (> 20 metros)

**Ejemplo de interacción con movimiento:**
```
Usuario: "Alice, necesito hablar contigo"
[Alice está a 6 metros]
Alice: "Claro, espera que me acerco"
[Alice camina hasta 3 metros]
Alice: "Ya estoy aquí, dime"
```

### Redirección de Preguntas

Si haces una pregunta ambigua, la IA puede redirigirla:

**Ejemplo:**
```
Usuario: "¿Y tu amiga qué piensa?"
Alice: "¿Quién, Sarah? Preguntémosle. Sarah! ¿Qué piensas de esto?"
Sarah: "Hmm, creo que es una buena idea..."
```

### Animaciones Emocionales

Los NPCs responden con animaciones según el contexto:
- 👋 **waving** - Saludar con la mano
- 🤔 **thinking** - Mirar hacia arriba (pensativo)
- 😊 **happy** - Saltar de alegría
- 😲 **surprised** - Paso atrás
- 👉 **pointing** - Señalar
- 🙋 **beckoning** - "Ven acá" (mano + salto)

---

## 🔧 Solución de Problemas

### "No hay agentes IA cercanos para responder"
- **Causa:** No hay NPCs de Blaniel en un radio de 16 bloques
- **Solución:** Invoca un agente con `/blaniel spawn <nombre>` o acércate a uno existente

### "Debes iniciar sesión primero"
- **Causa:** No has iniciado sesión o la sesión expiró
- **Solución:**
  1. Presiona `K` nuevamente (se abrirá login automático)
  2. Usa "Iniciar Sesión con Google" o ingresa tu email y contraseña
  3. Si el problema persiste, elimina `.minecraft/config/blaniel-mc.json` y reinicia

### Problemas con Google OAuth

#### El navegador no se abre al hacer clic en "Iniciar Sesión con Google"
- **Causa:** El sistema no puede abrir el navegador automáticamente
- **Solución:**
  1. Verifica que tengas un navegador instalado (Chrome, Firefox, Edge, Safari)
  2. En Linux: asegúrate de tener `xdg-open` instalado
  3. Si el problema persiste, usa login con email y contraseña

#### "Error OAuth: Timeout esperando autorización del usuario"
- **Causa:** No autorizaste en Google dentro de 5 minutos
- **Solución:**
  1. Vuelve a intentar el login
  2. Autoriza más rápido en la página de Google (antes de 5 minutos)
  3. Verifica que el puerto 8888 no esté bloqueado por un firewall

#### "Error OAuth: Error al intercambiar código"
- **Causa:** Problema de comunicación con Google o configuración incorrecta
- **Solución:**
  1. Verifica tu conexión a internet
  2. Si estás en desarrollo, verifica que el Client ID esté configurado correctamente
  3. Intenta de nuevo más tarde

#### La renovación automática falla cada vez que inicio Minecraft
- **Causa:** El refresh token expiró o fue revocado
- **Solución:**
  1. Ve a https://myaccount.google.com/permissions
  2. Verifica si "Blaniel" aparece en aplicaciones autorizadas
  3. Si no aparece o fue revocado, vuelve a hacer login con Google
  4. El mod te pedirá autorización nuevamente

#### Quiero cambiar de cuenta de Google
- **Solución:**
  1. Ejecuta el comando `/blaniel logout` en Minecraft
  2. Sal del mundo y vuelve a entrar
  3. Haz clic en "Iniciar Sesión con Google"
  4. En la página de Google, selecciona una cuenta diferente o agrega una nueva

### "Límite de tasa excedido. Espera un momento"
- **Causa:** Has enviado demasiados mensajes en poco tiempo
- **Solución:** Espera unos segundos antes de enviar otro mensaje
- **Nota:** Los límites dependen de tu plan en Blaniel:
  - Free: 10 msg/min, 100 msg/hora
  - Plus: 30 msg/min, 600 msg/hora
  - Ultra: 100 msg/min, 6000 msg/hora

### "No se encontraron agentes"
- **Causa:** No hay personajes creados en el servidor
- **Solución:**
  1. Ve a https://blaniel.com/create-character y crea un personaje
  2. También puedes usar personajes públicos de otros usuarios

### Las teclas K o C no funcionan
- **Causa:** Conflicto con otro mod o keybinding
- **Solución:**
  1. Ve a Opciones de Minecraft → Controles
  2. Busca la categoría "Blaniel"
  3. Reasigna las teclas:
     - `key.blaniel.openui` (por defecto K) → Abrir UI de Blaniel
     - `key.blaniel.openchat` (por defecto C) → Abrir chat

### Error de conexión al servidor
- **Causa:** El servidor de Blaniel no está disponible o hay problemas de red
- **Solución:**
  1. Verifica tu conexión a internet
  2. Si usas localhost en desarrollo, asegúrate de que el servidor esté corriendo
  3. Verifica la URL en `.minecraft/config/blaniel-mc.json`

---

## 📊 Información de Debug

Para ver información adicional en los logs, busca en `.minecraft/logs/latest.log`:

```
[Blaniel] Usuario logueado: Tu Nombre (tu@email.com)
[Blaniel] Tipo de conversación: individual
[Blaniel] Agentes respondiendo: 1
```

---

## 🎮 Comandos Disponibles

```bash
# Listar agentes disponibles
/blaniel list

# Invocar agente por nombre o ID
/blaniel spawn <nombre_o_id>

# Eliminar agente (mirando al NPC)
/blaniel remove

# Cerrar sesión
/blaniel logout

# Información del mod
/blaniel info
```

---

## 💡 Consejos de Uso

1. **Conversaciones naturales:** Habla como lo harías normalmente, el sistema entiende contexto
2. **Explora personajes públicos:** Usa `/blaniel list` para ver todos los personajes disponibles
3. **Nombra NPCs claramente:** Usa nombres fáciles de recordar y mencionar
4. **Espaciado:** Mantén los NPCs a menos de 16 bloques para que respondan
5. **Emociones:** Los NPCs responderán con animaciones apropiadas al contexto emocional
6. **Grupos:** Invoca varios agentes para crear conversaciones grupales dinámicas
7. **Privacidad:** Solo tú puedes ver las conversaciones con tus personajes privados
8. **Usa Google OAuth:** Para no tener que iniciar sesión cada vez que juegues

---

## 🔄 Renovación Automática de Sesión (Google OAuth)

### ¿Cómo funciona?

Cuando usas **"Iniciar Sesión con Google"**, el mod guarda un **refresh token** que le permite renovar automáticamente tu sesión sin intervención manual.

**Flujo técnico:**

1. **Primera autenticación** (solo una vez):
   ```
   Usuario → Clic en "Iniciar Sesión con Google"
   Mod → Abre navegador con página de Google
   Usuario → Autoriza aplicación
   Google → Envía authorization code al mod
   Mod → Intercambia code por tokens (access + refresh + id)
   Mod → Envía id_token al backend de Blaniel
   Backend → Valida y retorna JWT de Blaniel
   Mod → Guarda JWT + refresh_token en blaniel-mc.json
   ```

2. **Siguientes veces** (automático, 2-3 segundos):
   ```
   Usuario → Inicia Minecraft
   Mod → Detecta refresh_token guardado
   Mod → Solicita nuevos tokens a Google (sin intervención del usuario)
   Google → Retorna nuevos tokens
   Mod → Envía nuevo id_token al backend de Blaniel
   Backend → Retorna nuevo JWT actualizado
   Mod → Actualiza blaniel-mc.json con nuevos tokens
   Usuario → Ve mensaje "Sesión renovada automáticamente"
   ```

### Ventajas

- ✅ **Sin fricción:** Solo autenticas una vez, luego todo es automático
- ✅ **Rápido:** Renovación en 2-3 segundos en segundo plano
- ✅ **Seguro:** Google OAuth es el estándar de la industria
- ✅ **Confiable:** El refresh token se mantiene válido mientras no lo revoques
- ✅ **Transparente:** No interrumpe tu experiencia de juego

### ¿Cuándo caduca?

- **JWT de Blaniel:** 30 días (se renueva automáticamente antes de expirar)
- **Refresh Token de Google:** Indefinido (mientras no revoques el acceso manualmente)

**En la práctica:** Puedes jugar Minecraft durante meses sin necesidad de volver a autenticarte.

### ¿Cuándo debo volver a autenticar?

Solo en estos casos:
1. Ejecutaste `/blaniel logout` manualmente
2. Eliminaste el archivo `.minecraft/config/blaniel-mc.json`
3. Revocaste el acceso desde https://myaccount.google.com/permissions
4. Cambiaste de cuenta de Google y quieres usar otra

---

## 🔐 Seguridad y Privacidad

### Autenticación con Google OAuth2

- **Estándar de industria:** Usa OAuth 2.0, el mismo protocolo que usan aplicaciones como Discord, Spotify, GitHub
- **PKCE (Proof Key for Code Exchange):** Protección adicional contra ataques de interceptación (RFC 7636)
- **Sin contraseñas:** Google maneja tu autenticación, el mod nunca ve tu contraseña
- **Revocable:** Puedes revocar el acceso desde https://myaccount.google.com/permissions en cualquier momento
- **Scope mínimo:** Solo solicita `openid email profile` (información básica de perfil)

### Tokens y Sesiones

- **JWT Token:** Token de sesión de Blaniel (expira en 30 días)
- **Refresh Token:** Token de Google para renovación automática (se mantiene mientras no lo revoques)
- **Almacenamiento local:** Guardados en `.minecraft/config/blaniel-mc.json` (solo accesible por tu usuario del sistema)
- **Transmisión segura:** Todas las comunicaciones usan HTTPS obligatorio
- **Auto-limpieza:** Se eliminan completamente al ejecutar `/blaniel logout`

### Privacidad

- **Sin tracking:** El mod no recopila información de uso más allá de la autenticación
- **Datos locales:** Tus tokens se almacenan solo en tu computadora
- **Privacidad de personajes:** Tus personajes privados solo son accesibles por ti
- **Conversaciones encriptadas:** Las conversaciones se transmiten de forma segura (HTTPS)

### Recomendaciones de Seguridad

1. **Usa Google OAuth cuando sea posible** - Es más seguro que almacenar contraseñas
2. **No compartas blaniel-mc.json** - Contiene tokens de autenticación válidos
3. **Cierra sesión en computadoras compartidas** - Usa `/blaniel logout` antes de salir
4. **Revisa permisos periódicamente** - Verifica en tu cuenta de Google qué aplicaciones tienen acceso
5. **Mantén Minecraft actualizado** - Las versiones nuevas incluyen parches de seguridad

---

## ❓ Preguntas Frecuentes (FAQ)

### ¿Es seguro usar "Iniciar Sesión con Google"?

**Sí, es muy seguro.** Google OAuth 2.0 es el estándar de la industria usado por miles de aplicaciones (Discord, Spotify, GitHub, Slack, etc.). El mod:
- **Nunca ve tu contraseña de Google**
- **Solo solicita información básica** (email, nombre, foto de perfil)
- Usa **PKCE** para protección adicional contra ataques
- Todas las comunicaciones son **HTTPS encriptado**

Puedes revisar qué aplicaciones tienen acceso a tu cuenta de Google en: https://myaccount.google.com/permissions

### ¿Qué pasa si revoco el acceso desde Google?

Si revocas el acceso desde tu cuenta de Google:
1. El refresh token deja de funcionar
2. La próxima vez que inicies Minecraft, el mod intentará renovar la sesión y fallará
3. Se mostrará automáticamente la pantalla de login
4. Simplemente vuelves a hacer clic en "Iniciar Sesión con Google" y autorizas nuevamente

**No pierdes datos**, solo necesitas volver a autorizar.

### ¿Puedo usar el mod sin Google OAuth?

**Sí**, puedes usar login tradicional con email y contraseña de Blaniel. Sin embargo:
- ❌ Tendrás que iniciar sesión cada 30 días (cuando expire el JWT)
- ❌ No hay renovación automática de sesión
- ✅ Funciona igual para todo lo demás (chat, comandos, agentes)

**Recomendamos Google OAuth** por comodidad y seguridad.

### ¿El mod tiene acceso a mi cuenta de Google?

**No.** El mod solo recibe:
- Tu dirección de email
- Tu nombre de perfil
- Tu foto de perfil (opcional)

El mod **NO puede**:
- ❌ Leer tus emails de Gmail
- ❌ Acceder a Google Drive
- ❌ Modificar tu calendario
- ❌ Ver tu ubicación
- ❌ Acceder a ningún otro servicio de Google

El scope de OAuth es mínimo: `openid email profile` (solo identidad básica).

### ¿Qué pasa si alguien accede a mi computadora?

Si alguien tiene acceso físico a tu computadora y puede abrir Minecraft con tu cuenta del sistema:
- **Sí**, podría jugar Minecraft con tu sesión de Blaniel activa
- **Sí**, podría ver tus conversaciones con los agentes
- **No**, no podría acceder a tu cuenta de Google (no se guarda contraseña)

**Protección:**
1. Usa contraseña en tu cuenta del sistema operativo
2. Si compartes la computadora, ejecuta `/blaniel logout` antes de salir
3. En computadoras públicas, **siempre** usa `/blaniel logout`

### ¿El refresh token expira?

**No, en teoría no.** Google mantiene los refresh tokens activos indefinidamente mientras:
- ✅ No revoques el acceso manualmente
- ✅ No cambies la contraseña de Google (puede invalidar tokens)
- ✅ No pases 6 meses sin usar el mod (Google puede revocar tokens inactivos)

**En la práctica:** Mientras juegues Minecraft al menos una vez cada pocos meses, el refresh token se mantendrá activo.

### ¿Puedo usar múltiples cuentas de Blaniel?

**Sí**, pero necesitas alternar entre ellas:

1. Cierra sesión de la cuenta actual:
   ```
   /blaniel logout
   ```

2. Sal del mundo y vuelve a entrar

3. Haz clic en "Iniciar Sesión con Google"

4. En la página de Google, selecciona la otra cuenta o agrega una nueva

Cada vez que cambies de cuenta, se guardará el nuevo refresh token y el mod renovará automáticamente esa cuenta en el futuro.

### ¿Funciona en servidores multijugador?

El mod está diseñado para **modo individual (singleplayer)**. En servidores multijugador:
- ✅ El login funciona normalmente
- ✅ Puedes usar todos los comandos (`/blaniel`)
- ⚠️ Los NPCs de Blaniel son visibles para todos los jugadores
- ⚠️ Las conversaciones son visibles para jugadores cercanos

**Recomendación:** Usa el mod principalmente en mundos individuales para mejor privacidad.

### ¿El mod consume muchos recursos?

**No.** El mod es muy ligero:
- La renovación automática ocurre **solo al iniciar Minecraft** (1 vez)
- Toma **2-3 segundos** en segundo plano
- Después de eso, no hay overhead adicional
- Las conversaciones con agentes usan la misma cantidad de recursos que el chat normal

**Uso de red:** Solo cuando:
- Inicias sesión (OAuth)
- Renuevas sesión (automático, 1 vez al inicio)
- Envías mensajes a agentes IA
- Invocas agentes con `/blaniel spawn`

---

## 🚀 Próximas Características

- [ ] Voz (Text-to-Speech) con ElevenLabs
- [ ] Análisis de imágenes (enviar screenshots)
- [ ] Memoria persistente entre sesiones
- [ ] Eventos emergentes grupales
- [ ] Animaciones más complejas (mod Emotecraft)
- [ ] Sistema de relaciones entre NPCs
- [ ] Misiones y objetivos generados por IA

---

## 📞 Soporte

Si encuentras problemas o tienes preguntas:

1. **Logs:** Revisa los logs de Minecraft en `.minecraft/logs/latest.log`
2. **GitHub Issues:** Reporta bugs en el repositorio del mod
3. **Discord:** Únete al servidor de Blaniel para soporte comunitario
4. **Web:** https://blaniel.com/soporte

---

## 📄 Licencia

Este mod es parte del proyecto Blaniel y está licenciado bajo MIT License.

**Versión:** 0.1.0-alpha
**Fecha:** 2026-01-28
**Autor:** Sistema Blaniel
**Web:** https://blaniel.com
