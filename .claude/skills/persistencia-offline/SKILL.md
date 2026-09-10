---
name: persistencia-offline
description: Patrón de almacenamiento local y sincronización diferida para las capturas de trazo. Usar al implementar o revisar Room, WorkManager, la cola de subida, el esquema JSON de salida, o cualquier cosa relacionada con guardar capturas y enviarlas al servidor.
---

# Persistencia local y sincronización diferida

La app se usa en tamizajes sin conexión garantizada. El principio es simple y no admite atajos: **capturar siempre localmente, subir después, y no borrar nada hasta que el servidor confirme**.

Perder la captura de un paciente es irreparable: no se le puede pedir que vuelva a dibujar el mismo reloj.

## Ciclo de vida de una captura

1. El paciente termina el dibujo; la captura se escribe en Room con estado `PENDIENTE`.
2. WorkManager intenta subirla cuando haya red.
3. El servidor responde 2xx → estado `SINCRONIZADA` con marca de tiempo.
4. Cualquier otra respuesta o fallo de red → sigue `PENDIENTE`, se incrementa el contador de intentos, se reintenta después.

El estado nunca pasa a sincronizado por optimismo. Solo tras confirmación explícita.

## Esquema de Room

```kotlin
@Entity(tableName = "capturas")
data class CapturaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subject: String?,          // código de participante
    val task: String,              // "cubo" | "reloj" | "libre"
    val capturedAt: String,        // ISO-8601 UTC
    val payloadJson: String,       // el JSON completo, tal como se enviará
    val pointCount: Int,           // para verificación de integridad
    val strokeCount: Int,
    val syncStatus: String,        // "PENDIENTE" | "SINCRONIZADA"
    val attempts: Int = 0,
    val syncedAt: String? = null
)
```

Guardar el payload ya serializado tiene una ventaja concreta: lo que se sube es exactamente lo que se guardó, sin riesgo de que un cambio en las clases de datos altere silenciosamente el contenido de capturas viejas.

Índice sobre `syncStatus` para consultar pendientes sin recorrer toda la tabla.

## Sincronización con WorkManager

Usar `CoroutineWorker` con `Constraints` de red conectada y política de reintento exponencial. WorkManager sobrevive a reinicios del dispositivo y a que la app se cierre, que es justo lo que hace falta en campo.

El worker debe:

- Procesar las capturas pendientes de una en una, no todas en un lote. Si falla la quinta, las cuatro primeras ya quedaron confirmadas.
- Devolver `Result.retry()` ante fallos de red, y `Result.success()` cuando ya no queden pendientes.
- Ante un error 4xx del servidor que no sea transitorio (por ejemplo, payload rechazado por validación), marcar la captura para revisión manual en vez de reintentar indefinidamente. Un reintento infinito sobre un payload inválido gasta batería y no arregla nada.

Encolar el worker al guardar una captura y al arrancar la app.

## Verificación de integridad

El payload incluye un bloque con el conteo de puntos y trazos:

```json
"integrity": { "pointCount": 3421, "strokeCount": 7 }
```

El servidor debe verificar que recibió exactamente esa cantidad antes de responder 2xx. Una subida truncada que devuelva 200 se marcaría como sincronizada y los datos se perderían al liberar espacio.

## Formato JSON

Debe mantenerse compatible con `schema: "moca-trace/1"`, el que ya produce el prototipo web y para el que se diseñó el backend.

```json
{
  "schema": "moca-trace/1",
  "subject": "P-001",
  "task": "cubo",
  "capturedAt": "2026-09-09T14:32:11.482Z",
  "device": {
    "pointerType": "pen",
    "pressureSupported": true,
    "tiltSupported": true,
    "sampleRateHz": 120,
    "timeResolution": "ns",
    "pressureRange": [0.02, 0.98],
    "screenDensityDpi": 320,
    "canvasSizePx": [2560, 1600]
  },
  "features": { },
  "pointFormat": ["x", "y", "pressure", "tiltX", "tiltY", "timestampMs"],
  "strokes": [
    { "id": 1, "startMs": 10432.5, "endMs": 13890.2,
      "points": [[412.3, 208.7, 0.3412, -12, 4, 10432.5]] }
  ],
  "integrity": { "pointCount": 3421, "strokeCount": 7 }
}
```

Los puntos van como arreglos posicionales, no como objetos. Con miles de puntos por dibujo, repetir las claves en cada uno multiplica el peso varias veces sin aportar nada.

Campos que la versión nativa puede agregar sin romper compatibilidad: `timeResolution`, `pressureRange`, `screenDensityDpi`. Son aditivos y el backend los ignora si no los conoce.

Si hace falta un cambio que rompa compatibilidad, subir a `moca-trace/2` y decirlo explícitamente, nunca modificar el significado de un campo existente.

## Peso y retención

Un dibujo de 30 segundos a 120 Hz son unos 3.600 puntos, alrededor de 180 KB sin comprimir. Comprimir el cuerpo de la petición con gzip lo baja a 30–50 KB.

No borrar automáticamente las capturas ya sincronizadas. Dejar que el operador las elimine desde la interfaz cuando confirme que el servidor las tiene. El espacio no es el cuello de botella; perder datos sí.

## Datos clínicos

- El código de participante es un identificador de estudio, no un nombre. La app no debe pedir ni almacenar datos personales identificables.
- Nada de logs con contenido de las capturas en producción.
- La subida va solo al endpoint configurado del proyecto. Sin analítica de terceros, sin crash reporting que suba payloads.
