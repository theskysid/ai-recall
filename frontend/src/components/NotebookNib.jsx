import { useEffect, useRef, useState } from 'react';
import { motion, useMotionValue, useReducedMotion, useSpring } from 'motion/react';

/* The nib that reads the sheet.

   The cursor becomes a dot of ink; the ruled square it rests in takes
   the ink and darkens. The square snaps — a nib lands in a cell, it does
   not slide between them — while the dot itself tracks the hand. */

/* Must match --nb-line in index.css: the quadrille's pitch. */
const CELL = 34;

const INTERACTIVE = 'a, button, [role="button"]';

/* Bound out of `motion` so ESLint sees the import used — this file only
   ever reaches for it from JSX. */
const MotionSpan = motion.span;

const NotebookNib = ({ containerRef }) => {
    const reduced = useReducedMotion();

    /* A nib is a mouse. `any-*` (not the primary-pointer `hover`/`pointer`)
       so a touchscreen laptop — coarse PRIMARY pointer, fine trackpad also
       present — still gets the nib. Pure-touch phones stay on their own
       cursor. Must match the cursor:none media query in MainPage.css. */
    const fineRef = useRef(null);
    if (fineRef.current === null) {
        fineRef.current =
            typeof window !== 'undefined' &&
            window.matchMedia('(any-hover: hover) and (any-pointer: fine)').matches;
    }
    const fine = fineRef.current;

    /* Motion values, not state — the dot must not re-render the page on
       every pointermove. */
    const x = useMotionValue(-200);
    const y = useMotionValue(-200);
    const dotX = useSpring(x, { stiffness: 1500, damping: 72, mass: 0.32 });
    const dotY = useSpring(y, { stiffness: 1500, damping: 72, mass: 0.32 });

    /* These two do re-render, but only when the nib crosses a rule or
       passes onto something clickable. */
    const [cell, setCell] = useState(null);
    const [overLink, setOverLink] = useState(false);

    useEffect(() => {
        const root = containerRef.current;
        if (!root || !fine) return undefined;

        const onMove = (event) => {
            /* TODO: two getBoundingClientRect reads per move. One layout
               read, no write — swap for a cached rect + resize/scroll listener
               only if this ever shows up in a profile. */
            const rootRect = root.getBoundingClientRect();
            x.set(event.clientX - rootRect.left);
            y.set(event.clientY - rootRect.top);

            const el = event.target instanceof Element ? event.target : null;
            setOverLink(Boolean(el?.closest(INTERACTIVE)));

            /* The quadrille is painted per sheet, so each section restarts
               the grid at its own top-left. Snap against the sheet the nib
               is actually over, never against the page. */
            const sheet = el?.closest('.nb-sheet');
            if (!sheet) {
                setCell(null);
                return;
            }

            const sheetRect = sheet.getBoundingClientRect();
            const cx =
                Math.floor((event.clientX - sheetRect.left) / CELL) * CELL +
                sheetRect.left -
                rootRect.left;
            const cy =
                Math.floor((event.clientY - sheetRect.top) / CELL) * CELL +
                sheetRect.top -
                rootRect.top;

            setCell((prev) => (prev && prev.x === cx && prev.y === cy ? prev : { x: cx, y: cy }));
        };

        const onLeave = () => setCell(null);

        root.addEventListener('pointermove', onMove);
        root.addEventListener('pointerleave', onLeave);
        return () => {
            root.removeEventListener('pointermove', onMove);
            root.removeEventListener('pointerleave', onLeave);
        };
    }, [containerRef, fine, x, y]);

    /* Only a real absence of a fine pointer removes the nib. Reduced motion
       keeps the cursor — a pointer is not decorative motion — but tracks it
       instantly instead of on a spring, and snaps the cell with no easing. */
    if (!fine) return null;

    const cellTransition = reduced
        ? { duration: 0 }
        : {
              type: 'spring',
              stiffness: 720,
              damping: 44,
              mass: 0.55,
              opacity: { duration: 0.2, ease: 'easeOut' }
          };

    return (
        <div className="nib-layer" aria-hidden="true">
            {cell && (
                <MotionSpan
                    className="nib-cell"
                    /* Fades in on the square it landed on, then snaps
                       from square to square for as long as it lives. */
                    initial={{ opacity: 0, x: cell.x, y: cell.y }}
                    animate={{ opacity: 1, x: cell.x, y: cell.y }}
                    transition={cellTransition}
                />
            )}

            <MotionSpan
                className="nib-dot"
                style={{ x: reduced ? x : dotX, y: reduced ? y : dotY }}
                animate={{ scale: overLink ? 2.8 : 1 }}
                transition={reduced ? { duration: 0 } : { type: 'spring', stiffness: 520, damping: 30, mass: 0.5 }}
            />
        </div>
    );
};

export default NotebookNib;
