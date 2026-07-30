import React, { useState, useEffect, useCallback } from 'react';
import { friendService } from '../services/friendService';
import '../styles/FindFriendsModal.css';

const FindFriendsModal = ({ onClose, onFriendsChange, refreshTrigger }) => {
    const [activeTab, setActiveTab] = useState('search'); // 'search' | 'incoming' | 'rejected'
    const [searchQuery, setSearchQuery] = useState('');
    const [searchResults, setSearchResults] = useState([]);
    const [incomingRequests, setIncomingRequests] = useState([]);
    const [rejectedRequests, setRejectedRequests] = useState([]);
    const [isLoading, setIsLoading] = useState(false);
    const [isRefreshing, setIsRefreshing] = useState(false);
    const [feedbackMsg, setFeedbackMsg] = useState('');

    const loadRequests = useCallback(async (showSpin = false) => {
        if (showSpin) setIsRefreshing(true);
        try {
            const [inReqs, rejReqs] = await Promise.all([
                friendService.getIncomingRequests(),
                friendService.getRejectedRequests()
            ]);
            setIncomingRequests(inReqs || []);
            setRejectedRequests(rejReqs || []);
        } catch (err) {
            console.error('Error loading friend requests:', err);
        } finally {
            if (showSpin) setTimeout(() => setIsRefreshing(false), 400);
        }
    }, []);

    useEffect(() => {
        loadRequests();
    }, [loadRequests, refreshTrigger]);

    const showFeedback = (msg) => {
        setFeedbackMsg(msg);
        setTimeout(() => setFeedbackMsg(''), 3000);
    };

    const handleSearch = async (e) => {
        if (e) e.preventDefault();
        if (!searchQuery.trim() || searchQuery.trim().length < 2) {
            alert('Please enter at least 2 characters to search');
            return;
        }
        setIsLoading(true);
        try {
            const results = await friendService.searchUsers(searchQuery.trim());
            setSearchResults(results || []);
        } catch (err) {
            console.error('Search failed:', err);
            const msg = err.response?.data?.error || err.response?.data?.message || err.message || 'Search failed';
            alert(msg);
        } finally {
            setIsLoading(false);
        }
    };

    const handleSendRequest = async (username) => {
        try {
            await friendService.sendRequest(username);
            showFeedback(`Friend request sent to ${username}!`);
            setSearchResults(prev =>
                prev.map(u => u.username === username ? { ...u, friendshipStatus: 'PENDING_OUTGOING' } : u)
            );
            loadRequests();
            if (onFriendsChange) onFriendsChange();
        } catch (err) {
            const msg = err.response?.data?.error || err.response?.data?.message || err.message || 'Could not send friend request';
            alert(msg);
        }
    };

    const handleAcceptRequest = async (friendshipId, senderUsername) => {
        try {
            await friendService.acceptRequest(friendshipId);
            showFeedback(`Accepted friend request from ${senderUsername}!`);
            loadRequests();
            if (onFriendsChange) onFriendsChange();
        } catch (err) {
            const msg = err.response?.data?.error || err.response?.data?.message || err.message || 'Could not accept friend request';
            alert(msg);
        }
    };

    const handleRejectRequest = async (friendshipId) => {
        try {
            await friendService.rejectRequest(friendshipId);
            showFeedback('Friend request rejected.');
            loadRequests();
        } catch (err) {
            const msg = err.response?.data?.error || err.response?.data?.message || err.message || 'Could not reject friend request';
            alert(msg);
        }
    };

    const handleCancelRejected = async (friendshipId) => {
        try {
            await friendService.cancelRequest(friendshipId);
            showFeedback('Removed from rejected list.');
            loadRequests();
        } catch (err) {
            const msg = err.response?.data?.error || err.response?.data?.message || err.message || 'Could not remove';
            alert(msg);
        }
    };

    return (
        <div className="ffm-overlay" onClick={onClose}>
            <div className="ffm-sheet" onClick={e => e.stopPropagation()}>
                {/* Sheet handle */}
                <div className="ffm-handle" />

                {/* Header */}
                <div className="ffm-header">
                    <h3 className="ffm-title">People</h3>
                    <div className="ffm-header-right">
                        <button
                            onClick={() => loadRequests(true)}
                            className={`ffm-refresh-btn ${isRefreshing ? 'spin' : ''}`}
                            title="Refresh"
                        >
                            🔄
                        </button>
                        <button onClick={onClose} className="ffm-close-btn">✕</button>
                    </div>
                </div>

                {/* Tabs */}
                <div className="ffm-tabs">
                    <button
                        className={`ffm-tab ${activeTab === 'search' ? 'active' : ''}`}
                        onClick={() => setActiveTab('search')}
                    >
                        🔍 Search
                    </button>
                    <button
                        className={`ffm-tab ${activeTab === 'incoming' ? 'active' : ''}`}
                        onClick={() => { setActiveTab('incoming'); loadRequests(); }}
                    >
                        📥 Incoming
                        {incomingRequests.length > 0 && (
                            <span className="ffm-tab-badge">{incomingRequests.length}</span>
                        )}
                    </button>
                    <button
                        className={`ffm-tab ${activeTab === 'rejected' ? 'active' : ''}`}
                        onClick={() => { setActiveTab('rejected'); loadRequests(); }}
                    >
                        🚫 Rejected
                        {rejectedRequests.length > 0 && (
                            <span className="ffm-tab-badge">{rejectedRequests.length}</span>
                        )}
                    </button>
                </div>

                {/* Feedback */}
                {feedbackMsg && (
                    <div className="ffm-feedback">{feedbackMsg}</div>
                )}

                {/* Body */}
                <div className="ffm-body">

                    {/* ── Search Tab ── */}
                    {activeTab === 'search' && (
                        <div className="ffm-tab-content">
                            <form onSubmit={handleSearch} className="ffm-search-form">
                                <div className="ffm-search-wrap">
                                    <svg className="ffm-search-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" width="16" height="16">
                                        <circle cx="11" cy="11" r="8" /><line x1="21" y1="21" x2="16.65" y2="16.65" />
                                    </svg>
                                    <input
                                        type="text"
                                        placeholder="Search by username…"
                                        value={searchQuery}
                                        onChange={e => setSearchQuery(e.target.value)}
                                        className="ffm-search-input"
                                        autoFocus
                                    />
                                </div>
                                <button type="submit" disabled={isLoading || searchQuery.trim().length < 2} className="ffm-search-btn">
                                    {isLoading ? '…' : 'Search'}
                                </button>
                            </form>

                            {searchResults.length === 0 ? (
                                <div className="ffm-empty">
                                    <span className="ffm-empty-icon">🔍</span>
                                    <p>Search for people to add as friends</p>
                                </div>
                            ) : (
                                searchResults.map(user => (
                                    <div key={user.id} className="ffm-person-row">
                                        <div className="ffm-person-avatar">
                                            {user.username.charAt(0).toUpperCase()}
                                        </div>
                                        <div className="ffm-person-info">
                                            <span className="ffm-person-name">{user.username}</span>
                                            {user.displayName && <span className="ffm-person-sub">{user.displayName}</span>}
                                        </div>
                                        <div className="ffm-person-action">
                                            {user.friendshipStatus === 'ACCEPTED' && (
                                                <span className="ffm-badge-friends">✓ Friends</span>
                                            )}
                                            {user.friendshipStatus === 'PENDING_OUTGOING' && (
                                                <span className="ffm-badge-pending">Sent</span>
                                            )}
                                            {user.friendshipStatus === 'PENDING_INCOMING' && (
                                                <span className="ffm-badge-pending">Check Incoming</span>
                                            )}
                                            {(user.friendshipStatus === 'NONE' || !user.friendshipStatus) && (
                                                <button onClick={() => handleSendRequest(user.username)} className="ffm-btn-add">
                                                    Add
                                                </button>
                                            )}
                                        </div>
                                    </div>
                                ))
                            )}
                        </div>
                    )}

                    {/* ── Incoming Tab ── */}
                    {activeTab === 'incoming' && (
                        <div className="ffm-tab-content">
                            {incomingRequests.length === 0 ? (
                                <div className="ffm-empty">
                                    <span className="ffm-empty-icon">📭</span>
                                    <p>No pending friend requests</p>
                                    <button onClick={() => loadRequests(true)} className="ffm-btn-refresh">🔄 Refresh</button>
                                </div>
                            ) : (
                                incomingRequests.map(req => (
                                    <div key={req.id} className="ffm-person-row">
                                        <div className="ffm-person-avatar" style={{ background: 'linear-gradient(135deg,#667eea,#764ba2)' }}>
                                            {req.requesterUsername.charAt(0).toUpperCase()}
                                        </div>
                                        <div className="ffm-person-info">
                                            <span className="ffm-person-name">{req.requesterUsername}</span>
                                            <span className="ffm-person-sub">Wants to be friends</span>
                                        </div>
                                        <div className="ffm-person-action ffm-action-row">
                                            <button onClick={() => handleAcceptRequest(req.id, req.requesterUsername)} className="ffm-btn-accept">✓</button>
                                            <button onClick={() => handleRejectRequest(req.id)} className="ffm-btn-reject">✕</button>
                                        </div>
                                    </div>
                                ))
                            )}
                        </div>
                    )}

                    {/* ── Rejected Tab ── */}
                    {activeTab === 'rejected' && (
                        <div className="ffm-tab-content">
                            {rejectedRequests.length === 0 ? (
                                <div className="ffm-empty">
                                    <span className="ffm-empty-icon">✅</span>
                                    <p>No rejected requests</p>
                                </div>
                            ) : (
                                rejectedRequests.map(req => (
                                    <div key={req.id} className="ffm-person-row">
                                        <div className="ffm-person-avatar" style={{ background: 'linear-gradient(135deg,#ef4444,#b91c1c)' }}>
                                            {req.addresseeUsername.charAt(0).toUpperCase()}
                                        </div>
                                        <div className="ffm-person-info">
                                            <span className="ffm-person-name">{req.addresseeUsername}</span>
                                            <span className="ffm-person-sub">Rejected your request</span>
                                        </div>
                                        <div className="ffm-person-action">
                                            <button onClick={() => handleCancelRejected(req.id)} className="ffm-btn-unblock">
                                                Remove
                                            </button>
                                        </div>
                                    </div>
                                ))
                            )}
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
};

export default FindFriendsModal;

