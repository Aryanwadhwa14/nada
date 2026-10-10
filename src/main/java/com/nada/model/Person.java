package com.nada.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Person(
        @JsonProperty("id") long id,
        @JsonProperty("name") String name
) {
}
