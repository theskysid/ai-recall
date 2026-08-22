import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { GoogleLogin } from "@react-oauth/google";
import { authService } from "../services/authService.js";
import "../styles/Auth.css";

const Signup = () => {
  const [username, setUsername] = useState("");
  const [identifier, setIdentifier] = useState("");
  const [password, setPassword] = useState("");
  const [otp, setOtp] = useState("");
  const [message, setMessage] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [otpSent, setOtpSent] = useState(false);
  const [countdown, setCountdown] = useState(0);
  const navigate = useNavigate();

  useEffect(() => {
    if (countdown <= 0) return;
    const timer = setTimeout(() => setCountdown((c) => c - 1), 1000);
    return () => clearTimeout(timer);
  }, [countdown]);

  const handleSendOtp = async (e) => {
    e.preventDefault();
    setMessage("");
    setIsLoading(true);
    try {
      if (!username.trim()) {
        throw new Error("Username is required");
      }
      if (!password.trim()) {
        throw new Error("Password is required");
      }
      await authService.sendSignupOtp(identifier);
      setOtpSent(true);
      setCountdown(60);
      setMessage("OTP sent! Check your email.");
    } catch (error) {
      setMessage(error.message);
    } finally {
      setIsLoading(false);
    }
  };

  const handleVerifySignup = async (e) => {
    e.preventDefault();
    setMessage("");
    setIsLoading(true);
    try {
      const result = await authService.verifySignupOtp({
        username,
        identifier,
        password,
        otp,
      });
      if (result.success) {
        setMessage("Account created! Redirecting...");
        setTimeout(() => navigate("/chatarea"), 1500);
      }
    } catch (error) {
      setMessage(error.message);
    } finally {
      setIsLoading(false);
    }
  };

  const handleGoogleSuccess = async (credentialResponse) => {
    setMessage("");
    setIsLoading(true);
    try {
      const result = await authService.googleLogin(
        credentialResponse.credential
      );
      if (result.success) {
        setMessage("Account created! Redirecting...");
        setTimeout(() => navigate("/chatarea"), 1500);
      }
    } catch (error) {
      setMessage(error.message);
    } finally {
      setIsLoading(false);
    }
  };

  const isSuccess =
    message.toLowerCase().includes("successfully") ||
    message.toLowerCase().includes("created") ||
    message.toLowerCase().includes("sent");

  return (
    <div className="signup-container nb">
      <div className="fl-bind" aria-hidden="true" />

      <div className="fl-sheet nb-sheet">
        <div className="signup-box">
          <div className="signup-header">
            <h1>Start a record.</h1>
            <p>Create an account to open your first channel.</p>
          </div>

          <div className="fl-col">
            <form
              onSubmit={otpSent ? handleVerifySignup : handleSendOtp}
              className="signup-form"
            >
              <div className="fl-field">
                <label className="fl-label" htmlFor="signup-username">
                  Username
                </label>
                <input
                  id="signup-username"
                  name="username"
                  type="text"
                  autoComplete="username"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  className="fl-input"
                  maxLength={20}
                  required
                  disabled={isLoading || otpSent}
                />
              </div>
              <div className="fl-field">
                <label className="fl-label" htmlFor="signup-email">
                  Email
                </label>
                <input
                  id="signup-email"
                  name="email"
                  type="email"
                  autoComplete="email"
                  value={identifier}
                  onChange={(e) => setIdentifier(e.target.value)}
                  className="fl-input"
                  required
                  disabled={isLoading || otpSent}
                />
              </div>
              <div className="fl-field">
                <label className="fl-label" htmlFor="signup-password">
                  Password
                </label>
                <input
                  id="signup-password"
                  name="password"
                  type="password"
                  autoComplete="new-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="fl-input"
                  maxLength={20}
                  required
                  disabled={isLoading || otpSent}
                />
              </div>
              {otpSent && (
                <div className="otp-section">
                  <div className="fl-field">
                    <label className="fl-label" htmlFor="signup-otp">
                      Enter the 6-digit code sent to your email
                    </label>
                    <input
                      id="signup-otp"
                      name="otp"
                      type="text"
                      inputMode="numeric"
                      autoComplete="one-time-code"
                      value={otp}
                      onChange={(e) =>
                        setOtp(e.target.value.replace(/\D/g, "").slice(0, 6))
                      }
                      className="fl-input otp-input"
                      maxLength={6}
                      required
                      disabled={isLoading}
                      autoFocus
                    />
                  </div>
                  {countdown > 0 ? (
                    <p className="otp-countdown">
                      Resend OTP in <span>{countdown}s</span>
                    </p>
                  ) : (
                    <button
                      type="button"
                      className="resend-btn"
                      onClick={handleSendOtp}
                      disabled={isLoading}
                    >
                      Resend OTP
                    </button>
                  )}
                </div>
              )}
              <button
                type="submit"
                disabled={
                  otpSent
                    ? otp.length !== 6 || isLoading
                    : !username.trim() ||
                      !identifier.trim() ||
                      !password.trim() ||
                      isLoading
                }
                aria-busy={isLoading}
                className="auth-submit-btn"
              >
                {isLoading
                  ? "Please wait..."
                  : otpSent
                  ? "Verify & Create Account"
                  : "Send OTP"}
              </button>
            </form>

            {message && (
              <div
                className={`fl-note ${isSuccess ? "is-ok" : "is-error"}`}
                role={isSuccess ? "status" : "alert"}
              >
                <p className="fl-note-text">{message}</p>
                {!isSuccess && (
                  <p className="fl-note-fix">
                    The account was not created. Check the entries above and try
                    again.
                  </p>
                )}
              </div>
            )}

            <div className="auth-divider">
              <span>or</span>
            </div>

            <div className="google-btn-wrapper">
              <GoogleLogin
                onSuccess={handleGoogleSuccess}
                onError={() => setMessage("Google sign-in failed")}
                theme="outline"
                size="large"
                width="100%"
                text="signup_with"
                shape="rectangular"
              />
            </div>

            <div className="toggle-auth">
              <p>
                Already have an account?
                <a href="/login" className="toggle-auth-btn">
                  Login
                </a>
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Signup;
