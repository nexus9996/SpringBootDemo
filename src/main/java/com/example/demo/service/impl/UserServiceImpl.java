package com.example.demo.service.impl;

import com.example.demo.api.ErrorCode;
import com.example.demo.api.PageResponse;
import com.example.demo.common.exception.BusinessException;
import com.example.demo.common.exception.NotFoundException;
import com.example.demo.converter.UserConverter;
import com.example.demo.model.dto.CreateUserRequest;
import com.example.demo.model.dto.UpdateProfileRequest;
import com.example.demo.model.dto.UpdateUserRequest;
import com.example.demo.model.dto.UserDTO;
import com.example.demo.model.entity.User;
import com.example.demo.model.query.UserPageQuery;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * 用户用例实现。
 *
 * <p>注意与骨架版的两个关键差异：
 * <ul>
 *   <li>不再有 {@code UserServiceImpl<UserConverter>} 这种带类型参数的服务类 —— Spring 无法为类型变量注入依赖；</li>
 *   <li>密码只以 BCrypt 摘要入库，接口不返回密码，出站字段统一脱敏。</li>
 * </ul>
 */
@Service
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserConverter converter;

    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder, UserConverter converter) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.converter = converter;
    }

    @Override
    public UserDTO create(CreateUserRequest request) {
        var username = request.username().trim();
        if (userRepository.existsByUsername(username)) {
            throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS, "用户名已存在: " + username);
        }
        var user = converter.toEntity(request, passwordEncoder.encode(request.password()));
        return converter.toDTO(userRepository.save(user));
    }

    @Override
    @Transactional(readOnly = true)
    public UserDTO getById(Long id) {
        return converter.toDTO(requireUser(id));
    }

    @Override
    @Transactional(readOnly = true)
    public UserDTO getByUsername(String username) {
        return converter.toDTO(userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("用户 " + username)));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserDTO> page(UserPageQuery query) {
        var page = userRepository.findAll(query.toSpecification(), query.toPageable());
        return PageResponse.of(page.map(converter::toDTO));
    }

    @Override
    public UserDTO update(Long id, UpdateUserRequest request) {
        requireSomethingToChange(request.isEmpty());
        var user = requireUser(id);
        if (request.email() != null || request.phone() != null) {
            user.changeProfile(
                    request.email() != null ? request.email() : user.getEmail(),
                    request.phone() != null ? request.phone() : user.getPhone());
        }
        if (request.status() != null) {
            user.changeStatus(request.status());
        }
        if (request.password() != null) {
            user.changePassword(passwordEncoder.encode(request.password()));
        }
        userRepository.flush();   // 让 updateTime 在响应中即时可见
        return converter.toDTO(user);
    }

    @Override
    public UserDTO updateProfile(String username, UpdateProfileRequest request) {
        requireSomethingToChange(request.isEmpty());
        var user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("用户 " + username));
        if (request.email() != null || request.phone() != null) {
            user.changeProfile(
                    request.email() != null ? request.email() : user.getEmail(),
                    request.phone() != null ? request.phone() : user.getPhone());
        }
        if (request.password() != null) {
            user.changePassword(passwordEncoder.encode(request.password()));
        }
        userRepository.flush();
        return converter.toDTO(user);
    }

    @Override
    public void delete(Long id) {
        userRepository.delete(requireUser(id));
    }

    private User requireUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new NotFoundException("用户", id));
    }

    private static void requireSomethingToChange(boolean empty) {
        if (empty) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "至少需要提供一个待修改字段");
        }
    }
}
