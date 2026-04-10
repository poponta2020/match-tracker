package com.karuta.matchtracker.config;

import java.util.Map;

/**
 * かでる2・7の和室の隣室ペア設定（定数クラス）
 */
public final class AdjacentRoomConfig {

    private AdjacentRoomConfig() {}

    /** かでるサイトの施設コード（setAppStatusの第1引数） */
    private static final Map<String, String> KADERU_FACILITY_CODES = Map.of(
            "すずらん", "001|018|01|2|2|0",
            "はまなす", "001|018|02|3|2|0",
            "あかなら", "001|017|02|3|2|0",
            "えぞまつ", "001|017|01|2|2|0"
    );

    /** タイムスロット: index → 時間帯コード */
    private static final Map<Integer, String> TIME_SLOT_RANGES = Map.of(
            0, "09001200",
            1, "13001600",
            2, "17002100"
    );

    /**
     * かでるサイトの施設コードを返す
     */
    public static String getKaderuFacilityCode(String roomName) {
        return KADERU_FACILITY_CODES.get(roomName);
    }

    /**
     * タイムスロットの時間帯コードを返す
     * @param slotIndex 0=午前, 1=午後, 2=夜間
     */
    public static String getTimeSlotRange(int slotIndex) {
        return TIME_SLOT_RANGES.get(slotIndex);
    }

    /**
     * Venue IDからかでるサイトの施設コードを返す
     */
    public static String getKaderuFacilityCodeByVenueId(Long venueId) {
        String roomName = getKaderuRoomName(venueId);
        return roomName != null ? KADERU_FACILITY_CODES.get(roomName) : null;
    }

    /**
     * 有効なかでる部屋名かどうかを判定する
     */
    public static boolean isValidKaderuRoomName(String roomName) {
        return roomName != null && KADERU_FACILITY_CODES.containsKey(roomName);
    }

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
