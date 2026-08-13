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
     * How many people are in this channel's call right now.
     * Returns { active, participants }.
     */
    getCallStatus: async (channelId) => {
        const response = await api.get(`/api/channels/${channelId}/call-status`);
        return response.data;
    },

    /**
     * List saved call transcripts for a channel (most recent first).
     */
    getTranscripts: async (channelId) => {
        const response = await api.get(`/api/channels/${channelId}/transcripts`);
        return response.data;
    },

    /**
     * Upload a browser-captured call recording for transcription. The Content-Type
     * override stops the shared JSON default from turning the FormData into JSON.
     */
    uploadRecording: async (channelId, blob, filename) => {
        const form = new FormData();
        form.append('file', blob, filename);
        const response = await api.post(`/api/channels/${channelId}/recording`, form, {
            headers: { 'Content-Type': 'multipart/form-data' }
        });
        return response.data;
    }
};
