package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SystemSettingRepository extends JpaRepository<SystemSetting, Long> {
    Optional<SystemSetting> findBySettingKey(String settingKey);

    Optional<SystemSetting> findBySettingKeyAndOrganizationId(String settingKey, Long organizationId);

    List<SystemSetting> findByOrganizationId(Long organizationId);
}
