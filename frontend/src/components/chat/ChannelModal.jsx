import React, { useState } from 'react';
import { channelService } from '../../services/channelService';
import Icon from '../ui/Icon';
import '../../styles/FindFriendsModal.css';
import '../../styles/Channels.css';

const ChannelModal = ({ onClose, onChannelReady }) => {
    const [activeTab, setActiveTab] = useState('create'); // 'create' | 'join'
    const [isLoading, setIsLoading] = useState(false);
    const [feedback, setFeedback] = useState({ text: '', type: '' }); // type: 'success' | 'error'

    // Create form
    const [name, setName] = useState('');
    const [description, setDescription] = useState('');

    // Join form
    const [inviteCode, setInviteCode] = useState('');

    const showError = (text) => setFeedback({ text, type: 'error' });

    const switchTab = (tab) => {
        setActiveTab(tab);
        setFeedback({ text: '', type: '' });
    };

    const handleCreate = async (e) => {
        e.preventDefault();
        if (!name.trim()) {
            showError('Channel name is required');
            return;
        }
        setIsLoading(true);
        setFeedback({ text: '', type: '' });
        try {
            const channel = await channelService.createChannel(name.trim(), description.trim());
            if (onChannelReady) onChannelReady(channel);
            onClose();
        } catch (err) {
            showError(err.response?.data?.error || err.message || 'Could not create channel');
        } finally {
            setIsLoading(false);
        }
    };

    const handleJoin = async (e) => {
        e.preventDefault();
        const code = inviteCode.trim().toUpperCase();
        if (!code) {
            showError('Invite code is required');
            return;
        }
        setIsLoading(true);
        setFeedback({ text: '', type: '' });
        try {
            const channel = await channelService.joinChannel(code);
            if (onChannelReady) onChannelReady(channel);
            onClose();
        } catch (err) {
            showError(err.response?.data?.error || err.message || 'Could not join channel');
        } finally {
            setIsLoading(false);
        }
    };

    return (
        <div className="ffm-overlay" onClick={onClose}>
            <div className="ffm-sheet" onClick={(e) => e.stopPropagation()}>
                <div className="ffm-handle" />

                {/* Header */}
                <div className="ffm-header">
                    <h3 className="ffm-title">Channels</h3>
                    <div className="ffm-header-right">
                        <button onClick={onClose} className="ffm-close-btn" aria-label="Close">
                            <Icon name="close" size={14} />
                        </button>
                    </div>
                </div>

                {/* Tabs */}
                <div className="ffm-tabs">
                    <button
                        className={`ffm-tab ${activeTab === 'create' ? 'active' : ''}`}
                        onClick={() => switchTab('create')}
                    >
                        <Icon name="plus" size={13} />
                        Create
                    </button>
                    <button
                        className={`ffm-tab ${activeTab === 'join' ? 'active' : ''}`}
                        onClick={() => switchTab('join')}
                    >
                        <Icon name="link" size={13} />
                        Join
                    </button>
                </div>

                {/* Feedback */}
                {feedback.text && (
                    <div className={`ffm-feedback ${feedback.type === 'error' ? 'channel-feedback-error' : ''}`}>
                        {feedback.text}
                    </div>
                )}

                {/* Body */}
                <div className="ffm-body">
                    {activeTab === 'create' && (
                        <form onSubmit={handleCreate} className="channel-form">
                            <div className="channel-field">
                                <label className="channel-label" htmlFor="channel-name">Channel name</label>
                                <input
                                    id="channel-name"
                                    type="text"
                                    placeholder="e.g. product-eng"
                                    value={name}
                                    onChange={(e) => setName(e.target.value)}
                                    className="channel-input"
                                    maxLength={50}
                                    autoFocus
                                    disabled={isLoading}
                                />
                            </div>
                            <div className="channel-field">
                                <label className="channel-label" htmlFor="channel-desc">Description <span className="channel-optional">(optional)</span></label>
                                <textarea
                                    id="channel-desc"
                                    placeholder="What is this channel about?"
                                    value={description}
                                    onChange={(e) => setDescription(e.target.value)}
                                    className="channel-textarea"
                                    maxLength={500}
                                    rows={3}
                                    disabled={isLoading}
                                />
                            </div>
                            <button type="submit" disabled={isLoading || !name.trim()} className="channel-submit-btn">
                                {isLoading ? 'Creating…' : 'Create channel'}
                            </button>
                        </form>
                    )}

                    {activeTab === 'join' && (
                        <form onSubmit={handleJoin} className="channel-form">
                            <div className="channel-field">
                                <label className="channel-label" htmlFor="channel-code">Invite code</label>
                                <input
                                    id="channel-code"
                                    type="text"
                                    placeholder="8-character code"
                                    value={inviteCode}
                                    onChange={(e) => setInviteCode(e.target.value.toUpperCase().slice(0, 8))}
                                    className="channel-input channel-code-input"
                                    maxLength={8}
                                    autoFocus
                                    disabled={isLoading}
                                />
                                <span className="channel-hint">Ask a member for the channel&rsquo;s eight-character invite code.</span>
                            </div>
                            <button type="submit" disabled={isLoading || !inviteCode.trim()} className="channel-submit-btn">
                                {isLoading ? 'Joining…' : 'Join channel'}
                            </button>
                        </form>
                    )}
                </div>
            </div>
        </div>
    );
};

export default ChannelModal;
