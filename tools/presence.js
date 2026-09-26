// Works out, line by line, who is on stage and what each character perceives.
// The rules follow the original stage directions (Enter / Exit / Aside / Within / trance / death);
// OVERRIDES covers the places where the directions are silent or imprecise.

const NAMES = {
  Othello: ['othello'], Desdemona: ['desdemona'], Iago: ['iago'], Cassio: ['cassio'],
  Emilia: ['emilia'], Roderigo: ['roderigo'], Brabantio: ['brabantio'], Bianca: ['bianca'],
  Lodovico: ['lodovico'], Gratiano: ['gratiano'], Montano: ['montano'], Duke: ['duke'],
  Clown: ['clown'], Herald: ['herald'], Sailor: ['sailor'], Messenger: ['messenger'],
  Senators: ['senator1', 'senator2', 'senators'], Officers: ['officer'], Officer: ['officer'],
  Gentlemen: ['gentleman1', 'gentleman2', 'gentleman3', 'gentlemen'],
  Gentleman: ['gentleman1', 'gentleman2', 'gentleman3', 'gentlemen'],
  Musicians: ['musician'],
};

// Line id -> correction. private: nobody else hears the speech; to: who else hears an aside;
// exit/keep: who leaves or stays; unconscious/dead: state changes; after: applied after the line.
const OVERRIDES = {
  161: { exit: ['brabantio'] },                     // "Exeunt Duke, Senators, Officers, &c." — Brabantio leaves with the council
  279: { keep: ['iago', 'roderigo'] },              // everyone but Iago and Roderigo follows Othello to the castle
  355: { to: ['roderigo'] },                        // "Aside to him"
  567: { private: true },                           // Othello's "Why did I marry?" while Iago is leaving
  604: { private: true },                           // Iago watching Othello come in: "Not poppy, nor mandragora…"
  805: { private: true },                           // Iago's aside as Cassio enters
  1101: { private: true },                          // Roderigo alone at his stand
  1103: { private: true },                          // Iago's soliloquy while Roderigo waits apart
  1114: { private: true },                          // Othello watching the ambush from afar
  1116: { private: true },
  1118: { private: true },
  1147: { after: { unconscious: ['roderigo'] } },   // "O damn'd Iago!" — his last words
  1248: { unconscious: ['desdemona'] },             // "Smothers her."
  1388: { dead: ['othello'] },                      // "Falling upon Desdemona."
};

