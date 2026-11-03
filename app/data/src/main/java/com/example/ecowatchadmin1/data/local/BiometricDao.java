package com.example.ecowatchadmin1.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.ecowatchadmin1.data.models.BiometricSetting;

import java.util.List;

@Dao
public interface BiometricDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void saveSetting(BiometricSetting setting);

    @Query("SELECT * FROM biometric_settings WHERE residentId = :residentId LIMIT 1")
    BiometricSetting getSettingByResidentId(String residentId);

    @Query("UPDATE biometric_settings SET isEnabled = :enabled WHERE residentId = :residentId")
    void updateEnabledStatus(String residentId, boolean enabled);

    @Query("SELECT * FROM biometric_settings WHERE isEnabled = 1 OR isFingerprintEnabled = 1 OR isFaceIDEnabled = 1")
    List<BiometricSetting> getAllEnabledSettings();
}
