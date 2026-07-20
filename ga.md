Sí, es posible, y la vía más práctica hoy es `MCA Selector`.

Lo que encontré:
- `MCA Selector` es una herramienta externa para Minecraft Java que permite seleccionar y luego borrar chunks o regiones del mundo guardado. El repo oficial lo describe así y su release más reciente visible es `2.8`, publicada el `15 de junio de 2026`.  
  https://github.com/Querz/mcaselector
- Su wiki dice que una selección se puede:
  - invertir con `Selection -> Invert`
  - importar/exportar con `Selection -> Import selection` y `Export selection`
  - borrar con `Selection -> Delete selected chunks`  
  https://github.com/Querz/mcaselector/wiki/Selections
- El formato de selección es CSV separado por `;`, y para chunks usa exactamente 4 valores: `regionX;regionZ;chunkX;chunkZ`. O sea: el formato que estás exportando es compatible con MCA Selector.  
  https://github.com/Querz/mcaselector/wiki/Selections
- También soporta filtros para seleccionar chunks por condiciones y luego exportarlos o borrarlos.  
  https://github.com/Querz/mcaselector/wiki/Chunk-Filter

Entonces, para “borrar los no seleccionados”, el flujo sería:
1. Hacer/importar tu selección.
2. Usar `Selection -> Invert`.
3. Ejecutar `Selection -> Delete selected chunks`.

Sobre si el server debe estar apagado:
- No vi en la wiki una frase literal de “debe estar apagado”, pero sí está claro que la herramienta modifica directamente los archivos del mundo (`region`, `poi`, `entities`) en disco.
- Por eso, en la práctica, para un servidor dedicado lo correcto es hacerlo offline: apagar el server, hacer backup, editar el mundo, y volver a arrancar. Varias guías de hosting lo plantean así, descargando el mundo, editándolo fuera del server y re-subiéndolo.  
  https://docs.ultraservers.com/minecraft/server-management/use-mca-selector-to-trim-chunks

Mi conclusión:
- Sí, se puede borrar todo lo no seleccionado.
- Sí, tu CSV sirve como base para MCA Selector.
- Para un server, yo lo trataría como operación offline obligatoria en la práctica, aunque eso te lo digo como inferencia fuerte por cómo trabaja la herramienta sobre los archivos del mundo, no como cita textual de la wiki oficial.

No modifiqué nada en el repo. Si quieres, en el siguiente paso puedo decirte exactamente cómo sería el procedimiento con tus 3 CSV de Overworld/Nether/End para hacerlo sin equivocarte.