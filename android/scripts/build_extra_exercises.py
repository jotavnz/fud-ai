# Builds fud-ai's own extra exercises (MIT, written for this app) in English and Spanish.
import json, sys

E = []
def ex(id, name, es, cat, equip, level, force, mech, prim, sec, steps):
    E.append(dict(id=id, name=name, es=es, category=cat, equipment=equip, level=level, force=force,
                  mechanic=mech, primaryMuscles=prim, secondaryMuscles=sec, steps=steps))

# --- Dynamic warm-up ---
ex("Fud_Leg_Swings", "Leg Swings (Front to Back)", "Balanceo de piernas (adelante y atrás)", "stretching", "body only", "beginner", "static", None,
   ["hamstrings"], ["glutes", "quadriceps"],
   [("Stand side-on to a wall or rack and hold it with one hand. Stand tall on one leg with a soft knee.",
     "Ponte de lado junto a una pared o rack y sujétate con una mano. Quédate erguido sobre una pierna con la rodilla un poco flexionada."),
    ("Swing the free leg forward and back like a pendulum, starting small and getting a bit bigger each swing.",
     "Balancea la pierna libre hacia adelante y hacia atrás como un péndulo, empezando con poco recorrido y aumentándolo en cada balanceo."),
    ("Keep your trunk still and do 10–15 swings, then switch legs.",
     "Mantén el tronco quieto y haz 10–15 balanceos; luego cambia de pierna.")])
ex("Fud_Marching_In_Place", "Marching in Place", "Marcha en el sitio", "cardio", "body only", "beginner", None, None,
   ["quadriceps"], ["calves", "glutes"],
   [("Stand tall with feet hip-width apart. Hold a chair or wall if you need balance.",
     "Ponte de pie, pies a la anchura de la cadera. Apóyate en una silla o pared si necesitas equilibrio."),
    ("Lift one knee toward hip height, lower it and lift the other, swinging your arms naturally.",
     "Sube una rodilla hacia la altura de la cadera, bájala y sube la otra, moviendo los brazos con naturalidad."),
    ("March for 1–3 minutes at a pace that raises your breathing but still lets you talk.",
     "Marcha 1–3 minutos a un ritmo que acelere tu respiración pero te deje hablar.")])
ex("Fud_Jumping_Jacks", "Jumping Jacks", "Jumping jacks (saltos de tijera)", "plyometrics", "body only", "beginner", None, None,
   ["quadriceps"], ["calves", "shoulders"],
   [("Stand with feet together and arms by your sides.",
     "Ponte de pie con los pies juntos y los brazos a los lados."),
    ("Jump the feet apart while raising the arms overhead, then jump back to the start.",
     "Salta abriendo los pies mientras subes los brazos por encima de la cabeza y vuelve saltando a la posición inicial."),
    ("Land softly. For a low-impact version, step one foot out at a time instead of jumping.",
     "Aterriza suave. Para una versión sin impacto, abre un pie cada vez en lugar de saltar.")])

# --- Mobility and posture ---
ex("Fud_Cat_Cow", "Cat-Cow", "Gato-camello", "stretching", "body only", "beginner", None, None,
   ["lower back"], ["middle back", "abdominals"],
   [("Start on hands and knees, hands under shoulders and knees under hips.",
     "Empieza a cuatro apoyos: manos bajo los hombros y rodillas bajo la cadera."),
    ("Breathe in as you let the belly drop and lift the chest and tailbone (cow).",
     "Inhala mientras dejas caer el abdomen y elevas el pecho y el coxis (camello)."),
    ("Breathe out as you round the spine up toward the ceiling and tuck chin and tailbone (cat). Repeat slowly 8–10 times.",
     "Exhala mientras redondeas la espalda hacia el techo y metes barbilla y coxis (gato). Repite despacio 8–10 veces.")])
