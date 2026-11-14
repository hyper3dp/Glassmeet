// The Supabase library is ~215 kB, so it loads on first use instead of with every page: a
// signed-out visitor reading the landing page, Terms or Privacy never downloads it.
export const supabaseUrl = 'https://sjxraemrdpzleipfmwun.supabase.co';
const supabaseAnonKey = 'sb_publishable_0T8UhXmO8GUjyZa67yIU5w_RZwkPa4O';

// where supabase-js keeps the session (its default key for this project)
export const SESSION_KEY = `sb-${new URL(supabaseUrl).hostname.split('.')[0]}-auth-token`;

let client = null; // the loading (or loaded) client
let loaded = null; // the client once it has loaded
const ready = []; // callbacks waiting for the client (see whenSupabaseLoads)

export const getSupabase = () => {
  if (loaded) return Promise.resolve(loaded);
  if (!client) {
    client = import('@supabase/supabase-js').then(({ createClient }) => {
      const supabase = createClient(supabaseUrl, supabaseAnonKey, {
        auth: {
          persistSession: true,
          autoRefreshToken: true,
          detectSessionInUrl: true,
          // no explicit storage: reading window.localStorage throws when the browser blocks site
          // data (e.g. "block all cookies"), which would blank every page; supabase-js checks for
          // it and falls back to memory
        },
      });
      loaded = supabase;
      // listeners attach before any caller gets the client, so they see every auth event
      ready.splice(0).forEach((fn) => fn(supabase));
      return supabase;
    });
    // a failed download (offline, redeploy) can be retried by the next call
    client.catch(() => { client = null; });
  }
  return client;
};

// Runs fn with the client once something has loaded it, without loading it itself
export const whenSupabaseLoads = (fn) => {
  if (loaded) {
    fn(loaded);
    return () => {};
  }
  ready.push(fn);
  return () => { const i = ready.indexOf(fn); if (i >= 0) ready.splice(i, 1); };
};

// Whether this visit could be signed in: a stored session, or a sign-in link (tokens or an
// OAuth code in the address). False means signed out for certain, with no need to load Supabase.
export const maySignIn = () => {
  const { search, hash } = window.location;
  if (/[#&](access_token|refresh_token|error_description)=/.test(hash) || /[?&](code|token_hash|error_description)=/.test(search)) return true;
  try {
    return window.localStorage.getItem(SESSION_KEY) !== null;
  } catch {
    return true; // storage blocked: Supabase keeps the session in memory, so ask it
  }
};
