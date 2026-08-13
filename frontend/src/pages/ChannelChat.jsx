import React, { useState, useEffect, useRef, useCallback } from 'react';
import { channelService } from '../services/channelService';
import { callService } from '../services/callService';
import LiveCallRoom from './LiveCallRoom';
import AskAiWidget from '../components/chat/AskAiWidget';
import MemoryPanel from '../components/chat/MemoryPanel';
import Icon from '../components/ui/Icon';
import { hueClass } from '../utils/avatarHue';
import '../styles/ChannelPage.css';

/* The backend sends naive timestamps; treat them as UTC. */
const parseTs = (timestamp) => {
    if (!timestamp) return null;
    let str = typeof timestamp === 'string' ? timestamp : timestamp.toString();
    if (typeof str === 'string' && !str.endsWith('Z') && !str.includes('+') && !str.includes('GMT')) {
        str += 'Z';
    }
    const d = new Date(str);
    return isNaN(d.getTime()) ? null : d;
};

const sameDay = (a, b) =>
    a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();

const dayLabel = (d) => {
    const today = new Date();
    const yesterday = new Date();
    yesterday.setDate(today.getDate() - 1);
    if (sameDay(d, today)) return 'Today';
    if (sameDay(d, yesterday)) return 'Yesterday';
    return d.toLocaleDateString([], { month: 'long', day: 'numeric' });
};

