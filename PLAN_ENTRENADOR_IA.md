# Plan — Entrenador IA (fork de Fud AI)

Beta cerrada: 5–20 personas, Venezuela primero, nutrición pensada para ser general (no solo venezolana).
Solo inglés y español; el idioma elegido aplica a todo.

## Hecho
- Idioma EN/ES completo (UI + 877 ejercicios traducidos).
- Training: músculo → 3 máquinas con series/reps/descanso por objetivo; agregar a hoy.
- Cambiar una sola sugerencia ("Cambiar esta") y volver a la anterior ("Ver anterior").
- Sugerencias inteligentes (Ajustes): recuperación, fatiga cruzada, sobreuso, estancamiento, equilibrio empuje/tirón, músculo olvidado, cuánto peso subir.
- Resumen anual estilo Wrapped en Progreso: bloqueado hasta 1 año de uso ("Vuelve en X"), con interruptores en Ajustes (mostrar / ver antes de tiempo).
- Coach: registrar comidas, agua, peso y entrenamientos desde el chat (con confirmación), copiar mensajes y "enviar a registro de comida" (compila; falta probar en el teléfono).
- Pantalla de estado de Health Connect (compila; falta probar en el teléfono).
- Objetivo libre con el coach: para objetivos que no caben en bajar/mantener/subir (p. ej. bajar grasa abdominal y ganar músculo). Entrevista corta → tarjeta de plan → al aplicar fija calorías y entrenamiento; el coach lo recuerda siempre.
- Botón ⓘ con explicación en cada opción de objetivo y de entrenamiento.
- Pasos por fuente: la app usa el conteo más completo entre las apps que escriben en Health Connect (Honor Salud tapaba al Galaxy Watch: 5.720 vs 11.499); elegible en Ajustes → Health Connect; Inicio relee cada 5 min.
- Tutorial guiado omitible (resaltados en Inicio y pestañas); se repite desde Ajustes.
- Coach registra pasos (se suman al día) y sueño (hora de dormir/despertar u horas).
- Sueño de cualquier reloj o anillo vía Health Connect (gana el registro manual; si no, la fuente más completa) + recomendación de horas y hora de acostarse según entrenamiento, deuda de sueño y cena pesada tarde (desactivable).
- Mapa de recuperación muscular en Training (por grupo: % y "listo en X h", con consejos de recuperación activa), con series y cardio del reloj; el coach lo recibe.
- Alertas de sobreesfuerzo (notificación, desactivable): cardio del reloj sobre piernas cansadas, día de 150+ min, más de 25 series/semana en un grupo, 7 días sin descanso. Revisa cada 2 h y al abrir la app.
- Modo casa/gimnasio: en Training (Gimnasio | Casa) y desde el coach (tarjeta de plan con lugar y equipo); en casa sugiere mancuernas, bandas, pesa rusa o peso corporal. El coach pregunta cada ~14 días si sigue igual.

## En curso
- Probar todo lo anterior en el teléfono (Galaxy Watch 6 Classic + Honor) y con los amigos de la beta.

## Siguiente
1. Base propia: nombre (idea del dueño: **trAIng** o **trAIngCoach**, guiño a "entrenador AI"), ícono/logo, paquete; quitar RevenueCat/"Hosted"/enlaces de Fud AI; alojar las ilustraciones de ejercicios en servidor propio (hoy se descargan de assets.fud-ai.app).
2. Plan automático para principiantes (Full Body / Upper-Lower) — base: gymcoach (MIT).
3. Progresión en el diario: pesos de la última vez precargados, +2,5 kg al cumplir reps, descarga tras 3 fallos.
4. Dibujo del cuerpo (frente/espalda) para el mapa de recuperación — binarybeastt/musclemap (Kotlin/Compose, MIT) o body-highlighter (MIT); hoy el mapa es por grupos con colores.
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
- Reutilizable (MIT / Apache / dominio público): musclemap (Kotlin) y MuscleMap (Swift), Athlytics (ACWR), android/health-samples (sueño), darkhour-android (patrón de sueño), gymcoach, Flex, workout-cool (solo código), caloriemate, body-highlighter, free-exercise-db (datos), exercises-dataset (solo texto).
- Solo ideas (GPL/AGPL/no comercial): wger, Liftosaur, GymMane, openGym, LiftLog, OpenNutriTracker, SparkyFitness.
- No usar: GIFs de ExerciseDB/Kaggle/Gumroad, ExRx, videos de MuscleWiki guardados en el teléfono, Ultralytics YOLO sin licencia.
- Las fotos originales de free-exercise-db tienen origen dudoso: no se incluyen en la app (solo las ilustraciones de Fud AI, MIT).