ex("Fud_Open_Book_Rotation", "Open Book Thoracic Rotation", "Rotación torácica en libro abierto", "stretching", "body only", "beginner", None, None,
   ["middle back"], ["chest", "shoulders"],
   [("Lie on your side with hips and knees bent to 90 degrees and both arms straight in front of you.",
     "Túmbate de lado con caderas y rodillas a 90 grados y ambos brazos estirados al frente."),
    ("Keeping the knees together, open the top arm in an arc and rotate your upper back until the arm points to the other side.",
     "Sin separar las rodillas, abre el brazo de arriba en arco y gira la parte alta de la espalda hasta que el brazo apunte al otro lado."),
    ("Follow the hand with your eyes, breathe out at the end, return and repeat 8 times per side.",
     "Sigue la mano con la mirada, exhala al final, vuelve y repite 8 veces por lado.")])
ex("Fud_Chin_Tuck", "Chin Tuck", "Retracción de barbilla (chin tuck)", "stretching", "body only", "beginner", None, None,
   ["neck"], ["traps"],
   [("Sit or stand tall with shoulders relaxed and eyes looking straight ahead.",
     "Siéntate o ponte de pie erguido, con los hombros relajados y la mirada al frente."),
    ("Glide your head straight back, making a double chin, without tilting it up or down.",
     "Lleva la cabeza hacia atrás en línea recta, haciendo papada, sin inclinarla arriba ni abajo."),
    ("Hold 3–5 seconds and release. Do 10 reps; useful several times a day if you work at a desk.",
     "Mantén 3–5 segundos y suelta. Haz 10 repeticiones; útil varias veces al día si trabajas sentado.")])
ex("Fud_Wall_Angel", "Wall Angel", "Ángel en la pared", "stretching", "body only", "beginner", None, None,
   ["shoulders"], ["middle back", "traps"],
   [("Stand with your back, head and buttocks against a wall, feet a little in front of it.",
     "Apoya espalda, cabeza y glúteos contra una pared, con los pies un poco por delante."),
    ("Raise your arms in a goalpost shape with elbows and the backs of the hands touching the wall if you can.",
     "Sube los brazos en forma de portería, con codos y dorso de las manos tocando la pared si puedes."),
    ("Slide the arms up and down slowly, keeping your lower back gently against the wall. Do 8–12 reps.",
     "Desliza los brazos arriba y abajo despacio, manteniendo la zona lumbar suavemente contra la pared. Haz 8–12 repeticiones.")])

# --- Deep core ---
ex("Fud_Bird_Dog", "Bird Dog", "Bird dog (cuadrupedia alterna)", "strength", "body only", "beginner", "static", "compound",
   ["abdominals"], ["lower back", "glutes", "shoulders"],
   [("Start on hands and knees with a neutral spine.",
     "Empieza a cuatro apoyos con la espalda neutra."),
    ("Brace your belly lightly and reach one arm forward and the opposite leg back until both are level with your body.",
     "Activa suavemente el abdomen y estira un brazo al frente y la pierna contraria hacia atrás hasta que queden alineados con el cuerpo."),
    ("Hold 2–3 seconds without rotating the hips, return and switch sides. Do 8–10 per side.",
     "Mantén 2–3 segundos sin girar la cadera, vuelve y cambia de lado. Haz 8–10 por lado.")])
ex("Fud_Hollow_Body_Hold", "Hollow Body Hold", "Hollow body (posición hueca)", "strength", "body only", "intermediate", "static", "isolation",
   ["abdominals"], [],
   [("Lie on your back, press the lower back into the floor and lift head and shoulders slightly.",
     "Túmbate boca arriba, pega la zona lumbar al suelo y eleva un poco cabeza y hombros."),
    ("Lift the legs and reach the arms overhead, keeping the lower back down. Bend the knees to make it easier.",
     "Eleva las piernas y lleva los brazos por encima de la cabeza sin despegar la zona lumbar. Flexiona las rodillas para hacerlo más fácil."),
    ("Hold 15–30 seconds while breathing; stop if your lower back lifts.",
     "Mantén 15–30 segundos respirando; para si la zona lumbar se despega.")])
ex("Fud_Diaphragmatic_Breathing", "360° Diaphragmatic Breathing", "Respiración diafragmática 360°", "stretching", "body only", "beginner", None, None,
   ["abdominals"], [],
   [("Lie on your back with knees bent, one hand on the chest and one on the belly.",
     "Túmbate boca arriba con las rodillas flexionadas, una mano en el pecho y otra en el abdomen."),
    ("Breathe in through the nose so the belly, sides and lower back expand while the chest stays quiet.",
     "Inhala por la nariz para que se expandan abdomen, costados y zona lumbar mientras el pecho se mueve poco."),
    ("Breathe out slowly through the mouth, letting the ribs come down. Do 5–10 slow breaths; great to start or finish a session.",
     "Exhala despacio por la boca dejando bajar las costillas. Haz 5–10 respiraciones lentas; ideal para empezar o terminar una sesión.")])
