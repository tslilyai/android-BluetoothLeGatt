package org.mpi_sws.sddr_service.lib;

import java.util.HashMap;
import java.util.Map;

public class EnumConverter<T> {

    private final Map<T, Integer> enumToIntMap = new HashMap<T, Integer>();
    private final Map<Integer, T> intToEnumMap = new HashMap<Integer, T>();
    
    public void addMapping(final T t, final int i) {
        enumToIntMap.put(t, i);
        intToEnumMap.put(i, t);
    }
    
    public T fromInt(final int i) {
        return intToEnumMap.get(i);
    }
    
    public int toInt(final T t) {
        return enumToIntMap.get(t);
    }
}
