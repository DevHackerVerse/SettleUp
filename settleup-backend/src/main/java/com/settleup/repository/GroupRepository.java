package com.settleup.repository;

import com.settleup.entity.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GroupRepository extends JpaRepository<Group, Long> {

    Optional<Group> findByPublicId(UUID publicId);

    /**
     * Returns all groups the given user belongs to (as a member or owner).
     * Used for the "list my groups" endpoint.
     */
    @Query("""
        SELECT g FROM Group g
        JOIN GroupMember gm ON gm.group = g
        WHERE gm.user.id = :userId
        ORDER BY g.createdAt DESC
    """)
    List<Group> findAllByMemberUserId(@Param("userId") Long userId);

    /**
     * Count of active groups where user is a member.
     * Used for the premium tier gate (Phase 5): free tier ≤ 3 groups.
     */
    @Query("""
        SELECT COUNT(g) FROM Group g
        JOIN GroupMember gm ON gm.group = g
        WHERE gm.user.id = :userId
    """)
    long countGroupsByMemberUserId(@Param("userId") Long userId);
}
