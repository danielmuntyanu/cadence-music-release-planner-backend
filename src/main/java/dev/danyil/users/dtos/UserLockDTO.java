package dev.danyil.users.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UserLockDTO(
    
    @JsonProperty("make_locked") 
    boolean makeLocked

) {

}
