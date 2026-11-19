-- DawaBox / Tadhkira cloud schema (Supabase).
-- Patients own their data; doctors and caregivers see a patient only after
-- the patient gives them a 6-digit link code.

-- Profiles: one per auth user. role = patient | doctor | caregiver
create table public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  role text not null default 'patient' check (role in ('patient','doctor','caregiver')),
  full_name text not null default '',
  phone text not null default '',
  clinic_name text not null default '',
  created_at timestamptz not null default now()
);

-- Who may see / manage a patient's data (doctor or caregiver)
create table public.care_links (
  id uuid primary key default gen_random_uuid(),
  patient_id uuid not null references public.profiles(id) on delete cascade,
  carer_id uuid not null references public.profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  unique (patient_id, carer_id),
  check (patient_id <> carer_id)
);
create index care_links_carer_idx on public.care_links(carer_id);
create index care_links_patient_idx on public.care_links(patient_id);

-- Short-lived codes a patient shows to a doctor / caregiver to link accounts
create table public.link_codes (
  code text primary key,
  patient_id uuid not null references public.profiles(id) on delete cascade,
  expires_at timestamptz not null default now() + interval '24 hours'
);
create index link_codes_patient_idx on public.link_codes(patient_id);

create table public.medications (
  id uuid primary key default gen_random_uuid(),
  patient_id uuid not null references public.profiles(id) on delete cascade,
  name text not null,
  dosage text not null default '',
  form text not null default 'TABLET',
  frequency text not null default 'DAILY',
  times text[] not null default '{}',
  start_date date not null default current_date,
  compartment int not null default 1 check (compartment between 1 and 7),
  instructions text not null default '',
  stock int not null default 0,
  channels text[] not null default '{APP,VOICE,SMS}',
  active boolean not null default true,
  created_by uuid references public.profiles(id) on delete set null,
  updated_at timestamptz not null default now()
);
create index medications_patient_idx on public.medications(patient_id);
create index medications_created_by_idx on public.medications(created_by);

create table public.dose_logs (
  id uuid primary key default gen_random_uuid(),
  patient_id uuid not null references public.profiles(id) on delete cascade,
  medication_id uuid references public.medications(id) on delete set null,
  med_name text not null,
  date date not null,
  time text not null,
  status text not null check (status in ('TAKEN','MISSED')),
  source text not null default 'app',
  at timestamptz not null default now(),
  unique (patient_id, medication_id, date, time)
);
create index dose_logs_patient_date_idx on public.dose_logs(patient_id, date);
create index dose_logs_medication_idx on public.dose_logs(medication_id);

create table public.appointments (
  id uuid primary key default gen_random_uuid(),
  patient_id uuid not null references public.profiles(id) on delete cascade,
  doctor text not null,
  place text not null default '',
  date_time text not null,           -- local time, yyyy-MM-ddTHH:mm (same as the app)
  notes text not null default '',
  active boolean not null default true,
  created_by uuid references public.profiles(id) on delete set null,
  updated_at timestamptz not null default now()
);
create index appointments_patient_idx on public.appointments(patient_id);
create index appointments_created_by_idx on public.appointments(created_by);

-- ---------- helpers ----------
create or replace function public.can_access_patient(p uuid)
returns boolean language sql stable security definer set search_path = '' as $$
  select p = (select auth.uid())
      or exists (select 1 from public.care_links c where c.patient_id = p and c.carer_id = (select auth.uid()));
$$;

create or replace function public.touch_updated_at()
returns trigger language plpgsql set search_path = '' as $$
begin new.updated_at := now(); return new; end; $$;
create trigger medications_touch before update on public.medications for each row execute function public.touch_updated_at();
create trigger appointments_touch before update on public.appointments for each row execute function public.touch_updated_at();

-- New auth user -> profile (role/name come from sign-up metadata)
create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  insert into public.profiles (id, role, full_name, phone, clinic_name)
  values (
    new.id,
    case when new.raw_user_meta_data->>'role' in ('patient','doctor','caregiver') then new.raw_user_meta_data->>'role' else 'patient' end,
    coalesce(new.raw_user_meta_data->>'full_name', ''),
    coalesce(new.raw_user_meta_data->>'phone', ''),
    coalesce(new.raw_user_meta_data->>'clinic_name', '')
  );
  return new;
end; $$;
create trigger on_auth_user_created after insert on auth.users for each row execute function public.handle_new_user();

