package com.ldsilver.chingoohaja.common.util;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

@Slf4j
@Component
public class NicknameGenerator {
    private final SecureRandom random = new SecureRandom();
    private List<String> adjectives = new ArrayList<>();
    private List<String> nouns = new ArrayList<>();

    private static final int MAX_RETRY_ATTEMPTS = 10;

    @PostConstruct
    public void init() {
        try {
            loadWordsFromResources();
        } catch (Exception e) {
            log.error("닉네임 생성기 초기화 실패", e);
            throw new CustomException(ErrorCode.NICKNAME_RESOURCE_LOAD_FAILED, e);
        }
    }


    public String generateUniqueNickname(Predicate<String> duplicateChecker) {
        validateWordsLoaded();

        for (int attempt = 0; attempt < MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                String baseNickname = generateBaseNickname();
                if (!duplicateChecker.test(baseNickname)) {
                    log.debug("닉네임 생성 성공: {} ({}번째 시도)", baseNickname, attempt + 1);
                    return baseNickname;
                }
            } catch (Exception e) {
                log.warn("기본 닉네임 생성 중 오류 발생 (시도 {})", attempt + 1, e);
            }
        }

        log.debug("깔끔한 닉네임 생성 실패. 숫자 추가 시도");

        try {
            String baseNickname = generateBaseNickname();
            return generateNicknameWithNumber(baseNickname, duplicateChecker);
        } catch (Exception e) {
            log.error("숫자 포함 닉네임 생성도 실패", e);
            throw new CustomException(ErrorCode.NICKNAME_GENERATION_FAILED, "모든 닉네임 생성 방법이 실패했습니다.");
        }
    }

    public String generateBaseNickname() {
        validateWordsLoaded();

        String adjective = getRandomAdjective();
        String noun = getRandomNoun();

        return adjective + noun;
    }


    public String generateNicknameWithNumber(String baseNickname, Predicate<String> duplicateChecker) {
        if (baseNickname == null || baseNickname.trim().isEmpty()) {
            baseNickname = generateBaseNickname();
        }

        String cleanBase = baseNickname.replaceAll("\\d+$", "");
        List<Integer> numbers = generateRandomNumbers();

        for (int number : numbers) {
            String candidateNickname = cleanBase + number;
            if (!duplicateChecker.test(candidateNickname)) {
                log.debug("숫자 추가 닉네임 생성 성공: {}", candidateNickname);
                return candidateNickname;
            }
        }
        log.warn("기본 숫자 조합이 모두 중복됨. 최후 수단 사용");
        long timestamp = System.currentTimeMillis() % 100000;
        String lastResortNickname = cleanBase + timestamp;

        if (!duplicateChecker.test(lastResortNickname)) {
            return lastResortNickname;
        }

        throw new CustomException(ErrorCode.NICKNAME_ALL_COMBINATIONS_EXHAUSTED);
    }

    public String generateRandomNickname() {
        String baseNickname = generateBaseNickname();
        int number = random.nextInt(9999) + 1;
        return baseNickname + number;
    }

    // ========== Private 메서드들 ==========

    private String getRandomAdjective() {
        if (adjectives.isEmpty()) {
            throw new CustomException(ErrorCode.NICKNAME_ADJECTIVES_EMPTY);
        }
        return adjectives.get(random.nextInt(adjectives.size()));
    }

    private String getRandomNoun() {
        if (nouns.isEmpty()) {
            throw new CustomException(ErrorCode.NICKNAME_NOUNS_EMPTY);
        }
        return nouns.get(random.nextInt(nouns.size()));
    }

    private List<Integer> generateRandomNumbers() {
        Set<Integer> uniqueNumbers = new HashSet<>();
        int maxAttempts = 200;
        int attempts = 0;

        while (uniqueNumbers.size() < 100 && attempts < maxAttempts) {
            int number = random.nextInt(9999) + 1;
            uniqueNumbers.add(number);
            attempts++;
        }

        return new ArrayList<>(uniqueNumbers);
    }

    private void loadWordsFromResources() {
        try {
            adjectives = loadWordsFromFile("nickname/adjectives.txt");
            nouns = loadWordsFromFile("nickname/nouns.txt");
        } catch (Exception e) {
            log.error("닉네임 단어 파일 로드 실패", e);
            try {
                loadFallbackWords();
                log.warn("폴백 단어 목록을 사용합니다.");
            } catch (Exception fallbackError) {
                throw new CustomException(ErrorCode.NICKNAME_RESOURCE_LOAD_FAILED, "폴백 단어 로드도 실패");
            }
        }
    }

    private List<String> loadWordsFromFile(String filePath) throws IOException {
        List<String> words = new ArrayList<>();
        ClassPathResource resource = new ClassPathResource(filePath);

        if (!resource.exists()) {
            throw new CustomException(ErrorCode.NICKNAME_RESOURCE_LOAD_FAILED, filePath + " 파일이 존재하지 않습니다.");
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    words.add(line);
                }
            }
        }

        if (words.isEmpty()) {
            throw new CustomException(ErrorCode.NICKNAME_RESOURCE_LOAD_FAILED, "파일에서 단어를 로드할 수 없습니다: " + filePath);
        }

        return words;
    }

    private void loadFallbackWords() {
        adjectives = List.of(
                "귀여운", "멋진", "행복한", "즐거운", "사랑스러운", "똑똑한", "친절한", "유쾌한",
                "상냥한", "따뜻한", "밝은", "활발한", "차분한", "우아한", "당당한", "씩씩한"
        );

        nouns = List.of(
                "별", "달", "해", "구름", "비", "바람", "햇살", "노을", "무지개", "안개",
                "눈", "서리", "이슬", "번개", "천둥", "소나기", "폭풍", "바다", "파도", "모래",
                "산", "강", "계곡", "호수", "섬", "들판", "초원", "우주", "행성", "별자리",
                "은하", "혜성", "유성", "하늘", "우박", "진눈깨비", "산호", "조개", "진주", "파편"

        );
    }

    private void validateWordsLoaded() {
        if (adjectives.isEmpty() || nouns.isEmpty()) {
            throw new CustomException(ErrorCode.NICKNAME_WORDS_NOT_LOADED);
        }
    }

}
