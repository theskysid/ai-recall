import React from 'react';
import FriendList from './FriendList';
import MobileDrawer from './MobileDrawer';
import Icon from '../ui/Icon';
import DirectMessageChat from '../../pages/DirectMessageChat';
import ChannelChat from '../../pages/ChannelChat';

const MobileLayout = ({ chat, friends, ui, layout }) => {
    const {
        openChats = [],
        stompClient,
        closeDmChat,
        registerDmHandler,
        unregisterDmHandler,
        isConnected,
        subscribeToChannel,
        sendChannelMessage
    } = chat || {};
    const {
        friendsList = [],
        incomingRequestsCount = 0
    } = friends || {};
    const {
        setShowFindFriendsModal,
        username,
        channels = [],
        activeChannel,
        activeChannelId,
        onSelectChannel,
        onOpenChannelModal,
        onLeaveChannel
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

    // Search filters channels here the way FriendList filters people.
    const term = mobileSearch.trim().toLowerCase();
    const visibleChannels = term
        ? channels.filter((ch) => ch.name.toLowerCase().includes(term))
        : channels;

    const openChannel = (id) => {
        if (onSelectChannel) onSelectChannel(id);
        setMobileActiveView('channel');
    };

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
                            placeholder="Search channels and people"
                            value={mobileSearch}
                            onChange={e => setMobileSearch(e.target.value)}
                        />
                        {mobileSearch && (
                            <button className="tg-search-clear" onClick={() => setMobileSearch('')}>✕</button>
                        )}
                    </div>

                    {/* ── Chat List ── */}
                    <div className="tg-chat-list" ref={chatListScrollRef}>

                        {/* Channels — the record. First, because the channel is the memory. */}
                        <div className="tg-section-head">
                            <span className="tg-section-title">Channels</span>
                            <button
                                className="tg-section-add"
                                onClick={onOpenChannelModal}
                                aria-label="Create or join a channel"
                                title="Create or join a channel"
                            >
                                <Icon name="plus" size={15} />
                            </button>
                        </div>

                        {channels.length === 0 ? (
                            <div className="tg-section-empty">
                                No channels yet. Tap + to create one or join with an invite code.
                            </div>
                        ) : visibleChannels.length === 0 ? (
                            <div className="tg-section-empty">No channels match “{mobileSearch}”.</div>
                        ) : (
                            visibleChannels.map((ch) => (
                                <button
                                    key={ch.id}
                                    className={`tg-channel-row ${activeChannelId === ch.id ? 'is-active' : ''}`}
                                    onClick={() => openChannel(ch.id)}
                                >
                                    <span className="tg-channel-tile" aria-hidden="true">#</span>
                                    <span className="tg-channel-meta">
                                        <span className="tg-channel-name">{ch.name}</span>
                                        <span className="tg-channel-sub">
                                            {ch.description
                                                ? ch.description
                                                : ch.memberCount != null
                                                    ? `${ch.memberCount} member${ch.memberCount === 1 ? '' : 's'}`
                                                    : 'Open the record'}
                                        </span>
                                    </span>
                                    <Icon name="chevronRight" size={16} className="tg-channel-chev" />
                                </button>
                            ))
                        )}

                        {/* People — private, expiring chats. */}
                        <div className="tg-section-head">
                            <span className="tg-section-title">Direct messages</span>
                        </div>

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

            {mobileActiveView === 'channel' && activeChannel && (
                <div className="chat-panel-card mobile-panel">
                    <ChannelChat
                        key={`channel-${activeChannel.id}`}
                        currentUser={username}
                        channel={activeChannel}
                        subscribeToChannel={subscribeToChannel}
                        sendChannelMessage={sendChannelMessage}
                        isConnected={isConnected}
                        onLeave={(id) => {
                            if (onLeaveChannel) onLeaveChannel(id);
                            setMobileActiveView('list');
                        }}
                        onBack={() => setMobileActiveView('list')}
                        isEmbedded={true}
                    />
                </div>
            )}
        </div>
    );
};

export default MobileLayout;
