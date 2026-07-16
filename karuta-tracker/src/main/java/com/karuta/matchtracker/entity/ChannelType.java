package com.karuta.matchtracker.entity;

/**
 * LINEチャネルの用途種別
 */
public enum ChannelType {
    PLAYER,
    ADMIN,
    /** 全体LINEグループへの一斉配信専用（個人割当プールと分離＝カニバリ防止） */
    GROUP
}
