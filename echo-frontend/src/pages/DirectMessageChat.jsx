import React, { useState, useEffect, useRef, useCallback } from 'react';
import { conversationService } from '../services/conversationService';
import '../styles/DirectMessageChat.css';

const DirectMessageChat = ({
    currentUser,
    recipientUsername,
    stompClient,
    onClose,
    onBack,
    registerDmHandler,
    unregisterDmHandler,
    positionOffset = 380,
    isEmbedded = true,
    isMobile = false
}) => {
    const [conversation, setConversation] = useState(null);
    const [messages, setMessages] = useState([]);
    const [messageInput, setMessageInput] = useState('');
    const [isLoading, setIsLoading] = useState(true);
    const [isTyping, setIsTyping] = useState(false);
    const [retentionPolicy, setRetentionPolicy] = useState('ONE_DAY');
    const [error, setError] = useState('');
    const [currentTime, setCurrentTime] = useState(() => new Date());

    const messagesEndRef = useRef(null);
    const messageIdsRef = useRef(new Set());
    const typingTimeoutRef = useRef(null);
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
    }, [messages, isTyping]);

    useEffect(() => {
        const timer = setInterval(() => setCurrentTime(new Date()), 30000);
        return () => clearInterval(timer);
    }, []);

    const handleIncomingDm = useCallback((incomingMsg) => {
        if (incomingMsg.content === 'TYPING') {
            if (incomingMsg.senderUsername === recipientUsername) {
                setIsTyping(true);
                clearTimeout(typingTimeoutRef.current);
                typingTimeoutRef.current = setTimeout(() => {
                    setIsTyping(false);
                }, 2000);
            }
            return;
        }

        if (typeof incomingMsg.content === 'string' && incomingMsg.content.startsWith('RETENTION_POLICY_UPDATE:')) {
            const updatedPolicy = incomingMsg.content.split(':')[1];
            if (updatedPolicy) {
                setRetentionPolicy(updatedPolicy);
                setConversation((prev) => (prev ? { ...prev, retentionPolicy: updatedPolicy } : prev));
            }
            return;
        }

        const messageId = incomingMsg.id;
        const isRelevant =
            (incomingMsg.senderUsername === currentUser && incomingMsg.conversationId === conversation?.id) ||
            (incomingMsg.senderUsername === recipientUsername && incomingMsg.conversationId === conversation?.id);

        if (isRelevant && messageId && !messageIdsRef.current.has(messageId)) {
            messageIdsRef.current.add(messageId);
            setMessages((prev) => [...prev, incomingMsg]);
            setIsTyping(false);
        }
    }, [conversation?.id, currentUser, recipientUsername]);

    useEffect(() => {
        let isMounted = true;
        isInitialScrollRef.current = true;

        const initConversation = async () => {
            setIsLoading(true);
            setError('');
            try {
                const conv = await conversationService.getOrCreateConversationWith(recipientUsername);
                if (!isMounted) return;

                setConversation(conv);
                setRetentionPolicy(conv.retentionPolicy);

                const historyPage = await conversationService.getMessages(conv.id, 0, 50);
                if (!isMounted) return;

                const historyList = historyPage.content || historyPage || [];
                // Sort chronologically ascending for display
                const sortedHistory = [...historyList].sort(
                    (a, b) => new Date(a.timestamp) - new Date(b.timestamp)
                );

                messageIdsRef.current.clear();
                sortedHistory.forEach((msg) => {
                    if (msg.id) messageIdsRef.current.add(msg.id);
                });

                setMessages(sortedHistory);
            } catch (err) {
                console.error('Error initializing DM conversation:', err);
                if (isMounted) {
                    setError(err.response?.data?.error || err.message || 'Could not open conversation');
                }
            } finally {
                if (isMounted) {
                    setIsLoading(false);
                }
            }
        };

        initConversation();

        return () => {
            isMounted = false;
            clearTimeout(typingTimeoutRef.current);
        };
    }, [currentUser, recipientUsername]);

    useEffect(() => {
        if (conversation && registerDmHandler) {
            registerDmHandler(recipientUsername, handleIncomingDm);
        }
        return () => {
            if (unregisterDmHandler) {
                unregisterDmHandler(recipientUsername);
            }
        };
    }, [conversation, handleIncomingDm, recipientUsername, registerDmHandler, unregisterDmHandler]);

    const handleRetentionChange = async (e) => {
        const newPolicy = e.target.value;
        if (!conversation) return;

        try {
            const updated = await conversationService.updateRetentionPolicy(conversation.id, newPolicy);
            setRetentionPolicy(updated.retentionPolicy);
            setConversation(updated);
        } catch (err) {
            console.error('Failed to update retention policy:', err);
            alert(err.response?.data?.error || err.message || 'Could not update retention policy');
        }
    };

    const handleTypingInput = (e) => {
        setMessageInput(e.target.value);

        if (stompClient.current && stompClient.current.connected && conversation) {
            try {
                stompClient.current.send(
                    '/app/dm.typing',
                    {},
                    JSON.stringify({
                        conversationId: conversation.id,
                        senderUsername: currentUser,
                        recipientUsername: recipientUsername,
                        type: 'TYPING'
                    })
                );
            } catch (err) {
                console.error('Error sending typing notification:', err);
            }
        }
    };

    const sendDm = (e) => {
        e.preventDefault();
        if (!messageInput.trim() || !conversation || !stompClient.current?.connected) return;

        const content = messageInput.trim();
        const payload = {
            conversationId: conversation.id,
            senderUsername: currentUser,
            recipientUsername: recipientUsername,
            content: content,
            type: 'MESSAGE'
        };

        try {
            stompClient.current.send('/app/dm.sendMessage', {}, JSON.stringify(payload));
            setMessageInput('');
        } catch (err) {
            console.error('Failed to send direct message:', err);
        }
    };

    const normalizeTimestamp = (ts) => {
        if (!ts) return '';
        let str = typeof ts === 'string' ? ts : ts.toString();
        if (typeof str === 'string' && !str.endsWith('Z') && !str.includes('+') && !str.includes('GMT')) {
            str += 'Z';
        }
        return str;
    };

    const formatTime = (timestamp) => {
        if (!timestamp) return '';
        const normalized = normalizeTimestamp(timestamp);
        const dateObj = new Date(normalized);
        if (isNaN(dateObj.getTime())) return '';
        return dateObj.toLocaleTimeString([], {
            hour: '2-digit',
            minute: '2-digit'
        });
    };

    const formatExpiryDelta = (expiresAt) => {
        if (!expiresAt) return '';
        const normalized = normalizeTimestamp(expiresAt);
        const diffMs = new Date(normalized) - new Date();
        if (diffMs <= 0) return 'Expired';
        const hours = Math.floor(diffMs / (1000 * 60 * 60));
        const mins = Math.floor((diffMs % (1000 * 60 * 60)) / (1000 * 60));
        if (hours > 24) {
            return `${Math.round(hours / 24)}d left`;
        }
        if (hours > 0) {
            return `${hours}h ${mins}m left`;
        }
        return `${mins}m left`;
    };

    if (isLoading) {
        return (
            <div className={`dm-chat-window ${isEmbedded ? 'embedded' : ''}`} style={isEmbedded ? {} : { right: `${positionOffset}px` }}>
            <div className="dm-chat-header">
                <div className="dm-recipient-info">
                    {onBack && (
                        <button type="button" onClick={onBack} className="mobile-back-button dm-back-btn" aria-label="Back to contacts list">
                            ←
                        </button>
                    )}
                    <div className="dm-avatar">💬</div>
                    <h3>{recipientUsername}</h3>
                </div>
                <button onClick={onClose} className="dm-close-btn">✕</button>
            </div>
                <div className="dm-loading">⏳ Opening secure DM...</div>
            </div>
        );
    }

    if (error) {
        return (
            <div className={`dm-chat-window ${isEmbedded ? 'embedded' : ''}`} style={isEmbedded ? {} : { right: `${positionOffset}px` }}>
            <div className="dm-chat-header">
                <div className="dm-recipient-info">
                    {onBack && (
                        <button type="button" onClick={onBack} className="mobile-back-button dm-back-btn" aria-label="Back to contacts list">
                            ←
                        </button>
                    )}
                    <div className="dm-avatar">✕</div>
                    <h3>{recipientUsername}</h3>
                </div>
                <button onClick={onClose} className="dm-close-btn">✕</button>
            </div>
                <div className="dm-loading" style={{ color: '#d32f2f' }}>
                    <p>⚠️ {error}</p>
                    <button
                        onClick={onClose}
                        style={{ padding: '6px 14px', borderRadius: '16px', border: 'none', background: '#e0e0e0', cursor: 'pointer' }}
                    >
                        Close
                    </button>
                </div>
            </div>
        );
    }

    return (
        <div className={`dm-chat-window ${isEmbedded ? 'embedded' : ''}`} style={isEmbedded ? {} : { right: `${positionOffset}px` }}>
            <div className="dm-chat-header">
                <div className="dm-recipient-info">
                    {onBack && (
                        <button type="button" onClick={onBack} className="mobile-back-button dm-back-btn" aria-label="Back to contacts list">
                            ←
                        </button>
                    )}
                    <div className="dm-avatar-wrapper">
                        <div className="dm-avatar">
                            {recipientUsername.charAt(0).toUpperCase()}
                        </div>
                        <div className={`dm-online-badge ${conversation?.otherUserOnline ? 'online' : 'offline'}`} />
                    </div>
                    <div className="dm-header-titles">
                        <h3>
                            {recipientUsername}
                            <span className="dm-badge-ephemeral">⚡ DM</span>
                        </h3>
                    </div>
                </div>

                <div className="dm-header-actions">
                    <select
                        className="retention-select"
                        value={retentionPolicy}
                        onChange={handleRetentionChange}
                        title="Ephemeral Message Retention Policy"
                    >
                        <option value="SIX_HOURS">⏳ 6 Hours</option>
                        <option value="ONE_DAY">🕒 1 Day</option>
                        <option value="SEVEN_DAYS">📅 7 Days</option>
                    </select>
                    {!onBack && <button onClick={onClose} className="dm-close-btn" title="Close Chat">✕</button>}
                </div>
            </div>

            <div className="dm-messages-container">
                <div className="dm-ephemeral-banner">
                    🔒 Messages auto-delete after{' '}
                    <strong>
                        {retentionPolicy === 'SIX_HOURS' ? '6 hours' : retentionPolicy === 'ONE_DAY' ? '1 day' : '7 days'}
                    </strong>
                    .
                </div>

                {messages.length === 0 ? (
                    <div className="dm-no-messages">
                        <p>No messages yet.</p>
                        <p style={{ fontSize: '12px', color: '#90a4ae' }}>Say hello to start this ephemeral conversation!</p>
                    </div>
                ) : (
                    messages.map((msg, index) => {
                        const isOwn = msg.senderUsername === currentUser;
                        return (
                            <div key={msg.id || index} className={`dm-message ${isOwn ? 'own-message' : 'received-message'}`}>
                                <div className="dm-message-header">
                                    <span className="dm-sender-name">{isOwn ? 'You' : msg.senderUsername}</span>
                                    <span className="dm-timestamp">{formatTime(msg.timestamp)}</span>
                                </div>
                                <div className="dm-message-content">{msg.content}</div>
                                {index === 0 && msg.expiresAt && (
                                    <div className="dm-message-footer" title={`Top message expires at ${new Date(normalizeTimestamp(msg.expiresAt)).toLocaleString()}`}>
                                        ⏳ {formatExpiryDelta(msg.expiresAt)}
                                    </div>
                                )}
                            </div>
                        );
                    })
                )}

                {isTyping && (
                    <div className="dm-typing-indicator">
                        ✏️ {recipientUsername} is typing...
                    </div>
                )}
                <div ref={messagesEndRef} />
            </div>

            <div className="dm-input-container">
                <form onSubmit={sendDm} className="dm-form">
                    <input
                        type="text"
                        placeholder={`Direct message ${recipientUsername}...`}
                        value={messageInput}
                        onChange={handleTypingInput}
                        className="dm-input"
                        maxLength={500}
                    />
                    <button type="submit" disabled={!messageInput.trim()} className="dm-send-btn" title="Send Ephemeral Message">
                        Send
                    </button>
                </form>
            </div>
        </div>
    );
};

export default DirectMessageChat;
