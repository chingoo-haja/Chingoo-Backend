package com.ldsilver.chingoohaja.domain.setting;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "service_settings")
@Getter
@NoArgsConstructor
public class ServiceSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "setting_key", unique = true, nullable = false)
    private String key;

    @Column(name = "setting_value", nullable = false)
    private String value;

    private String description;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public void updateValue(String value) {
        this.value = value;
        this.updatedAt = LocalDateTime.now();
    }
}
