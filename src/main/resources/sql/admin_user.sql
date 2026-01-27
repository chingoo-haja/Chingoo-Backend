INSERT IGNORE INTO users (
    email,
    password,
    nickname,
    real_name,
    gender,
    birth,
    phone_number,
    user_type,
    profile_image_url,
    provider,
    provider_id,
    created_at,
    updated_at
) VALUES (
    'admin@chingoohaja.app',
    '$2a$10$vBJjKIdUkaJi8GvrH6gf1.7mbQ1522K98UiWz4BtVnF09E1WiA5ny',
    'Admin',
    '시스템 관리자',
    'MALE',
    '1990-01-01',
    '010-0000-0000',
    'ADMIN',
    NULL,
    'local',
    'admin@chingoohaja.app',
    NOW(),
    NOW()
);