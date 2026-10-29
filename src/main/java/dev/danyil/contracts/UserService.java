package dev.danyil.contracts;

import org.springframework.web.multipart.MultipartFile;

import dev.danyil.users.dtos.UserAdministrationResponseDTO;
import dev.danyil.users.dtos.UserCurrentResponseDTO;
import dev.danyil.users.dtos.UserRequestCreateDTO;
import dev.danyil.users.dtos.UserRequestPasswordDTO;
import dev.danyil.users.dtos.UserRequestUpdateDTO;
import dev.danyil.users.dtos.UserResponseDTO;

public interface UserService extends GenericGetService<UserAdministrationResponseDTO>, GenericEditService<UserRequestUpdateDTO, UserCurrentResponseDTO>  {

    UserResponseDTO register(UserRequestCreateDTO requestDTO);
    
    UserCurrentResponseDTO getCurrent(String username);

    UserAdministrationResponseDTO updateLocked(Long id, boolean makeLocked);

    void updatePassword(Long id, UserRequestPasswordDTO dto);

    UserCurrentResponseDTO uploadAvatar(Long id, MultipartFile file);

    UserCurrentResponseDTO removeAvatar(Long id);
}
