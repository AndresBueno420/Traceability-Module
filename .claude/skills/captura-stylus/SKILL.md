---
name: captura-stylus
description: Reglas para capturar eventos de stylus en Android sin perder muestras. Usar SIEMPRE al escribir o revisar código que toque MotionEvent, onTouchEvent, pointerInput, o cualquier captura de trazo, presión, inclinación o timestamps del S Pen. También al diagnosticar tasas de muestreo bajas, presión constante o trazos entrecortados.
---

# Captura de stylus sin pérdida de muestras

El error central de este dominio: el código de captura ingenuo **compila, dibuja bien y produce datos silenciosamente incorrectos**. No hay excepción, no hay warning. Simplemente se pierde la mayoría de las muestras y nadie se entera hasta que el análisis no discrimina nada.

## Regla 1 — Leer el historial del MotionEvent

Android **agrupa varias muestras del digitalizador en un solo `MotionEvent`** por eficiencia. Si el código solo lee `event.x` y `event.y`, está descartando todas las muestras intermedias del lote.

El S Pen muestrea a 120 Hz o más; los eventos llegan a la frecuencia de refresco. En un lote típico se pierden entre la mitad y dos tercios de las muestras.

Siempre iterar el historial **antes** de la muestra actual, porque el historial contiene las muestras más antiguas:

```kotlin
private fun MotionEvent.forEachSample(action: (x: Float, y: Float, p: Float, tMs: Long) -> Unit) {
    val i = actionIndex.coerceAtLeast(0)
    // Las muestras históricas son las más antiguas del lote y van primero.
    for (h in 0 until historySize) {
        action(
            getHistoricalX(i, h),
            getHistoricalY(i, h),
            getHistoricalPressure(i, h),
            getHistoricalEventTime(h)
        )
    }
    // Luego la muestra actual, que es la más reciente.
    action(getX(i), getY(i), getPressure(i), eventTime)
}
```

Invertir ese orden desordena la serie temporal y arruina cualquier derivada.

Existe el equivalente para inclinación: `getHistoricalAxisValue(MotionEvent.AXIS_TILT, pointerIndex, h)`.

## Regla 2 — Pedir despacho sin buffer

Aun leyendo el historial, el sistema sigue agrupando. `requestUnbufferedDispatch()` reduce ese agrupamiento y entrega los eventos con menos latencia:

```kotlin
override fun onTouchEvent(event: MotionEvent): Boolean {
    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
        // Reduce el batching mientras dure el gesto. Se cancela solo al terminar.
        requestUnbufferedDispatch(event)
    }
    ...
}
```

La variante que recibe el `MotionEvent` está disponible desde API 29. La variante `requestUnbufferedDispatch(inputSource)` existe desde antes. Usar la que corresponda al minSdk del proyecto.

No sustituye a la Regla 1: hay que hacer las dos.

## Regla 3 — La resolución del timestamp importa más de lo que parece

`getEventTime()` devuelve milisegundos. A 120 Hz las muestras están separadas ~8.3 ms, así que la cuantización a 1 ms introduce alrededor de 12% de error en cada intervalo.

Eso es tolerable para velocidad, pero el jerk es la **tercera derivada**: el error se amplifica en cada derivación. Un ruido de cuantización pequeño en el tiempo se vuelve enorme en el jerk, que es justamente la variable que se usa como proxy de temblor.

Desde API 34 existen `getEventTimeNanos()` y `getHistoricalEventTimeNanos(h)`. Si el dispositivo de pruebas los soporta, usarlos y guardar nanosegundos.

Si hay que quedarse en milisegundos, dejarlo registrado en los metadatos de la captura (`timeResolution: "ms"`) para que el análisis posterior sepa que debe suavizar antes de derivar tres veces. No silenciar el problema.

## Regla 4 — Distinguir el lápiz de la mano

La palma apoyada genera eventos táctiles que producen trazos fantasma. Filtrar por tipo de herramienta:

```kotlin
val esLapiz = event.getToolType(i) == MotionEvent.TOOL_TYPE_STYLUS ||
              event.getToolType(i) == MotionEvent.TOOL_TYPE_ERASER
```

Regla práctica: una vez que se detecta un stylus en la sesión, ignorar por completo `TOOL_TYPE_FINGER`. Mezclar ambas fuentes contamina los datos con trazos que el paciente no hizo.

Desde API 33, el sistema marca con `MotionEvent.FLAG_CANCELED` los eventos que él mismo identificó como palma. Respetarlo: si el flag está presente, descartar ese gesto completo, incluidos los puntos ya registrados.

## Regla 5 — Verificar que la presión es real

Muchos dispositivos devuelven presión constante cuando no hay sensor. Antes de tratar la presión como dato válido, comprobar que **varía** a lo largo de la sesión:

```kotlin
// Un valor único repetido en toda la sesión significa que no hay sensor real.
val presionEsReal = presiones.distinct().size > 3
```

No asumir que el rango es 0..1. La documentación indica que normalmente lo es, pero algunos dispositivos reportan valores por encima de 1. Registrar el mínimo y el máximo observados en los metadatos en vez de normalizar a ciegas.

## Regla 6 — Compose necesita cuidado extra

`pointerInput` de Compose puede filtrar o consolidar eventos. Si se usa Compose para el canvas de captura:

- Acceder al `MotionEvent` subyacente vía `PointerEvent.motionEvent` y aplicar las reglas anteriores sobre él.
- Alternativamente, usar `historical` en los `PointerInputChange`, que expone las muestras agrupadas.
- Si algo no cuadra, envolver una `View` clásica con `AndroidView` y hacer la captura ahí. La interfaz puede seguir en Compose; solo el canvas de captura baja a `View`.

Preferir la solución que se pueda verificar midiendo, no la más elegante.

## Cómo verificar que la captura está bien

No basta con que dibuje bonito. Instrumentar y medir:

1. **Tasa efectiva:** mediana de los intervalos entre muestras consecutivas, convertida a Hz. Si sale cerca de 60 Hz con un S Pen, se están perdiendo muestras: revisar la Regla 1.
2. **Muestras por evento:** promedio de `historySize + 1`. Si es 1.0, no está llegando el historial.
3. **Presión:** cantidad de valores distintos observados.
4. **Monotonía temporal:** ningún timestamp debe ser menor o igual al anterior. Si ocurre, el orden de iteración del historial está invertido.

Estas cuatro métricas deben ser visibles en la interfaz durante las pruebas, no solo en logs.

## Umbrales de referencia

Derivados del rango de frecuencias del temblor clínicamente relevante, que llega hasta ~18 Hz en el temblor ortostático. Por el teorema de Nyquist hay que muestrear al menos al doble:

- **Menos de 40 Hz:** inservible para análisis de temblor. Se pierden componentes reales de la señal.
- **60 Hz:** piso pragmático, validado en literatura para tareas de tamizaje.
- **100 Hz o más:** estándar en investigación de cinemática de escritura.

Si la medición cae por debajo de 40 Hz, el problema es de implementación, no de hardware: el S Pen da más.
