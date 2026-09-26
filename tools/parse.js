// Parses the Project Gutenberg Othello text into scenes of speeches and stage directions.
const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..');
const raw = fs.readFileSync(path.join(root, 'source/othello_en.txt'), 'utf8').replace(/\r/g, '');
const start = raw.indexOf('\nACT I\n', raw.indexOf('SCENE: The First Act'));
const end = raw.indexOf('*** END OF THE PROJECT GUTENBERG');
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

fs.writeFileSync(path.join(root, 'source/othello_en.json'), JSON.stringify(scenes, null, 1));

// Worksheets for translation: one per scene.
const wdir = path.join(root, 'source/work');
fs.mkdirSync(wdir, { recursive: true });
for (const s of scenes) {
  const out = s.items.map(it => `#${it.id} ${it.t === 'd' ? '[D]' : it.sp}\n${it.en}`).join('\n\n');
  fs.writeFileSync(path.join(wdir, `${s.act}-${s.scene}.txt`), `${s.place}\n\n${out}\n`);
}

const speakers = {};
for (const s of scenes) for (const it of s.items) if (it.sp) speakers[it.sp] = (speakers[it.sp] || 0) + 1;
console.log(scenes.map(s => `${s.act}.${s.scene} ${s.items.length} items, ${s.items.reduce((a, i) => a + i.en.split(/\s+/).length, 0)} words`).join('\n'));
console.log('total items', n);
console.log(speakers);
