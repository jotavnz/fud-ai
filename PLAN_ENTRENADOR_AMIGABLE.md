# Plan: Entrenador IA más amigable y personalizado

Fecha: 2026-10-27. Basado en la investigación de esta sesión (ciclo menstrual, contenido de ejercicios, UX para principiantes y coach).

## Estado (2026-10-28 tarde, APK `28e`)

Hecho la tarde del 28 (oficina):
- **Imágenes** para los 44 ejercicios sin foto (22 propios + 22 cardio/calentamiento), tomadas del mismo dataset.
- **Caducidad por alimento** (USDA FoodKeeper).
- **Aviso de calentamiento** antes de la sesión.
- **Resto de Fase 1**: ✓ por serie, rutinas de inicio, peso de la última vez y sugerencia sin historial, glosario, tamaños mayores en modo sencillo.
- **Fase 3**: estilo detectado, próxima sesión, semana de descarga, inicio suave, logros de constancia (semanas 2/4/6/8/12…).
- **Fase 2.4**: progresiones.
- **Fase 4**: se quitó el permiso de pulso sin uso; textos de declaración de sueño y ciclo en `PLAYSTORE.md`.

Sigue pendiente:
- Probar en el teléfono: factura, código de barras, Flo.
- Enviar la declaración de Health apps en Play.
- Fase 2.1: etiquetas por posición e impacto (las fases calentamiento/vuelta a la calma/movilidad ya están en `ExerciseRoles`).
- Fase 2.2: 60–80 ejercicios propios con fisioterapeuta.
- "Sesión planificada sin hacer" como aviso.

## Estado anterior (2026-10-28, APK `28a`)

Nuevo el 28: imágenes Gym visual para 272 ejercicios (ver RETOMAR_EN_OTRA_PC.md → sección 00), Despensa con tarjeta propia en Inicio, despensa por voz/Coach (`add_pantry_items`, pregunta ante duplicados), sueño en Mi cuerpo limitado a 5 noches.

Antes (APK `m`–`n`):

Hecho e instalado:
- Vista sencilla/experta (Ajustes → Entrenamiento): Fácil/Bien/Al límite, encabezados, guía, descanso automático al calificar, "Pedir rutina al Coach".
- Esfera de voz encima del + y pausada al hacer scroll; scroll más fluido.
- 22 ejercicios propios (hipopresivos, core profundo, postura, movilidad, mayores, calistenia) en inglés/español, sin imágenes aún.
- Tarjetas en Training: calentamiento sugerido y vuelta a la calma tras sesión dura.
- Coach: reglas de entrenador atento + herramientas `get_session_extras`, `get_pantry`, `get_cycle`.
- Despensa (+ de Inicio): nevera/congelador/alacena, código de barras, foto de factura con revisión, gastos, "¿Qué cocino?" y "¿Qué compro?".
- Ciclo (Ajustes → Entrenamiento, o + de Inicio si el perfil no es masculino): periodos, síntomas, fase estimada, importación desde Health Connect, consejos del día.

Pendiente:
- Probar en el teléfono: foto de factura real, escáner de código, importación de Flo vía Health Connect.
- ~~Ilustraciones para los 22 ejercicios nuevos~~ (hecho el 28 por la tarde, imágenes del dataset).
- Hecho el 28 (oficina): la biblioteca pasó al catálogo completo de la carpeta `exercises-dataset-main` (1.324 con foto y animación); ver RETOMAR_EN_OTRA_PC.md → sección 00.
- ~~Modo guiado~~ (botón "Entrenar paso a paso", APK `n`).
- ~~Lista de compras~~ (en Despensa; "Se acabó" la llena) y ~~avisos~~ (comida que vence mañana, "¿volvemos?" tras 7 días sin entrenar).
- ~~Caducidades por alimento (USDA FoodKeeper)~~ (hecho el 28).
- ~~Aviso de calentamiento antes de la sesión planificada~~ (hecho el 28).
- Declarar READ_MENSTRUATION en Google Play antes de publicar.

## Ya hecho (inicio)

