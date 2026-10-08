package dev.danyil.contracts;

import dev.danyil.users.dtos.UserCurrentResponseDTO;
import dev.danyil.users.dtos.UserRequestCreateDTO;
import dev.danyil.users.dtos.UserResponseDTO;

public interface UserService {

    UserResponseDTO register(UserRequestCreateDTO requestDTO);
    UserCurrentResponseDTO getCurrent(String username);

}
