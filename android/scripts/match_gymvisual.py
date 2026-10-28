"""Matches the app's exercise catalog to the Gym visual animations in exercises-dataset.

The media is (c) Gym visual (https://gymvisual.com/) and is NOT committed to this repo.
This script copies the matched GIFs into android/app/src/gymvisual/assets (git-ignored),
renamed by catalog id, plus gymvisual_map.json. Without that folder the app keeps its own art.

Usage: python android/scripts/match_gymvisual.py <path-to-exercises-dataset-main> [--report]
"""
import json
import os
import re
import shutil
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ANDROID = os.path.dirname(HERE)
REPO = os.path.dirname(ANDROID)
CATALOG = os.path.join(REPO, "ios/calorietracker/Resources/FreeExerciseDB/dist/exercises.json")
EXTRA = os.path.join(ANDROID, "app/src/main/assets/exercises_extra.json")
OUT = os.path.join(ANDROID, "app/src/gymvisual/assets/gymvisual")

EQUIPMENT_WORDS = {
    "barbell", "dumbbell", "cable", "kettlebell", "band", "smith", "machine", "lever", "ez", "sled",
    "trap", "medicine", "stability", "bosu", "rope", "weighted", "assisted", "suspension", "roller", "wheel",
}
SYNONYMS = {
    "dumbbells": "dumbbell", "palms": "palm", "arm": "", "leg": "", "with": "", "ups": "", "up": "", "single": "one", "alternating": "alternate", "db": "dumbbell", "bb": "barbell", "kettlebells": "kettlebell", "bands": "band",
    "pushup": "push", "pushups": "push", "pullup": "pull", "pullups": "pull", "chinup": "chin", "chinups": "chin",
    "situp": "sit", "situps": "sit", "curls": "curl", "raises": "raise", "presses": "press", "rows": "row",
    "squats": "squat", "lunges": "lunge", "extensions": "extension", "flyes": "fly", "flys": "fly", "flye": "fly",
    "crunches": "crunch", "shrugs": "shrug", "dips": "dip", "deadlifts": "deadlift", "biceps": "bicep",
    "triceps": "tricep", "lying": "lying", "machine": "lever", "calves": "calf", "stretches": "stretch",
}
# Words that change the movement: both names must agree on all of them.
VARIANT_WORDS = {
    "incline", "decline", "seated", "standing", "lying", "upright", "hammer", "reverse", "preacher",
    "concentration", "one", "single", "close", "wide", "behind", "front", "side", "bent", "overhead",
    "jump", "kneeling", "alternate", "alternating", "rear", "inner", "outer", "two", "split", "sumo",
    "romanian", "stiff", "floor", "bench", "box", "step", "pistol", "hack", "zercher", "goblet",
    "pullover", "fly", "shrug", "row", "press", "curl", "extension", "raise", "squat", "lunge",
    "deadlift", "crunch", "stretch", "kickback", "clean", "snatch", "jerk", "throw", "walk", "run",
    "down", "zottman", "lateral", "palm", "twist", "military", "strap",
}
STOP = {"the", "a", "on", "with", "to", "and", "of", "in", "-", "v", "2", "version", "grip", "medium", "position"}


def tokens(name):
    words = re.split(r"[^a-z0-9]+", name.lower().replace("push-up", "pushup").replace("pull-up", "pullup")
                     .replace("chin-up", "chinup").replace("sit-up", "situp").replace("e-z", "ez"))
    out = []
    for w in words:
        if not w or w in STOP:
            continue
        w = SYNONYMS.get(w, w)
        if w:
            out.append(w)
    return out


def score(a, b):
    ta, tb = set(tokens(a)), set(tokens(b))
    if not ta or not tb:
        return 0.0
    ea, eb = ta & EQUIPMENT_WORDS, tb & EQUIPMENT_WORDS
    if ea != eb:
        return 0.0  # different equipment: never the same exercise picture
    if (ta & VARIANT_WORDS) != (tb & VARIANT_WORDS):
        return 0.0  # different variant or movement
    inter = len(ta & tb)
    return inter / len(ta | tb)


def main():
    dataset = sys.argv[1]
    report = "--report" in sys.argv
    gv = json.load(open(os.path.join(dataset, "data/exercises.json"), encoding="utf-8"))
    catalog = json.load(open(CATALOG, encoding="utf-8")) + json.load(open(EXTRA, encoding="utf-8"))
    mapping, rows = {}, []
    for item in catalog:
        best, best_score = None, 0.0
        for g in gv:
            s = score(item["name"], g["name"])
            if s > best_score:
                best, best_score = g, s
        rows.append((best_score, item["id"], item["name"], best["name"] if best else ""))
        if best and best_score >= 0.75:
            mapping[item["id"]] = {
                "gif": os.path.basename(best["gif_url"]),
                "jpg": os.path.basename(best["image"]),
                "source_name": best["name"],
                "score": round(best_score, 2),
            }
    if report:
        for r in sorted(rows):
            print(f"{r[0]:.2f}\t{r[2]}\t->\t{r[3]}")
    print(f"matched {len(mapping)} of {len(catalog)}", file=sys.stderr)
    os.makedirs(OUT, exist_ok=True)
    for f in os.listdir(OUT):
        os.remove(os.path.join(OUT, f))
    for item_id, m in mapping.items():
        shutil.copyfile(os.path.join(dataset, "videos", m["gif"]), os.path.join(OUT, item_id + ".gif"))
        shutil.copyfile(os.path.join(dataset, "images", m["jpg"]), os.path.join(OUT, item_id + ".jpg"))
    with open(os.path.join(OUT, "gymvisual_map.json"), "w", encoding="utf-8") as fh:
        json.dump({k: {"source_name": v["source_name"], "score": v["score"]} for k, v in mapping.items()}, fh, indent=1)


if __name__ == "__main__":
    main()
