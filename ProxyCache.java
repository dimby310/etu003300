package server;

import javax.swing.JTextArea;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class ProxyCache {

    private static Timer inactivityTimer;
    private static final long MAX_CACHE_SIZE_IN_BYTES = 100 * 1024;
    private static final long INACTIVITY_LIMIT_LARGE_CACHE = TimeUnit.HOURS.toMillis(24);
    private static final long INACTIVITY_LIMIT_SMALL_CACHE = TimeUnit.HOURS.toMillis(72);

    public static String getRealName(String request) {
        if (request != null) {
            String[] parts = request.split(" ");
            String result = parts[1].substring(1);
            return result;
        }
        return "";
    }

    public static void listCacheKeys(Map<String, String> cache, JTextArea output) {
        if (cache.isEmpty()) {
            output.append("Le cache est vide.\n");
        } else {
            output.append("Clés dans le cache :\n");
            for (String key : cache.keySet()) {
                output.append(key + "\n");
            }
        }
        resetInactivityTimer(cache, output);
    }

    public static void listCacheElements(Map<String, String> cache, JTextArea output) {
        if (cache.isEmpty()) {
            output.append("Le cache est vide.\n");
        } else {
            output.append("Contenu du cache :\n");
            int index = 1;
            for (Map.Entry<String, String> entry : cache.entrySet()) {
                output.append(index + ". " + getRealName(entry.getKey()) + "\n");
                index++;
            }
        }
        resetInactivityTimer(cache, output);
    }

    public static void clearCache(Map<String, String> cache, JTextArea output) {
        cache.clear();
        output.append("Cache vidé !\n");
    }

    public static void autoClearCache(Map<String, String> cache, JTextArea output) {
        if (inactivityTimer != null) {
            inactivityTimer.cancel();
        }
        inactivityTimer = new Timer(true);

        inactivityTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                long currentCacheSize = calculateCacheSize(cache);
                if (currentCacheSize > MAX_CACHE_SIZE_IN_BYTES) {
                    clearCache(cache, output);
                    output.append("Cache volumineux vidé automatiquement après 24 heures d'inactivité !\n");
                } else if (!cache.isEmpty()) {
                    clearCache(cache, output);
                    output.append("Cache léger vidé automatiquement après 72 heures d'inactivité !\n");
                } else {
                    output.append("Cache déjà vide, aucune suppression nécessaire.\n");
                }
            }
        }, getInactivityLimit(cache));
    }

    private static long getInactivityLimit(Map<String, String> cache) {
        long currentCacheSize = calculateCacheSize(cache);
        if (currentCacheSize > MAX_CACHE_SIZE_IN_BYTES) {
            return INACTIVITY_LIMIT_LARGE_CACHE;
        }
        return INACTIVITY_LIMIT_SMALL_CACHE;
    }

    private static long calculateCacheSize(Map<String, String> cache) {
        long totalSize = 0;
        for (Map.Entry<String, String> entry : cache.entrySet()) {
            totalSize += entry.getKey().getBytes().length;
            totalSize += entry.getValue().getBytes().length;
        }
        return totalSize;
    }

    public static void deleteElementByIndex(Map<String, String> cache, int index, JTextArea output) {
        if (index < 1 || index > cache.size()) {
            output.append("Indice invalide. Veuillez entrer un indice entre 1 et " + cache.size() + "\n");
            return;
        }

        int currentIndex = 1;
        Iterator<Map.Entry<String, String>> iterator = cache.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            if (currentIndex == index) {
                iterator.remove();
                output.append("Élément supprimé : " + entry.getKey() + "\n");
                resetInactivityTimer(cache, output);
                return;
            }
            currentIndex++;
        }
    }

    public static void addToCache(Map<String, String> cache, String key, String value, JTextArea output) {
        cache.put(key, value);
        output.append("Ajouté au cache : " + key + " -> " + value + "\n");
        resetInactivityTimer(cache, output);
    }

    public static void searchCacheByKey(Map<String, String> cache, String key, JTextArea output) {
        if (cache.containsKey(key)) {
            output.append("Trouvé : " + key + " -> " + cache.get(key) + "\n");
        } else {
            output.append("Clé " + key + " non trouvée dans le cache.\n");
        }
        resetInactivityTimer(cache, output);
    }

    public static void searchCacheByName(Map<String, String> cache, String name, JTextArea output) {
        int cnt = 0;
        for (Map.Entry<String, String> entry : cache.entrySet()) {
            if (entry.getKey().contains(name)) {
                output.append("Trouvé : " + name + " -> " + entry.getKey() + "\n");
                cnt++;
            }
        }
        if (cnt == 0) {
            output.append("Clé " + name + " non trouvée dans le cache.\n");
        }
        resetInactivityTimer(cache, output);
    }

    private static void resetInactivityTimer(Map<String, String> cache, JTextArea output) {
        autoClearCache(cache, output);
    }
}
