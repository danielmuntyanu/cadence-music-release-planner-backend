package dev.danyil.contracts;

import dev.danyil.users.dtos.UserAdministrationResponseDTO;
import dev.danyil.users.dtos.UserCurrentResponseDTO;
import dev.danyil.users.dtos.UserRequestCreateDTO;
import dev.danyil.users.dtos.UserResponseDTO;

public interface UserService extends GenericGetService<UserAdministrationResponseDTO> {

    UserResponseDTO register(UserRequestCreateDTO requestDTO);
    
    UserCurrentResponseDTO getCurrent(String username);

    UserAdministrationResponseDTO updateLocked(Long id, boolean makeLocked);

}
