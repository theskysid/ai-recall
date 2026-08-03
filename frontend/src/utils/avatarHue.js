/* A name always gets the same avatar colour, on every device and across
   reloads.

   The stored per-user `color` cannot do this job: it defaults to one blue
   for every account and is arbitrary hex, so white initials on it are not
   guaranteed to be legible. The hues these indexes map to are defined
   once, in ChannelPage.css, and every one of them clears 4.5:1 against
   the white initial it carries. */

export const AVATAR_HUES = 6;

export const hueOf = (name) => {
    let h = 0;
    for (let i = 0; i < (name || '').length; i++) {
        h = (h * 31 + name.charCodeAt(i)) >>> 0;
    }
    return h % AVATAR_HUES;
};

/* Convenience for the common case: the full class string for an avatar. */
export const hueClass = (name) => `hue-${hueOf(name)}`;
