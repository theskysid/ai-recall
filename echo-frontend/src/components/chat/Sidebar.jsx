import React from 'react';
import FriendList from './FriendList';
import '../../styles/Channels.css';

const Sidebar = ({ chat, friends, ui, layout }) => {
    const { onlineUsers = new Set(), openDmChat, unreadDms = new Map() } = chat || {};
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
        sidebarTab,
        setSidebarTab,
        setShowFindFriendsModal,
        longPressTimerRef,
        isLongPressTriggeredRef,
        username,
        userColor,
        channels = [],
        activeChannelId,
        onSelectChannel,
        onOpenChannelModal
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

            {/* ── CHANNELS ── */}
            <div className="channels-section">
                <div className="channels-section-header">
                    <span className="channels-section-title">Channels</span>
                    <button
                        className="channels-add-btn"
                        onClick={onOpenChannelModal}
                        title="Create or join a channel"
                        aria-label="Create or join a channel"
                    >
                        +
                    </button>
                </div>
                {channels.length === 0 ? (
                    <div className="channels-empty">No channels yet. Tap + to create or join.</div>
                ) : (
                    <div className="channels-list">
                        {channels.map((ch) => (
                            <button
                                key={ch.id}
                                className={`channel-item ${activeChannelId === ch.id ? 'active' : ''}`}
                                onClick={() => onSelectChannel && onSelectChannel(ch.id)}
                                title={ch.description || ch.name}
                            >
                                <span className="channel-item-hash">#</span>
                                <span className="channel-item-name">{ch.name}</span>
                            </button>
                        ))}
                    </div>
                )}
            </div>

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
