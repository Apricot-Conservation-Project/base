package base;

import arc.util.Log;

import java.sql.*;
import java.util.HashMap;
import java.util.stream.Stream;
import java.util.Arrays;

public class DBInterface {
    public Connection conn = null;

    public void connect(String db, String username, String password) {
        String url = "jdbc:mariadb://127.0.0.1:3306/" + db + "?useSSL=false";
        try {
            Class.forName("org.mariadb.jdbc.Driver");
            conn = DriverManager.getConnection(url, username, password);
            Log.info("Connected to database successfully");
        } catch (SQLException | ClassNotFoundException e) {
            Log.err(e);
        }
    }

    public boolean hasRow(String table, String key, Object val) {
        return hasRow(table, new String[] { key }, new Object[] { val });
    }

    public boolean hasRow(String table, String[] keys, Object[] vals) {
        String sql = "SELECT * FROM " + table + " WHERE ";
        for (int i = 0; i < keys.length; i++) {
            sql += keys[i] + " = ?" + (i < keys.length - 1 ? " AND " : "");
        }
        try {
            var preparedStatement = conn.prepareStatement(sql);
            for (int i = 0; i < vals.length; i++) {
                preparedStatement.setObject(i + 1, vals[i]);
            }

            ResultSet rs = preparedStatement.executeQuery();
            return rs.last();
        } catch (SQLException e) {
            Log.err(e);
        }
        return false;
    }

    public void addEmptyRow(String table, String key, Object val) {
        addEmptyRow(table, new String[] { key }, new Object[] { val });
    }

    public void addEmptyRow(String table, String keys[], Object vals[]) {
        String sql = "INSERT INTO " + table + " (";
        String keyString = "";
        String valString = "";
        for (int i = 0; i < keys.length; i++) {
            keyString += keys[i] + (i < keys.length - 1 ? ", " : "");
            valString += "? " + (i < keys.length - 1 ? ", " : "");
        }
        sql += keyString + ") VALUES(" + valString + ")";
        try {
            var preparedStatement = conn.prepareStatement(sql);
            for (int i = 0; i < vals.length; i++) {
                preparedStatement.setObject(i + 1, vals[i]);
            }
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            Log.err(e);
        }
    }

    public HashMap<String, Object> loadRow(String table, String key, Object val) {
        return loadRow(table, new String[] { key }, new Object[] { val });

    }

    public HashMap<String, Object> loadRow(String table, String keys[], Object vals[]) {
        HashMap<String, Object> returnedVals = new HashMap<String, Object>();

        if (!hasRow(table, keys, vals))
            addEmptyRow(table, keys, vals);
        String sql = "SELECT * FROM " + table + " WHERE ";
        for (int i = 0; i < keys.length; i++) {
            sql += keys[i] + " = ?" + (i < keys.length - 1 ? " AND " : "");
        }
        Log.debug(sql);

        try {
            var preparedStatement = conn.prepareStatement(sql);
            for (int i = 0; i < vals.length; i++) {
                preparedStatement.setObject(i + 1, vals[i]);
            }
            ResultSet rs = preparedStatement.executeQuery();
            rs.next();
            ResultSetMetaData rsmd = rs.getMetaData();
            for (int i = 1; i <= rsmd.getColumnCount(); i++) { // ONE INDEXED? REALLY?
                returnedVals.put(rsmd.getColumnName(i), rs.getObject(rsmd.getColumnName(i)));
            }
            rs.close();
        } catch (SQLException e) {
            Log.err(e);
        }
        Log.debug(returnedVals);

        return returnedVals;

    }

    public void saveRow(String table, String searchKey, Object searchVal, String key, Object val) {
        saveRow(table, new String[] { searchKey }, new Object[] { searchVal }, new String[] { key },
                new Object[] { val });
    }

    public void saveRow(String table, String searchKey, Object searchVal, String keys[], Object vals[]) {
        saveRow(table, new String[] { searchKey }, new Object[] { searchVal }, keys, vals);
    }

    public void saveRow(String table, String searchKeys[], Object searchVals[], String key, Object val) {
        saveRow(table, searchKeys, searchVals, new String[] { key }, new Object[] { val });
    }

    public void saveRow(String table, String searchKeys[], Object searchVals[], String[] keys, Object[] vals) {
        String sql = "UPDATE " + table + " SET ";
        for (int i = 0; i < keys.length; i++) {
            sql += keys[i] + " = ?" + (i < keys.length - 1 ? ", " : "");
        }
        sql += " WHERE ";
        for (int i = 0; i < searchKeys.length; i++) {
            sql += searchKeys[i] + " = ?" + (i < searchKeys.length - 1 ? " AND " : "");
        }
        customUpdate(sql, Stream.concat(Arrays.stream(vals), Arrays.stream(searchVals)).toArray(Object[]::new));

    }

    public ResultSet customQuery(String query) {
        try {
            return conn.prepareStatement(query).executeQuery();
        } catch (SQLException e) {
            Log.err(e);
        }
        return null;
    }

    public void customUpdate(String update) {
        try {
            Statement stmt = conn.createStatement();
            stmt.executeUpdate(update);
            stmt.close();
        } catch (SQLException e) {
            Log.err(e);
        }
    }

    public void customUpdate(String sql, Object[] vals) {
        try {
            var stmt = conn.prepareStatement(sql);
            for (int i = 0; i < vals.length; i++) {
                stmt.setObject(i + 1, vals[i]);
            }
            stmt.executeUpdate();
            stmt.close();

        } catch (SQLException e) {
            Log.err(e);
        }
    }

    // Large scale modifications

    public void setColumn(String table, String col, Object value) {
        // Sets an entire column to the provided value
        try {
            String sql = "UPDATE " + table + " SET " + col + " = ?";
            var preparedStatement = conn.prepareStatement(sql);
            preparedStatement.setObject(1, value);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            Log.err(e);
        }
    }
}
