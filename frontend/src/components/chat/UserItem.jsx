import Avatar from '../ui/Avatar';

const UserItem = ({ user, currentUser, userColor, unreadCount, onOpenChat }) => {
    const isCurrentUser = user === currentUser;

    return (
        <div
            className={`user-item ${isCurrentUser ? 'user-item--self' : ''}`}
            onClick={() => !isCurrentUser && onOpenChat(user)}
            role={isCurrentUser ? undefined : 'button'}
            tabIndex={isCurrentUser ? undefined : 0}
        >
            <Avatar
                username={user}
                color={isCurrentUser ? userColor : undefined}
                size={34}
                showOnline
                isOnline
            />
            <div className="user-item-info">
                <span className="user-item-name">{user}</span>
                {isCurrentUser && <span className="user-item-you">You</span>}
            </div>
            {unreadCount > 0 && (
                <span className="user-item-badge">{unreadCount}</span>
            )}
        </div>
    );
};

export default UserItem;
