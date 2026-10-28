// Builds android/app/src/main/assets/shelf_life.json, the per-food storage times the Pantry
// uses to estimate best-before dates.
//
//   node scripts/build_shelf_life.mjs
//
// Source: USDA FSIS FoodKeeper (public domain, catalog.data.gov/dataset/fsis-foodkeeper-data),
// as CSV in scripts/foodkeeper/ (copied from github.com/jelera/food-shelflife-db, MIT).
// scripts/shelf-life-es.json maps Spanish food words to FoodKeeper names, so "pechuga de
// pollo" or "leche de almendras" find their entry.
//
// Output: { "foods": [[name, fridgeDays, freezerDays, pantryDays], ...], "es": {...} } with
// null where FoodKeeper has no time (or advises against that storage). Ranges keep the
// shorter end, so estimates stay on the safe side.

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");

function parseCsv(text) {
  const rows = [];
  let row = [], field = "", quoted = false;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (quoted) {
      if (c === '"' && text[i + 1] === '"') { field += '"'; i++; }
      else if (c === '"') quoted = false;
      else field += c;
    } else if (c === '"') quoted = true;
    else if (c === ",") { row.push(field); field = ""; }
    else if (c === "\n") { row.push(field.replace(/\r$/, "")); rows.push(row); row = []; field = ""; }
    else field += c;
  }
  if (field || row.length) { row.push(field); rows.push(row); }
  const [header, ...body] = rows;
  return body.filter((r) => r.length > 1).map((r) => Object.fromEntries(header.map((h, i) => [h, (r[i] ?? "").trim()])));
}

const unitDays = { day: 1, days: 1, week: 7, weeks: 7, month: 30, months: 30, year: 365, years: 365 };

// Shorter end of a FoodKeeper range in days; date-of-purchase columns first.
function days(row, ...prefixes) {
  for (const prefix of prefixes) {
    const min = parseFloat(row[`${prefix}_Min`]);
    const unit = unitDays[(row[`${prefix}_Metric`] ?? "").toLowerCase()];
    if (Number.isFinite(min) && min > 0 && unit) return Math.round(min * unit);
  }
  return null;
}

const rows = parseCsv(fs.readFileSync(path.join(repo, "scripts/foodkeeper/ingredients.csv"), "utf8"));
const foods = [];
for (const row of rows) {
  const name = [row.Name, row.Name_subtitle].filter(Boolean).join(", ").replace(/\s+/g, " ");
  const entry = [
    name,
    days(row, "DOP_Refrigerate", "Refrigerate"),
    days(row, "DOP_Freeze", "Freeze"),
    days(row, "DOP_Pantry", "Pantry"),
  ];
  if (entry.slice(1).some((value) => value !== null)) foods.push(entry);
}

const es = JSON.parse(fs.readFileSync(path.join(repo, "scripts/shelf-life-es.json"), "utf8"));
const out = path.join(repo, "android/app/src/main/assets/shelf_life.json");
fs.writeFileSync(out, JSON.stringify({ foods, es }) + "\n");
console.log(`foods=${foods.length} es=${Object.keys(es).length} bytes=${fs.statSync(out).size}`);