-- Patient: make a 6-digit code valid for 24 h
create or replace function public.create_link_code()
returns text language plpgsql security definer set search_path = '' as $$
declare c text;
begin
  if (select auth.uid()) is null then raise exception 'not signed in'; end if;
  delete from public.link_codes where patient_id = (select auth.uid()) or expires_at < now();
  loop
    c := lpad((floor(random() * 1000000))::int::text, 6, '0');
    exit when not exists (select 1 from public.link_codes where code = c);
  end loop;
  insert into public.link_codes(code, patient_id) values (c, (select auth.uid()));
  return c;
end; $$;

-- Doctor / caregiver: redeem a patient's code -> creates the care link
create or replace function public.redeem_link_code(p_code text)
returns uuid language plpgsql security definer set search_path = '' as $$
declare pid uuid;
begin
  if (select auth.uid()) is null then raise exception 'not signed in'; end if;
  select patient_id into pid from public.link_codes where code = trim(p_code) and expires_at > now();
  if pid is null then raise exception 'invalid or expired code'; end if;
  if pid = (select auth.uid()) then raise exception 'cannot link to yourself'; end if;
  insert into public.care_links(patient_id, carer_id) values (pid, (select auth.uid())) on conflict do nothing;
  delete from public.link_codes where code = trim(p_code);
  return pid;
end; $$;

revoke execute on function public.create_link_code() from anon, public;
revoke execute on function public.redeem_link_code(text) from anon, public;
grant execute on function public.create_link_code() to authenticated;
grant execute on function public.redeem_link_code(text) to authenticated;
revoke execute on function public.handle_new_user() from anon, authenticated, public;
revoke execute on function public.can_access_patient(uuid) from anon, public;
grant execute on function public.can_access_patient(uuid) to authenticated;

-- ---------- row level security ----------
alter table public.profiles enable row level security;
alter table public.care_links enable row level security;
alter table public.link_codes enable row level security;
alter table public.medications enable row level security;
alter table public.dose_logs enable row level security;
alter table public.appointments enable row level security;

create policy "profiles: self and linked people can read" on public.profiles for select to authenticated
  using (id = (select auth.uid())
      or public.can_access_patient(id)
      or exists (select 1 from public.care_links c where c.carer_id = profiles.id and c.patient_id = (select auth.uid())));
create policy "profiles: update self" on public.profiles for update to authenticated
  using (id = (select auth.uid())) with check (id = (select auth.uid()));

create policy "care_links: both sides can read" on public.care_links for select to authenticated
  using (patient_id = (select auth.uid()) or carer_id = (select auth.uid()));
create policy "care_links: both sides can remove" on public.care_links for delete to authenticated
  using (patient_id = (select auth.uid()) or carer_id = (select auth.uid()));

create policy "link_codes: patient reads own" on public.link_codes for select to authenticated
  using (patient_id = (select auth.uid()));

create policy "medications: read" on public.medications for select to authenticated using (public.can_access_patient(patient_id));
create policy "medications: insert" on public.medications for insert to authenticated with check (public.can_access_patient(patient_id));
create policy "medications: update" on public.medications for update to authenticated using (public.can_access_patient(patient_id)) with check (public.can_access_patient(patient_id));
create policy "medications: delete" on public.medications for delete to authenticated using (public.can_access_patient(patient_id));

create policy "dose_logs: read" on public.dose_logs for select to authenticated using (public.can_access_patient(patient_id));
create policy "dose_logs: insert" on public.dose_logs for insert to authenticated with check (public.can_access_patient(patient_id));
create policy "dose_logs: update" on public.dose_logs for update to authenticated using (public.can_access_patient(patient_id)) with check (public.can_access_patient(patient_id));
create policy "dose_logs: delete" on public.dose_logs for delete to authenticated using (public.can_access_patient(patient_id));

create policy "appointments: read" on public.appointments for select to authenticated using (public.can_access_patient(patient_id));
create policy "appointments: insert" on public.appointments for insert to authenticated with check (public.can_access_patient(patient_id));
create policy "appointments: update" on public.appointments for update to authenticated using (public.can_access_patient(patient_id)) with check (public.can_access_patient(patient_id));
create policy "appointments: delete" on public.appointments for delete to authenticated using (public.can_access_patient(patient_id));

-- Live updates for the clinic dashboard
alter publication supabase_realtime add table public.dose_logs, public.medications, public.appointments, public.care_links;
