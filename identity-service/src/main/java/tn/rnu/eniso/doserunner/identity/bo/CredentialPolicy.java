package tn.rnu.eniso.doserunner.identity.bo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Rules for usernames and passwords. Pure Java: no Spring, no JPA, so it is unit-tested in isolation.
 */
public class CredentialPolicy {

    public static final int MIN_PASSWORD_LENGTH = 8;
    public static final int MAX_PASSWORD_LENGTH = 72; // BCrypt ignores bytes beyond 72
    private static final Pattern USERNAME = Pattern.compile("[a-z0-9][a-z0-9._-]{2,31}");

    /** Lower-cased, trimmed form under which usernames are stored and looked up. */
    public static String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    /** Every rule the pair breaks; empty means acceptable. */
    public List<String> violations(String username, String password) {
        List<String> problems = new ArrayList<>();
        String user = normalizeUsername(username);
        if (!USERNAME.matcher(user).matches()) {
            problems.add("username must be 3-32 characters: lowercase letters, digits, '.', '_' or '-'");
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            problems.add("password must be at least " + MIN_PASSWORD_LENGTH + " characters");
            return problems;
        }
        if (password.length() > MAX_PASSWORD_LENGTH) {
            problems.add("password must be at most " + MAX_PASSWORD_LENGTH + " characters");
        }
        if (password.chars().noneMatch(Character::isLetter) || password.chars().noneMatch(Character::isDigit)) {
            problems.add("password must contain letters and digits");
        }
        if (!user.isEmpty() && password.toLowerCase(Locale.ROOT).contains(user)) {
            problems.add("password must not contain the username");
        }
        return problems;
    }

    /** @throws IllegalArgumentException listing every broken rule (the REST layer turns it into a 400) */
    public void check(String username, String password) {
        List<String> problems = violations(username, password);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", problems));
        }
    }
}
