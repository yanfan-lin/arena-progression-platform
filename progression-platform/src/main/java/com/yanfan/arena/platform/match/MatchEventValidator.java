package com.yanfan.arena.platform.match;

import com.yanfan.arena.contract.ArenaMatchCompleted;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

// Validate a match even before any business rules run
@Component
public class MatchEventValidator {

    private final Validator validator;

    @Autowired
    public MatchEventValidator(Validator validator) {
        this.validator = validator;
    }


    public void validate(ArenaMatchCompleted event) {
        if (event == null) {
            throw new MatchEventValidationException("Match event is missing");
        }

        if (event.contractVersion() == null
                || event.contractVersion() != ArenaMatchCompleted.CONTRACT_VERSION) {
            throw new MatchEventValidationException("Unsupported contract version");
        }

        Set<ConstraintViolation<ArenaMatchCompleted>> violations = validator.validate(event);

        // For every violation, convert it into a string containing its property path and error message
        if (!violations.isEmpty()) {
            String details = violations.stream()
                    .map(v ->
                            v.getPropertyPath() + " " + v.getMessage())
                    .collect(Collectors.joining("; "));

            throw new MatchEventValidationException("Match event is invalid: " + details);
        }


    }


}
