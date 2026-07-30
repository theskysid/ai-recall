import { useState, useEffect, useRef } from 'react';
import SockJS from 'sockjs-client';
import { Stomp } from '@stomp/stompjs';
import { authService } from '../services/authService';

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
    const [onlineUsers, setOnlineUsers] = useState(new Set());

    const stompClient = useRef(null);
    const socketRef = useRef(null);
    const reconnectTimeoutRef = useRef(null);
    const isMountedRef = useRef(true);

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

                // Presence channel — tracks who is online for the Friends/Users list.
                stompClient.current.subscribe('/topic/public', (msg) => {
                    const presenceEvent = JSON.parse(msg.body);

                    setOnlineUsers(prev => {
                        const newUsers = new Set(prev);
                        if (presenceEvent.type === 'JOIN') {
                            newUsers.add(presenceEvent.sender);
                        } else if (presenceEvent.type === 'LEAVE') {
                            newUsers.delete(presenceEvent.sender);
                        }
                        return newUsers;
                    });

                    if (
                        presenceEvent.type === 'JOIN'
                        && presenceEvent.sender !== username
                        && friendsListRef?.current?.some((friend) => friend.username === presenceEvent.sender)
                    ) {
                        pushNotification(`${presenceEvent.sender} is online now`);
                    }
                });

                // Ephemeral DM queue subscription
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

                // Register this session for presence tracking.
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
        };
    }, [username, userColor, loadFriendsData, pushNotification, friendsListRef, dmHandlers, setUnreadDms, setRefreshTrigger]);

    return {
        stompClient,
        onlineUsers,
        setOnlineUsers
    };
};

export default useSocket;
