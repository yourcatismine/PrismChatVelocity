package h2ph.util;

import com.velocitypowered.api.event.player.PlayerChatEvent;

import java.lang.reflect.Method;
import java.util.Optional;

public final class ChatEventSignUtil {

    private ChatEventSignUtil() {
    }

    public static boolean isSigned(PlayerChatEvent event) {
        if (event == null) {
            return true;
        }

        Boolean isSigned = invokeBoolean(event, "isSigned");
        if (isSigned != null) {
            return isSigned;
        }

        Object signedMsg = invoke(event, "getSignedMessage");
        if (isOptionalPresent(signedMsg)) {
            return true;
        }

        Object signature = invoke(event, "getMessageSignature");
        if (isOptionalPresent(signature)) {
            return true;
        }
        if (signature != null) {
            return true;
        }

        Object legacySignature = invoke(event, "getSignature");
        if (isOptionalPresent(legacySignature)) {
            return true;
        }
        if (legacySignature != null) {
            return true;
        }

        Boolean heuristic = scanForSigned(event);
        if (heuristic != null) {
            return heuristic;
        }

        // If we cannot determine, assume signed to avoid cancel/modify warnings.
        return true;
    }

    private static Boolean invokeBoolean(PlayerChatEvent event, String name) {
        Object result = invoke(event, name);
        if (result instanceof Boolean) {
            return (Boolean) result;
        }
        return null;
    }

    private static Object invoke(PlayerChatEvent event, String name) {
        try {
            Method method = event.getClass().getMethod(name);
            return method.invoke(event);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isOptionalPresent(Object value) {
        if (value instanceof Optional) {
            return ((Optional<?>) value).isPresent();
        }
        return false;
    }

    private static Boolean scanForSigned(PlayerChatEvent event) {
        try {
            for (Method method : event.getClass().getMethods()) {
                String name = method.getName().toLowerCase();
                if (!name.contains("signed") && !name.contains("signature")) {
                    continue;
                }
                if (method.getParameterCount() != 0) {
                    continue;
                }
                Object result = method.invoke(event);
                if (result instanceof Boolean) {
                    return (Boolean) result;
                }
                if (result instanceof Optional) {
                    return ((Optional<?>) result).isPresent();
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }
}
