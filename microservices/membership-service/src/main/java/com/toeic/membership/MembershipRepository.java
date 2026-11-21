package com.toeic.membership;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
interface MembershipRepository extends JpaRepository<Membership,UUID>{Optional<Membership> findByUserId(UUID userId);}
