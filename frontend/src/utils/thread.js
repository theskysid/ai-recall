/* Thread primitives — shared by the channel thread and the DM panel so both
   read as the same record: one run per speaker, a ruled line per day. */

/* The backend sends naive timestamps; treat them as UTC. */
export const parseTs = (timestamp) => {
    if (!timestamp) return null;
    let str = typeof timestamp === 'string' ? timestamp : timestamp.toString();
    if (!str.endsWith('Z') && !str.includes('+') && !str.includes('GMT')) str += 'Z';
    const d = new Date(str);
    return isNaN(d.getTime()) ? null : d;
};

export const formatTime = (timestamp) => {
    const d = parseTs(timestamp);
    return d ? d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '';
};

const sameDay = (a, b) =>
    a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();

export const dayLabel = (d) => {
    const today = new Date();
    const yesterday = new Date();
    yesterday.setDate(today.getDate() - 1);
    if (sameDay(d, today)) return 'Today';
    if (sameDay(d, yesterday)) return 'Yesterday';
    return d.toLocaleDateString([], { month: 'long', day: 'numeric' });
};

/* Messages from the same person inside this window read as one run. */
const RUN_MS = 5 * 60 * 1000;

/* Walk the thread once and decide, per message, whether it opens a new day and
   whether it leads a run. Callers render; this only says where the breaks fall.
   `senderKey` differs between the two surfaces — 'sender' on channels,
   'senderUsername' on DMs. */
export const groupThread = (messages, senderKey) => {
    let lastDate = null;
    let lastSender = null;

    return messages.map((msg) => {
        const date = parseTs(msg.timestamp);
        const startsDay = !!date && (!lastDate || !sameDay(date, lastDate));
        const isLead =
            startsDay || msg[senderKey] !== lastSender || !date || !lastDate || date - lastDate > RUN_MS;

        lastDate = date || lastDate;
        lastSender = msg[senderKey];

        return { msg, day: startsDay ? dayLabel(date) : null, isLead };
    });
};
