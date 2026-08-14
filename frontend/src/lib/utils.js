/* shadcn's cn(), minus the deps.

   shadcn ships cn = twMerge(clsx(inputs)). We control every className we
   pass to these buttons (no conflicting utilities collide at runtime), so
   a flatten-filter-join does the same job without pulling in clsx +
   tailwind-merge.

   TODO: naive join, no conflict resolution. If a caller ever needs to
   OVERRIDE a base utility via className (e.g. pass `rounded-full` to beat
   `rounded-md`), install clsx + tailwind-merge and swap this one line. */
export function cn(...inputs) {
  return inputs.flat(Infinity).filter(Boolean).join(" ");
}
