import React from 'react';
import { Link } from 'react-router-dom';
import { authService } from '../services/authService';
import '../styles/MainPage.css';

const MainPage = () => {
    const isAuthenticated = authService.isAuthenticated();

    return (
        <div className="mainpage-container">
            {/* Top Status Bar / Ticker */}
            <div className="mainpage-hero-wrapper">
                <div className="mainpage-top-badge">
                    <span className="live-indicator-dot"></span>
                    <span className="badge-text">LIVE · 1,284 IN THE ROOM</span>
                    <span className="badge-divider">/</span>
                    <span className="badge-version">ECHO — VOL.07</span>
                </div>

                <div className="mainpage-hero-grid">
                    {/* Left Column: Headline & Intro */}
                    <div className="mainpage-hero-text">
                        <h1 className="hero-title">
                            Where the internet <span className="title-gradient">actually talks.</span>
                        </h1>
                        <p className="hero-subtitle">
                            No channels. No threads. No algorithm. Just people talking right now.
                        </p>
                        <div className="hero-cta-group">
                            {isAuthenticated ? (
                                <Link to="/chatarea" className="cta-pill-button">
                                    Go to Chat Area <span className="cta-arrow">↗</span>
                                </Link>
                            ) : (
                                <div className="hero-auth-row">
                                    <Link to="/login" className="cta-pill-button">
                                        Enter the Room <span className="cta-arrow">↗</span>
                                    </Link>
                                    <Link to="/signup" className="cta-secondary-button">
                                        Create Account
                                    </Link>
                                </div>
                            )}
                        </div>
                    </div>

                    {/* Right Column: Floating Live Chat Preview Card */}
                    <div className="mainpage-preview-card">
                        <div className="preview-card-header">
                            <div className="preview-title">
                                <span className="preview-globe-icon">🌐</span>
                                <span>GLOBAL ROOM</span>
                            </div>
                            <div className="preview-dots">
                                <span></span><span></span><span></span>
                            </div>
                        </div>

                        <div className="preview-chat-list">
                            <div className="preview-message-group">
                                <span className="preview-sender pink-sender">MIRA</span>
                                <div className="preview-bubble dark-bubble">
                                    wait you're online rn?
                                </div>
                            </div>

                            <div className="preview-message-group own-group">
                                <span className="preview-sender you-sender">YOU</span>
                                <div className="preview-bubble purple-bubble">
                                    always. this room never sleeps.
                                </div>
                            </div>

                            <div className="preview-message-group">
                                <span className="preview-sender blue-sender">KENJI</span>
                                <div className="preview-bubble dark-bubble">
                                    same. best 3am energy on the internet.
                                </div>
                            </div>
                        </div>

                        <div className="preview-footer-dots">
                            <span className="pulse-dot"></span>
                            <span className="pulse-dot delay-1"></span>
                            <span className="pulse-dot delay-2"></span>
                        </div>
                    </div>
                </div>
            </div>

            {/* Bento Grid Features Section */}
            <div className="mainpage-bento-section">
                <div className="bento-section-header">
                    <span className="section-label">/ WHAT'S INSIDE</span>
                    <h2 className="section-title">Small app. Big feeling.</h2>
                    <span className="section-counter">003 FEATURES · 001 ROOM</span>
                </div>

                <div className="bento-grid">
                    <div className="bento-card bento-card-large">
                        <div className="bento-card-top">
                            <div className="bento-icon-circle">🌐</div>
                            <span className="bento-number">01</span>
                        </div>
                        <h3>Echo Messaging</h3>
                        <p>
                            Chat instantly with everyone online. Messages land in the same room, in the order they were said — no algorithm rearranging your life.
                        </p>
                    </div>

                    <div className="bento-card">
                        <div className="bento-card-top">
                            <div className="bento-icon-circle">👥</div>
                            <span className="bento-number">02</span>
                        </div>
                        <h3>Multiple Users</h3>
                        <p>
                            See who's around. Join the conversation already in progress.
                        </p>
                    </div>

                    <div className="bento-card">
                        <div className="bento-card-top">
                            <div className="bento-icon-circle">🔒</div>
                            <span className="bento-number">03</span>
                        </div>
                        <h3>Private Chats</h3>
                        <p>
                            Slip into a DM. Just you two, nothing else.
                        </p>
                    </div>
                </div>
            </div>

            {/* Bottom Call to Action Section */}
            <div className="mainpage-footer-cta">
                <span className="cta-sub-label">/ THE DOOR IS OPEN</span>
                <h2 className="footer-cta-headline">
                    Come in. Someone's <span className="headline-gradient">already</span> talking about you.
                </h2>
                <div className="footer-cta-button-wrapper">
                    {isAuthenticated ? (
                        <Link to="/chatarea" className="cta-pill-button cta-glow-white">
                            Go to Chat Area <span className="cta-arrow">↗</span>
                        </Link>
                    ) : (
                        <Link to="/login" className="cta-pill-button cta-glow-white">
                            Go to Chat Area <span className="cta-arrow">↗</span>
                        </Link>
                    )}
                </div>
            </div>
        </div>
    );
};

export default MainPage;