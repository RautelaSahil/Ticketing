package com.ticketing.repository;

import com.ticketing.model.Room;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface RoomRepository extends MongoRepository<Room, String> {

    Optional<Room> findByRoomNumber(String roomNumber);

    boolean existsByRoomNumber(String roomNumber);

    // Room list for the next-interval grid, in stable order
    List<Room> findByActiveTrueOrderByRoomNumberAsc();
}