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
//
// A book of your own lives in my-books/<id>/ instead (same files; the folder is never committed).
// It is packed into dist/books/<id>.book.json and stays out of the app: copy that file to a phone
// and add it in the library with «Додати книгу з файлу».
const fs = require('fs');
const path = require('path');
const { computePresence } = require('./presence');

const root = path.join(__dirname, '..');
const id = process.argv[2];
if (!id) { console.error('usage: node tools/build-book.js <book id>'); process.exit(1); }
const shelfDir = path.join(root, 'books', id);
const ownDir = path.join(root, 'my-books', id);
if (fs.existsSync(shelfDir) && fs.existsSync(ownDir)) { console.error(`${id} is both in books/ and in my-books/`); process.exit(1); }
const own = fs.existsSync(ownDir);
const dir = own ? ownDir : shelfDir;
if (!fs.existsSync(dir)) { console.error(`no book ${id} in books/ or my-books/`); process.exit(1); }
const read = name => JSON.parse(fs.readFileSync(path.join(dir, name), 'utf8'));

const meta = read('book.json');
const build = read('build.json');
const guide = build.format === 'guide';
const characterIds = new Set(read('characters.json').characters.map(c => c.id));


// A guide to a work under copyright: no text of the work, only a retelling in our own words.
// source/guide/<act>.txt holds the units of one act (a novel's part):
//   == <kind> | <label> | <short> | <pov or ->     kind: prologue, chapter, calendar
//   title: …   place: …   summary: …
//   ! <key moment>                                 attaches to the next beat
//   [<ids who take part>] <beat of the retelling>
//   * [<ids>] <note>                               a beat that explains rather than tells (terms.noteLabel)
//   *<kind> [<ids>] <note>                         a note of one of terms.noteKinds, with its own label and colour
function readGuide() {
  const dir2 = path.join(dir, 'source/guide');
  const acts = fs.readdirSync(dir2).filter(f => /^\d+\.txt$/.test(f)).map(f => +f.slice(0, -4)).sort((x, y) => x - y);
  const out = [], summaries = [];
  let id = 0;
  for (const act of acts) {
    let unit = null, pending = null, number = 0;
    const lines = fs.readFileSync(path.join(dir2, `${act}.txt`), 'utf8').replace(/\r/g, '').split('\n');
    lines.forEach((raw, n) => {
      const line = raw.trim();
      const where = `${act}.txt:${n + 1}`;
      if (!line || line.startsWith('//')) return;
      let m;
      if ((m = line.match(/^== (\w+) \| ([^|]+) \| ([^|]+) \| (\S+)$/))) {
        number++;
        unit = { act, scene: number, kind: m[1], label: m[2].trim(), short: m[3].trim(), place: '', items: [] };
        if (m[4] !== '-') { unit.pov = m[4]; if (!characterIds.has(m[4])) errors.push(`${where}: unknown pov ${m[4]}`); }
        out.push(unit);
        summaries.push({ act, scene: number, title: '', summary: '', moments: [] });
        return;
      }
      if (!unit) { errors.push(`${where}: text before the first unit`); return; }
      const sum = summaries[summaries.length - 1];
      if ((m = line.match(/^(title|place|summary): (.+)$/))) {
        if (m[1] === 'place') unit.place = m[2]; else sum[m[1]] = m[2];
        return;
      }
      if ((m = line.match(/^! (.+)$/))) { pending = m[1]; return; }
      if ((m = line.match(/^(?:\*(\w*) )?\[([a-z0-9_ -]*)\] (.+)$/))) {
        const h = m[2].split(/\s+/).filter(Boolean);
        for (const c of h) if (!characterIds.has(c)) errors.push(`${where}: unknown character ${c}`);
        const item = { id: ++id, uk: m[3], p: 1 };
        if (m[1] !== undefined) item.n = 1;
        if (m[1]) {
          item.k = m[1];
          if (!(meta.terms.noteKinds || {})[m[1]]) errors.push(`${where}: note kind ${m[1]} is not in terms.noteKinds`);
        }
        if (h.length) item.h = h;
        unit.items.push(item);
        if (pending) { sum.moments.push({ line: id, text: pending }); pending = null; }
        return;
      }
      errors.push(`${where}: cannot read «${line.slice(0, 40)}»`);
    });
  }
  for (const [i, u] of out.entries()) {
    const s = summaries[i];
    if (!u.items.length) errors.push(`${u.act}.${u.scene} has no beats`);
    if (!s.title || !s.summary || !u.place) errors.push(`${u.act}.${u.scene} lacks title, place or summary`);
    if (!s.moments.length) errors.push(`${u.act}.${u.scene} has no key moment`);
  }
  return { units: out, summaries };
}
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
for (const s of guide ? [] : read('source/original.json')) {
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
const unknown = guide ? [] : computePresence(scenes, build.presence);
if (unknown.length) {
  console.error('Stage directions the presence rules do not understand:\n' + unknown.join('\n'));
  process.exit(1);
}

let pyramid = read('pyramid.json');
if (guide) {
  const g = readGuide();
  if (errors.length) { console.error(errors.join('\n')); process.exit(1); }
  scenes.push(...g.units);
  pyramid = { ...pyramid, scenes: g.summaries };
}

// A guide's perspectives point at beats as "<act>.<unit>#<n>", the n-th beat of a unit, so adding a
// beat elsewhere does not shift them; here they become beat ids like the lines of a play.
const perspectives = read('perspectives.json');
function beatId(ref, where) {
  if (typeof ref === 'number') return ref;
  const m = /^(\d+)\.(\d+)#(\d+)$/.exec(ref);
  const unit = m && scenes.find(s => s.act === +m[1] && s.scene === +m[2]);
  const item = unit && unit.items[+m[3] - 1];
  if (!item) { errors.push(`${where}: no beat ${ref}`); return 0; }
  return item.id;
}
for (const p of perspectives.perspectives) {
  if (p.death !== undefined) p.death = beatId(p.death, `${p.id} death`);
  for (const kind of ['phases', 'events']) for (const e of p[kind]) e.line = beatId(e.line, `${p.id} ${kind}`);
}
if (errors.length) { console.error(errors.join('\n')); process.exit(1); }

const book = {
  format: 1,
  meta,
  play: { scenes },
  characters: read('characters.json').characters,
  pyramid,
  perspectives,
};

const assets = path.join(root, 'app/src/main/assets/books');
const outDir = own ? path.join(root, 'dist/books') : assets;
fs.mkdirSync(outDir, { recursive: true });
const out = path.join(outDir, `${id}.book.json`);
fs.writeFileSync(out, JSON.stringify(book));
console.log(`ok ${id}: ${scenes.length} scenes, ${scenes.reduce((a, s) => a + s.items.length, 0)} lines, ${fs.statSync(out).size} bytes`);
if (own) {
  console.log(`your own book: ${path.relative(root, out)} — copy it to the phone and add it with «Додати книгу з файлу»`);
  process.exit(0);
}

// The library index: what the shelf shows without opening every book.
const shelf = fs.readdirSync(path.join(root, 'books'))
  .filter(b => fs.existsSync(path.join(assets, `${b}.book.json`)))
  .map(b => {
    const m = JSON.parse(fs.readFileSync(path.join(root, 'books', b, 'book.json'), 'utf8'));
    const idea = JSON.parse(fs.readFileSync(path.join(root, 'books', b, 'pyramid.json'), 'utf8')).idea;
    return { id: m.id, title: m.title, author: m.author, genre: m.genre, year: m.year, cover: m.cover, idea, file: `${m.id}.book.json` };
  })
  .sort((a, b) => a.year - b.year);
fs.writeFileSync(path.join(assets, 'library.json'), JSON.stringify({ books: shelf }, null, 1));
console.log(`library: ${shelf.map(b => b.title).join(', ')}`);