ex("Fud_Pelvic_Floor_Activation", "Pelvic Floor Activation", "Activación del suelo pélvico", "strength", "body only", "beginner", "static", "isolation",
   ["abdominals"], [],
   [("Lie on your back with knees bent or sit tall, breathing normally.",
     "Túmbate boca arriba con las rodillas flexionadas o siéntate erguido, respirando con normalidad."),
    ("Gently squeeze and lift the pelvic floor, as if stopping the flow of urine, without holding your breath or squeezing the buttocks.",
     "Contrae y eleva suavemente el suelo pélvico, como si cortaras el chorro de orina, sin aguantar la respiración ni apretar glúteos."),
    ("Hold 3–5 seconds and fully relax for the same time. Do 8–10 reps. With leaks, pain, pregnancy or after birth, see a pelvic floor physiotherapist.",
     "Mantén 3–5 segundos y relaja por completo el mismo tiempo. Haz 8–10 repeticiones. Si hay escapes, dolor, embarazo o posparto, consulta a un fisioterapeuta de suelo pélvico.")])
ex("Fud_Side_Plank_Knees", "Side Plank on Knees", "Plancha lateral de rodillas", "strength", "body only", "beginner", "static", "isolation",
   ["abdominals"], ["glutes", "shoulders"],
   [("Lie on your side with the elbow under the shoulder and knees bent behind you.",
     "Túmbate de lado con el codo bajo el hombro y las rodillas flexionadas hacia atrás."),
    ("Lift your hips so the body forms a straight line from head to knees.",
     "Eleva la cadera hasta formar una línea recta de la cabeza a las rodillas."),
    ("Hold 15–30 seconds breathing normally, then switch sides. Straighten the legs when it gets easy.",
     "Mantén 15–30 segundos respirando con normalidad y cambia de lado. Estira las piernas cuando te resulte fácil.")])

# --- Hypopressives ---
HYPO_NOTE = ("Avoid during pregnancy, with high blood pressure, heart or lung problems, or hernias; learn it with a qualified instructor or pelvic floor physiotherapist.",
             "Evítalo durante el embarazo, con hipertensión, problemas cardíacos o pulmonares, o hernias; apréndelo con un instructor cualificado o fisioterapeuta de suelo pélvico.")
ex("Fud_Hypopressive_Standing", "Hypopressive Breathing (Standing)", "Hipopresivo de pie", "stretching", "body only", "beginner", None, None,
   ["abdominals"], [],
   [("Stand with feet hip-width apart, knees soft, weight slightly forward and arms out in front at waist height.",
     "De pie, pies a la anchura de la cadera, rodillas algo flexionadas, peso un poco adelante y brazos al frente a la altura de la cintura."),
    ("Take 2–3 calm breaths. Then breathe all the air out slowly.",
     "Haz 2–3 respiraciones tranquilas. Después saca todo el aire despacio."),
    ("With empty lungs, hold your breath and open the ribs as if breathing in without letting air in; the belly draws in. Hold 5–10 seconds.",
     "Con los pulmones vacíos, mantén la apnea y abre las costillas como si inhalaras sin dejar entrar aire; el abdomen se mete. Mantén 5–10 segundos."),
    ("Release, breathe in gently and rest for 3 breaths. Repeat 3–5 times. " + HYPO_NOTE[0],
     "Suelta, inhala suave y descansa 3 respiraciones. Repite 3–5 veces. " + HYPO_NOTE[1])])
