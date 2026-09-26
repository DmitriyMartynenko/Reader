// Parses a Project Gutenberg play into scenes of speeches and stage directions.
//   node tools/parse.js othello
// Reads books/<id>/source/original.txt, writes source/original.json and translation worksheets
// source/work/<act>-<scene>.txt (one numbered item per speech or direction).
const fs = require('fs');
const path = require('path');

const id = process.argv[2];
if (!id) { console.error('usage: node tools/parse.js <book id>'); process.exit(1); }
const dir = path.join(__dirname, '..', 'books', id);
const { source } = JSON.parse(fs.readFileSync(path.join(dir, 'build.json'), 'utf8'));

const raw = fs.readFileSync(path.join(dir, 'source/original.txt'), 'utf8').replace(/\r/g, '');
const start = raw.indexOf('\nACT I\n', raw.indexOf(source.startAfter));
const end = raw.indexOf(source.endBefore);
const body = raw.slice(start, end);

const ROMAN = { I: 1, II: 2, III: 3, IV: 4, V: 5 };
const scenes = [];
let act = 0, scene = null, lastSpeaker = null;

const isSpeaker = l => /^[A-Z][A-Z .’'&-]*\.$/.test(l.trim()) && !/^(ACT|SCENE)\b/.test(l.trim());
const isDirection = b => /^(Enter|Re-enter|Exit|Exeunt)\b/.test(b) || /^\[_[^\]]*_\]$/.test(b);

for (const block of body.split(/\n\s*\n/)) {
  const text = block.trim();
  if (!text) continue;
  let m;
  if ((m = text.match(/^ACT ([IV]+)$/))) { act = ROMAN[m[1]]; continue; }
  if ((m = text.match(/^SCENE ([IV]+)\.\s*(.*)$/s))) {
    scene = { act, scene: ROMAN[m[1]], place: m[2].replace(/\s+/g, ' ').replace(/\.$/, ''), items: [] };
    scenes.push(scene);
    lastSpeaker = null;
    continue;
  }
  if (!scene) continue;
  const lines = text.split('\n');
  if (isSpeaker(lines[0])) {
    lastSpeaker = lines[0].trim().replace(/\.$/, '');
    scene.items.push({ t: 's', sp: lastSpeaker, en: lines.slice(1).join('\n').trim() });
  } else if (isDirection(text.replace(/\s+/g, ' '))) {
    scene.items.push({ t: 'd', en: text.replace(/\s+/g, ' ') });
  } else if (lastSpeaker) {
    scene.items.push({ t: 's', sp: lastSpeaker, en: text });
  } else {
    scene.items.push({ t: 'd', en: text.replace(/\s+/g, ' ') });
  }
}

let n = 0;
for (const s of scenes) for (const it of s.items) it.id = ++n;

fs.writeFileSync(path.join(dir, 'source/original.json'), JSON.stringify(scenes, null, 1));

const wdir = path.join(dir, 'source/work');
fs.mkdirSync(wdir, { recursive: true });
for (const s of scenes) {
  const out = s.items.map(it => `#${it.id} ${it.t === 'd' ? '[D]' : it.sp}\n${it.en}`).join('\n\n');
  fs.writeFileSync(path.join(wdir, `${s.act}-${s.scene}.txt`), `${s.place}\n\n${out}\n`);
}

console.log(scenes.map(s => `${s.act}.${s.scene} ${s.items.length} items`).join('\n'));
console.log('total items', n);
