import assert from 'node:assert';
import { mergeEcho } from './mergeEcho.js';

const pend = { id: 'pending-1', sender: 'me', content: 'hi', pending: true };
const echo = { id: 42, sender: 'me', content: 'hi' };

// own echo replaces the pending bubble, in place, without duplicating
assert.deepStrictEqual(mergeEcho([pend], echo, 'sender'), [echo]);
// someone else's message appends
assert.deepStrictEqual(mergeEcho([echo], { id: 43, sender: 'you', content: 'hi' }, 'sender'),
    [echo, { id: 43, sender: 'you', content: 'hi' }]);
// same text sent twice: each echo clears one pending, oldest first
const two = [{ ...pend, id: 'p1' }, { ...pend, id: 'p2' }];
assert.deepStrictEqual(mergeEcho(two, echo, 'sender'), [echo, two[1]]);
// a confirmed message is never overwritten
assert.strictEqual(mergeEcho([echo], echo, 'sender').length, 2);

console.log('mergeEcho ok');
