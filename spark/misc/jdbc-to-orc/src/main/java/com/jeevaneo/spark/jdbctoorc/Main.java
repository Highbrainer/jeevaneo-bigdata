package com.jeevaneo.spark.jdbctoorc;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SaveMode;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.SparkSession.Builder;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructField;

import com.jeevaneo.util.Args;
import com.jeevaneo.util.OS;

public class Main {

	private static Logger log = Logger.getLogger(Main.class);

	private static String SQL_SERVER_QUOTING_CARACTER = "[%s]";

	private static String ORACLE_QUOTING_CARACTER = "\"%s\"";

	private static String MYSQL_QUOTING_CARACTER = "[%s]";

	public Main() {
	}

	private static final File HADOOP_HOME = new File(System.getProperty("java.io.tmpdir", "/tmp"), "hadoop");
	private static final File HADOOP_BIN = new File(HADOOP_HOME, "bin");
	private static final File HADOOP_CONF = new File(HADOOP_HOME, "conf");

	private String jdbcUrl;
	private String jdbcLogin;
	private String jdbcPassword;
	private String driver;
	private boolean exportingTables = true;
	private boolean exportingViews = false;
	private boolean exportingSynonyms = false;
	private String compression = "snappy"; // none, snappy, zlib
	private String lineBreakReplaceString = "";

	private List<String> tableBlackList = new LinkedList<>();
	private List<String> schemaBlackList = new LinkedList<>();
	{
		schemaBlackList.add("information_schema");
	}

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

