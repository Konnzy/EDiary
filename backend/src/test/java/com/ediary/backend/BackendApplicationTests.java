package com.ediary.backend;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class BackendApplicationTests {

    @Test
    void applicationEntryPointIsAvailable() {
        assertDoesNotThrow(() -> Class.forName(BackendApplication.class.getName()));
    }

}
