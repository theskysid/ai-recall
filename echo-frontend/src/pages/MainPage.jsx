import React from 'react';
import { Link } from 'react-router-dom';
import { authService } from '../services/authService';
import Icon from '../components/ui/Icon';
import '../styles/MainPage.css';

/* Each row is a kind of record the app keeps. The code on the rail is
   the record type, not a step number — nothing here is a sequence. */
const RECORDS = [
    {
        code: 'MSG',
        title: 'Channels',
        body: 'Named rooms with an invite code. Every message is kept and indexed, so the channel still knows what was said last month.'
    },
    {
        code: 'CALL',
        title: 'Calls that write themselves down',
        body: 'Start a video call from any channel. Speech is transcribed live and the transcript is filed with the channel when the call ends.'
    },
    {
        code: 'DEC',
        title: 'Decisions, pulled out and dated',
        body: 'When a channel settles something, it is recorded as a decision. Change your mind later and the old one is marked superseded, not erased.'
    },
    {
        code: 'ASK',
        title: 'Ask the channel',
        body: 'Question the memory in plain language. Answers come back with the messages and transcripts they were drawn from.'
    },
    {
        code: 'DM',
        title: 'Private messages that expire',
        body: 'One-to-one chat that clears itself after six hours, a day, or a week. You choose the window; nothing is indexed.'
    }
];

const MainPage = () => {
    const isAuthenticated = authService.isAuthenticated();

    const primaryCta = isAuthenticated ? (
        <Link to="/chatarea" className="cta cta-solid">
            Open your channels
            <Icon name="arrowUpRight" size={15} />
        </Link>
    ) : (
        <>
            <Link to="/login" className="cta cta-solid">
                Sign in
                <Icon name="arrowUpRight" size={15} />
            </Link>
            <Link to="/signup" className="cta cta-quiet">
                Create an account
            </Link>
        </>
    );

    return (
        <div className="mainpage">
            {/* ── Lede ─────────────────────────────────────── */}
            <header className="lede">
                <div className="lede-slug">
                    <span className="lede-slug-dot" />
                    <span>Recall</span>
                    <span className="lede-slug-rule" />
                    <span>Chat with a memory</span>
                </div>

                <div className="lede-grid">
                    <div className="lede-copy">
                        <h1 className="lede-title">
                            Every conversation
                            <em> remembers itself.</em>
                        </h1>
                        <p className="lede-sub">
                            Recall is team chat that keeps the record. Talk, call, decide — then
                            ask the channel what happened while you were away.
                        </p>

                        <div className="lede-actions">{primaryCta}</div>

                        <dl className="lede-facts">
                            <div className="fact">
                                <dt>Embeddings</dt>
                                <dd>On your own server</dd>
                            </div>
                            <div className="fact">
                                <dt>Transcripts</dt>
                                <dd>Written during the call</dd>
                            </div>
                            <div className="fact">
                                <dt>Direct messages</dt>
                                <dd>Deleted on a timer</dd>
                            </div>
                        </dl>
                    </div>

                    {/* Signature: the product's actual thesis, shown once. */}
                    <aside className="recall-card" aria-label="Example of asking a channel about its own history">
                        <div className="recall-card-head">
                            <span className="recall-card-channel">
                                <Icon name="hash" size={12} />
                                product-eng
                            </span>
                            <span className="recall-card-tag">Memory</span>
                        </div>

                        <div className="recall-ask">
                            <span className="recall-ask-label">You asked</span>
                            <p>What did we decide about the pricing page?</p>
                        </div>

                        <div className="recall-answer">
                            <span className="recall-answer-label">
                                <Icon name="archive" size={12} />
                                From the record
                            </span>
                            <p>
                                The team moved the pricing page behind the marketing site and dropped
                                the third tier. That replaced the earlier plan to ship all three tiers
                                at launch.
                            </p>
                            <div className="recall-sources">
                                <span className="recall-source">Call · 12 Mar</span>
                                <span className="recall-source">Decision #14</span>
                                <span className="recall-source">18 messages</span>
                            </div>
                        </div>
                    </aside>
                </div>
            </header>

            {/* ── The record ───────────────────────────────── */}
            <section className="ledger">
                <div className="ledger-head">
                    <span className="ledger-eyebrow">What it keeps</span>
                    <h2 className="ledger-title">Five kinds of record.</h2>
                </div>

                <ul className="ledger-list">
                    {RECORDS.map((record) => (
                        <li key={record.code} className="ledger-row">
                            <span className="ledger-code">{record.code}</span>
                            <div className="ledger-body">
                                <h3>{record.title}</h3>
                                <p>{record.body}</p>
                            </div>
                        </li>
                    ))}
                </ul>
            </section>

            {/* ── Close ────────────────────────────────────── */}
            <section className="closing">
                <span className="closing-eyebrow">Start a channel</span>
                <h2 className="closing-title">
                    Nothing worth saying
                    <em> should have to be said twice.</em>
                </h2>
                <div className="closing-actions">
                    <Link to={isAuthenticated ? '/chatarea' : '/login'} className="cta cta-solid">
                        {isAuthenticated ? 'Open your channels' : 'Sign in to Recall'}
                        <Icon name="arrowUpRight" size={15} />
                    </Link>
                </div>
            </section>
        </div>
    );
};

export default MainPage;
