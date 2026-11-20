# Aeternum Carpet

> [!WARNING]
> **Aviso importante / Úsalo bajo tu propio riesgo**
>
> Este mod fue desarrollado íntegramente por **Gemini 4 Hydrogen** a partir de indicaciones, ideas y pruebas del autor (sin programación manual directa).
>
> Se publica tal cual está: **úsalo bajo tu propio riesgo**. Aunque se utiliza en **Aeternum SMP**, puede contener bugs, errores de sincronización o comportamientos inesperados.

Extensión de **Fabric Carpet** para **Minecraft 1.21.6** orientada a servidores técnicos y supervivencia avanzada.

## Qué incluye (resumen)

- **Dock (`quickDock`) y navegación**: barra de acceso rápido, notas, tareas MOTD y comandos de dimensión (`/nether`, `/overworld`, `/end`).
- **Perímetros y World Eater (`commandPerimeters`)**: gestión de perímetros (`/perimeter`), escaneo incremental, alertas y grilla automática de bots espectadores.
- **Proyectos y Materiales (`commandProjects`)**: gestión de proyectos (`/projects`), selector de `.litematic` y lista de materiales con progreso/claim.
- **Finder (`commandFinder`)**: búsqueda y conteo de ítems en contenedores (`/find`, `/count`, `near_me`, `/finder clean`).
- **Bots y utilidades**: edición de inventario de fake players (`editBotInventory`), persistencia al reiniciar (`reloadFakePlayers`), `/cs` (`cameraMode`), `/carefulbreak`, `/hat`, `/viewinv`, `playerSit`, entre otros.
- **Interfaz `/rules`**: gestor visual unificado para reglas de Carpet y extensiones, con soporte de `carpet.conf`.
- **Mecánicas y estabilidad**: `updateSuppressionCrashFix`, `endermanGriefing`, `phantomSpawning`, `craftableCalcite`, `instamineDeepslate`, `fastObsidian`, `openShulkerInHand`, `openEnderChestInHand`, etc.

## Requisitos

- **Minecraft**: `1.21.6`
- **Java**: `21` o superior
- **Fabric Loader**: `>=0.19.2`
- **Dependencias**: `fabric-api` y `carpet` (`1.4.176+`)

## Compilación

```bash
./gradlew build
```

El `.jar` compilado se genera en `build/libs/`.

## Licencia

Disponible bajo la licencia **CC0-1.0** (ver [LICENSE](LICENSE)).
