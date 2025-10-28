package com.authapi.webhostingservice.controller;

import com.authapi.webhostingservice.model.Admin;
import com.authapi.webhostingservice.repository.AdminRepository;
import com.authapi.webhostingservice.security.JwtUtil;

import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth/admin")
public class AdminAuthController {
    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
        private static final Pattern EMAIL_PATTERN = 
        Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    

    public AdminAuthController(AdminRepository adminRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    // Request DTO
    public static class LoginRequest {
        private String email;
        private String password;

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    // Response DTOs
    public static class ErrorResponse {
        private String error;
        public ErrorResponse(String error) { this.error = error; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }

    public static class TokenResponse {
        private String token;
        public TokenResponse(String token) { this.token = token; }
        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        // Basic validation
        if (request.getEmail() == null || request.getPassword() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("Email and password are required"));
        }

        
        


        if (!EMAIL_PATTERN.matcher(request.getEmail()).matches()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("Invalid email format"));
        }

        Admin dbAdmin = adminRepository.findByEmail(request.getEmail());

                // Generic error message - same as user login
                if (dbAdmin == null || !passwordEncoder.matches(request.getPassword(), dbAdmin.getPassword())) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(new ErrorResponse("Invalid credentials"));
                }
        try {
            String token = jwtUtil.generateToken(dbAdmin.getEmail(), "ADMIN");
            return ResponseEntity.ok(new TokenResponse(token));
        } catch (Exception e) {
            System.err.println("Token generation error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Login failed"));
        }
    }
}




// Use the following to create the admin, comment when done and use the code above
// package com.authapi.webhostingservice.controller;

// import com.authapi.webhostingservice.model.Admin;
// import com.authapi.webhostingservice.repository.AdminRepository;
// import com.authapi.webhostingservice.security.JwtUtil;
// import org.springframework.http.HttpStatus;
// import org.springframework.http.ResponseEntity;
// import org.springframework.security.crypto.password.PasswordEncoder;
// import org.springframework.web.bind.annotation.*;

// @RestController
// @RequestMapping("/auth/admin")
// public class AdminAuthController {
//     private final AdminRepository adminRepository;
//     private final PasswordEncoder passwordEncoder;
//     private final JwtUtil jwtUtil;

//     public AdminAuthController(AdminRepository adminRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
//         this.adminRepository = adminRepository;
//         this.passwordEncoder = passwordEncoder;
//         this.jwtUtil = jwtUtil;
//     }

//     // Request DTO
//     public static class LoginRequest {
//         private String email;
//         private String password;

//         public String getEmail() { return email; }
//         public void setEmail(String email) { this.email = email; }
//         public String getPassword() { return password; }
//         public void setPassword(String password) { this.password = password; }
//     }

//     // Response DTOs
//     public static class ErrorResponse {
//         private String error;
//         public ErrorResponse(String error) { this.error = error; }
//         public String getError() { return error; }
//         public void setError(String error) { this.error = error; }
//     }

//     public static class TokenResponse {
//         private String token;
//         public TokenResponse(String token) { this.token = token; }
//         public String getToken() { return token; }
//         public void setToken(String token) { this.token = token; }
//     }

//     // TEMPORARY: Admin signup for testing - REMOVE IN PRODUCTION!
//     public static class SignupRequest {
//         private String email;
//         private String password;

//         public String getEmail() { return email; }
//         public void setEmail(String email) { this.email = email; }
//         public String getPassword() { return password; }
//         public void setPassword(String password) { this.password = password; }
//     }

//     public static class SuccessResponse {
//         private String message;
//         public SuccessResponse(String message) { this.message = message; }
//         public String getMessage() { return message; }
//         public void setMessage(String message) { this.message = message; }
//     }

//     @PostMapping("/signup")
//     public ResponseEntity<?> signup(@RequestBody SignupRequest request) {
//         // Check if admin already exists
//         if (adminRepository.findByEmail(request.getEmail()) != null) {
//             return ResponseEntity.status(HttpStatus.CONFLICT)
//                     .body(new ErrorResponse("Admin already exists"));
//         }

//         Admin admin = new Admin();
//         admin.setEmail(request.getEmail());
//         admin.setPassword(passwordEncoder.encode(request.getPassword()));
//         admin.setSalt("");
        
//         try {
//             adminRepository.save(admin);
//             return ResponseEntity.status(HttpStatus.CREATED)
//                     .body(new SuccessResponse("Admin registered successfully"));
//         } catch (Exception e) {
//             System.err.println("Admin signup error: " + e.getMessage());
//             return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                     .body(new ErrorResponse("Registration failed"));
//         }
//     }

//     @PostMapping("/login")
//     public ResponseEntity<?> login(@RequestBody LoginRequest request) {
//         // Basic validation
//         if (request.getEmail() == null || request.getPassword() == null) {
//             return ResponseEntity.status(HttpStatus.BAD_REQUEST)
//                     .body(new ErrorResponse("Email and password are required"));
//         }

//         Admin dbAdmin = adminRepository.findByEmail(request.getEmail());
        
//         // Generic error message - same as user login
//         if (dbAdmin == null || !passwordEncoder.matches(request.getPassword(), dbAdmin.getPassword())) {
//             return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
//                     .body(new ErrorResponse("Invalid credentials"));
//         }

//         try {
//             String token = jwtUtil.generateToken(dbAdmin.getEmail());
//             return ResponseEntity.ok(new TokenResponse(token));
//         } catch (Exception e) {
//             System.err.println("Token generation error: " + e.getMessage());
//             return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                     .body(new ErrorResponse("Login failed"));
//         }
//     }
// }