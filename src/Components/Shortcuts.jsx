import React, { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { X } from 'lucide-react';
import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog';
import { BetaTag } from '@/components/glass';

// Beta keyboard shortcuts (lib/beta.js 'shortcuts'). AppShell loads this file only while the
// feature is on. G then a letter opens a page, N starts a new event type, / goes to the page's
// search field and ? lists them all. Never while typing in a field, with Ctrl/Cmd/Alt held, or
// while a dialog is open (Escape and the dialog's own keys stay theirs).
const PAGES = [
  { key: 'd', label: 'Dashboard', path: '/dashboard' },
  { key: 'c', label: 'Calendar', path: '/calendar' },
  { key: 'm', label: 'Meetings', path: '/bookings' },
  { key: 'e', label: 'Event types', path: '/event-types' },
  { key: 'a', label: 'Availability', path: '/availability' },
  { key: 'w', label: 'WhatsApp', path: '/whatsapp' },
  { key: 's', label: 'Settings', path: '/settings' },
];
const OTHERS = [
  { keys: ['N'], label: 'New event type' },
  { keys: ['/'], label: 'Search this page' },
  { keys: ['Ctrl', 'K'], label: 'Command menu' },
  { keys: ['?'], label: 'Show these shortcuts' },
];
const SEQUENCE_MS = 1500; // how long G waits for its letter

const typing = (el) => !!el && (el.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(el.tagName));

const Key = ({ children }) => (
  <kbd className="inline-flex h-6 min-w-6 items-center justify-center rounded-md border border-border bg-secondary px-1.5 text-xs font-semibold text-foreground">{children}</kbd>
);

export default function Shortcuts() {
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const pendingG = useRef(0); // when G was pressed, or 0

  useEffect(() => {
    const onKey = (e) => {
      if (e.defaultPrevented || e.ctrlKey || e.metaKey || e.altKey || typing(e.target)) return;
      if (document.querySelector('[role="dialog"]')) return;
      const key = e.key;
      if (pendingG.current && Date.now() - pendingG.current < SEQUENCE_MS) {
        pendingG.current = 0;
        const page = PAGES.find((p) => p.key === key.toLowerCase());
        if (page) { e.preventDefault(); navigate(page.path); }
        return;
      }
      pendingG.current = 0;
      if (key === 'g' || key === 'G') { pendingG.current = Date.now(); return; }
      if (key === '?') { e.preventDefault(); setOpen(true); return; }
      if (key === 'n' || key === 'N') { e.preventDefault(); navigate('/event-types?new=true'); return; }
      if (key === '/') {
        const search = document.querySelector('main input[type="search"], input[type="search"]');
        if (search) { e.preventDefault(); search.focus(); search.select?.(); }
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [navigate]);

  if (!open) return null;
  return (
    <Dialog open onOpenChange={(next) => { if (!next) setOpen(false); }}>
      <DialogContent showCloseButton={false} aria-describedby={undefined} className="sm:max-w-md p-6 block">
        <div className="mb-4 flex items-center gap-2">
          <DialogTitle>Keyboard shortcuts</DialogTitle>
          <BetaTag />
          <button type="button" onClick={() => setOpen(false)} aria-label="Close" className="ml-auto w-8 h-8 rounded-full flex items-center justify-center text-muted-foreground transition-all duration-200 hover:bg-secondary"><X className="w-4 h-4" /></button>
        </div>
        <h3 className="text-sm font-semibold">Go to a page</h3>
        <ul className="mt-2 divide-y divide-border rounded-lg border border-border">
          {PAGES.map((p) => (
            <li key={p.key} className="flex items-center justify-between px-3.5 py-2 text-sm">
              {p.label}
              <span className="flex items-center gap-1"><Key>G</Key> <span className="text-hint">then</span> <Key>{p.key.toUpperCase()}</Key></span>
            </li>
          ))}
        </ul>
        <h3 className="mt-4 text-sm font-semibold">Anywhere</h3>
        <ul className="mt-2 divide-y divide-border rounded-lg border border-border">
          {OTHERS.map((o) => (
            <li key={o.label} className="flex items-center justify-between px-3.5 py-2 text-sm">
              {o.label}
              <span className="flex items-center gap-1">{o.keys.map((k) => <Key key={k}>{k}</Key>)}</span>
            </li>
          ))}
        </ul>
        <p className="mt-4 text-xs text-muted-foreground">Shortcuts pause while you’re typing in a field. Turn them off in Settings, Beta features.</p>
      </DialogContent>
    </Dialog>
  );
}
