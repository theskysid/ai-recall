import React, { useState } from 'react';
import Icon from '../ui/Icon';
import '../../styles/ChannelPage.css';

const formatDay = (ts) => {
    if (!ts) return '';
    let s = typeof ts === 'string' ? ts : ts.toString();
    if (!s.endsWith('Z') && !s.includes('+') && !s.includes('GMT')) s += 'Z';
    const d = new Date(s);
    return isNaN(d.getTime()) ? '' : d.toLocaleDateString([], { month: 'short', day: 'numeric' });
};

const formatTime = (ts) => {
    if (!ts) return '';
    let s = typeof ts === 'string' ? ts : ts.toString();
    if (!s.endsWith('Z') && !s.includes('+') && !s.includes('GMT')) s += 'Z';
    const d = new Date(s);
    return isNaN(d.getTime()) ? '' : d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
};

// CURRENT is the fallback: rows written before the status column existed, and
// anything the backend sends with an unrecognised value, read as still standing.
const STATUS_TAG = {
    SUPERSEDED: { label: 'Superseded', className: 'is-old' },
    UNRESOLVED: { label: 'Unresolved', className: 'is-clash' },
    CURRENT: { label: 'Decided', className: 'is-live' }
};

const statusOf = (d) => {
    if (d.status && STATUS_TAG[d.status]) return d.status;
    return d.superseded ? 'SUPERSEDED' : 'CURRENT';
};

/* The channel's memory, as a rail beside the conversation rather than a
   drawer on top of it. It is the product's whole argument, so it is visible
   while you read — but it never takes height from the thread.

   Data is fetched by ChannelChat and passed in, because the thread marks the
   same decisions inline and the two must not disagree. */
const MemoryPanel = ({
    channelName,
    decisions = [],
    transcripts = [],
    isLoading,
    error,
    onReload,
    onViewMessage,
    onClose
}) => {
    const [tab, setTab] = useState('decisions');
    const [expandedId, setExpandedId] = useState(null);
    const [viewError, setViewError] = useState('');

    // Take a decision back to where it came from.
    //
    // MESSAGE items scroll to the message itself. TRANSCRIPT items open the
    // Transcripts tab and expand the whole call: transcripts render as one
    // block per call, so there is nothing finer to point at.
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

    const renderBody = () => {
        if (isLoading) {
            return (
                <div className="ch-working" role="status">
                    <span className="ch-pulse" />
                    Opening the record…
                </div>
            );
        }

        if (error) {
            return (
                <div className="ch-ask-fail" role="alert">
                    <Icon name="alert" size={14} />
                    {error}
                </div>
            );
        }

        if (tab === 'decisions') {
            if (decisions.length === 0) {
                return (
                    <p className="ch-rail-blank">
                        Nothing decided here yet. Decisions are picked out of the conversation
                        as the channel settles things.
                    </p>
                );
            }
            return (
                <ul className="ch-rail-list">
                    {decisions.map((d) => {
                        const status = statusOf(d);
                        const tag = STATUS_TAG[status];
                        return (
                            <li key={d.id}>
                                <article className={`ch-card ${tag.className}`}>
                                    <div className="ch-card-top">
                                        <span className={`ch-tag ${tag.className}`}>
                                            <Icon name="check" size={11} />
                                            {tag.label}
                                        </span>
                                        <span className="ch-card-day">{formatDay(d.createdAt)}</span>
                                    </div>
                                    {d.title && <h4 className="ch-card-title">{d.title}</h4>}
                                    <p className="ch-card-text">{d.content}</p>
                                    <div className="ch-card-foot">
                                        <span className="ch-card-src">
                                            {d.sourceAuthor && (
                                                <span className="ch-card-who">{d.sourceAuthor}</span>
                                            )}
                                            {formatTime(d.createdAt)}
                                        </span>
                                        {d.sourceId != null && (
                                            <button
                                                type="button"
                                                className="ch-card-jump"
                                                onClick={() => viewSource(d)}
                                            >
                                                {d.sourceType === 'TRANSCRIPT' ? 'View call' : 'View message'}
                                                <Icon name="arrowUpRight" size={12} />
                                            </button>
                                        )}
                                    </div>
                                </article>
                            </li>
                        );
                    })}
                </ul>
            );
        }

        if (transcripts.length === 0) {
            return (
                <p className="ch-rail-blank">
                    No call transcripts yet. Start a call and it will be written down here
                    when it ends.
                </p>
            );
        }
        return (
            <ul className="ch-rail-list">
                {transcripts.map((t) => {
                    const open = expandedId === t.id;
                    return (
                        <li key={t.id}>
                            <article className="ch-card">
                                <button
                                    type="button"
                                    className="ch-tr-head"
                                    onClick={() => setExpandedId(open ? null : t.id)}
                                    aria-expanded={open}
                                >
                                    <Icon name="phone" size={12} />
                                    Call · {formatDay(t.createdAt)}
                                    <span className="ch-mem-chevron">
                                        <Icon name={open ? 'chevronDown' : 'chevronRight'} size={12} />
                                    </span>
                                </button>
                                {open && <p className="ch-tr-text">{t.fullTranscript}</p>}
                            </article>
                        </li>
                    );
                })}
            </ul>
        );
    };

    return (
        <div className="ch-mem">
            <header className="ch-rail-head">
                <h3 className="ch-rail-title">
                    <Icon name="sparkle" size={15} />
                    Channel memory
                </h3>
                <button
                    type="button"
                    className="ch-rail-x"
                    onClick={onClose}
                    title="Hide channel memory"
                    aria-label="Hide channel memory"
                >
                    <Icon name="close" size={14} />
                </button>
                <p className="ch-rail-sub">
                    What #{channelName || 'this channel'} settled, without the scrolling.
                </p>
            </header>

            <div className="ch-rail-tabs" role="tablist">
                <button
                    type="button"
                    role="tab"
                    aria-selected={tab === 'decisions'}
                    className={`ch-rail-tab ${tab === 'decisions' ? 'is-on' : ''}`}
                    onClick={() => setTab('decisions')}
                >
                    <Icon name="check" size={13} />
                    Decisions
                    {decisions.length > 0 && <span className="ch-rail-n">{decisions.length}</span>}
                </button>
                <button
                    type="button"
                    role="tab"
                    aria-selected={tab === 'transcripts'}
                    className={`ch-rail-tab ${tab === 'transcripts' ? 'is-on' : ''}`}
                    onClick={() => setTab('transcripts')}
                >
                    <Icon name="note" size={13} />
                    Transcripts
                    {transcripts.length > 0 && <span className="ch-rail-n">{transcripts.length}</span>}
                </button>
                <span className="ch-seg-spacer" />
                <button
                    type="button"
                    className="ch-rail-refresh"
                    onClick={onReload}
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

            <div className="ch-rail-scroll">{renderBody()}</div>
        </div>
    );
};

export default MemoryPanel;
