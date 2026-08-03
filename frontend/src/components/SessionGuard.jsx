import { useState, useEffect } from 'react';
import { authService } from '../services/authService';
import LoadingScreen from './LoadingScreen';

const SessionGuard = ({ children }) => {
    const [isReady, setIsReady] = useState(false);

    useEffect(() => {
        let cancelled = false;

        const initSession = async () => {
            // Users without saved login / JWT get instant access to the site (no loader)
            if (!authService.isAuthenticated()) {
                setIsReady(true);
                return;
            }

            const startTime = Date.now();
            const MIN_LOADING_TIME = 2800; // ~3 seconds loader duration for session & chat restoration

            try {
                // Validate stored JWT against backend and refresh user state
                await authService.fetchCurrentUser();

                const elapsedTime = Date.now() - startTime;
                const remainingTime = Math.max(0, MIN_LOADING_TIME - elapsedTime);

                setTimeout(() => {
                    if (!cancelled) {
                        setIsReady(true);
                    }
                }, remainingTime);
            } catch (error) {
                // Token is invalid / expired — clean up and send to login
                console.warn('Session validation failed, redirecting to login:', error);
                await authService.logout();
                if (!cancelled) {
                    window.location.href = '/login';
                }
            }
        };

        initSession();

        return () => {
            cancelled = true;
        };
    }, []);

    if (!isReady) {
        return <LoadingScreen />;
    }

    return children;
};

export default SessionGuard;
