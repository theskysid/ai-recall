import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { authService } from '../services/authService';
import { channelService } from '../services/channelService';
import useSocket from '../hooks/useSocket';
import useFriends from '../hooks/useFriends';
import useChannels from '../hooks/useChannels';
import useNotifications from '../hooks/useNotifications';
import DirectMessageChat from './DirectMessageChat';
import FindFriendsModal from './FindFriendsModal';
import ChannelModal from '../components/chat/ChannelModal';
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

    // Multi-section chat state (up to 3 DM sections total)
    const [openChats, setOpenChats] = useState([]); // Array of { username: string }

    // Responsive Mobile State
    const [isMobile, setIsMobile] = useState(() => window.innerWidth <= 768);
    const [mobileActiveViewRaw, setMobileActiveViewRaw] = useState('list'); // 'list' | 'dm'

    const setMobileActiveView = useCallback((newView) => {
        setMobileActiveViewRaw(newView);
        if (newView === 'dm') {
            window.history.pushState({ mobileView: newView }, '', `#${newView}`);
        } else if (newView === 'list' && window.location.hash === '#dm') {
            window.history.back();
        }
    }, []);

    const mobileActiveView = mobileActiveViewRaw;

    useEffect(() => {
        const handlePopState = () => {
            if (window.location.hash === '#dm') {
                setMobileActiveViewRaw('dm');
            } else {
                setMobileActiveViewRaw('list');
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

    // Channel states
    const [showChannelModal, setShowChannelModal] = useState(false);
    const [activeChannelId, setActiveChannelId] = useState(null);
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
        onlineUsers,
        isConnected,
        subscribeToChannel,
        sendChannelMessage
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

    const { channels, upsertChannel, removeChannel } = useChannels();

    const activeChannel = channels.find((c) => c.id === activeChannelId) || null;

    // Selecting a channel opens its message feed.
    const onSelectChannel = useCallback((channelId) => {
        setActiveChannelId(channelId);
        if (isMobile) {
            setMobileSidebarOpen(false);
        }
    }, [isMobile]);

    // Add a newly created/joined channel to the sidebar immediately and select it.
    const handleChannelReady = useCallback((channel) => {
        if (!channel) return;
        upsertChannel(channel);
        setActiveChannelId(channel.id);
    }, [upsertChannel]);

    // Leave a channel: call the API, drop it from the sidebar, clear the active view.
    const handleLeaveChannel = useCallback(async (channelId) => {
        try {
            await channelService.leaveChannel(channelId);
        } catch (err) {
            window.alert(err.response?.data?.error || err.message || 'Could not leave channel');
            return;
        }
        removeChannel(channelId);
        setActiveChannelId((prev) => (prev === channelId ? null : prev));
    }, [removeChannel]);

    const openDmChat = (otherUser) => {
        if (otherUser === username) return;

        setOpenChats(prev => {
            const filtered = prev.filter(c => c.username !== otherUser);
            const maxLimit = 3;
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





    const totalColumns = (activeChannel ? 1 : 0) + openChats.length;
    const gridClass = totalColumns <= 1 ? 'columns-1' : totalColumns === 2 ? 'columns-2' : 'columns-3';

    const chat = {
        onlineUsers,
        openChats,
        setOpenChats,
        openDmChat,
        closeDmChat,
        unreadDms,
        stompClient,
        registerDmHandler,
        unregisterDmHandler,
        isConnected,
        subscribeToChannel,
        sendChannelMessage
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
        sidebarTab,
        setSidebarTab,
        longPressTimerRef,
        isLongPressTriggeredRef,
        navigate,
        authService,
        channels,
        activeChannelId,
        activeChannel,
        onSelectChannel,
        onLeaveChannel: handleLeaveChannel,
        onOpenChannelModal: () => setShowChannelModal(true)
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

            {showChannelModal && (
                <ChannelModal
                    onClose={() => setShowChannelModal(false)}
                    onChannelReady={handleChannelReady}
                />
            )}
        </div>
    );
};

export default ChatArea;
