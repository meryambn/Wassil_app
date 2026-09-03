package com.example.wassilapp.remote.dto;

/**
 * Represents a configuration row from public.platform_settings.
 */
public class PlatformSettingDto {
    public String key;
    public String value;
    public String updated_at;

    public PlatformSettingDto() {}

    public PlatformSettingDto(String key, String value) {
        this.key = key;
        this.value = value;
    }
}
