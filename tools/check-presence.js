// Prints what a character perceives, scene by scene, and checks facts every reader of the play knows.
//   node tools/check-presence.js            — facts + summary for the main characters
//   node tools/check-presence.js othello 4.1 — line-by-line view of one scene through one character
const path = require('path');
const play = require(path.join(__dirname, '../app/src/main/assets/play.json'));

const lines = new Map();
for (const s of play.scenes) for (const it of s.items) lines.set(it.id, { ...it, scene: `${s.act}.${s.scene}` });

function perceives(id, who) {
  const l = lines.get(id);
  if ((l.h || []).includes(who)) return 'hears';
  if ((l.s || []).includes(who)) return 'sees';
  if ((l.u || []).includes(who)) return 'unconscious';
  if ((l.z || []).includes(who)) return 'asleep';
  if ((l.x || []).includes(who)) return 'aside';
  return 'absent';
}

const [who, sceneArg] = process.argv.slice(2);
if (who && sceneArg) {
  const [a, n] = sceneArg.split('.').map(Number);
  const scene = play.scenes.find(s => s.act === a && s.scene === n);
  for (const it of scene.items) {
    console.log(`${perceives(it.id, who).padEnd(11)} #${it.id} ${it.d ? '[D]' : it.sp}: ${it.uk.replace(/\s+/g, ' ').slice(0, 70)}`);
  }
  process.exit(0);
}

const facts = [
  [186, 'othello', 'absent', "Othello never hears Iago's plan at the end of I.3"],
  [277, 'othello', 'aside', "Othello on stage but does not hear Iago's aside «O, you are well tun'd now»"],
  [277, 'iago', 'hears', 'Iago hears himself'],
  [355, 'roderigo', 'hears', 'Iago whispers to Roderigo'],
  [355, 'montano', 'aside', 'Montano does not hear the whisper'],
  [422, 'cassio', 'absent', "Cassio has left before Iago reveals the trap"],
  [546, 'othello', 'hears', "Othello hears the warning about the green-eyed monster"],
  [584, 'othello', 'absent', 'Othello does not see Emilia pick up the handkerchief'],
  [584, 'desdemona', 'absent', 'Desdemona does not see it either'],
  [598, 'emilia', 'hears', 'Emilia sees Iago snatch the handkerchief'],
  [604, 'othello', 'aside', "Othello does not hear «Not poppy, nor mandragora…»"],
  [783, 'othello', 'unconscious', "Othello is in a trance during «Work on, my medicine»"],
  [808, 'othello', 'sees', 'Hidden Othello sees Cassio but cannot hear him'],
  [809, 'cassio', 'aside', "Cassio does not hear Othello's asides"],
  [834, 'othello', 'sees', 'Othello sees Bianca throw the handkerchief'],
  [847, 'iago', 'hears', 'Othello comes forward: «How shall I murder him?»'],
  [868, 'othello', 'hears', 'Othello hears the advice to strangle her'],
  [963, 'desdemona', 'hears', 'Desdemona hears herself called a whore'],
  [1002, 'iago', 'hears', 'Iago hears Emilia curse the unknown villain'],
  [1103, 'roderigo', 'aside', "Roderigo does not hear Iago's soliloquy in the ambush"],
  [1118, 'cassio', 'aside', 'Cassio does not hear Othello watching from afar'],
  [1147, 'othello', 'absent', 'Othello has already left the street'],
  [1195, 'desdemona', 'asleep', 'Desdemona sleeps through «It is the cause»'],
  [1219, 'desdemona', 'hears', 'Desdemona hears the accusation about the handkerchief'],
  [1250, 'desdemona', 'unconscious', 'Smothered Desdemona hears nothing'],
  [1251, 'othello', 'hears', "Othello hears Emilia's voice through the door"],
  [1274, 'emilia', 'hears', 'Emilia hears «Nobody; I myself»'],
  [1310, 'desdemona', 'absent', 'Dead Desdemona perceives nothing'],
  [1370, 'othello', 'hears', "Othello hears Iago's last words"],
  [1390, 'othello', 'absent', 'Othello is dead when Lodovico speaks the last lines'],
];

let failed = 0;
for (const [id, whom, expected, why] of facts) {
  const got = perceives(id, whom);
  const ok = got === expected || (expected === 'absent' && got === 'absent');
  if (!ok) failed++;
  console.log(`${ok ? 'ok  ' : 'FAIL'} #${id} ${whom}: ${got}${ok ? '' : ` (expected ${expected})`} — ${why}`);
}

console.log('\nShare of the play each character perceives (scenes with presence / lines heard):');
for (const whom of ['othello', 'desdemona', 'iago', 'cassio', 'emilia', 'roderigo', 'brabantio', 'bianca']) {
  const scenes = play.scenes.filter(s => s.items.some(it => ['hears', 'sees'].includes(perceives(it.id, whom))));
  const heard = [...lines.keys()].filter(id => perceives(id, whom) === 'hears').length;
  console.log(`  ${whom.padEnd(10)} ${String(scenes.length).padStart(2)} scenes, ${String(heard).padStart(4)} of ${lines.size} lines — ${scenes.map(s => `${s.act}.${s.scene}`).join(' ')}`);
}
process.exit(failed ? 1 : 0);