const ASIDE_EN = /^\s*\[_Aside/;
const ASIDE_UK = /^\s*\[_(Убік|Тихо до)/;
const WITHIN = /^\s*\[_Within/;

function namesIn(text) {
  const ids = new Set();
  for (const [name, list] of Object.entries(NAMES)) {
    // "Othello’s Herald" names the herald, not Othello.
    if (new RegExp(`\\b${name}\\b(?![’']s)`).test(text)) list.forEach(id => ids.add(id));
  }
  return ids;
}

function computePresence(scenes) {
  const dead = new Set();
  const unknown = [];
  for (const scene of scenes) {
    const onStage = new Set();
    const unconscious = new Set();
    const asleep = new Set();
    const hiding = new Set();
    let lastSpeaker = null;

    const aware = id => onStage.has(id) && !dead.has(id) && !unconscious.has(id) && !asleep.has(id);
    const record = (it, hear, see) => {
      it.h = [...hear].filter(id => !dead.has(id)).sort();
      const s = [...see].filter(id => !hear.has(id) && !dead.has(id));
      if (s.length) it.s = s.sort();
      const u = [...onStage].filter(id => unconscious.has(id) && !dead.has(id));
      if (u.length) it.u = u.sort();
      const z = [...onStage].filter(id => asleep.has(id));
      if (z.length) it.z = z.sort();
    };

    for (const it of scene.items) {
      const ov = OVERRIDES[it.id] || {};
      if (it.d) {
        const t = it.en.replace(/\s+/g, ' ');
        const before = new Set([...onStage].filter(aware));
        let known = true;
        if (/^(Enter|Re-enter)\b|appears above|sitting at a table/.test(t)) {
          namesIn(t).forEach(id => onStage.add(id));
          if (/in bed asleep/.test(t)) { onStage.add('desdemona'); asleep.add('desdemona'); }
        } else if (/in bed asleep/.test(t)) {
          onStage.add('desdemona'); asleep.add('desdemona');
        } else if (/^\[_Exeunt all but (.*)_\]$/.test(t)) {
          const keep = namesIn(t);
          [...onStage].forEach(id => { if (!keep.has(id)) onStage.delete(id); });
        } else if (/^\[_Exeunt\._\]$/.test(t)) {
          onStage.clear();
        } else if (/^\[_(Exit|Exeunt) (from above|with Emilia)/.test(t)) {
          if (lastSpeaker) onStage.delete(lastSpeaker);
          if (/with Emilia/.test(t)) onStage.delete('emilia');
        } else if (/^\[_Exit\._\]$/.test(t)) {
          if (lastSpeaker) onStage.delete(lastSpeaker);
        } else if (/^\[_(Exit|Exeunt) /.test(t) || /is led off|are borne off|runs out|and exit\._\]$/.test(t)) {
          const leaving = namesIn(t);
          // "Iago stabs Emilia and then runs out" / "Iago rushes … and exit": only Iago leaves.
          if (/runs out|and exit\._\]$/.test(t)) { leaving.clear(); leaving.add('iago'); }
          leaving.forEach(id => onStage.delete(id));
        } else if (/Falls in a trance/.test(t)) {
          if (lastSpeaker) unconscious.add(lastSpeaker);
        } else if (/withdraws/.test(t)) {
          hiding.add('othello');
        } else if (/She dies/.test(t)) {
          if (lastSpeaker) dead.add(lastSpeaker);
        } else {
          known = /^\[_(They draw|Music|A shot|Guns within|Kissing her|Trumpets within|Sings|Cry within|Striking|They fight|A bell rings|Montano is led|He puts the handkerchief|They rise|Giving her|Gives him|Opens the packet|Going|Retires|Goes to his stand|Rushes out|Draws, and wounds|Falls\._|Stabs Roderigo|A chair brought|Smothers her|Unlocks the door|Hanging over|Iago offers|Wounds Iago|Stabs himself|Falling upon)/.test(t);
        }
        if (!known) unknown.push(`#${it.id} ${t}`);
        (ov.exit || []).forEach(id => onStage.delete(id));
        if (ov.keep) [...onStage].forEach(id => { if (!ov.keep.includes(id)) onStage.delete(id); });
        (ov.unconscious || []).forEach(id => unconscious.add(id));
        (ov.dead || []).forEach(id => dead.add(id));
        const after = new Set([...onStage].filter(aware));
        const seers = new Set([...before, ...after].filter(id => !hiding.has(id)));
        const hidden = new Set([...hiding].filter(id => onStage.has(id)));
        record(it, seers, hidden);
      } else {
        const sp = it.sp;
        const within = WITHIN.test(it.en);
        if (!within && !onStage.has(sp)) { onStage.add(sp); }
        unconscious.delete(sp);
        asleep.delete(sp);
        if (/Coming forward/.test(it.en)) hiding.delete(sp);

        const aside = ov.private || ov.to || ASIDE_EN.test(it.en) || ASIDE_UK.test(it.uk);
        if (aside) {
          const hear = new Set([sp]);
          const to = [...(ov.to || [])];
          const m = it.en.match(/^\s*\[_Aside to ([A-Z][a-z]+)/);
          if (m && NAMES[m[1]]) NAMES[m[1]].forEach(id => to.push(id));
          to.filter(aware).forEach(id => hear.add(id));
          record(it, hear, new Set());
          const excluded = [...onStage].filter(id => aware(id) && !hear.has(id));
          if (excluded.length) it.x = excluded.sort();
        } else {
          const hear = new Set([...onStage].filter(id => aware(id) && !hiding.has(id)));
          hear.add(sp);
          const see = new Set([...hiding].filter(id => onStage.has(id) && id !== sp && aware(id)));
          record(it, hear, see);
        }
        const after = ov.after || {};
        (after.unconscious || []).forEach(id => unconscious.add(id));
        lastSpeaker = sp;
      }
    }
  }
  return unknown;
}

module.exports = { computePresence };
