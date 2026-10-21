package com.example.ecowatchadmin1;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.example.ecowatchadmin1.utils.AdminAccountDateFormatter;

import org.junit.Test;

public class AdminAccountDateFormatterTest {

    @Test
    public void formatsPostgresIsoTimestamp() {
        assertNotNull(AdminAccountDateFormatter.format("2026-09-25T12:30:00.123456Z"));
    }

    @Test
    public void doesNotInventMissingServerTimestamp() {
        assertNull(AdminAccountDateFormatter.format(null));
        assertNull(AdminAccountDateFormatter.format("not-a-date"));
    }
}
