package com.karuta.matchtracker.config;

import java.util.Map;
import java.util.Set;

/**
 * 隣室ペア設定（かでる2・7 + 東区民センター）の定数クラス
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
        if (!isKaderuRoom(venueId)) {
            return null;
        }
        return KADERU_FACILITY_CODES.get(getSiteRoomName(venueId));
    }

    /**
     * 有効なかでる部屋名かどうかを判定する
     */
    public static boolean isValidKaderuRoomName(String roomName) {
        return roomName != null && KADERU_FACILITY_CODES.containsKey(roomName);
    }

    /** かでる和室のVenue ID集合 */
    private static final Set<Long> KADERU_VENUE_IDS = Set.of(3L, 11L, 4L, 8L);

    /** 隣室空き確認の対象となるVenue ID集合（かでる和室 + 東🌸）。かっこう(12)は単独運用対象外のため含めない */
    private static final Set<Long> ADJACENT_CHECK_TARGET_VENUE_IDS = Set.of(3L, 11L, 4L, 8L, 6L);

    /** 会場ごとの夜間時間帯ラベル */
    private static final Map<Long, String> NIGHT_TIME_LABELS = Map.of(
            3L,  "17-21",
            11L, "17-21",
            4L,  "17-21",
            8L,  "17-21",
            6L,  "18-21"
    );

    /** 隣室ペア・拡張マップ（かでる和室 + 東区民センター） */
    private static final Map<Long, RoomInfo> ROOM_MAP = Map.of(
            3L,  new RoomInfo("すずらん", 11L, 7L),
            11L, new RoomInfo("はまなす", 3L,  7L),
            4L,  new RoomInfo("あかなら", 8L,  9L),
            8L,  new RoomInfo("えぞまつ", 4L,  9L),
            6L,  new RoomInfo("さくら",   12L, 10L),
            12L, new RoomInfo("かっこう", 6L,  10L)
    );

    /** 隣室名のマッピング（Venue ID → サイト上の隣室名） */
    private static final Map<Long, String> ADJACENT_ROOM_NAMES = Map.of(
            3L,  "はまなす",
            11L, "すずらん",
            4L,  "えぞまつ",
            8L,  "あかなら",
            6L,  "かっこう",
            12L, "さくら"
    );

    /** 拡張後の会場名 */
    private static final Map<Long, String> EXPANDED_VENUE_NAMES = Map.of(
            7L,  "すずらん・はまなす",
            9L,  "あかなら・えぞまつ",
            10L, "東全室"
    );

    /** 拡張後の定員 */
    private static final Map<Long, Integer> EXPANDED_CAPACITY = Map.of(
            7L,  24,
            9L,  24,
            10L, 18
    );

    /**
     * かでる和室かどうかを判定する
     */
    public static boolean isKaderuRoom(Long venueId) {
        return venueId != null && KADERU_VENUE_IDS.contains(venueId);
    }

    /**
     * 隣室空き確認の対象会場かどうかを判定する（かでる和室 + 東🌸）
     */
    public static boolean isAdjacentCheckTarget(Long venueId) {
        return venueId != null && ADJACENT_CHECK_TARGET_VENUE_IDS.contains(venueId);
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
     * 予約サイト上の部屋名を返す（自身のVenue IDから）
     */
    public static String getSiteRoomName(Long venueId) {
        RoomInfo info = ROOM_MAP.get(venueId);
        return info != null ? info.siteRoomName : null;
    }

    /**
     * 隣室の部屋名を返す（サイト上の名前）
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

    /**
     * 会場ごとの夜間時間帯ラベルを返す（例: "17-21", "18-21"）
     */
    public static String getNightTimeLabel(Long venueId) {
        return NIGHT_TIME_LABELS.get(venueId);
    }

    private record RoomInfo(String siteRoomName, Long adjacentVenueId, Long expandedVenueId) {}
}
