package com.ldsilver.chingoohaja.repository;

import com.ldsilver.chingoohaja.domain.guardian.GuardianRelationship;
import com.ldsilver.chingoohaja.domain.guardian.enums.RelationshipStatus;
import com.ldsilver.chingoohaja.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GuardianRelationshipRepository extends JpaRepository<GuardianRelationship, Long> {
    List<GuardianRelationship> findByGuardianAndRelationshipStatus(User guardian, RelationshipStatus status);
    List<GuardianRelationship> findBySeniorAndRelationshipStatus(User senior, RelationshipStatus status);
}
