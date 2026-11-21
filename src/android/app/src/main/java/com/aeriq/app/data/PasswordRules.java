package com.aeriq.app.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Requisitos de la contraseña nueva (T33): al menos 8 caracteres, una mayúscula, una minúscula, un número y un
 * símbolo. Son los mismos que comprueba el servidor (auth_password_missing en AuthCore.php); aquí solo sirven para
 * avisar mientras se escribe, porque el que decide es siempre el servidor.
 */
public final class PasswordRules {
    public static final String LENGTH = "al menos 8 caracteres", UPPER = "una mayúscula", LOWER = "una minúscula",
        DIGIT = "un número", SYMBOL = "un símbolo (por ejemplo ! ? # @ . -)";
    public static final List<String> ALL = Collections.unmodifiableList(java.util.Arrays.asList(LENGTH, UPPER, LOWER, DIGIT, SYMBOL));

    private PasswordRules() {}

    /** Lo que le falta a la contraseña, en el orden de {@link #ALL}; vacío si cumple todo. */
    public static List<String> missing(String password) {
        String value = password == null ? "" : password;
        boolean upper = false, lower = false, digit = false, symbol = false;
        int length = value.codePointCount(0, value.length());
        for (int i = 0; i < value.length(); ) {
            int c = value.codePointAt(i);
            i += Character.charCount(c);
            if (Character.isUpperCase(c)) upper = true;
            else if (Character.isLowerCase(c)) lower = true;
            else if (Character.getType(c) == Character.DECIMAL_DIGIT_NUMBER) digit = true;
            else if (!Character.isLetter(c) && !Character.isWhitespace(c)) symbol = true;
        }
        List<String> out = new ArrayList<>();
        if (length < 8) out.add(LENGTH);
        if (!upper) out.add(UPPER);
        if (!lower) out.add(LOWER);
        if (!digit) out.add(DIGIT);
        if (!symbol) out.add(SYMBOL);
        return out;
    }

    /** Frase para avisar de lo que falta: «Le falta: una mayúscula y un símbolo…». Vacía si cumple. */
    public static String problem(String password) {
        List<String> missing = missing(password);
        if (missing.isEmpty()) return "";
        String last = missing.remove(missing.size() - 1);
        return "La contraseña no es segura. Le falta: " + (missing.isEmpty() ? last : String.join(", ", missing) + " y " + last) + ".";
    }
}
