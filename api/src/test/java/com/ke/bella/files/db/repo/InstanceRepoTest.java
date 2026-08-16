package com.ke.bella.files.db.repo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.Locale;

import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.Test;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

public class InstanceRepoTest {

    @Test
    public void registerUsesReadCommittedIsolation() throws NoSuchMethodException {
        Method register = InstanceRepo.class.getMethod("register", String.class, int.class);
        Transactional transactional = register.getAnnotation(Transactional.class);

        assertNotNull(transactional);
        assertEquals(Isolation.READ_COMMITTED, transactional.isolation());
    }

    @Test
    public void idleInstanceQueryUsesMySql57CompatibleUpdateLock() {
        String sql = InstanceRepo.idleInstanceQuery(DSL.using(SQLDialect.MYSQL))
                .getSQL()
                .toLowerCase(Locale.ROOT);

        assertTrue(sql.contains("for update"));
        assertFalse(sql.contains("skip locked"));
    }
}
