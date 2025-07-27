package com.bloom.app.service.mapper;

import com.bloom.app.domain.dto.request.user.CreateUserRequest;
import com.bloom.app.domain.dto.request.user.UpdateUserRequest;
import com.bloom.app.domain.dto.response.user.UserResponse;
import com.bloom.app.domain.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface UserMapper {
    UserResponse userToUserResponse(User user);

    User toUserEntity(CreateUserRequest request);

    void updateUserRequestToEntity(UpdateUserRequest request, @MappingTarget User user);
}
