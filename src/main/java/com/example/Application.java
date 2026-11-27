
package com.example;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.bson.Document;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import org.springframework.web.bind.annotation.*;

@SpringBootApplication
@RestController
@CrossOrigin(origins = "*")
public class Application {

    private final MongoTemplate mongoTemplate;
    private final TransactionTemplate transactionTemplate;

    public Application(
            MongoTemplate mongoTemplate,
            PlatformTransactionManager transactionManager) {

        this.mongoTemplate = mongoTemplate;
        this.transactionTemplate =
                new TransactionTemplate(transactionManager);
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    // ================= MONGODB TRANSACTION CONFIGURATION =================

    @Bean
    public static MongoTransactionManager transactionManager(
            MongoDatabaseFactory dbFactory) {

        return new MongoTransactionManager(dbFactory);
    }

    // ================= ACCOUNT DOCUMENT =================

    @org.springframework.data.mongodb.core.mapping.Document(
            collection = "account")
    public static class Account {

        @Id
        public Integer acc_num;

        public String name;
        public int pin;
        public double balance;

        public Account() {
        }

        public Account(
                int acc_num,
                String name,
                int pin,
                double balance) {

            this.acc_num = acc_num;
            this.name = name;
            this.pin = pin;
            this.balance = balance;
        }
    }

    // ================= TRANSACTION DOCUMENT =================

    @org.springframework.data.mongodb.core.mapping.Document(
            collection = "transactions")
    public static class BankTransaction {

        @Id
        public String id;

        @Field("from_acc")
        public int from_acc;

        @Field("to_acc")
        public int to_acc;

        public double amount;

        public Date txn_date;

        public BankTransaction() {
        }

        public BankTransaction(
                int from_acc,
                int to_acc,
                double amount) {

            this.from_acc = from_acc;
            this.to_acc = to_acc;
            this.amount = amount;
            this.txn_date = new Date();
        }
    }

    // ================= TEST DATABASE =================

    @GetMapping("/testdb")
    public String testdb() {

        try {
            mongoTemplate.getDb().runCommand(
                    new Document("ping", 1));

            return "MongoDB Connected Successfully";

        } catch (Exception e) {
            return "DB ERROR: " + e.getMessage();
        }
    }

    // ================= CREATE DATABASE COLLECTIONS =================

    @GetMapping("/setupdb")
    public String setupdb() {

        try {

            if (!mongoTemplate.collectionExists("account")) {
                mongoTemplate.createCollection("account");
            }

            if (!mongoTemplate.collectionExists("transactions")) {
                mongoTemplate.createCollection("transactions");
            }

            return "Account and Transactions collections created successfully";

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

        try {

            Query query = new Query(
                    Criteria.where("_id").is(acc_num)
                            .and("pin").is(pin));

            Account account = mongoTemplate.findOne(
                    query, Account.class);

            if (account != null) {

                return "Welcome " + account.name
                        + " | Balance: " + account.balance;
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
            @RequestParam double balance) {

        if (balance < 0 || pin < 0) {
            return "Invalid PIN or Balance";
        }

        try {

            Account account = new Account(
                    acc_num, name, pin, balance);

            mongoTemplate.insert(account);

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

        try {

            Query query = new Query(
                    Criteria.where("_id").is(acc_num));

            Update update = new Update()
                    .set("name", name);

            var result = mongoTemplate.updateFirst(
                    query, update, Account.class);

            return result.getMatchedCount() > 0
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

        try {

            Query query = new Query(
                    Criteria.where("_id").is(acc_num));

            var result = mongoTemplate.remove(
                    query, Account.class);

            return result.getDeletedCount() > 0
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
            @RequestParam double amount) {

        if (!Double.isFinite(amount) || amount <= 0) {
            return "Invalid Transfer Amount";
        }

        if (from_acc == to_acc) {
            return "Cannot Transfer To The Same Account";
        }

        try {

            return transactionTemplate.execute(status -> {

                // Check sender
                Account sender = mongoTemplate.findById(
                        from_acc, Account.class);

                if (sender == null) {
                    return "Sender Not Found";
                }

                // Check receiver
                Account receiver = mongoTemplate.findById(
                        to_acc, Account.class);

                if (receiver == null) {
                    return "Receiver Not Found";
                }

                // Deduct sender balance only if sufficient
                Query senderQuery = new Query(
                        Criteria.where("_id").is(from_acc)
                                .and("balance").gte(amount));

                Update deduct = new Update()
                        .inc("balance", -amount);

                var senderResult = mongoTemplate.updateFirst(
                        senderQuery, deduct, Account.class);

                if (senderResult.getModifiedCount() == 0) {
                    return "Insufficient Balance";
                }

                // Add receiver balance
                Query receiverQuery = new Query(
                        Criteria.where("_id").is(to_acc));

                Update add = new Update()
                        .inc("balance", amount);

                mongoTemplate.updateFirst(
                        receiverQuery, add, Account.class);

                // Record transaction
                BankTransaction transaction =
                        new BankTransaction(
                                from_acc, to_acc, amount);

                mongoTemplate.insert(transaction);

                return "Transfer Successful";
            });

        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    // ================= TRANSACTION HISTORY =================

    @GetMapping("/transactions")
    public String transactions(
            @RequestParam int acc_num) {

        try {

            Query query = new Query(
                    new Criteria().orOperator(
                            Criteria.where("from_acc").is(acc_num),
                            Criteria.where("to_acc").is(acc_num)
                    )
            );

            query.with(
                    org.springframework.data.domain.Sort.by(
                            org.springframework.data.domain.Sort.Direction.DESC,
                            "txn_date"
                    )
            );

            List<BankTransaction> list =
                    mongoTemplate.find(
                            query,
                            BankTransaction.class
                    );

            StringBuilder sb = new StringBuilder();

            SimpleDateFormat formatter =
                    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss'.0'");

            for (BankTransaction txn : list) {

                sb.append(txn.id).append(",")
                        .append(txn.from_acc).append(",")
                        .append(txn.to_acc).append(",")
                        .append(txn.amount).append(",")
                        .append(formatter.format(txn.txn_date))
                        .append(";");
            }

            return sb.length() > 0
                    ? sb.toString()
                    : "No Transactions";

        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }
}
