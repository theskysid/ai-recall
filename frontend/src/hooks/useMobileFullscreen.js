import { useEffect } from 'react';

/* Auto-fullscreen on mobile — as automatic as a browser permits.

   The Fullscreen API refuses to run without a user gesture, so there is no
   legal "fullscreen on load". The next best thing is to enter it on the very
   first tap of the session, once. Installed as a PWA (see the manifest) the
   app already launches chrome-free, so this is the browser-tab fallback.

   iOS Safari on iPhone has no element.requestFullscreen at all — the guard
   below no-ops there, and Add-to-Home-Screen is the fullscreen path instead. */
export default function useMobileFullscreen() {
    useEffect(() => {
        const isMobile = window.matchMedia('(max-width: 768px)').matches;
        const el = document.documentElement;
        if (!isMobile || !document.fullscreenEnabled || !el.requestFullscreen) {
            return undefined;
        }

        const enter = () => {
            if (!document.fullscreenElement) {
                // A rejected request (user setting, iframe policy) is fine — swallow it.
                el.requestFullscreen().catch(() => {});
            }
            remove();
        };

        const remove = () => {
            window.removeEventListener('pointerdown', enter);
            window.removeEventListener('keydown', enter);
        };

        window.addEventListener('pointerdown', enter, { once: true });
        window.addEventListener('keydown', enter, { once: true });
        return remove;
    }, []);
}
