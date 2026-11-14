import { useSyncExternalStore } from 'react';

// Hidden beta features. Tapping "Coming soon" in Settings five times unlocks the Beta features
// menu, where each feature has its own switch. Kept per browser; the menu can be hidden again.
export const BETA_FEATURES = [
  { id: 'embed', name: 'Add to website', body: 'Put a booking button, pop-up or link for any event type on your own website. Find it in each event type’s menu.' },
  { id: 'countdown', name: 'Next meeting countdown', body: 'See how long until your next meeting at the top of every page, with a Join button when it’s about to start. Works straight away, with nothing to set up.' },
  { id: 'week', name: 'This week at a glance', body: 'A card on your dashboard with your meetings and booked hours for the next 7 days, and your busiest day.' },
  { id: 'agenda', name: 'Copy your agenda', body: 'Copy the next 7 days of meetings as plain text from your dashboard, ready to paste into a WhatsApp chat, a note or an email.' },
  { id: 'export', name: 'Export meetings', body: 'Download the meetings you’re looking at on the Meetings page as a spreadsheet (CSV) for Excel, Numbers or Google Sheets.' },
  { id: 'guestHistory', name: 'Guest history', body: 'See how often you’ve met a guest before, and when, in each meeting’s details. Matched by WhatsApp number or email.' },
  { id: 'shortcuts', name: 'Keyboard shortcuts', body: 'Move around without the mouse: press G then D, C, M, E, A, W or S to open a page, N for a new event type and ? to see them all.' },
];

const KEY = 'gm-beta';
const read = () => {
  try { return JSON.parse(localStorage.getItem(KEY)) || null; } catch { return null; }
};

let state = read();
const listeners = new Set();
const write = (next) => {
  state = next;
  try {
    if (next) localStorage.setItem(KEY, JSON.stringify(next));
    else localStorage.removeItem(KEY);
  } catch { /* storage unavailable: lasts until reload */ }
  listeners.forEach((l) => l());
};

// other tabs follow along
if (typeof window !== 'undefined') {
  window.addEventListener('storage', (e) => {
    if (e.key === KEY) { state = read(); listeners.forEach((l) => l()); }
  });
}

const subscribe = (l) => { listeners.add(l); return () => listeners.delete(l); };

// features start on when the menu is unlocked
export const unlockBeta = () => write({ ...Object.fromEntries(BETA_FEATURES.map((f) => [f.id, true])), ...state, unlocked: true });
export const hideBeta = () => write(null);
export const setBetaFeature = (id, on) => write({ ...state, [id]: on });

export const useBeta = () => useSyncExternalStore(subscribe, () => state);
// on unless switched off, so a feature added after the menu was unlocked starts on too
export const betaFeatureOn = (beta, id) => !!beta?.unlocked && beta[id] !== false;
export const useBetaFeature = (id) => {
  const beta = useBeta();
  return betaFeatureOn(beta, id);
};
