// Opt in from DevTools: localStorage.setItem('recall.chatDiagnostics', '1').
// Use this browser's monotonic clock; comparing different users' wall clocks is misleading.
export const createChatDiagnostics = ({
    enabled = () => {
        try { return globalThis.localStorage?.getItem('recall.chatDiagnostics') === '1'; }
        catch { return false; }
    },
    now = () => performance.now(),
    makeId = () => crypto.randomUUID(),
    report = (sample) => console.info('[chat timing]', sample)
} = {}) => {
    const pending = new Map();
    const prune = (time) => {
        for (const [id, sample] of pending) {
            if (time - sample.start > 60000) pending.delete(id);
        }
    };
    return {
        start(event) {
            if (!enabled()) return undefined;
            prune(now());
            if (pending.size >= 100) pending.delete(pending.keys().next().value);
            const id = makeId();
            pending.set(id, { event, start: now() });
            return id;
        },
        finish(message, headers = {}) {
            prune(now());
            const sample = pending.get(message?.clientMessageId);
            if (!sample) return;
            pending.delete(message.clientMessageId);
            if (!enabled()) return;
            const echoRoundTripMs = now() - sample.start;
            const serverMs = Number.parseFloat(headers['x-chat-server-ms']);
            report({
                event: sample.event,
                echoRoundTripMs,
                ...(Number.isFinite(serverMs) && serverMs >= 0 ? {
                    serverMs,
                    // Includes both network directions, socket writes, and browser scheduling.
                    residualRoundTripMs: Math.max(0, echoRoundTripMs - serverMs)
                } : {})
            });
        },
        clear() { pending.clear(); }
    };
};

export const chatDiagnostics = createChatDiagnostics();
