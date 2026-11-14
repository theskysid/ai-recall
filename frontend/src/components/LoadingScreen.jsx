import React from 'react';
import '../styles/LoadingScreen.css';

const LoadingScreen = ({ message = 'Restoring your session…', error = false, onRetry, onBack }) => {
    return (
        <div className="loading-screen" role={error ? 'alert' : 'status'} aria-live="polite" aria-busy={!error}>
            <span className="loading-screen__brand">Recall</span>
            {!error && <div className="loader" aria-hidden="true"></div>}
            <span className="loading-screen__subtitle">{message}</span>
            {error && (
                <div className="loading-screen__actions">
                    {onRetry && <button type="button" onClick={onRetry}>Try again</button>}
                    {onBack && <button type="button" onClick={onBack}>Back to sign in</button>}
                </div>
            )}
        </div>
    );
};

export default LoadingScreen;
