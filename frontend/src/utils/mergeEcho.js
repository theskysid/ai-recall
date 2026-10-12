/* Append a server message, or swap it in over the pending bubble our own send
   already painted. Matched on sender+content: the backend echoes no client id. */
export const mergeEcho = (prev, incoming, senderKey) => {
    const i = prev.findIndex(
        (m) => m.pending && m[senderKey] === incoming[senderKey] && m.content === incoming.content
    );
    if (i === -1) return [...prev, incoming];
    const next = [...prev];
    next[i] = incoming;
    return next;
};
