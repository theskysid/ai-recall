import React, { useState } from 'react';
import { channelService } from '../../services/channelService';
import '../../styles/Channels.css';

const AskAiWidget = ({ channelId, channelName }) => {
    const [collapsed, setCollapsed] = useState(true);
    const [query, setQuery] = useState('');
    const [answer, setAnswer] = useState('');
    const [sourceIds, setSourceIds] = useState([]);
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState('');
    const [asked, setAsked] = useState(false);

    const handleAsk = async (e) => {
        e.preventDefault();
        const q = query.trim();
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

    return (
        <div className={`ask-ai-widget ${collapsed ? 'collapsed' : ''}`}>
            <button
                type="button"
                className="ask-ai-header"
                onClick={() => setCollapsed((v) => !v)}
                aria-expanded={!collapsed}
            >
                <span className="ask-ai-title">✨ Ask AI</span>
                <span className="ask-ai-chevron">{collapsed ? '▸' : '▾'}</span>
            </button>

            {!collapsed && (
                <div className="ask-ai-body">
                    <form onSubmit={handleAsk} className="ask-ai-form">
                        <input
                            type="text"
                            className="ask-ai-input"
                            placeholder={`Ask about #${channelName || 'this channel'}…`}
                            value={query}
                            onChange={(e) => setQuery(e.target.value)}
                            disabled={isLoading}
                        />
                        <button type="submit" className="ask-ai-submit" disabled={isLoading || !query.trim()}>
                            {isLoading ? '…' : 'Ask'}
                        </button>
                    </form>

                    {isLoading && (
                        <div className="ask-ai-status">
                            <span className="ask-ai-spinner" /> AI is thinking…
                        </div>
                    )}

                    {!isLoading && error && (
                        <div className="ask-ai-error">⚠️ {error}</div>
                    )}

                    {!isLoading && !error && asked && answer && (
                        <div className="ask-ai-answer">
                            <p className="ask-ai-answer-text">{answer}</p>
                            {sourceIds.length > 0 && (
                                <div className="ask-ai-sources">
                                    {sourceIds.map((id, i) => (
                                        <span key={`${id}-${i}`} className="ask-ai-source-badge">
                                            [Source {id}]
                                        </span>
                                    ))}
                                </div>
                            )}
                        </div>
                    )}
                </div>
            )}
        </div>
    );
};

export default AskAiWidget;
