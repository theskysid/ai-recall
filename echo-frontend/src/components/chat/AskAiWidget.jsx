import React, { useEffect, useRef, useState } from 'react';
import { channelService } from '../../services/channelService';
import Icon from '../ui/Icon';
import '../../styles/ChannelPage.css';

/* Starter questions as [chip label, query sent]. The label stays short so
   the row does not wrap; the query stays a full sentence because that is
   what the retrieval reads. They exist because an empty box does not tell
   you that the channel can be questioned at all. */
const STARTERS = [
    ['Decisions', 'What was decided here?'],
    ['This week', 'Catch me up on this week'],
    ['Last call', 'What came out of the last call?']
];

const AskAiWidget = ({ channelId, channelName }) => {
    const [query, setQuery] = useState('');
    const [turns, setTurns] = useState([]);
    const [isLoading, setIsLoading] = useState(false);
    const [open, setOpen] = useState(false);
    const logRef = useRef(null);

    /* Follow the newest turn, the way any chat log does. */
    useEffect(() => {
        const log = logRef.current;
        if (log) log.scrollTop = log.scrollHeight;
    }, [turns]);

    const patchLast = (patch) =>
        setTurns((t) => t.map((turn, i) => (i === t.length - 1 ? { ...turn, ...patch } : turn)));

    // ponytail: each ask is retrieved independently — no follow-up context.
    // Thread prior turns into the query if "and what about X?" needs to work.
    const ask = async (q) => {
        if (!q || channelId == null || isLoading) return;

        setQuery('');
        setTurns((t) => [...t, { q, pending: true }]);
        setIsLoading(true);
        try {
            const data = await channelService.ask(channelId, q);
            patchLast({
                pending: false,
                answer: data.answer || 'No answer returned.',
                sourceIds: Array.isArray(data.sourceIds) ? data.sourceIds : []
            });
        } catch (err) {
            console.error('Ask AI failed:', err);
            patchLast({ pending: false, error: 'AI is currently unavailable. Please try again.' });
        } finally {
            setIsLoading(false);
        }
    };

    const handleAsk = (e) => {
        e.preventDefault();
        ask(query.trim());
    };

    /* Collapsed, this is a single button in the corner of the thread; the
       panel is the same markup, just revealed. Nothing about the request
       path changes with it. */
    if (!open) {
        return (
            <button
                type="button"
                className="ch-ask-fab"
                onClick={() => setOpen(true)}
                aria-expanded={false}
            >
                <Icon name="sparkle" size={15} />
                Ask Recall
            </button>
        );
    }

    return (
        <div className="ch-ask">
            <div className="ch-ask-top">
                <span className="ch-ask-mark">
                    <Icon name="sparkle" size={12} />
                </span>
                <span className="ch-ask-title">Ask Recall</span>
                <button
                    type="button"
                    className="ch-ask-close"
                    onClick={() => setOpen(false)}
                    aria-label="Close Ask Recall"
                >
                    <Icon name="close" size={13} />
                </button>
            </div>

            <div className="ch-ask-log" ref={logRef}>
                {turns.length === 0 && (
                    <div className="ch-ask-blank">
                        <p className="ch-ask-blank-text">
                            Ask anything about #{channelName || 'this channel'}. Answers cite what
                            they came from.
                        </p>
                        <div className="ch-ask-chips">
                            {STARTERS.map(([label, q]) => (
                                <button
                                    key={label}
                                    type="button"
                                    className="ch-chip"
                                    onClick={() => ask(q)}
                                >
                                    {label}
                                </button>
                            ))}
                        </div>
                    </div>
                )}

                {turns.map((turn, i) => (
                    <React.Fragment key={i}>
                        <p className="ch-ask-q">{turn.q}</p>

                        {turn.pending && (
                            <div className="ch-ask-working" role="status">
                                <span className="ch-pulse" />
                                Reading the record…
                            </div>
                        )}

                        {turn.error && (
                            <div className="ch-ask-fail" role="alert">
                                <Icon name="alert" size={14} />
                                {turn.error}
                            </div>
                        )}

                        {turn.answer && (
                            <div className="ch-answer">
                                <div className="ch-answer-label">
                                    <Icon name="archive" size={12} />
                                    From the record
                                </div>
                                <p className="ch-answer-text">{turn.answer}</p>
                                {turn.sourceIds?.length > 0 && (
                                    <div className="ch-sources">
                                        {turn.sourceIds.map((id, n) => (
                                            <span key={`${id}-${n}`} className="ch-source">
                                                Source {id}
                                            </span>
                                        ))}
                                    </div>
                                )}
                            </div>
                        )}
                    </React.Fragment>
                ))}
            </div>

            <form onSubmit={handleAsk} className="ch-ask-form">
                <input
                    type="text"
                    className="ch-ask-field"
                    placeholder={`What happened in #${channelName || 'this channel'}?`}
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                    disabled={isLoading}
                    aria-label={`Ask a question about #${channelName || 'this channel'}`}
                />
                <button type="submit" className="ch-ask-go" disabled={isLoading || !query.trim()}>
                    {isLoading ? 'Reading…' : 'Ask'}
                </button>
            </form>
        </div>
    );
};

export default AskAiWidget;
