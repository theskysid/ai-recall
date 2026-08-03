/* Hairline icon set — one stroke weight, one grid, currentColor.
   Replaces emoji in the app chrome so the interface reads as drawn
   rather than typed. Emoji still belong in message content and the
   emoji picker. */

const paths = {
    search: <><circle cx="11" cy="11" r="7" /><line x1="20" y1="20" x2="16.2" y2="16.2" /></>,
    plus: <><line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" /></>,
    close: <><line x1="6" y1="6" x2="18" y2="18" /><line x1="18" y1="6" x2="6" y2="18" /></>,
    check: <polyline points="4 12.5 9.5 18 20 6.5" />,
    refresh: <><path d="M20 11a8 8 0 0 0-13.7-5.3L3 9" /><path d="M4 13a8 8 0 0 0 13.7 5.3L21 15" /><polyline points="3 4 3 9 8 9" /><polyline points="21 20 21 15 16 15" /></>,
    people: <><circle cx="9" cy="8" r="3.2" /><path d="M3 19.5c0-3.2 2.7-5 6-5s6 1.8 6 5" /><path d="M16 5.5a3.2 3.2 0 0 1 0 6" /><path d="M17.5 14.8c2.1.5 3.5 2 3.5 4.7" /></>,
    globe: <><circle cx="12" cy="12" r="8.5" /><path d="M3.5 12h17" /><path d="M12 3.5c2.2 2.4 3.3 5.3 3.3 8.5S14.2 18.1 12 20.5c-2.2-2.4-3.3-5.3-3.3-8.5S9.8 5.9 12 3.5z" /></>,
    user: <><circle cx="12" cy="8" r="3.5" /><path d="M5 20c0-3.6 3.1-5.5 7-5.5s7 1.9 7 5.5" /></>,
    message: <path d="M20 5.5v9a2 2 0 0 1-2 2H9l-4.5 3.5v-3.5H6a2 2 0 0 1-2-2v-9a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2z" />,
    video: <><rect x="3" y="6" width="12" height="12" rx="2.5" /><path d="M15 10.5l5.5-3v9l-5.5-3z" /></>,
    phone: <path d="M20.5 16.4v2.3a1.7 1.7 0 0 1-1.9 1.7 17 17 0 0 1-7.4-2.7 16.7 16.7 0 0 1-5.1-5.1A17 17 0 0 1 3.4 5.2 1.7 1.7 0 0 1 5.1 3.3h2.3a1.7 1.7 0 0 1 1.7 1.5c.1.9.3 1.7.6 2.5a1.7 1.7 0 0 1-.4 1.8l-1 1a13.6 13.6 0 0 0 5.1 5.1l1-1a1.7 1.7 0 0 1 1.8-.4c.8.3 1.6.5 2.5.6a1.7 1.7 0 0 1 1.5 1.7z" />,
    sparkle: <><path d="M12 3.5l1.9 5.1 5.1 1.9-5.1 1.9-1.9 5.1-1.9-5.1L5 10.5l5.1-1.9z" /><path d="M18.5 16.5l.7 1.8 1.8.7-1.8.7-.7 1.8-.7-1.8-1.8-.7 1.8-.7z" /></>,
    archive: <><rect x="3.5" y="4.5" width="17" height="4" rx="1.2" /><path d="M5 8.5v10a1.5 1.5 0 0 0 1.5 1.5h11a1.5 1.5 0 0 0 1.5-1.5v-10" /><line x1="10" y1="13" x2="14" y2="13" /></>,
    pin: <><path d="M9 3.5h6l-.8 5.4 3.3 3.3H6.5l3.3-3.3z" /><line x1="12" y1="12.2" x2="12" y2="20.5" /></>,
    note: <><path d="M6 3.5h8.5L19 8v12.5H6z" /><polyline points="14 3.5 14 8.2 18.8 8.2" /><line x1="9" y1="13" x2="16" y2="13" /><line x1="9" y1="16.5" x2="14" y2="16.5" /></>,
    link: <><path d="M10.5 13.5a3.8 3.8 0 0 0 5.4 0l2.8-2.8a3.8 3.8 0 0 0-5.4-5.4l-1.4 1.4" /><path d="M13.5 10.5a3.8 3.8 0 0 0-5.4 0l-2.8 2.8a3.8 3.8 0 0 0 5.4 5.4l1.4-1.4" /></>,
    lock: <><rect x="4.5" y="10.5" width="15" height="10" rx="2" /><path d="M8 10.5V7.8a4 4 0 0 1 8 0v2.7" /></>,
    key: <><circle cx="8" cy="12" r="3.8" /><path d="M11.8 12H21" /><path d="M17.5 12v3.2" /><path d="M20 12v2.2" /></>,
    mail: <><rect x="3.5" y="5.5" width="17" height="13" rx="2" /><polyline points="4 7 12 13 20 7" /></>,
    inbox: <><path d="M3.5 13.5h4l1.5 3h6l1.5-3h4" /><path d="M6 4.5h12l2.5 9v5a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2v-5z" /></>,
    ban: <><circle cx="12" cy="12" r="8.5" /><line x1="6" y1="18" x2="18" y2="6" /></>,
    trash: <><polyline points="4.5 6.5 19.5 6.5" /><path d="M9.5 6.5V4.8h5v1.7" /><path d="M6.5 6.5l1 13h9l1-13" /></>,
    pencil: <><path d="M4.5 19.5l.9-4 10-10a2 2 0 0 1 2.8 0l.3.3a2 2 0 0 1 0 2.8l-10 10z" /><line x1="13.5" y1="6.5" x2="17.5" y2="10.5" /></>,
    clock: <><circle cx="12" cy="12" r="8.5" /><polyline points="12 7 12 12 15.5 14" /></>,
    calendar: <><rect x="3.5" y="5.5" width="17" height="15" rx="2" /><line x1="3.5" y1="10" x2="20.5" y2="10" /><line x1="8" y1="3" x2="8" y2="7" /><line x1="16" y1="3" x2="16" y2="7" /></>,
    alert: <><path d="M12 4l9 16H3z" /><line x1="12" y1="10" x2="12" y2="14.5" /><line x1="12" y1="17" x2="12" y2="17.2" /></>,
    logout: <><path d="M14.5 4.5h3.5a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2h-3.5" /><polyline points="9 8 13 12 9 16" /><line x1="13" y1="12" x2="3.5" y2="12" /></>,
    more: <><circle cx="12" cy="5.5" r="1.3" /><circle cx="12" cy="12" r="1.3" /><circle cx="12" cy="18.5" r="1.3" /></>,
    chevronRight: <polyline points="9.5 5 16.5 12 9.5 19" />,
    chevronDown: <polyline points="5 9.5 12 16.5 19 9.5" />,
    arrowLeft: <><line x1="20" y1="12" x2="4.5" y2="12" /><polyline points="11 5 4 12 11 19" /></>,
    arrowUpRight: <><line x1="6" y1="18" x2="18" y2="6" /><polyline points="8.5 6 18 6 18 15.5" /></>,
    send: <><line x1="21" y1="3" x2="10.5" y2="13.5" /><polygon points="21 3 14.5 21 10.5 13.5 3 9.5" /></>,
    hash: <><line x1="9.5" y1="3.5" x2="7.5" y2="20.5" /><line x1="16.5" y1="3.5" x2="14.5" y2="20.5" /><line x1="4" y1="9" x2="19.5" y2="9" /><line x1="3.5" y1="15" x2="19" y2="15" /></>
};

const Icon = ({ name, size = 16, strokeWidth = 1.6, className = '', ...rest }) => {
    const glyph = paths[name];
    if (!glyph) return null;

    return (
        <svg
            className={`icon ${className}`.trim()}
            width={size}
            height={size}
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth={strokeWidth}
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
            focusable="false"
            {...rest}
        >
            {glyph}
        </svg>
    );
};

export default Icon;
