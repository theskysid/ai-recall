import { api } from './authService';

export const callService = {
    /**
     * Fetch a LiveKit access token for the current user to join a channel's call.
     * Returns { token, url, room, identity }.
     */
    getCallToken: async (channelId) => {
        const response = await api.get(`/api/channels/${channelId}/call-token`);
        return response.data;
    },

    /**
     * List saved call transcripts for a channel (most recent first).
     */
    getTranscripts: async (channelId) => {
        const response = await api.get(`/api/channels/${channelId}/transcripts`);
        return response.data;
    }
};
