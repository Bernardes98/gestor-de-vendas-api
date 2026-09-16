package com.gestordevendas.api.auth;

import com.gestordevendas.api.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class PasswordPolicy {
    public void validate(String password) {
        boolean strongEnough = password != null
            && password.length() >= 8
            && password.chars().anyMatch(Character::isLetter)
            && password.chars().anyMatch(Character::isDigit);
        if (!strongEnough) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WEAK_PASSWORD",
                "A senha deve ter ao menos 8 caracteres, uma letra e um número.");
        }
    }
}
