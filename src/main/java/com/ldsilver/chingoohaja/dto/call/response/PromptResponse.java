package com.ldsilver.chingoohaja.dto.call.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ldsilver.chingoohaja.domain.call.ConversationPrompt;

public record PromptResponse(
        @JsonProperty("prompt_id") Long promptId,
        @JsonProperty("question") String question,
        @JsonProperty("category_id") Long categoryId,
        @JsonProperty("category_name") String categoryName,
        @JsonProperty("difficulty") Integer difficulty
) {
    public static PromptResponse from(ConversationPrompt prompt) {
        return new PromptResponse(
                prompt.getId(),
                prompt.getQuestion(),
                prompt.getCategory().getId(),
                prompt.getCategory().getName(),
                prompt.getDifficulty()
        );
    }
}