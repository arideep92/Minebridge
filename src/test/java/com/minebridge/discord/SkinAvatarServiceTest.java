package com.minebridge.discord;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkinAvatarServiceTest {

    @Test
    void testResolvesAvatarUrlWithUuidAndPlayer() {
        SkinAvatarService service = new SkinAvatarService();
        UUID uuid = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
        String url = service.getAvatarUrl(uuid, "Notch", "https://mc-heads.net/avatar/{uuid}/100", "SMP");

        assertEquals("https://mc-heads.net/avatar/069a79f4-44e9-4726-a5be-fca90e38aaf5/100", url);
    }

    @Test
    void testCachesAvatarUrl() {
        SkinAvatarService service = new SkinAvatarService();
        UUID uuid = UUID.randomUUID();

        assertEquals(0, service.getCacheSize());
        String url1 = service.getAvatarUrl(uuid, "Alex", SkinAvatarService.DEFAULT_AVATAR_TEMPLATE, "SMP");
        assertEquals(1, service.getCacheSize());

        String url2 = service.getAvatarUrl(uuid, "Alex", SkinAvatarService.DEFAULT_AVATAR_TEMPLATE, "SMP");
        assertEquals(url1, url2);
        assertEquals(1, service.getCacheSize());
    }

    @Test
    void testInvalidateRemovesEntry() {
        SkinAvatarService service = new SkinAvatarService();
        UUID uuid = UUID.randomUUID();

        service.getAvatarUrl(uuid, "Steve", SkinAvatarService.DEFAULT_AVATAR_TEMPLATE, "SMP");
        assertEquals(1, service.getCacheSize());

        service.invalidate(uuid);
        assertEquals(0, service.getCacheSize());
    }

    @Test
    void testClearRemovesAllEntries() {
        SkinAvatarService service = new SkinAvatarService();
        service.getAvatarUrl(UUID.randomUUID(), "P1", SkinAvatarService.DEFAULT_AVATAR_TEMPLATE, "SMP");
        service.getAvatarUrl(UUID.randomUUID(), "P2", SkinAvatarService.DEFAULT_AVATAR_TEMPLATE, "SMP");
        assertEquals(2, service.getCacheSize());

        service.clear();
        assertEquals(0, service.getCacheSize());
    }

    @Test
    void testDisabledAndNoneTemplatesReturnEmptyString() {
        SkinAvatarService service = new SkinAvatarService();
        UUID uuid = UUID.randomUUID();

        assertEquals("", service.getAvatarUrl(uuid, "Steve", "none", "SMP"));
        assertEquals("", service.getAvatarUrl(uuid, "Steve", "disabled", "SMP"));
        assertEquals("", service.getAvatarUrl(uuid, "Steve", "DISABLED", "SMP"));
    }

    @Test
    void testFallbackWhenUuidNull() {
        SkinAvatarService service = new SkinAvatarService();
        String url = service.getAvatarUrl(null, "Steve", "https://mc-heads.net/avatar/{uuid}/100", "SMP");
        assertEquals("https://mc-heads.net/avatar/Steve/100", url);
    }

    @Test
    void testBoundedCacheEviction() {
        SkinAvatarService service = new SkinAvatarService(1_000_000_000L, 5);
        for (int i = 0; i < 10; i++) {
            service.getAvatarUrl(UUID.randomUUID(), "P" + i, SkinAvatarService.DEFAULT_AVATAR_TEMPLATE, "SMP");
        }
        assertTrue(service.getCacheSize() <= 5);
    }
}
