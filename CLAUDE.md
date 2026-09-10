# Captura de trazo MoCA — app Android

App de tablet para capturar los datos cinemáticos del dibujo durante las pruebas visoespaciales del MoCA (copia del cubo y dibujo del reloj). El paciente dibuja con el S Pen sobre la tablet; la app registra el proceso completo del trazo, no solo la imagen final.

Forma parte de un proyecto de grado sobre evaluación automatizada del MoCA con IA. El módulo de visión (que evalúa la imagen final con modelos multimodales) ya existe y es un servicio web aparte. Esta app cubre el módulo de trazabilidad dinámica, que es complementario.

## Contexto clínico

Los datos capturados alimentarán un análisis que busca discriminar entre niveles de deterioro cognitivo a partir de cómo se dibuja, no de qué se dibuja. Las variables de interés son tiempo, pausas, velocidad, presión y temblor.

El destinatario final del resultado es un neuropsicólogo. Cualquier salida del sistema tiene que ser explicable: si el análisis dice que hay temblor anómalo, tiene que poder señalarse en qué parte del trazo y con qué medida.

## Stack

- Kotlin, Android nativo. Sin Flutter ni React Native: se necesita acceso crudo al digitalizador.
- Jetpack Compose para la interfaz; el canvas de captura puede ser una `View` clásica si Compose estorba para el manejo de `MotionEvent`.
- Room para persistencia local.
- WorkManager para la cola de sincronización.
- Kotlinx Serialization para el JSON.
- minSdk 26. Documentar cualquier API que exija un nivel mayor y su alternativa.

Dispositivo de prueba: Samsung Galaxy Tab con S Pen.

## Restricciones que no se negocian

**Ninguna muestra se descarta.** La captura debe recuperar todas las muestras del digitalizador, incluidas las agrupadas en lotes por el sistema. Ver el skill `captura-stylus`. Este es el requisito del que depende todo lo demás: si la captura pierde muestras, ningún análisis posterior lo puede recuperar.

**Offline primero.** La app se usa en tamizajes sin conexión garantizada. Todo se guarda localmente y se sube después. Una captura solo se marca como sincronizada cuando el servidor confirma. Ver el skill `persistencia-offline`.

**El formato JSON de salida debe seguir siendo compatible** con el que ya produce el prototipo web (`schema: "moca-trace/1"`), porque el backend y el análisis posterior ya se diseñaron alrededor de él. Si hace falta cambiarlo, se sube la versión del esquema y se dice explícitamente.

**Sin telemetría ni servicios de terceros.** Son datos clínicos de pacientes. Nada sale del dispositivo salvo hacia el endpoint configurado del proyecto.

## Antecedente: el prototipo web

Existe un prototipo web funcional que hace lo mismo con Pointer Events. Sirve como referencia del formato de datos, de las métricas calculadas y del panel de diagnóstico. La app nativa existe porque la web depende del navegador para la tasa de muestreo y no da garantías sobre la resolución de presión.

Al implementar una funcionalidad que el prototipo web ya resuelve, revisar primero cómo lo hizo antes de inventar una solución distinta.

## Cómo trabajar en este repo

- Antes de tocar la captura de eventos, leer el skill `captura-stylus`. El código de captura tiene trampas que no son evidentes y que producen datos silenciosamente incorrectos: compila, dibuja bien, y los datos salen mal.
- Los cálculos cinemáticos van en el skill `cinematica-trazo`. Las fórmulas están ahí; no reinventarlas.
- Preferir código legible sobre código corto. Este repo lo mantienen tres estudiantes, no un equipo de plataforma.
- Comentar en español las partes que tienen razón clínica o de instrumentación, no las obvias del lenguaje.
- No agregar dependencias sin justificarlo. Cada librería nueva es algo que el equipo tiene que entender y mantener.

## Estado actual

Fase 1: captura y diagnóstico. El objetivo inmediato es tener una pantalla de dibujo que registre correctamente y un panel que reporte qué está entregando el hardware realmente (tipo de entrada, presión real o simulada, tasa de muestreo medida, resolución temporal). Esa medición es el entregable: define si el resto del proyecto es viable con este hardware.

Todavía no hay backend. La app debe funcionar y acumular capturas sin él.
