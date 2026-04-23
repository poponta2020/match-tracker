package com.karuta.matchtracker.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AdjacentRoomConfig テスト")
class AdjacentRoomConfigTest {

    @Test
    @DisplayName("かでる和室の判定 - すずらん(3)")
    void isKaderuRoom_suzuran() {
        assertTrue(AdjacentRoomConfig.isKaderuRoom(3L));
    }

    @Test
    @DisplayName("かでる和室の判定 - はまなす(11)")
    void isKaderuRoom_hamanasu() {
        assertTrue(AdjacentRoomConfig.isKaderuRoom(11L));
    }

    @Test
    @DisplayName("かでる和室の判定 - あかなら(4)")
    void isKaderuRoom_akanara() {
        assertTrue(AdjacentRoomConfig.isKaderuRoom(4L));
    }

    @Test
    @DisplayName("かでる和室の判定 - えぞまつ(8)")
    void isKaderuRoom_ezomatsu() {
        assertTrue(AdjacentRoomConfig.isKaderuRoom(8L));
    }

    @Test
    @DisplayName("かでる和室でないVenue IDの判定")
    void isKaderuRoom_nonKaderu() {
        assertFalse(AdjacentRoomConfig.isKaderuRoom(1L));
        assertFalse(AdjacentRoomConfig.isKaderuRoom(99L));
        assertFalse(AdjacentRoomConfig.isKaderuRoom(null));
    }

    @Test
    @DisplayName("隣接会場IDの取得 - すずらん→はまなす")
    void getAdjacentVenueId_suzuran() {
        assertEquals(11L, AdjacentRoomConfig.getAdjacentVenueId(3L));
    }

    @Test
    @DisplayName("隣接会場IDの取得 - はまなす→すずらん")
    void getAdjacentVenueId_hamanasu() {
        assertEquals(3L, AdjacentRoomConfig.getAdjacentVenueId(11L));
    }

    @Test
    @DisplayName("隣接会場IDの取得 - あかなら→えぞまつ")
    void getAdjacentVenueId_akanara() {
        assertEquals(8L, AdjacentRoomConfig.getAdjacentVenueId(4L));
    }

    @Test
    @DisplayName("隣接会場IDの取得 - えぞまつ→あかなら")
    void getAdjacentVenueId_ezomatsu() {
        assertEquals(4L, AdjacentRoomConfig.getAdjacentVenueId(8L));
    }

    @Test
    @DisplayName("拡張後Venue IDの取得")
    void getExpandedVenueId() {
        assertEquals(7L, AdjacentRoomConfig.getExpandedVenueId(3L));
        assertEquals(7L, AdjacentRoomConfig.getExpandedVenueId(11L));
        assertEquals(9L, AdjacentRoomConfig.getExpandedVenueId(4L));
        assertEquals(9L, AdjacentRoomConfig.getExpandedVenueId(8L));
    }

    @Test
    @DisplayName("サイト上の部屋名の取得")
    void getSiteRoomName() {
        assertEquals("すずらん", AdjacentRoomConfig.getSiteRoomName(3L));
        assertEquals("はまなす", AdjacentRoomConfig.getSiteRoomName(11L));
        assertEquals("あかなら", AdjacentRoomConfig.getSiteRoomName(4L));
        assertEquals("えぞまつ", AdjacentRoomConfig.getSiteRoomName(8L));
    }

    @Test
    @DisplayName("隣室名の取得")
    void getAdjacentRoomName() {
        assertEquals("はまなす", AdjacentRoomConfig.getAdjacentRoomName(3L));
        assertEquals("すずらん", AdjacentRoomConfig.getAdjacentRoomName(11L));
        assertEquals("えぞまつ", AdjacentRoomConfig.getAdjacentRoomName(4L));
        assertEquals("あかなら", AdjacentRoomConfig.getAdjacentRoomName(8L));
    }

    @Test
    @DisplayName("拡張後の会場名の取得")
    void getExpandedVenueName() {
        assertEquals("すずらん・はまなす", AdjacentRoomConfig.getExpandedVenueName(3L));
        assertEquals("すずらん・はまなす", AdjacentRoomConfig.getExpandedVenueName(11L));
        assertEquals("あかなら・えぞまつ", AdjacentRoomConfig.getExpandedVenueName(4L));
        assertEquals("あかなら・えぞまつ", AdjacentRoomConfig.getExpandedVenueName(8L));
    }

    @Test
    @DisplayName("拡張後の定員の取得")
    void getExpandedCapacity() {
        assertEquals(24, AdjacentRoomConfig.getExpandedCapacity(3L));
        assertEquals(24, AdjacentRoomConfig.getExpandedCapacity(11L));
        assertEquals(24, AdjacentRoomConfig.getExpandedCapacity(4L));
        assertEquals(24, AdjacentRoomConfig.getExpandedCapacity(8L));
    }

