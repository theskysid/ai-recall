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

// CURRENT is the fallback: rows written before the status column existed, and
// anything the backend sends with an unrecognised value, read as still standing.
const STATUS_TAG = {
    SUPERSEDED: { label: 'Superseded', className: 'ch-tag-old' },
    UNRESOLVED: { label: 'Unresolved', className: 'ch-tag-clash' },
    CURRENT: { label: 'Active', className: 'ch-tag-live' }
};

const statusOf = (d) => {
    if (d.status && STATUS_TAG[d.status]) return d.status;
    return d.superseded ? 'SUPERSEDED' : 'CURRENT';
};

const SNIPPET_CHARS = 160;

const MemoryPanel = ({ channelId, onViewMessage }) => {
    const [collapsed, setCollapsed] = useState(true);
    const [tab, setTab] = useState('decisions'); // 'decisions' | 'transcripts'
    const [decisions, setDecisions] = useState([]);
    const [transcripts, setTranscripts] = useState([]);
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState('');
    const [expandedId, setExpandedId] = useState(null);
    const [viewError, setViewError] = useState('');

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
        setViewError('');
    }, [channelId]);

    // Take a decision back to where it came from.
    //
    // MESSAGE items scroll to the message itself. TRANSCRIPT items open the
    // Transcripts tab and expand the whole call: transcripts render as one
    // block per call, so there is nothing finer to point at. Addressing an
    // individual transcript line would need a UI that does not exist yet.
    const viewSource = (d) => {
        setViewError('');
        if (d.sourceType === 'TRANSCRIPT') {
            setTab('transcripts');
            setExpandedId(d.sourceId);
            return;
        }
        if (!onViewMessage || !onViewMessage(d.sourceId)) {
            setViewError('That message is no longer loaded in this channel.');
        }
    };

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

                    {viewError && (
                        <div className="ch-ask-fail" role="status">
                            <Icon name="alert" size={14} />
                            {viewError}
                        </div>
                    )}

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
                                    {decisions.map((d) => {
                                        const status = statusOf(d);
                                        const tag = STATUS_TAG[status];
                                        const snippet = (d.content || '').length > SNIPPET_CHARS
                                            ? `${d.content.slice(0, SNIPPET_CHARS).trimEnd()}…`
                                            : d.content;
                                        return (
                                            <li key={d.id}>
                                                <div className={`ch-dec ${status === 'SUPERSEDED' ? 'is-old' : ''} ${status === 'UNRESOLVED' ? 'is-clash' : ''}`}>
                                                    <span className="ch-dec-dot" />
                                                    <div>
                                                        {d.title && <p className="ch-dec-title">{d.title}</p>}
                                                        <p className="ch-dec-text">{snippet}</p>
                                                        <div className="ch-dec-meta">
                                                            <span>{formatTime(d.createdAt)}</span>
                                                            <span className={`ch-tag ${tag.className}`}>{tag.label}</span>
                                                            {d.sourceId != null && (
                                                                <button
                                                                    type="button"
                                                                    className="ch-dec-jump"
                                                                    onClick={() => viewSource(d)}
                                                                >
                                                                    {d.sourceType === 'TRANSCRIPT' ? 'View call' : 'View message'}
                                                                </button>
                                                            )}
                                                        </div>
                                                    </div>
                                                </div>
                                            </li>
                                        );
                                    })}
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
