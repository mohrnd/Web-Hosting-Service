package com.authapi.webhostingservice.repository;

import com.authapi.webhostingservice.model.Admin;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;

@Repository
public class AdminRepository {

    private final JdbcTemplate jdbcTemplate;

    public AdminRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Admin> rowMapper = new RowMapper<>() {
        @Override
        public Admin mapRow(ResultSet rs, int rowNum) throws SQLException {
            Admin admin = new Admin();
            admin.setId(rs.getInt("id"));
            admin.setEmail(rs.getString("email"));
            admin.setPassword(rs.getString("password"));
            admin.setSalt(rs.getString("salt"));
            return admin;
        }
    };

    public Admin findByEmail(String email) {
        String sql = "SELECT * FROM admins WHERE email = ?";
        try {
            return jdbcTemplate.queryForObject(sql, rowMapper, email);
        } catch (Exception e) {
            return null; // admin not found
        }
    }

    public void save(Admin admin) {
        String sql = "INSERT INTO admins (email, password, salt) VALUES (?, ?, ?)";
        jdbcTemplate.update(sql, admin.getEmail(), admin.getPassword(), admin.getSalt());
    }
}
