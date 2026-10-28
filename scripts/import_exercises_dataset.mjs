// Builds the Android exercise catalog from the exercises-dataset export.
//
//   node scripts/import_exercises_dataset.mjs <dataset-dir> [--media <gif2webp.exe>]
//
// <dataset-dir> holds data/exercises.json, images/<id>-<media>.jpg and videos/<id>-<media>.gif.
// Spanish names come from scripts/exercise-names-es.json (dataset id -> name).
//
// Writes into android/app/src/main/assets:
//   exercises.json               catalog in the app's ExerciseRecord schema (English)
//   exercises_es.json            id -> { name, instructions } in Spanish
//   exercise-id-migration.json   old free-exercise-db id -> new id, so saved history,
//                                routines and favorites keep pointing at an exercise
//   exercises_extra.json         only the images of the app's own exercises (borrowed media)
//
// With --media it also fills android/app/src/exercisemedia/assets/exercise-media (committed)
// with <id>.jpg and the GIF converted to animated <id>.webp (gif2webp from Google's libwebp,
// ~3x smaller than the GIF). Existing files are kept, so a rerun only converts what is missing.

import { execFileSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const [datasetDir, mediaFlag, gif2webp] = process.argv.slice(2);
if (!datasetDir || (mediaFlag && (mediaFlag !== "--media" || !gif2webp))) {
  console.error("usage: node scripts/import_exercises_dataset.mjs <dataset-dir> [--media <gif2webp.exe>]");
  process.exit(1);
}

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const assets = path.join(repo, "android/app/src/main/assets");
const mediaDir = path.join(repo, "android/app/src/exercisemedia/assets/exercise-media");
const readJson = (file) => JSON.parse(fs.readFileSync(file, "utf8"));

const dataset = readJson(path.join(datasetDir, "data/exercises.json"));
const spanishNames = readJson(path.join(repo, "scripts/exercise-names-es.json"));
const oldCatalog = readJson(path.join(repo, "ios/calorietracker/Resources/FreeExerciseDB/dist/exercises.json"));
const manualMigration = readJson(path.join(repo, "scripts/exercise-id-migration.manual.json"));

const newId = (datasetId) => `ed_${datasetId}`;

// Dataset muscle names -> the app's muscle tokens (muscle map, splits, recovery).
const muscles = {
  abs: "abdominals", abdominals: "abdominals", obliques: "abdominals", core: "abdominals", "lower abs": "abdominals",
  "serratus anterior": "chest", pectorals: "chest", chest: "chest", "upper chest": "chest",
  quads: "quadriceps", quadriceps: "quadriceps",
  hamstrings: "hamstrings", glutes: "glutes",
  adductors: "adductors", groin: "adductors", "inner thighs": "adductors", abductors: "abductors",
  calves: "calves", soleus: "calves", ankles: "calves", "ankle stabilizers": "calves", feet: "calves", shins: "calves",
  lats: "lats", "latissimus dorsi": "lats",
  "upper back": "middle back", back: "middle back", rhomboids: "middle back",
  spine: "lower back", "lower back": "lower back",
  traps: "traps", trapezius: "traps",
  delts: "shoulders", deltoids: "shoulders", shoulders: "shoulders", "rear deltoids": "shoulders", "rotator cuff": "shoulders",
  biceps: "biceps", brachialis: "biceps", triceps: "triceps",
  forearms: "forearms", wrists: "forearms", "wrist flexors": "forearms", "wrist extensors": "forearms",
  "grip muscles": "forearms", hands: "forearms",
  "levator scapulae": "neck", sternocleidomastoid: "neck", neck: "neck",
};

// Dataset equipment -> the app's equipment tokens (filters, profile equipment preference).
const equipment = {
  "body weight": "body only", assisted: "machine", "leverage machine": "machine", "sled machine": "machine",
  "smith machine": "machine", "upper body ergometer": "machine", "skierg machine": "machine",
  "stationary bike": "machine", "elliptical machine": "machine", "stepmill machine": "machine",
  cable: "cable", barbell: "barbell", "olympic barbell": "barbell", "trap bar": "barbell",
  "ez barbell": "e-z curl bar", dumbbell: "dumbbell", kettlebell: "kettlebells",
  band: "bands", "resistance band": "bands", "stability ball": "exercise ball", "bosu ball": "exercise ball",
  "medicine ball": "medicine ball", roller: "foam roll",
  rope: "other", weighted: "other", "wheel roller": "other", hammer: "other", tire: "other",
};

const mapMuscle = (name) => muscles[name.trim().toLowerCase()];

function category(record) {
  if (record.category === "cardio") return "cardio";
  if (/stretch/i.test(record.name)) return "stretching";
  return "strength";
}

const capitalize = (text) => text.charAt(0).toUpperCase() + text.slice(1);
const steps = (list) => (list ?? []).map((step) => step.trim()).filter(Boolean);

const records = [];
const spanish = {};
const problems = [];
for (const record of dataset) {
  const id = newId(record.id);
  const media = path.basename(record.image, ".jpg"); // 0001-2gPfomN
  if (media.split("-")[0] !== record.id) problems.push(`${record.id}: media ${media} does not match id`);
  const primary = [mapMuscle(record.target)].filter(Boolean);
  const secondary = [...new Set(
    [record.muscle_group, ...(record.secondary_muscles ?? [])].filter(Boolean).map(mapMuscle).filter(Boolean)
  )].filter((muscle) => !primary.includes(muscle));
  const gear = equipment[record.equipment];
  if (!gear) problems.push(`${record.id}: unmapped equipment ${record.equipment}`);
  const english = steps(record.instruction_steps?.en);
  records.push({
    id,
    name: capitalize(record.name.trim()),
    equipment: gear ?? "other",
    primaryMuscles: primary,
    secondaryMuscles: secondary,
    instructions: english,
    category: category(record),
    images: [`exercise-media/${record.id}.jpg`, `exercise-media/${record.id}.webp`],
  });
  const name = spanishNames[record.id]?.trim();
  if (!name) problems.push(`${record.id}: missing Spanish name`);
  const instructionsEs = steps(record.instruction_steps?.es);
  spanish[id] = {
    name: name ? capitalize(name) : capitalize(record.name.trim()),
    // ExerciseLocalization only uses a translation whose step count matches English.
    instructions: instructionsEs.length === english.length ? instructionsEs : undefined,
  };
}

// Exercises the dataset lacks borrow the media of a close dataset exercise
// (scripts/exercise-borrowed-media.json): the animation when the movement is the same,
// only the still photo when just the position matches.
const borrowed = readJson(path.join(repo, "scripts/exercise-borrowed-media.json"));
const datasetIds = new Set(dataset.map((record) => record.id));
function borrowedImages(id) {
  const animated = borrowed.animated[id];
  const still = animated ?? borrowed.still[id];
  if (!still) {
    problems.push(`${id}: no borrowed media`);
    return [];
  }
  if (!datasetIds.has(still)) problems.push(`${id}: borrowed media ${still} is not in the dataset`);
  return animated ? [`exercise-media/${still}.jpg`, `exercise-media/${still}.webp`] : [`exercise-media/${still}.jpg`];
}

// Exercises the dataset lacks: cardio activities (outdoor walk/run quick log, treadmill,
// bike...) and warm-up basics (bodyweight squat, arm circles, superman...). They keep their
// free-exercise-db ids, which burn estimates, voice commands and ExerciseRoles use.
for (const { record, es } of readJson(path.join(repo, "scripts/exercise-kept-activities.json"))) {
  records.push({ ...record, images: borrowedImages(record.id) });
  spanish[record.id] = es;
}

// The app's own exercises (exercises_extra.json, android/scripts/build_extra_exercises.py)
// keep the Spanish copy that script wrote.
const previousSpanish = readJson(path.join(assets, "exercises_es.json"));
const extras = readJson(path.join(assets, "exercises_extra.json"));
for (const extra of extras) {
  extra.images = borrowedImages(extra.id);
  if (previousSpanish[extra.id]) spanish[extra.id] = previousSpanish[extra.id];
  else problems.push(`${extra.id}: own exercise lost its Spanish copy`);
}

// Old free-exercise-db ids -> new ids. Same name (ignoring word order and plurals)
// maps automatically; scripts/exercise-id-migration.manual.json covers the rest that
// the app's code or saved data commonly reference.
const normalize = (name) => name.toLowerCase().replace(/[^a-z0-9]+/g, " ")
  .replace(/\b(\w+?)s\b/g, "$1").trim().split(" ").sort().join(" ");
const byName = new Map(dataset.map((record) => [normalize(record.name), newId(record.id)]));
const knownIds = new Set(records.map((record) => record.id));
const migration = {};
for (const old of oldCatalog) {
  if (knownIds.has(old.id)) continue;
  const target = manualMigration[old.id] ?? byName.get(normalize(old.name));
  if (target) migration[old.id] = target;
}
for (const [oldId, target] of Object.entries(migration)) {
  if (!knownIds.has(target)) problems.push(`migration ${oldId} -> unknown ${target}`);
}

if (problems.length) {
  console.error(problems.join("\n"));
  process.exit(1);
}

if (gif2webp) {
  fs.mkdirSync(mediaDir, { recursive: true });
  let converted = 0;
  for (const record of dataset) {
    const media = path.basename(record.image, ".jpg");
    const still = path.join(mediaDir, `${record.id}.jpg`);
    const animation = path.join(mediaDir, `${record.id}.webp`);
    if (!fs.existsSync(still)) fs.copyFileSync(path.join(datasetDir, "images", `${media}.jpg`), still);
    if (!fs.existsSync(animation)) {
      execFileSync(gif2webp, ["-quiet", "-lossy", "-q", "70", "-m", "6", "-mixed",
        path.join(datasetDir, "videos", `${media}.gif`), "-o", animation]);
      converted++;
    }
  }
  console.log(`media=${dataset.length} converted=${converted}`);
}

const write = (file, value) => fs.writeFileSync(path.join(assets, file), JSON.stringify(value) + "\n");
write("exercises.json", records);
write("exercises_es.json", spanish);
write("exercise-id-migration.json", migration);
fs.writeFileSync(path.join(assets, "exercises_extra.json"), JSON.stringify(extras, null, 1));
const translatedSteps = Object.values(spanish).filter((entry) => entry.instructions).length;
console.log(`exercises=${records.length} spanishSteps=${translatedSteps} migrated=${Object.keys(migration).length}/${oldCatalog.length}`);
