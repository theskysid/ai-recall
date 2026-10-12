import assert from 'node:assert';
import { groupThread, parseTs, dayLabel } from './thread.js';

const at = (iso, sender) => ({ sender, timestamp: iso });
const day = (d, t) => `2026-10-${d}T${t}:00`;

// naive timestamps are UTC, not local
assert.strictEqual(parseTs('2026-10-12T09:00:00').toISOString(), '2026-10-12T09:00:00.000Z');
assert.strictEqual(parseTs('nonsense'), null);
assert.strictEqual(parseTs(null), null);

const g = groupThread([
    at(day('11', '09:00'), 'priya'),   // first ever -> opens a day, leads
    at(day('11', '09:02'), 'priya'),   // 2 min later, same person -> grouped
    at(day('11', '09:03'), 'sid'),     // different person -> leads
    at(day('11', '09:30'), 'sid'),     // same person, 27 min later -> leads
    at(day('12', '08:00'), 'sid'),     // next day -> opens a day, leads
], 'sender');

assert.deepStrictEqual(g.map((x) => x.isLead), [true, false, true, true, true]);
assert.deepStrictEqual(g.map((x) => x.day !== null), [true, false, false, false, true]);

// a message with an unreadable timestamp leads rather than silently joining a run
const bad = groupThread([at(day('11', '09:00'), 'priya'), { sender: 'priya', timestamp: 'x' }], 'sender');
assert.strictEqual(bad[1].isLead, true);

// the key is a parameter: DMs group on senderUsername
const dm = groupThread([
    { senderUsername: 'a', timestamp: day('11', '09:00') },
    { senderUsername: 'a', timestamp: day('11', '09:01') },
], 'senderUsername');
assert.deepStrictEqual(dm.map((x) => x.isLead), [true, false]);

// today and yesterday are named, older days are dated
assert.strictEqual(dayLabel(new Date()), 'Today');
const older = new Date('2020-03-04T12:00:00Z');
assert.strictEqual(dayLabel(older), older.toLocaleDateString([], { month: 'long', day: 'numeric' }));

console.log('thread ok');
