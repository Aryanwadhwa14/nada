package com.nada.repository;

import com.nada.model.Person;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryGraphRepository implements GraphRepository {

    private final ConcurrentMap<Long, Person> people = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, Set<Long>> adjacencyList = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong(0);

    @Override
    public Person savePerson(String name) {
        long id = idSequence.incrementAndGet();
        Person person = new Person(id, name);
        people.put(id, person);
        adjacencyList.putIfAbsent(id, ConcurrentHashMap.newKeySet());
        return person;
    }

    @Override
    public Optional<Person> findPersonById(long id) {
        return Optional.ofNullable(people.get(id));
    }

    @Override
    public boolean existsPerson(long id) {
        return people.containsKey(id);
    }

    @Override
    public void addMutualConnection(long a, long b) {
        adjacencyList.computeIfAbsent(a, k -> ConcurrentHashMap.newKeySet()).add(b);
        adjacencyList.computeIfAbsent(b, k -> ConcurrentHashMap.newKeySet()).add(a);
    }

    @Override
    public Set<Long> getDirectContactIds(long id) {
        Set<Long> contacts = adjacencyList.get(id);
        if (contacts == null) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(contacts);
    }

    @Override
    public List<Person> getDirectContacts(long id) {
        Set<Long> contactIds = adjacencyList.get(id);
        if (contactIds == null || contactIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Person> result = new ArrayList<>();
        for (Long contactId : contactIds) {
            Person person = people.get(contactId);
            if (person != null) {
                result.add(person);
            }
        }
        // Consistent ordering by ID for deterministic responses
        result.sort((p1, p2) -> Long.compare(p1.id(), p2.id()));
        return result;
    }

    @Override
    public void clear() {
        people.clear();
        adjacencyList.clear();
        idSequence.set(0);
    }
}