    @Test
    @DisplayName("存在しないVenue IDはnullを返す")
    void nonExistentVenueReturnsNull() {
        assertNull(AdjacentRoomConfig.getAdjacentVenueId(99L));
        assertNull(AdjacentRoomConfig.getExpandedVenueId(99L));
        assertNull(AdjacentRoomConfig.getSiteRoomName(99L));
        assertNull(AdjacentRoomConfig.getAdjacentRoomName(99L));
        assertNull(AdjacentRoomConfig.getExpandedVenueName(99L));
        assertNull(AdjacentRoomConfig.getExpandedCapacity(99L));
    }

    // ------------------------------------------------------------
    // 東区民センター（東🌸=6 / かっこう=12 / 東全室=10）
    // ------------------------------------------------------------

    @Test
    @DisplayName("東🌸(6)はかでる和室ではないが隣室チェック対象")
    void higashi_sakura_isNotKaderu_butIsAdjacentCheckTarget() {
        assertFalse(AdjacentRoomConfig.isKaderuRoom(6L));
        assertTrue(AdjacentRoomConfig.isAdjacentCheckTarget(6L));
    }

    @Test
    @DisplayName("かっこう(12)は隣室チェック対象外（単独運用対象外）")
    void kakkou_isNotAdjacentCheckTarget() {
        assertFalse(AdjacentRoomConfig.isKaderuRoom(12L));
        assertFalse(AdjacentRoomConfig.isAdjacentCheckTarget(12L));
    }

    @Test
    @DisplayName("隣室チェック対象はかでる4部屋 + 東🌸")
    void isAdjacentCheckTarget_coversKaderuAndHigashi() {
        assertTrue(AdjacentRoomConfig.isAdjacentCheckTarget(3L));
        assertTrue(AdjacentRoomConfig.isAdjacentCheckTarget(11L));
        assertTrue(AdjacentRoomConfig.isAdjacentCheckTarget(4L));
        assertTrue(AdjacentRoomConfig.isAdjacentCheckTarget(8L));
        assertTrue(AdjacentRoomConfig.isAdjacentCheckTarget(6L));
        assertFalse(AdjacentRoomConfig.isAdjacentCheckTarget(1L));
        assertFalse(AdjacentRoomConfig.isAdjacentCheckTarget(99L));
        assertFalse(AdjacentRoomConfig.isAdjacentCheckTarget(null));
    }

    @Test
    @DisplayName("夜間時間帯ラベル - かでる和室は17-21、東🌸は18-21")
    void getNightTimeLabel_returnsVenueSpecificLabel() {
        assertEquals("17-21", AdjacentRoomConfig.getNightTimeLabel(3L));
        assertEquals("17-21", AdjacentRoomConfig.getNightTimeLabel(11L));
        assertEquals("17-21", AdjacentRoomConfig.getNightTimeLabel(4L));
        assertEquals("17-21", AdjacentRoomConfig.getNightTimeLabel(8L));
        assertEquals("18-21", AdjacentRoomConfig.getNightTimeLabel(6L));
        assertNull(AdjacentRoomConfig.getNightTimeLabel(12L)); // かっこうは対象外
        assertNull(AdjacentRoomConfig.getNightTimeLabel(99L));
    }

    @Test
    @DisplayName("東区民センターの隣室ペア - 東🌸↔かっこう")
    void higashi_adjacentPair() {
        assertEquals(12L, AdjacentRoomConfig.getAdjacentVenueId(6L));
        assertEquals(6L, AdjacentRoomConfig.getAdjacentVenueId(12L));
        assertEquals("かっこう", AdjacentRoomConfig.getAdjacentRoomName(6L));
        assertEquals("さくら", AdjacentRoomConfig.getAdjacentRoomName(12L));
    }

    @Test
    @DisplayName("東区民センターの拡張後会場 - 東全室(10) / 定員18")
    void higashi_expandedVenue() {
        assertEquals(10L, AdjacentRoomConfig.getExpandedVenueId(6L));
        assertEquals(10L, AdjacentRoomConfig.getExpandedVenueId(12L));
        assertEquals("東全室", AdjacentRoomConfig.getExpandedVenueName(6L));
        assertEquals("東全室", AdjacentRoomConfig.getExpandedVenueName(12L));
        assertEquals(18, AdjacentRoomConfig.getExpandedCapacity(6L));
        assertEquals(18, AdjacentRoomConfig.getExpandedCapacity(12L));
    }

    @Test
    @DisplayName("東区民センターのサイト部屋名")
    void higashi_siteRoomName() {
        assertEquals("さくら", AdjacentRoomConfig.getSiteRoomName(6L));
        assertEquals("かっこう", AdjacentRoomConfig.getSiteRoomName(12L));
    }
}
