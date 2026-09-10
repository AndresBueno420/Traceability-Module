package com.mocatrace.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.mocatrace.app.navigation.Pantalla
import com.mocatrace.app.ui.capture.DiagnosticoCaptura
import com.mocatrace.app.ui.screens.PantallaCaptura
import com.mocatrace.app.ui.screens.PantallaInicio
import com.mocatrace.app.ui.screens.PantallaResultados
import com.mocatrace.app.ui.theme.MocaTraceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MocaTraceTheme {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot() {
    var pantallaActual by remember { mutableStateOf(Pantalla.INICIO) }
    // El diagnóstico se guarda aquí para pasarlo a Resultados sin depender de un nav back-stack.
    var ultimoDiagnostico by remember { mutableStateOf<DiagnosticoCaptura?>(null) }

    when (pantallaActual) {
        Pantalla.INICIO -> PantallaInicio(
            onIniciarCaptura = { pantallaActual = Pantalla.CAPTURA }
        )
        Pantalla.CAPTURA -> PantallaCaptura(
            onVerResultados = { diag ->
                ultimoDiagnostico = diag
                pantallaActual = Pantalla.RESULTADOS
            },
            onVolver = { pantallaActual = Pantalla.INICIO }
        )
        Pantalla.RESULTADOS -> PantallaResultados(
            diagnostico = ultimoDiagnostico,
            onVolver = { pantallaActual = Pantalla.CAPTURA }
        )
    }
}
