import axios from 'axios';


// Set your API base URL
const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080'; 

// Create axios instance
export const api = axios.create({
    baseURL: API_URL,
    headers: {
        'Content-Type': 'application/json'
    },
    withCredentials: true // Important for handling cookies cross-origin
});

// Request interceptor to attach JWT token if present
api.interceptors.request.use(
    (config) => {
        const currentUserStr = localStorage.getItem('currentUser') || localStorage.getItem('user');
        if (currentUserStr) {
            try {
                const userData = JSON.parse(currentUserStr);
                if (userData && userData.token) {
                    config.headers.Authorization = `Bearer ${userData.token}`;
                }
            } catch (e) {
                // ignore parsing error
            }
        }
        return config;
    },
    (error) => Promise.reject(error)
);

// Response interceptor for global error handling
api.interceptors.response.use(
    (response) => response,
    (error) => {
        // Global error handling
        if (error.response) {
            switch (error.response.status) {
                case 401: // Unauthorized
                    // Skip auto-redirect for session validation calls —
                    // SessionGuard handles that flow explicitly.
                    if (error.config?.url !== '/auth/getcurrentuser') {
                        authService.logout();
                        window.location.href = '/login';
                    }
                    break;
                case 403: // Forbidden
                    console.error('Access forbidden');
                    break;
                case 404: // Not Found
                    console.error('Resource not found');
                    break;
                case 500: // Internal Server Error
                    console.error('Server error');
                    break;
            }
        } else if (error.request) {
            // Request made but no response received
            console.error('No response received', error.request);
        } else {
            // Something happened in setting up the request
            console.error('Error setting up request', error.message);
        }
        return Promise.reject(error);
    }
);

const generateUserColor = () => {
    const colors = [
        '#FF6B6B', '#4ECDC4', '#45B7D1', '#96CEB4', '#FFEAA7',
        '#DDA0DD', '#98D8C8', '#F7DC6F', '#BB8FCE', '#85C1E9'
    ]
    return colors[Math.floor(Math.random() * colors.length)];
}

const isEmailIdentifier = (identifier) => identifier?.includes('@');
const normalizeIdentifier = (identifier) => identifier?.trim();

