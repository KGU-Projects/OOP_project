package main.java.game.service;

//임시 서비스
public class UserService {

    public boolean registerUser(String username) {
        return username != null && !username.trim().isEmpty();
    }
}