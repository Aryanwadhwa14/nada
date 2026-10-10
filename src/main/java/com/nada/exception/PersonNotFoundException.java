package com.nada.exception;

public class PersonNotFoundException extends RuntimeException {
    private final long personId;

    public PersonNotFoundException(long personId) {
        super("no person " + personId);
        this.personId = personId;
    }

    public long getPersonId() {
        return personId;
    }
}
