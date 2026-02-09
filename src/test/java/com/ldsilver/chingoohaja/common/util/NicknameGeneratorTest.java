package com.ldsilver.chingoohaja.common.util;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("NicknameGenerator 테스트")
class NicknameGeneratorTest {

    private NicknameGenerator nicknameGenerator;

    private static final List<String> TEST_ADJECTIVES = List.of("귀여운", "멋진", "행복한");
    private static final List<String> TEST_NOUNS = List.of("고양이", "강아지", "토끼");

    @BeforeEach
    void setUp() throws Exception {
        nicknameGenerator = new NicknameGenerator();
        setField("adjectives", TEST_ADJECTIVES);
        setField("nouns", TEST_NOUNS);
    }

    private void setField(String fieldName, Object value) throws Exception {
        Field field = NicknameGenerator.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(nicknameGenerator, value);
    }

    @Nested
    @DisplayName("generateBaseNickname")
    class GenerateBaseNickname {

        @Test
        @DisplayName("형용사와 명사를 조합한 닉네임을 생성한다")
        void givenWordsLoaded_whenGenerate_thenReturnsAdjectiveAndNounCombination() {
            // when
            String nickname = nicknameGenerator.generateBaseNickname();

            // then
            assertThat(nickname).isNotNull().isNotEmpty();
            boolean startsWithAdjective = TEST_ADJECTIVES.stream().anyMatch(nickname::startsWith);
            boolean endsWithNoun = TEST_NOUNS.stream().anyMatch(nickname::endsWith);
            assertThat(startsWithAdjective).isTrue();
            assertThat(endsWithNoun).isTrue();
        }

