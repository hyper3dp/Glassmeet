-- GlassMeet: everything a booking page shows in one request. The page used to ask for the event,
-- then (once it knew the host) for their hours, busy times and holidays: two round trips to the
-- database before a guest saw a single date. This returns the same four answers at once.
-- Security invoker: it only calls the public functions guests can already call, with the
-- caller's own rights, so it shows nothing they couldn't read before. The app falls back to the
-- separate requests until this is applied. Run after 20261114120000_guest_contact_checks.sql.
-- Safe to run more than once.

create or replace function public.booking_page(p_id uuid)
returns jsonb language sql stable security invoker set search_path = '' as $$
  with ev as (select public.public_event_type(p_id) as e),
  host as (select (e ->> 'created_by_id')::uuid as id from ev where e is not null)
  select case when (select e from ev) is null then null else jsonb_build_object(
    'event', (select e from ev),
    'availability', public.host_availability((select id from host)),
    -- as the page asked before: confirmed bookings from yesterday on
    'busy', coalesce((
      select jsonb_agg(to_jsonb(b))
      from public.busy_times((select id from host), now() - interval '1 day', now() + interval '800 days') b
      where b.status = 'confirmed'
    ), '[]'::jsonb),
    'holidays', coalesce((
      select jsonb_agg(to_jsonb(h)) from public.host_holidays((select id from host)) h
    ), '[]'::jsonb)
  ) end;
$$;
revoke execute on function public.booking_page(uuid) from public;
grant execute on function public.booking_page(uuid) to anon, authenticated;
