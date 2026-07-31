import React from 'react';
import FriendList from './FriendList';
import MobileDrawer from './MobileDrawer';
import DirectMessageChat from '../../pages/DirectMessageChat';

const MobileLayout = ({ chat, friends, notifications, ui, layout }) => {
    const {
        openDmChat,
        unreadDms = new Map(),
        openChats = [],
        stompClient,
        closeDmChat,
        registerDmHandler,
        unregisterDmHandler
    } = chat || {};
    const {
        friendsList = [],
        incomingRequestsCount = 0,
        contextMenuFriendId,
        setContextMenuFriendId,
        removingFriendId,
        handleRemoveFriend,
        loadFriendsData
    } = friends || {};
    const {
        setShowFindFriendsModal,
        longPressTimerRef,
        isLongPressTriggeredRef,
        username,
        navigate,
        authService
    } = ui || {};
    const {
        mobileActiveView,
        setMobileActiveView,
        mobileListRef,
        setMobileMenuOpen,
        mobileSearch,
        setMobileSearch,
        chatListScrollRef,
        mobileMenuOpen
    } = layout || {};

    return (
        <div className="mobile-chat-layout">
            {mobileActiveView === 'list' && (
                <div className="mobile-chat-list-view" ref={mobileListRef}>

                    {/* ── Top App Bar — auto-hides on scroll ── */}
                    <div className="tg-topbar" id="tg-topbar-el">
                        <button
                            className="tg-topbar-menu-btn"
                            onClick={() => setMobileMenuOpen(true)}
                            aria-label="Menu"
                        >
                            <span className="tg-hamburger-line" />
                            <span className="tg-hamburger-line" />
                            <span className="tg-hamburger-line" />
                        </button>
                        <h2 className="tg-topbar-title">Chats</h2>
                        <button
                            className="tg-topbar-compose-btn"
                            onClick={() => setShowFindFriendsModal(true)}
                            aria-label="New chat / Add friend"
                            title="Find friends, view requests"
                        >
                            {incomingRequestsCount > 0 && (
                                <span className="tg-compose-badge">{incomingRequestsCount}</span>
                            )}
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" width="22" height="22">
                                <line x1="12" y1="5" x2="12" y2="19" />
                                <line x1="5" y1="12" x2="19" y2="12" />
                            </svg>
                        </button>
                    </div>

                    {/* ── Sticky Brand Bar ("Echo" text only) — appears when scrolling down ── */}
                    <div className="tg-sticky-brand-bar" id="tg-sticky-brand-el">
                        <h2 className="tg-brand-text">Echo</h2>
                    </div>

                    {/* ── Search Bar ── */}
                    <div className="tg-search-bar">
                        <svg className="tg-search-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" width="16" height="16">
                            <circle cx="11" cy="11" r="8" />
                            <line x1="21" y1="21" x2="16.65" y2="16.65" />
                        </svg>
                        <input
                            type="text"
                            className="tg-search-input"
                            placeholder="Search"
                            value={mobileSearch}
                            onChange={e => setMobileSearch(e.target.value)}
                        />
                        {mobileSearch && (
                            <button className="tg-search-clear" onClick={() => setMobileSearch('')}>✕</button>
                        )}
                    </div>

                    {/* ── Chat List ── */}
                    <div className="tg-chat-list" ref={chatListScrollRef}>

                        {/* Friends */}
                        {friendsList.length === 0 ? (
                            <div className="tg-empty-state">
                                <div className="tg-empty-icon">👥</div>
                                <p>No friends yet.</p>
                                <button className="tg-empty-add-btn" onClick={() => setShowFindFriendsModal(true)}>
                                    Find Friends
                                </button>
                            </div>
                        ) : (
                            <FriendList chat={chat} friends={friends} ui={ui} layout={layout} mobile={true} />
                        )}
                    </div>

                    {/* ── Left Slide-in Menu Drawer ── */}
                    <MobileDrawer friends={friends} ui={ui} layout={layout} />

                </div>
            )}

            {mobileActiveView === 'dm' && openChats.length > 0 && (
                <div className="chat-panel-card mobile-panel">
                    <DirectMessageChat
                        currentUser={username}
                        recipientUsername={openChats[openChats.length - 1].username}
                        stompClient={stompClient}
                        onClose={() => {
                            closeDmChat(openChats[openChats.length - 1].username);
                            setMobileActiveView('list');
                        }}
                        onBack={() => setMobileActiveView('list')}
                        registerDmHandler={registerDmHandler}
                        unregisterDmHandler={unregisterDmHandler}
                        isEmbedded={true}
                        isMobile={true}
                    />
                </div>
            )}
        </div>
    );
};

export default MobileLayout;
