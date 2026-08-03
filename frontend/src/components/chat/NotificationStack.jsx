import React from 'react';

const NotificationStack = ({ notifications = [] }) => {
    if (!notifications || notifications.length === 0) {
        return null;
    }

    return (
        <div className="friend-notifications">
            {notifications.map((notification) => (
                <div
                    key={notification.id}
                    className={`friend-notification ${notification.tone === 'error' ? 'error' : ''}`}
                >
                    {notification.messageText}
                </div>
            ))}
        </div>
    );
};

export default NotificationStack;
