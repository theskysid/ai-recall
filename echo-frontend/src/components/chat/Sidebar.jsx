import React from 'react';
import FriendList from './FriendList';

const Sidebar = ({ chat, friends, ui, layout }) => {
    const { openChats = [], setOpenChats, onlineUsers = new Set(), openDmChat, unreadDms = new Map() } = chat || {};
    const {
        friendsList = [],
        incomingRequestsCount = 0,
        isSidebarRefreshing,
        loadFriendsData,
        removingFriendId,
        contextMenuFriendId,
        setContextMenuFriendId,
        handleRemoveFriend
    } = friends || {};
    const {
        showGlobalChat,
        setShowGlobalChat,
        sidebarTab,
        setSidebarTab,
        setShowFindFriendsModal,
        longPressTimerRef,
        isLongPressTriggeredRef,
        username,
        userColor
    } = ui || {};
    const { mobileSidebarOpen, setMobileSidebarOpen } = layout || {};

    return (
        <div className={`sidebar ${mobileSidebarOpen ? 'sidebar-mobile-open' : ''}`}>
            <div className="sidebar-header">
                <h3>Users & Friends</h3>
                <div className="sidebar-header-actions">
                    <button
                        onClick={() => loadFriendsData(true)}
                        className={`sidebar-refresh-icon ${isSidebarRefreshing ? 'spin' : ''}`}
                        title="Live Refresh Friends & Requests"
                    >
                        🔄
                    </button>
                    <button
                        className="sidebar-close-mobile"
                        onClick={() => setMobileSidebarOpen(false)}
                    >
                        ✕
                    </button>
                </div>
            </div>

            {!showGlobalChat && (
                <button
                    onClick={() => {
                        setShowGlobalChat(true);
                        if (openChats.length >= 3) {
                            setOpenChats(prev => prev.slice(prev.length - 2));
                        }
                    }}
                    className="open-global-chat-btn"
                >
                    + Open Global Chat
                </button>
            )}

            <div className="sidebar-tabs">
                <button
                    className={`sidebar-tab-btn ${sidebarTab === 'friends' ? 'active' : ''}`}
                    onClick={() => setSidebarTab('friends')}
                >
                    👥 Friends
                    {incomingRequestsCount > 0 && (
                        <span className="unread-count" style={{ marginLeft: '6px' }}>{incomingRequestsCount}</span>
                    )}
                </button>
                <button
                    className={`sidebar-tab-btn ${sidebarTab === 'users' ? 'active' : ''}`}
                    onClick={() => setSidebarTab('users')}
                >
                    🌐 Global Users
                </button>
            </div>

            <div className="users-list">
                {/* Pinned Global Chat at top of sidebar */}
                <div
                    className={`friend-sidebar-item ${showGlobalChat ? 'menu-active' : ''}`}
                    style={{ borderBottom: '1px solid var(--border-glass)', marginBottom: '0.5rem', paddingBottom: '0.6rem' }}
                    onClick={() => {
                        setShowGlobalChat(true);
                        if (openChats.length >= 3) {
                            setOpenChats(prev => prev.slice(prev.length - 2));
                        }
                    }}
                >
                    <div className="friend-sidebar-user">
                        <div className="user-avatar friend-avatar" style={{ background: 'var(--gradient-primary)', color: '#fff' }}>
                            🌐
                        </div>
                        <div className="friend-sidebar-meta">
                            <span className="friend-sidebar-name">Global Chat</span>
                            <span className="friend-sidebar-status online">● {chat?.onlineUsers?.size || 1} online</span>
                        </div>
                    </div>
                    <div className="friend-sidebar-actions">
                        <span className="friend-dm-icon" title="Pinned Global Chat" style={{ fontSize: '1rem' }}>📌</span>
                    </div>
                </div>

                {sidebarTab === 'friends' ? (
                    <>
                        <button
                            className="add-friend-trigger-btn"
                            onClick={() => setShowFindFriendsModal(true)}
                        >
                            🔍 Find / Add Friends
                            {incomingRequestsCount > 0 && (
                                <span className="unread-count">{incomingRequestsCount}</span>
                            )}
                        </button>

                        {friendsList.length === 0 ? (
                            <div className="friends-empty-hint">
                                No friends added yet. Click above to search and add friends!
                            </div>
                        ) : (
                            <FriendList chat={chat} friends={friends} ui={ui} layout={layout} mobile={false} />
                        )}
                    </>
                ) : (
                    <>
                        <div className="global-users-hint">
                            Private chat is available only from the Friends tab.
                        </div>
                        {Array.from(onlineUsers).map(user => {
                            const isFriend = friendsList.some((friend) => friend.username === user);
                            return (
                                <div
                                    key={user}
                                    className={`user-item ${user === username ? 'current-user' : ''} ${isFriend ? 'friend-user-item' : ''}`}
                                    onClick={() => {
                                        if (isFriend && user !== username) {
                                            openDmChat(user);
                                        }
                                    }}
                                >
                                    <div className="user-avatar" style={{ backgroundColor: user === username ? userColor : '#007bff' }}>
                                        {user.charAt(0).toUpperCase()}
                                    </div>
                                    <span>{user}</span>
                                    {user === username && <span className="you-label">(You)</span>}
                                    {user !== username && (
                                        <span className="global-user-tag">
                                            {isFriend ? 'Friend' : 'Global'}
                                        </span>
                                    )}
                                </div>
                            );
                        })}
                    </>
                )}
            </div>
        </div>
    );
};

export default Sidebar;
