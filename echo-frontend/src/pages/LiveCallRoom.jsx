import React, { useState, useEffect } from 'react';
import {
    LiveKitRoom,
    VideoConference,
    RoomAudioRenderer
} from '@livekit/components-react';
import '@livekit/components-styles';
import { callService } from '../services/callService';
import Icon from '../components/ui/Icon';
import '../styles/Channels.css';

const LiveCallRoom = ({ channelId, channelName, onLeaveCall }) => {
    const [token, setToken] = useState('');
    const [serverUrl, setServerUrl] = useState('');
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState('');

    useEffect(() => {
        let isMounted = true;
        setIsLoading(true);
        setError('');
        setToken('');

        (async () => {
            try {
                const data = await callService.getCallToken(channelId);
                if (!isMounted) return;
                const url = data.url || import.meta.env.VITE_LIVEKIT_URL || '';
                if (!data.token || !url) {
                    throw new Error('Video calling is not configured on the server.');
                }
                setToken(data.token);
                setServerUrl(url);
            } catch (err) {
                console.error('Error fetching call token:', err);
                if (isMounted) {
                    setError(err.response?.data?.error || err.message || 'Could not start the call');
                }
            } finally {
                if (isMounted) setIsLoading(false);
            }
        })();

        return () => {
            isMounted = false;
        };
    }, [channelId]);

    if (isLoading) {
        return (
            <div className="channel-call-container">
                <div className="channel-call-status">Connecting to the call…</div>
            </div>
        );
    }

    if (error || !token || !serverUrl) {
        return (
            <div className="channel-call-container">
                <div className="channel-call-status channel-call-error">
                    <p>{error || 'Could not start the call'}</p>
                    <button className="channel-call-exit-btn" onClick={onLeaveCall}>Back to chat</button>
                </div>
            </div>
        );
    }

    return (
        <div className="channel-call-container">
            <div className="channel-call-bar">
                <span className="channel-call-title">
                    <Icon name="video" size={13} />
                    Call · #{channelName}
                </span>
                <button className="channel-call-exit-btn" onClick={onLeaveCall} title="Return to chat">
                    Back to chat
                </button>
            </div>
            <div className="channel-call-stage">
                <LiveKitRoom
                    token={token}
                    serverUrl={serverUrl}
                    connect={true}
                    video={true}
                    audio={true}
                    onDisconnected={onLeaveCall}
                    data-lk-theme="default"
                    style={{ height: '100%' }}
                >
                    <VideoConference />
                    <RoomAudioRenderer />
                </LiveKitRoom>
            </div>
        </div>
    );
};

export default LiveCallRoom;
