import { api } from './authService';

export const friendService = {
    /**
     * Get all accepted friends
     */
    getFriends: async () => {
        const response = await api.get('/api/friends');
        return response.data;
    },

    /**
     * Get pending incoming friend requests
     */
    getIncomingRequests: async () => {
        const response = await api.get('/api/friends/requests/incoming');
        return response.data;
    },

    /**
     * Get rejected friend requests
     */
    getRejectedRequests: async () => {
        const response = await api.get('/api/friends/requests/rejected');
        return response.data;
    },

    /**
     * Search users by username query
     */
    searchUsers: async (query) => {
        const response = await api.get(`/api/friends/search?q=${encodeURIComponent(query)}`);
        return response.data;
    },

    /**
     * Send a friend request by username
     */
    sendRequest: async (addresseeUsername) => {
        const response = await api.post('/api/friends/request', { addresseeUsername });
        return response.data;
    },

    /**
     * Accept a friend request by friendship id
     */
    acceptRequest: async (friendshipId) => {
        const response = await api.post(`/api/friends/accept/${friendshipId}`);
        return response.data;
    },

    /**
     * Reject a friend request by friendship id
     */
    rejectRequest: async (friendshipId) => {
        const response = await api.post(`/api/friends/reject/${friendshipId}`);
        return response.data;
    },

    /**
     * Cancel an outgoing friend request by friendship id
     */
    cancelRequest: async (friendshipId) => {
        const response = await api.delete(`/api/friends/cancel/${friendshipId}`);
        return response.data;
    },

    /**
     * Remove a friend by friendship id
     */
    removeFriend: async (friendshipId) => {
        const response = await api.delete(`/api/friends/${friendshipId}`);
        return response.data;
    }
};
