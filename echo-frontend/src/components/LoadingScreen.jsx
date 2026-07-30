import React from 'react';
import '../styles/LoadingScreen.css';

const LoadingScreen = () => {
    return (
        <div className="loading-screen">
            <span className="loading-screen__brand">Echo</span>
            <div className="loader"></div>
            <span className="loading-screen__subtitle">Restoring your session…</span>
        </div>
    );
};

export default LoadingScreen;
