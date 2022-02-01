package main;

import arc.util.Time;

import java.sql.*;
import java.util.HashMap;


public class DBInterface {
    public Connection conn = null;

    private PreparedStatement preparedStatement = null;

    public void connect(String db, String username, String password){
        // SQLite connection string
        String url = "jdbc:mysql://127.0.0.1:3306/" + db + "?useSSL=false";
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            conn = DriverManager.getConnection(url, username, password);
            System.out.println("Connected to database successfully");
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public boolean hasRow(String table, String key, Object val){
        return hasRow(table, new String[]{key}, new Object[]{val});
    }

    public boolean hasRow(String table, String[] keys, Object[] vals){
        String sql = "SELECT * FROM " + table + " WHERE ";
        for(int i=0; i<keys.length; i++){
            sql += keys[i] + " = ?" + (i < keys.length-1 ? " AND " : "");
        }
        try {
            preparedStatement = conn.prepareStatement(sql);
            for(int i=0; i<vals.length; i++){
                preparedStatement.setObject(i+1, vals[i]);
            }

            ResultSet rs = preparedStatement.executeQuery();
            return rs.last();
        } catch (SQLException e) {
            System.out.println("SQL ERROR: " + e);
        }
        return false;
    }

    public void addEmptyRow(String table, String key, Object val){
        addEmptyRow(table, new String[]{key}, new Object[]{val});
    }

    public void addEmptyRow(String table, String keys[], Object vals[]){
        String sql = "INSERT INTO " + table + " (";
        String keyString = "";
        String valString = "";
        for(int i=0; i<keys.length; i++){
            keyString += keys[i] + (i < keys.length-1 ? ", " : "");
            valString += "? " + (i < keys.length-1 ? ", " : "");
        }
        sql += keyString + ") VALUES(" + valString + ")";
        try {
            preparedStatement = conn.prepareStatement(sql);
            for(int i=0; i<vals.length; i++){
                preparedStatement.setObject(i+1, vals[i]);
            }
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public HashMap<String, Object> loadRow(String table, String key, Object val){
        return loadRow(table, new String[]{key}, new Object[]{val});

    }

    public HashMap<String, Object> loadRow(String table, String keys[], Object vals[]){
        HashMap<String, Object> returnedVals = new HashMap<String, Object>();

        if(!hasRow(table, keys, vals)) addEmptyRow(table, keys, vals);
        String sql = "SELECT * FROM " + table + " WHERE ";
        for(int i=0; i<keys.length; i++){
            sql += keys[i] + " = ?" + (i < keys.length-1 ? " AND " : "");
        }

        try {
            preparedStatement = conn.prepareStatement(sql);
            for(int i=0; i<vals.length; i++){
                preparedStatement.setObject(i+1, vals[i]);
            }
            ResultSet rs = preparedStatement.executeQuery();
            rs.next();
            ResultSetMetaData rsmd = rs.getMetaData();
            for(int i = 1; i <= rsmd.getColumnCount(); i++){ // ONE INDEXED? REALLY?
                returnedVals.put(rsmd.getColumnName(i),rs.getObject(rsmd.getColumnName(i)));
            }
            rs.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return returnedVals;

    }

    public void saveRow(String table, String searchKey, Object serachVal, String key, Object val){
        saveRow(table, new String[]{searchKey}, new Object[]{serachVal}, new String[]{key}, new Object[]{val});
    }

    public void saveRow(String table, String searchKey, Object serachVal, String keys[], Object vals[]){
        saveRow(table, new String[]{searchKey}, new Object[]{serachVal}, keys, vals);
    }

    public void saveRow(String table, String searchKeys[], Object serachVals[], String key, Object val){
        saveRow(table, searchKeys, serachVals, new String[]{key}, new Object[]{val});
    }

    public void saveRow(String table, String searchKeys[], Object searchVals[], String[] keys, Object[] vals){
        String sql = "UPDATE " + table + " SET ";
        for (int i=0; i < keys.length ; i++) {
            sql += keys[i] + " = ?" + (i < keys.length-1 ? ", " : "");
        }
        sql += " WHERE ";
        for(int i=0; i<searchKeys.length; i++){
            sql += searchKeys[i] + " = ?" + (i < searchKeys.length-1 ? " AND " : "");
        }
        try {
            preparedStatement = conn.prepareStatement(sql);
            for (int i=0; i < keys.length; i++){
                preparedStatement.setObject(i+1, vals[i]);
            }
            for (int i=0; i < searchKeys.length; i++){
                preparedStatement.setObject(i+keys.length+1, searchVals[i]);
            }
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }


    }

    public ResultSet customQuery(String query){
        try {
            return conn.prepareStatement(query).executeQuery();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void customUpdate(String update){
        try {
            Statement stmt = conn.createStatement();
            stmt.executeUpdate(update);
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Large scale modifications

    public void setColumn(String table, String col, Object value){
        // Sets an entire column to the provided value
        try {
            String sql = "UPDATE " + table + " SET " + col + " = ?";
            preparedStatement = conn.prepareStatement(sql);
            preparedStatement.setObject(1, value);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}