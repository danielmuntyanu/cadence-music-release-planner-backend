package dev.danyil.users;

import org.springframework.stereotype.Service;

import dev.danyil.contracts.UserService;
import dev.danyil.users.dtos.UserRequestCreateDTO;
import lombok.RequiredArgsConstructor;

@Service 
@RequiredArgsConstructor 
public class UserServiceImpl implements UserService {

    private UserRepository userRepository;
    private UserProfileEntity userProfileEntity;

    @Override
    public void register(UserRequestCreateDTO requestDTO) {
        // TODO Auto-generated method stub
        
    }

}
