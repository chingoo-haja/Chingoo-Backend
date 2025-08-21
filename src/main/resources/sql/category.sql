-- src/main/resources/sql/category.sql
-- 중복 실행 방지 로직 추가 (중요!)

-- 일반 매칭용 카테고리 (RANDOM)
INSERT INTO categories (name, is_active, category_type, created_at, updated_at)
SELECT name, is_active, category_type, created_at, updated_at
FROM (
         SELECT '취미' as name, true as is_active, 'RANDOM' as category_type, NOW() as created_at, NOW() as updated_at
         UNION ALL SELECT '자녀', true, 'RANDOM', NOW(), NOW()
         UNION ALL SELECT '요리', true, 'RANDOM', NOW(), NOW()
         UNION ALL SELECT '추억', true, 'RANDOM', NOW(), NOW()
         UNION ALL SELECT '음악', true, 'RANDOM', NOW(), NOW()
         UNION ALL SELECT '여행', true, 'RANDOM', NOW(), NOW()
         UNION ALL SELECT '운동', true, 'RANDOM', NOW(), NOW()
         UNION ALL SELECT '책', true, 'RANDOM', NOW(), NOW()
         UNION ALL SELECT '영화', true, 'RANDOM', NOW(), NOW()
         UNION ALL SELECT '반려동물', true, 'RANDOM', NOW(), NOW()
         UNION ALL SELECT '건강', true, 'RANDOM', NOW(), NOW()
         UNION ALL SELECT '일상', true, 'RANDOM', NOW(), NOW()
     ) AS new_categories
WHERE NOT EXISTS (
    SELECT 1 FROM categories
    WHERE categories.name = new_categories.name
      AND categories.category_type = new_categories.category_type
);

-- 보호자용 카테고리 (GUARDIAN)
INSERT INTO categories (name, is_active, category_type, created_at, updated_at)
SELECT name, is_active, category_type, created_at, updated_at
FROM (
         SELECT '안부 확인' as name, true as is_active, 'GUARDIAN' as category_type, NOW() as created_at, NOW() as updated_at
         UNION ALL SELECT '건강 상담', true, 'GUARDIAN', NOW(), NOW()
         UNION ALL SELECT '생활 지원', true, 'GUARDIAN', NOW(), NOW()
     ) AS new_categories
WHERE NOT EXISTS (
    SELECT 1 FROM categories
    WHERE categories.name = new_categories.name
      AND categories.category_type = new_categories.category_type
);