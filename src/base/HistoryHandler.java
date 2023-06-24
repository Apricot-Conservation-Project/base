package base;

import java.time.Instant;
import java.util.*;

public class HistoryHandler {

    private HashMap<Tuple<Integer, Integer>, List<Tuple<Long, String>>> history = new HashMap<>();

    public void addEntry(int x, int y, String info) {
        Tuple<Integer, Integer> key = new Tuple<>(x, y);
        Tuple<Long, String> newEntry = new Tuple<>(Instant.now().getEpochSecond(), info);
        if (history.containsKey(key)) {
            List<Tuple<Long, String>> entry = history.get(key);
            if (entry.size() > 10) {
                entry.remove(0);
            }
            entry.add(newEntry);
        } else {
            history.put(new Tuple<Integer, Integer>(x, y),
                    new ArrayList<>(Collections.singletonList(newEntry)));
        }
    }

    public List<Tuple<Long, String>> getHistory(int x, int y) {
        Tuple<Integer, Integer> key = new Tuple<>(x, y);
        if (!history.containsKey(key)) {
            return null;
        }
        return history.get(key);
    }

    public void clear() {
        history.clear();
    }

}
