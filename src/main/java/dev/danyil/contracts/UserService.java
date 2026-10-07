package dev.danyil.contracts;

import dev.danyil.users.dtos.UserRequestCreateDTO;

public interface UserService {

    void register(UserRequestCreateDTO requestDTO);

}
