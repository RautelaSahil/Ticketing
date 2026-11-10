package com.ticketing.config;

import com.ticketing.model.Room;
import com.ticketing.model.User;
import com.ticketing.repository.RoomRepository;
import com.ticketing.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Starting Phase 1 database initialization and index verification...");
        seedRoomsIfEmpty();
        seedUsersIfEmpty();
        verifyIndexes();
        log.info("Phase 1 initialization complete.");
    }

    private void seedRoomsIfEmpty() {
        if (roomRepository.count() == 0) {
            log.info("Seeding initial room catalog...");
            List<Room> initialRooms = List.of(
                    Room.builder()
                            .roomNumber("101")
                            .capacity(4)
                            .metadata(Map.of("floor", 1, "hasProjector", true))
                            .active(true)
                            .build(),
                    Room.builder()
                            .roomNumber("102")
                            .capacity(6)
                            .metadata(Map.of("floor", 1, "hasProjector", false))
                            .active(true)
                            .build(),
                    Room.builder()
                            .roomNumber("103")
                            .capacity(2)
                            .metadata(Map.of("floor", 1, "hasProjector", false))
                            .active(true)
                            .build(),
                    Room.builder()
                            .roomNumber("201")
                            .capacity(8)
                            .metadata(Map.of("floor", 2, "hasProjector", true))
                            .active(true)
                            .build(),
                    Room.builder()
                            .roomNumber("202")
                            .capacity(4)
                            .metadata(Map.of("floor", 2, "hasProjector", false))
                            .active(true)
                            .build(),
                    Room.builder()
                            .roomNumber("203")
                            .capacity(10)
                            .metadata(Map.of("floor", 2, "hasProjector", true))
                            .active(true)
                            .build()
            );

            roomRepository.saveAll(initialRooms);
            log.info("Successfully seeded {} rooms.", initialRooms.size());
        } else {
            log.info("Rooms collection already seeded (count: {}).", roomRepository.count());
        }
    }

    private void seedUsersIfEmpty() {
        if (userRepository.count() == 0) {
            log.info("Seeding default test users (alice, bob)...");
            String defaultPassword = "Password123!";
            String encodedPassword = passwordEncoder.encode(defaultPassword);

            User alice = User.builder()
                    .username("alice")
                    .profileName("ALICE")
                    .passwordHash(encodedPassword)
                    .roles(Set.of("ROLE_USER"))
                    .createdAt(Instant.now())
                    .build();

            User bob = User.builder()
                    .username("bob")
                    .profileName("BOB")
                    .passwordHash(encodedPassword)
                    .roles(Set.of("ROLE_USER"))
                    .createdAt(Instant.now())
                    .build();

            userRepository.saveAll(List.of(alice, bob));
            log.info("Successfully seeded test users 'alice' and 'bob' (password: {}).", defaultPassword);
        } else {
            log.info("Users collection already seeded (count: {}).", userRepository.count());
        }
    }

    private void verifyIndexes() {
        verifyCollectionIndexes("rooms");
        verifyCollectionIndexes("users");
        verifyCollectionIndexes("reservations");
        verifyCollectionIndexes("notifications");
    }

    private void verifyCollectionIndexes(String collectionName) {
        try {
            List<IndexInfo> indexes = mongoTemplate.indexOps(collectionName).getIndexInfo();
            log.info("Collection '{}' verified with {} active index(es):", collectionName, indexes.size());
            for (IndexInfo info : indexes) {
                log.info("  -> Index: name='{}', unique={}, partialFilter={}",
                        info.getName(),
                        info.isUnique(),
                        info.getPartialFilterExpression()
                );
            }
        } catch (Exception e) {
            log.warn("Could not inspect indexes for collection '{}': {}", collectionName, e.getMessage());
        }
    }
}