ex("Fud_Hypopressive_Kneeling", "Hypopressive Breathing (Kneeling)", "Hipopresivo de rodillas", "stretching", "body only", "beginner", None, None,
   ["abdominals"], [],
   [("Kneel tall with knees hip-width apart, lean slightly forward and bring the hands in front at chest height, wrists flexed.",
     "De rodillas, erguido, con las rodillas a la anchura de la cadera, inclínate un poco adelante y lleva las manos al frente a la altura del pecho con las muñecas flexionadas."),
    ("Take 2–3 calm breaths, then breathe all the air out slowly.",
     "Haz 2–3 respiraciones tranquilas y luego saca todo el aire despacio."),
    ("Hold your breath with empty lungs and open the ribs without letting air in, drawing the belly in. Hold 5–10 seconds.",
     "Mantén la apnea con los pulmones vacíos y abre las costillas sin dejar entrar aire, metiendo el abdomen. Mantén 5–10 segundos."),
    ("Release and recover for 3 breaths. Repeat 3–5 times. " + HYPO_NOTE[0],
     "Suelta y recupera 3 respiraciones. Repite 3–5 veces. " + HYPO_NOTE[1])])
ex("Fud_Hypopressive_Lying", "Hypopressive Breathing (Lying)", "Hipopresivo tumbado", "stretching", "body only", "beginner", None, None,
   ["abdominals"], [],
   [("Lie on your back with knees bent, feet flat, arms by your sides with palms down.",
     "Túmbate boca arriba con las rodillas flexionadas, pies apoyados y brazos a los lados con las palmas hacia abajo."),
    ("Take 2–3 calm breaths, then breathe all the air out slowly.",
     "Haz 2–3 respiraciones tranquilas y luego saca todo el aire despacio."),
    ("Hold your breath with empty lungs and expand the ribs without letting air in. Hold 5–10 seconds. This is the easiest position to learn.",
     "Mantén la apnea con los pulmones vacíos y expande las costillas sin dejar entrar aire. Mantén 5–10 segundos. Es la posición más fácil para aprender."),
    ("Release and recover for 3 breaths. Repeat 3–5 times. " + HYPO_NOTE[0],
     "Suelta y recupera 3 respiraciones. Repite 3–5 veces. " + HYPO_NOTE[1])])

# --- Seniors, balance, beginners ---
ex("Fud_Sit_To_Stand", "Sit-to-Stand from a Chair", "Sentarse y levantarse de la silla", "strength", "body only", "beginner", "push", "compound",
   ["quadriceps"], ["glutes", "hamstrings"],
   [("Sit near the front of a sturdy chair with feet flat and hip-width apart.",
     "Siéntate en el borde de una silla firme con los pies apoyados a la anchura de la cadera."),
    ("Lean slightly forward and stand up by pushing through your feet, using your hands only if needed.",
     "Inclínate un poco adelante y ponte de pie empujando con los pies, usando las manos solo si lo necesitas."),
    ("Sit back down slowly and with control. Do 8–12 reps; it builds the strength used every day.",
     "Vuelve a sentarte despacio y con control. Haz 8–12 repeticiones; fortalece lo que usas cada día.")])
ex("Fud_Wall_Push_Up", "Wall Push-Up", "Flexión contra la pared", "strength", "body only", "beginner", "push", "compound",
   ["chest"], ["triceps", "shoulders"],
   [("Stand an arm's length from a wall and place your hands on it at shoulder height and width.",
     "Ponte a un brazo de distancia de una pared y apoya las manos a la altura y anchura de los hombros."),
    ("Keeping the body straight, bend the elbows and bring your chest toward the wall.",
     "Con el cuerpo recto, flexiona los codos y acerca el pecho a la pared."),
    ("Push back to the start. Do 10–15 reps; step further back to make it harder, then progress to a bench and the floor.",
     "Empuja para volver. Haz 10–15 repeticiones; aléjate más para hacerlo más difícil y luego progresa a un banco y al suelo.")])
ex("Fud_Knee_Push_Up", "Knee Push-Up", "Flexión de rodillas", "strength", "body only", "beginner", "push", "compound",
   ["chest"], ["triceps", "shoulders", "abdominals"],
   [("Start in a push-up position with your knees on the floor and a straight line from head to knees.",
     "Empieza en posición de flexión con las rodillas apoyadas y una línea recta de la cabeza a las rodillas."),
    ("Lower your chest toward the floor with elbows at about 45 degrees from the body.",
     "Baja el pecho hacia el suelo con los codos a unos 45 grados del cuerpo."),
    ("Push back up. When 15 reps feel easy, move to full push-ups.",
     "Empuja para subir. Cuando 15 repeticiones te resulten fáciles, pasa a flexiones completas.")])
