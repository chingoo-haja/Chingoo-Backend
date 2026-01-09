-- src/main/resources/sql/category.sql
-- 중복 실행 방지 로직 추가 (중요!)

-- 일반 매칭용 카테고리 (RANDOM)
INSERT INTO categories (name, is_active, category_type, created_at, updated_at)
SELECT name, is_active, category_type, created_at, updated_at
FROM (
         SELECT '취미' as name, true as is_active, 'RANDOM' as category_type, NOW() as created_at, NOW() as updated_at
         UNION ALL SELECT '자녀', true, 'RANDOM', NOW(), NOW()
         UNION ALL SELECT '배우자', true, 'RANDOM', NOW(), NOW()
         UNION ALL SELECT '추억', true, 'RANDOM', NOW(), NOW()
         UNION ALL SELECT '스트레스', true, 'RANDOM', NOW(), NOW()
         UNION ALL SELECT '여행', true, 'RANDOM', NOW(), NOW()
         UNION ALL SELECT '운동', true, 'RANDOM', NOW(), NOW()
         UNION ALL SELECT '책', true, 'RANDOM', NOW(), NOW()
         UNION ALL SELECT '영화', true, 'RANDOM', NOW(), NOW()
         UNION ALL SELECT '반려동물', true, 'RANDOM', NOW(), NOW()
         UNION ALL SELECT '건강', true, 'RANDOM', NOW(), NOW()
         UNION ALL SELECT '일상', true, 'RANDOM', NOW(), NOW()
         UNION ALL SELECT '음악', true, 'RANDOM', NOW(), NOW()
         UNION ALL SELECT '요리', true, 'RANDOM', NOW(), NOW()
         UNION ALL SELECT '주식', true, 'RANDOM', NOW(), NOW()

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

-- 서비스 운영 시간 설정
INSERT INTO service_settings (setting_key, setting_value, description, created_at, updated_at)
SELECT setting_key, setting_value, description, created_at, updated_at
FROM (
         SELECT 'call_service_enabled' as setting_key, 'true' as setting_value, '통화 서비스 활성화 여부' as description, NOW() as created_at, NOW() as updated_at
         UNION ALL SELECT 'call_start_time', '09:00', '통화 서비스 시작 시간', NOW(), NOW()
         UNION ALL SELECT 'call_end_time', '23:00', '통화 서비스 종료 시간', NOW(), NOW()
     ) AS new_settings
WHERE NOT EXISTS (
    SELECT 1 FROM service_settings
    WHERE service_settings.setting_key = new_settings.setting_key
);

-- 대화 질문 데이터 (conversation_prompts)
-- 카테고리별로 통화 중 대화를 돕는 질문을 제공
-- difficulty: 1(쉬움), 2(보통), 3(깊은 질문)

INSERT INTO conversation_prompts (category_id, question, difficulty, is_active, display_order, created_at, updated_at)
SELECT category_id, question, difficulty, is_active, display_order, created_at, updated_at
FROM (
         -- 카테고리 1: 취미 (예시 - 실제 category_id에 맞게 조정 필요)
         SELECT 1 as category_id, '요즘 가장 관심있는 취미가 무엇인가요?' as question, 1 as difficulty, true as is_active, 1 as display_order, NOW() as created_at, NOW() as updated_at
         UNION ALL SELECT 1, '지금 좋아하는 취미는 어떻게 시작하게됐나요?', 1, true, 2, NOW(), NOW()
         UNION ALL SELECT 1, '같이 취미를 즐기는 사람이있나요?', 2, true, 3, NOW(), NOW()
         UNION ALL SELECT 1, '주에 몇회씩 취미를 즐기시나요?', 2, true, 4, NOW(), NOW()
         UNION ALL SELECT 1, '취미에 시간을 많이 쓰는 편인가요?', 1, true, 5, NOW(), NOW()

         -- 카테고리 2: 자녀
         UNION ALL SELECT 2, '자녀와 가장 행복했던 순간은 언제인가요?', 1, true, 1, NOW(), NOW()
         UNION ALL SELECT 2, '자녀에게 미안한게 있다면?', 1, true, 2, NOW(), NOW()
         UNION ALL SELECT 2, '자녀의 친구들은 어떤가요?', 1, true, 3, NOW(), NOW()
         UNION ALL SELECT 2, '자녀와 같은 나이였다면, 어떤 얘기를 해주고싶나요?', 2, true, 4, NOW(), NOW()
         UNION ALL SELECT 2, '최근 자녀와 같이 나는 얘기가 뭔가요?', 1, true, 5, NOW(), NOW()
         UNION ALL SELECT 2, '최근 자녀와 함께한 순간 중 가장 기억에 남는 일은 어떤 것인가요?', 2, true, 6, NOW(), NOW()

         -- 카테고리 3: 배우자
         UNION ALL SELECT 3, '배우자와 함께한지 얼마나됐나요?', 3, true, 1, NOW(), NOW()
         UNION ALL SELECT 3, '배우자와 성격이 비슷한가요?', 3, true, 2, NOW(), NOW()
         UNION ALL SELECT 3, '배우자를 어떻게 처음 만났나요?', 3, true, 3, NOW(), NOW()
         UNION ALL SELECT 3, '가장 최근 배우자와 데이트를한게 언제인가요?', 3, true, 4, NOW(), NOW()
         UNION ALL SELECT 3, '배우자와 놀러가고싶은 장소는 어디인가요?', 2, true, 5, NOW(), NOW()

         -- 카테고리 4: 추억
         UNION ALL SELECT 4, '나의 가장 빛났던 순간은 언제인가요?', 2, true, 1, NOW(), NOW()
         UNION ALL SELECT 4, '어린 시절 가장 많이 먹었던 간식은 어떤 것인가요?', 2, true, 2, NOW(), NOW()
         UNION ALL SELECT 4, '동네 친구들과 가장 많이 했던 놀이들이 어떤 것인가요?', 2, true, 3, NOW(), NOW()
         UNION ALL SELECT 4, '나의 20대는 어땠나요?', 1, true, 4, NOW(), NOW()
         UNION ALL SELECT 4, '가장 기억에 남는 장소는 어디인가요?', 1, true, 5, NOW(), NOW()

         -- 카테고리 5: 스트레스
         UNION ALL SELECT 5, '최근 들은 가장 스트레스되는 말은 어떤 것인가요?', 1, true, 1, NOW(), NOW()
         UNION ALL SELECT 5, '스트레스를 어떻게 해소하나요?', 1, true, 2, NOW(), NOW()
         UNION ALL SELECT 5, '최근 가장 스트레스를 주는 사람은 누구인가요?', 2, true, 3, NOW(), NOW()
         UNION ALL SELECT 5, '스트레스를 자주 받는 편인가요?', 1, true, 4, NOW(), NOW()
         UNION ALL SELECT 5, '최근 가장 스트레스를 받았던 일은 무엇인가요?', 1, true, 4, NOW(), NOW()

         -- 카테고리 7: 운동
         UNION ALL SELECT 7, '평소에 운동을 하시나요?', 1, true, 1, NOW(), NOW()
         UNION ALL SELECT 7, '건강을 위해 실천하고 있는 습관이 있나요?', 2, true, 2, NOW(), NOW()
         UNION ALL SELECT 7, '최근에 도전해보고 싶은 운동이 있나요?', 2, true, 3, NOW(), NOW()

         -- 카테고리 15: 주식
         UNION ALL SELECT 15, '최근 관심있는 종목은 어떤 것인가요?', 2, true, 1, NOW(), NOW()
         UNION ALL SELECT 15, '국내와 해외 중 어떤 주식을 많이 하나요?', 2, true, 2, NOW(), NOW()
         UNION ALL SELECT 15, '단기 투자와 장기 투자 중 선호하는 방법은 뭔가요?', 2, true, 3, NOW(), NOW()
         UNION ALL SELECT 15, '최근 관심있는 ETF는 어떤 것인가요?', 1, true, 1, NOW(), NOW()
         UNION ALL SELECT 15, '지금 코스피 상황을 어떻게 생각하시나요?', 2, true, 2, NOW(), NOW()

         -- 더 많은 카테고리별 질문을 추가할 수 있습니다
     ) AS new_prompts
WHERE NOT EXISTS (
    SELECT 1 FROM conversation_prompts
    WHERE conversation_prompts.category_id = new_prompts.category_id
      AND conversation_prompts.question = new_prompts.question
);