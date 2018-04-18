package com.jeevaneo.spark.jdbctoorc;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

import com.jeevaneo.util.Args;

public class Test {

	public Test() {
	}

	public static void main2(String[] args) throws Exception {

		Args params = new Args(args);

		String jdbcLogin = "altimaconsult";
		String jdbcPassword = "@Lt!m@c0nsult";
		String driver = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
		String table = params.os("table", "ADRESSE");
		String schema = params.os("schema", "dbo");
		String database = params.os("database", schema);
		String jdbcUrl = params.os("jdbc-url", "jdbc:sqlserver://BDD-BI:1433;databaseName=" + database);

		try {
			Class<?> clazz = Class.forName(driver);
			DriverManager.registerDriver((Driver) clazz.newInstance());
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException | SQLException e) {
			throw new IllegalArgumentException("Driver JDBC introuvable : '" + driver + "'", e);
		}
		try (Connection con = DriverManager.getConnection(jdbcUrl, jdbcLogin, jdbcPassword);

				PreparedStatement ps = con.prepareStatement("select * from " + schema + "." + table);
				ResultSet rs = ps.executeQuery();) {
			ResultSetMetaData metadata = rs.getMetaData();
			int nb = metadata.getColumnCount();
			while (rs.next()) {
				for (int i = 0; i < nb; ++i) {
					System.out.print(rs.getObject(i + 1) + "\t");
				}
				System.out.println();
			}
		}
	}
}
