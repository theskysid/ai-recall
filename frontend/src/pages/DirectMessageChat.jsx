import React, { useState, useEffect, useRef, useCallback } from 'react';
import { conversationService } from '../services/conversationService';
import Icon from '../components/ui/Icon';
import { hueClass } from '../utils/avatarHue';
import { mergeEcho } from '../utils/mergeEcho';
import { groupThread, formatTime, parseTs } from '../utils/thread';
import '../styles/ChannelPage.css';
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
    isEmbedded = true
}) => {
    const [conversation, setConversation] = useState(null);
    const [messages, setMessages] = useState([]);
    const [messageInput, setMessageInput] = useState('');
    const [isLoading, setIsLoading] = useState(true);
    const [isTyping, setIsTyping] = useState(false);
    const [retentionPolicy, setRetentionPolicy] = useState('ONE_DAY');
    const [error, setError] = useState('');
    // ticks every 30s purely to re-render relative timestamps
    const [, setCurrentTime] = useState(() => new Date());

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
            setMessages((prev) => mergeEcho(prev, incomingMsg, 'senderUsername'));
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
            // Render before the round-trip; handleIncomingDm swaps in the server copy.
            // ponytail: a send that never echoes stays dimmed forever — add a timeout
            // and a retry affordance if that turns out to happen in practice.
            setMessages((prev) => [...prev, {
                ...payload,
                id: `pending-${Date.now()}`,
                timestamp: new Date().toISOString(),
                pending: true
            }]);
        } catch (err) {
            console.error('Failed to send direct message:', err);
        }
    };

    /* How long the oldest message on screen has left. Shown once, on the
       first message, because that is the one about to go. */
    const expiryDelta = (expiresAt) => {
        const d = parseTs(expiresAt);
        if (!d) return '';
        const diffMs = d - new Date();
        if (diffMs <= 0) return 'Expired';
        const hours = Math.floor(diffMs / 3600000);
        const mins = Math.floor((diffMs % 3600000) / 60000);
        if (hours > 24) return `${Math.round(hours / 24)}d left`;
        if (hours > 0) return `${hours}h ${mins}m left`;
        return `${mins}m left`;
    };

    const keepLabel =
        retentionPolicy === 'SIX_HOURS' ? '6 hours' : retentionPolicy === 'SEVEN_DAYS' ? '7 days' : '1 day';

    const renderStream = () => {
        if (isLoading) {
            return (
                <div className="ch-state">
                    <span className="ch-state-mark"><Icon name="lock" size={15} /></span>
                    <h4>Opening your chat with {recipientUsername}</h4>
                    <p>Reading the messages that have not expired yet.</p>
                </div>
            );
        }

        if (error) {
            return (
                <div className="ch-state is-bad">
                    <span className="ch-state-mark"><Icon name="alert" size={15} /></span>
                    <h4>This conversation would not open</h4>
                    <p>{error}</p>
                </div>
            );
        }

        if (messages.length === 0) {
            return (
                <div className="ch-state">
                    <span className="ch-state-mark"><Icon name="message" size={15} /></span>
                    <h4>Nothing here yet</h4>
                    <p>Say hello. Messages in this conversation clear themselves after {keepLabel}.</p>
                </div>
            );
        }

        return groupThread(messages, 'senderUsername').map(({ msg, day, isLead }, index) => {
            const isOwn = msg.senderUsername === currentUser;
            const who = isOwn ? currentUser : msg.senderUsername;

            return (
                <React.Fragment key={msg.id || index}>
                    {day && (
                        <div className="ch-day">
                            <span>{day}</span>
                        </div>
                    )}
                    <div
                        className={`ch-msg ${isLead ? 'is-lead' : ''} ${isOwn ? 'is-own' : ''} ${msg.pending ? 'is-pending' : ''}`}
                    >
                        <div className="ch-msg-gutter">
                            {isLead ? (
                                <span className={`ch-msg-avatar ${hueClass(who)}`} aria-hidden="true">
                                    {who?.charAt(0) || '?'}
                                </span>
                            ) : (
                                <span className="ch-msg-tick">{formatTime(msg.timestamp)}</span>
                            )}
                        </div>
                        <div className="ch-msg-main">
                            {isLead && (
                                <div className="ch-msg-head">
                                    <span className="ch-msg-who">{isOwn ? 'You' : msg.senderUsername}</span>
                                    <span className="ch-msg-when">{formatTime(msg.timestamp)}</span>
                                    <span className="ch-msg-marks">
                                        {index === 0 && msg.expiresAt && (
                                            <span className="dm-expiry" title="When the oldest message on screen is deleted">
                                                <Icon name="clock" size={10} />
                                                {expiryDelta(msg.expiresAt)}
                                            </span>
                                        )}
                                    </span>
                                </div>
                            )}
                            <div className="ch-msg-body">{msg.content}</div>
                        </div>
                    </div>
                </React.Fragment>
            );
        });
    };

    return (
        <div
            className={`ch-page dm-panel ${isEmbedded ? 'ch-embedded' : ''}`}
            style={isEmbedded ? undefined : { right: `${positionOffset}px` }}
        >
            <header className="ch-header">
                <div className="ch-header-id">
                    {onBack && (
                        <button type="button" onClick={onBack} className="ch-back" aria-label="Back to contacts">
                            <Icon name="arrowLeft" size={15} />
                        </button>
                    )}
                    <span className="dm-who">
                        <span className={`dm-who-avatar ${hueClass(recipientUsername)}`} aria-hidden="true">
                            {recipientUsername.charAt(0).toUpperCase()}
                        </span>
                        <span
                            className={`dm-presence ${conversation?.otherUserOnline ? 'is-on' : ''}`}
                            title={conversation?.otherUserOnline ? 'Online now' : 'Offline'}
                        />
                    </span>
                    <h3 className="ch-name">{recipientUsername}</h3>
                    <span className="dm-keep">
                        <Icon name="clock" size={11} />
                        Clears after {keepLabel}
                    </span>
                </div>

                <div className="ch-header-actions">
                    <select
                        className="dm-keep-select"
                        value={retentionPolicy}
                        onChange={handleRetentionChange}
                        aria-label="How long messages are kept"
                    >
                        <option value="SIX_HOURS">Keep 6 hours</option>
                        <option value="ONE_DAY">Keep 1 day</option>
                        <option value="SEVEN_DAYS">Keep 7 days</option>
                    </select>
                    {onClose && !onBack && (
                        <button onClick={onClose} className="ch-btn ch-btn-icon" title="Close" aria-label="Close chat">
                            <Icon name="close" size={13} />
                        </button>
                    )}
                </div>
            </header>

            <div className="ch-thread">
                {renderStream()}
                {isTyping && (
                    <div className="dm-typing">
                        <span className="dm-typing-dots" aria-hidden="true">
                            <i /><i /><i />
                        </span>
                        {recipientUsername} is typing
                    </div>
                )}
                <div ref={messagesEndRef} />
            </div>

            <div className="ch-composer">
                <form onSubmit={sendDm} className="ch-composer-form">
                    <input
                        type="text"
                        placeholder={`Message ${recipientUsername}`}
                        value={messageInput}
                        onChange={handleTypingInput}
                        className="ch-composer-field"
                        maxLength={500}
                        disabled={!conversation}
                        aria-label={`Message ${recipientUsername}`}
                    />
                    <button
                        type="submit"
                        disabled={!messageInput.trim() || !conversation}
                        className="ch-send"
                        title="Send"
                        aria-label="Send"
                    >
                        <Icon name="send" size={14} />
                    </button>
                </form>
            </div>
        </div>
    );
};

export default DirectMessageChat;
