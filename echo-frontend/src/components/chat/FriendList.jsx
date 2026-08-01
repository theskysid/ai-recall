import React from 'react';
import Icon from '../ui/Icon';
import { hueClass } from '../../utils/avatarHue';

const FriendList = ({ chat, friends, ui, layout, mobile = false }) => {
    const { onlineUsers = new Set(), unreadDms = new Map(), openDmChat } = chat || {};
    const {
        friendsList = [],
        contextMenuFriendId,
        setContextMenuFriendId,
        removingFriendId,
        handleRemoveFriend
    } = friends || {};
    const {
        longPressTimerRef,
        isLongPressTriggeredRef
    } = ui || {};
    const { mobileSearch = '' } = layout || {};

    const listToRender = mobile && mobileSearch
        ? friendsList.filter(f => !mobileSearch || f.username.toLowerCase().includes(mobileSearch.toLowerCase()))
        : friendsList;

    return (
        <>
            {listToRender.map((friend) => {
                const isOnline = onlineUsers.has(friend.username) || friend.online;
                const friendKey = friend.friendshipId || friend.id;
                const isMenuOpen = contextMenuFriendId === friendKey;
                const unreadCount = unreadDms.get(friend.username);

                const handleStartPress = () => {
                    if (longPressTimerRef && longPressTimerRef.current) clearTimeout(longPressTimerRef.current);
                    if (longPressTimerRef) {
                        longPressTimerRef.current = setTimeout(() => {
                            if (isLongPressTriggeredRef) isLongPressTriggeredRef.current = true;
                            if (setContextMenuFriendId) setContextMenuFriendId(friendKey);
                        }, 550);
                    }
                };

                const handleCancelPress = () => {
                    if (longPressTimerRef && longPressTimerRef.current) clearTimeout(longPressTimerRef.current);
                };

                const handleClick = () => {
                    if (isLongPressTriggeredRef && isLongPressTriggeredRef.current) {
                        isLongPressTriggeredRef.current = false;
                        return;
                    }
                    if (openDmChat) openDmChat(friend.username);
                };

                if (mobile) {
                    return (
                        <div
                            key={friendKey}
                            className={`tg-chat-row ${isMenuOpen ? 'tg-menu-open' : ''}`}
                            onClick={handleClick}
                            onContextMenu={e => {
                                e.preventDefault();
                                if (setContextMenuFriendId) setContextMenuFriendId(friendKey);
                            }}
                            onMouseDown={handleStartPress}
                            onMouseUp={handleCancelPress}
                            onMouseLeave={handleCancelPress}
                            onTouchStart={handleStartPress}
                            onTouchEnd={handleCancelPress}
                        >
                            <div className="tg-avatar-wrapper">
                                <div className={`tg-avatar ${hueClass(friend.username)}`}>
                                    {friend.username.charAt(0).toUpperCase()}
                                </div>
                                {isOnline && <span className="tg-online-dot" />}
                            </div>
                            <div className="tg-chat-info">
                                <div className="tg-chat-row-top">
                                    <span className="tg-chat-name">{friend.username}</span>
                                    <span className={`tg-chat-status-label ${isOnline ? 'online' : ''}`}>
                                        {isOnline ? 'online' : 'offline'}
                                    </span>
                                </div>
                                <div className="tg-chat-row-bottom">
                                    <span className="tg-chat-preview">Tap to open a private chat</span>
                                    {unreadCount && (
                                        <span className="tg-unread-badge">{unreadCount}</span>
                                    )}
                                </div>
                            </div>

                            <button
                                type="button"
                                className="friend-menu-trigger-btn"
                                title="More options"
                                aria-label={`More options for ${friend.username}`}
                                onClick={(e) => {
                                    e.stopPropagation();
                                    if (setContextMenuFriendId) setContextMenuFriendId(isMenuOpen ? null : friendKey);
                                }}
                            >
                                <Icon name="more" size={16} />
                            </button>

                            {isMenuOpen && (
                                <div className="tg-context-menu" onClick={e => e.stopPropagation()}>
                                    <button
                                        className="tg-ctx-item tg-ctx-danger"
                                        disabled={removingFriendId === friend.friendshipId}
                                        onClick={e => {
                                            e.stopPropagation();
                                            if (setContextMenuFriendId) setContextMenuFriendId(null);
                                            if (handleRemoveFriend) handleRemoveFriend(friend);
                                        }}
                                    >
                                        <Icon name="trash" size={15} />
                                        {removingFriendId === friend.friendshipId ? 'Removing…' : 'Remove friend'}
                                    </button>
                                    <button
                                        className="tg-ctx-item"
                                        onClick={e => {
                                            e.stopPropagation();
                                            if (setContextMenuFriendId) setContextMenuFriendId(null);
                                        }}
                                    >
                                        <Icon name="close" size={15} />
                                        Cancel
                                    </button>
                                </div>
                            )}
                        </div>
                    );
                }

                return (
                    <div
                        key={friendKey}
                        className={`friend-sidebar-item ${isMenuOpen ? 'menu-active' : ''}`}
                        onClick={handleClick}
                        onContextMenu={(e) => {
                            e.preventDefault();
                            e.stopPropagation();
                            if (setContextMenuFriendId) setContextMenuFriendId(friendKey);
                        }}
                        onMouseDown={handleStartPress}
                        onMouseUp={handleCancelPress}
                        onMouseLeave={handleCancelPress}
                        onTouchStart={handleStartPress}
                        onTouchEnd={handleCancelPress}
                    >
                        <div className="friend-sidebar-user">
                            <div className={`user-avatar friend-avatar ${hueClass(friend.username)}`}>
                                {friend.username.charAt(0).toUpperCase()}
                            </div>
                            <div className="friend-sidebar-meta">
                                <span className="friend-sidebar-name">
                                    {friend.username}
                                </span>
                                <span className={`friend-sidebar-status ${isOnline ? 'online' : ''}`}>
                                    {isOnline ? '● Online' : '○ Offline'}
                                </span>
                            </div>
                        </div>

                        <div className="friend-sidebar-actions">
                            {unreadDms.has(friend.username) && (
                                <span className="unread-count">{unreadDms.get(friend.username)}</span>
                            )}
                            <span className="friend-dm-icon" title="Open private chat">
                                <Icon name="message" size={14} />
                            </span>
                            <button
                                type="button"
                                className="friend-menu-trigger-btn"
                                title="More options (or long press)"
                                aria-label={`More options for ${friend.username}`}
                                onClick={(e) => {
                                    e.stopPropagation();
                                    if (setContextMenuFriendId) setContextMenuFriendId(isMenuOpen ? null : friendKey);
                                }}
                            >
                                <Icon name="more" size={16} />
                            </button>
                        </div>

                        {isMenuOpen && (
                            <div
                                className="friend-longpress-menu"
                                onClick={(e) => e.stopPropagation()}
                            >
                                <button
                                    type="button"
                                    className="delete-friend-btn"
                                    disabled={removingFriendId === friend.friendshipId}
                                    onClick={(event) => {
                                        event.stopPropagation();
                                        if (setContextMenuFriendId) setContextMenuFriendId(null);
                                        if (handleRemoveFriend) handleRemoveFriend(friend);
                                    }}
                                >
                                    <Icon name="trash" size={13} />
                                    {removingFriendId === friend.friendshipId ? 'Removing…' : 'Remove friend'}
                                </button>
                                <button
                                    type="button"
                                    className="close-menu-btn"
                                    onClick={(event) => {
                                        event.stopPropagation();
                                        if (setContextMenuFriendId) setContextMenuFriendId(null);
                                    }}
                                    title="Close menu"
                                    aria-label="Close menu"
                                >
                                    <Icon name="close" size={13} />
                                </button>
                            </div>
                        )}
                    </div>
                );
            })}
        </>
    );
};

export default FriendList;
