**1. FUNCIONALIDAD ACTUAL DETECTADA**

- Existe la rule Carpet `/carpet allowPlayerExploration true|false` en `AeternumCarpetSettings`.
- Existe carga/creación de config en `config/aeternum-carpet.json` desde `AeternumCarpetConfig`.
- El JSON real usa `allowedGenerationAreas`, y cada región actualmente usa estos campos:
  - `name`
  - `dimension`
  - `centerX`
  - `centerZ`
  - `size`
- Hay compatibilidad con config vieja sin `dimension`: al cargar, si falta, se asume `minecraft:overworld`.
- Existen estos comandos exactos en `AeternumCommand`:
  - `/aeternum region add <size> <name>`
  - `/aeternum region del <name>`
  - `/aeternum region info`
- Restricciones reales de comandos:
  - `add`: requiere permiso nivel 2 y jugador.
  - `del`: requiere permiso nivel 2, pero no exige jugador.
  - `info`: exige jugador, pero no permiso nivel 2.
- La dimensión sí se usa ya en la lógica:
  - al crear región, se guarda la dimensión actual del jugador.
  - `region info` filtra regiones por dimensión actual del jugador.
  - el guard de exploración filtra regiones por dimensión actual del jugador.
- Existe lógica activa por tick en `PlayerExplorationGuard`, registrada en `AeternumCarpet` con `ServerTickEvents.END_SERVER_TICK`.
- Si `allowPlayerExploration = true`, el guard no hace nada.
- Si `allowPlayerExploration = false`, el guard revisa todos los jugadores y, si están fuera de cualquier región válida de su dimensión, intenta teletransportarlos a una posición válida dentro del borde de la región más cercana de esa misma dimensión.
- Si no hay regiones para la dimensión actual, no teletransporta; solo hace warning por log una vez por dimensión.

**2. PRUEBAS MANUALES RECOMENDADAS**

- Prueba: arrancar servidor sin `config/aeternum-carpet.json`
  - Acción: iniciar servidor limpio.
  - Resultado esperado: se crea `config/aeternum-carpet.json` con `allowedGenerationAreas: []`.
  - Bug si: no se crea, se crea mal, o el servidor falla al iniciar.

- Prueba: compatibilidad con config vieja
  - Acción: dejar una región sin `dimension` en el JSON e iniciar servidor.
  - Resultado esperado: el servidor no crashea y la región se trata como `minecraft:overworld`.
  - Bug si: falla al cargar o ignora la región de forma inesperada.

- Prueba: agregar región en Overworld
  - Comando: `/aeternum region add 64 main`
  - Resultado esperado: responde con `name`, `dimension`, `centerX`, `centerZ`, `centerChunkX`, `centerChunkZ`, `size`; el JSON guarda `dimension: minecraft:overworld`.
  - Bug si: no guarda dimensión, guarda coordenadas incorrectas o no persiste en archivo.

- Prueba: agregar región en Nether
  - Acción: ir al Nether y ejecutar `/aeternum region add 32 nether_main`
  - Resultado esperado: se guarda con `dimension: minecraft:the_nether`.
  - Bug si: se guarda como Overworld o no usa la dimensión actual.

- Prueba: rechazo de nombres inválidos
  - Comando: `/aeternum region add 64 nombre-con-espacio`
  - Resultado esperado: rechazo.
  - Bug si: acepta espacios u otros caracteres fuera de letras, números, `_`, `-`.

- Prueba: rechazo de size inválido
  - Comando: `/aeternum region add 0 test`
  - Resultado esperado: rechazo.
  - Bug si: crea región con `size <= 0`.

- Prueba: rechazo de nombre duplicado
  - Acción: crear una región y volver a crear otra con el mismo nombre exacto.
  - Resultado esperado: rechazo.
  - Bug si: duplica nombres.

- Prueba: borrar región existente
  - Comando: `/aeternum region del main`
  - Resultado esperado: confirma borrado y desaparece del JSON.
  - Bug si: sigue en archivo o no borra la exacta.

- Prueba: borrar región inexistente
  - Comando: `/aeternum region del no_existe`
  - Resultado esperado: mensaje de error.
  - Bug si: dice que borró algo o modifica config.

