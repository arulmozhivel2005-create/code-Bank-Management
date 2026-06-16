package com.example;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
@CrossOrigin
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
        private Connection getConnection() throws Exception {

    String host = System.getenv("MYSQLHOST");
    String port = System.getenv("MYSQLPORT");
    String database = System.getenv("MYSQLDATABASE");
    String user = System.getenv("MYSQLUSER");
    String password = System.getenv("MYSQLPASSWORD");

    String url =
        "jdbc:mysql://" + host + ":" + port + "/" + database +
        "?useSSL=false&allowPublicKeyRetrieval=true";

    return DriverManager.getConnection(url, user, password);
}
    }

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
                    + " | Balance: "
                    + res.getInt("balance");
        }

        return "Invalid Login";

    } catch (Exception e) {
        return e.getMessage();
    }
}

    @PostMapping("/adminLogin")
    public String adminLogin(@RequestParam String username,
                            @RequestParam String password) {
        if (username.equals("admin") && password.equals("admin123")) {
            return "Admin Login Success";
        } else {
            return "Invalid Admin";
        }
    }

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
            return e.getMessage();
        }
    }

    // UPDATE CUSTOMER
@PutMapping("/updateCustomer")
public String updateCustomer(@RequestParam int acc_num,
                             @RequestParam String name) {
    try {
        Connection con = getConnection();

        // Check if account exists first
        PreparedStatement check = con.prepareStatement(
            "SELECT * FROM account WHERE acc_num=?");
        check.setInt(1, acc_num);
        ResultSet rs = check.executeQuery();

        if (!rs.next()) {
            return "Account Not Found";
        }

        PreparedStatement pst = con.prepareStatement(
            "UPDATE account SET name=? WHERE acc_num=?");
        pst.setString(1, name);
        pst.setInt(2, acc_num);
        pst.executeUpdate();
        return "Customer Updated Successfully";

    } catch (Exception e) { return e.getMessage(); }
}



// DELETE CUSTOMER
@DeleteMapping("/deleteCustomer")
public String deleteCustomer(@RequestParam int acc_num) {
    try {
       Connection con = getConnection();

        // Check if account exists first
        PreparedStatement check = con.prepareStatement(
            "SELECT * FROM account WHERE acc_num=?");
        check.setInt(1, acc_num);
        ResultSet rs = check.executeQuery();

        if (!rs.next()) {
            return "Account Not Found";
        }

        PreparedStatement pst = con.prepareStatement(
            "DELETE FROM account WHERE acc_num=?");
        pst.setInt(1, acc_num);
        pst.executeUpdate();
        return "Customer Deleted Successfully";

    } catch (Exception e) { return e.getMessage(); }
}

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
            if (!rs.next()) return "Sender account not found";
            int balance = rs.getInt("balance");
            if (balance < amount) return "Insufficient Balance";

            PreparedStatement checkTo = con.prepareStatement(
                "SELECT * FROM account WHERE acc_num=?");
            checkTo.setInt(1, to_acc);
            ResultSet rsTo = checkTo.executeQuery();
            if (!rsTo.next()) return "Receiver account not found";

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
            return e.getMessage();
        }
    }

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
            return e.getMessage();
        }
    }
}
