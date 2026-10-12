package com.example.demo.repository;

import com.example.demo.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 用户仓储。
 *
 * <p>复杂条件走 {@link Specification}（类型安全、可组合），避免方法名爆炸与手写字符串 SQL。
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    /** 登录与唯一性校验使用，方法名即查询语义。 */
    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);
}
