package me.ellbristow.SimpleSpawn;

import java.io.File;
import java.sql.*;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SQLBridge {

    private static SimpleSpawn plugin;
    private Connection conn;
    private File sqlFile;
    private Statement statement;
    private HashMap<Integer, HashMap<String, Object>> rows = new HashMap<Integer, HashMap<String, Object>>();
    private int numRows = 0;
    
    public SQLBridge (SimpleSpawn instance) {
        plugin = instance;
        sqlFile = new File(plugin.getDataFolder() + File.separator + plugin.getName() + ".db");
    }
    
    public Connection getConnection() {
        if (conn == null) {
            return open();
        }
        return conn;
    }
    
    public Connection open() {
        try {
            conn = DriverManager.getConnection("jdbc:sqlite:" + sqlFile.getAbsolutePath());
            return conn;
        } catch (Exception e) {
            plugin.getLogger().severe(e.getMessage() + e.getStackTrace()[0].getLineNumber());
        }
        return null;
    }
    
    public void close() {
        if (conn !=null) {
            try {
                conn.close();
            } catch (Exception e) {
                plugin.getLogger().severe(e.getMessage() + e.getStackTrace()[0].getLineNumber());
            }
        }
    }
    
    public boolean checkTable(String tableName) {
        DatabaseMetaData dbm = null;
        ResultSet tables = null;
        try {
            dbm = this.open().getMetaData();
            tables = dbm.getTables(null, null, tableName, null);
            if (tables.next())
                return true;
            else
                return false;
        } catch (Exception e) {
            plugin.getLogger().severe(e.getMessage() + e.getStackTrace()[0].getLineNumber());
            return false;
        } finally {
            try {
                tables.close();
            } catch (SQLException e) {
                plugin.getLogger().severe(e.getMessage() + e.getStackTrace()[0].getLineNumber());
            }
        }
    }
    
    public boolean createTable(String tableName, String[] columns, String[] dims) {
        try {
            statement = conn.createStatement();
            String query = "CREATE TABLE " + tableName + "(";
            for (int i = 0; i < columns.length; i++) {
                if (i!=0) {
                    query += ",";
                }
                query += columns[i] + " " + dims[i];
            }
            query += ")";
            statement.execute(query);
        } catch (Exception e) {
            plugin.getLogger().severe(e.getMessage() + e.getStackTrace()[0].getLineNumber());
        } finally {
            try {
                statement.close();
            } catch (SQLException e) {
                plugin.getLogger().severe(e.getMessage() + e.getStackTrace()[0].getLineNumber());
            }
        }
        return true;
    }
    
    public ResultSet query(String query) {
        ResultSet results = null;
        try {
            statement = conn.createStatement();
            results = statement.executeQuery(query);
            return results;
        } catch (Exception e) {
            if (!e.getMessage().contains("not return ResultSet") || (e.getMessage().contains("not return ResultSet") && query.startsWith("SELECT"))) {
                plugin.getLogger().severe(e.getMessage() + e.getStackTrace()[0].getLineNumber());
            }
        } finally {
            try {
                statement.close();
            } catch (Exception e) {
                plugin.getLogger().severe(e.getMessage() + e.getStackTrace()[0].getLineNumber());
            }
        }
        return null;
    }
    
    public HashMap<Integer, HashMap<String, Object>> select(String fields, String tableName, String where, String group, String order) {
        if ("".equals(fields) || fields == null) {
            fields = "*";
        }
        String query = "SELECT " + fields + " FROM " + tableName;
        ResultSet results = null;
        try {
            statement = conn.createStatement();
            if (!"".equals(where) && where != null) {
                query += " WHERE " + where;
            }
            if (!"".equals(group) && group != null) {
                query += " GROUP BY " + group;
            }
            if (!"".equals(order) && order != null) {
                query += " ORDER BY " + order;
            }
            rows.clear();
            numRows = 0;
            results = statement.executeQuery(query);
            if (results != null) {
                int columns = results.getMetaData().getColumnCount();
                String columnNames = "";
                for (int i = 1; i <= columns; i++) {
                    if (!"".equals(columnNames)) {
                        columnNames += ",";
                    }
                    columnNames += results.getMetaData().getColumnName(i);
                }
                String[] columnArray = columnNames.split(",");
                numRows = 0;
                while (results.next()) {
                    HashMap<String, Object> thisColumn = new HashMap<String, Object>();
                    for (String columnName : columnArray) {
                        thisColumn.put(columnName, results.getObject(columnName));
                    }
                    rows.put(numRows, thisColumn);
                    numRows++;
                }
                return rows;
            } else {
                return null;
            }
        } catch (Exception e) {
            plugin.getLogger().severe(e.getMessage() + e.getStackTrace()[0].getLineNumber());
        } finally {
            try {
                statement.close();
            } catch (Exception e) {
                plugin.getLogger().severe(e.getMessage() + e.getStackTrace()[0].getLineNumber());
            }
        }
        return null;
    }
    
}
