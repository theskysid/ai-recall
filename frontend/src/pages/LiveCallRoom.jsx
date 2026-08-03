import React, { useState, useEffect } from 'react';
import {
    LiveKitRoom,
    VideoConference,
    RoomAudioRenderer,
    useRoomContext
} from '@livekit/components-react';
import { RoomEvent, Track } from 'livekit-client';
import '@livekit/components-styles';
import { callService } from '../services/callService';
import Icon from '../components/ui/Icon';
import '../styles/Channels.css';

// Anything smaller than this is a stray start/stop, not a real call.
const MIN_RECORDING_BYTES = 8000;

const RECORDING_MIME_TYPES = ['audio/webm;codecs=opus', 'audio/webm', 'audio/mp4'];

const pickMimeType = () =>
    RECORDING_MIME_TYPES.find((type) => MediaRecorder.isTypeSupported(type)) || '';

/**
 * Records the call by mixing every audio track the browser has — the local mic
 * plus each subscribed remote track — into one WebAudio destination, then
 * uploading the capture when the component unmounts (call ended or left).
 *
 * Renders nothing. Mounted inside LiveKitRoom so it can reach the room context.
 */
const CallRecorder = ({ channelId }) => {
    const room = useRoomContext();

    useEffect(() => {
        const AudioCtx = window.AudioContext || window.webkitAudioContext;
        if (!room || !AudioCtx || typeof MediaRecorder === 'undefined') return undefined;

        const mimeType = pickMimeType();
        const context = new AudioCtx();
        const mixer = context.createMediaStreamDestination();
        const mixed = new Set();
        const chunks = [];

        // Route one LiveKit audio track into the mix. The mixer is never wired to
        // context.destination, so nothing here is played back — RoomAudioRenderer
        // still owns playback, and that <audio> attachment is also what keeps
        // remote tracks flowing into WebAudio in Chrome.
        const mix = (track) => {
            const source = track?.mediaStreamTrack;
            if (!source || track.kind !== Track.Kind.Audio || mixed.has(source.id)) return;
            mixed.add(source.id);
            context.createMediaStreamSource(new MediaStream([source])).connect(mixer);
        };

        const onTrackSubscribed = (track) => mix(track);
        const onLocalTrackPublished = (publication) => mix(publication.track);

        // Whatever is already live at mount, plus everything that joins later.
        room.remoteParticipants?.forEach((participant) =>
            participant.audioTrackPublications.forEach((publication) => mix(publication.track)));
        room.localParticipant?.audioTrackPublications.forEach((publication) => mix(publication.track));

        room.on(RoomEvent.TrackSubscribed, onTrackSubscribed);
        room.on(RoomEvent.LocalTrackPublished, onLocalTrackPublished);

        const recorder = new MediaRecorder(mixer.stream, mimeType ? { mimeType } : undefined);

        recorder.ondataavailable = (event) => {
            if (event.data.size > 0) chunks.push(event.data);
        };

        // Fires after unmount — MediaRecorder and the upload both outlive React here,
        // which is exactly what makes "record until they leave" work.
        recorder.onstop = () => {
            context.close().catch(() => {});
            const type = recorder.mimeType || 'audio/webm';
            const blob = new Blob(chunks, { type });
            if (blob.size < MIN_RECORDING_BYTES) return;
            const extension = type.includes('mp4') ? 'm4a' : 'webm';
            callService
                .uploadRecording(channelId, blob, `call-${channelId}.${extension}`)
                .catch((err) => console.error('Failed to upload call recording:', err));
        };

        context.resume().catch(() => {});
        recorder.start(1000); // flush a chunk per second so long calls stay in memory-sized pieces

        return () => {
            room.off(RoomEvent.TrackSubscribed, onTrackSubscribed);
            room.off(RoomEvent.LocalTrackPublished, onLocalTrackPublished);
            if (recorder.state !== 'inactive') {
                recorder.stop();
            } else {
                context.close().catch(() => {});
            }
        };
    }, [room, channelId]);

    return null;
};

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
                    <CallRecorder channelId={channelId} />
                </LiveKitRoom>
            </div>
        </div>
    );
};

export default LiveCallRoom;
