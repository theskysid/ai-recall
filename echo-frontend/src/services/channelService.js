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
    }
};
