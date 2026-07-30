const ChatHeader = ({ username }) => {
    return (
        <div className="chat-top-bar">
            <div className="chat-top-info">
                <h2 className="chat-top-title">
                    <span className="chat-top-icon">💬</span>
                    General Chat
                </h2>
                <p className="chat-top-subtitle">Public room • Everyone can see messages</p>
            </div>
            <div className="chat-top-user">
                <span className="chat-top-welcome">Signed in as</span>
                <span className="chat-top-name">{username}</span>
            </div>
        </div>
    );
};

export default ChatHeader;
