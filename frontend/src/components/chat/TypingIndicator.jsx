const TypingIndicator = ({ username }) => {
    if (!username) return null;

    return (
        <div className="typing-indicator">
            <div className="typing-dots">
                <span className="typing-dot" />
                <span className="typing-dot" />
                <span className="typing-dot" />
            </div>
            <span className="typing-text">{username} is typing</span>
        </div>
    );
};

export default TypingIndicator;
