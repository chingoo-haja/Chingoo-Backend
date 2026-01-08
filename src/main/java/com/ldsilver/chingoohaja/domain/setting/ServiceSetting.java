package com.ldsilver.chingoohaja.domain.setting;

import com.ldsilver.chingoohaja.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "service_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ServiceSetting extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "setting_key", unique = true, nullable = false, length = 50)
    private String key;

    @Column(name = "setting_value", nullable = false)
    private String value;

    @Column(length = 255)
    private String description;

    public static ServiceSetting of(String key, String value, String description) {
        ServiceSetting setting = new ServiceSetting();
        setting.key = key;
        setting.value = value;
        setting.description = description;
        return setting;
    }

    public void updateValue(String value) {
        this.value = value;
    }
}
