const AuthInput = ({ type = 'text', placeholder, value, onChange, maxLength = 20, required = true, disabled = false, icon }) => {
    return (
        <div className="auth-input-wrapper">
            {icon && <span className="auth-input-icon">{icon}</span>}
            <input
                type={type}
                placeholder={placeholder}
                value={value}
                onChange={onChange}
                className={`auth-input ${icon ? 'has-icon' : ''}`}
                maxLength={maxLength}
                required={required}
                disabled={disabled}
            />
            <div className="auth-input-glow" />
        </div>
    );
};

export default AuthInput;