- **Sentadilla búlgara**: el buscador y el coach ahora ignoran tildes, entienden "bulgarian/búlgara" y ya no crean un ejercicio "personalizado" sin imagen cuando existe uno casi igual en el catálogo. Probado en el teléfono (APK `f`).
- **Atasco al hacer scroll en Training**: cada fila de la lista creaba su propio almacén de fotos (acceso a disco en el hilo principal). Ahora usa el compartido. Compilado en APK `g`, **falta instalar y medir**.

## Fase 1 — Registro de entrenamientos para principiantes (impacto alto, esfuerzo bajo)

Lo que hoy confunde (en `ui/workouts/WorkoutDiaryScreen.kt`):
- "RPE" visible en cada serie sin explicación (solo existe para lectores de pantalla).
- Fila "Serie N" sin encabezados: peso / reps / RPE solo se distinguen por el placeholder, que desaparece al escribir.
- Reloj de arena y cronómetro: iconos sin texto; el cronómetro es para calcular calorías y se confunde con el descanso.
- El disquete significa "favorito" en un sitio y "guardar rutina" en otro.
- Menú + con 10–12 opciones; estado vacío sin sugerencias.
- Botones de 32–36 dp y textos de 9–13 sp (difícil para personas mayores).

Propuesta:
1. **Modo principiante** (activo por defecto si no hay historial, perfil sedentario o 60+): sin RPE; al terminar cada serie se pregunta **Fácil / Bien / Al límite** (internamente se guarda como reps en reserva → RPE).
2. Encabezados claros "Peso (kg) · Repeticiones" y un **✓ por serie** que inicia el descanso solo (reemplaza el reloj de arena).
3. Texto bajo cada icono; marcador y borrar a un menú "⋮"; el cronómetro se llama "Duración" y solo aparece en cardio o modo avanzado.
4. Precargar "la última vez"; sin historial: "elige un peso que puedas mover 12–15 veces con buena técnica".
5. Tooltips una sola vez la primera sesión (Serie, Repetición, Descanso) + glosario en el detalle del ejercicio.
6. Rutinas de inicio en el estado vacío ("Cuerpo completo 2×/semana").
7. Botones de 48 dp, texto mínimo 14 sp.
8. (Después) **Modo guiado "Entrenar ahora"**: un ejercicio a la vez, animación, botón grande "Hecho", cuenta atrás.

## Fase 2 — Contenido: calentamiento, vuelta a la calma, movilidad, postura, core, hipopresivos, mayores

Situación: el catálogo actual (free-exercise-db, dominio público) está pensado para gimnasio. **No tiene hipopresivos, equilibrio, programas para mayores ni progresiones**, y casi nada de postura (sin chin tuck, wall angel, psoas, bird dog, hollow). No existe en ningún idioma un dataset abierto de hipopresivos, Otago, calentamientos por grupo muscular o postura: esa parte hay que escribirla.

1. **Etiquetar lo que ya hay** (sin problema de licencia): fase (calentamiento / vuelta a la calma / movilidad), articulación, impacto, posición (de pie / sentado / suelo), apto para mayores. Con los 123 estiramientos, 13 de foam roller y los de movilidad ya salen calentamientos y vueltas a la calma por grupo muscular.
2. **Escribir 60–80 ejercicios propios**, idealmente revisados por un fisioterapeuta: bird dog, hollow, chin tuck, wall angel, psoas, gato-camello, rotación torácica, CARs, flexión contra la pared, sentarse-levantarse de silla, marcha en tándem, apoyo monopodal, un set tipo Otago con redacción propia, hipopresivos base (respiración, apnea, posturas). Ilustraciones con el estilo propio de la app (`shared/workout-vectors`). Para mayores, basarse en CDC STEADI y la guía del NIA (obras públicas de EE. UU.).
3. **Calistenia**: tomar los textos (MIT, con español) de `exercises-dataset` para variantes (flexión de rodillas, archer, dominadas asistidas) — **sin sus imágenes ni videos** (son de Gym visual, no reutilizables).
4. **Progresiones** (`progressions.json` propio: de flexión en pared → rodillas → completa, etc.) para quien viene de calistenia o empieza.

