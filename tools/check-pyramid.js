// Checks that every key moment of a book's pyramid points at a line of its own scene
// and prints the start of that line so the link can be eyeballed.
//   node tools/check-pyramid.js othello
const { loadBook } = require('./book');
const { play, pyramid } = loadBook(process.argv[2]);

let errors = 0;
let total = 0;
for (const s of pyramid.scenes) {
  const scene = play.scenes.find(x => x.act === s.act && x.scene === s.scene);
  if (!scene) { console.error(`no scene ${s.act}.${s.scene}`); errors++; continue; }
  console.log(`\n${s.act}.${s.scene} ${s.title}`);
  let prev = 0;
  for (const m of s.moments) {
    total++;
    const line = scene.items.find(it => it.id === m.line);
    if (!line) { console.error(`  #${m.line} is not in scene ${s.act}.${s.scene}`); errors++; continue; }
    if (m.line <= prev) { console.error(`  #${m.line} is out of order`); errors++; }
    prev = m.line;
    console.log(`  #${m.line} ${m.text}\n        → ${line.uk.replace(/\s+/g, ' ').slice(0, 90)}`);
  }
}
if (pyramid.scenes.length !== play.scenes.length) { console.error('scene count differs'); errors++; }
console.log(`\n${total} moments, ${errors} errors`);
process.exit(errors ? 1 : 0);
