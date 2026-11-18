package backend.dto;

public class LoginRequest {
    private final String email;
    private final String password;

    public LoginRequest(String email, String password){
        this.email = email;
        this.password = password;

    }
    
    public String getEmail(){
        return this.email;
    }

    public String getPassword(){
        return this.password;
    }
}
