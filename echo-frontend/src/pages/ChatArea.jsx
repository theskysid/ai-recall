import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { authService } from '../services/authService';
import useSocket from '../hooks/useSocket';
import useFriends from '../hooks/useFriends';
import useNotifications from '../hooks/useNotifications';
import DirectMessageChat from './DirectMessageChat';
import FindFriendsModal from './FindFriendsModal';
import GlobalChat from '../components/chat/GlobalChat';
import Sidebar from '../components/chat/Sidebar';
import MobileLayout from '../components/chat/MobileLayout';
import DesktopLayout from '../components/chat/DesktopLayout';
import NotificationStack from '../components/chat/NotificationStack';
import '../styles/ChatArea.css';

const isMobileViewport = () => window.innerWidth <= 768;

const ChatArea = () => {
    const navigate = useNavigate();
    const [currentUser] = useState(() => authService.getCurrentUser());

    useEffect(() => {
        if (!currentUser) {
            navigate('/login');
            return;
        }
    }, [currentUser, navigate]);

    // Multi-section chat state (up to 3 sections total)
    const [showGlobalChat, setShowGlobalChat] = useState(true);
    const [openChats, setOpenChats] = useState([]); // Array of { username: string }

    // Responsive Mobile State
    const [isMobile, setIsMobile] = useState(() => window.innerWidth <= 768);
    const [mobileActiveViewRaw, setMobileActiveViewRaw] = useState('list'); // 'list' | 'global' | 'dm'

    const setMobileActiveView = useCallback((newView) => {
        setMobileActiveViewRaw(newView);
        if (newView === 'global' || newView === 'dm') {
            window.history.pushState({ mobileView: newView }, '', `#${newView}`);
        } else if (newView === 'list' && (window.location.hash === '#global' || window.location.hash === '#dm')) {
            window.history.back();
        }
    }, []);

    const mobileActiveView = mobileActiveViewRaw;

    useEffect(() => {
        const handlePopState = () => {
            if (!window.location.hash || (window.location.hash !== '#global' && window.location.hash !== '#dm')) {
                setMobileActiveViewRaw('list');
            } else if (window.location.hash === '#global') {
                setMobileActiveViewRaw('global');
            } else if (window.location.hash === '#dm') {
                setMobileActiveViewRaw('dm');
            }
        };
        window.addEventListener('popstate', handlePopState);
        return () => window.removeEventListener('popstate', handlePopState);
    }, []);

    useEffect(() => {
        const handleResize = () => {
            setIsMobile(window.innerWidth <= 768);
        };
        window.addEventListener('resize', handleResize);
        return () => window.removeEventListener('resize', handleResize);
    }, []);

    // Auto-hide topbar on scroll down, reveal on scroll up
    // The actual scroll happens on tg-chat-list (chatListScrollRef), not the outer wrapper
    useEffect(() => {
        const container = chatListScrollRef.current;
        if (!container) return;
        let lastScrollY = 0;
        const topbar = document.getElementById('tg-topbar-el');
        const brandBar = document.getElementById('tg-sticky-brand-el');
        const onScroll = () => {
            const current = container.scrollTop;
            if (!topbar || !brandBar) return;
            if (current > lastScrollY && current > 40) {
                topbar.classList.add('tg-topbar-hidden');
                brandBar.classList.add('tg-brand-bar-visible');
            } else {
                topbar.classList.remove('tg-topbar-hidden');
                brandBar.classList.remove('tg-brand-bar-visible');
            }
            lastScrollY = current;
        };
        container.addEventListener('scroll', onScroll, { passive: true });
        return () => container.removeEventListener('scroll', onScroll);
    }, [mobileActiveView]); // re-attach when view changes


    // Friend System & Ephemeral DM states
    const [sidebarTab, setSidebarTab] = useState('friends'); // 'friends' | 'users'
    const [showFindFriendsModal, setShowFindFriendsModal] = useState(false);
    const [unreadDms, setUnreadDms] = useState(new Map());
    const [mobileSidebarOpen, setMobileSidebarOpen] = useState(false);
    const [mobileSearch, setMobileSearch] = useState('');
    const [mobileFilter, setMobileFilter] = useState('all'); // 'all' | 'personal'
    const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

    const dmHandlers = useRef(new Map());
    const isMountedRef = useRef(true);
    const longPressTimerRef = useRef(null);
    const isLongPressTriggeredRef = useRef(false);
    const mobileListRef = useRef(null);
    const chatListScrollRef = useRef(null);

    const emojis = ['😀', '😂', '😍', '🤔', '👍', '❤️', '🎉', '🔥', '😎', '⭐', '✨', '💯'];
    const username = currentUser?.username ?? '';
    const userColor = currentUser?.color ?? '#007bff';



    const registerDmHandler = useCallback((otherUser, handler) => {
        dmHandlers.current.set(otherUser, handler);
    }, []);

    const unregisterDmHandler = useCallback((otherUser) => {
        dmHandlers.current.delete(otherUser);
    }, []);

    const { notifications: notificationItems, pushNotification } = useNotifications();

    const {
        friendsList,
        incomingRequestsCount,
        isSidebarRefreshing,
        refreshTrigger,
        setRefreshTrigger,
        removingFriendId,
        contextMenuFriendId,
        setContextMenuFriendId,
        friendsListRef,
        loadFriendsData,
        handleRemoveFriend
    } = useFriends({
        setOpenChats,
        setUnreadDms,
        pushNotification
    });

    const {
        stompClient,
        messages,
        setMessages,
        onlineUsers,
        isTyping,
        sendMessage,
        handleTyping,
        message,
        setMessage,
        showEmojiPicker,
        setShowEmojiPicker,
        addEmoji,
        messagesEndRef
    } = useSocket({
        username,
        userColor,
        loadFriendsData,
        pushNotification,
        friendsListRef,
        dmHandlers,
        setUnreadDms,
        setRefreshTrigger
    });

    const openDmChat = (otherUser) => {
        if (otherUser === username) return;

        setOpenChats(prev => {
            const filtered = prev.filter(c => c.username !== otherUser);
            const maxLimit = showGlobalChat && !isMobile ? 2 : 3;
            const next = [...filtered, { username: otherUser }];
            if (next.length > maxLimit) {
                return next.slice(next.length - maxLimit);
            }
            return next;
        });

        setUnreadDms(prev => {
            const newUnread = new Map(prev);
            newUnread.delete(otherUser);
            return newUnread;
        });

        if (isMobile) {
            setMobileActiveView('dm');
        } else if (isMobileViewport()) {
            setMobileSidebarOpen(false);
        }
    };

    const closeDmChat = (otherUser) => {
        setOpenChats(prev => prev.filter(c => c.username !== otherUser));
        unregisterDmHandler(otherUser);
    };





    const formatTime = (timestamp) => {
        if (!timestamp) return '';
        let ts = timestamp;
        if (typeof ts === 'string' && !ts.endsWith('Z') && !ts.includes('+') && !ts.includes('GMT')) {
            ts += 'Z';
        }
        const dateObj = new Date(ts);
        if (isNaN(dateObj.getTime())) return '';
        return dateObj.toLocaleTimeString([], {
            hour: '2-digit',
            minute: '2-digit'
        });
    };

    const totalColumns = (showGlobalChat ? 1 : 0) + openChats.length;
    const gridClass = totalColumns <= 1 ? 'columns-1' : totalColumns === 2 ? 'columns-2' : 'columns-3';

    const chat = {
        messages,
        setMessages,
        message,
        setMessage,
        sendMessage,
        handleTyping,
        isTyping,
        emojis,
        addEmoji,
        showEmojiPicker,
        setShowEmojiPicker,
        formatTime,
        messagesEndRef,
        onlineUsers,
        openChats,
        setOpenChats,
        openDmChat,
        closeDmChat,
        unreadDms,
        stompClient,
        registerDmHandler,
        unregisterDmHandler
    };

    const friends = {
        friendsList,
        incomingRequestsCount,
        isSidebarRefreshing,
        refreshTrigger,
        setRefreshTrigger,
        removingFriendId,
        contextMenuFriendId,
        setContextMenuFriendId,
        friendsListRef,
        loadFriendsData,
        handleRemoveFriend
    };

    const notifications = {
        list: notificationItems,
        pushNotification
    };

    const ui = {
        username,
        currentUser: username,
        userColor,
        showFindFriendsModal,
        setShowFindFriendsModal,
        showGlobalChat,
        setShowGlobalChat,
        sidebarTab,
        setSidebarTab,
        longPressTimerRef,
        isLongPressTriggeredRef,
        navigate,
        authService
    };

    const layout = {
        isMobile,
        mobileActiveView,
        setMobileActiveView,
        mobileSidebarOpen,
        setMobileSidebarOpen,
        mobileSearch,
        setMobileSearch,
        mobileFilter,
        setMobileFilter,
        mobileMenuOpen,
        setMobileMenuOpen,
        mobileListRef,
        chatListScrollRef,
        gridClass
    };

    if (!currentUser) {
        return null;
    }

    return (
        <div className="chat-container">
            <NotificationStack notifications={notifications.list} />

            {/* Responsive Layout Selection */}
            {isMobile ? (
                <MobileLayout
                    chat={chat}
                    friends={friends}
                    notifications={notifications}
                    ui={ui}
                    layout={layout}
                />
            ) : (
                <DesktopLayout
                    chat={chat}
                    friends={friends}
                    notifications={notifications}
                    ui={ui}
                    layout={layout}
                />
            )}

            {showFindFriendsModal && (
                <FindFriendsModal
                    onClose={() => setShowFindFriendsModal(false)}
                    onFriendsChange={() => loadFriendsData(true)}
                    refreshTrigger={refreshTrigger}
                />
            )}
        </div>
    );
};

export default ChatArea;
