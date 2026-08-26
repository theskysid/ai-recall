/* The landing page's primary action.

   Ported off shadcn/Tailwind: this was the only component in the app using
   utility classes, and it carried tailwindcss, @tailwindcss/vite,
   class-variance-authority and @radix-ui/react-slot for one button. The
   surrounding app is hand-written CSS, so the styles now live in
   styles/LiquidButton.css and those four dependencies are gone.

   The variant/size matrix went with them — every call site used the same
   (default, lg) pair. Add a prop back when a second look actually appears. */
import '../../styles/LiquidButton.css';

export default function LiquidButton({ className = '', children, ...props }) {
    return (
        <button type="button" className={`liquid-btn ${className}`.trim()} {...props}>
            {/* Refraction layer: samples what is behind the button and warps it. */}
            <span className="liquid-btn-glass" aria-hidden="true" />
            <span className="liquid-btn-label">{children}</span>
            <GlassFilter />
        </button>
    );
}

/* The SVG filter the glass layer's backdrop-filter points at. Inline rather
   than in index.html so the button stays self-contained; duplicate ids across
   several buttons on a page resolve to the first, which is identical. */
function GlassFilter() {
    return (
        <svg className="liquid-btn-filter" aria-hidden="true" focusable="false">
            <defs>
                <filter id="container-glass" x="0%" y="0%" width="100%" height="100%"
                        colorInterpolationFilters="sRGB">
                    <feTurbulence type="fractalNoise" baseFrequency="0.05 0.05"
                                  numOctaves="1" seed="1" result="turbulence" />
                    <feGaussianBlur in="turbulence" stdDeviation="2" result="blurredNoise" />
                    <feDisplacementMap in="SourceGraphic" in2="blurredNoise" scale="70"
                                       xChannelSelector="R" yChannelSelector="B" result="displaced" />
                    <feGaussianBlur in="displaced" stdDeviation="4" result="finalBlur" />
                    <feComposite in="finalBlur" in2="finalBlur" operator="over" />
                </filter>
            </defs>
        </svg>
    );
}
