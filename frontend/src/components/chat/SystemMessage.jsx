const SystemMessage = ({ message }) => {
    const isJoin = message.type === 'JOIN';

    return (
        <div className="sys-msg">
            <span className="sys-msg-icon">{isJoin ? '→' : '←'}</span>
            <span className="sys-msg-text">
                <strong>{message.sender}</strong> {isJoin ? 'joined the chat' : 'left the chat'}
            </span>
        </div>
    );
};

export default SystemMessage;
