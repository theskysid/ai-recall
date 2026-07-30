import { useState, useRef, useCallback, useEffect } from 'react';

const useNotifications = () => {
    const [notifications, setNotifications] = useState([]);
    const notificationTimeoutsRef = useRef(new Set());

    const pushNotification = useCallback((messageText, tone = 'info') => {
        const id = Date.now() + Math.random();
        setNotifications((prev) => [...prev, { id, messageText, tone }]);

        const timeoutId = window.setTimeout(() => {
            notificationTimeoutsRef.current.delete(timeoutId);
            setNotifications((prev) => prev.filter((notification) => notification.id !== id));
        }, 4000);

        notificationTimeoutsRef.current.add(timeoutId);
    }, []);

    useEffect(() => {
        const notificationTimeouts = notificationTimeoutsRef.current;
        return () => {
            notificationTimeouts.forEach((timeoutId) => clearTimeout(timeoutId));
            notificationTimeouts.clear();
        };
    }, []);

    return {
        notifications,
        pushNotification
    };
};

export default useNotifications;
