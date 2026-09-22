package com.placefy.infrastructure.system;

import com.placefy.application.port.out.IdGenerator;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class RandomUuidGenerator implements IdGenerator {

    @Override
    public UUID newId() {
        return UUID.randomUUID();
    }
}
