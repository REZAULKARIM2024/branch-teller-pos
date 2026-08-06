package com.branchteller.docker;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * "Install" category checks for the Docker Compose deployment path: MySQL's
 * docker-entrypoint-initdb.d mechanism runs every *.sql file in docker/initdb in
 * filename order exactly once, the first time the container starts against an empty
 * data volume -- so a fresh `docker compose up` is effectively this project's "install",
 * and re-running it against an already-initialized volume (a no-op, since MySQL only
 * runs init scripts once) is its closest analogue to an "upgrade" path. These tests
 * don't need Docker installed -- they just verify the init-script directory's contract:
 * numbered so the schema, phase-expansion, GL, and demo-credential scripts always apply
 * in the right order, and none of them are accidentally empty.
 */
class DockerInitScriptsTest {

    private static final File INITDB_DIR = new File("docker/initdb");
    private static final Pattern NUMBER_PREFIX = Pattern.compile("^(\\d+)-.*\\.sql$");

    @Test
    void initdbDirectory_exists() {
        assertTrue(INITDB_DIR.isDirectory(), "docker/initdb should exist for docker-compose's MySQL init mount");
    }

    @Test
    void initdbDirectory_containsAtLeastFourNumberedScripts() {
        File[] files = INITDB_DIR.listFiles((dir, name) -> name.endsWith(".sql"));
        assertNotNull(files);
        assertTrue(files.length >= 4, "Expected schema + phase-expansion + GL + demo-credentials scripts");
    }

    @Test
    void everyScript_isNumberedForDeterministicLoadOrder() {
        File[] files = INITDB_DIR.listFiles((dir, name) -> name.endsWith(".sql"));
        assertNotNull(files);
        for (File f : files) {
            assertTrue(NUMBER_PREFIX.matcher(f.getName()).matches(),
                    f.getName() + " should start with a zero-padded number prefix (e.g. 01-schema.sql) " +
                            "so MySQL's alphabetical init-script ordering applies scripts in the intended sequence");
        }
    }

    @Test
    void scriptNumbers_haveNoDuplicates() {
        File[] files = INITDB_DIR.listFiles((dir, name) -> name.endsWith(".sql"));
        assertNotNull(files);
        List<String> numbers = Arrays.stream(files)
                .map(f -> NUMBER_PREFIX.matcher(f.getName()))
                .filter(java.util.regex.Matcher::matches)
                .map(m -> m.group(1))
                .toList();
        assertEquals(numbers.size(), numbers.stream().distinct().count(),
                "Two scripts sharing the same numeric prefix would race on ordering");
    }

    @Test
    void demoCredentialsScript_runsLast() throws IOException {
        File[] files = INITDB_DIR.listFiles((dir, name) -> name.endsWith(".sql"));
        assertNotNull(files);
        File last = Arrays.stream(files).max((a, b) -> a.getName().compareTo(b.getName())).orElseThrow();
        assertTrue(last.getName().contains("demo-credentials"),
                "The demo-credentials UPDATE must run after schema creation, so it should sort last");
    }

    @Test
    void noScriptIsEmpty() throws IOException {
        File[] files = INITDB_DIR.listFiles((dir, name) -> name.endsWith(".sql"));
        assertNotNull(files);
        for (File f : files) {
            long size = Files.size(f.toPath());
            assertTrue(size > 0, f.getName() + " should not be an empty file");
        }
    }
}
