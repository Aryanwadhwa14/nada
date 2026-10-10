package com.nada.repository;

import com.nada.model.Person;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface GraphRepository {
    Person savePerson(String name);

    Optional<Person> findPersonById(long id);

    boolean existsPerson(long id);

    void addMutualConnection(long a, long b);

    Set<Long> getDirectContactIds(long id);

    List<Person> getDirectContacts(long id);

    void clear();
}
