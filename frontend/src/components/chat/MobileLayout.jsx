import React from 'react';
import FriendList from './FriendList';
import MobileDrawer from './MobileDrawer';
import Icon from '../ui/Icon';
import DirectMessageChat from '../../pages/DirectMessageChat';

const MobileLayout = ({ chat, friends, ui, layout }) => {
    const {
        openChats = [],
        stompClient,
        closeDmChat,
        registerDmHandler,
        unregisterDmHandler
    } = chat || {};
    const {
        friendsList = [],
        incomingRequestsCount = 0
    } = friends || {};
    const {
        setShowFindFriendsModal,
        username
    } = ui || {};
    const {
        mobileActiveView,
        setMobileActiveView,
        mobileListRef,
        setMobileMenuOpen,
        mobileSearch,
        setMobileSearch,
        chatListScrollRef
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
                            aria-label="Find people and view requests"
                            title="Find people, view requests"
                        >
                            {incomingRequestsCount > 0 && (
                                <span className="tg-compose-badge">{incomingRequestsCount}</span>
                            )}
                            <Icon name="plus" size={18} />
                        </button>
                    </div>

                    {/* ── Sticky wordmark — appears when scrolling down ── */}
                    <div className="tg-sticky-brand-bar" id="tg-sticky-brand-el">
                        <h2 className="tg-brand-text">Recall</h2>
                    </div>

                    {/* ── Search Bar ── */}
                    <div className="tg-search-bar">
                        <Icon name="search" size={15} className="tg-search-icon" />
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
                                <div className="tg-empty-icon"><Icon name="people" size={22} /></div>
                                <p>No private chats yet. Find someone to start one.</p>
                                <button className="tg-empty-add-btn" onClick={() => setShowFindFriendsModal(true)}>
                                    Find people
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