Licencias — **no usar**: textos propios, traducciones e imágenes de `openGym` (AGPL / Gym visual), código de wger o Liftosaur (AGPL), videos Otago de UNC, texto de la OMS (no comercial). wger solo las entradas CC0/CC-BY, con atribución.

## Fase 3 — El coach como acompañante (no un entrenador genérico)

Reglas propias en Kotlin sobre lo que ya existe (`TrainingInsights`, recuperación muscular, fatiga cruzada). Cada una: disparador → mensaje/sugerencia/notificación.

| Regla | Cuándo | Qué hace |
|---|---|---|
| Estilo detectado | 6+ sesiones en 28 días sin rutina configurada | "Parece que entrenas tipo Empuje/Tirón/Piernas, en rango de hipertrofia. ¿Lo configuro así?" |
| Incoherencias | mismo grupo duro <48 h, mucho más empuje que tirón, piernas olvidadas 14 días, principiante con >10 series de un músculo | Explica por qué y ofrece alternativa |
| Próxima sesión | al terminar una sesión | Propone grupos descansados y 3–5 ejercicios |
| **Calentamiento del día** (RAMP) | antes de la sesión planificada o al añadir el primer ejercicio (sirve para planear camino al gym) | 3–5 min de cardio suave + movilidad/activación según músculos del día + 1–2 series de aproximación. Más largo y sin saltos para mayores o sobrepeso |
| **Después de una sesión dura** | volumen alto personal, muchas series "Al límite", o piernas pesadas | Piernas a tope: caminar suave/bici sin parar de golpe, respiración, estiramiento suave, proteína e hidratación. Tren superior: versión corta. Honesto: ayuda a bajar pulsaciones y a sentirse mejor, no está probado que evite agujetas o calambres |
| Fatiga | volumen semanal de un músculo >1,5× lo habitual, menos reps con el mismo peso, el mismo peso se siente más duro | Propone sesión ligera o semana de descarga (como conversación, no alarma) |
| Arranque suave | primeras 4 semanas, sedentario o sobrepeso | 2 días/semana, 1–2 series de 10–15, máquinas, estabilidad articular y core; subir una sola cosa a la vez |
| Constancia | >7 días sin entrenar, sesión planificada sin hacer | Mensaje corto sin culpa, versión de 15 min; celebrar semanas 2, 4 y 6 (el 63 % de socios nuevos abandona antes del tercer mes) |
| Mayores / sobrepeso | según perfil | Equilibrio + fuerza 3+ días/semana, bajo impacto, alternativas sentadas |
| Solo cardio | historial solo cardio | El calentamiento/movilidad puede ser su entrenamiento; sugerir fuerza básica poco a poco |

## Fase 4 — Ciclo menstrual

- **"Sincronizar con Flo" en realidad significa leer Health Connect.** Flo no tiene API pública; su ayuda dice que se conecta con Health Connect en Android, pero no aclara si escribe la menstruación. **Hay que probarlo con Flo instalado** antes de prometerlo. En iOS, Flo no envía la menstruación a Apple Health.
- Implementación: permiso `READ_MENSTRUATION` (opcional temperatura basal y test de ovulación), **apagado por defecto**, la usuaria lo activa. Siempre con registro manual de respaldo y **registro de síntomas dentro de la app** (Health Connect no guarda síntomas): cólicos, fatiga, sangrado abundante, ánimo, sueño, dolor de cabeza.
- Lo que dice la evidencia: el efecto de la fase sobre el rendimiento es pequeño o nulo; **lo que importa son los síntomas del día**. El coach ajusta por síntomas (bajar intensidad, bajo impacto, movilidad, descanso si hay mucho dolor) y usa la fase solo como contexto. Con anticonceptivos hormonales no hay fases naturales: solo síntomas. Perimenopausia/menopausia: fuerza e impacto progresivos.
- Derivar al médico ante dolor incapacitante, sangrado muy abundante, ausencia de regla o sangrado posmenopáusico. Hipopresivos no se presentan como tratamiento; ante incontinencia, prolapso o posparto → fisioterapia de suelo pélvico.
- Requisitos: declarar el permiso en Google Play (formulario de Health apps), explicarlo en la política de privacidad, no enviar fechas exactas al modelo de IA, opción de borrar todo. Código reutilizable: **Mensinator** (MIT, Kotlin). drip y Periodical son GPL: solo ideas.

