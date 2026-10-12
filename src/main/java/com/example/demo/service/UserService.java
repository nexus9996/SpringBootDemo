package com.example.demo.service;

import com.example.demo.api.PageResponse;
import com.example.demo.model.dto.CreateUserRequest;
import com.example.demo.model.dto.UpdateProfileRequest;
import com.example.demo.model.dto.UpdateUserRequest;
import com.example.demo.model.dto.UserDTO;
import com.example.demo.model.query.UserPageQuery;

/**
 * 用户应用服务：编排用例，事务边界在这一层，返回 DTO 而不暴露实体。
 */
public interface UserService {

    UserDTO create(CreateUserRequest request);

    UserDTO getById(Long id);

    UserDTO getByUsername(String username);

    PageResponse<UserDTO> page(UserPageQuery query);

    UserDTO update(Long id, UpdateUserRequest request);

    UserDTO updateProfile(String username, UpdateProfileRequest request);

    void delete(Long id);
}
