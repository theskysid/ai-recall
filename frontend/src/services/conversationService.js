import { api } from './authService';

export const conversationService = {
    /**
     * Get all active conversations for the current user
     */
    getConversations: async () => {
        const response = await api.get('/api/conversations');
        return response.data;
    },

    /**
     * Get or create a conversation with a specific friend by username
     */
    getOrCreateConversationWith: async (username) => {
        const response = await api.post(`/api/conversations/with/${encodeURIComponent(username)}`);
        return response.data;
    },

    /**
     * Get paginated active message history for a conversation
     */
    getMessages: async (conversationId, page = 0, size = 50) => {
        const response = await api.get(`/api/conversations/${conversationId}/messages?page=${page}&size=${size}`);
        return response.data;
    },

    /**
     * Update retention policy (SIX_HOURS, ONE_DAY, SEVEN_DAYS)
     */
    updateRetentionPolicy: async (conversationId, policy) => {
        const response = await api.put(`/api/conversations/${conversationId}/retention`, { policy });
        return response.data;
    }
};
