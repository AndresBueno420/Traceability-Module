package com.mocatrace.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mocatrace.app.ui.capture.DiagnosticoCaptura
import com.mocatrace.app.ui.capture.DrawingView
import com.mocatrace.app.ui.theme.AmarilloAlerta
import com.mocatrace.app.ui.theme.RojoError
import com.mocatrace.app.ui.theme.VerdeOK

@Composable
fun PantallaCaptura(
    onVerResultados: (DiagnosticoCaptura?) -> Unit,
    onVolver: () -> Unit
) {
    var diagnostico by remember { mutableStateOf<DiagnosticoCaptura?>(null) }
    var drawingView by remember { mutableStateOf<DrawingView?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {

        // ---- Barra superior ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onVolver) { Text("← Inicio") }
            Text("MoCA Trace", style = MaterialTheme.typography.titleMedium)
            Button(
                onClick = {
                    val (_, diag) = drawingView?.obtenerCaptura() ?: return@Button
                    onVerResultados(diag)
                }
            ) { Text("Ver resultados") }
        }

        // ---- Canvas de dibujo ----
        AndroidView(
            factory = { ctx ->
                DrawingView(ctx).also { view ->
                    drawingView = view
                    view.setBackgroundColor(android.graphics.Color.WHITE)
                    // El callback viene del hilo principal (onTouchEvent), así que
                    // asignar a un MutableState es seguro sin necesidad de coroutines.
                    view.onDiagnosticsUpdate = { diag -> diagnostico = diag }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        // ---- Panel de diagnóstico ----
        BarraDiagnostico(
            diagnostico = diagnostico,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // ---- Controles ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            OutlinedButton(
                onClick = { drawingView?.limpiar() },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Limpiar")
            }
        }
    }
}

/**
 * Muestra las cuatro métricas clave que confirman que el hardware entrega datos válidos.
 * Si alguna está en rojo, hay un problema de implementación, no de hardware.
 */
@Composable
private fun BarraDiagnostico(
    diagnostico: DiagnosticoCaptura?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (diagnostico == null) {
            Text(
                "Dibuja para ver el diagnóstico",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Row
        }

        val colorTasa = when {
            diagnostico.tasaHz >= 100 -> VerdeOK
            diagnostico.tasaHz >= 60  -> AmarilloAlerta
            else                       -> RojoError
        }
        MetricaTexto(
            label = "Hz",
            valor = "%.0f".format(diagnostico.tasaHz),
            color = colorTasa
        )
        MetricaTexto(
            label = "Muestras/ev",
            valor = "%.1f".format(diagnostico.muestrasPorEvento),
            color = if (diagnostico.muestrasPorEvento > 1.0) VerdeOK else RojoError
        )
        MetricaTexto(
            label = "Presiones",
            valor = "${diagnostico.presionesDistintas}",
            color = if (diagnostico.presionesDistintas > 3) VerdeOK else AmarilloAlerta
        )
        MetricaTexto(
            label = "Monotóna",
            valor = if (diagnostico.serieMonotona) "✓" else "✗",
            color = if (diagnostico.serieMonotona) VerdeOK else RojoError
        )
        Text(
            text = "${diagnostico.totalMuestras} pts · ${diagnostico.totalTrazos} trazos",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MetricaTexto(label: String, valor: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(valor, style = MaterialTheme.typography.titleSmall, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
