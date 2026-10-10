package com.nada.service;

import com.nada.exception.PersonNotFoundException;
import com.nada.model.PathResponse;
import com.nada.model.Person;
import com.nada.repository.InMemoryGraphRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GraphServiceTest {

    private InMemoryGraphRepository repository;
    private GraphService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryGraphRepository();
        service = new GraphService(repository);
    }

    @Test
    @DisplayName("Should create person and assign sequential IDs")
    void testCreatePerson() {
        Person p1 = service.createPerson("Asha");
        Person p2 = service.createPerson("Rohan");

        assertThat(p1.id()).isEqualTo(1L);
        assertThat(p1.name()).isEqualTo("Asha");
        assertThat(p2.id()).isEqualTo(2L);
        assertThat(p2.name()).isEqualTo("Rohan");
    }

    @Test
    @DisplayName("Should reject blank or null names")
    void testCreatePersonValidation() {
        assertThatThrownBy(() -> service.createPerson(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createPerson(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createPerson("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should create bidirectional mutual connection")
    void testCreateConnection() {
        Person a = service.createPerson("Asha");
        Person b = service.createPerson("Rohan");

        service.createConnection(a.id(), b.id());

        List<Person> aContacts = service.getDirectContacts(a.id());
        List<Person> bContacts = service.getDirectContacts(b.id());

        assertThat(aContacts).extracting(Person::id).containsExactly(b.id());
        assertThat(bContacts).extracting(Person::id).containsExactly(a.id());
    }

    @Test
    @DisplayName("Should reject connecting a person to themselves")
    void testSelfConnectionRejected() {
        Person a = service.createPerson("Asha");

        assertThatThrownBy(() -> service.createConnection(a.id(), a.id()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot connect a person to themselves");
    }

    @Test
    @DisplayName("Should throw 404 PersonNotFoundException if either person does not exist")
    void testUnknownPersonInConnection() {
        Person a = service.createPerson("Asha");

        assertThatThrownBy(() -> service.createConnection(a.id(), 999L))
                .isInstanceOf(PersonNotFoundException.class)
                .hasMessage("no person 999");

        assertThatThrownBy(() -> service.createConnection(999L, a.id()))
                .isInstanceOf(PersonNotFoundException.class)
                .hasMessage("no person 999");
    }

    @Test
    @DisplayName("Mandatory Requirement: A-B-C-D is connected (3 hops), A-B-C-D-E is not connected (4 hops)")
    void testMandatoryThreeHopBoundary() {
        Person a = service.createPerson("A");
        Person b = service.createPerson("B");
        Person c = service.createPerson("C");
        Person d = service.createPerson("D");
        Person e = service.createPerson("E");

        service.createConnection(a.id(), b.id());
        service.createConnection(b.id(), c.id());
        service.createConnection(c.id(), d.id());
        service.createConnection(d.id(), e.id());

        // A to D: exactly 3 hops -> connected: true, hops: 3
        PathResponse pathAtoD = service.findShortestPath(a.id(), d.id());
        assertThat(pathAtoD.connected()).isTrue();
        assertThat(pathAtoD.hops()).isEqualTo(3);
        assertThat(pathAtoD.path()).extracting(Person::name).containsExactly("A", "B", "C", "D");

        // A to E: 4 hops -> exceeds limit -> connected: false
        PathResponse pathAtoE = service.findShortestPath(a.id(), e.id());
        assertThat(pathAtoE.connected()).isFalse();
        assertThat(pathAtoE.hops()).isNull();
        assertThat(pathAtoE.path()).isNull();
    }

    @Test
    @DisplayName("Same start and end should return hops: 0 with path containing only that person")
    void testSameStartAndEnd() {
        Person a = service.createPerson("Asha");

        PathResponse response = service.findShortestPath(a.id(), a.id());
        assertThat(response.connected()).isTrue();
        assertThat(response.hops()).isEqualTo(0);
        assertThat(response.path()).extracting(Person::name).containsExactly("Asha");
    }

    @Test
    @DisplayName("Should return the shortest path when multiple paths exist")
    void testShortestPathSelection() {
        // A - B - C - D (3 hops)
        // A - X - D     (2 hops)
        Person a = service.createPerson("A");
        Person b = service.createPerson("B");
        Person c = service.createPerson("C");
        Person d = service.createPerson("D");
        Person x = service.createPerson("X");

        service.createConnection(a.id(), b.id());
        service.createConnection(b.id(), c.id());
        service.createConnection(c.id(), d.id());

        service.createConnection(a.id(), x.id());
        service.createConnection(x.id(), d.id());

        PathResponse response = service.findShortestPath(a.id(), d.id());
        assertThat(response.connected()).isTrue();
        assertThat(response.hops()).isEqualTo(2);
        assertThat(response.path()).extracting(Person::name).containsExactly("A", "X", "D");
    }

    @Test
    @DisplayName("Should return connected: false when nodes belong to disconnected components")
    void testDisconnectedGraph() {
        Person a = service.createPerson("A");
        Person b = service.createPerson("B");

        PathResponse response = service.findShortestPath(a.id(), b.id());
        assertThat(response.connected()).isFalse();
    }
}
