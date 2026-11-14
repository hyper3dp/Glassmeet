-- GlassMeet: guests type their email and WhatsApp number, and anyone can insert a booking through
-- the API without the booking page's checks. Until now only their length was limited, so a line
-- break in either could add lines to the .ics files and calendar feeds built from the booking.
-- New and changed rows must now be a plain address and a number without control characters.
-- NOT VALID: rows already saved are left as they are. Run after
-- 20261107120000_day_limit_connections.sql. Safe to run more than once.

-- the booking page's own rule (^[^\s@]+@[^\s@]+\.[^\s@]+$), which also rules out line breaks
alter table public.bookings drop constraint if exists bookings_guest_email_format;
alter table public.bookings add constraint bookings_guest_email_format
  check (guest_email is null or guest_email = '' or guest_email ~ '^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$') not valid;

alter table public.bookings drop constraint if exists bookings_guest_phone_plain;
alter table public.bookings add constraint bookings_guest_phone_plain
  check (guest_phone is null or guest_phone !~ '[[:cntrl:]]') not valid;

alter table public.bookings drop constraint if exists bookings_guest_name_plain;
alter table public.bookings add constraint bookings_guest_name_plain
  check (guest_name is null or guest_name !~ '[[:cntrl:]]') not valid;
