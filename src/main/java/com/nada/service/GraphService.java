package com.nada.service;

import com.nada.exception.PersonNotFoundException;
import com.nada.model.PathResponse;
import com.nada.model.Person;
import com.nada.repository.GraphRepository;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public class GraphService {

    public static final int MAX_HOPS_LIMIT = 3;

    private final GraphRepository repository;

    public GraphService(GraphRepository repository) {
        this.repository = repository;
    }

    public Person createPerson(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("name must not be empty or blank");
        }
        return repository.savePerson(name);
    }

    public void createConnection(Long a, Long b) {
        if (a == null || b == null) {
            throw new IllegalArgumentException("both 'a' and 'b' are required");
        }
        if (a.equals(b)) {
            throw new IllegalArgumentException("cannot connect a person to themselves");
        }
        if (!repository.existsPerson(a)) {
            throw new PersonNotFoundException(a);
        }
        if (!repository.existsPerson(b)) {
            throw new PersonNotFoundException(b);
        }

        repository.addMutualConnection(a, b);
    }

    public List<Person> getDirectContacts(long id) {
        if (!repository.existsPerson(id)) {
            throw new PersonNotFoundException(id);
        }
        return repository.getDirectContacts(id);
    }

    public PathResponse findShortestPath(long fromId, long toId) {
        if (!repository.existsPerson(fromId)) {
            throw new PersonNotFoundException(fromId);
        }
        if (!repository.existsPerson(toId)) {
            throw new PersonNotFoundException(toId);
        }

        // Edge case: same start and end
        if (fromId == toId) {
            Person person = repository.findPersonById(fromId).orElseThrow();
            return PathResponse.connected(0, List.of(person));
        }

        // Bounded Breadth-First Search (BFS) up to MAX_HOPS_LIMIT (3 hops)
        Queue<Long> queue = new ArrayDeque<>();
        Map<Long, Long> parentMap = new HashMap<>();
        Map<Long, Integer> depthMap = new HashMap<>();

        queue.add(fromId);
        depthMap.put(fromId, 0);
        parentMap.put(fromId, null);

        boolean reachedTarget = false;

        while (!queue.isEmpty()) {
            long current = queue.poll();
            int currentDepth = depthMap.get(current);

            // Do not expand neighbors if already at max allowable hops
            if (currentDepth >= MAX_HOPS_LIMIT) {
                continue;
            }

            Set<Long> neighbors = repository.getDirectContactIds(current);
            for (Long neighbor : neighbors) {
                if (!depthMap.containsKey(neighbor)) {
                    depthMap.put(neighbor, currentDepth + 1);
                    parentMap.put(neighbor, current);

                    if (neighbor == toId) {
                        reachedTarget = true;
                        break;
                    }

                    queue.add(neighbor);
                }
            }

            if (reachedTarget) {
                break;
            }
        }

        if (!reachedTarget) {
            return PathResponse.disconnected();
        }

        // Reconstruct path
        List<Person> path = new ArrayList<>();
        Long curr = toId;
        while (curr != null) {
            path.add(repository.findPersonById(curr).orElseThrow());
            curr = parentMap.get(curr);
        }
        Collections.reverse(path);

        int totalHops = depthMap.get(toId);
        return PathResponse.connected(totalHops, path);
    }
}
