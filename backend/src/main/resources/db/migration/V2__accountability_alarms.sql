-- 책임 알람 (docs/API.md 7절). 시각은 모두 UTC로 저장한다.

-- 앱이 계산해서 보고한 확정 장소까지의 이동시간. 장소 이름이 지금 확정 장소와 같을 때만 쓴다
alter table participants
    add column travel_place_name varchar(60),
    add column travel_minutes    double precision,
    add column travel_transfers  integer;

-- 참가자마다 알람 하나. 참가자가 나가거나 약속이 지워지면 함께 지워진다
create table alarms (
    id             uuid         primary key,
    meeting_id     uuid         not null references meetings (id) on delete cascade,
    participant_id uuid         not null unique references participants (id) on delete cascade,
    fire_at        timestamptz  not null,
    -- fire_at + 유예시간. 이때까지 끔 보고가 없으면 에스컬레이션한다
    grace_until    timestamptz  not null,
    status         varchar(10)  not null,
    ringing_at     timestamptz,
    dismissed_at   timestamptz,
    dismiss_source varchar(12),
    escalated_at   timestamptz,
    updated_at     timestamptz  not null
);

create index alarms_meeting_id_idx on alarms (meeting_id);
-- Redis 에스컬레이션 큐를 DB에서 다시 채울 때 쓴다
create index alarms_active_grace_idx on alarms (grace_until) where status in ('SCHEDULED', 'RINGING');

-- 푸시를 받을 기기. 참가자 토큰 하나에 기기 하나
create table devices (
    participant_id uuid         primary key references participants (id) on delete cascade,
    fcm_token      varchar(512) not null,
    platform       varchar(10)  not null,
    app_version    varchar(20),
    updated_at     timestamptz  not null
);
