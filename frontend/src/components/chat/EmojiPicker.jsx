const emojis = ['😀', '😂', '😍', '🤔', '👍', '❤️', '🎉', '🔥', '😎', '⭐', '✨', '💯', '🙌', '😢', '🤣', '💪'];

const EmojiPicker = ({ onSelect, onClose }) => {
    return (
        <div className="emoji-picker">
            <div className="emoji-picker-header">
                <span className="emoji-picker-title">Emoji</span>
                <button className="emoji-picker-close" onClick={onClose}>✕</button>
            </div>
            <div className="emoji-picker-grid">
                {emojis.map(emoji => (
                    <button
                        key={emoji}
                        className="emoji-picker-item"
                        onClick={() => onSelect(emoji)}
                        type="button"
                    >
                        {emoji}
                    </button>
                ))}
            </div>
        </div>
    );
};

export default EmojiPicker;
