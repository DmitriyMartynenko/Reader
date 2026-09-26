// Works out, line by line, who is on stage and what each character perceives, for any play whose
// stage directions follow the usual English conventions (Enter / Exit / Exeunt / Aside / Within).
// Each book supplies, in its build.json: `names` — how characters are named in the directions,
// and `overrides` — corrections by line id where the directions are silent or imprecise:
//   private: nobody else hears the speech; to: who else hears an aside; exit/keep: who leaves or stays;
//   unconscious/dead: state changes; after: changes applied after the line.

const ASIDE_EN = /^\s*\[_Aside/;
const ASIDE_UK = /^\s*\[_(Убік|Тихо до)/;
const WITHIN = /^\s*\[_Within/;

// Directions that do not change who is on stage or aware.
const NO_EFFECT = /^\[_(They |Music|A shot|A bell|Alarum|Flourish|Thunder|Guns|Kiss|Trumpets|Sings|Cry within|Strik|He puts|She puts|Giv|Opens|Reads|Going|Returning|Retires|Goes to|Rushes|Draws|Falls\._|Stabs (him|her)self|Stabs \w+\._|A chair|Smothers|Unlocks|Hanging|\w+ offers|Wounds \w+\._|Falling upon)/;

function computePresence(scenes, { names, overrides }) {
  // Character ids named in a direction, in the order they are mentioned. "Othello’s Herald" names the herald.
  function namesIn(text) {
    const found = [];
    for (const [name, ids] of Object.entries(names)) {
      const m = new RegExp(`\\b${name}\\b(?![’']s)`).exec(text);
      if (m) found.push([m.index, ids]);
    }
    return found.sort((a, b) => a[0] - b[0]).flatMap(([, ids]) => ids);
  }

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
      const ov = overrides[it.id] || {};
      if (it.d) {
        const t = it.en.replace(/\s+/g, ' ');
        const named = namesIn(t.replace(/\[_|_\]/g, ' '));
        const before = new Set([...onStage].filter(aware));
        if (/^(Enter|Re-enter)\b|appears above|sitting at a table/.test(t)) {
          named.forEach(id => onStage.add(id));
          if (/in bed asleep/.test(t) && named.length) asleep.add(named[0]);
        } else if (/in bed asleep/.test(t)) {
          // "Desdemona in bed asleep" opens the scene with her already there.
          if (named.length) { onStage.add(named[0]); asleep.add(named[0]); }
        } else if (/^\[_Exeunt all but /.test(t)) {
          [...onStage].forEach(id => { if (!named.includes(id)) onStage.delete(id); });
        } else if (/^\[_Exeunt\._\]$/.test(t)) {
          onStage.clear();
        } else if (/^\[_Exit (from above|with )/.test(t)) {
          // "Exit with Emilia": the last speaker leaves, taking the named ones along.
          if (lastSpeaker) onStage.delete(lastSpeaker);
          named.forEach(id => onStage.delete(id));
        } else if (/^\[_Exit\._\]$/.test(t)) {
          if (lastSpeaker) onStage.delete(lastSpeaker);
        } else if (/runs out|and exit\._\]$/.test(t)) {
          // "Iago stabs Emilia and then runs out": only the subject leaves.
          if (named.length) onStage.delete(named[0]);
        } else if (/^\[_(Exit|Exeunt) /.test(t) || /is led off|are borne off/.test(t)) {
          named.forEach(id => onStage.delete(id));
        } else if (/Falls in a trance/.test(t)) {
          if (lastSpeaker) unconscious.add(lastSpeaker);
        } else if (/withdraws/.test(t)) {
          if (named.length) hiding.add(named[0]);
        } else if (/(She|He) dies/.test(t)) {
          if (lastSpeaker) dead.add(lastSpeaker);
        } else if (!NO_EFFECT.test(t)) {
          unknown.push(`#${it.id} ${t}`);
        }
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
        if (!within && !onStage.has(sp)) onStage.add(sp);
        unconscious.delete(sp);
        asleep.delete(sp);
        if (/Coming forward/.test(it.en)) hiding.delete(sp);

        const aside = ov.private || ov.to || ASIDE_EN.test(it.en) || ASIDE_UK.test(it.uk);
        if (aside) {
          const hear = new Set([sp]);
          const to = [...(ov.to || [])];
          const m = it.en.match(/^\s*\[_Aside to ([A-Z][a-z]+)/);
          if (m && names[m[1]]) to.push(...names[m[1]]);
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
        ((ov.after || {}).unconscious || []).forEach(id => unconscious.add(id));
        lastSpeaker = sp;
      }
    }
  }
  return unknown;
}

module.exports = { computePresence };
