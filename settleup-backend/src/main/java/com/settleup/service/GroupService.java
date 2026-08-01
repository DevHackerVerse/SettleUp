package com.settleup.service;

import com.settleup.dto.*;
import com.settleup.entity.Group;
import com.settleup.entity.GroupMember;
import com.settleup.entity.GroupMember.MemberRole;
import com.settleup.entity.User;
import com.settleup.exception.ConflictException;
import com.settleup.exception.ForbiddenException;
import com.settleup.exception.ResourceNotFoundException;
import com.settleup.repository.GroupMemberRepository;
import com.settleup.repository.GroupRepository;
import com.settleup.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Handles group creation, retrieval, and membership management.
 *
 * Spec §5.2 — Group endpoints.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;

    /**
     * Creates a new group and auto-adds the creator as OWNER.
     */
    @Transactional
    public GroupResponse createGroup(CreateGroupRequest req, User creator) {
        Group group = Group.builder()
                .name(req.name())
                .description(req.description())
                .defaultCurrency(req.currency() != null ? req.currency() : "INR")
                .budgetAmount(req.budgetAmount())
                .createdBy(creator)
                .build();

        group = groupRepository.save(group);

        // Auto-add creator as OWNER
        GroupMember ownerMembership = GroupMember.builder()
                .group(group)
                .user(creator)
                .role(MemberRole.OWNER)
                .build();
        groupMemberRepository.save(ownerMembership);

        log.info("Created group id={} name={} by userId={}", group.getId(), group.getName(), creator.getId());
        return toResponse(group, List.of(ownerMembership));
    }

    /**
     * Returns all groups the current user belongs to.
     */
    @Transactional(readOnly = true)
    public List<GroupResponse> getGroupsForUser(User currentUser) {
        return groupRepository.findAllByMemberUserId(currentUser.getId()).stream()
                .map(g -> toResponse(g, null))
                .toList();
    }

    /**
     * Returns a single group with its full member list.
     * Only members of the group can view it.
     */
    @Transactional(readOnly = true)
    public GroupResponse getGroupDetail(String groupPublicId, User currentUser) {
        Group group = findGroupByPublicId(groupPublicId);
        requireMembership(group.getId(), currentUser.getId());

        List<GroupMember> members = groupMemberRepository.findAllByGroupId(group.getId());
        return toResponse(group, members);
    }

    /**
     * Adds a user (looked up by email) to a group.
     * Only group members can add other members.
     */
    @Transactional
    public void addMember(String groupPublicId, AddMemberRequest req, User currentUser) {
        Group group = findGroupByPublicId(groupPublicId);
        requireMembership(group.getId(), currentUser.getId());

        User newMember = userRepository.findByEmail(req.email())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No user found with email: " + req.email()));

        if (groupMemberRepository.existsByGroupIdAndUserId(group.getId(), newMember.getId())) {
            throw new ConflictException("User is already a member of this group");
        }

        GroupMember membership = GroupMember.builder()
                .group(group)
                .user(newMember)
                .role(MemberRole.MEMBER)
                .build();
        groupMemberRepository.save(membership);
        log.info("Added userId={} to groupId={}", newMember.getId(), group.getId());
    }

    // ── Helpers ──────────────────────────────────────────────────────

    public Group findGroupByPublicId(String publicId) {
        return groupRepository.findByPublicId(UUID.fromString(publicId))
                .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + publicId));
    }

    public void requireMembership(Long groupId, Long userId) {
        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new ForbiddenException("You are not a member of this group");
        }
    }

    private GroupResponse toResponse(Group group, List<GroupMember> members) {
        List<GroupResponse.MemberEntry> memberEntries = members == null ? List.of()
                : members.stream()
                    .map(m -> new GroupResponse.MemberEntry(
                            m.getUser().getPublicId().toString(),
                            m.getUser().getName(),
                            m.getUser().getEmail(),
                            m.getRole().name()
                    ))
                    .toList();

        return new GroupResponse(
                group.getPublicId().toString(),
                group.getName(),
                group.getDescription(),
                group.getDefaultCurrency(),
                group.getBudgetAmount(),
                group.getBudgetAlertThresholdPct(),
                group.getCreatedBy().getPublicId().toString(),
                group.getCreatedAt(),
                memberEntries
        );
    }
}
