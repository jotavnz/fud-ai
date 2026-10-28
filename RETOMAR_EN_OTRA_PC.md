# Retomar el proyecto en otra PC

Última actualización: **2026-10-28** (oficina, tarde). Todo está en GitHub: **jotavnz/fud-ai**, rama **`feature/entrenador-ia-es`**.
Lee también `PLAN_ENTRENADOR_IA.md` y **`PLAN_ENTRENADOR_AMIGABLE.md`** (plan de la tanda del 27–28 y pendientes).

## 00. Estado al 2026-10-28 (leer primero)

**Versiones**
| APK | Commit / etiqueta | Estado |
|---|---|---|
| `EntrenadorIA-beta-2026-10-27n.apk` | `83fdda6f8`, etiqueta `estable-2026-10-27n` | Estable, probada en el teléfono |
| `EntrenadorIA-beta-2026-10-27o.apk` | `742dfbbf2` | Despensa en Inicio, despensa por voz, sueño corto |
| `EntrenadorIA-beta-2026-10-28a.apk` | `9b6bacaba` | + imágenes Gym visual (272), ya reemplazadas |
| `EntrenadorIA-beta-2026-10-28b.apk` (oficina) | `e6786f39d` | Catálogo nuevo de 1.368 ejercicios, buscador tolerante |
| `EntrenadorIA-beta-2026-10-28c.apk` (oficina) | `95b9221de` | + imágenes para los 44 sin foto, caducidad por alimento (FoodKeeper), aviso de calentamiento |
| `EntrenadorIA-beta-2026-10-28d.apk` (oficina) | `eb84338a6` | + Fase 1: rutinas de inicio, ✓ por serie, peso de la última vez, glosario |
| `EntrenadorIA-beta-2026-10-28e.apk` (oficina) | `a345b0351` | + reglas del coach (descarga, inicio suave, logros, próxima sesión, estilo), progresiones. **Es la que está en el teléfono** (firmada con el `debug.keystore` de casa, ya copiado a la oficina) |

