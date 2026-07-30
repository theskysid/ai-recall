import { useState, useEffect, useRef, useCallback } from 'react';
import { friendService } from '../services/friendService';

const useFriends = ({ setOpenChats, setUnreadDms, pushNotification }) => {
    const [friendsList, setFriendsList] = useState([]);
    const [incomingRequestsCount, setIncomingRequestsCount] = useState(0);
    const [isSidebarRefreshing, setIsSidebarRefreshing] = useState(false);
    const [refreshTrigger, setRefreshTrigger] = useState(0);
    const [removingFriendId, setRemovingFriendId] = useState(null);
    const [contextMenuFriendId, setContextMenuFriendId] = useState(null);

    const friendsListRef = useRef([]);
    const isMountedRef = useRef(true);

    useEffect(() => {
        isMountedRef.current = true;
        return () => {
            isMountedRef.current = false;
        };
    }, []);

    useEffect(() => {
        const handleDocumentClick = () => setContextMenuFriendId(null);
        window.addEventListener('click', handleDocumentClick);
        return () => window.removeEventListener('click', handleDocumentClick);
    }, []);

    useEffect(() => {
        friendsListRef.current = friendsList;
    }, [friendsList]);

    const loadFriendsData = useCallback(async (showSpin = false) => {
        if (!isMountedRef.current) return;
        if (showSpin) setIsSidebarRefreshing(true);
        try {
            const [friends, incoming] = await Promise.all([
                friendService.getFriends(),
                friendService.getIncomingRequests()
            ]);
            if (isMountedRef.current) {
                setFriendsList(friends || []);
                setIncomingRequestsCount(incoming ? incoming.length : 0);
            }
        } catch (error) {
            console.error('Error loading friends data:', error);
        } finally {
            if (showSpin && isMountedRef.current) {
                setTimeout(() => setIsSidebarRefreshing(false), 400);
            }
        }
    }, []);

    useEffect(() => {
        loadFriendsData();
        const interval = setInterval(() => {
            loadFriendsData();
        }, 15000);
        return () => clearInterval(interval);
    }, [loadFriendsData]);

    const handleRemoveFriend = async (friend) => {
        if (!friend.friendshipId) {
            return;
        }

        const confirmed = window.confirm(`Remove ${friend.username} from your friends list?`);
        if (!confirmed) {
            return;
        }

        setRemovingFriendId(friend.friendshipId);
        setFriendsList((prev) => prev.filter((f) => f.friendshipId !== friend.friendshipId));
        try {
            await friendService.removeFriend(friend.friendshipId);
            if (setOpenChats) {
                setOpenChats((prev) => prev.filter((chat) => chat.username !== friend.username));
            }
            if (setUnreadDms) {
                setUnreadDms((prev) => {
                    const nextUnread = new Map(prev);
                    nextUnread.delete(friend.username);
                    return nextUnread;
                });
            }
            if (pushNotification) {
                pushNotification(`${friend.username} was removed from your friends list`);
            }
            await loadFriendsData();
        } catch (error) {
            const errorMessage = error.response?.data?.error || error.message || 'Could not remove friend';
            window.alert(errorMessage);
            await loadFriendsData();
        } finally {
            setRemovingFriendId(null);
        }
    };

    return {
        friendsList,
        setFriendsList,
        incomingRequestsCount,
        setIncomingRequestsCount,
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
};

export default useFriends;
