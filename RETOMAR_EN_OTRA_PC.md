# Retomar el proyecto en otra PC

Pausado el 2026-10-26. Todo está en GitHub: **jotavnz/fud-ai**, rama **`feature/entrenador-ia-es`**.
Lee también `PLAN_ENTRENADOR_IA.md` (plan por fases y pendientes).

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

## 5. Próximos pasos para cerrar esta tanda

1. `.\gradlew.bat compileDebugKotlin` y arreglar errores (empezar por `HealthSyncStatusScreen.kt:689`).
2. Pruebas: `.\gradlew.bat testDebugUnitTest -PworkoutVectors=none`
   (2 pruebas fallan solo en Windows y no tienen que ver: `ConsistentDataStoreTest.reReadsAfterAWriteDoNotReEmitEqualValues`, `StorageMaintenanceTest.compressionOnly…`).
3. Revisar que cada texto nuevo exista en `res/values` y `res/values-es`.
4. `assembleDebug`, instalar y probar en el teléfono:
   - Coach: escribir lo que comiste → tarjeta de confirmación → aparece en Inicio. Copiar mensaje. "Enviar a registro de comida".
   - Ajustes → Health Connect → pantalla de estado; "Importar historial".
   - Training → un músculo → "Cambiar esta", "Ver anterior", "Otras 3 opciones".
   - Progreso → tarjeta bloqueada "Vuelve en…"; Ajustes → "Verlo antes del año" la desbloquea.
5. Commit y push a `feature/entrenador-ia-es`.

## 6. Reloj Samsung (Galaxy Watch) → nuestra app

1. En el teléfono: **Samsung Health → menú (☰) → Ajustes → Health Connect** → permitir que Samsung Health escriba los datos (pasos, entrenamientos, ritmo cardíaco, sueño, peso…). Tener activada la sincronización de Samsung Health con la cuenta Samsung.
2. En nuestra app: **Ajustes → Health Connect** → activar y conceder permisos de lectura.
3. Abrir la pantalla de estado de Health (cuando esté terminada) para ver que llegan datos "de Samsung Health" e importar el historial.
Para Xiaomi (Mi Fitness), Amazfit (Zepp) y Cubitt: activar Health Connect dentro de la app de cada reloj. Huawei no comparte con Health Connect (requiere la app Health Sync).

## 7. Seguir con Claude Code en la otra PC

Abre la carpeta `C:\EntrenadorIA\fud-ai` en Claude Code y escribe:

> Lee RETOMAR_EN_OTRA_PC.md y PLAN_ENTRENADOR_IA.md y continúa desde la sección 5.

Notas: la clave de Gemini que se compartió en un chat debe rotarse; nunca subir claves a Git (`local.properties` y `keystore.properties` están ignorados).