Las APK viven en la carpeta `APK` junto al repo en casa y en `C:\EntrenadorIA\` en la oficina (no en Git).

**Hecho en la tanda del 27–28** (detalle en `PLAN_ENTRENADOR_AMIGABLE.md`): vista sencilla/experta (Ajustes → Entrenamiento),
"Entrenar paso a paso", calentamiento y vuelta a la calma, 22 ejercicios propios (hipopresivos, core profundo, postura,
mayores, calistenia), coach con reglas de entrenador atento (`get_session_extras`, `get_pantry`, `get_cycle`,
`add_pantry_items`), Despensa (tarjeta en Inicio, código de barras, foto de factura, lista de compras, gastos, por voz con
aviso de duplicados), Ciclo menstrual (opt-in, síntomas, importar de Health Connect), avisos (comida por vencer, volver tras
7 días), búsqueda con tildes, scroll más fluido, esfera de voz encima del +, sueño en Mi cuerpo limitado a 5 noches.

**Catálogo de ejercicios (cambió el 28 en la oficina):** la biblioteca ya no es free-exercise-db (877) sino la carpeta
local `exercises-dataset-main` (1.324 ejercicios, foto + animación, instrucciones en español del propio dataset; nombres
traducidos en `scripts/exercise-names-es.json`), más 22 cardio/calentamientos básicos que el dataset no tiene y los
22 ejercicios propios: 1.368 en total. Ids nuevos `ed_<id del dataset>`; el historial, las rutinas y los favoritos
con ids viejos se migran al leerse (`exercise-id-migration.json`). El crédito de las imágenes es
"© tr.AI.ng Coach — All.In.One App". Las fotos y animaciones **sí están en Git** (`android/app/src/exercisemedia/`,
~53 MB: JPG + WebP animado), así que basta con `git pull`. Solo si cambia el dataset hay que regenerarlas, con la
carpeta del dataset y `gif2webp.exe` (libwebp de Google, storage.googleapis.com/downloads.webmproject.org/releases/webp/):
```
node scripts/import_exercises_dataset.mjs "<ruta>/exercises-dataset-main" --media <ruta>/gif2webp.exe
```
Eso regenera el catálogo y convierte solo los medios que falten.

**Hecho la tarde del 28 (oficina):**
- Los 22 ejercicios propios y los 22 de cardio/calentamiento conservados usan la foto (y la animación cuando el
  movimiento coincide) de un ejercicio parecido del dataset: `scripts/exercise-borrowed-media.json`.
- Caducidad por alimento con USDA FoodKeeper (`scripts/build_shelf_life.mjs` → `shelf_life.json`,
  diccionario español en `scripts/shelf-life-es.json`).
- Aviso de calentamiento (Ajustes → Entrenamiento: días y hora habituales; avisa 30 min antes).
- Fase 1 del diario: rutinas de inicio, ✓ por serie, peso de la última vez, glosario, controles más grandes.
- Reglas del coach: semana de descarga, inicio suave, logros de constancia, próxima sesión, estilo detectado.
- Progresiones de peso corporal en el detalle del ejercicio.
- Se quitó el permiso de pulso (`READ_HEART_RATE`), que nada usaba.
- `PLAYSTORE.md` tiene los textos para declarar sueño y ciclo en Google Play.

**Hecho al final de la tarde del 28 (`EntrenadorIA-beta-2026-10-28f.apk`, commits `b8dee95a5` y `7ecb4cda7`, NO instalada aún):**
- Chat del coach: el borrador, la foto y la respuesta en curso viven en `CoachSession` (AppContainer), así que girar
  el teléfono o cambiar de pestaña ya no los borra.
- Despensa: "Foto de factura" y "Foto de nevera" con Tomar foto / Galería, casilla "Guardar automáticamente, sin
  revisar", botón "Contarle al coach lo que tengo". La herramienta del coach `update_pantry` (reemplaza a
  `add_pantry_items`) aplica add/set/used_up/discard sin preguntar y junta lo repetido (`PantryEditor` en `Pantry.kt`).

**SIGUIENTE (pedido el 28 al cerrar, en este orden):**
1. HECHO (commit "Warn when adding food to a day other than today", sin compilar APK ni probar en el teléfono): aviso ámbar
   en Inicio bajo la tira de días con "Volver a hoy", y en la hoja de revisión de comida con "Guardar hoy"
   (`ui/home/OtherDayNotice.kt`). Antes decía: aviso al añadir comida en un día que no es hoy: que se note en qué día
   estás añadiendo, sin bloquear.
2. Despensa y mercado con más diseño, inspirado en whats-in-the-fridge (MIT: semáforo de frescura, etc.) y grocy
   (MIT: historial de precios por tienda). Solo ideas, no copiar código ajeno sin revisar licencia.
3. Histórico de compras: registro de cada vez que se hace el mercado (fecha, tienda, total, qué se compró) y un botón
   para verlo. Hoy solo las facturas guardan `GroceryPurchase`; también deberían contar el stock de la lista de compras,
   la foto de nevera no.
4. HECHO: `EntrenadorIA-beta-2026-10-28g.apk` (commit `ba0e4728e`, incluye 28f + aviso de otro día) instalada en el
   teléfono el 28 a las 17:10, firma de casa verificada. iOS: el usuario dijo que todavía no.

**Pendiente:**
- Probar en el teléfono: factura real, código de barras, importación de Flo, despensa por voz.
- Declarar `READ_SLEEP` y `READ_MENSTRUATION` en Google Play antes de publicar (textos listos en `PLAYSTORE.md`).
- Contenido propio nuevo (60–80 ejercicios, Otago, hipopresivos avanzados), idealmente revisado por un fisioterapeuta.

**Para Claude en la otra PC:** el usuario prefiere mensajes muy cortos y que se siga el plan sin pedir confirmación
en cada paso; ignorar las 3 pruebas que fallan solo en Windows (abajo); nunca copiar código GPL/AGPL (openGym, wger,
Menstrudel) a esta app MIT; no subir medios de RepDB a Git (los del dataset sí, con permiso del usuario). Commits con
`git -c user.name=jotavnz -c user.email=jotavnz@users.noreply.github.com commit …` si la PC no tiene identidad.

## 0. Arranque rápido en otra PC

**Antes, desde la PC de casa:** copia `C:\Users\Job\.android\debug.keystore` a la oficina, **en la misma ruta**
(`C:\Users\<tu usuario>\.android\debug.keystore`, reemplazando el que haya). Sin eso, las APK de la oficina
salen con otra firma y no se instalan encima de la beta: habría que desinstalar y se pierden los datos del
teléfono. Pásalo por un lugar privado (USB, tu Drive personal), nunca por la carpeta de los amigos ni por Git.

**En la PC de la oficina:**
1. Si ya está el clon del 26-oct, actualízalo (guarda o descarta antes cualquier cambio local con `git status`):
   ```bash
   git fetch origin
   git switch feature/entrenador-ia-es
   git pull
   ```
   Si no está, sigue la sección 1 (clonar fuera de OneDrive).
2. Comprueba JDK 17 y Android SDK (sección 2) y que `android/local.properties` apunte al SDK de esa PC.
3. Compila y prueba (PowerShell, dentro de `android/`):
   ```
   .\gradlew.bat testDebugUnitTest -PworkoutVectors=none
   .\gradlew.bat assembleDebug -PworkoutVectors=none
   ```
   Pasan todas salvo 3 que fallan solo en Windows: `ConsistentDataStoreTest.reReadsAfterAWriteDoNotReEmitEqualValues`,
   `StorageMaintenanceTest.compressionOnly…` y `FoodImageStoreRestoreTest.stagingRefusesANameThatIsASymbolicLink…`.
4. Con el teléfono por cable: `adb install --no-streaming -r --user 0 app\build\outputs\apk\debug\app-debug.apk`
   (sección 3). Si dice `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, el keystore no es el de casa: no desinstales, copia el keystore.
