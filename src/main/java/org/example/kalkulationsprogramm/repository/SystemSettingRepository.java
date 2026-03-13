package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SystemSettingRepository extends JpaRepository<SystemSetting, String> {

    List<SystemSetting> findByKeyStartingWith(String prefix);
}
