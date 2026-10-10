package com.nada.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreatePersonRequest(
        @JsonProperty("name") String name
) {
}
