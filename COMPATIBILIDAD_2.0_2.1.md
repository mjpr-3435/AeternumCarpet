# Compatibilidad Cliente `2.0.0` vs Servidor `2.1.0` · AeternumCarpet

Análisis comparativo entre **`aeternum-carpet-2.0.0.jar`** y **`2.1.0`** para decidir cuándo es obligatorio actualizar en el cliente.

---

## 1. ¿Pueden entrar y jugar normal con `2.0.0`?
**Sí.** Al conectarse (`JOIN`), los paquetes que envía el servidor (`DockStatePayload`, `PerimeterHudPayload`) mantienen exactamente el mismo formato que en `2.0.0`, así que **no los desconecta al entrar**.

---

## 2. Qué bloquea el Handshake `v2` (sin desconectar ni crashear)

Gracias a `ProtocolHandshake` (`ClientHelloPayload`) y `project_upload_v2`, los clientes `2.0.0` **ya no sufren desconexiones por `DecoderException`**. En su lugar, cuando intentan usar funciones incompatibles, el servidor cierra la pantalla y les muestra un aviso en chat indicando actualizar desde **Discord: `Resources/AeternumCarpet`**:

1. **Proyectos (`/projects` → Descargar / Cargar en Litematica y Subir `.litematic`):**
   - Si un jugador con `2.0.0` pulsa **Descargar** o **Cargar en Litematica**, el servidor cierra su pantalla y le avisa en chat que debe actualizar a `2.1.0`.
   - Si un OP con `2.0.0` intenta subir un `.litematic` (`project_upload` legacy de 9 campos), el servidor también cierra su pantalla y le avisa en chat (aunque el selector local de Litematica sí se abre antes de enviar).
   - *(Lo demás de `/projects` —abrir la lista de materiales, reclamar ítems, marcar subregiones— **sí funciona** en `2.0.0` porque `MaterialsDataPayload` y `ProjectSubregionActionPayload` no cambiaron).*
2. **Abrir la UI de `/stats` (solo afecta a OPs):**
   - Si un OP con `2.0.0` ejecuta `/stats` sin argumentos o intenta abrirla desde el Dock, el servidor no envía `StatsDataPayload` y le muestra el aviso en chat para actualizar a `2.1.0`.

---

## 3. Desync de inventario si se activa `aeStackableShulkers`
- Si `aeStackableShulkers = true` en el servidor, este apila shulkers vacías hasta `64` (y mantiene señal de comparador no-apilable cuando ningún slot tiene `count > 1`).
- Un cliente `2.0.0` (o vanilla) sigue creyendo que el máximo es `1`, por lo que **no crashea**, pero sufrirá **desync visual/de inventario** al mover, dividir o juntar shulkers vacías hasta que actualice a `2.1.0`.

---

## 4. Lo que simplemente NO tendrán en `2.0.0` (sin crash)
- **Botón `Kick`** en la pantalla de inventario de bots (`BotInventoryScreen` sigue abriendo normal gracias a que `BotOpenData` no cambió).
- **UI de `/stats`** (Registro, Scoreboards visuales, `Migrar`, `TP última pos.`).
- **Sincronización de transformaciones de subregiones** en Litematica.

---

## 5. Lo nuevo de `2.1.0` + lo de `2.0.0` que SÍ funciona sin actualizar
- **Todo lo de `2.0.0`:** Quick Dock, `/perimeter`, `/find` y `/count` (incluyendo el nuevo autocompletado en español/inglés), `/cs` (sin spam en chat), `/hat`, `/carefulbreak`, `/quickshulker`, `/quickenderchest`, `/nether`/`/overworld`/`/end`, `/viewinv`, `/guide`, `/actions`, `/protect`.
- **`/waypoints`** (`list`, `add`, `del`, `tp`): es 100% servidor + chat clickeable, funciona en `2.0.0`.
- **`/stats scoreboard <categoría> <stat>` y `hide`**: también es 100% servidor (usa el sidebar vanilla), funciona en `2.0.0`.

---

## Conclusión rápida
- Si **no** está activa `aeStackableShulkers` ni necesitan descargar/cargar/subir `.litematic` en `/projects`, pueden seguir en `2.0.0` sin riesgo de crash ni desconexión.
- En cuanto quieran **cargar/descargar/subir proyectos en `/projects`**, usar la **UI de `/stats`** (OPs) o se active **`aeStackableShulkers = true`**, **sí necesitan actualizar a `2.1.0`**.