ex("Fud_Single_Leg_Stand", "Single-Leg Balance", "Equilibrio sobre una pierna", "stretching", "body only", "beginner", "static", None,
   ["calves"], ["glutes", "abdominals"],
   [("Stand next to a counter or chair and hold it lightly.",
     "Ponte junto a una encimera o silla y sujétate ligeramente."),
    ("Lift one foot off the floor and balance on the other leg, standing tall.",
     "Levanta un pie del suelo y mantén el equilibrio sobre la otra pierna, erguido."),
    ("Hold up to 30 seconds, then switch. Use fewer fingers, then no hands, as balance improves.",
     "Mantén hasta 30 segundos y cambia. Usa menos dedos y luego sin manos a medida que mejore el equilibrio.")])
ex("Fud_Tandem_Walk", "Heel-to-Toe Walk", "Marcha en tándem (talón-punta)", "stretching", "body only", "beginner", None, None,
   ["calves"], ["abdominals"],
   [("Stand tall near a wall for support if needed.",
     "Ponte erguido junto a una pared para apoyarte si lo necesitas."),
    ("Walk in a straight line placing the heel of one foot right in front of the toes of the other.",
     "Camina en línea recta colocando el talón de un pie justo delante de la punta del otro."),
    ("Look ahead, not at your feet. Take 10–20 steps, turn and come back.",
     "Mira al frente, no a los pies. Da 10–20 pasos, gira y vuelve.")])
ex("Fud_Supported_Heel_Raises", "Supported Heel Raises", "Elevación de talones con apoyo", "strength", "body only", "beginner", "push", "isolation",
   ["calves"], [],
   [("Stand behind a chair or counter, holding it lightly, feet hip-width apart.",
     "Ponte detrás de una silla o encimera, sujetándote ligeramente, con los pies a la anchura de la cadera."),
    ("Rise up onto the balls of your feet as high as you can.",
     "Sube sobre las puntas de los pies lo más alto que puedas."),
    ("Lower slowly. Do 10–15 reps; good for ankles, balance and walking.",
     "Baja despacio. Haz 10–15 repeticiones; bueno para tobillos, equilibrio y para caminar.")])

# --- Calisthenics ---
ex("Fud_Dead_Hang", "Dead Hang", "Colgarse de la barra (dead hang)", "strength", "other", "beginner", "pull", "isolation",
   ["forearms"], ["lats", "shoulders"],
   [("Grip a pull-up bar with hands shoulder-width apart, using a box to reach it if needed.",
     "Agarra una barra de dominadas con las manos a la anchura de los hombros, usando un cajón para alcanzarla si hace falta."),
    ("Hang with arms straight and shoulders active (slightly pulled down), feet off the floor or lightly touching for help.",
     "Cuélgate con los brazos estirados y los hombros activos (un poco hacia abajo), con los pies en el aire o tocando ligeramente para ayudarte."),
    ("Hold 10–30 seconds. It is the first step toward pull-ups.",
     "Mantén 10–30 segundos. Es el primer paso hacia las dominadas.")])

if __name__ == "__main__":
    asset_dir = sys.argv[1]
    records, es = [], {}
    for e in E:
        records.append({
            "id": e["id"], "name": e["name"], "force": e["force"], "level": e["level"], "mechanic": e["mechanic"],
            "equipment": e["equipment"], "primaryMuscles": e["primaryMuscles"], "secondaryMuscles": e["secondaryMuscles"],
            "instructions": [s[0] for s in e["steps"]], "category": e["category"], "images": []
        })
        es[e["id"]] = {"name": e["es"], "instructions": [s[1] for s in e["steps"]]}
    json.dump(records, open(asset_dir + "/exercises_extra.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    p = asset_dir + "/exercises_es.json"
    table = json.load(open(p, encoding="utf-8"))
    table.update(es)
    open(p, "w", encoding="utf-8", newline="").write(json.dumps(table, ensure_ascii=False, separators=(",", ":")))
    print(len(records), "extra exercises")
