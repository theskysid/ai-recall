import { useState, useEffect, useCallback, useRef } from 'react';
import { channelService } from '../services/channelService';

const useChannels = () => {
    const [channels, setChannels] = useState([]);
    const isMountedRef = useRef(true);

    useEffect(() => {
        isMountedRef.current = true;
        return () => {
            isMountedRef.current = false;
        };
    }, []);

    const loadChannels = useCallback(async () => {
        try {
            const data = await channelService.getChannels();
            if (isMountedRef.current) {
                setChannels(Array.isArray(data) ? data : []);
            }
        } catch (error) {
            console.error('Error loading channels:', error);
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

    return {
        channels,
        setChannels,
        loadChannels,
        upsertChannel
    };
};

export default useChannels;
