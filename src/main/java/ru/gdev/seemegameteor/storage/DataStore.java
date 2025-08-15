package ru.gdev.seemegameteor.storage;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public interface DataStore {
    void init(Consumer<Boolean> callback);
    void close();
    void loadLoot(Consumer<List<Map<String, Object>>> callback);
    void saveLoot(List<Map<String, Object>> raw, Consumer<Boolean> callback);
    void convertTo(DataStore newStore, Consumer<Boolean> callback);
    void backup(Consumer<Boolean> callback);
}
