# Bots Persistentes y Mecánicas Técnicas

Guía de referencia de todas las reglas técnicas de servidor, persistencia de bots, fixes de shulkers, protección contra crashes y mecánicas QoL configurables en **Aeternum Carpet**.

---

## 1. Bots / Fake Players

| Regla | Default | Opciones | Comportamiento |
| --- | --- | --- | --- |
| `reloadFakePlayers` | `false` | `true`, `false` | Guarda los fake players de Carpet activos y los **respawnea automáticamente** reanudando sus acciones **20 ticks** tras iniciar el servidor. |
| `editBotInventory` | `false` | `true`, `false` | Permite abrir y editar inventario, armadura y manos de bots haciendo clic derecho sobre ellos. |

---

## 2. Almacenamiento, Shulkers y Acceso Rápido

| Regla | Default | Opciones | Comportamiento |
| --- | --- | --- | --- |
| `aeStackableShulkersFixes` | `false` | `true`, `false` | Fixes complementarios para `stackableShulkerBoxes` de Carpet: los hoppers mueven shulkers de **1 en 1** sin apilarlas y los comparadores mantienen señal no-apilable cuando ningún slot tiene `count > 1`. |
| `openShulkerInHand` | `false` | `true`, `false` | Permite abrir una shulker box directamente desde la mano al usarla en el aire (también vía `/quickshulker`). |
| `openEnderChestInHand` | `false` | `true`, `false` | Permite abrir tu Ender Chest directamente desde la mano al usarlo en el aire (también vía `/quickenderchest`). |

---

## 3. Minado, Portales y Redstone Técnica

| Regla | Default | Opciones | Comportamiento |
| --- | --- | --- | --- |
| `instamineDeepslate` | `false` | `true`, `false` | Permite minar deepslate instantáneamente con pico de netherite **Eficiencia V** y **Prisa II+**. |
| `fastObsidian` | `false` | `true`, `false` | Obsidiana y obsidiana llorosa se pican a velocidad de piedra con pico de netherite **Eficiencia V**. |
| `breakEndPortalFrames` | `false` | `true`, `false` | Permite romper marcos del portal del End con **maza Breach IV**, **Prisa II** y **Fuerza II** tras minado sostenido. |
| `breakEndPortalsWithWindCharge` | `false` | `true`, `false` | Permite que las **wind charges** eliminen bloques de portal del End en un área pequeña. |
| `oldFlintAndSteelBehavior` | `false` | `true`, `false` | Restaura el comportamiento **1.12** del mechero para actualizar observers y BUDs. |
| `spectatorPortalTravel` | `false` | `true`, `false` | Permite a jugadores en modo Espectador cruzar portales normalmente. |

---

## 4. Estabilidad, Mobs y Mundo

| Regla | Default | Opciones | Comportamiento |
| --- | --- | --- | --- |
| `updateSuppressionCrashFix` | `"false"` | `"false"`, `"true"`, `"silence"` | Evita que los crashes por *update suppression* tumben el servidor (`silence` además suprime el log/aviso). |
| `allowUnlimitedPlayerSpeed` | `false` | `true`, `false` | Desactiva la comprobación vanilla *moved too quickly* del servidor. |
| `naturalIceFormation` | `true` | `true`, `false` | Controla si el agua se congela de forma natural. |
| `protectedPots` | `false` | `true`, `false` | Evita que las vasijas decoradas se rompan salvo por minado explícito del jugador. |
| `phantomSpawning` | `"vanilla"` | `"vanilla"`, `"mobcap"`, `"disable"` | Controla el spawn de phantoms (`mobcap` los sujeta al mobcap, `disable` los desactiva). |
| `endermanGriefing` | `"vanilla"` | `"vanilla"`, `"farm_only"`, `"disable"` | Controla el griefing de endermen (`farm_only`: no colocan bloques y solo recogen cultivos de granja). |