5. Abre Claude Code en la carpeta del repo y escribe:
   > Lee RETOMAR_EN_OTRA_PC.md y PLAN_ENTRENADOR_IA.md y continúa desde la sección 0. Quiero hacer estos cambios: …

**Pendiente del usuario (no es código):**
- Teléfono: Samsung Health → Ajustes → Health Connect → activar sincronización con **todos** los tipos.
  Luego en la app: Ajustes → Health Connect → estado → "Apps de salud en este teléfono" debe mostrar
  Samsung Health "Compartiendo". Por cable: `adb shell dumpsys healthconnect` → "Contributed Data: true".
- App: Ajustes → Health Connect → "Importar desde Samsung Health" (carpeta de la descarga de datos personales)
  para traer los 6 meses anteriores. Probado con la descarga real: 132 días de pasos, 148 noches,
  208 entrenamientos, 8 pesajes.
- Drive "EntrenarIA": subir `EntrenadorIA-beta-2026-10-27d.apk` (carpeta `APK` junto al repo en la PC de casa)
  y marcar `EntrenadorIA-debug.apk` (26-oct, oficina, otra firma) como antigua.

**Ideas siguientes (ver `PLAN_ENTRENADOR_IA.md`):** probar en el teléfono todo lo de la tanda del 27; renombrar a
trAIng / trAIngCoach con logo (todavía no aplicar); escáner de máquinas.

## 1. Descargar el código

Usa una carpeta **fuera de OneDrive/Dropbox** (OneDrive bloquea archivos de compilación), por ejemplo `C:\EntrenadorIA`:

```bash
git clone https://github.com/jotavnz/fud-ai.git C:/EntrenadorIA/fud-ai
cd C:/EntrenadorIA/fud-ai
git switch feature/entrenador-ia-es
```

## 2. Preparar la PC (Windows)

**Opción fácil — Android Studio:** instálalo, abre la carpeta `android/`, deja que descargue el SDK. En *SDK Manager* instala **Android SDK Platform 37.2** y **Build-Tools 37.0.0**. Usa el JDK 17 que trae o uno aparte.

**Opción manual (la que se usó en la primera PC):**
- JDK 17 (Temurin).
- Android SDK command-line tools, y luego:
  `sdkmanager "platforms;android-37.2" "build-tools;37.0.0" "platform-tools"` (acepta licencias con `sdkmanager --licenses`).
- Crea `android/local.properties` (no se sube a Git) con: `sdk.dir=C\:\\ruta\\al\\android-sdk`

Compilar (PowerShell, dentro de `android/`):
```
$env:JAVA_HOME='C:\ruta\jdk-17'; $env:ANDROID_HOME='C:\ruta\android-sdk'
.\gradlew.bat assembleDebug
```
La primera vez tarda ~30 min (descarga Gradle y librerías). El APK sale en `android/app/build/outputs/apk/debug/app-debug.apk`.

