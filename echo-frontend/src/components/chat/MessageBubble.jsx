const MessageBubble = ({ message, isOwn, formatTime }) => {
    return (
        <div className={`msg-bubble ${isOwn ? 'msg-bubble--own' : 'msg-bubble--other'}`}>
            <div className="msg-bubble-meta">
                <span
                    className="msg-bubble-sender"
                    style={{ color: message.color || 'var(--primary-light)' }}
                >
                    {isOwn ? 'You' : message.sender}
                </span>
                <span className="msg-bubble-time">{formatTime(message.timestamp)}</span>
            </div>
            <div className="msg-bubble-content">{message.content}</div>
        </div>
    );
};

export default MessageBubble;
