// Packs one book of the library into a single file the app reads, and refreshes the library index.
//   node tools/build-book.js othello
//
// A book lives in books/<id>/:
//   book.json          title, author, cover colour, the words for its units (act / scene …)
//   build.json         how to read the source: speaker names, presence names and corrections
//   source/original.*  the original text; source/uk/<act>-<scene>.txt the translation by item id
//   characters.json    profiles; pyramid.json idea → acts → scenes → key moments;
//   perspectives.json  the story through each character's eyes
// Output: app/src/main/assets/books/<id>.book.json and app/src/main/assets/books/library.json.
const fs = require('fs');
const path = require('path');
const { computePresence } = require('./presence');

const root = path.join(__dirname, '..');
const id = process.argv[2];
if (!id) { console.error('usage: node tools/build-book.js <book id>'); process.exit(1); }
const dir = path.join(root, 'books', id);
const read = name => JSON.parse(fs.readFileSync(path.join(dir, name), 'utf8'));

const meta = read('book.json');
const build = read('build.json');
const original = read('source/original.json');

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
const scenes = [];
for (const s of original) {
  const tr = readTranslation(path.join(dir, `source/uk/${s.act}-${s.scene}.txt`));
  const items = s.items.map(it => {
    const u = tr.items[it.id];
    if (!u || !u.text) { errors.push(`missing #${it.id} in ${s.act}-${s.scene}`); return null; }
    const o = { id: it.id, uk: u.text, en: it.en };
    if (it.t === 'd' || u.dir) o.d = 1;
    else {
      const spEn = u.speaker || it.sp;
      o.sp = build.speakers[spEn];
      if (!o.sp) errors.push(`unknown speaker ${spEn} at #${it.id}`);
    }
    return o;
  }).filter(Boolean);
  const extra = Object.keys(tr.items).filter(n => !s.items.some(it => it.id === +n));
  if (extra.length) errors.push(`extra ids in ${s.act}-${s.scene}: ${extra.join(',')}`);
  scenes.push({ act: s.act, scene: s.scene, place: tr.place, placeEn: s.place, items });
}
if (errors.length) { console.error(errors.join('\n')); process.exit(1); }

// Who perceives each line: h = hears/sees it, s = watches without hearing, u = on stage but
// unconscious, z = asleep, x = on stage but shut out of an aside.
const unknown = computePresence(scenes, build.presence);
if (unknown.length) {
  console.error('Stage directions the presence rules do not understand:\n' + unknown.join('\n'));
  process.exit(1);
}

const pyramid = read('pyramid.json');
const book = {
  format: 1,
  meta,
  play: { scenes },
  characters: read('characters.json').characters,
  pyramid,
  perspectives: read('perspectives.json'),
};

const assets = path.join(root, 'app/src/main/assets/books');
fs.mkdirSync(assets, { recursive: true });
const out = path.join(assets, `${id}.book.json`);
fs.writeFileSync(out, JSON.stringify(book));
console.log(`ok ${id}: ${scenes.length} scenes, ${scenes.reduce((a, s) => a + s.items.length, 0)} lines, ${fs.statSync(out).size} bytes`);

// The library index: what the shelf shows without opening every book.
const shelf = fs.readdirSync(path.join(root, 'books'))
  .filter(b => fs.existsSync(path.join(assets, `${b}.book.json`)))
  .map(b => {
    const m = JSON.parse(fs.readFileSync(path.join(root, 'books', b, 'book.json'), 'utf8'));
    const idea = JSON.parse(fs.readFileSync(path.join(root, 'books', b, 'pyramid.json'), 'utf8')).idea;
    return { id: m.id, title: m.title, author: m.author, genre: m.genre, year: m.year, cover: m.cover, idea, file: `${m.id}.book.json` };
  });
fs.writeFileSync(path.join(assets, 'library.json'), JSON.stringify({ books: shelf }, null, 1));
console.log(`library: ${shelf.map(b => b.title).join(', ')}`);