## 3. Instalar en el teléfono (HONOR Magic8 Lite, Android 16)

- Opciones de desarrollador → Depuración USB activada; aceptar el aviso al conectar.
- Instalar **sin streaming** (el cable corrompía el APK de 134 MB):
  `adb install --no-streaming -r --user 0 android/app/build/outputs/apk/debug/app-debug.apk`
- `--user 0` es necesario: el Honor tiene un segundo usuario (app gemela/espacio privado).
- Si `adb devices` dice *offline*: desbloquear el teléfono, `adb kill-server`, reconectar.
- No automatizar la pantalla del teléfono mientras el dueño lo usa; solo instalar.

## 4. Estado al pausar

**Terminado y probado (compila, pruebas unitarias OK, ya en GitHub antes de la pausa):**
inglés/español completo, base de 877 ejercicios en español, sugerencia de 3 máquinas por músculo,
sugerencias inteligentes (Ajustes), resumen anual (Progreso).

**Terminado pero SIN compilar todavía junto (commit "WIP" de la pausa):**
- Máquinas: botón "Cambiar esta" por tarjeta + "Ver anterior" (deshacer) + "Otras 3 opciones".
- Resumen anual bloqueado hasta 1 año desde la instalación ("Vuelve en X meses/días"); Ajustes: "Resumen anual" (encendido) y "Verlo antes del año" (apagado).
- `PLAN_ENTRENADOR_IA.md` con el escáner de máquinas como pendiente a futuro.

**A medias (lo hacían dos agentes cuando se pausó):**
1. **Coach registra lo que comiste desde el chat** — archivos nuevos `services/ai/CoachLogTool.kt`, `CoachLogService.kt`, `models/CoachLogProposal.kt`, `ui/coach/CoachLogCard.kt`, `CoachMessageText.kt`, cambios en `CoachScreen/CoachViewModel/ChatService/CoachTools/ChatRepository/ChatMessage`, `HomeScreen/HomeViewModel`, `AppNavigator`; textos en `strings_coach_log.xml`; pruebas `CoachLogServiceTest`, `CoachLogToolTest`, `CoachMessageTextTest`.
   Objetivo: el coach propone una tarjeta de confirmación (comida con kcal/P/C/G, agua, peso, entrenamiento) → "Registrar / Editar / Cancelar"; copiar cualquier mensaje (mantener pulsado o ícono); "Enviar a registro de comida" abre el registro por texto con el mensaje. Su último reporte: **la app compilaba**; las pruebas estaban corriendo (resultado desconocido).
2. **Pantalla de estado de Health Connect** — `ui/settings/HealthSyncStatusScreen.kt`, `services/health/HealthSyncStatus.kt`, `HealthImportRunner.kt`, cambios en `FudAIApp.kt`, `ImportedHealthWorkoutRepository.kt`, una fila en `SettingsScreen.kt`; textos en `strings_health_status.xml`.
   Objetivo: ver permisos, por tipo de dato (peso, grasa, entrenamientos…) última sincronización, cantidad, rango de fechas y **de qué app/reloj vienen** (Samsung Health, Mi Fitness, Zepp, Cubitt…), botones "Sincronizar ahora" e "Importar historial completo", últimos registros importados. Su último error conocido: `HealthSyncStatusScreen.kt:689` — *"@Composable invocations can only happen from the context of a @Composable function"* (puede que ya lo haya arreglado; verificar).

### Actualización 2026-10-26 (noche, PC de casa)

- Lo "a medias" y el WIP ya **compilan juntos**; pruebas OK salvo 3 fallos solo de Windows
  (los 2 de abajo + `FoodImageStoreRestoreTest.stagingRefusesANameThatIsASymbolicLink…`, que necesita
  permiso de enlaces simbólicos). APK instalado en el teléfono (hubo que desinstalar: otra clave debug).
- Nuevo: **objetivo libre con el coach** — 4.ª opción en el onboarding "Mi objetivo es más complejo",
  aviso en Inicio, botón en Ajustes → Objetivo, atajo en el chat; el coach entrevista (máx. 4 preguntas)
  y propone una tarjeta de plan (`propose_goal_plan`) que al aplicarse fija objetivo de peso, ritmo y
  objetivo de entrenamiento, y queda en el prompt del coach (`CoachGoalPlan`, `CoachGoalTool`).
