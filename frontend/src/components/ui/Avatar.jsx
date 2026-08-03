const Avatar = ({ username, color, size = 36, showOnline = false, isOnline = false }) => {
    const initial = username ? username.charAt(0).toUpperCase() : '?';

    return (
        <div
            className="avatar"
            style={{
                width: `${size}px`,
                height: `${size}px`,
                backgroundColor: color || 'var(--primary)',
                fontSize: `${size * 0.4}px`,
            }}
        >
            {initial}
            {showOnline && (
                <span className={`avatar-status ${isOnline ? 'online' : 'offline'}`} />
            )}
        </div>
    );
};

export default Avatar;
