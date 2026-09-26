// Mirrors Play.nameRegex from the app to check which names get linked in the Ukrainian text.
const path = require('path');
const assets = path.join(__dirname, '../app/src/main/assets');
const play = require(path.join(assets, 'play.json'));
const chars = require(path.join(assets, 'characters.json')).characters;

const map = {};
for (const c of chars) for (const n of c.names) map[n.toLowerCase()] = c.id;
const esc = s => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
const alt = Object.keys(map).sort((a, b) => b.length - a.length).map(esc).join('|');
const re = new RegExp(`(?<![\\p{L}'’])(${alt})(?![\\p{L}'’])`, 'giu');

const counts = {};
const missed = {};
const probe = /(Отелл\p{L}*|Дездемон\p{L}*|Кассі\p{L}*|Емілі\p{L}*|Б'янк\p{L}*|[Мм]авр\p{L}*|[Дд]ож\p{L}*|Яго\p{L}*|Родріг\p{L}*)/gu;
for (const s of play.scenes) for (const it of s.items) {
  for (const x of it.uk.matchAll(re)) { const id = map[x[0].toLowerCase()]; counts[id] = (counts[id] || 0) + 1; }
  for (const w of it.uk.matchAll(probe)) if (!map[w[0].toLowerCase()]) missed[w[0]] = (missed[w[0]] || 0) + 1;
}
console.log(counts);
console.log('not linked:', missed);
