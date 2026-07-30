import React, { useState, useEffect, useRef, useCallback } from 'react';
import { channelService } from '../services/channelService';
import '../styles/DirectMessageChat.css';
import '../styles/Channels.css';

const ChannelChat = ({
    currentUser,
    channel,
    subscribeToChannel,
    sendChannelMessage,
    isConnected,
    onLeave,
    onClose,
    onBack,
    isEmbedded = true
}) => {
    const channelId = channel?.id;

    const [messages, setMessages] = useState([]);
    const [messageInput, setMessageInput] = useState('');
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState('');
    const [isLeaving, setIsLeaving] = useState(false);

    const messagesEndRef = useRef(null);
    const messageIdsRef = useRef(new Set());
    const isInitialScrollRef = useRef(true);

    const scrollToBottom = (behavior = 'smooth') => {
        messagesEndRef.current?.scrollIntoView({ behavior });
    };

    useEffect(() => {
        if (isInitialScrollRef.current && messages.length > 0) {
            isInitialScrollRef.current = false;
            scrollToBottom('auto');
            const timer = setTimeout(() => scrollToBottom('auto'), 50);
            return () => clearTimeout(timer);
        } else {
            scrollToBottom('smooth');
        }
    }, [messages]);

    // Load history whenever the active channel changes.
    useEffect(() => {
        let isMounted = true;
        isInitialScrollRef.current = true;
        setIsLoading(true);
        setError('');
        setMessages([]);
        messageIdsRef.current.clear();

        if (channelId == null) return;

        (async () => {
            try {
                const history = await channelService.getMessages(channelId);
                if (!isMounted) return;
                const list = Array.isArray(history) ? history : [];
                const sorted = [...list].sort((a, b) => new Date(a.timestamp) - new Date(b.timestamp));
                sorted.forEach((msg) => {
                    if (msg.id != null) messageIdsRef.current.add(msg.id);
                });
                setMessages(sorted);
            } catch (err) {
                console.error('Error loading channel history:', err);
                if (isMounted) setError(err.response?.data?.error || err.message || 'Could not load channel');
            } finally {
                if (isMounted) setIsLoading(false);
            }
        })();

        return () => {
            isMounted = false;
        };
    }, [channelId]);

    const handleIncoming = useCallback((incoming) => {
        if (!incoming || incoming.type !== 'CHAT') return;
        const id = incoming.id;
        if (id != null && messageIdsRef.current.has(id)) return;
        if (id != null) messageIdsRef.current.add(id);
        setMessages((prev) => [...prev, incoming]);
    }, []);

    // Subscribe to the active channel; unsubscribe on switch/unmount to avoid cross-talk.
    useEffect(() => {
        if (channelId == null || !isConnected || !subscribeToChannel) return;
        const subscription = subscribeToChannel(channelId, handleIncoming);
        return () => {
            if (subscription) {
                try {
                    subscription.unsubscribe();
                } catch (err) {
                    console.error('Error unsubscribing from channel:', err);
                }
            }
        };
    }, [channelId, isConnected, subscribeToChannel, handleIncoming]);

    const sendMessage = (e) => {
        e.preventDefault();
        const content = messageInput.trim();
        if (!content || channelId == null) return;
        sendChannelMessage(channelId, content);
        setMessageInput('');
    };

    const handleLeave = async () => {
        if (channelId == null || isLeaving) return;
        if (!window.confirm(`Leave #${channel?.name}?`)) return;
        setIsLeaving(true);
        try {
            await onLeave(channelId);
        } finally {
            setIsLeaving(false);
        }
    };

    const formatTime = (timestamp) => {
        if (!timestamp) return '';
        let str = typeof timestamp === 'string' ? timestamp : timestamp.toString();
        if (typeof str === 'string' && !str.endsWith('Z') && !str.includes('+') && !str.includes('GMT')) {
            str += 'Z';
        }
        const dateObj = new Date(str);
        if (isNaN(dateObj.getTime())) return '';
        return dateObj.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    };

    return (
        <div className={`dm-chat-window ${isEmbedded ? 'embedded' : ''}`}>
            <div className="dm-chat-header">
                <div className="dm-recipient-info">
                    {onBack && (
                        <button type="button" onClick={onBack} className="mobile-back-button dm-back-btn" aria-label="Back">
                            ←
                        </button>
                    )}
                    <div className="dm-avatar channel-header-avatar">#</div>
                    <div className="dm-header-titles">
                        <h3>
                            {channel?.name}
                            <span className="channel-member-badge">👥 {channel?.memberCount ?? ''}</span>
                        </h3>
                    </div>
                </div>

                <div className="dm-header-actions">
                    <button
                        onClick={handleLeave}
                        disabled={isLeaving}
                        className="channel-leave-btn"
                        title="Leave this channel"
                    >
                        {isLeaving ? 'Leaving…' : 'Leave'}
                    </button>
                    {onClose && !onBack && (
                        <button onClick={onClose} className="dm-close-btn" title="Close">✕</button>
                    )}
                </div>
            </div>

            <div className="dm-messages-container">
                {channel?.inviteCode && (
                    <div className="channel-invite-banner">
                        🔗 Invite code: <strong>{channel.inviteCode}</strong>
                    </div>
                )}

                {isLoading ? (
                    <div className="dm-loading">⏳ Loading channel…</div>
                ) : error ? (
                    <div className="dm-loading" style={{ color: 'var(--color-danger)' }}>⚠️ {error}</div>
                ) : messages.length === 0 ? (
                    <div className="dm-no-messages">
                        <p>No messages yet.</p>
                        <p style={{ fontSize: '12px', color: '#90a4ae' }}>Be the first to post in #{channel?.name}!</p>
                    </div>
                ) : (
                    messages.map((msg, index) => {
                        const isOwn = msg.sender === currentUser;
                        return (
                            <div key={msg.id || index} className={`dm-message ${isOwn ? 'own-message' : 'received-message'}`}>
                                <div className="dm-message-header">
                                    <span className="dm-sender-name" style={{ color: msg.color || undefined }}>
                                        {isOwn ? 'You' : msg.sender}
                                    </span>
                                    <span className="dm-timestamp">{formatTime(msg.timestamp)}</span>
                                </div>
                                <div className="dm-message-content">{msg.content}</div>
                            </div>
                        );
                    })
                )}
                <div ref={messagesEndRef} />
            </div>

            <div className="dm-input-container">
                <form onSubmit={sendMessage} className="dm-form">
                    <input
                        type="text"
                        placeholder={`Message #${channel?.name || ''}...`}
                        value={messageInput}
                        onChange={(e) => setMessageInput(e.target.value)}
                        className="dm-input"
                        maxLength={2000}
                    />
                    <button type="submit" disabled={!messageInput.trim()} className="dm-send-btn" title="Send">
                        Send
                    </button>
                </form>
            </div>
        </div>
    );
};

export default ChannelChat;
