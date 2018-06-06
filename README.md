# Jeevaneo-bigdata

This project gathers different demos about Big Data, especially Hadoop and Spark.

## jdbc-to-orc
The project in subfolder spark/misc/jdbc-to-orc is a simple demonstration of how to leverage spark in order to export an RDBMS (such as postgresql, mysql etc.) tables to ORC files on the local file system.

Exemple use :

### Downloading a single table from netezza, to local folder `/tmp/your/directory/here`:

 ```
 java -jar jdbc-to-orc-1.0.0-alpha.jar \
 --jdbc-url=jdbc:netezza://HOST:PORT/SCHEMA \
 --jdbc-login=YOUR_DB_ACCOUNT \
 --jdbc-password=YOUR_PASSWORD_HERE \
 --jdbc-driver=org.netezza.Driver \
 --output-dir=/tmp/your/directory/here \
 --table=YOUR_TABLE_HERE
 ```
 
### Downloading all tables for a given schema from MS Sql Server:

 ```
 java -jar jdbc-to-orc-1.0.0-alpha.jar \
 --jdbc-url=jdbc:microsoft:sqlserver://HOST:1433;DatabaseName=DATABASE \
 --jdbc-login=YOUR_DB_ACCOUNT \
 --jdbc-password=YOUR_PASSWORD_HERE \
 --jdbc-driver=com.microsoft.sqlserver.jdbc.SQLServerDriver \
 --output-dir=/tmp/your/directory/here \
 --schema=YOUR_SCHEMA_HERE
 ``` 
 
### Downloading all tables for a given schema from Oracle:
 ```
 java -jar jdbc-to-orc-1.0.0-alpha.jar \
 --jdbc-url=jdbc:oracle:thin:@HOST:1521/SERVICENAME \
 --jdbc-login=YOUR_DB_ACCOUNT \
 --jdbc-password=YOUR_PASSWORD_HERE \
 --jdbc-driver=oracle.jdbc.OracleDriver \
 --output-dir=/tmp/your/directory/here \
 --schema=YOUR_SCHEMA_HERE
 ```
 
### Downloading all tables from a given database / JDBC URL (no matter the schema)
 
 This is done by *not* specifying `--schema` nor `--table`.
 
 ```
 java -jar jdbc-to-orc-1.0.0.jar \
 --jdbc-url=jdbc:oracle:thin:@HOST:1521/SERVICENAME \
 --jdbc-login=YOUR_DB_ACCOUNT \
 --jdbc-password=YOUR_PASSWORD_HERE \
 --jdbc-driver=oracle.jdbc.OracleDriver \
 --output-dir=/tmp/your/directory/here
 ```
 
 ### Black listing tables or schemas
 
 Schemas or tables can be black listed through the use of `--table-black-list=table1,table2` or `--schema-black-list=schema1,schema2,...`.

 The following command line will export every table from the schema named `YOUR_SCHEMA_HERE` except for tables `TABLE_I_DONT_WANT` and `OTHER_UNWANTED_TABLE` that will be ignored.
 
 ```
 java -jar jdbc-to-orc-1.0.0-alpha.jar \
 --jdbc-url=jdbc:oracle:thin:@HOST:1521/SERVICENAME \
 --jdbc-login=YOUR_DB_ACCOUNT \
 --jdbc-password=YOUR_PASSWORD_HERE \
 --jdbc-driver=oracle.jdbc.OracleDriver \
 --output-dir=/tmp/your/directory/here \
 --schema=YOUR_SCHEMA_HERE \
 --table-black-list=TABLE_I_DONT_WANT,OTHER_UNWANTED_TABLE
 ```
 
 
 The following command line will export every table from the database (wether it is in a schema or not) except for tables in schema `SCHEMA_I_DONT_WANT` that will be ignored. 
 
 ```
 java -jar jdbc-to-orc-1.0.0-alpha.jar \
 --jdbc-url=jdbc:oracle:thin:@HOST:1521/SERVICENAME \
 --jdbc-login=YOUR_DB_ACCOUNT \
 --jdbc-password=YOUR_PASSWORD_HERE \
 --jdbc-driver=oracle.jdbc.OracleDriver \
 --output-dir=/tmp/your/directory/here \
 --schema-black-list=SCHEMA_I_DONT_WANT
 ```
 
 ### Compression
 
 You can control the compression of the output ORC files with `--compresion=...`.
 Possible values are
 * `none` : data files will not be compressed
 * `snappy` (default) : data files will be snappy compressed
 * `zlib` : orc files will be compressed using the famous zlib algorythm 
 
 Exemple:
  ```
 java -jar jdbc-to-orc-1.0.0-alpha.jar \
 --jdbc-url=jdbc:microsoft:sqlserver://HOST:1433;DatabaseName=DATABASE \
 --jdbc-login=YOUR_DB_ACCOUNT \
 --jdbc-password=YOUR_PASSWORD_HERE \
 --jdbc-driver=com.microsoft.sqlserver.jdbc.SQLServerDriver \
 --output-dir=/tmp/your/directory/here \
 --schema=YOUR_SCHEMA_HERE \
 --compression=zlib
 ``` 
 
 ### Using regular expressions
 
 `--table-black-list`and `--schema-black-list` accept simple patterns.
 
 Example : Export all tables from database except those with a tmp_ prefix :
   ```
 java -jar jdbc-to-orc-1.0.0-alpha.jar \
 --jdbc-url=jdbc:microsoft:sqlserver://HOST:1433;DatabaseName=DATABASE \
 --jdbc-login=YOUR_DB_ACCOUNT \
 --jdbc-password=YOUR_PASSWORD_HERE \
 --jdbc-driver=com.microsoft.sqlserver.jdbc.SQLServerDriver \
 --output-dir=/tmp/your/directory/here \
 '--table-black-list=tmp_*'
 ```
 
 ### Exporting only tables or views or synonyms
 
 While exporting all entities from a schema or a database, you can control 
 * wether you want to export tables using `--exporting-tables=true|false` 
 * wether you want to export views using `--exporting-views=true|false` 
 * wether you want to export synonyms using `--exporting-synonyms=true|false`
 
 By default
 * tables are included in the export
 * views are ignored
 * synonyms are ignored
 
 Note that "=true" is optional. For example `--exporting-views=true` is equivalent to `--exporting-views`.
 
 Example : Export all tables and synonyms - but not views :
   ```
 java -jar jdbc-to-orc-1.0.0-alpha.jar \
 --jdbc-url=jdbc:microsoft:sqlserver://HOST:1433;DatabaseName=DATABASE \
 --jdbc-login=YOUR_DB_ACCOUNT \
 --jdbc-password=YOUR_PASSWORD_HERE \
 --jdbc-driver=com.microsoft.sqlserver.jdbc.SQLServerDriver \
 --output-dir=/tmp/your/directory/here \
 --exporting-synonyms
 ```
 