        @Test
        @DisplayName("단어가 로드되지 않았으면 CustomException을 던진다")
        void givenWordsNotLoaded_whenGenerate_thenThrowsCustomException() throws Exception {
            // given
            setField("adjectives", List.of());
            setField("nouns", List.of());

            // when & then
            assertThatThrownBy(() -> nicknameGenerator.generateBaseNickname())
                    .isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("generateUniqueNickname")
    class GenerateUniqueNickname {

        @Test
        @DisplayName("중복이 없으면 기본 닉네임을 반환한다")
        void givenNoDuplicate_whenGenerate_thenReturnsBaseNickname() {
            // given
            var duplicateChecker = (java.util.function.Predicate<String>) nickname -> false;

            // when
            String result = nicknameGenerator.generateUniqueNickname(duplicateChecker);

            // then
            assertThat(result).isNotNull().isNotEmpty();
            boolean startsWithAdjective = TEST_ADJECTIVES.stream().anyMatch(result::startsWith);
            assertThat(startsWithAdjective).isTrue();
        }

        @Test
        @DisplayName("기본 닉네임이 모두 중복이면 숫자가 추가된 닉네임을 반환한다")
        void givenAllBaseNicknamesDuplicated_whenGenerate_thenReturnsNicknameWithNumber() {
            // given
            Set<String> baseNicknames = new HashSet<>();
            for (String adj : TEST_ADJECTIVES) {
                for (String noun : TEST_NOUNS) {
                    baseNicknames.add(adj + noun);
                }
            }
            // 숫자 없는 기본 닉네임만 중복으로 판정
            var duplicateChecker = (java.util.function.Predicate<String>) baseNicknames::contains;

            // when
            String result = nicknameGenerator.generateUniqueNickname(duplicateChecker);

            // then
            assertThat(result).isNotNull();
            assertThat(result).matches(".*\\d+$"); // 숫자로 끝남
        }

        @Test
        @DisplayName("모든 조합이 소진되면 CustomException을 던진다")
        void givenAllCombinationsExhausted_whenGenerate_thenThrowsCustomException() {
            // given - 모든 닉네임을 중복으로 판정
            var duplicateChecker = (java.util.function.Predicate<String>) nickname -> true;

            // when & then
            assertThatThrownBy(() -> nicknameGenerator.generateUniqueNickname(duplicateChecker))
                    .isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("generateNicknameWithNumber")
    class GenerateNicknameWithNumber {

        @Test
        @DisplayName("기본 닉네임에 숫자를 추가하여 고유한 닉네임을 생성한다")
        void givenBaseNickname_whenGenerate_thenReturnsNicknameWithNumber() {
            // given
            String baseNickname = "귀여운고양이";
            var duplicateChecker = (java.util.function.Predicate<String>) nickname -> false;

            // when
            String result = nicknameGenerator.generateNicknameWithNumber(baseNickname, duplicateChecker);

            // then
            assertThat(result).startsWith("귀여운고양이");
            assertThat(result).matches("귀여운고양이\\d+");
        }

        @Test
        @DisplayName("기존 숫자가 있는 닉네임에서 숫자를 제거하고 새 숫자를 추가한다")
        void givenNicknameWithExistingNumber_whenGenerate_thenReplacesNumber() {
            // given
            String baseNickname = "귀여운고양이123";
            var duplicateChecker = (java.util.function.Predicate<String>) nickname -> false;

            // when
            String result = nicknameGenerator.generateNicknameWithNumber(baseNickname, duplicateChecker);

            // then
            assertThat(result).startsWith("귀여운고양이");
            assertThat(result).matches("귀여운고양이\\d+");
        }

        @Test
        @DisplayName("null 입력 시 새로운 기본 닉네임을 생성하여 숫자를 추가한다")
        void givenNullInput_whenGenerate_thenGeneratesNewBaseAndAddsNumber() {
            // given
            var duplicateChecker = (java.util.function.Predicate<String>) nickname -> false;

            // when
            String result = nicknameGenerator.generateNicknameWithNumber(null, duplicateChecker);

            // then
            assertThat(result).isNotNull();
            assertThat(result).matches(".*\\d+$");
        }

        @Test
        @DisplayName("빈 문자열 입력 시 새로운 기본 닉네임을 생성하여 숫자를 추가한다")
        void givenEmptyInput_whenGenerate_thenGeneratesNewBaseAndAddsNumber() {
            // given
            var duplicateChecker = (java.util.function.Predicate<String>) nickname -> false;

            // when
            String result = nicknameGenerator.generateNicknameWithNumber("  ", duplicateChecker);

            // then
            assertThat(result).isNotNull();
            assertThat(result).matches(".*\\d+$");
        }
    }

    @Nested
    @DisplayName("generateRandomNickname")
    class GenerateRandomNickname {

        @Test
        @DisplayName("형용사 + 명사 + 숫자 형태의 닉네임을 생성한다")
        void givenWordsLoaded_whenGenerate_thenReturnsNicknameWithNumber() {
            // when
            String nickname = nicknameGenerator.generateRandomNickname();

            // then
            assertThat(nickname).isNotNull();
            assertThat(nickname).matches(".*\\d+$");
        }

        @Test
        @DisplayName("매번 다른 닉네임을 생성한다")
        void whenGenerateMultipleTimes_thenReturnsDistinctNicknames() {
            // when
            Set<String> nicknames = new HashSet<>();
            for (int i = 0; i < 50; i++) {
                nicknames.add(nicknameGenerator.generateRandomNickname());
            }

            // then - 50개 생성 시 최소 2개 이상 다른 닉네임이 있어야 함
            assertThat(nicknames.size()).isGreaterThan(1);
        }
    }

    @Nested
    @DisplayName("init - 리소스 로드")
    class Init {

        @Test
        @DisplayName("리소스 파일에서 단어를 정상적으로 로드한다")
        void givenResourceFiles_whenInit_thenLoadsWordsSuccessfully() {
            // given
            NicknameGenerator freshGenerator = new NicknameGenerator();

            // when
            freshGenerator.init();

            // then
            String nickname = freshGenerator.generateBaseNickname();
            assertThat(nickname).isNotNull().isNotEmpty();
        }
    }
}
