package com.jeevaneo.spark.jdbctoorc;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SaveMode;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.SparkSession.Builder;

import com.jeevaneo.util.Args;

import oracle.jdbc.OracleDriver;

public class Main {

	private static Logger log = Logger.getLogger(Main.class);

	public Main() {
	}

	private static final File HADOOP_HOME = new File(System.getProperty("java.io.tmpdir", "/tmp"), "hadoop");
	private static final File HADOOP_BIN = new File(HADOOP_HOME, "bin");
	private static final File HADOOP_CONF = new File(HADOOP_HOME, "conf");

	private String jdbcUrl;
	private String jdbcLogin;
	private String jdbcPassword;
	private String driver;

	private String dir;

	private boolean exporting = true;
	private Long limit = null;
	private int parallelism = 1; // no parallelism!

	public Main(String jdbcUrl, String jdbcLogin, String jdbcPassword, String driver, String dir) {
		super();
		this.jdbcUrl = jdbcUrl;
		this.jdbcLogin = jdbcLogin;
		this.jdbcPassword = jdbcPassword;
		this.driver = driver;
		this.dir = dir;
	}

	static {
		if (System.getProperty("hadoop.home.dir") == null) {
			try {

				HADOOP_BIN.mkdirs();
				File targetFile = new File(HADOOP_BIN, "winutils.exe");
				Files.copy(Main.class.getResourceAsStream("/winutils.exe"), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
				System.setProperty("hadoop.home.dir", HADOOP_HOME.getAbsolutePath());
				System.setProperty("hadoop.conf.dir", HADOOP_CONF.getAbsolutePath());
				System.setProperty("hadoop.bin.dir", HADOOP_BIN.getAbsolutePath());

				// File tmpHive = new File("/tmp/hive");
				// tmpHive.mkdirs();
				// OS.INSTANCE.fork(targetFile.getAbsolutePath(), "chmod", "777", tmpHive.getAbsolutePath());

			} catch (IOException e) {
				throw new RuntimeException("Impossible d'installer winutils.exe", e);
			}
		}
	}

	public static void main(String[] args) throws Exception {

		Args params = new Args(args);
		String jdbcUrl = params.ms("jdbc-url");
		String jdbcLogin = params.ms("jdbc-login");
		String jdbcPassword = params.ms("jdbc-password");
		String table = params.os("table");
		String schema = params.os("schema");
		String dir = params.ms("output-dir");
		String driver = params.os("jdbc-driver", OracleDriver.class.getName());
		boolean exporting = params.ob("export", true);
		Long limit = params.ol("limit");
		int parallelism = params.oi("parallelism", 1);

		if (null == table && null == schema) {
			throw new IllegalArgumentException("At least one of table or schema is needed.");
		}

		Main moi = new Main(jdbcUrl, jdbcLogin, jdbcPassword, driver, dir);
		moi.setExporting(exporting);
		moi.setLimit(limit);
		moi.setParallelism(parallelism);

		if (null != table) {

			String[] tables = table.split(",");
			for (String t : tables) {
				if (null != schema) {
					t = schema + "." + t;
				}
				moi.exportTable(t);
			}
		} else if (null != schema) {
			moi.exportAll(schema);
		}

	}

	private void exportTable(String table) throws SQLException {
		workInSpark(ss -> {
			exportTable(ss, table);
		});
	}

	private Set<String> listTables(String schema) throws SQLException {
		Set<String> tables = new TreeSet<>();
		try (Connection con = connect();) {
			DatabaseMetaData md = con.getMetaData();
			try (ResultSet rs = md.getTables(null, schema, null, new String[] { "TABLE", "VIEW"/* , "SYNONYM" */ });) {
				while (rs.next()) {
					String tablename = rs.getString("TABLE_NAME");
					log.debug(tablename);
					tables.add(tablename);
				}
				rs.close();
			}
		}
		return tables;
	}

	private Connection connect() throws SQLException {
		registerDriver();
		return DriverManager.getConnection(jdbcUrl, jdbcLogin, jdbcPassword);
	}

	private void registerDriver() {
		try {
			Class.forName(driver);
		} catch (ClassNotFoundException e) {
			throw new IllegalArgumentException("Driver JDBC introuvable : '" + driver + "'", e);
		}
	}

	private void exportAll(String schema) throws SQLException {
		workInSpark(ss -> {
			ForkJoinPool threadPool = new ForkJoinPool(getParallelism());
			threadPool.submit(() -> {
				try {
					listTables(schema).parallelStream().forEach(t -> exportTable(ss, t));
				} catch (SQLException e) {
					throw new RuntimeException(e);
				}
			});
			threadPool.shutdown();
			try {
				threadPool.awaitTermination(2, TimeUnit.DAYS);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		});
	}

	private void exportTable(SparkSession ss, String table) {

		log.info("Exporting " + table + "...");

		Properties props = new Properties();
		props.put("user", jdbcLogin);
		props.put("password", jdbcPassword);

		registerDriver();

		File file = new File(dir, table);
		file.getParentFile().mkdirs();
		String path = file.toURI().toString();
		if (isExporting()) {

			String sql = table;
			if (null != limit) {
				if (driver.equalsIgnoreCase(OracleDriver.class.getName())) {
					sql = "(select * from " + table + " where rownum < " + limit + ") src";
				} else {
					sql = "(select * from " + table + " limit " + limit + ") src";
				}
			}
			Dataset<Row> df = ss.read().jdbc(jdbcUrl, sql /* , new String[] { "1=2" } */, props);
			// df.printSchema();
			// log.debug("Table " + table + " : " + df.count() + " lignes.");

			df.write().mode(SaveMode.Overwrite).orc(path);
			log.info("Table " + table + " exportée vers " + file.getAbsolutePath());
		} else {
			ss.read().orc(path).printSchema();
			System.out.println(ss.read().orc(path).count());
		}

	}

	public void workInSpark(ConsumerWithSqlException<SparkSession> worker) throws SQLException {
		log.info("Initializing spark...");
		Builder builder = SparkSession.builder().config("mapreduce.app-submission.cross-platform", "true");

		builder.appName("appName");

		builder.master("local");

		// builder.enableHiveSupport();

		try (SparkSession ss = builder.getOrCreate();) {
			log.info("Starting...");
			long start = System.currentTimeMillis();
			worker.accept(ss);
			long end = System.currentTimeMillis();
			log.info("Done in " + (end - start) + " ms.");
		}
	}

	public boolean isExporting() {
		return exporting;
	}

	public void setExporting(boolean exporting) {
		this.exporting = exporting;
	}

	public Long getLimit() {
		return limit;
	}

	public void setLimit(Long limit) {
		this.limit = limit;
	}

	public int getParallelism() {
		return parallelism;
	}

	public void setParallelism(int parallelism) {
		this.parallelism = parallelism;
	}
}

@FunctionalInterface
interface ConsumerWithSqlException<T> {
	void accept(T t) throws SQLException;
}
