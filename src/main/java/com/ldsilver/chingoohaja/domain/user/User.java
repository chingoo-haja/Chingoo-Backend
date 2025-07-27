package com.ldsilver.chingoohaja.domain.user;

import com.ldsilver.chingoohaja.domain.common.BaseEntity;
import com.ldsilver.chingoohaja.domain.user.enums.Gender;
import com.ldsilver.chingoohaja.domain.user.enums.UserType;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    @Email()
    @NotBlank
    private String email;

    @Column(nullable = false,unique = true)
    @NotBlank
    @Size(min = 1, max = 100)
    private String nickname;

    @Column(nullable = false)
    private String realName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gender gender;

    @Column(nullable = false)
    private LocalDate birth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserType userType = UserType.USER;

    @Column(nullable = false)
    private String profileImageUrl;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private String providerId;

    public static User of(
            String email,
            String nickname,
            String realName,
            Gender gender,
            LocalDate birth,
            UserType userType,
            String profileImageUrl,
            String provider,
            String providerId
    ) {
        User user = new User();
        user.email = email;
        user.nickname = nickname;
        user.realName = realName;
        user.gender = gender;
        user.birth = birth;
        user.userType = userType;
        user.profileImageUrl = profileImageUrl;
        user.provider = provider;
        user.providerId = providerId;
        return user;
    }

}
