-- Entrenador IA: registro de uso y errores de la beta (opcional, con consentimiento en la app).
-- Dos niveles: uso/errores, y "Incluir mis registros" (comida, peso, agua, resumen diario,
-- inicio de los mensajes al coach). Son datos de salud: no compartas este panel.
-- Ejecutar una vez en Supabase: Dashboard -> SQL Editor -> pegar todo -> Run.
-- La app solo puede INSERTAR con la anon key; nadie puede leer con esa clave.
-- Tú lees desde el panel de Supabase (Table Editor / SQL Editor), que usa permisos de dueño.

create table if not exists public.events (
    id           bigint generated always as identity primary key,
    received_at  timestamptz not null default now(),
    occurred_at  timestamptz not null,
    install_id   uuid not null,
    name         text not null check (char_length(name) between 1 and 64),
    app_version  text check (char_length(app_version) <= 40),
    device       text check (char_length(device) <= 80),
    android      int,
    locale       text check (char_length(locale) <= 20),
    props        jsonb not null default '{}'::jsonb check (pg_column_size(props) <= 8000)
);

create index if not exists events_received_at_idx on public.events (received_at desc);
create index if not exists events_name_idx on public.events (name, received_at desc);
create index if not exists events_install_idx on public.events (install_id, received_at desc);

alter table public.events enable row level security;

-- Solo insertar, nunca leer/editar/borrar desde la app.
revoke all on public.events from anon, authenticated;
grant insert (occurred_at, install_id, name, app_version, device, android, locale, props) on public.events to anon;

drop policy if exists "app inserts events" on public.events;
create policy "app inserts events" on public.events
    for insert to anon
    with check (occurred_at > now() - interval '60 days' and occurred_at < now() + interval '1 day');

-- Limpieza: borra eventos de más de 90 días. Requiere la extensión pg_cron
-- (Dashboard -> Database -> Extensions -> pg_cron). Si no la activas, puedes correr el DELETE a mano.
-- create extension if not exists pg_cron;
-- select cron.schedule('events-retention', '0 4 * * *', $$delete from public.events where received_at < now() - interval '90 days'$$);

-- ---------------------------------------------------------------------------
-- Vistas para mirar (solo las ve el dueño del proyecto; no se exponen a anon).
-- ---------------------------------------------------------------------------

-- Errores y cierres de los últimos 7 días, más recientes primero.
create or replace view public.v_errors_recent with (security_invoker = true) as
select received_at, name, app_version, device,
       props->>'where' as donde, props->>'type' as tipo, props->>'message' as mensaje,
       props->>'stack' as stack, install_id
from public.events
where (name in ('error', 'crash') or props->>'ok' = 'false')
  and received_at > now() - interval '7 days'
order by received_at desc;

-- Personas activas por día (instalaciones distintas).
create or replace view public.v_daily_active with (security_invoker = true) as
select date_trunc('day', occurred_at)::date as dia, count(distinct install_id) as personas, count(*) as eventos
from public.events
group by 1 order by 1 desc;

-- Qué usan: pantallas y funciones en los últimos 7 días.
create or replace view public.v_feature_usage with (security_invoker = true) as
select name, coalesce(props->>'route', props->>'kind', props->>'section', '') as detalle,
       count(*) as veces, count(distinct install_id) as personas
from public.events
where received_at > now() - interval '7 days' and name not in ('error', 'crash')
group by 1, 2 order by veces desc;

-- Llamadas a la IA: cuántas fallan y cuánto tardan, por versión.
create or replace view public.v_ai_health with (security_invoker = true) as
select app_version, name,
       count(*) as llamadas,
       count(*) filter (where props->>'ok' = 'false') as fallidas,
       round(avg((props->>'ms')::numeric)) as ms_promedio
from public.events
where name in ('ai_call', 'coach_reply') and received_at > now() - interval '14 days'
group by 1, 2 order by 1 desc, 2;

-- Registros (solo de quienes activaron "Incluir mis registros").
-- Comida registrada, más reciente primero.
create or replace view public.v_food_log with (security_invoker = true) as
select occurred_at, install_id, props->>'food' as comida, (props->>'kcal')::int as kcal,
       (props->>'protein_g')::numeric as proteina_g, (props->>'carbs_g')::numeric as carbos_g,
       (props->>'fat_g')::numeric as grasa_g, props->>'source' as origen, props->>'meal' as comida_del_dia
from public.events where name = 'food_logged'
order by occurred_at desc;

-- Un día por persona: lo comido, agua, peso, sueño, pasos y entrenamiento.
create or replace view public.v_daily_summary with (security_invoker = true) as
select (props->>'day')::date as dia, install_id,
       (props->>'kcal')::int as kcal, (props->>'protein_g')::int as proteina_g,
       (props->>'foods')::int as comidas, (props->>'water_ml')::int as agua_ml,
       (props->>'weight_kg')::numeric as peso_kg, (props->>'sleep_min')::int as sueno_min,
       (props->>'steps')::int as pasos, (props->>'workouts')::int as entrenos,
       (props->>'workout_min')::int as entreno_min
from public.events where name = 'daily_summary'
order by dia desc, install_id;

-- Qué le preguntan al coach (primeros 300 caracteres).
create or replace view public.v_coach_messages with (security_invoker = true) as
select occurred_at, install_id, props->>'text' as mensaje, (props->>'image')::boolean as con_foto
from public.events where name = 'coach_message'
order by occurred_at desc;

revoke all on public.v_errors_recent, public.v_daily_active, public.v_feature_usage, public.v_ai_health,
              public.v_food_log, public.v_daily_summary, public.v_coach_messages from anon, authenticated;