### Repos de ciclo que enviaste (revisados)

| Repo | Licencia | Uso |
|---|---|---|
| Menstrudel (Flutter, con app Wear OS) | GPL-3.0 | **Solo ideas** (lista de síntomas, modelo de datos, referencia de reloj Wear OS). No copiar código. |
| wellness-cycle-coach (TypeScript, MCP) | MIT | Diseño de herramientas del coach: estimar fase, predecir periodo, guía por fase, modo ciclos irregulares/SOP con confianza baja. Portable a Kotlin con atribución. |
| sportwatch-how-to-monitor-periods | MIT | Solo relojes Huawei (HarmonyOS Lite). No sirve para Wear OS ni Apple Watch. |
| Mensinator (Kotlin) | MIT | Código de predicción reutilizable directamente. |

## Fase 5 — Despensa / Nevera, compras y gastos

Idea: saber qué hay en casa (nevera, congelador, despensa), que el coach sugiera comidas con eso y con las calorías/macros que faltan (también de viaje, donde los ingredientes se llaman distinto), registrar lo que se gasta, cargar la **foto de la factura** para reabastecer, y que el coach resuma y diga qué comprar en el mercado semanal/quincenal/mensual.

- **Repos**: grocy es **MIT** (ideas: varios códigos de barras por producto, unidades de compra vs consumo, ubicaciones, fecha de caducidad y tras abrir, stock mínimo → lista de compra, historial de precios por tienda, recetas que consumen stock, plan de comidas). whats-in-the-fridge (MIT): semáforo de frescura, mover a congelador recalcula caducidad, consumido → lista de compra, y datos **USDA FoodKeeper (CC0, 658 alimentos)** de vida útil. JoannaKang no tiene licencia (solo ideas). El CSV de recetas de Samridhi es de origen desconocido: no usar.
- **Ya existe en la app**: escáner de códigos (ML Kit), Open Food Facts / FoodData Central, análisis de fotos y etiquetas con IA, y el patrón del coach "propone → tarjeta → confirmar".
- **Modelo**: `PantryItem` (ubicación, cantidad, unidad, caducidad, abierto, código), `GroceryPurchase` (tienda, fecha, moneda, total, líneas), `ShoppingListItem`, `PantrySettings` (frecuencia de compra, presupuesto, país).
- **Coach**: `get_pantry`, `suggest_meals_from_pantry` (prioriza lo que caduca, usa macros restantes, traduce ingredientes por país), `propose_shopping_list`, `propose_pantry_update`, `get_grocery_spending`, descontar del stock al registrar comidas.
- **Factura**: foto → texto (ML Kit en el teléfono) → IA devuelve tienda, fecha y líneas → **pantalla de revisión** → se guarda la compra y se reabastece con caducidad estimada.
- **Nombres por país**: taxonomías de ingredientes de Open Food Facts (sinónimos por idioma; ODbL, con atribución).
- **MVP**: (1) despensa con ubicaciones + código de barras + caducidad + sugerencias del coach; (2) factura y gastos; (3) lista de compra y descuento automático; (4) modo viaje, avisos de caducidad, copia de seguridad.
- **Riesgos**: OCR de tickets térmicos y abreviaturas (por eso la revisión manual), coste de IA por ticket, caducidades orientativas.

## Orden recomendado

1. ~~Medir el scroll de Training~~ y ~~Fase 1 base~~ (APK `j`: modo sencillo Fácil/Bien/Al límite, encabezados de columnas, "Series", "Duración", guía de una vez, interruptor en Ajustes, esfera de voz encima del + y pausada al hacer scroll).
2. Resto de Fase 1: ✓ por serie que inicia el descanso, rutinas de inicio, sugerencia de peso con historial, modo guiado.
3. Fase 2.1 (etiquetar contenido) + Fase 3 calentamiento y vuelta a la calma — van juntos.
4. Resto de Fase 3 (estilo, incoherencias, fatiga, constancia).
5. Fase 2.2 (ejercicios propios, hipopresivos, mayores) con revisión de fisioterapeuta.
6. Fase 4 (ciclo) tras probar Flo + Health Connect y preparar la declaración de Play.

