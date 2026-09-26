// Checks who perceives what against facts every reader of the play knows (books/<id>/presence-facts.json)
// and prints how much of the play each character with a perspective takes in.
//   node tools/check-presence.js othello             — facts + summary
//   node tools/check-presence.js othello othello 4.1 — one scene line by line through one character
const fs = require('fs');
const path = require('path');
const { loadBook } = require('./book');

const [bookId, who, sceneArg] = process.argv.slice(2);
const book = loadBook(bookId);

const lines = new Map();
for (const s of book.play.scenes) for (const it of s.items) lines.set(it.id, it);

function perceives(id, whom) {
  const l = lines.get(id);
  if ((l.h || []).includes(whom)) return 'hears';
  if ((l.s || []).includes(whom)) return 'sees';
  if ((l.u || []).includes(whom)) return 'unconscious';
  if ((l.z || []).includes(whom)) return 'asleep';
  if ((l.x || []).includes(whom)) return 'aside';
  return 'absent';
}

if (who && sceneArg) {
  const [a, n] = sceneArg.split('.').map(Number);
  const scene = book.play.scenes.find(s => s.act === a && s.scene === n);
  for (const it of scene.items) {
    console.log(`${perceives(it.id, who).padEnd(11)} #${it.id} ${it.d ? '[D]' : it.sp}: ${it.uk.replace(/\s+/g, ' ').slice(0, 70)}`);
  }
  process.exit(0);
}

const factsFile = path.join(book.dir, 'presence-facts.json');
const facts = fs.existsSync(factsFile) ? JSON.parse(fs.readFileSync(factsFile, 'utf8')) : [];
let failed = 0;
for (const [id, whom, expected, why] of facts) {
  const got = perceives(id, whom);
  if (got !== expected) failed++;
  console.log(`${got === expected ? 'ok  ' : 'FAIL'} #${id} ${whom}: ${got}${got === expected ? '' : ` (expected ${expected})`} — ${why}`);
}

console.log('\nShare of the play each character perceives (scenes with presence / lines heard):');
for (const p of book.perspectives.perspectives) {
  const scenes = book.play.scenes.filter(s => s.items.some(it => ['hears', 'sees'].includes(perceives(it.id, p.id))));
  const heard = [...lines.keys()].filter(id => perceives(id, p.id) === 'hears').length;
  console.log(`  ${p.id.padEnd(10)} ${String(scenes.length).padStart(2)} scenes, ${String(heard).padStart(4)} of ${lines.size} lines — ${scenes.map(s => `${s.act}.${s.scene}`).join(' ')}`);
}
console.log(`\n${facts.length - failed} of ${facts.length} facts hold`);
process.exit(failed ? 1 : 0);
