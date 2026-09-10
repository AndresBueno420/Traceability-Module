package com.mocatrace.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mocatrace.app.ui.capture.DiagnosticoCaptura
import com.mocatrace.app.ui.theme.AmarilloAlerta
import com.mocatrace.app.ui.theme.RojoError
import com.mocatrace.app.ui.theme.VerdeOK

@Composable
fun PantallaResultados(
    diagnostico: DiagnosticoCaptura?,
    onVolver: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Button(onClick = onVolver) { Text("← Volver a captura") }

        Text("Resultados de la sesión", style = MaterialTheme.typography.headlineMedium)

        if (diagnostico == null) {
            Text(
                "No hay datos. Dibuja en la pantalla de captura primero.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        // ---- Viabilidad del hardware ----
        val tasaOk = diagnostico.tasaHz >= 60
        val historialOk = diagnostico.muestrasPorEvento > 1.0
        val presionReal = diagnostico.presionesDistintas > 3
        val monotona = diagnostico.serieMonotona

        val colorTasa = when {
            diagnostico.tasaHz >= 100 -> VerdeOK
            diagnostico.tasaHz >= 60  -> AmarilloAlerta
            else                       -> RojoError
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Diagnóstico del hardware", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))

                FilaDiagnostico(
                    label = "Tasa de muestreo",
                    valor = "%.0f Hz".format(diagnostico.tasaHz),
                    color = colorTasa,
                    nota = when {
                        diagnostico.tasaHz >= 100 -> "Apta para análisis de temblor"
                        diagnostico.tasaHz >= 60  -> "Piso aceptable para tamizaje"
                        else                       -> "Insuficiente (<40 Hz = problema de implementación)"
                    }
                )
                FilaDiagnostico(
                    label = "Muestras por evento",
                    valor = "%.1f".format(diagnostico.muestrasPorEvento),
                    color = if (historialOk) VerdeOK else RojoError,
                    nota = if (historialOk) "Historial leído correctamente" else "No se está leyendo el historial del MotionEvent"
                )
                FilaDiagnostico(
                    label = "Presiones distintas",
                    valor = "${diagnostico.presionesDistintas}",
                    color = if (presionReal) VerdeOK else AmarilloAlerta,
                    nota = if (presionReal) "Sensor de presión real" else "Presión posiblemente simulada"
                )
                FilaDiagnostico(
                    label = "Serie temporal",
                    valor = if (monotona) "Monotóna ✓" else "NO monotóna ✗",
                    color = if (monotona) VerdeOK else RojoError,
                    nota = if (monotona) "Timestamps en orden correcto" else "El orden del historial puede estar invertido"
                )
            }
        }

        // ---- Totales ----
        Text(
            "Total: ${diagnostico.totalMuestras} muestras en ${diagnostico.totalTrazos} trazos",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FilaDiagnostico(
    label: String,
    valor: String,
    color: androidx.compose.ui.graphics.Color,
    nota: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(nota, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = valor,
            style = MaterialTheme.typography.bodyLarge,
            color = color,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}
