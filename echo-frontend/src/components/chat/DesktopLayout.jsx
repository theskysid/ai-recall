import React from 'react';
import Sidebar from './Sidebar';
import GlobalChat from './GlobalChat';
import DirectMessageChat from '../../pages/DirectMessageChat';

const DesktopLayout = ({ chat, friends, notifications, ui, layout }) => {
    const { mobileSidebarOpen, setMobileSidebarOpen, gridClass } = layout || {};
    const { showGlobalChat, setShowGlobalChat, username } = ui || {};
    const { openChats = [], closeDmChat, stompClient, registerDmHandler, unregisterDmHandler } = chat || {};

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
                {showGlobalChat && (
                    <GlobalChat
                        chat={chat}
                        ui={ui}
                        layout={{ mobile: false, onClose: () => setShowGlobalChat(false) }}
                    />
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
