import React, { useState, useEffect, useCallback } from 'react';
import { channelService } from '../../services/channelService';
import { callService } from '../../services/callService';
import '../../styles/Channels.css';

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

    return (
        <div className={`memory-panel ${collapsed ? 'collapsed' : ''}`}>
            <button
                type="button"
                className="memory-panel-header"
                onClick={() => setCollapsed((v) => !v)}
                aria-expanded={!collapsed}
            >
                <span className="memory-panel-title">🧠 Channel Memory</span>
                <span className="memory-panel-chevron">{collapsed ? '▸' : '▾'}</span>
            </button>

            {!collapsed && (
                <div className="memory-panel-body">
                    <div className="memory-tabs">
                        <button
                            className={`memory-tab ${tab === 'decisions' ? 'active' : ''}`}
                            onClick={() => setTab('decisions')}
                        >
                            📌 Decisions {decisions.length > 0 && <span className="memory-count">{decisions.length}</span>}
                        </button>
                        <button
                            className={`memory-tab ${tab === 'transcripts' ? 'active' : ''}`}
                            onClick={() => setTab('transcripts')}
                        >
                            📝 Transcripts {transcripts.length > 0 && <span className="memory-count">{transcripts.length}</span>}
                        </button>
                        <button className="memory-refresh" onClick={load} title="Refresh" disabled={isLoading}>🔄</button>
                    </div>

                    {isLoading ? (
                        <div className="memory-status"><span className="ask-ai-spinner" /> Loading…</div>
                    ) : error ? (
                        <div className="ask-ai-error">⚠️ {error}</div>
                    ) : tab === 'decisions' ? (
                        decisions.length === 0 ? (
                            <div className="memory-empty">No decisions captured yet.</div>
                        ) : (
                            <ul className="memory-list">
                                {decisions.map((d) => (
                                    <li key={d.id} className={`decision-item ${d.superseded ? 'superseded' : ''}`}>
                                        <div className="decision-dot" />
                                        <div className="decision-body">
                                            <p className="decision-text">{d.content}</p>
                                            <div className="decision-meta">
                                                <span>{formatTime(d.createdAt)}</span>
                                                {d.superseded
                                                    ? <span className="decision-badge superseded-badge">Superseded</span>
                                                    : <span className="decision-badge active-badge">Active</span>}
                                            </div>
                                        </div>
                                    </li>
                                ))}
                            </ul>
                        )
                    ) : (
                        transcripts.length === 0 ? (
                            <div className="memory-empty">No call transcripts yet.</div>
                        ) : (
                            <ul className="memory-list">
                                {transcripts.map((t) => {
                                    const open = expandedId === t.id;
                                    return (
                                        <li key={t.id} className="transcript-item">
                                            <button
                                                type="button"
                                                className="transcript-head"
                                                onClick={() => setExpandedId(open ? null : t.id)}
                                            >
                                                <span>📞 Call · {formatTime(t.createdAt)}</span>
                                                <span className="memory-panel-chevron">{open ? '▾' : '▸'}</span>
                                            </button>
                                            {open && <p className="transcript-text">{t.fullTranscript}</p>}
                                        </li>
                                    );
                                })}
                            </ul>
                        )
                    )}
                </div>
            )}
        </div>
    );
};

export default MemoryPanel;
