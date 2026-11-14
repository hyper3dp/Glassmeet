// Helpers for the beta features built on a host's meetings (see lib/beta.js): the week at a
// glance, the copyable agenda, the CSV export and a guest's history. Everything works on
// meetings a page has already loaded; nothing here asks the server.

const DAY_MS = 86400000;
const BOM = String.fromCharCode(0xfeff);

// a meeting that's going ahead (not cancelled, declined or waiting for approval)
export const isOn = (b) => b.status === 'confirmed' && b.approval_status !== 'pending' && b.approval_status !== 'declined';

const minutesOf = (b) => (b.end_time ? Math.max(0, Math.round((new Date(b.end_time) - new Date(b.start_time)) / 60000)) : 30);

// the meetings still ahead in the next 7 days, soonest first
export const nextWeek = (bookings, now = Date.now()) => bookings
  .filter((b) => isOn(b) && new Date(b.end_time || b.start_time).getTime() >= now && new Date(b.start_time).getTime() < now + 7 * DAY_MS)
  .sort((a, b) => new Date(a.start_time) - new Date(b.start_time));

// { meetings, minutes, busiest: { day, count } | null } for the next 7 days, days in the host's zone
export const weekSummary = (bookings, timeZone, now = Date.now()) => {
  const week = nextWeek(bookings, now);
  const dayName = new Intl.DateTimeFormat('en-US', { weekday: 'long', timeZone });
  const perDay = new Map();
  for (const b of week) {
    const day = dayName.format(new Date(b.start_time));
    perDay.set(day, (perDay.get(day) || 0) + 1);
  }
  let busiest = null;
  for (const [day, count] of perDay) if (!busiest || count > busiest.count) busiest = { day, count };
  return { meetings: week.length, minutes: week.reduce((sum, b) => sum + minutesOf(b), 0), busiest };
};

// "45 min", "2 h", "2 h 15 min"
export const formatHours = (minutes) => {
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  if (!h) return `${m} min`;
  return m ? `${h} h ${m} min` : `${h} h`;
};

// The next 7 days as plain text, one line per meeting under each day: ready for a WhatsApp chat,
// a note or an email
export const agendaText = (bookings, timeZone, now = Date.now()) => {
  const week = nextWeek(bookings, now);
  if (!week.length) return 'No meetings in the next 7 days.';
  const day = new Intl.DateTimeFormat('en-US', { weekday: 'long', day: 'numeric', month: 'long', timeZone });
  const time = new Intl.DateTimeFormat('en-US', { hour: 'numeric', minute: '2-digit', timeZone });
  const lines = ['My meetings for the next 7 days'];
  let current = '';
  for (const b of week) {
    const label = day.format(new Date(b.start_time));
    if (label !== current) { lines.push('', label); current = label; }
    const end = b.end_time ? ` – ${time.format(new Date(b.end_time))}` : '';
    lines.push(`• ${time.format(new Date(b.start_time))}${end}  ${b.guest_name || 'Guest'} (${b.event_name || 'Meeting'})`);
  }
  return lines.join('\n');
};

// ── CSV ─────────────────────────────────────────────────────────────────────
// Every cell quoted. Guests type their own name and notes, so a cell starting with = + - @ (or a
// tab or return) gets a leading ' to stop spreadsheet apps running it as a formula. A phone
// number (+, then only digits, spaces, dashes and brackets) can't run anything and stays as is.
const cell = (value) => {
  let text = String(value ?? '');
  if (/^[=+\-@\t\r]/.test(text) && !/^\+[\d\s()-]+$/.test(text)) text = `'${text}`;
  return `"${text.replace(/"/g, '""')}"`;
};

const statusOf = (b) => {
  if (b.approval_status === 'declined') return 'Declined';
  if (b.status === 'cancelled') return 'Canceled';
  if (b.approval_status === 'pending') return 'Pending';
  return 'Confirmed';
};

export const bookingsCsv = (bookings, timeZone) => {
  const date = new Intl.DateTimeFormat('en-CA', { year: 'numeric', month: '2-digit', day: '2-digit', timeZone });
  const time = new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit', hourCycle: 'h23', timeZone });
  const header = ['Date', 'Start', 'End', 'Time zone', 'Guest', 'Email', 'WhatsApp', 'Event type', 'Status', 'Guest notes', 'Host notes'];
  const rows = bookings.map((b) => [
    date.format(new Date(b.start_time)),
    time.format(new Date(b.start_time)),
    b.end_time ? time.format(new Date(b.end_time)) : '',
    timeZone,
    b.guest_name,
    b.guest_email,
    b.guest_phone,
    b.event_name || 'Meeting',
    statusOf(b),
    b.guest_notes,
    b.notes,
  ]);
  // CRLF and a byte-order mark, so Excel opens it as UTF-8 (names in Arabic, emoji) straight away
  return `${BOM}${[header, ...rows].map((r) => r.map(cell).join(',')).join('\r\n')}\r\n`;
};

export const downloadFile = (name, text, type = 'text/csv;charset=utf-8') => {
  const url = URL.createObjectURL(new Blob([text], { type }));
  const a = document.createElement('a');
  a.href = url;
  a.download = name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
};

// ── guest history ───────────────────────────────────────────────────────────
const digits = (phone) => (phone || '').replace(/\D/g, '');
const email = (value) => (value || '').trim().toLowerCase();

// The guest's other meetings with this host, newest first: same WhatsApp number (at least 6
// digits, so a short or empty one matches nobody) or same email
export const guestMeetings = (booking, bookings) => {
  const phone = digits(booking.guest_phone);
  const mail = email(booking.guest_email);
  return bookings
    .filter((b) => b.id !== booking.id
      && ((phone.length >= 6 && digits(b.guest_phone) === phone) || (mail && email(b.guest_email) === mail)))
    .sort((a, b) => new Date(b.start_time) - new Date(a.start_time));
};

export const meetingStatus = statusOf;
