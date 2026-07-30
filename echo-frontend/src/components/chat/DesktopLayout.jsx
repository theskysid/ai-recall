import React from 'react';
import Sidebar from './Sidebar';
import DirectMessageChat from '../../pages/DirectMessageChat';
import ChannelChat from '../../pages/ChannelChat';

const DesktopLayout = ({ chat, friends, notifications, ui, layout }) => {
    const { mobileSidebarOpen, setMobileSidebarOpen, gridClass } = layout || {};
    const { username, activeChannel, onLeaveChannel } = ui || {};
    const {
        openChats = [],
        closeDmChat,
        stompClient,
        registerDmHandler,
        unregisterDmHandler,
        isConnected,
        subscribeToChannel,
        sendChannelMessage
    } = chat || {};

    return (
        <>
            {/* Mobile sidebar toggle */}
            <button
                className="mobile-sidebar-toggle"
                onClick={() => setMobileSidebarOpen(!mobileSidebarOpen)}
                aria-label="Toggle sidebar"
            >
                {mobileSidebarOpen ? '✕' : '☰'}
            </button>

            {/* Sidebar overlay for mobile */}
            {mobileSidebarOpen && (
                <div className="sidebar-overlay" onClick={() => setMobileSidebarOpen(false)} />
            )}

            <Sidebar
                chat={chat}
                friends={friends}
                ui={ui}
                layout={layout}
            />

            <div className={`chat-workspace-grid ${gridClass}`}>
                {activeChannel && (
                    <div key={`channel-${activeChannel.id}`} className="chat-panel-card">
                        <ChannelChat
                            key={`channel-${activeChannel.id}`}
                            currentUser={username}
                            channel={activeChannel}
                            subscribeToChannel={subscribeToChannel}
                            sendChannelMessage={sendChannelMessage}
                            isConnected={isConnected}
                            onLeave={onLeaveChannel}
                            isEmbedded={true}
                        />
                    </div>
                )}

                {openChats.map((chatItem) => (
                    <div key={`dm-${chatItem.username}`} className="chat-panel-card">
                        <DirectMessageChat
                            currentUser={username}
                            recipientUsername={chatItem.username}
                            stompClient={stompClient}
                            onClose={() => closeDmChat(chatItem.username)}
                            registerDmHandler={registerDmHandler}
                            unregisterDmHandler={unregisterDmHandler}
                            isEmbedded={true}
                        />
                    </div>
                ))}
            </div>
        </>
    );
};

export default DesktopLayout;
