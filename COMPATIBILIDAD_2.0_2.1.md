# Compatibilidad Cliente `2.0.0` vs Servidor `2.1.0` · AeternumCarpet

Análisis comparativo entre **`aeternum-carpet-2.0.0.jar`** y **`2.1.0`** para decidir cuándo es obligatorio actualizar en el cliente.

---

## 1. ¿Pueden entrar y jugar normal con `2.0.0`?
**Sí.** Al conectarse (`JOIN`), los paquetes que envía el servidor (`DockStatePayload`, `PerimeterHudPayload`) mantienen exactamente el mismo formato que en `2.0.0`, así que **no los desconecta al entrar**.

---

## 2. Lo que SÍ rompe o desconecta si NO actualizan

1. **Proyectos (`/projects` → Descargar / Cargar en Litematica y Subir `.litematic`):**
   - En `2.0.0.jar`, `ProjectDownloadDataPayload` tiene **10 campos** y `ProjectUploadPayload` **9 campos**.
   - En `2.1.0`, ambos añaden `String subregionsJson` al final (**11** y **10** campos).
   - **Qué pasa:** si alguien con `2.0.0` intenta **descargar o cargar un proyecto en Litematica** (o subir uno siendo OP), el cliente y el servidor leen distinta cantidad de bytes en el buffer y **lo desconecta del servidor** por error de red (`DecoderException`).
   - *(Lo demás de `/projects` —abrir la lista de materiales, reclamar ítems, marcar subregiones— **sí funciona** porque `MaterialsDataPayload` y `ProjectSubregionActionPayload` no cambiaron).*
2. **Shulkers apilables (`aeStackableShulkers`, si está activa):**
   - El servidor permite stacks de `64` shulkers vacías, pero el cliente `2.0.0` cree que el máximo sigue siendo `1`.
   - **Qué pasa:** no crashea el juego, pero el jugador en `2.0.0` sufrirá **desync visual/de inventario** al intentar mover, dividir o juntar shulkers vacías.
3. **Abrir la UI de `/stats` (solo afecta a OPs):**
   - `StatsDataPayload` y `StatsScreen` no existen en `2.0.0.jar`. Si un OP con `2.0.0` ejecuta `/stats` sin argumentos para abrir la interfaz, el servidor le enviará un paquete que su cliente no tiene registrado.

---

## 3. Lo que simplemente NO tendrán (sin crash)
- **Botón `Kick`** en la pantalla de inventario de bots (`BotInventoryScreen` sigue abriendo normal gracias a que `BotOpenData` no cambió).
- **UI de `/stats`** (Registro, Scoreboards visuales, `Migrar`, `TP última pos.`).
- **Sincronización de transformaciones de subregiones** en Litematica.

---

## 4. Lo nuevo de `2.1.0` + lo de `2.0.0` que SÍ funciona sin actualizar
- **Todo lo de `2.0.0`:** Quick Dock, `/perimeter`, `/find` y `/count` (incluyendo el nuevo autocompletado en español/inglés), `/cs` (sin spam en chat), `/hat`, `/carefulbreak`, `/quickshulker`, `/quickenderchest`, `/nether`/`/overworld`/`/end`, `/viewinv`, `/guide`, `/actions`, `/protect`.
- **`/waypoints`** (`list`, `add`, `del`, `tp`): es 100% servidor + chat clickeable, funciona en `2.0.0`.
- **`/stats scoreboard <categoría> <stat>` y `hide`**: también es 100% servidor (usa el sidebar vanilla), funciona en `2.0.0`.

---

## Conclusión rápida
- Si **no** está activa `aeStackableShulkers` ni descargan proyectos de `/projects` para cargarlos en Litematica, pueden seguir en `2.0.0` sin problema.
- En cuanto alguien quiera **cargar/descargar un proyecto de `/projects`** o se use **`aeStackableShulkers = true`**, **sí necesitan actualizar a `2.1.0`**.
