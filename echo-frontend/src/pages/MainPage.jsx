import React, { useEffect, useRef } from 'react';
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

const CITATIONS = ['Call · 12 Mar', 'Decision #14', '18 messages'];

const MainPage = () => {
    const isAuthenticated = authService.isAuthenticated();

    /* The page's one authored moment: the ruled line is drawn through the
       superseded entry at the speed of a hand, once, when it is reached. */
    const struckRef = useRef(null);

    useEffect(() => {
        const el = struckRef.current;
        if (!el) return undefined;

        if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
            el.classList.add('is-drawn', 'is-instant');
            return undefined;
        }

        const observer = new IntersectionObserver(
            (entries) => {
                if (entries[0].isIntersecting) {
                    el.classList.add('is-drawn');
                    observer.disconnect();
                }
            },
            { threshold: 0.45 }
        );

        observer.observe(el);
        return () => observer.disconnect();
    }, []);

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
        <div className="mainpage nb">
            {/* The oxblood cloth binding, sewn down its length. */}
            <div className="binding" aria-hidden="true">
                <span className="binding-stitch" />
            </div>

            <div className="sheets">
                {/* ── 001 · the lede ───────────────────────────── */}
                <section className="page nb-sheet">
                    <div className="measure measure-lede">
                        <span className="page-no">001</span>

                        <h1 className="lede-title">
                            Every conversation
                            <br />
                            remembers itself.
                        </h1>

                        <p className="lede-deck">
                            Recall is team chat that keeps the record. Talk, call, decide — then
                            ask the channel what happened while you were away.
                        </p>

                        {/* The thesis, drawn the way a lab notebook draws it: the
                            old reading is ruled through, initialled, and left legible. */}
                        <figure className="register" aria-label="Illustrative example of a superseded decision">
                            <figcaption className="rubric register-rubric">
                                Illustrative example — not a real channel&rsquo;s record
                            </figcaption>

                            <div className="entry entry-struck" ref={struckRef}>
                                <span className="entry-date">12 MAR</span>
                                <p className="entry-text">
                                    <span className="entry-strike">
                                        Ship all three pricing tiers at launch.
                                    </span>
                                </p>
                                <span className="entry-margin">A.K. · 19 MAR · superseded</span>
                            </div>

                            <div className="entry entry-current">
                                <span className="entry-date">19 MAR</span>
                                <p className="entry-text">
                                    Ship two tiers. The third waits until the pricing page moves
                                    behind the marketing site.
                                </p>
                                <span className="entry-margin entry-countersign">
                                    <Icon name="check" size={12} />
                                    countersigned
                                </span>
                            </div>
                        </figure>

                        <div className="signing">{primaryCta}</div>

                        <dl className="specimen">
                            <div className="specimen-row">
                                <dt>Embeddings</dt>
                                <dd>On your own server</dd>
                            </div>
                            <div className="specimen-row">
                                <dt>Transcripts</dt>
                                <dd>Written during the call</dd>
                            </div>
                            <div className="specimen-row">
                                <dt>Direct messages</dt>
                                <dd>Deleted on a timer</dd>
                            </div>
                        </dl>
                    </div>
                </section>

                {/* ── 002 · what the notebook takes ────────────── */}
                <section className="page nb-sheet">
                    <div className="measure measure-ledger">
                        <span className="page-no">002</span>

                        <h2 className="page-title">Five kinds of record.</h2>

                        <ul className="ledger">
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
                    </div>
                </section>

                {/* ── 003 · asking the record ──────────────────── */}
                <section className="page nb-sheet">
                    <div className="measure measure-ask">
                        <span className="page-no">003</span>

                        <figure className="ask" aria-label="Example of asking a channel about its own history">
                            <figcaption className="rubric ask-rubric">
                                <span className="ask-channel">
                                    <Icon name="hash" size={12} />
                                    product-eng
                                </span>
                                Illustrative example — not a real channel&rsquo;s record
                            </figcaption>

                            <p className="ask-question">What did we decide about the pricing page?</p>

                            <span className="rubric ask-answer-rubric">
                                <Icon name="archive" size={12} />
                                From the record
                            </span>
                            <p className="ask-answer">
                                The team moved the pricing page behind the marketing site and dropped
                                the third tier. That replaced the earlier plan to ship all three tiers
                                at launch.
                            </p>

                            <ul className="citations">
                                {CITATIONS.map((source) => (
                                    <li key={source}>{source}</li>
                                ))}
                            </ul>
                        </figure>
                    </div>
                </section>

                {/* ── the countersignature ─────────────────────── */}
                <section className="page page-close nb-sheet">
                    <div className="measure">
                        <h2 className="close-title">
                            Nothing worth saying
                            <br />
                            should have to be said twice.
                        </h2>

                        <span className="rubric">Read and understood by</span>
                        <div className="signing">
                            <Link to={isAuthenticated ? '/chatarea' : '/login'} className="cta cta-solid">
                                {isAuthenticated ? 'Open your channels' : 'Sign in to Recall'}
                                <Icon name="arrowUpRight" size={15} />
                            </Link>
                        </div>
                    </div>
                </section>
            </div>
        </div>
    );
};

export default MainPage;
