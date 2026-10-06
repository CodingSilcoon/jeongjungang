-- 약속과 참가자 (docs/API.md 6절). 시각은 모두 UTC로 저장한다.

create table meetings (
    id                     uuid         primary key,
    invite_code            varchar(8)   not null unique,
    title                  varchar(40),
    purpose                varchar(10)  not null,
    meet_at                timestamptz,
    status                 varchar(10)  not null,
    place_name             varchar(60),
    place_lat              double precision,
    place_lng              double precision,
    place_lines            integer[],
    accountability_enabled boolean      not null default false,
    grace_period_sec       integer      not null default 60,
    margin_minutes         integer      not null default 5,
    -- 약속이나 참가자가 바뀔 때마다 올린다. GET /meetings/{id}의 version·ETag로 쓴다
    state_version          bigint       not null,
    created_at             timestamptz  not null,
    -- 약속 시각 + 24시간, 약속 시각이 없으면 생성 + 7일 (docs/API.md 4절)
    expires_at             timestamptz  not null
);

create index meetings_expires_at_idx on meetings (expires_at);

create table participants (
    id           uuid         primary key,
    meeting_id   uuid         not null references meetings (id) on delete cascade,
    -- 참가자 토큰의 SHA-256(16진수). 토큰 원문은 저장하지 않는다
    token_hash   varchar(64)  not null unique,
    nickname     varchar(20)  not null,
    role         varchar(10)  not null,
    origin_label varchar(60),
    origin_lat   double precision,
    origin_lng   double precision,
    prep_minutes integer,
    opted_in     boolean      not null default false,
    -- 들어온 순서(1부터). 참가자 목록 순서와 다음 방장을 정하는 기준
    join_order   integer      not null,
    joined_at    timestamptz  not null,
    unique (meeting_id, join_order)
);
