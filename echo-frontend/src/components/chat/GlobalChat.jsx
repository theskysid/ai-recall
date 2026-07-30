import React from 'react';

const GlobalChat = ({ chat, ui, layout }) => {
    const {
        messages = [],
        onlineUsers,
        message = '',
        setMessage,
        handleTyping,
        sendMessage,
        emojis = [],
        addEmoji,
        showEmojiPicker,
        setShowEmojiPicker,
        isTyping,
        formatTime,
        messagesEndRef
    } = chat || {};

    const { username, currentUser } = ui || {};
    const { mobile, onBack, onClose } = layout || {};

    const activeUsername = username || currentUser;

    return (
        <div className={`chat-panel-card global-chat-panel ${mobile ? 'mobile-panel' : ''}`}>
            <div className="chat-header">
                {mobile ? (
                    <div className="mobile-header-left">
                        {onBack && (
                            <button
                                type="button"
                                onClick={onBack}
                                className="mobile-back-button"
                                aria-label="Back to contacts list"
                            >
                                ←
                            </button>
                        )}
                        <div>
                            <h2 className="chat-header-title">
                                🌐 Global Chat
                                <span className="online-badge-count">
                                    {onlineUsers ? onlineUsers.size : 0} Online
                                </span>
                            </h2>
                            <p className="chat-header-subtitle">Welcome, {activeUsername}!</p>
                        </div>
                    </div>
                ) : (
                    <>
                        <div>
                            <h2 className="chat-header-title">
                                🌐 Global Chat
                                <span className="online-badge-count">
                                    {onlineUsers ? onlineUsers.size : 0} Online
                                </span>
                            </h2>
                            <p className="chat-header-subtitle">Welcome, {activeUsername}!</p>
                        </div>
                        {onClose && (
                            <button
                                onClick={onClose}
                                className="panel-close-btn"
                                title="Hide Global Chat to make room for 3 private chats"
                            >
                                ✕
                            </button>
                        )}
                    </>
                )}
            </div>

            <div className="messages-container">
                {messages.map((msg) => (
                    <div key={msg.id} className={`message ${msg.type.toLowerCase()}`}>
                        {msg.type === 'JOIN' && (
                            <div className="system-message">
                                {msg.sender} joined the chat
                            </div>
                        )}
                        {msg.type === 'LEAVE' && (
                            <div className="system-message">
                                {msg.sender} left the chat
                            </div>
                        )}
                        {msg.type === 'CHAT' && (
                            <div className={`chat-message ${msg.sender === activeUsername ? 'own-message' : ''}`}>
                                <div className="message-info">
                                    <span className="sender" style={{ color: msg.color || '#007bff' }}>
                                        {msg.sender}
                                    </span>
                                    <span className="time">{formatTime(msg.timestamp)}</span>
                                </div>
                                <div className="message-text">{msg.content}</div>
                            </div>
                        )}
                    </div>
                ))}

                {isTyping && isTyping !== activeUsername && (
                    <div className="typing-indicator">
                        {isTyping} is typing...
                    </div>
                )}

                <div ref={messagesEndRef} />
            </div>

            <div className="input-area">
                {showEmojiPicker && (
                    <div className="emoji-picker">
                        {emojis.map(emoji => (
                            <button key={emoji} type="button" onClick={() => addEmoji(emoji)}>
                                {emoji}
                            </button>
                        ))}
                    </div>
                )}

                <form onSubmit={sendMessage} className="message-form">
                    <button
                        type="button"
                        onClick={() => setShowEmojiPicker(!showEmojiPicker)}
                        className="emoji-btn"
                    >
                        😀
                    </button>
                    <input
                        type="text"
                        placeholder="Type a message to global chat..."
                        value={message}
                        onChange={handleTyping || (setMessage ? (e) => setMessage(e.target.value) : undefined)}
                        className="message-input"
                        maxLength={500}
                    />
                    <button type="submit" disabled={!message.trim()} className="send-btn">
                        Send
                    </button>
                </form>
            </div>
        </div>
    );
};

export default GlobalChat;
