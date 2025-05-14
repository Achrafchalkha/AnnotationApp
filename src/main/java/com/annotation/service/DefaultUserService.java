package com.annotation.service;


import com.annotation.model.User;
import org.springframework.security.core.userdetails.UserDetailsService;

public interface DefaultUserService extends UserDetailsService {

    String getCurrentUserName();

}
