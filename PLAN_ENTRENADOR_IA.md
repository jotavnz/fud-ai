# Plan — Entrenador IA (fork de Fud AI)

Beta cerrada: 5–20 personas, Venezuela primero, nutrición pensada para ser general (no solo venezolana).
Solo inglés y español; el idioma elegido aplica a todo.

## Hecho
- Idioma EN/ES completo (UI + 877 ejercicios traducidos).
- Training: músculo → 3 máquinas con series/reps/descanso por objetivo; agregar a hoy.
- Cambiar una sola sugerencia ("Cambiar esta") y volver a la anterior ("Ver anterior").
- Sugerencias inteligentes (Ajustes): recuperación, fatiga cruzada, sobreuso, estancamiento, equilibrio empuje/tirón, músculo olvidado, cuánto peso subir.
- Resumen anual estilo Wrapped en Progreso: bloqueado hasta 1 año de uso ("Vuelve en X"), con interruptores en Ajustes (mostrar / ver antes de tiempo).

## En curso
- Coach: registrar comidas, agua, peso y entrenamientos desde el chat (con confirmación), copiar mensajes y "enviar a registro de comida".
- Pantalla de estado de Health Connect: qué se importa, de qué reloj/app, última sincronización, importar historial.

## Siguiente
1. Base propia: nombre, ícono, paquete; quitar RevenueCat/"Hosted"/enlaces de Fud AI; alojar las ilustraciones de ejercicios en servidor propio (hoy se descargan de assets.fud-ai.app).
2. Plan automático para principiantes (Full Body / Upper-Lower) — base: gymcoach (MIT).
3. Progresión en el diario: pesos de la última vez precargados, +2,5 kg al cumplir reps, descarga tras 3 fallos.
4. Mapa de recuperación muscular (frente/espalda) — SVG de body-highlighter (MIT).
5. Calculadora de calentamiento y de discos en kg.
6. Nutrición: foto → ajuste de porción en medidas caseras; macros desde base de datos (USDA, dominio público) + platos venezolanos/latinos revisados por nutricionista; recordar porciones habituales.
7. Retención: rachas, récords en vivo, tarjetas para WhatsApp/Instagram, superseries y tipos de serie.
8. Relojes: probar Samsung (Galaxy Watch del dueño), Xiaomi, Amazfit y Cubitt reales; ayuda para Huawei (Health Sync).
9. Nube y premium (más adelante): Supabase + sincronización offline; cuentas, varios dispositivos, panel de entrenador; privacidad y políticas de Play/Apple.
10. iPhone: portar las funciones nuevas a la app Swift (requiere Mac).

## Por hacer en algún momento (fuera de foco)
- **Escanear máquina con la cámara** (opcional; las ilustraciones ya orientan). Enfoque recomendado: Gemini con lista cerrada de 40–60 máquinas mapeadas a ids de free-exercise-db (~$0,001 por foto), opción "¿cuál de estas es?" si duda; luego modelo propio en el teléfono (MediaPipe Model Maker, Apache-2.0) entrenado con fotos confirmadas por usuarios.
- **Investigar las máquinas más usadas en gimnasios** (general y en LatAm): lista con nombre en ES/EN, foto de referencia con licencia libre (Wikimedia Commons, Unsplash, Pexels — guardar autor y licencia), músculos y ejercicio equivalente. Sirve para el escáner y para mejorar las listas curadas de MachineCoach.

## Licencias (resumen)
- Reutilizable (MIT / dominio público): gymcoach, Flex, workout-cool (solo código), caloriemate, body-highlighter, free-exercise-db (datos), exercises-dataset (solo texto).
- Solo ideas (GPL/AGPL/no comercial): wger, Liftosaur, GymMane, openGym, LiftLog, OpenNutriTracker, SparkyFitness.
- No usar: GIFs de ExerciseDB/Kaggle/Gumroad, ExRx, videos de MuscleWiki guardados en el teléfono, Ultralytics YOLO sin licencia.
- Las fotos originales de free-exercise-db tienen origen dudoso: no se incluyen en la app (solo las ilustraciones de Fud AI, MIT).
