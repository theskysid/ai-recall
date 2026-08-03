const AuthButton = ({ type = 'submit', disabled = false, isLoading = false, children, onClick }) => {
    return (
        <button
            type={type}
            disabled={disabled || isLoading}
            className="auth-btn"
            onClick={onClick}
        >
            {isLoading ? (
                <span className="auth-btn-loader">
                    <span className="dot" />
                    <span className="dot" />
                    <span className="dot" />
                </span>
            ) : children}
        </button>
    );
};

export default AuthButton;
