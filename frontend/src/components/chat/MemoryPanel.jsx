import React, { useState, useEffect, useCallback } from 'react';
import { channelService } from '../../services/channelService';
import { callService } from '../../services/callService';
import Icon from '../ui/Icon';
import '../../styles/ChannelPage.css';

const formatTime = (ts) => {
    if (!ts) return '';
    let s = typeof ts === 'string' ? ts : ts.toString();
    if (typeof s === 'string' && !s.endsWith('Z') && !s.includes('+') && !s.includes('GMT')) s += 'Z';
    const d = new Date(s);
    return isNaN(d.getTime()) ? '' : d.toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
};

const MemoryPanel = ({ channelId }) => {
    const [collapsed, setCollapsed] = useState(true);
    const [tab, setTab] = useState('decisions'); // 'decisions' | 'transcripts'
    const [decisions, setDecisions] = useState([]);
    const [transcripts, setTranscripts] = useState([]);
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState('');
    const [expandedId, setExpandedId] = useState(null);

    const load = useCallback(async () => {
        if (channelId == null) return;
        setIsLoading(true);
        setError('');
        try {
            const [dec, tr] = await Promise.all([
                channelService.getDecisions(channelId),
                callService.getTranscripts(channelId)
            ]);
            setDecisions(Array.isArray(dec) ? dec : []);
            setTranscripts(Array.isArray(tr) ? tr : []);
        } catch (err) {
            console.error('Memory panel load failed:', err);
            setError('Could not load channel memory.');
        } finally {
            setIsLoading(false);
        }
    }, [channelId]);

    // Load whenever opened or the channel changes while open.
    useEffect(() => {
        if (!collapsed) load();
    }, [collapsed, load]);

    // Reset when switching channels.
    useEffect(() => {
        setCollapsed(true);
        setExpandedId(null);
    }, [channelId]);

    const hasCounts = !collapsed && !isLoading && !error;

    return (
        <div className="ch-mem">
            <button
                type="button"
                className="ch-mem-toggle"
                onClick={() => setCollapsed((v) => !v)}
                aria-expanded={!collapsed}
            >
                <Icon name="archive" size={13} />
                Channel memory
                {hasCounts && (decisions.length > 0 || transcripts.length > 0) && (
                    <span className="ch-mem-counts">
                        {decisions.length > 0 && (
                            <span className="ch-count ch-count-rec">
                                {decisions.length} decided
                            </span>
                        )}
                        {transcripts.length > 0 && (
                            <span className="ch-count">
                                {transcripts.length} transcribed
                            </span>
                        )}
                    </span>
                )}
                <span className="ch-mem-chevron">
                    <Icon name={collapsed ? 'chevronRight' : 'chevronDown'} size={13} />
                </span>
            </button>

            {!collapsed && (
                <div className="ch-mem-body">
                    <div className="ch-seg" role="tablist">
                        <button
                            type="button"
                            role="tab"
                            aria-selected={tab === 'decisions'}
                            className={`ch-seg-btn ${tab === 'decisions' ? 'is-on' : ''}`}
                            onClick={() => setTab('decisions')}
                        >
                            <Icon name="pin" size={12} />
                            Decisions
                        </button>
                        <button
                            type="button"
                            role="tab"
                            aria-selected={tab === 'transcripts'}
                            className={`ch-seg-btn ${tab === 'transcripts' ? 'is-on' : ''}`}
                            onClick={() => setTab('transcripts')}
                        >
                            <Icon name="note" size={12} />
                            Transcripts
                        </button>
                        <span className="ch-seg-spacer" />
                        <button
                            type="button"
                            className="ch-seg-refresh"
                            onClick={load}
                            title="Refresh channel memory"
                            aria-label="Refresh channel memory"
                            disabled={isLoading}
                        >
                            <Icon name="refresh" size={13} />
                        </button>
                    </div>

                    {isLoading ? (
                        <div className="ch-working" role="status">
                            <span className="ch-pulse" />
                            Opening the record…
                        </div>
                    ) : error ? (
                        <div className="ch-ask-fail" role="alert">
                            <Icon name="alert" size={14} />
                            {error}
                        </div>
                    ) : tab === 'decisions' ? (
                        decisions.length === 0 ? (
                            <p className="ch-note">
                                Nothing decided here yet. Decisions are picked out of the
                                conversation as the channel settles things.
                            </p>
                        ) : (
                            <div className="ch-mem-scroll">
                                <ul className="ch-mem-list">
                                    {decisions.map((d) => (
                                        <li key={d.id}>
                                            <div className={`ch-dec ${d.superseded ? 'is-old' : ''}`}>
                                                <span className="ch-dec-dot" />
                                                <div>
                                                    <p className="ch-dec-text">{d.content}</p>
                                                    <div className="ch-dec-meta">
                                                        <span>{formatTime(d.createdAt)}</span>
                                                        <span className={`ch-tag ${d.superseded ? 'ch-tag-old' : 'ch-tag-live'}`}>
                                                            {d.superseded ? 'Superseded' : 'Active'}
                                                        </span>
                                                    </div>
                                                </div>
                                            </div>
                                        </li>
                                    ))}
                                </ul>
                            </div>
                        )
                    ) : (
                        transcripts.length === 0 ? (
                            <p className="ch-note">
                                No call transcripts yet. Start a call and it will be written
                                down here when it ends.
                            </p>
                        ) : (
                            <div className="ch-mem-scroll">
                                <ul className="ch-mem-list">
                                    {transcripts.map((t) => {
                                        const open = expandedId === t.id;
                                        return (
                                            <li key={t.id}>
                                                <button
                                                    type="button"
                                                    className="ch-tr-head"
                                                    onClick={() => setExpandedId(open ? null : t.id)}
                                                    aria-expanded={open}
                                                >
                                                    <Icon name="phone" size={12} />
                                                    Call · {formatTime(t.createdAt)}
                                                    <span className="ch-mem-chevron">
                                                        <Icon name={open ? 'chevronDown' : 'chevronRight'} size={12} />
                                                    </span>
                                                </button>
                                                {open && <p className="ch-tr-text">{t.fullTranscript}</p>}
                                            </li>
                                        );
                                    })}
                                </ul>
                            </div>
                        )
                    )}
                </div>
            )}
        </div>
    );
};

export default MemoryPanel;
