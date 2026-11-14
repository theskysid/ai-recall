import { useState, useEffect, useCallback, useRef } from 'react';
import { channelService } from '../services/channelService';

const useChannels = () => {
    const [channels, setChannels] = useState([]);
    const [isLoading, setIsLoading] = useState(true);
    const [loadError, setLoadError] = useState('');
    const isMountedRef = useRef(true);

    useEffect(() => {
        isMountedRef.current = true;
        return () => {
            isMountedRef.current = false;
        };
    }, []);

    const loadChannels = useCallback(async () => {
        if (!isMountedRef.current) return;
        setIsLoading(true);
        setLoadError('');
        try {
            const data = await channelService.getChannels();
            if (isMountedRef.current) {
                setChannels(Array.isArray(data) ? data : []);
            }
        } catch (error) {
            console.error('Error loading channels:', error);
            if (isMountedRef.current) setLoadError('Could not load your channels. Please try again.');
        } finally {
            if (isMountedRef.current) setIsLoading(false);
        }
    }, []);

    useEffect(() => {
        loadChannels();
        const interval = setInterval(loadChannels, 20000);
        return () => clearInterval(interval);
    }, [loadChannels]);

    /**
     * Insert a newly created/joined channel into the list immediately so the
     * sidebar updates without waiting for the next poll. De-duplicates by id.
     */
    const upsertChannel = useCallback((channel) => {
        if (!channel || channel.id == null) return;
        setChannels((prev) => {
            const withoutDupe = prev.filter((c) => c.id !== channel.id);
            return [...withoutDupe, channel];
        });
    }, []);

    /**
     * Remove a channel from the list (e.g. after leaving).
     */
    const removeChannel = useCallback((channelId) => {
        setChannels((prev) => prev.filter((c) => c.id !== channelId));
    }, []);

    return {
        channels,
        isLoading,
        loadError,
        setChannels,
        loadChannels,
        upsertChannel,
        removeChannel
    };
};

export default useChannels;
