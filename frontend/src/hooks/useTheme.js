import { useCallback, useEffect, useState } from 'react';

const KEY = 'recall-theme';

/* The attribute is already set by the bootstrap in index.html, which runs
   before first paint. This hook only reads it back and changes it, so the
   two never disagree about what is on screen. */
const current = () => document.documentElement.dataset.theme || 'light';

const useTheme = () => {
    const [theme, setTheme] = useState(current);

    /* Follow the system while the user has not made a choice of their own —
       someone whose laptop goes dark at sunset expects this to come with it. */
    useEffect(() => {
        const mq = window.matchMedia('(prefers-color-scheme: dark)');
        const onChange = (e) => {
            try {
                if (localStorage.getItem(KEY)) return;
            } catch {
                /* no storage: system preference is the only signal there is */
            }
            const next = e.matches ? 'dark' : 'light';
            document.documentElement.dataset.theme = next;
            setTheme(next);
        };
        mq.addEventListener('change', onChange);
        return () => mq.removeEventListener('change', onChange);
    }, []);

    const toggle = useCallback(() => {
        const next = current() === 'dark' ? 'light' : 'dark';
        document.documentElement.dataset.theme = next;
        setTheme(next);
        try {
            localStorage.setItem(KEY, next);
        } catch {
            /* The theme still changes for this visit; it just will not persist. */
        }
    }, []);

    return { theme, toggle };
};

export default useTheme;
