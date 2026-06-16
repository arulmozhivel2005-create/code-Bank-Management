package com.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.*;

@SpringBootApplication
@RestController
@CrossOrigin
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    // ================= DATABASE CONNECTION =================
    private Connection getConnection() throws Exception {

        String host = System.getenv("MYSQLHOST");
        String port = System.getenv("MYSQLPORT");
        String database = System.getenv("MYSQLDATABASE");
        String user = System.getenv("MYSQLUSER");
        String password = System.getenv("MYSQLPASSWORD");

        String url = "jdbc:mysql://" + host + ":" + port + "/" + database +
                "?useSSL=false&allowPublicKeyRetrieval=true";

        return DriverManager.getConnection(url, user, password);
    }

    // ================= TEST ENDPOINT =================
    @GetMapping("/testdb")
    public String testdb() {
        try {
            getConnection();
            return "Database Connected Successfully";
        } catch (Exception e) {
            return "DB ERROR: " + e.toString();
        }
    }

    // ================= LOGIN =================
    @PostMapping("/login")
    public String login(@RequestParam int acc_num,
                        @RequestParam int pin) {
        try {
            Connection con = getConnection();

            PreparedStatement pst = con.prepareStatement(
                    "SELECT * FROM account WHERE acc_num=? AND pin=?");

            pst.setInt(1, acc_num);
            pst.setInt(2, pin);

            ResultSet res = pst.executeQuery();

            if (res.next()) {
                return "Welcome " + res.getString("name")
                        + " | Balance: " + res.getInt("balance");
            }

            return "Invalid Login";

        } catch (Exception e) {
            return "ERROR: " + e.toString();
        }
    }

    // ================= ADMIN LOGIN =================
    @PostMapping("/adminLogin")
    public String adminLogin(@RequestParam String username,
                             @RequestParam String password) {

        if ("admin".equals(username) && "admin123".equals(password)) {
            return "Admin Login Success";
        }
        return "Invalid Admin";
    }

    // ================= ADD CUSTOMER =================
    @PostMapping("/addCustomer")
    public String addCustomer(@RequestParam int acc_num,
                              @RequestParam String name,
                              @RequestParam int pin,
                              @RequestParam int balance) {
        try {
            Connection con = getConnection();

            PreparedStatement pst = con.prepareStatement(
                    "INSERT INTO account VALUES(?,?,?,?)");

            pst.setInt(1, acc_num);
            pst.setString(2, name);
            pst.setInt(3, pin);
            pst.setInt(4, balance);

            pst.executeUpdate();
            return "Customer Added Successfully";

        } catch (Exception e) {
            return "ERROR: " + e.toString();
        }
    }

    // ================= UPDATE CUSTOMER =================
    @PutMapping("/updateCustomer")
    public String updateCustomer(@RequestParam int acc_num,
                                 @RequestParam String name) {
        try {
            Connection con = getConnection();

            PreparedStatement pst = con.prepareStatement(
                    "UPDATE account SET name=? WHERE acc_num=?");

            pst.setString(1, name);
            pst.setInt(2, acc_num);

            int rows = pst.executeUpdate();

            return rows > 0 ? "Customer Updated Successfully" : "Account Not Found";

        } catch (Exception e) {
            return "ERROR: " + e.toString();
        }
    }

    // ================= DELETE CUSTOMER =================
    @DeleteMapping("/deleteCustomer")
    public String deleteCustomer(@RequestParam int acc_num) {
        try {
            Connection con = getConnection();

            PreparedStatement pst = con.prepareStatement(
                    "DELETE FROM account WHERE acc_num=?");

            pst.setInt(1, acc_num);

            int rows = pst.executeUpdate();

            return rows > 0 ? "Customer Deleted Successfully" : "Account Not Found";

        } catch (Exception e) {
            return "ERROR: " + e.toString();
        }
    }

    // ================= TRANSFER =================
    @PostMapping("/transfer")
    public String transfer(@RequestParam int from_acc,
                            @RequestParam int to_acc,
                            @RequestParam int amount) {
        try {
            Connection con = getConnection();

            PreparedStatement check = con.prepareStatement(
                    "SELECT balance FROM account WHERE acc_num=?");
            check.setInt(1, from_acc);
            ResultSet rs = check.executeQuery();

            if (!rs.next()) return "Sender not found";

            int balance = rs.getInt("balance");
            if (balance < amount) return "Insufficient Balance";

            PreparedStatement deduct = con.prepareStatement(
                    "UPDATE account SET balance=balance-? WHERE acc_num=?");
            deduct.setInt(1, amount);
            deduct.setInt(2, from_acc);
            deduct.executeUpdate();

            PreparedStatement add = con.prepareStatement(
                    "UPDATE account SET balance=balance+? WHERE acc_num=?");
            add.setInt(1, amount);
            add.setInt(2, to_acc);
            add.executeUpdate();

            PreparedStatement log = con.prepareStatement(
                    "INSERT INTO transactions(from_acc,to_acc,amount) VALUES(?,?,?)");
            log.setInt(1, from_acc);
            log.setInt(2, to_acc);
            log.setInt(3, amount);
            log.executeUpdate();

            return "Transfer Successful";

        } catch (Exception e) {
            return "ERROR: " + e.toString();
        }
    }

    // ================= TRANSACTIONS =================
    @GetMapping("/transactions")
    public String transactions(@RequestParam int acc_num) {
        try {
            Connection con = getConnection();

            PreparedStatement pst = con.prepareStatement(
                    "SELECT * FROM transactions WHERE from_acc=? OR to_acc=? ORDER BY txn_date DESC");

            pst.setInt(1, acc_num);
            pst.setInt(2, acc_num);

            ResultSet rs = pst.executeQuery();

            StringBuilder result = new StringBuilder();

            while (rs.next()) {
                result.append(rs.getInt("id")).append(",")
                        .append(rs.getInt("from_acc")).append(",")
                        .append(rs.getInt("to_acc")).append(",")
                        .append(rs.getInt("amount")).append(",")
                        .append(rs.getTimestamp("txn_date")).append(";");
            }

            return result.length() > 0 ? result.toString() : "No Transactions";

        } catch (Exception e) {
            return "ERROR: " + e.toString();
        }
    }
}