/* Messages from the same person inside this window read as one run. */
const RUN_MS = 5 * 60 * 1000;


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
    const [inCall, setInCall] = useState(false);
    const [callParticipants, setCallParticipants] = useState(0);
    const [copiedInvite, setCopiedInvite] = useState(false);

    // Leave any active call when switching channels.
    useEffect(() => {
        setInCall(false);
    }, [channelId]);

    // Poll so people sitting in the chat can see a call is running and join it.
    // LiveKit only knows this server-side, and nothing pushes it over STOMP —
    // a 10s poll of one small endpoint is cheaper than wiring up webhooks.
    useEffect(() => {
        if (!channelId || inCall) {
            setCallParticipants(0);
            return undefined;
        }
        let isMounted = true;
        const check = () => callService
            .getCallStatus(channelId)
            .then((status) => {
                if (isMounted) setCallParticipants(status?.participants || 0);
            })
            .catch(() => {}); // a failed poll just leaves the bar hidden

        check();
        const timer = setInterval(check, 10000);
        return () => {
            isMounted = false;
            clearInterval(timer);
        };
    }, [channelId, inCall]);

    const messagesEndRef = useRef(null);
    const messageIdsRef = useRef(new Set());
    const isInitialScrollRef = useRef(true);
    const copyTimerRef = useRef(null);

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

    useEffect(() => () => clearTimeout(copyTimerRef.current), []);

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

    /* navigator.clipboard is undefined on non-secure origins, which this app
       hits when it is served over plain http. Fall back so the control is
       never a dead button. */
    const copyInvite = async () => {
        const code = channel?.inviteCode;
        if (!code) return;
        try {
            if (navigator.clipboard?.writeText) {
                await navigator.clipboard.writeText(code);
            } else {
                const scratch = document.createElement('textarea');
                scratch.value = code;
                scratch.setAttribute('readonly', '');
                scratch.style.position = 'fixed';
                scratch.style.opacity = '0';
                document.body.appendChild(scratch);
                scratch.select();
                document.execCommand('copy');
                document.body.removeChild(scratch);
            }
            setCopiedInvite(true);
            clearTimeout(copyTimerRef.current);
            copyTimerRef.current = setTimeout(() => setCopiedInvite(false), 1800);
        } catch (err) {
            console.error('Could not copy invite code:', err);
        }
    };

    const formatTime = (timestamp) => {
        const d = parseTs(timestamp);
        return d ? d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '';
    };

    const renderThread = () => {
        if (isLoading) {
            return (
                <div className="ch-state">
                    <span className="ch-state-mark"><Icon name="archive" size={15} /></span>
                    <h4>Opening #{channel?.name}</h4>
                    <p>Reading everything the channel has on file.</p>
                </div>
            );
        }

        if (error) {
            return (
                <div className="ch-state is-bad">
                    <span className="ch-state-mark"><Icon name="alert" size={15} /></span>
                    <h4>This channel would not open</h4>
                    <p>{error}</p>
                </div>
            );
        }

        if (messages.length === 0) {
            return (
                <div className="ch-state">
                    <span className="ch-state-mark"><Icon name="message" size={15} /></span>
                    <h4>Nothing on the record yet</h4>
                    <p>Post the first message in #{channel?.name}. Everything said here is kept and indexed from now on.</p>
                </div>
            );
        }

        let lastDate = null;
        let lastSender = null;

        return messages.map((msg, index) => {
            const date = parseTs(msg.timestamp);
            const isOwn = msg.sender === currentUser;

            const startsDay = !!date && (!lastDate || !sameDay(date, lastDate));
            const isLead =
                startsDay ||
                msg.sender !== lastSender ||
                !date ||
                !lastDate ||
                date - lastDate > RUN_MS;

            const divider = startsDay ? (
                <div className="ch-day">
                    <span>{dayLabel(date)}</span>
                </div>
            ) : null;

            lastDate = date || lastDate;
            lastSender = msg.sender;

            return (
                <React.Fragment key={msg.id || index}>
                    {divider}
                    <div className={`ch-msg ${isLead ? 'is-lead' : ''} ${isOwn ? 'is-own' : ''}`}>
                        <div className="ch-msg-gutter">
                            {isLead ? (
                                <span
                                    className={`ch-msg-avatar ${hueClass(isOwn ? currentUser : msg.sender)}`}
                                    aria-hidden="true"
                                >
                                    {(isOwn ? currentUser : msg.sender)?.charAt(0) || '?'}
                                </span>
                            ) : (
                                <span className="ch-msg-tick">{formatTime(msg.timestamp)}</span>
                            )}
                        </div>
                        <div className="ch-msg-main">
                            {isLead && (
                                <div className="ch-msg-head">
                                    <span className="ch-msg-who">{isOwn ? 'You' : msg.sender}</span>
                                    <span className="ch-msg-when">{formatTime(msg.timestamp)}</span>
                                    {/* Slot for a per-message status marker. The backend does not
                                        send one yet, so it stays empty and collapses. */}
                                    <span className="ch-msg-marks" />
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
        <div className={`ch-page ${isEmbedded ? 'ch-embedded' : ''}`}>
            <header className="ch-header">
                <div className="ch-header-id">
                    {onBack && (
                        <button type="button" onClick={onBack} className="ch-back" aria-label="Back">
                            <Icon name="arrowLeft" size={15} />
                        </button>
                    )}
                    <span className="ch-hash" aria-hidden="true">
                        <Icon name="hash" size={13} />
                    </span>
                    <h3 className="ch-name">{channel?.name}</h3>
                    {channel?.description && (
                        <span className="ch-desc" title={channel.description}>
                            {channel.description}
                        </span>
                    )}
                    {channel?.memberCount != null && (
                        <span className="ch-members" title={`${channel.memberCount} members`}>
                            <Icon name="people" size={12} />
                            {channel.memberCount}
                        </span>
                    )}
                </div>

                <div className="ch-header-actions">
                    {channel?.inviteCode && (
                        <button
                            type="button"
                            onClick={copyInvite}
                            className={`ch-invite ${copiedInvite ? 'is-copied' : ''}`}
                            title="Copy the invite code"
                        >
                            <Icon name={copiedInvite ? 'check' : 'link'} size={12} />
                            <span className="ch-invite-label">{copiedInvite ? 'Copied' : 'Invite'}</span>
                            <span className="ch-invite-code">{channel.inviteCode}</span>
                        </button>
                    )}
                    <button
                        onClick={() => setInCall((v) => !v)}
                        className={`ch-btn ${inCall ? 'ch-btn-live' : 'ch-btn-outline'}`}
                        title={inCall ? 'Return to chat' : 'Start or join the channel call'}
                    >
                        <Icon name={inCall ? 'message' : 'video'} size={13} />
                        <span className="ch-btn-label">{inCall ? 'Chat' : 'Call'}</span>
                    </button>
                    <button
                        onClick={handleLeave}
                        disabled={isLeaving}
                        className="ch-btn ch-btn-danger"
                        title="Leave this channel"
                    >
                        <span className="ch-btn-label">{isLeaving ? 'Leaving…' : 'Leave'}</span>
                        <Icon name="logout" size={13} className="ch-btn-leave-icon" />
                    </button>
                    {onClose && !onBack && (
                        <button onClick={onClose} className="ch-btn ch-btn-icon" title="Close" aria-label="Close channel">
                            <Icon name="close" size={13} />
                        </button>
                    )}
                </div>
            </header>

            {inCall && (
                <LiveCallRoom
                    channelId={channelId}
                    channelName={channel?.name}
                    onLeaveCall={() => setInCall(false)}
                />
            )}

            {!inCall && callParticipants > 0 && (
                <div className="ch-callbar">
                    <span className="ch-callbar-dot" aria-hidden="true" />
                    <span className="ch-callbar-text">
                        Call in progress · {callParticipants}
                        {callParticipants === 1 ? ' person' : ' people'}
                    </span>
                    <button
                        onClick={() => setInCall(true)}
                        className="ch-callbar-join"
                        title="Join the call in this channel"
                    >
                        <Icon name="video" size={12} />
                        Join
                    </button>
                </div>
            )}

            {!inCall && (
                <div className="ch-intel">
                    <AskAiWidget channelId={channelId} channelName={channel?.name} />
                    <MemoryPanel channelId={channelId} />
                </div>
            )}

            {/* Kept mounted through a call so scroll position and history survive. */}
            <div className="ch-thread" style={inCall ? { display: 'none' } : undefined}>
                {renderThread()}
                <div ref={messagesEndRef} />
            </div>

            <div className="ch-composer" style={inCall ? { display: 'none' } : undefined}>
                <form onSubmit={sendMessage} className="ch-composer-form">
                    <input
                        type="text"
                        placeholder={`Message #${channel?.name || ''}`}
                        value={messageInput}
                        onChange={(e) => setMessageInput(e.target.value)}
                        className="ch-composer-field"
                        maxLength={2000}
                        aria-label={`Message #${channel?.name || ''}`}
                    />
                    <button type="submit" disabled={!messageInput.trim()} className="ch-send" title="Send" aria-label="Send">
                        <Icon name="send" size={14} />
                    </button>
                </form>
            </div>
        </div>
    );
};

export default ChannelChat;