## Tanda del 29 (pedidos del usuario, 2026-10-28 noche)

Estado: [ ] pendiente · [x] hecho

1. [ ] Reloj/Samsung Health: revisar que todo lo del reloj llegue (los pasos del reloj siguen más altos que la app).
2. [ ] Coach: arreglar el hover/foco del logo de la IA (se ve un cuadrado alrededor del círculo al tocar).
3. [ ] "Cuéntale al coach qué quieres lograr": solo mostrarlo si no se ha pulsado nunca.
4. [ ] Recordatorio "Esta noche duerme…": botón X; vuelve la tarde-noche del día siguiente.
5. [ ] Training → biblioteca: al volver de un ejercicio, conservar el scroll (no volver al inicio).
6. [ ] Detalle del ejercicio: botón "Añadir a hoy" (ir de compras por ejercicios).
7. [ ] Aviso de sobreexigencia (demasiados ejercicios/series/volumen para 1–2 h), descartable.
8. [ ] "Deshacer" (snackbar unos segundos) en todo lo que se borra: comidas, entrenamientos, despensa (el ✓ borraba sin avisar).
9. [ ] Lista de compras: acceso directo propio (junto a Despensa).
10. [ ] Despensa como "otra app": stock que baja al registrar comidas (porciones restantes), el coach sugiere según cantidades y avisa si falta algo ("no importa" / reformular).
11. [ ] Comida de la calle / ya preparada: preguntar si se cocinó en casa; si no, no descontar de la despensa.
12. [ ] FUTURO: grupo familiar (pareja/roomies): despensa compartida, dietas y restricciones de cada uno, recetas para ambos, descuento en la despensa del grupo. Necesita backend (ver 20). Revisar si grocy / whats-in-the-fridge traen algo reutilizable.
13. [ ] Mensajes sugeridos del coach: X para quitarlos; vuelven a las 24 h; la 2.ª vez se apagan y se reactivan en Ajustes.
14. [ ] Training → Recuperación muscular: solo 2 (las más recientes) + "Ver más" a otra pantalla.
15. [ ] "¿Qué quieres entrenar hoy?": opción "No sé" que lleva al coach.
16. [ ] Diario de entreno: swipe = "Terminar" (no guardar); si faltan kg/reps, popup rápido para llenarlos. Borrar con deshacer.
17. [ ] Tarjetas de ejercicio en el diario: preview limpia sin el texto de crédito (crédito solo en el detalle).
18. [ ] Training: "Pedir rutina al coach" lo primero. Orden de pestañas por defecto Inicio, Progreso, Training, Coach, Ajustes, y configurable en Ajustes.
19. [ ] Gastos de despensa en Bs o $ con la tasa del día (y decir qué tasa se usó).
20. [ ] Datos en la nube (Supabase) para Play Store y grupo familiar: decidir.

### Investigación grupo familiar / despensa (2026-10-28)
- grocy (MIT): consumir receta (rechaza si falta stock), "fulfillment" por receta (alcanza o no, faltantes, kcal), due score (+1 por ingrediente que vence pronto). Portable con atribución. Sin hogares ni dietas por miembro.
- Pantrie (MIT, untraceablez/pantrie): hogares con roles Admin/Editor/Viewer y alérgenos por ingrediente. Referencia de modelo.
- EverShelf (MIT): recetas con IA, lista de compras que descuenta lo que hay, cantidades "X / Y".
- KitchenOwl, Mealie, Tandoor: AGPL, solo ideas (grupo → hogar, "on hand", listas compartidas).
- Modelo propuesto: Household(id, name, inviteCode); Member(householdId, role, dietTags, allergens, kcalTarget, miembros locales sin cuenta); StockTransaction(delta, mealLogId, memberId) como registro solo de altas (facilita deshacer y sincronizar). Porciones = mín. de floor(stock / cantidad por porción). IA recibe unión de restricciones (alergias duras, preferencias suaves).
- Todos necesitan servidor: offline-first local + sincronizar con Supabase después.
