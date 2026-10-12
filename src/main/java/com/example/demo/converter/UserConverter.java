package com.example.demo.converter;

import com.example.demo.common.util.Strings;
import com.example.demo.model.dto.CreateUserRequest;
import com.example.demo.model.dto.UserDTO;
import com.example.demo.model.entity.User;
import org.springframework.stereotype.Component;

/**
 * 用户实体 ↔ DTO 转换。
 *
 * <p>替换骨架里的泛型 {@code UserConverter<User>} 占位：Spring 无法注入类型变量，转换器必须是具体 bean。
 * 出于安全考虑，手机号/邮箱在出站时统一脱敏。
 */
@Component
public class UserConverter {

    public User toEntity(CreateUserRequest request, String encodedPassword) {
        return User.register(request.username(), encodedPassword, request.email(), request.phone(), null);
    }

    public UserDTO toDTO(User user) {
        return new UserDTO(
                user.getId(),
                user.getUsername(),
                Strings.maskEmail(user.getEmail()),
                Strings.maskPhone(user.getPhone()),
                user.getStatus(),
                user.getRoles().stream().sorted().toList(),
                user.getCreateTime()
        );
    }
}
