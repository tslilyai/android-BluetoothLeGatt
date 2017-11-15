package org.mpisws.sddrservice.dbplatform;

/**
 *
 * @author verdelyi
 */
public class DBColumn {
    private final String name;
    private final String type;

    public DBColumn(String name, String type) {
        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    @Override
    public String toString() {
        return name + " " + type;
    }
}
