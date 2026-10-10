package com.nada.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KnowsRequest(
        @JsonProperty("a") Long a,
        @JsonProperty("b") Long b
) {
}
