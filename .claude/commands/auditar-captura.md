---
description: Audita el código de captura de trazo buscando pérdida silenciosa de muestras
---

Revisa el código de captura de este repositorio y verifica, uno por uno, los puntos de abajo. Para cada uno di si se cumple, y si no, muestra el archivo, la línea y la corrección concreta.

No des un veredicto general de "se ve bien": recorre la lista completa aunque los primeros puntos estén correctos. Estos fallos no producen errores en tiempo de ejecución, así que la única forma de detectarlos es leyendo.

1. **Historial del MotionEvent.** ¿Se itera `historySize` con `getHistoricalX/Y/Pressure/EventTime` antes de leer la muestra actual? Si solo se lee `event.x` / `event.y`, se está perdiendo la mayoría de las muestras del digitalizador.

2. **Orden de las muestras.** Las históricas son las más antiguas y van primero, la actual va al final. ¿El orden es ese? Invertirlo desordena la serie temporal.

3. **Despacho sin buffer.** ¿Se llama `requestUnbufferedDispatch()` al recibir `ACTION_DOWN`?

4. **Resolución temporal.** ¿Se usan los timestamps en nanosegundos donde el nivel de API lo permite? Si se usan milisegundos, ¿queda registrado en los metadatos de la captura?

5. **Filtro de herramienta.** ¿Se distingue `TOOL_TYPE_STYLUS` de `TOOL_TYPE_FINGER`? Una vez detectado el lápiz, ¿se ignoran los eventos de dedo?

6. **Cancelación por palma.** ¿Se respeta `FLAG_CANCELED` descartando el gesto completo, incluidos los puntos ya registrados?

7. **Presión real.** ¿Se verifica que la presión varía antes de tratarla como válida? ¿Se devuelve `null` en vez de un valor inventado cuando no hay sensor?

8. **División por cero.** ¿Se descartan las muestras con `dt <= 0` antes de calcular velocidades?

9. **Segmentación.** ¿Las derivadas se calculan por trazo, o se concatenan todos los puntos? Concatenar produce saltos artificiales entre el fin de un trazo y el inicio del siguiente.

10. **Instrumentación.** ¿La interfaz muestra tasa de muestreo medida, muestras por evento, valores distintos de presión y monotonía temporal? Sin eso no hay forma de saber si la captura funciona.

Al final, propone una prueba manual concreta para verificar en el dispositivo lo que hayas encontrado.
