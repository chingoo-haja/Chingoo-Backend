package com.ldsilver.chingoohaja.domain.category;

import com.ldsilver.chingoohaja.domain.category.enums.RequestStatus;
import com.ldsilver.chingoohaja.domain.common.BaseEntity;
import com.ldsilver.chingoohaja.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "desired_categories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DesiredCategory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String categoryName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus status = RequestStatus.PENDING;

    @Column(length = 500)
    private String adminNote; // 관리자 메모

    public static DesiredCategory of(User user, String categoryName) {
        DesiredCategory request = new DesiredCategory();
        request.user = user;
        request.categoryName = categoryName.trim();
        request.status = RequestStatus.PENDING;
        return request;
    }

    public void approve(String adminNote) {
        this.status = RequestStatus.APPROVED;
        this.adminNote = adminNote;
    }

    public void reject(String adminNote) {
        this.status = RequestStatus.REJECTED;
        this.adminNote = adminNote;
    }

}