- Prueba: info dentro de región
  - Acción: pararse dentro de una región y ejecutar `/aeternum region info`.
  - Resultado esperado: muestra dimensión actual, chunk actual y las regiones que contienen ese chunk, con límites `min/max`.
  - Bug si: no lista región válida o lista regiones de otra dimensión.

- Prueba: info fuera de región
  - Acción: pararse fuera de cualquier región de esa dimensión y ejecutar `/aeternum region info`.
  - Resultado esperado: indica chunk actual y que no estás dentro de ninguna región.
  - Bug si: reporta una región incorrecta.

- Prueba: exploración permitida
  - Comando: `/carpet allowPlayerExploration true`
  - Acción: salir caminando de la región.
  - Resultado esperado: no hay corrección ni teletransporte.
  - Bug si: igual te devuelve.

- Prueba: exploración bloqueada caminando
  - Comando: `/carpet allowPlayerExploration false`
  - Acción: caminar fuera del borde de una región válida.
  - Resultado esperado: el jugador es devuelto dentro del área y aparece el mensaje de aviso.
  - Bug si: puedes alejarte libremente, te manda a una dimensión/región equivocada, o te teletransporta a una posición claramente fuera del borde.

- Prueba: exploración bloqueada con `/tp`
  - Comando: `/tp <coords fuera de región>`
  - Resultado esperado: tras aparecer fuera, el tick siguiente te devuelve a una región válida de esa dimensión.
  - Bug si: te deja fuera permanentemente.

- Prueba: regiones por dimensión
  - Acción: tener región solo en Overworld, poner `allowPlayerExploration = false`, entrar al Nether.
  - Resultado esperado: no usa la región del Overworld; no debe teletransportarte a esa región. Debe quedar sin aplicar bloqueo en Nether y registrar warning.
  - Bug si: aplica región de otra dimensión.

- Prueba: región válida en Nether
  - Acción: crear región en Nether y salir de ella con `allowPlayerExploration = false`.
  - Resultado esperado: te corrige usando la región del Nether.
  - Bug si: te manda a Overworld, no corrige, o usa una región de dimensión distinta.

- Prueba: sin regiones en dimensión actual
  - Acción: dejar `allowPlayerExploration = false` y no tener regiones para la dimensión donde estás.
  - Resultado esperado: no teletransporta en bucle y solo deja warning en log.
  - Bug si: te mueve a un lugar indefinido o hace spam constante.

- Prueba: cambio de dimensión
  - Acción: con bloqueo activo, pasar de Overworld a Nether o End.
  - Resultado esperado: en la nueva dimensión solo se consideran sus propias regiones; si no hay, no corrige.
  - Bug si: usa regiones de la dimensión anterior.

- Prueba: respawn
  - Acción: morir y respawnear con bloqueo activo, dentro y fuera de región.
  - Resultado esperado: si reapareces fuera de región válida, el guard debería corregirte en ticks siguientes.
  - Bug si: quedas fuera sin corrección o entras en teletransporte extraño.

- Prueba: seguridad del destino
  - Acción: salir del borde cerca de montaña, cueva, techo bajo, agua o terreno raro.
  - Resultado esperado: intenta dejarte en una posición caminable de esa columna o fallback a spawn de la dimensión.
  - Bug si: te mete dentro de bloques, te deja cayendo bajo el mundo o no logra corregir nunca.

**3. RIESGOS NO CUBIERTOS**

- La corrección es reactiva por tick, no preventiva. Primero sales y luego intenta devolverte.
- La búsqueda de posición segura es básica y local a una sola columna. Puede fallar en terreno extraño, estructuras, líquidos, hojas, techos bajos o columnas problemáticas.
- Si la posición bordeada no es segura y el spawn tampoco lo es, el código simplemente no mueve al jugador.
- No hay manejo especial para End/Nether más allá del filtro por dimensión; conviene probarlos porque la superficie segura puede comportarse distinto.
- No hay validación runtime aquí de cómo se siente el cooldown del mensaje.
- `region info` no está restringido por permiso 2; cualquier jugador puede usarlo si el servidor se lo permite.
- `region del` no exige jugador; puede ejecutarse desde consola si tiene permisos.
- El warning de “sin regiones” es por dimensión y por ciclo de habilitación, pero conviene probar logs para confirmar que no haga spam inesperado.
- No hay pruebas automáticas; los casos de `/tp`, cambio de dimensión, respawn y terrenos raros necesitan validación real en juego.