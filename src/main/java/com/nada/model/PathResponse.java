package com.nada.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PathResponse(
        @JsonProperty("connected") boolean connected,
        @JsonProperty("hops") Integer hops,
        @JsonProperty("path") List<Person> path
) {
    public static PathResponse disconnected() {
        return new PathResponse(false, null, null);
    }

    public static PathResponse connected(int hops, List<Person> path) {
        return new PathResponse(true, hops, path);
    }
}
