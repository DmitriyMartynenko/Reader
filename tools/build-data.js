// Merges the parsed English text with the Ukrainian translation into app/src/main/assets/play.json.
const fs = require('fs');
const path = require('path');
const { computePresence } = require('./presence');

const root = path.join(__dirname, '..');
const scenes = JSON.parse(fs.readFileSync(path.join(root, 'source/othello_en.json'), 'utf8'));

const SPEAKERS = {
  OTHELLO: 'othello', DESDEMONA: 'desdemona', IAGO: 'iago', CASSIO: 'cassio', EMILIA: 'emilia',
  RODERIGO: 'roderigo', BRABANTIO: 'brabantio', BIANCA: 'bianca', LODOVICO: 'lodovico',
  GRATIANO: 'gratiano', MONTANO: 'montano', DUKE: 'duke', CLOWN: 'clown',
  'FIRST SENATOR': 'senator1', 'SECOND SENATOR': 'senator2', 'DUKE AND SENATORS': 'senators',
  OFFICER: 'officer', SAILOR: 'sailor', MESSENGER: 'messenger', HERALD: 'herald', ALL: 'all',
  'FIRST GENTLEMAN': 'gentleman1', 'SECOND GENTLEMAN': 'gentleman2', 'THIRD GENTLEMAN': 'gentleman3',
  GENTLEMEN: 'gentlemen', 'FIRST MUSICIAN': 'musician', VOICES: 'voices',
};

function readTranslation(file) {
  const out = { place: '', items: {} };
  let cur = null;
  for (const line of fs.readFileSync(file, 'utf8').replace(/\r/g, '').split('\n')) {
    let m;
    if ((m = line.match(/^@place (.*)$/))) { out.place = m[1].trim(); continue; }
    if ((m = line.match(/^#(\d+)(?: (D|@.+))?$/))) {
      cur = { lines: [], dir: m[2] === 'D', speaker: m[2] && m[2].startsWith('@') ? m[2].slice(1) : null };
      out.items[+m[1]] = cur;
      continue;
    }
    if (cur) cur.lines.push(line);
  }
  for (const it of Object.values(out.items)) it.text = it.lines.join('\n').trim();
  return out;
}

const errors = [];
const result = [];
for (const s of scenes) {
  const tr = readTranslation(path.join(root, `source/uk/${s.act}-${s.scene}.txt`));
  const items = s.items.map(it => {
    const u = tr.items[it.id];
    if (!u || !u.text) { errors.push(`missing #${it.id} in ${s.act}-${s.scene}`); return null; }
    const isDir = it.t === 'd' || u.dir;
    const spEn = u.speaker || it.sp;
    const o = { id: it.id, uk: u.text, en: it.en };
    if (isDir) o.d = 1;
    else {
      const sp = SPEAKERS[spEn];
      if (!sp) errors.push(`unknown speaker ${spEn} at #${it.id}`);
      o.sp = sp;
    }
    return o;
  }).filter(Boolean);
  const extra = Object.keys(tr.items).filter(id => !s.items.some(it => it.id === +id));
  if (extra.length) errors.push(`extra ids in ${s.act}-${s.scene}: ${extra.join(',')}`);
  result.push({ act: s.act, scene: s.scene, place: tr.place, placeEn: s.place, items });
}

if (errors.length) { console.error(errors.join('\n')); process.exit(1); }

// Who perceives each line: h = hears/sees it, s = watches without hearing, u = on stage but
// unconscious, z = asleep, x = on stage but shut out of an aside.
const unknownDirections = computePresence(result);
if (unknownDirections.length) {
  console.error('Stage directions the presence rules do not understand:\n' + unknownDirections.join('\n'));
  process.exit(1);
}

const out = path.join(root, 'app/src/main/assets/play.json');
fs.mkdirSync(path.dirname(out), { recursive: true });
fs.writeFileSync(out, JSON.stringify({ scenes: result }));
const counts = {};
for (const s of result) for (const it of s.items) if (it.sp) counts[it.sp] = (counts[it.sp] || 0) + 1;
console.log('ok', result.length, 'scenes,', fs.statSync(out).size, 'bytes');
console.log(counts);
