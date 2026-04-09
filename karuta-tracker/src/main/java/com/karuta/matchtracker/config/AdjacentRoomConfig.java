package com.karuta.matchtracker.config;

import java.util.Map;

/**
 * かでる2・7の和室の隣室ペア設定（定数クラス）
 */
public final class AdjacentRoomConfig {

    private AdjacentRoomConfig() {}

    /** かでる和室のVenue ID一覧 */
    private static final Map<Long, RoomInfo> ROOM_MAP = Map.of(
            3L, new RoomInfo("すずらん", 11L, 7L),
            11L, new RoomInfo("はまなす", 3L, 7L),
            4L, new RoomInfo("あかなら", 8L, 9L),
            8L, new RoomInfo("えぞまつ", 4L, 9L)
    );

    /** 隣室名のマッピング（Venue ID → かでるサイト上の隣室名） */
    private static final Map<Long, String> ADJACENT_ROOM_NAMES = Map.of(
            3L, "はまなす",
            11L, "すずらん",
            4L, "えぞまつ",
            8L, "あかなら"
    );

    /** 拡張後の会場名 */
    private static final Map<Long, String> EXPANDED_VENUE_NAMES = Map.of(
            7L, "すずらん・はまなす",
            9L, "あかなら・えぞまつ"
    );

    /** 拡張後の定員 */
    private static final Map<Long, Integer> EXPANDED_CAPACITY = Map.of(
            7L, 24,
            9L, 24
    );

    /**
     * かでる和室かどうかを判定する
     */
    public static boolean isKaderuRoom(Long venueId) {
        return venueId != null && ROOM_MAP.containsKey(venueId);
    }

    /**
     * 隣接会場のVenue IDを返す
     */
    public static Long getAdjacentVenueId(Long venueId) {
        RoomInfo info = ROOM_MAP.get(venueId);
        return info != null ? info.adjacentVenueId : null;
    }

    /**
     * 拡張後のVenue IDを返す
     */
    public static Long getExpandedVenueId(Long venueId) {
        RoomInfo info = ROOM_MAP.get(venueId);
        return info != null ? info.expandedVenueId : null;
    }

    /**
     * かでるサイト上の部屋名を返す（自身のVenue IDから）
     */
    public static String getKaderuRoomName(Long venueId) {
        RoomInfo info = ROOM_MAP.get(venueId);
        return info != null ? info.kaderuRoomName : null;
    }

    /**
     * 隣室の部屋名を返す（かでるサイト上の名前）
     */
    public static String getAdjacentRoomName(Long venueId) {
        return ADJACENT_ROOM_NAMES.get(venueId);
    }

    /**
     * 拡張後の会場名を返す
     */
    public static String getExpandedVenueName(Long venueId) {
        Long expandedId = getExpandedVenueId(venueId);
        return expandedId != null ? EXPANDED_VENUE_NAMES.get(expandedId) : null;
    }

    /**
     * 拡張後の定員を返す
     */
    public static Integer getExpandedCapacity(Long venueId) {
        Long expandedId = getExpandedVenueId(venueId);
        return expandedId != null ? EXPANDED_CAPACITY.get(expandedId) : null;
    }

    private record RoomInfo(String kaderuRoomName, Long adjacentVenueId, Long expandedVenueId) {}
}
