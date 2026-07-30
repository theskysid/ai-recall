import { useState, useEffect, useRef } from 'react';
import SockJS from 'sockjs-client';
import { Stomp } from '@stomp/stompjs';
import { authService, api } from '../services/authService';

const useSocket = ({
    username,
    userColor,
    loadFriendsData,
    pushNotification,
    friendsListRef,
    dmHandlers,
    setUnreadDms,
    setRefreshTrigger
}) => {
    const [messages, setMessages] = useState([]);
    const [message, setMessage] = useState('');
    const [onlineUsers, setOnlineUsers] = useState(new Set());
    const [showEmojiPicker, setShowEmojiPicker] = useState(false);
    const [isTyping, setIsTyping] = useState('');

    const stompClient = useRef(null);
    const socketRef = useRef(null);
    const messagesEndRef = useRef(null);
    const typingTimeoutRef = useRef(null);
    const reconnectTimeoutRef = useRef(null);
    const isMountedRef = useRef(true);
    const isInitialScrollRef = useRef(true);

    const scrollToBottom = (behavior = "smooth") => {
        messagesEndRef.current?.scrollIntoView({ behavior });
    };

    useEffect(() => {
        if (isInitialScrollRef.current && messages.length > 0) {
            isInitialScrollRef.current = false;
            scrollToBottom("auto");
            const timer = setTimeout(() => scrollToBottom("auto"), 50);
            return () => clearTimeout(timer);
        } else {
            scrollToBottom("smooth");
        }
    }, [messages, isTyping]);

    useEffect(() => {
        isMountedRef.current = true;

        const cleanupConnection = () => {
            if (reconnectTimeoutRef.current) {
                clearTimeout(reconnectTimeoutRef.current);
                reconnectTimeoutRef.current = null;
            }

            if (stompClient.current) {
                try {
                    stompClient.current.disconnect();
                } catch (error) {
                    console.error('Error disconnecting STOMP client:', error);
                }
            }

            if (socketRef.current) {
                try {
                    socketRef.current.close();
                } catch (error) {
                    console.error('Error closing SockJS socket:', error);
                }
            }

            stompClient.current = null;
            socketRef.current = null;
        };

        const connectAndFetch = async () => {
            if (!isMountedRef.current || !username) return;

            setOnlineUsers(prev => {
                const newSet = new Set(prev);
                newSet.add(username);
                return newSet;
            });

            cleanupConnection();

            socketRef.current = new SockJS(`${import.meta.env.VITE_API_URL}/ws`);
            stompClient.current = Stomp.over(socketRef.current);

            stompClient.current.connect({
                'client-id': username,
                'session-id': Date.now().toString(),
                'username': username
            }, () => {
                if (reconnectTimeoutRef.current) {
                    clearTimeout(reconnectTimeoutRef.current);
                    reconnectTimeoutRef.current = null;
                }

                stompClient.current.subscribe('/topic/public', (msg) => {
                    const chatMessage = JSON.parse(msg.body);

                    setOnlineUsers(prev => {
                        const newUsers = new Set(prev);
                        if (chatMessage.type === 'JOIN') {
                            newUsers.add(chatMessage.sender);
                        } else if (chatMessage.type === 'LEAVE') {
                            newUsers.delete(chatMessage.sender);
                        }
                        return newUsers;
                    });

                    if (chatMessage.type === 'TYPING') {
                        setIsTyping(chatMessage.sender);
                        clearTimeout(typingTimeoutRef.current);
                        typingTimeoutRef.current = setTimeout(() => {
                            setIsTyping('');
                        }, 2000);
                        return;
                    }

                    if (
                        chatMessage.type === 'JOIN'
                        && chatMessage.sender !== username
                        && friendsListRef?.current?.some((friend) => friend.username === chatMessage.sender)
                    ) {
                        pushNotification(`${chatMessage.sender} is online now`);
                    }

                    setMessages(prev => [...prev, {
                        ...chatMessage,
                        timestamp: chatMessage.timestamp || new Date(),
                        id: chatMessage.id || (Date.now() + Math.random())
                    }]);
                });

                // New Ephemeral DM queue subscription
                stompClient.current.subscribe(`/user/${username}/queue/dm`, (msg) => {
                    const dmMessage = JSON.parse(msg.body);
                    const otherUser = dmMessage.senderUsername === username ? dmMessage.recipientUsername : dmMessage.senderUsername;
                    const handler = dmHandlers?.current?.get(otherUser);

                    if (handler) {
                        try {
                            handler(dmMessage);
                        } catch (error) {
                            console.error('Error calling DM handler:', error);
                        }
                    } else if (dmMessage.senderUsername !== username && dmMessage.content !== 'TYPING') {
                        setUnreadDms(prev => {
                            const newUnread = new Map(prev);
                            const currentCount = newUnread.get(otherUser) || 0;
                            newUnread.set(otherUser, currentCount + 1);
                            return newUnread;
                        });
                        pushNotification(`New private message from ${otherUser}`);
                    }
                });

                // Real-time notifications for Friend System updates
                stompClient.current.subscribe(`/user/${username}/queue/friends`, (msg) => {
                    const event = JSON.parse(msg.body);
                    if (event?.type === 'FRIEND_REQUEST_RECEIVED') {
                        pushNotification(`New friend request from ${event.username}`);
                    } else if (event?.type === 'FRIEND_REQUEST_ACCEPTED') {
                        pushNotification(`${event.username} accepted your friend request`);
                    } else if (event?.type === 'FRIEND_REMOVED') {
                        pushNotification(`${event.username} is no longer in your friends list`);
                    }
                    if (loadFriendsData) loadFriendsData();
                    if (setRefreshTrigger) setRefreshTrigger(prev => prev + 1);
                });

                stompClient.current.send("/app/chat.addUser", {}, JSON.stringify({
                    sender: username,
                    type: 'JOIN',
                    color: userColor
                }));

                authService.getOnlineUsers()
                    .then(data => {
                        if (isMountedRef.current && Array.isArray(data)) {
                            setOnlineUsers(prev => {
                                const mergedSet = new Set(prev);
                                data.forEach(user => mergedSet.add(user));
                                mergedSet.add(username);
                                return mergedSet;
                            });
                        }
                    })
                    .catch(error => {
                        console.error('Error fetching initial online users:', error);
                    });

                api.get('/api/messages/global')
                    .then(response => {
                        if (isMountedRef.current && Array.isArray(response.data)) {
                            setMessages(response.data);
                        }
                    })
                    .catch(error => {
                        console.error('Error fetching global chat history:', error);
                    });

            }, (error) => {
                console.error('STOMP connection error:', error);
                if (isMountedRef.current && !reconnectTimeoutRef.current) {
                    reconnectTimeoutRef.current = setTimeout(() => {
                        connectAndFetch();
                    }, 5000);
                }
            });
        };

        connectAndFetch();

        return () => {
            isMountedRef.current = false;
            cleanupConnection();
            clearTimeout(typingTimeoutRef.current);
        };
    }, [username, userColor, loadFriendsData, pushNotification, friendsListRef, dmHandlers, setUnreadDms, setRefreshTrigger]);

    const sendMessage = (e) => {
        e.preventDefault();
        if (message.trim() && stompClient.current && stompClient.current.connected) {
            const chatMessage = {
                sender: username,
                content: message,
                type: 'CHAT',
                color: userColor
            };

            stompClient.current.send("/app/chat.sendMessage", {}, JSON.stringify(chatMessage));
            setMessage('');
            setShowEmojiPicker(false);
        }
    };

    const handleTyping = (e) => {
        setMessage(e.target.value);

        if (stompClient.current && stompClient.current.connected && e.target.value.trim()) {
            stompClient.current.send("/app/chat.sendMessage", {}, JSON.stringify({
                sender: username,
                type: 'TYPING'
            }));
        }
    };

    const addEmoji = (emoji) => {
        setMessage(prev => prev + emoji);
        setShowEmojiPicker(false);
    };

    return {
        stompClient,
        messages,
        setMessages,
        onlineUsers,
        setOnlineUsers,
        isTyping,
        sendMessage,
        handleTyping,
        message,
        setMessage,
        showEmojiPicker,
        setShowEmojiPicker,
        addEmoji,
        messagesEndRef
    };
};

export default useSocket;
