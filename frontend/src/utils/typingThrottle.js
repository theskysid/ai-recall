// Leading-edge notifications keep typing responsive without sending every keystroke.
export const createTypingThrottle = (intervalMs = 1000, now = () => performance.now()) => {
    let lastSentAt = null;
    let lastConversation = null;
    return {
        shouldSend(conversationId) {
            const time = now();
            if (conversationId === lastConversation && lastSentAt !== null && time - lastSentAt < intervalMs) return false;
            lastConversation = conversationId;
            lastSentAt = time;
            return true;
        },
        reset() {
            lastSentAt = null;
            lastConversation = null;
        }
    };
};
