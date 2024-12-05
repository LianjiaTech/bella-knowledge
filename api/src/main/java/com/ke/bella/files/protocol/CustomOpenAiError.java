package com.ke.bella.files.protocol;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.theokanning.openai.OpenAiError;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class CustomOpenAiError extends OpenAiError {
    @JsonIgnore
    private int code;
}
