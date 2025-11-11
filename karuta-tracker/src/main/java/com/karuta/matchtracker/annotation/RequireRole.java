package com.karuta.matchtracker.annotation;

import com.karuta.matchtracker.entity.Player.Role;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * メソッド実行に必要なロールを指定するアノテーション
 *
 * このアノテーションが付与されたメソッドは、指定されたロールを持つユーザーのみが実行できます。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {
    /**
     * メソッド実行に必要なロール
     * 複数指定した場合は、いずれかのロールを持っていればアクセス可能
     */
    Role[] value();
}