export const authService = {

    login: async (username, password) => {
        try{

            const response = await api.post('/auth/login', {
                username,
                password
            });

            //After successful login
            const userColor = generateUserColor();
            const userData = {
                ...response.data,
                color: userColor,
                loginTime: new Date().toISOString()
            };

            localStorage.setItem('currentUser', JSON.stringify(userData));
            localStorage.setItem('user', JSON.stringify(response.data));

            return{
                success: true,
                user: userData
            };

        }
        catch(error){
            console.error('Login failed', error);
            const errorMessage = error.response?.data?.message || 'Login failed, Please check your credentials';
            throw new Error(errorMessage);  
        }
    },

    signup: async(username, email, password) => {
        try{

            const response = await api.post('/auth/signup', {
                username,
                email,
                password
            });

            return{
                success: true,
                user: response.data
            };
        }
        catch (error){
            console.error('Signup failed', error);
            const errorMessage = error.response?.data?.message || 'Signup failed, Please check your credentials';
            throw new Error(errorMessage);
        }
    },

    logout: async() => {
        try{
            await api.post('/auth/logout');
        }
        catch(error){
            console.error('Logout failed', error);
        }
        finally {
            localStorage.removeItem('currentUser');
            localStorage.removeItem('user');
        }
    },

    fetchCurrentUser: async() => {
        try{
            const response = await api.get('/auth/getcurrentuser');

            // Preserve the existing JWT token — the /auth/getcurrentuser
            // endpoint returns user data WITHOUT a token field.
            const existingUser = JSON.parse(localStorage.getItem('user') || '{}');
            const existingCurrentUser = JSON.parse(localStorage.getItem('currentUser') || '{}');
            const existingToken = existingCurrentUser.token || existingUser.token;

            const updatedUser = { ...response.data };
            if (existingToken && !updatedUser.token) {
                updatedUser.token = existingToken;
            }

            localStorage.setItem('user', JSON.stringify(updatedUser));

            // Keep 'currentUser' in sync with fresh data while preserving color/loginTime/token
            const updatedCurrentUser = {
                ...existingCurrentUser,
                ...updatedUser,
            };
            localStorage.setItem('currentUser', JSON.stringify(updatedCurrentUser));

            return updatedUser;
        }
        catch (error){
            console.error('Error fetching user data', error);

            //if unauthorized, needs to logout
            if(error.response && error.response.status === 401){
                await authService.logout();
            }

            // Re-throw so callers (e.g. SessionGuard) can detect failure
            throw error;
        }
    },



    getCurrentUser: () => {

        const currentUserStr = localStorage.getItem('currentUser');
        const userStr = localStorage.getItem('user');

        try{
            if(currentUserStr){
                return JSON.parse(currentUserStr);
            }
            else if(userStr){
                const userData = JSON.parse(userStr);
                const userColor = generateUserColor();
                const enrichedUser = {
                    ...userData,
                    color: userColor,
                    loginTime: new Date().toISOString()
                };

                localStorage.setItem('currentUser', JSON.stringify(enrichedUser));
                return enrichedUser;
            }
            return null
        }
        catch (error){
            console.error('Error parsing user data', error);
            return null;
        }
    },

    isAuthenticated: () => {
        const user = localStorage.getItem('user') || localStorage.getItem('currentUser');
        return !!user;
    },

    sendOtp: async (identifier) => {
        const value = normalizeIdentifier(identifier);
        if (!value) {
            throw new Error('Email or phone is required');
        }
        return isEmailIdentifier(value)
            ? authService.sendEmailOtp(value)
            : authService.sendPhoneOtp(value);
    },

    verifyOtp: async (identifier, otp) => {
        const value = normalizeIdentifier(identifier);
        if (!value) {
            throw new Error('Email or phone is required');
        }
        return isEmailIdentifier(value)
            ? authService.verifyEmailOtp(value, otp)
            : authService.verifyPhoneOtp(value, otp);
    },

    fetchPrivateMessages: async(user1, user2) => {
        try{

            const response = await api.get(`/api/messages/private?user1=${encodeURIComponent(user1)}&user2=${encodeURIComponent(user2)}`);
            return response.data;
        }
        catch(error){
            console.error('Error in fetching private messages', error);
            throw error;
        }
    },

    getOnlineUsers: async () => {
        try {
            const response = await api.get('/auth/getonlineusers');
            return response.data;
        } catch (error) {
            console.error('Fetch online users error:', error);
            throw error;
        }
    },

    // ── Email OTP ──────────────────────────────────────────

    sendEmailOtp: async (email) => {
        try {
            const response = await api.post('/auth/email-otp/send', { email });
            return { success: true, message: response.data.message };
        } catch (error) {
            console.error('Send email OTP failed', error);
            const errorMessage = error.response?.data?.error || 'Failed to send OTP. Please try again.';
            throw new Error(errorMessage);
        }
    },

    verifyEmailOtp: async (email, otp) => {
        try {
            const response = await api.post('/auth/email-otp/verify', { email, otp });

            const userColor = generateUserColor();
            const userData = {
                ...response.data,
                color: userColor,
                loginTime: new Date().toISOString()
            };

            localStorage.setItem('currentUser', JSON.stringify(userData));
            localStorage.setItem('user', JSON.stringify(response.data));

            return { success: true, user: userData };
        } catch (error) {
            console.error('Verify email OTP failed', error);
            const errorMessage = error.response?.data?.error || 'OTP verification failed.';
            throw new Error(errorMessage);
        }
    },

    // ── Phone OTP ──────────────────────────────────────────

    sendPhoneOtp: async (phone) => {
        try {
            const response = await api.post('/auth/phone-otp/send', { phone });
            return { success: true, message: response.data.message };
        } catch (error) {
            console.error('Send phone OTP failed', error);
            const errorMessage = error.response?.data?.error || 'Failed to send OTP. Please try again.';
            throw new Error(errorMessage);
        }
    },

    verifyPhoneOtp: async (phone, otp) => {
        try {
            const response = await api.post('/auth/phone-otp/verify', { phone, otp });

            const userColor = generateUserColor();
            const userData = {
                ...response.data,
                color: userColor,
                loginTime: new Date().toISOString()
            };

            localStorage.setItem('currentUser', JSON.stringify(userData));
            localStorage.setItem('user', JSON.stringify(response.data));

            return { success: true, user: userData };
        } catch (error) {
            console.error('Verify phone OTP failed', error);
            const errorMessage = error.response?.data?.error || 'OTP verification failed.';
            throw new Error(errorMessage);
        }
    },

    sendSignupOtp: async (identifier) => authService.sendOtp(identifier),

    verifySignupOtp: async ({ username, identifier, password, otp }) => {
        try {
            const response = await api.post('/auth/signup/verify', {
                username,
                identifier,
                password,
                otp
            });

            const userColor = generateUserColor();
            const userData = {
                ...response.data,
                color: userColor,
                loginTime: new Date().toISOString()
            };

            localStorage.setItem('currentUser', JSON.stringify(userData));
            localStorage.setItem('user', JSON.stringify(response.data));

            return { success: true, user: userData };
        } catch (error) {
            console.error('Verify signup OTP failed', error);
            const errorMessage = error.response?.data?.error || error.response?.data?.message || 'Signup verification failed.';
            throw new Error(errorMessage);
        }
    },

    // ── Google OAuth ───────────────────────────────────────

    googleLogin: async (idToken) => {
        try {
            const response = await api.post('/auth/google/login', { idToken });

            const userColor = generateUserColor();
            const userData = {
                ...response.data,
                color: userColor,
                loginTime: new Date().toISOString()
            };

            localStorage.setItem('currentUser', JSON.stringify(userData));
            localStorage.setItem('user', JSON.stringify(response.data));

            return { success: true, user: userData };
        } catch (error) {
            console.error('Google login failed', error);
            const errorMessage = error.response?.data?.error || 'Google authentication failed.';
            throw new Error(errorMessage);
        }
    },

    // ── Profile API ───────────────────────────────────────

    getProfile: async () => {
        try {
            const response = await api.get('/api/profile');
            return response.data;
        } catch (error) {
            console.error('Get profile failed', error);
            const errorMessage = error.response?.data?.error || 'Failed to load profile.';
            throw new Error(errorMessage);
        }
    },

    updateProfile: async (data) => {
        try {
            const response = await api.put('/api/profile', data);
            // Update local storage with new username if changed
            const currentUser = JSON.parse(localStorage.getItem('currentUser') || '{}');
            const updatedUser = { ...currentUser, ...response.data };
            localStorage.setItem('currentUser', JSON.stringify(updatedUser));
            localStorage.setItem('user', JSON.stringify(response.data));
            return response.data;
        } catch (error) {
            console.error('Update profile failed', error);
            const errorMessage = error.response?.data?.error || 'Profile update failed.';
            throw new Error(errorMessage);
        }
    },

    sendLinkEmailOtp: async (email) => {
        try {
            const response = await api.post('/api/profile/link-email/send', { email });
            return { success: true, message: response.data.message };
        } catch (error) {
            const errorMessage = error.response?.data?.error || 'Failed to send OTP.';
            throw new Error(errorMessage);
        }
    },

    verifyLinkEmail: async (email, otp) => {
        try {
            const response = await api.post('/api/profile/link-email/verify', { email, otp });
            return response.data;
        } catch (error) {
            const errorMessage = error.response?.data?.error || 'Email linking failed.';
            throw new Error(errorMessage);
        }
    },

    sendLinkPhoneOtp: async (phone) => {
        try {
            const response = await api.post('/api/profile/link-phone/send', { phone });
            return { success: true, message: response.data.message };
        } catch (error) {
            const errorMessage = error.response?.data?.error || 'Failed to send OTP.';
            throw new Error(errorMessage);
        }
    },

    verifyLinkPhone: async (phone, otp) => {
        try {
            const response = await api.post('/api/profile/link-phone/verify', { phone, otp });
            return response.data;
        } catch (error) {
            const errorMessage = error.response?.data?.error || 'Phone linking failed.';
            throw new Error(errorMessage);
        }
    },

    linkGoogle: async (idToken) => {
        try {
            const response = await api.post('/api/profile/link-google', { idToken });
            return response.data;
        } catch (error) {
            const errorMessage = error.response?.data?.error || 'Google linking failed.';
            throw new Error(errorMessage);
        }
    },

    unlinkEmail: async () => {
        try {
            const response = await api.post('/api/profile/unlink-email');
            return response.data;
        } catch (error) {
            const errorMessage = error.response?.data?.error || 'Cannot unlink email.';
            throw new Error(errorMessage);
        }
    },

    unlinkPhone: async () => {
        try {
            const response = await api.post('/api/profile/unlink-phone');
            return response.data;
        } catch (error) {
            const errorMessage = error.response?.data?.error || 'Cannot unlink phone.';
            throw new Error(errorMessage);
        }
    },

    unlinkGoogle: async () => {
        try {
            const response = await api.post('/api/profile/unlink-google');
            return response.data;
        } catch (error) {
            const errorMessage = error.response?.data?.error || 'Cannot unlink Google.';
            throw new Error(errorMessage);
        }
    }
}


















