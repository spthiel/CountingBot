package me.database;

import com.mysql.cj.jdbc.result.ResultSetFactory;

import java.sql.*;
import java.util.LinkedList;
import java.util.List;

import me.main.Config;

public class Database {
	
	private static Database   instance = new Database();
	
	public static Database getInstance() {
		return instance;
	}
	
	private Database() {
		
		if (Config.instance.DEV) {
			return;
		}
		
		this.execute("CREATE TABLE IF NOT EXISTS channels (channelid bigint PRIMARY KEY, count bigint, secret varchar(255), webhookid bigint)");
			
	}
	
	private Connection createConnection() {
		Config config = Config.instance;
		if (config.DEV) {
			return null;
		}
		try {
			return DriverManager.getConnection("jdbc:mysql://" + config.DBHOST + ":" + config.DBPORT + "/" + config.DBDB, config.DBUSER, config.DBPASS);
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}
	 
	 public PreparedStatement createStatement(String query) {
		if (Config.instance.DEV) {
			return new EmptyStatement();
		}
		 try {
			 var connection = this.createConnection();
			 return connection.prepareStatement(query);
		 } catch (SQLException e) {
			 // Should never happen
			 throw new RuntimeException(e);
		 }
	 }
	 
	 public ResultSet query(PreparedStatement statement) {
		if (Config.instance.DEV) {
			return new EmptyResultSet();
		}
		try {
			return statement.executeQuery();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	 }
	 
	 public void execute(PreparedStatement statement) {
		 if (Config.instance.DEV) {
			 return;
		 }
		 try {
			 statement.execute();
			 clean(statement);
		 } catch (SQLException e) {
			 throw new RuntimeException(e);
		 }
	 }
	 
	 public ResultSet query(String query) {
		 if (Config.instance.DEV) {
			 return new EmptyResultSet();
		 }
		return query(createStatement(query));
	 }
	 
	 public void execute(String query) {
		 if (Config.instance.DEV) {
			 return;
		 }
		execute(createStatement(query));
	 }
	 
	 public void clean(ResultSet set) {
		 if (Config.instance.DEV) {
			 return;
		 }
		 try {
			 set.close();
		 } catch (SQLException ignored) {}
		 
		 try {
			 clean(set.getStatement());
		 } catch (SQLException ignored) {}
	 }
	 
	 public void clean(Statement statement) {
		 if (Config.instance.DEV) {
			 return;
		 }
		 try {
			 statement.close();
		 } catch (SQLException ignored) {}
		
		 try {
			 statement.getConnection().close();
		 } catch (SQLException ignored) {}
	 }
}
