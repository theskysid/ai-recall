import { api } from './authService';

export const channelService = {
    /**
     * Get all channels the current user belongs to.
     */
    getChannels: async () => {
        const response = await api.get('/api/channels');
        return response.data;
    },

    /**
     * Create a new channel. The creator becomes the owner.
     */
    createChannel: async (name, description) => {
        const response = await api.post('/api/channels', { name, description });
        return response.data;
    },

    /**
     * Join an existing channel using its 8-character invite code.
     */
    joinChannel: async (inviteCode) => {
        const response = await api.post('/api/channels/join', { inviteCode });
        return response.data;
    },

    /**
     * Get the CHAT message history for a channel the user belongs to.
     */
    getMessages: async (channelId) => {
        const response = await api.get(`/api/channels/${channelId}/messages`);
        return response.data;
    },

    /**
     * Leave a channel.
     */
    leaveChannel: async (channelId) => {
        const response = await api.delete(`/api/channels/${channelId}/leave`);
        return response.data;
    },

    /**
     * Ask the channel's AI memory a question (RAG). Returns { answer, sourceIds }.
     */
    ask: async (channelId, query) => {
        const response = await api.get(`/api/channels/${channelId}/ask`, { params: { q: query } });
        return response.data;
    }
};
