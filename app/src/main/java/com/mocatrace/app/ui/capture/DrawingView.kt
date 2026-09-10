package com.mocatrace.app.ui.capture

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Canvas de captura para el S Pen. Implementa las 6 reglas del skill captura-stylus.
 *
 * El error más común en este dominio es silencioso: leer solo event.x/event.y descarta
 * la mayoría de las muestras del digitalizador sin ninguna excepción ni warning.
 * Este View aplica las protecciones necesarias para no perder muestras.
 */
class DrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // ---- Datos del dibujo ----
    private val trazos = mutableListOf<Trazo>()
    private val paths = mutableListOf<Path>()   // paralelo a trazos, para el dibujo en Canvas
    private var trazoActual: Trazo? = null
    private var pathActual: Path? = null

    // ---- Estado de la sesión ----
    // Una vez detectado el lápiz, los eventos de dedo se ignoran completamente
    // para no mezclar datos de palma con el trazo del paciente.
    private var lapizDetectado = false

    // ---- Métricas de diagnóstico ----
    private var totalEventos = 0
    private var totalMuestrasAcumuladas = 0
    private val todasLasMuestras = mutableListOf<Muestra>()

    /** Se invoca al terminar cada trazo con las métricas actualizadas. */
    var onDiagnosticsUpdate: ((DiagnosticoCaptura) -> Unit)? = null

    private val paint = Paint().apply {
        color = Color.BLACK
        strokeWidth = 4f
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    // ---- Manejo de eventos ----

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Regla 5: distinguir lápiz de dedo. Se usa el puntero que generó el evento.
        val toolType = event.getToolType(0)
        val esLapiz = toolType == MotionEvent.TOOL_TYPE_STYLUS ||
                      toolType == MotionEvent.TOOL_TYPE_ERASER
        val esDedo = toolType == MotionEvent.TOOL_TYPE_FINGER

        // Una vez que apareció el lápiz, se ignora todo lo que venga del dedo/palma.
        if (lapizDetectado && esDedo) return true
        if (!esLapiz && !esDedo) return true
        if (esLapiz) lapizDetectado = true

        // Regla 6: FLAG_CANCELED indica que el sistema identificó el contacto como palma (API 33+).
        // Se descarta el trazo completo, incluidos los puntos ya registrados.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (event.flags and MotionEvent.FLAG_CANCELED != 0) {
                cancelarTrazoActual()
                return true
            }
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Regla 3: reducir el agrupamiento de eventos mientras dure el gesto.
                // La variante con MotionEvent está disponible desde API 29; antes se usa sin arg.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    requestUnbufferedDispatch(event)
                } else {
                    @Suppress("DEPRECATION")
                    requestUnbufferedDispatch()
                }
                iniciarTrazo()
                procesarEvento(event)
            }
            MotionEvent.ACTION_MOVE -> procesarEvento(event)
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                procesarEvento(event)
                cerrarTrazo()
                emitirDiagnostico()
            }
        }

        invalidate()
        return true
    }

    /**
     * Extrae todas las muestras de un MotionEvent respetando el orden temporal correcto.
     *
     * Regla 1: el historial contiene las muestras más antiguas del lote; la muestra actual
     * (event.x/y) es la más reciente. Iterar primero el historial garantiza el orden cronológico.
     *
     * Regla 2: invertir este orden desordena la serie temporal y hace que las derivadas
     * (velocidad, aceleración, jerk) calculadas después sean incorrectas.
     */
    private fun procesarEvento(event: MotionEvent) {
        totalEventos++

        // Muestras históricas: las más antiguas del lote, van primero
        for (h in 0 until event.historySize) {
            registrarMuestra(
                Muestra(
                    x = event.getHistoricalX(0, h),
                    y = event.getHistoricalY(0, h),
                    presion = event.getHistoricalPressure(0, h),
                    // Regla 4: timestamps en ms. minSdk 26 no tiene getHistoricalEventTimeNanos().
                    // El análisis posterior debe suavizar antes de derivar al jerk.
                    // Se registrará timeResolution:"ms" en los metadatos de la captura.
                    tMs = event.getHistoricalEventTime(h)
                )
            )
        }

        // Muestra actual: la más reciente del lote, va al final
        registrarMuestra(
            Muestra(
                x = event.getX(0),
                y = event.getY(0),
                presion = event.getPressure(0),
                tMs = event.eventTime
            )
        )

        totalMuestrasAcumuladas += event.historySize + 1
    }

    private fun registrarMuestra(muestra: Muestra) {
        trazoActual?.muestras?.add(muestra)
        todasLasMuestras.add(muestra)

        // Actualizar el Path para que el dibujo sea visible de inmediato en el siguiente frame
        val path = pathActual ?: return
        val trazo = trazoActual ?: return
        if (trazo.muestras.size == 1) {
            path.moveTo(muestra.x, muestra.y)
        } else {
            path.lineTo(muestra.x, muestra.y)
        }
    }

    private fun iniciarTrazo() {
        val trazo = Trazo()
        trazos.add(trazo)
        trazoActual = trazo

        val path = Path()
        paths.add(path)
        pathActual = path
    }

    private fun cerrarTrazo() {
        trazoActual = null
        pathActual = null
    }

    private fun cancelarTrazoActual() {
        trazoActual?.muestras?.clear()
        if (trazos.isNotEmpty()) trazos.removeLast()
        if (paths.isNotEmpty()) paths.removeLast()
        cerrarTrazo()
        invalidate()
    }

    // ---- Diagnóstico ----

    private fun emitirDiagnostico() {
        val callback = onDiagnosticsUpdate ?: return
        if (todasLasMuestras.size < 2) return
        callback(calcularDiagnostico())
    }

    private fun calcularDiagnostico(): DiagnosticoCaptura {
        // Tasa efectiva: mediana de 1000/dt para cada intervalo entre muestras consecutivas.
        // Se usa la mediana porque un outlier (pausa entre trazos) inflaría la media.
        val intervalosMs = todasLasMuestras
            .zipWithNext { a, b -> (b.tMs - a.tMs).toDouble() }
            .filter { it > 0 }
        val tasaHz = if (intervalosMs.isNotEmpty()) {
            val sorted = intervalosMs.sorted()
            val mediana = sorted[sorted.size / 2]
            if (mediana > 0) 1000.0 / mediana else 0.0
        } else 0.0

        // > 1.0 confirma que el historial del MotionEvent se está leyendo (Regla 1)
        val muestrasPorEvento = if (totalEventos > 0)
            totalMuestrasAcumuladas.toDouble() / totalEventos
        else 0.0

        // > 3 valores distintos indica que hay sensor de presión real, no constante simulada
        val presionesDistintas = todasLasMuestras.map { it.presion }.distinct().size

        // Los timestamps deben ser estrictamente crecientes (Regla 2)
        val serieMonotona = todasLasMuestras.zipWithNext { a, b -> b.tMs > a.tMs }.all { it }

        return DiagnosticoCaptura(
            tasaHz = tasaHz,
            muestrasPorEvento = muestrasPorEvento,
            presionesDistintas = presionesDistintas,
            serieMonotona = serieMonotona,
            totalMuestras = todasLasMuestras.size,
            totalTrazos = trazos.size
        )
    }

    // ---- API pública ----

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (path in paths) {
            canvas.drawPath(path, paint)
        }
    }

    fun limpiar() {
        trazos.clear()
        paths.clear()
        todasLasMuestras.clear()
        totalEventos = 0
        totalMuestrasAcumuladas = 0
        lapizDetectado = false
        trazoActual = null
        pathActual = null
        invalidate()
        onDiagnosticsUpdate?.invoke(
            DiagnosticoCaptura(0.0, 0.0, 0, true, 0, 0)
        )
    }

    /** Devuelve los datos acumulados junto con el diagnóstico actual. */
    fun obtenerCaptura(): Pair<List<Trazo>, DiagnosticoCaptura?> {
        val diag = if (todasLasMuestras.size >= 2) calcularDiagnostico() else null
        return Pair(trazos.toList(), diag)
    }
}
