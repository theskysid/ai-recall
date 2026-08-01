import React, { useState } from 'react';
import { channelService } from '../../services/channelService';
import Icon from '../ui/Icon';
import '../../styles/ChannelPage.css';

/* Starter questions. These only prefill the field — the request path is
   unchanged. They exist because an empty box does not tell you that the
   channel can be questioned at all. */
const STARTERS = [
    'What was decided here?',
    'Catch me up on this week',
    'What came out of the last call?'
];

const AskAiWidget = ({ channelId, channelName }) => {
    const [query, setQuery] = useState('');
    const [answer, setAnswer] = useState('');
    const [sourceIds, setSourceIds] = useState([]);
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState('');
    const [asked, setAsked] = useState(false);

    const ask = async (q) => {
        if (!q || channelId == null || isLoading) return;

        setIsLoading(true);
        setError('');
        setAnswer('');
        setSourceIds([]);
        setAsked(true);
        try {
            const data = await channelService.ask(channelId, q);
            setAnswer(data.answer || 'No answer returned.');
            setSourceIds(Array.isArray(data.sourceIds) ? data.sourceIds : []);
        } catch (err) {
            console.error('Ask AI failed:', err);
            setError('AI is currently unavailable. Please try again.');
        } finally {
            setIsLoading(false);
        }
    };

    const handleAsk = (e) => {
        e.preventDefault();
        ask(query.trim());
    };

    const showStarters = !isLoading && !error && !asked;

    return (
        <div className="ch-ask">
            <div className="ch-ask-top">
                <span className="ch-ask-mark">
                    <Icon name="sparkle" size={12} />
                </span>
                <span className="ch-ask-title">Ask this channel</span>
                <span className="ch-ask-note">Answers cite what they came from</span>
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

            {showStarters && (
                <div className="ch-ask-chips">
                    {STARTERS.map((s) => (
                        <button
                            key={s}
                            type="button"
                            className="ch-chip"
                            onClick={() => {
                                setQuery(s);
                                ask(s);
                            }}
                        >
                            {s}
                        </button>
                    ))}
                </div>
            )}

            {isLoading && (
                <div className="ch-ask-working" role="status">
                    <span className="ch-pulse" />
                    Reading the record…
                </div>
            )}

            {!isLoading && error && (
                <div className="ch-ask-fail" role="alert">
                    <Icon name="alert" size={14} />
                    {error}
                </div>
            )}

            {!isLoading && !error && asked && answer && (
                <div className="ch-answer">
                    <div className="ch-answer-label">
                        <Icon name="archive" size={12} />
                        From the record
                    </div>
                    <p className="ch-answer-text">{answer}</p>
                    {sourceIds.length > 0 && (
                        <div className="ch-sources">
                            {sourceIds.map((id, i) => (
                                <span key={`${id}-${i}`} className="ch-source">
                                    Source {id}
                                </span>
                            ))}
                        </div>
                    )}
                </div>
            )}
        </div>
    );
};

export default AskAiWidget;
