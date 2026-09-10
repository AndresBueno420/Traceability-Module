---
name: cinematica-trazo
description: Fórmulas y convenciones para calcular características cinemáticas del trazo (velocidad, aceleración, jerk normalizado, pausas, presión, segmentación en trazos). Usar al implementar o revisar cualquier cálculo sobre los puntos capturados, al definir umbrales de detección, o al agregar una variable nueva al análisis.
---

# Características cinemáticas del trazo

Estas son las variables que alimentan el análisis de deterioro cognitivo. Cada una tiene una razón clínica; no son métricas genéricas de dibujo.

## Convención de datos

Un trazo es una secuencia de muestras `(x, y, presión, inclinación, t)`. El muestreo **no es uniforme**: los intervalos varían. Todo cálculo debe usar el `dt` real entre muestras, nunca asumir un paso fijo.

Descartar muestras con `dt <= 0` en vez de dividir por cero. Ocurren cuando dos muestras comparten timestamp por cuantización.

## Derivadas sucesivas

Sobre la posición, con dt real en segundos:

- **Desplazamiento** entre muestras consecutivas: distancia euclidiana.
- **Velocidad**: desplazamiento dividido por dt. Componentes por separado (`vx`, `vy`) además de la magnitud, porque el análisis direccional puede importar.
- **Aceleración**: derivada de la velocidad. El dt a usar es el promedio de los dos intervalos que rodean la muestra, no uno de los dos.
- **Jerk**: derivada de la aceleración. Misma convención de dt promediado.

```kotlin
// dt promediado entre intervalos adyacentes al derivar series ya derivadas
val dtv = (tv[j] + tv[j - 1]) / 2.0
val ax = (vx[j] - vx[j - 1]) / dtv
```

Cada derivación amplifica el ruido. Al llegar al jerk se está amplificando tres veces, así que si la resolución temporal es de milisegundos conviene suavizar la serie antes de derivar (media móvil corta o Savitzky-Golay). Documentar cualquier suavizado aplicado: cambia el valor resultante y tiene que ser reproducible.

## Jerk normalizado

Es la variable más asociada a temblor en la literatura clínica. Se normaliza para poder comparar trazos de distinta duración y tamaño:

```
jerkNormalizado = sqrt( 0.5 * ∫jerk² dt * T⁵ / L² )
```

donde `T` es la duración del trazo en segundos y `L` la longitud del recorrido. El resultado es adimensional.

```kotlin
val nj = sqrt(0.5 * integralJerkCuadrado * T.pow(5) / (L * L))
```

Sin la normalización, un trazo largo y lento da valores enormes por razones geométricas y no clínicas.

Solo calcularlo si `L > 1` y `T > 0.05`. Por debajo de eso el resultado es ruido.

## Segmentación en trazos

Un dibujo son varios trazos separados por levantamientos del lápiz. `ACTION_DOWN` abre un trazo, `ACTION_UP` o `ACTION_CANCEL` lo cierra.

Calcular las variables **por trazo** y luego agregarlas al nivel del dibujo. Calcularlas sobre la concatenación de todos los puntos como si fueran uno solo introduce saltos artificiales entre el fin de un trazo y el inicio del siguiente, que se traducen en velocidades y jerks enormes que no existieron.

Variables a nivel de dibujo:

- **Número de trazos.** Un dibujo fragmentado en muchos trazos cortos puede indicar dificultad de planificación.
- **Tiempo en el aire**: suma de los intervalos entre el fin de un trazo y el inicio del siguiente. Es tiempo de decisión, no de ejecución, y en la literatura se asocia a carga cognitiva.
- **Tiempo total**: desde la primera muestra hasta la última.

## Pausas

Tramos donde la velocidad cae por debajo de un umbral durante más de un tiempo mínimo. Los valores de partida en el prototipo web son velocidad menor a 12 px/s durante más de 120 ms.

Ambos umbrales son provisionales y **dependen de la densidad de píxeles del dispositivo**. Antes de fijarlos definitivamente hay que calibrarlos con trazos reales, y preferiblemente expresar el umbral de velocidad en milímetros por segundo usando la densidad de pantalla, para que sea comparable entre dispositivos.

Registrar los umbrales usados en los metadatos de cada análisis. Un conteo de pausas sin el umbral que lo produjo no es interpretable.

## Presión

- **Media y desviación estándar** a lo largo del dibujo.
- **Tasa de cambio de presión** respecto al tiempo.
- **Correlación entre presión y velocidad.** Esta es menos obvia y vale la pena: una correlación anómala puede ser tan informativa como el valor crudo, porque en escritura normal la presión y la velocidad tienen una relación característica.

Nunca calcular estadísticas de presión si el dispositivo no entregó presión real. Devolver `null`, no cero ni 0.5. Un valor inventado contamina el análisis aguas abajo de forma indetectable.

## Análisis espectral

Para caracterizar temblor: transformada de Fourier sobre la serie de velocidad, buscando componentes en el rango de 4 a 18 Hz.

Requiere que la tasa de muestreo sea al menos el doble de la frecuencia máxima de interés. Con muestreo por debajo de 40 Hz este análisis no es válido y no debe reportarse.

Como el muestreo es irregular, hay que remuestrear a intervalo uniforme antes de aplicar la FFT. Interpolación lineal es suficiente en este rango.

## Unidades

Trabajar internamente en píxeles y segundos, pero **guardar la densidad de pantalla y el `devicePixelRatio` en los metadatos** para poder convertir a milímetros después. Sin eso, los datos de dos tablets distintas no son comparables y el dataset queda inservible para agregación.

## Qué no hacer todavía

No implementar modelos de clasificación sobre estas variables aún. La fase actual es de caracterización: calcular las variables, ver cuáles discriminan entre niveles de calidad clínica con estadística simple, y solo después construir un modelo. La decisión ya tomada es empezar con un modelo interpretable (regresión logística o score ponderado), no con una red recurrente, porque no hay volumen de datos que justifique lo segundo.
