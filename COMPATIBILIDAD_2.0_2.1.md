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

## 3. Shulkers apilables (`stackableShulkerBoxes` + `aeStackableShulkersFixes`)
- El apilado de shulkers vacías depende de la regla nativa `stackableShulkerBoxes` de Carpet Core (que Carpet sincroniza a clientes `2.0.0`, `2.1.0` y `fabric-carpet`), mientras que `aeStackableShulkersFixes` actúa en el servidor como regla independiente de fixes (hoppers `1` en `1` y comparadores sin romper unloaders).
- Por ello, cualquier jugador con **`2.0.0` o `fabric-carpet`** puede apilar y mover shulkers sin desync. *(Solo un cliente **vanilla puro** sin Carpet sufriría desync visual si `stackableShulkerBoxes` está activo).*

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
- Si no necesitan descargar/cargar/subir `.litematic` en `/projects` ni la UI de `/stats` (OPs), pueden seguir en `2.0.0` sin riesgo de crash, desconexión ni desync de shulkers.
- En cuanto quieran **cargar/descargar/subir proyectos en `/projects`** o usar la **UI de `/stats`** (OPs), **sí necesitan actualizar a `2.1.0`**.
