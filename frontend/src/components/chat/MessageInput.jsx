import { useState } from 'react';
import EmojiPicker from './EmojiPicker';

const MessageInput = ({ message, onMessageChange, onSendMessage }) => {
    const [showEmoji, setShowEmoji] = useState(false);

    const handleEmojiSelect = (emoji) => {
        onMessageChange({ target: { value: message + emoji } });
        setShowEmoji(false);
    };

    const handleSubmit = (e) => {
        e.preventDefault();
        onSendMessage(e);
        setShowEmoji(false);
    };

    return (
        <div className="msg-input-area">
            {showEmoji && (
                <EmojiPicker
                    onSelect={handleEmojiSelect}
                    onClose={() => setShowEmoji(false)}
                />
            )}

            <form onSubmit={handleSubmit} className="msg-input-form">
                <button
                    type="button"
                    className="msg-emoji-btn"
                    onClick={() => setShowEmoji(!showEmoji)}
                >
                    😊
                </button>

                <input
                    type="text"
                    placeholder="Type a message..."
                    value={message}
                    onChange={onMessageChange}
                    className="msg-input"
                    maxLength={500}
                />

                <button
                    type="submit"
                    disabled={!message.trim()}
                    className="msg-send-btn"
                >
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                        <line x1="22" y1="2" x2="11" y2="13" />
                        <polygon points="22 2 15 22 11 13 2 9 22 2" />
                    </svg>
                </button>
            </form>
        </div>
    );
};

export default MessageInput;
