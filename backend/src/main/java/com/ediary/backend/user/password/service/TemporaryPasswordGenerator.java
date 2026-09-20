package com.ediary.backend.user.password.service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TemporaryPasswordGenerator {

    private static final String UPPERCASE = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWERCASE = "abcdefghjkmnpqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SYMBOLS = "!@#$%_-";
    private static final int PASSWORD_LENGTH = 16;

    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        List<Character> characters = new ArrayList<>(PASSWORD_LENGTH);
        characters.add(randomCharacter(UPPERCASE));
        characters.add(randomCharacter(LOWERCASE));
        characters.add(randomCharacter(DIGITS));
        characters.add(randomCharacter(SYMBOLS));

        String allCharacters = UPPERCASE + LOWERCASE + DIGITS + SYMBOLS;
        while (characters.size() < PASSWORD_LENGTH) {
            characters.add(randomCharacter(allCharacters));
        }
        Collections.shuffle(characters, secureRandom);

        StringBuilder password = new StringBuilder(PASSWORD_LENGTH);
        characters.forEach(password::append);
        return password.toString();
    }

    private char randomCharacter(String alphabet) {
        return alphabet.charAt(secureRandom.nextInt(alphabet.length()));
    }
}
