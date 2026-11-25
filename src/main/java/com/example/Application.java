
package com.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.*;

@SpringBootApplication
@RestController
@CrossOrigin(origins = "*")
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    // ================= DB CONNECTION =================
    private Connection getConnection() throws Exception {

        String host = System.getenv("MYSQLHOST");
        String port = System.getenv("MYSQLPORT");
        String database = System.getenv("MYSQLDATABASE");
        String user = System.getenv("MYSQLUSER");
        String password = System.getenv("MYSQLPASSWORD");

        String url = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?sslMode=REQUIRED";

        return DriverManager.getConnection(url, user, password);
    }

    // ================= TEST DATABASE =================
    @GetMapping("/testdb")
    public String testdb() {

        try (Connection con = getConnection()) {
            return "Database Connected Successfully";

        } catch (Exception e) {
            return "DB ERROR: " + e.getMessage();
        }
    }

    // ================= CREATE DATABASE TABLES =================
    @GetMapping("/setupdb")
    public String setupdb() {

        try (Connection con = getConnection();
             Statement stmt = con.createStatement()) {

            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS account (" +
                "acc_num INT PRIMARY KEY, " +
                "name VARCHAR(100) NOT NULL, " +
                "pin INT NOT NULL, " +
                "balance INT NOT NULL)"
            );

            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS transactions (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "from_acc INT NOT NULL, " +
                "to_acc INT NOT NULL, " +
                "amount INT NOT NULL, " +
                "txn_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
            );

            return "Account and Transactions tables created successfully";

        } catch (Exception e) {
            return "SETUP ERROR: " + e.getMessage();
        }
    }

    // ================= ADMIN LOGIN =================
    @PostMapping("/adminLogin")
    public String adminLogin(
            @RequestParam String username,
            @RequestParam String password) {

        if ("admin".equals(username) &&
            "admin123".equals(password)) {

            return "Admin Login Success";
        }

        return "Invalid Admin Credentials";
    }

    // ================= CUSTOMER LOGIN =================
    @PostMapping("/login")
    public String login(
            @RequestParam int acc_num,
            @RequestParam int pin) {

        String sql =
            "SELECT * FROM account WHERE acc_num=? AND pin=?";

        try (Connection con = getConnection();
             PreparedStatement pst = con.prepareStatement(sql)) {

            pst.setInt(1, acc_num);
            pst.setInt(2, pin);

            try (ResultSet rs = pst.executeQuery()) {

                if (rs.next()) {
                    return "Welcome " + rs.getString("name")
                            + " | Balance: " + rs.getInt("balance");
                }
            }

            return "Invalid Login";

        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    // ================= ADD CUSTOMER =================
    @PostMapping("/addCustomer")
    public String addCustomer(
            @RequestParam int acc_num,
            @RequestParam String name,
            @RequestParam int pin,
            @RequestParam int balance) {

        if (balance < 0 || pin < 0) {
            return "Invalid PIN or Balance";
        }

        String sql =
            "INSERT INTO account (acc_num,name,pin,balance) VALUES(?,?,?,?)";

        try (Connection con = getConnection();
             PreparedStatement pst = con.prepareStatement(sql)) {

            pst.setInt(1, acc_num);
            pst.setString(2, name);
            pst.setInt(3, pin);
            pst.setInt(4, balance);

            pst.executeUpdate();

            return "Customer Added Successfully";

        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    // ================= UPDATE CUSTOMER =================
    @PutMapping("/updateCustomer")
    public String updateCustomer(
            @RequestParam int acc_num,
            @RequestParam String name) {

        String sql =
            "UPDATE account SET name=? WHERE acc_num=?";

        try (Connection con = getConnection();
             PreparedStatement pst = con.prepareStatement(sql)) {

            pst.setString(1, name);
            pst.setInt(2, acc_num);

            int rows = pst.executeUpdate();

            return rows > 0
                    ? "Updated Successfully"
                    : "Account Not Found";

        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    // ================= DELETE CUSTOMER =================
    @DeleteMapping("/deleteCustomer")
    public String deleteCustomer(
            @RequestParam int acc_num) {

        String sql =
            "DELETE FROM account WHERE acc_num=?";

        try (Connection con = getConnection();
             PreparedStatement pst = con.prepareStatement(sql)) {

            pst.setInt(1, acc_num);

            int rows = pst.executeUpdate();

            return rows > 0
                    ? "Deleted Successfully"
                    : "Account Not Found";

        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    // ================= MONEY TRANSFER =================
    @PostMapping("/transfer")
    public String transfer(
            @RequestParam int from_acc,
            @RequestParam int to_acc,
            @RequestParam int amount) {

        if (amount <= 0) {
            return "Invalid Transfer Amount";
        }

        if (from_acc == to_acc) {
            return "Cannot Transfer To The Same Account";
        }

        Connection con = null;

        try {
            con = getConnection();
            con.setAutoCommit(false);

            // Check sender and lock their account row
            String senderSql =
                "SELECT balance FROM account WHERE acc_num=? FOR UPDATE";

            try (PreparedStatement check =
                    con.prepareStatement(senderSql)) {

                check.setInt(1, from_acc);

                try (ResultSet rs = check.executeQuery()) {

                    if (!rs.next()) {
                        con.rollback();
                        return "Sender Not Found";
                    }

                    if (rs.getInt("balance") < amount) {
                        con.rollback();
                        return "Insufficient Balance";
                    }
                }
            }

            // Check receiver
            String receiverSql =
                "SELECT acc_num FROM account WHERE acc_num=?";

            try (PreparedStatement receiver =
                    con.prepareStatement(receiverSql)) {

                receiver.setInt(1, to_acc);

                try (ResultSet rs = receiver.executeQuery()) {

                    if (!rs.next()) {
                        con.rollback();
                        return "Receiver Not Found";
                    }
                }
            }

            // Deduct sender balance
            String deductSql =
                "UPDATE account SET balance=balance-? WHERE acc_num=?";

            try (PreparedStatement deduct =
                    con.prepareStatement(deductSql)) {

                deduct.setInt(1, amount);
                deduct.setInt(2, from_acc);
                deduct.executeUpdate();
            }

            // Add receiver balance
            String addSql =
                "UPDATE account SET balance=balance+? WHERE acc_num=?";

            try (PreparedStatement add =
                    con.prepareStatement(addSql)) {

                add.setInt(1, amount);
                add.setInt(2, to_acc);
                add.executeUpdate();
            }

            // Record transaction
            String logSql =
                "INSERT INTO transactions(from_acc,to_acc,amount) VALUES(?,?,?)";

            try (PreparedStatement log =
                    con.prepareStatement(logSql)) {

                log.setInt(1, from_acc);
                log.setInt(2, to_acc);
                log.setInt(3, amount);
                log.executeUpdate();
            }

            con.commit();

            return "Transfer Successful";

        } catch (Exception e) {

            try {
                if (con != null) {
                    con.rollback();
                }
            } catch (Exception rollbackError) {
                // Rollback failed
            }

            return "ERROR: " + e.getMessage();

        } finally {

            try {
                if (con != null) {
                    con.setAutoCommit(true);
                    con.close();
                }
            } catch (Exception e) {
                // Connection cleanup
            }
        }
    }

    // ================= TRANSACTION HISTORY =================
    @GetMapping("/transactions")
    public String transactions(
            @RequestParam int acc_num) {

        String sql =
            "SELECT * FROM transactions " +
            "WHERE from_acc=? OR to_acc=? ORDER BY id DESC";

        try (Connection con = getConnection();
             PreparedStatement pst = con.prepareStatement(sql)) {

            pst.setInt(1, acc_num);
            pst.setInt(2, acc_num);

            try (ResultSet rs = pst.executeQuery()) {

                StringBuilder sb = new StringBuilder();

                while (rs.next()) {

                    sb.append(rs.getInt("id")).append(",")
                      .append(rs.getInt("from_acc")).append(",")
                      .append(rs.getInt("to_acc")).append(",")
                      .append(rs.getInt("amount")).append(",")
                      .append(rs.getTimestamp("txn_date"))
                      .append(";");
                }

                return sb.length() > 0
                        ? sb.toString()
                        : "No Transactions";
            }

        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }
}