- Nuevo: **botón ⓘ** con explicación en cada opción de objetivo (onboarding, Ajustes) y en
  "Tu objetivo" de Training.
- Pendiente de probar en el teléfono: coach-registro, Health Connect, máquinas, resumen anual y el
  objetivo libre. Siguiente: pasos del Galaxy Watch incompletos (app 5.720 vs reloj 11.499) y tutorial guiado.

### Actualización 2026-10-27 (madrugada, PC de casa)

Hecho, compilado e instalado (ver `PLAN_ENTRENADOR_IA.md` → Hecho): pasos por fuente, tutorial, pasos y
sueño desde el coach, sueño de relojes/anillos y recomendación de sueño, mapa de recuperación, alertas de
sobreesfuerzo y modo casa/gimnasio. Health Connect pide ahora también sueño y ritmo cardíaco (tipos v7):
la app vuelve a pedir permisos. Para las alertas con cardio del reloj, activar en Ajustes el acceso a
los entrenamientos de otras apps (ExerciseSession).

Después (madrugada del 27): fatiga cruzada en todos los músculos, pantalla Mi cuerpo (mapa de calor con
musclemap, MIT, copiado en `android/app/src/main/java/com/fittribe/musclemap`), "sigo adolorido", formato
de hora 12/24 h con rueda, IA de comida en español, historial de sueño y sincronizar todo (deslizar Inicio).

**APK de prueba para amigos:** carpeta de Drive "EntrenarIA" (dueño jotavnz@gmail.com):
`EntrenadorIA-beta-2026-10-27.apk` (de esta PC). La anterior `EntrenadorIA-debug.apk` (26-oct, PC de la
oficina) está firmada con otra clave: quien la tenga debe desinstalarla antes de instalar la nueva.
**Firma:** las APK de prueba se firman con `C:\Users\Job\.android\debug.keystore`. Copiar ese archivo a
la otra PC (misma ruta) para que las próximas versiones se instalen encima sin perder datos.

En esta PC: JDK 17 y Android SDK en `C:\Users\Job\dev` (`local.properties` apunta ahí). El repo está en
`C:\Users\Job\Documents\Proyectos OnGoing\Entrenador IA\fud-ai`.

## 5. Cómo se sincroniza el reloj (desde el 27-oct)

- **Hacia adelante es automático** una vez que Samsung Health comparte con Health Connect: pasos y sueño se leen
  al abrir Inicio, cada 5 min con Inicio abierto y al deslizar hacia abajo; entrenamientos, peso y grasa al abrir
  la app y cada 2 h (`TrainingAlertsWorker`).
- **Hacia atrás** (o si Samsung no comparte): importador de la descarga de datos personales
  (`services/samsung/SamsungHealthExport.kt` + `SamsungHealthImporter.kt`).
- La integración directa con el SDK de Samsung Health necesita aprobación de socio de Samsung: descartada por ahora.

## 6. Reloj Samsung (Galaxy Watch) → nuestra app

1. En el teléfono: **Samsung Health → menú (☰) → Ajustes → Health Connect** → permitir que Samsung Health escriba los datos (pasos, entrenamientos, ritmo cardíaco, sueño, peso…). Tener activada la sincronización de Samsung Health con la cuenta Samsung.
2. En nuestra app: **Ajustes → Health Connect** → activar y conceder permisos de lectura.
3. Abrir Ajustes → Health Connect → estado para ver que llegan datos "de Samsung Health" e importar el historial.
Para Xiaomi (Mi Fitness), Amazfit (Zepp) y Cubitt: activar Health Connect dentro de la app de cada reloj. Huawei no comparte con Health Connect (requiere la app Health Sync).

## 7. Seguir con Claude Code en la otra PC

Abre la carpeta del repo en Claude Code y escribe:

> Lee RETOMAR_EN_OTRA_PC.md y PLAN_ENTRENADOR_IA.md y continúa desde la sección 0.

Notas: la clave de Gemini que se compartió en un chat debe rotarse; nunca subir claves a Git (`local.properties` y `keystore.properties` están ignorados).