		try {
			Class.forName(driver);
		} catch (ClassNotFoundException e) {
			throw new IllegalArgumentException("Driver not found - missing jar on the classpath? " + driver, e);
		}
	}

	static {
		if (System.getProperty("hadoop.home.dir") == null) {
			System.setProperty("hadoop.home.dir", HADOOP_HOME.getAbsolutePath());
			System.setProperty("hadoop.conf.dir", HADOOP_CONF.getAbsolutePath());
			System.setProperty("hadoop.bin.dir", HADOOP_BIN.getAbsolutePath());
			try {

				if (OS.INSTANCE.isWindows()) {
					File targetFile = new File(HADOOP_BIN, "winutils.exe");
					if (!targetFile.exists()) {
						HADOOP_BIN.mkdirs();
						Files.copy(Main.class.getResourceAsStream("/winutils.exe"), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
					}
				}

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
		String driver = params.ms("jdbc-driver");

		Main moi = new Main(jdbcUrl, jdbcLogin, jdbcPassword, driver, dir);
		params.populate(moi);

		if (null != table) {

			String[] tables = table.split(",");
			log.info("Loading " + tables.length + " tables...");
			for (String t : tables) {
				if (null != schema) {
					t = schema + "." + t.trim();
				}
				moi.exportTable(t);
			}
		} else if (null != schema) {
			moi.exportAll(schema);
		} else {
			moi.exportAll();
		}

	}

	private void exportTable(String table) throws SQLException {
		workInSpark(ss -> {
			exportTable(ss, table);
		});
	}

	private Set<String> listTables() throws SQLException {
		Set<String> tables = new TreeSet<>();
		try (Connection con = connect();) {
			DatabaseMetaData md = con.getMetaData();

			List<String> types = new ArrayList<>(3);
			if (isExportingSynonyms()) {
				types.add("SYNONYM");
			}
			if (isExportingTables()) {
				types.add("TABLE");
			}
			if (isExportingViews()) {
				types.add("VIEW");
			}
			String[] aTypes = types.toArray(new String[types.size()]);

			try (ResultSet rs = md.getTables(con.getCatalog(), null, "%", aTypes);) {
				while (rs.next()) {
					String name = rs.getString("TABLE_NAME");
					String schem = rs.getString("TABLE_SCHEM");
					log.debug(name);
					if (!isSchemaBlackListed(name)) {
						if (null != schem && !schem.trim().isEmpty()) {
							name = schem + "." + name;
						}
						tables.add(name);
					}
				}
				rs.close();
			}
		}
		log.info("Exporting " + tables.size() + " tables...");
		return tables;
	}

	private Set<String> listTables(String schema) throws SQLException {
		Set<String> tables = new TreeSet<>();
		try (Connection con = connect();) {
			DatabaseMetaData md = con.getMetaData();

			List<String> types = new ArrayList<>(3);
			if (isExportingSynonyms()) {
				types.add("SYNONYM");
			}
			if (isExportingTables()) {
				types.add("TABLE");
			}
			if (isExportingViews()) {
				types.add("VIEW");
			}
			String[] aTypes = types.toArray(new String[types.size()]);

			try (ResultSet rs = md.getTables(null, schema, null, aTypes);) {
				while (rs.next()) {
					String tablename = schema + "." + rs.getString("TABLE_NAME");
					log.debug(tablename);
					if (!isTableBlackListed(tablename)) {
						tables.add(tablename);
					}
				}
				rs.close();
			}
		}
		log.info("Exporting " + tables.size() + " tables...");
		return tables;
	}

	private boolean isTableBlackListed(String tablename) {
		if (null == tableBlackList) {
			return false;
		}
		return tableBlackList.stream().map(this::regexpify).anyMatch(tablename::matches);
	}

	private boolean isSchemaBlackListed(String name) {
		if (null == schemaBlackList) {
			return false;
		}
		return schemaBlackList.stream().map(this::regexpify).anyMatch(name::matches);
	}

	private String regexpify(String in) {
		// TODO should protect litetals with Pattern.quote()...
		return in.replaceAll("([\\*\\?])", ".$1");
	}

	private Connection connect() throws SQLException {
		registerDriver();
		return DriverManager.getConnection(jdbcUrl, jdbcLogin, jdbcPassword);
	}

	private void registerDriver() {
		try {
			Class<?> clazz = Class.forName(driver);
			DriverManager.registerDriver((Driver) clazz.newInstance());
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException | SQLException e) {
			throw new IllegalArgumentException("Driver JDBC introuvable : '" + driver + "'", e);
		}
	}

	private void exportAll(String schema) throws SQLException {

		configureParallelism();

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

	private void configureParallelism() {
		String threads = System.getProperty("java.util.concurrent.ForkJoinPool.common.parallelism", "-1");
		if (Integer.parseInt(threads) < parallelism) {
			System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", "" + parallelism);
		}
	}

	private void exportAll() throws SQLException {
		configureParallelism();
		workInSpark(ss -> {
			ForkJoinPool threadPool = new ForkJoinPool(getParallelism());
			threadPool.submit(() -> {
				try {
					listTables().parallelStream().forEach(t -> exportTable(ss, t));
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

	private String selectColumnQuotingPattern() {
		if (driver.endsWith("oracle")) {
			return ORACLE_QUOTING_CARACTER;
		}
		if (jdbcUrl.contains("sqlserver")) {
			return SQL_SERVER_QUOTING_CARACTER;
		}
		if (jdbcUrl.contains("mysql")) {
			return MYSQL_QUOTING_CARACTER;
		}
		return null;
	}

	private void exportTable(SparkSession ss, String table) {

		log.info("Exporting " + table + "...");

		Properties props = new Properties();
		props.put("user", jdbcLogin);
		props.put("password", jdbcPassword);
		props.put("driver", driver);

		registerDriver();

		File file = new File(dir, table);
		file.getParentFile().mkdirs();
		String path = file.toURI().toString();
		if (isExporting()) {

			String sql = table;
			List<String> columnList = null;

			try {
				columnList = getColumnList(table);
			} catch (SQLException e1) {
				throw new RuntimeException("Error during computing column list of table " + table, e1);
			}

			String columns = columnList.stream().collect(Collectors.joining(","));

			if (null != limit) {
				if (driver.endsWith("OracleDriver")) {
					sql = "(select " + columns + " from " + table + " where rownum < " + limit + ") src";
				} else {
					sql = "(select " + columns + " from " + table + " limit " + limit + ") src";
				}
			} else {
				sql = "(select " + columns + " from " + table + ") src";
			}
			log.debug("SQL: " + sql);

			try {
				Dataset<Row> df = ss.read().jdbc(jdbcUrl, sql /* , new String[] { "1=2" } */, props);
				List<StructField> fields = Arrays.stream(df.schema().fields()).collect(Collectors.toList());
				for (StructField f : fields) {
					String colName = f.name();
					if (colName.contains(" ")) {
						df = df.withColumnRenamed(colName, colName.replace(" ", "_"));
					}
					if (getLineBreakReplaceString() != null) {
						if (f.dataType().equals(DataTypes.StringType)) {
							String auxColName = colName + "_";
							log.info("Replacing line break characters by '" + getLineBreakReplaceString() + "' into column " + colName);
							df = df.withColumn(auxColName, functions.regexp_replace(df.col(colName), "\r?\n\\s*", getLineBreakReplaceString()))
									.drop(colName).withColumnRenamed(auxColName, colName);
						}
					}
				}

				df.write().mode(SaveMode.Overwrite).option("orc.compress", getCompression()).orc(path);
				log.info("Table " + table + " exportée vers " + file.getAbsolutePath());
			} catch (Throwable e) {
				e.printStackTrace();
				throw new RuntimeException("Error when writing data of table " + table, e);
			}
			// df.printSchema();
			// log.debug("Table " + table + " : " + df.count() + " lignes.");

		} else {
			ss.read().orc(path).printSchema();
			System.out.println(ss.read().orc(path).count());
		}

	}

	private List<String> getColumnList(String schemaAndTable) throws SQLException {
		String quotingPattern = selectColumnQuotingPattern();
		String schema = schemaAndTable.split("\\.")[0];
		String table = schemaAndTable.split("\\.")[1];
		List<String> columnList = new ArrayList<>();
		try (Connection con = DriverManager.getConnection(jdbcUrl, jdbcLogin, jdbcPassword);
				PreparedStatement ps = con.prepareStatement("select * from " + schema + "." + table + " where 1=2");
				ResultSet rs = ps.executeQuery();) {
			ResultSetMetaData metadata = rs.getMetaData();
			int nb = metadata.getColumnCount();
			for (int i = 0; i < nb; ++i) {
				String c = metadata.getColumnName(i + 1);
				columnList.add(String.format(quotingPattern, c));
			}
		}
		return columnList;
	}

	public void workInSpark(ConsumerWithSqlException<SparkSession> worker) throws SQLException {
		log.info("Initializing spark...");
		Builder builder = SparkSession.builder().config("mapreduce.app-submission.cross-platform", "true");

		builder.config("hive.support.quoted.identifiers", "column");

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

	public boolean isExportingTables() {
		return exportingTables;
	}

	public void setExportingTables(boolean exportingTables) {
		this.exportingTables = exportingTables;
	}

	public boolean isExportingViews() {
		return exportingViews;
	}

	public void setExportingViews(boolean exportingViews) {
		this.exportingViews = exportingViews;
	}

	public boolean isExportingSynonyms() {
		return exportingSynonyms;
	}

	public void setExportingSynonyms(boolean exportingSynonyms) {
		this.exportingSynonyms = exportingSynonyms;
	}

	public List<String> getTableBlackList() {
		return tableBlackList;
	}

	public void setTableBlackList(String tableBlackList) {
		this.tableBlackList = Arrays.stream(tableBlackList.split(",")).collect(Collectors.toList());
	}

	public List<String> getSchemaBlackList() {
		return schemaBlackList;
	}

	public void setSchelaBlackList(String schemaBlackList) {
		this.schemaBlackList = Arrays.stream(schemaBlackList.split(",")).collect(Collectors.toList());
	}

	public String getCompression() {
		return compression;
	}

	public void setCompression(String compression) {
		this.compression = compression;
	}

	public String getLineBreakReplaceString() {
		return lineBreakReplaceString;
	}

	public void setLineBreakReplaceString(String lineBreakReplaceString) {
		this.lineBreakReplaceString = lineBreakReplaceString;
	}

}

@FunctionalInterface
interface ConsumerWithSqlException<T> {
	void accept(T t) throws SQLException;
}
