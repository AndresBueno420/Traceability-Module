package com.mocatrace.app.ui.capture

/** Una muestra individual del digitalizador. */
data class Muestra(
    val x: Float,
    val y: Float,
    val presion: Float,
    val tMs: Long   // milisegundos desde el arranque del sistema (event.eventTime)
)

/** Secuencia de muestras entre un ACTION_DOWN y su ACTION_UP/CANCEL. */
data class Trazo(
    val muestras: MutableList<Muestra> = mutableListOf()
)

/**
 * Métricas que el panel de diagnóstico debe mostrar para confirmar que el hardware
 * entrega datos útiles. Sin estas cuatro cifras no se puede saber si la captura funciona.
 */
data class DiagnosticoCaptura(
    /** Mediana de la frecuencia de muestreo efectiva en Hz. */
    val tasaHz: Double,
    /** Promedio de muestras por evento. Debe ser > 1.0 si se lee el historial correctamente. */
    val muestrasPorEvento: Double,
    /** Cantidad de valores de presión distintos. > 3 indica sensor real, no simulado. */
    val presionesDistintas: Int,
    /** Los timestamps deben ser estrictamente crecientes. false = orden del historial invertido. */
    val serieMonotona: Boolean,
    val totalMuestras: Int,
    val totalTrazos: Int
)
