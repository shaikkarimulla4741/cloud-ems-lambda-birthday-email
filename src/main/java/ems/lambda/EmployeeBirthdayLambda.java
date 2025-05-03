package ems.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder;
import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest;
import com.amazonaws.services.secretsmanager.model.GetSecretValueResult;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder;
import com.amazonaws.services.simpleemail.model.*;
import com.google.gson.Gson;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

public class EmployeeBirthdayLambda implements RequestHandler<Object, String> {

    private static final AWSSecretsManager secretsManager = AWSSecretsManagerClientBuilder.defaultClient();

    // ✅ Use environment variables
    private static final String SECRET_NAME = System.getenv("SECRET_NAME");
    private static final String SENDER_EMAIL = System.getenv("SENDER_EMAIL");

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    @Override
    public String handleRequest(Object input, Context context) {
        List<String> messages = new ArrayList<>();
        try {
            Map<String, String> credentials = getRDSCredentials(context);
            Class.forName("com.mysql.cj.jdbc.Driver");

            String dbUrl = String.format("jdbc:mysql://%s:3306/%s",
                    credentials.get("host"),
                    credentials.get("dbname"));

            try (Connection connection = DriverManager.getConnection(
                    dbUrl,
                    credentials.get("username"),
                    credentials.get("password"))) {

                resetYearlyBirthdayFlags(connection, context);
                messages.addAll(checkBirthdays(connection, LocalDate.now().toString(), context));
            }
        } catch (Exception e) {
            logError(context, "Birthday processing failed", e);
            return "Failed to process birthdays: " + e.getMessage();
        }
        return "Successfully processed birthdays";
    }

    private Map<String, String> getRDSCredentials(Context context) {
        try {
            GetSecretValueResult result = secretsManager.getSecretValue(
                    new GetSecretValueRequest().withSecretId(SECRET_NAME));
            return new Gson().fromJson(result.getSecretString(), Map.class);
        } catch (Exception e) {
            logError(context, "Failed to retrieve secrets", e);
            throw new RuntimeException("Secrets retrieval failed", e);
        }
    }

    private List<String> checkBirthdays(Connection connection, String todayStr, Context context) throws SQLException {
        List<String> messages = new ArrayList<>();
        String query = "SELECT name, email FROM employees WHERE DATE_FORMAT(birthday, '%m-%d') = DATE_FORMAT(?, '%m-%d') AND birthday_email_sent = false";

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, todayStr);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String email = rs.getString("email");
                if (isValidEmail(email)) {
                    logWarning(context, "Invalid birthday email skipped: " + email);
                    continue;
                }

                String message = String.format("Happy Birthday %s! 🎉", rs.getString("name"));
                sendEmail(email, "Happy Birthday!", message, context);
                updateBirthdayEmailStatus(connection, email);
                messages.add("Birthday sent to: " + email);
            }
        }
        return messages;
    }

    private void resetYearlyBirthdayFlags(Connection connection, Context context) throws SQLException {
        LocalDate today = LocalDate.now();
        if (today.getMonthValue() == 1 && today.getDayOfMonth() == 1) {
            String resetQuery = "UPDATE employees SET birthday_email_sent = false";
            try (PreparedStatement stmt = connection.prepareStatement(resetQuery)) {
                int updatedCount = stmt.executeUpdate();
                context.getLogger().log("Reset birthday flags for " + updatedCount + " employees");
            }
        }
    }

    private void updateBirthdayEmailStatus(Connection connection, String email) throws SQLException {
        String query = "UPDATE employees SET birthday_email_sent = true WHERE email = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, email);
            stmt.executeUpdate();
        }
    }

    private void sendEmail(String recipientEmail, String subject, String body, Context context) {
        try {
            if (isValidEmail(recipientEmail)) {
                throw new IllegalArgumentException("Invalid recipient email: " + recipientEmail);
            }

            AmazonSimpleEmailService ses = AmazonSimpleEmailServiceClientBuilder.defaultClient();
            ses.sendEmail(new SendEmailRequest()
                    .withSource(SENDER_EMAIL)
                    .withDestination(new Destination().withToAddresses(recipientEmail))
                    .withMessage(new Message()
                            .withSubject(new Content().withCharset("UTF-8").withData(subject))
                            .withBody(new Body().withText(new Content().withCharset("UTF-8").withData(body)))));

            context.getLogger().log("Email sent to: " + recipientEmail);
        } catch (Exception e) {
            logError(context, "Email failed to " + recipientEmail, e);
            throw new RuntimeException("Email send failed", e);
        }
    }

    private boolean isValidEmail(String email) {
        return email == null || !EMAIL_PATTERN.matcher(email).matches();
    }

    private void logError(Context context, String message, Exception e) {
        context.getLogger().log("ERROR: " + message + " - " + e.getMessage());
    }

    private void logWarning(Context context, String message) {
        context.getLogger().log("WARNING: " + message);
    }
}