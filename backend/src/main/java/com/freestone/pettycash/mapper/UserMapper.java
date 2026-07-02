package com.freestone.pettycash.mapper;

import com.freestone.pettycash.dto.UserResponse;
import com.freestone.pettycash.model.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {
    UserResponse toResponse(User user);
}
