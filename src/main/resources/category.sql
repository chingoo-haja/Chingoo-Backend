-- 일반 매칭용 카테고리 (RANDOM)
INSERT INTO categories (name, is_active, category_type, created_at, updated_at) VALUES
('취미', true, 'RANDOM', NOW(), NOW()),
('자녀', true, 'RANDOM', NOW(), NOW()),
('요리', true, 'RANDOM', NOW(), NOW()),
('추억', true, 'RANDOM', NOW(), NOW()),
('음악', true, 'RANDOM', NOW(), NOW()),
('여행', true, 'RANDOM', NOW(), NOW()),
('운동', true, 'RANDOM', NOW(), NOW()),
('책', true, 'RANDOM', NOW(), NOW()),
('영화', true, 'RANDOM', NOW(), NOW()),
('반려동물', true, 'RANDOM', NOW(), NOW()),
('건강', true, 'RANDOM', NOW(), NOW()),
('일상', true, 'RANDOM', NOW(), NOW());

-- 보호자용 카테고리 (GUARDIAN)
INSERT INTO categories (name, is_active, category_type, created_at, updated_at) VALUES
('안부 확인', true, 'GUARDIAN', NOW(), NOW()),
('건강 상담', true, 'GUARDIAN', NOW(), NOW()),
('생활 지원', true, 'GUARDIAN', NOW(), NOW());