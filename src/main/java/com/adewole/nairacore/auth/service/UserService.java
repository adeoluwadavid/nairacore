package com.adewole.nairacore.auth.service;

import com.adewole.nairacore.auth.repository.UserRepository;
import com.adewole.nairacore.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public void validateUserExists(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException(
                    "Customer with id " + userId + " does not exist"
            );
        }
    }
}