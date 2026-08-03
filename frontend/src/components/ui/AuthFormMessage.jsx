const AuthFormMessage = ({ message, isSuccess = false }) => {
    if (!message) return null;

    return (
        <div className={`auth-form-message ${isSuccess ? 'success' : 'error'}`}>
            <span className="auth-form-message-icon">
                {isSuccess ? '✓' : '!'}
            </span>
            <p>{message}</p>
        </div>
    );
};

export default AuthFormMessage;
