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
         SELECT 1 as category_id, '최근에 재미있게 본 영화나 드라마가 있나요?' as question, 1 as difficulty, true as is_active, 1 as display_order, NOW() as created_at, NOW() as updated_at
         UNION ALL SELECT 1, '좋아하는 음악 장르는 무엇인가요?', 1, true, 2, NOW(), NOW()
         UNION ALL SELECT 1, '가장 기억에 남는 여행지가 어디인가요?', 2, true, 3, NOW(), NOW()
         UNION ALL SELECT 1, '취미로 하고 싶었지만 아직 시작하지 못한 것이 있나요?', 2, true, 4, NOW(), NOW()
         UNION ALL SELECT 1, '주말에 주로 어떤 활동을 하시나요?', 1, true, 5, NOW(), NOW()
         UNION ALL SELECT 1, '최근에 새로 시작한 취미가 있나요?', 2, true, 6, NOW(), NOW()

         -- 카테고리 2: 자녀
         UNION ALL SELECT 2, '오늘의 기분은 어떤가요?', 1, true, 1, NOW(), NOW()
         UNION ALL SELECT 2, '주말에는 주로 뭐하며 시간을 보내시나요?', 1, true, 2, NOW(), NOW()
         UNION ALL SELECT 2, '좋아하는 음식은 무엇인가요?', 1, true, 3, NOW(), NOW()
         UNION ALL SELECT 2, '스트레스를 풀 때 주로 뭐하시나요?', 2, true, 4, NOW(), NOW()
         UNION ALL SELECT 2, '좋아하는 계절과 그 이유는?', 1, true, 5, NOW(), NOW()
         UNION ALL SELECT 2, '최근에 가장 기억에 남는 일은?', 2, true, 6, NOW(), NOW()

         -- 카테고리 3: 배우자
         UNION ALL SELECT 3, '가장 행복했던 순간은 언제였나요?', 3, true, 1, NOW(), NOW()
         UNION ALL SELECT 3, '10년 후의 나는 어떤 모습일 것 같나요?', 3, true, 2, NOW(), NOW()
         UNION ALL SELECT 3, '인생에서 가장 중요하게 생각하는 가치는 무엇인가요?', 3, true, 3, NOW(), NOW()
         UNION ALL SELECT 3, '요즘 가장 고민하고 있는 것이 있나요?', 3, true, 4, NOW(), NOW()
         UNION ALL SELECT 3, '가장 감동받았던 경험은?', 2, true, 5, NOW(), NOW()

         -- 카테고리 4: 추억
         UNION ALL SELECT 4, '요즘 가장 관심 있는 주제가 뭔가요?', 2, true, 1, NOW(), NOW()
         UNION ALL SELECT 4, '어렸을 때 꿈꿨던 직업이 있나요?', 2, true, 2, NOW(), NOW()
         UNION ALL SELECT 4, '최근에 읽은 책이나 기사 중 기억에 남는 게 있나요?', 2, true, 3, NOW(), NOW()
         UNION ALL SELECT 4, '친구들과 만나면 주로 무엇을 하나요?', 1, true, 4, NOW(), NOW()
         UNION ALL SELECT 4, '반려동물을 키우시나요? 키운다면 어떤 동물인가요?', 1, true, 5, NOW(), NOW()

         -- 카테고리 5: 스트레스
         UNION ALL SELECT 5, '요즘 가장 자주 먹는 음식은?', 1, true, 1, NOW(), NOW()
         UNION ALL SELECT 5, '좋아하는 요리나 음식 스타일은?', 1, true, 2, NOW(), NOW()
         UNION ALL SELECT 5, '최근에 가본 맛집이 있나요?', 2, true, 3, NOW(), NOW()
         UNION ALL SELECT 5, '직접 요리를 자주 하시나요?', 1, true, 4, NOW(), NOW()

         -- 카테고리 6: 여행
         UNION ALL SELECT 6, '평소에 운동을 하시나요?', 1, true, 1, NOW(), NOW()
         UNION ALL SELECT 6, '건강을 위해 실천하고 있는 습관이 있나요?', 2, true, 2, NOW(), NOW()
         UNION ALL SELECT 6, '최근에 도전해보고 싶은 운동이 있나요?', 2, true, 3, NOW(), NOW()

         -- 카테고리 7: 운동
         UNION ALL SELECT 7, '요즘 배우고 있는 것이 있나요?', 2, true, 1, NOW(), NOW()
         UNION ALL SELECT 7, '앞으로 공부하고 싶은 분야가 있나요?', 2, true, 2, NOW(), NOW()
         UNION ALL SELECT 7, '최근에 새롭게 알게 된 흥미로운 지식이 있나요?', 2, true, 3, NOW(), NOW()

         -- 카테고리 8: 책
         UNION ALL SELECT 8, '반려동물의 이름과 나이는 어떻게 되나요?', 1, true, 1, NOW(), NOW()
         UNION ALL SELECT 8, '반려동물과 함께한 가장 재미있던 순간은?', 2, true, 2, NOW(), NOW()
         UNION ALL SELECT 8, '반려동물을 키우면서 가장 힘든 점은?', 2, true, 3, NOW(), NOW()

         -- 더 많은 카테고리별 질문을 추가할 수 있습니다
     ) AS new_prompts
WHERE NOT EXISTS (
    SELECT 1 FROM conversation_prompts
    WHERE conversation_prompts.category_id = new_prompts.category_id
      AND conversation_prompts.question = new_prompts.question
);