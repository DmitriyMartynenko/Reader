// Mirrors Play.nameRegex from the app to check which names get linked in the translation.
//   node tools/check-names.js othello
const { loadBook } = require('./book');
const { play, characters } = loadBook(process.argv[2]);

const map = {};
for (const c of characters) for (const n of c.names) map[n.toLowerCase()] = c.id;
const esc = s => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
const alt = Object.keys(map).sort((a, b) => b.length - a.length).map(esc).join('|');
const re = new RegExp(`(?<![\\p{L}'’])(${alt})(?![\\p{L}'’])`, 'giu');

// Words that start like a character's name but are not linked: likely a missing inflected form.
const stems = [...new Set(characters.flatMap(c => c.names).map(n => esc(n.length > 4 ? n.slice(0, -2) : n)))];
const probe = new RegExp(`(?<![\\p{L}'’])(${stems.join('|')})\\p{L}*`, 'giu');

const counts = {};
const missed = {};
for (const s of play.scenes) for (const it of s.items) {
  for (const x of it.uk.matchAll(re)) { const id = map[x[0].toLowerCase()]; counts[id] = (counts[id] || 0) + 1; }
  for (const w of it.uk.matchAll(probe)) if (!map[w[0].toLowerCase()]) missed[w[0]] = (missed[w[0]] || 0) + 1;
}
console.log(counts);
console.log('not linked:', missed);
