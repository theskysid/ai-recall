import React, { useRef } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { motion, MotionConfig, useReducedMotion } from 'motion/react';
import { authService } from '../services/authService';
import Icon from '../components/ui/Icon';
import NotebookNib from '../components/NotebookNib';
import { LiquidButton } from '../components/ui/liquid-glass-button';
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

/* Ink settles onto the sheet — it does not slide in from off-page. */
const EASE = [0.22, 0.9, 0.26, 1];

const reveal = {
    hidden: { opacity: 0, y: 16 },
    shown: { opacity: 1, y: 0, transition: { duration: 0.55, ease: EASE } }
};

/* A page is written top to bottom, one line after the next. */
const page = {
    hidden: {},
    shown: { transition: { staggerChildren: 0.085, delayChildren: 0.04 } }
};

const rows = {
    hidden: {},
    shown: { transition: { staggerChildren: 0.07 } }
};

/* Every section reveals once, when its top third has cleared the fold. */
const inView = { once: true, amount: 0.2 };

const MotionLink = motion.create(Link);

/* Pressing a stamp into the sheet, not lifting a card off it. */
const stampHover = { y: 1 };
const stampTap = { y: 2, scale: 0.99 };

const MainPage = () => {
    const isAuthenticated = authService.isAuthenticated();
    const reduced = useReducedMotion();
    const rootRef = useRef(null);
    const navigate = useNavigate();

    /* The primary action is now a liquid-glass button; nesting a <button>
       inside react-router's <Link> is invalid, so it navigates on click. */
    const primaryCta = isAuthenticated ? (
        <LiquidButton size="lg" onClick={() => navigate('/chatarea')}>
            Open your channels
            <Icon name="arrowUpRight" size={15} />
        </LiquidButton>
    ) : (
        <>
            <LiquidButton size="lg" onClick={() => navigate('/login')}>
                Sign in
                <Icon name="arrowUpRight" size={15} />
            </LiquidButton>
            <MotionLink
                to="/signup"
                className="cta cta-quiet"
                whileHover={stampHover}
                whileTap={stampTap}
            >
                Create an account
            </MotionLink>
        </>
    );

    return (
        <MotionConfig reducedMotion="user" transition={{ duration: 0.35, ease: EASE }}>
            <div className="mainpage nb" ref={rootRef}>
                {/* The oxblood cloth binding, sewn down its length. */}
                <div className="binding" aria-hidden="true">
                    <span className="binding-stitch" />
                </div>

                {/* The sheet's own weather: a cross-hatch front crossing
                    the page corner to corner, on a loop. */}
                {!reduced && (
                    <div className="grid-wave" aria-hidden="true">
                        <motion.div
                            className="grid-wave-band"
                            animate={{ x: ['-45%', '45%'], y: ['-45%', '45%'] }}
                            transition={{ duration: 19, repeat: Infinity, ease: 'linear' }}
                        />
                    </div>
                )}

                {/* The pointer, reading the ruled sheet. */}
                <NotebookNib containerRef={rootRef} />


                <div className="sheets">
                    {/* ── 001 · the lede ───────────────────────────── */}
                    <motion.section
                        className="page nb-sheet"
                        variants={page}
                        initial="hidden"
                        whileInView="shown"
                        viewport={inView}
                    >
                        <div className="measure measure-lede">
                            <motion.span className="page-no" variants={reveal}>001</motion.span>

                            <motion.h1 className="lede-title" variants={reveal}>
                                Every conversation
                                <br />
                                remembers itself.
                            </motion.h1>

                            <motion.p className="lede-deck" variants={reveal}>
                                Recall is team chat that keeps the record. Talk, call, decide — then
                                ask the channel what happened while you were away.
                            </motion.p>

                            {/* The thesis, drawn the way a lab notebook draws it: the
                                old reading is ruled through, initialled, and left legible. */}
                            <motion.figure
                                className="register"
                                aria-label="Illustrative example of a superseded decision"
                                variants={rows}
                            >
                                <motion.figcaption className="rubric register-rubric" variants={reveal}>
                                    Illustrative example — not a real channel&rsquo;s record
                                </motion.figcaption>

                                <motion.div className="entry entry-struck" variants={reveal}>
                                    <span className="entry-date">12 MAR</span>
                                    <p className="entry-text">
                                        {/* THE authored motion: the rule is drawn left to right at
                                            the speed of a hand, once, when the entry is reached. */}
                                        <motion.span
                                            className="entry-strike"
                                            initial={{ backgroundSize: '0% 1.5px' }}
                                            whileInView={{ backgroundSize: '100% 1.5px' }}
                                            viewport={{ once: true, amount: 0.45 }}
                                            transition={{
                                                duration: reduced ? 0 : 0.5,
                                                delay: reduced ? 0 : 0.35,
                                                ease: EASE
                                            }}
                                        >
                                            Ship all three pricing tiers at launch.
                                        </motion.span>
                                    </p>
                                    <span className="entry-margin">A.K. · 19 MAR · superseded</span>
                                </motion.div>

                                <motion.div className="entry entry-current" variants={reveal}>
                                    <span className="entry-date">19 MAR</span>
                                    <p className="entry-text">
                                        Ship two tiers. The third waits until the pricing page moves
                                        behind the marketing site.
                                    </p>
                                    <span className="entry-margin entry-countersign">
                                        <Icon name="check" size={12} />
                                        countersigned
                                    </span>
                                </motion.div>
                            </motion.figure>

                            <motion.div className="signing" variants={reveal}>{primaryCta}</motion.div>

                            <motion.dl className="specimen" variants={rows}>
                                <motion.div className="specimen-row" variants={reveal}>
                                    <dt>Embeddings</dt>
                                    <dd>On your own server</dd>
                                </motion.div>
                                <motion.div className="specimen-row" variants={reveal}>
                                    <dt>Transcripts</dt>
                                    <dd>Written during the call</dd>
                                </motion.div>
                                <motion.div className="specimen-row" variants={reveal}>
                                    <dt>Direct messages</dt>
                                    <dd>Deleted on a timer</dd>
                                </motion.div>
                            </motion.dl>
                        </div>
                    </motion.section>

                    {/* ── 002 · what the notebook takes ────────────── */}
                    <motion.section
                        className="page nb-sheet"
                        variants={page}
                        initial="hidden"
                        whileInView="shown"
                        viewport={inView}
                    >
                        <div className="measure measure-ledger">
                            <motion.span className="page-no" variants={reveal}>002</motion.span>

                            <motion.h2 className="page-title" variants={reveal}>
                                Five kinds of record.
                            </motion.h2>

                            <motion.ul className="ledger" variants={rows}>
                                {RECORDS.map((record) => (
                                    <motion.li
                                        key={record.code}
                                        className="ledger-row"
                                        variants={reveal}
                                    >
                                        <span className="ledger-code">{record.code}</span>
                                        <div className="ledger-body">
                                            <h3>{record.title}</h3>
                                            <p>{record.body}</p>
                                        </div>
                                    </motion.li>
                                ))}
                            </motion.ul>
                        </div>
                    </motion.section>

                    {/* ── 003 · asking the record ──────────────────── */}
                    <motion.section
                        className="page nb-sheet"
                        variants={page}
                        initial="hidden"
                        whileInView="shown"
                        viewport={inView}
                    >
                        <div className="measure measure-ask">
                            <motion.span className="page-no" variants={reveal}>003</motion.span>

                            <motion.figure
                                className="ask"
                                aria-label="Example of asking a channel about its own history"
                                variants={rows}
                            >
                                <motion.figcaption className="rubric ask-rubric" variants={reveal}>
                                    <span className="ask-channel">
                                        <Icon name="hash" size={12} />
                                        product-eng
                                    </span>
                                    Illustrative example — not a real channel&rsquo;s record
                                </motion.figcaption>

                                <motion.p className="ask-question" variants={reveal}>
                                    What did we decide about the pricing page?
                                </motion.p>

                                <motion.span className="rubric ask-answer-rubric" variants={reveal}>
                                    <Icon name="archive" size={12} />
                                    From the record
                                </motion.span>

                                <motion.p className="ask-answer" variants={reveal}>
                                    The team moved the pricing page behind the marketing site and dropped
                                    the third tier. That replaced the earlier plan to ship all three tiers
                                    at launch.
                                </motion.p>

                                <motion.ul className="citations" variants={rows}>
                                    {CITATIONS.map((source) => (
                                        <motion.li key={source} variants={reveal}>{source}</motion.li>
                                    ))}
                                </motion.ul>
                            </motion.figure>
                        </div>
                    </motion.section>

                    {/* ── the countersignature ─────────────────────── */}
                    <motion.section
                        className="page page-close nb-sheet"
                        variants={page}
                        initial="hidden"
                        whileInView="shown"
                        viewport={inView}
                    >
                        <div className="measure">
                            <motion.h2 className="close-title" variants={reveal}>
                                Nothing worth saying
                                <br />
                                should have to be said twice.
                            </motion.h2>

                            <motion.span className="rubric" variants={reveal}>
                                Read and understood by
                            </motion.span>

                            <motion.div className="signing" variants={reveal}>
                                <LiquidButton
                                    size="lg"
                                    onClick={() => navigate(isAuthenticated ? '/chatarea' : '/login')}
                                >
                                    {isAuthenticated ? 'Open your channels' : 'Sign in to Recall'}
                                    <Icon name="arrowUpRight" size={15} />
                                </LiquidButton>
                            </motion.div>
                        </div>
                    </motion.section>
                </div>
            </div>
        </MotionConfig>
    );
};

export default MainPage;
