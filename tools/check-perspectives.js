// Checks perspectives.json against the text and the presence data from build-data.js:
// first-person scenes only where the character is on stage, and events that point at real lines.
const path = require('path');
const assets = path.join(__dirname, '../app/src/main/assets');
const play = require(path.join(assets, 'play.json'));
const { perspectives, tones } = require(path.join(assets, 'perspectives.json'));

const lines = new Map();
for (const s of play.scenes) for (const it of s.items) lines.set(it.id, { ...it, key: `${s.act}.${s.scene}` });
const perceives = (it, who) => (it.h || []).includes(who) || (it.s || []).includes(who);

let errors = 0;
const fail = msg => { errors++; console.error('  ERROR ' + msg); };

for (const p of perspectives) {
  console.log(`\n${p.id} — «${p.idea}»`);
  const present = new Set(play.scenes.filter(s => s.items.some(it => perceives(it, p.id))).map(s => `${s.act}.${s.scene}`));
  for (const key of Object.keys(p.scenes)) if (!present.has(key)) fail(`scene ${key} has a first-person text but ${p.id} is not there`);
  for (const key of present) if (!p.scenes[key]) fail(`${p.id} is in scene ${key} but has no first-person text`);
  const actsPresent = new Set([...present].map(k => k.split('.')[0]));
  for (const a of Object.keys(p.acts)) if (!actsPresent.has(a)) fail(`act ${a} has a text but ${p.id} never appears in it`);
  for (const a of actsPresent) if (!p.acts[a]) fail(`${p.id} appears in act ${a} but has no act text`);

  for (const kind of ['phases', 'events']) {
    let prev = 0;
    for (const e of p[kind]) {
      const l = lines.get(e.line);
      if (!l) { fail(`${kind} line #${e.line} does not exist`); continue; }
      if (!tones[e.tone]) fail(`unknown tone ${e.tone}`);
      if (e.line < prev) fail(`${kind} #${e.line} is out of order`);
      prev = e.line;
      if (kind === 'events') {
        const mark = perceives(l, p.id) ? '' : '  (не бачить цього рядка)';
        console.log(`  #${e.line} ${l.key} ${e.label}${mark}\n        → ${l.uk.replace(/\s+/g, ' ').slice(0, 80)}`);
      }
    }
  }
}
console.log(`\n${errors} errors`);
process.exit(errors ? 1 : 0);
