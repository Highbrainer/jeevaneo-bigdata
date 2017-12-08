# Jeevaneo-bigdata

This project gathers different demos about Big Data, especially Hadoop and Spark.

## jdbc-to-orc
The project in subfolder spark/misc/jdbc-to-orc is a simple demonstration of how to leverage spark in order to export an RDBMS (such as postgresql, mysql etc.) tables to ORC files on the local file system.

Exemple use : 

Downloading a single table from netezza, to local folder `/tmp/your/directory/here`:

 ```
 java -jar jdbc-to-orc-1.0.0-SNAPSHOT.jar \
 --jdbc-url=jdbc:netezza://HOST:PORT/SCHEMA \
 --jdbc-login=YOUR_DB_ACCOUNT \
 --jdbc-password=YOUR_PASSWORD_HERE \
 --jdbc-driver=org.netezza.Driver \
 --output-dir=/tmp/your/directory/here \
 --table=YOUR_TABLE_HERE
 ```
 
 Downloading all tables for a given schema from Oracle:

 ```
 java -jar jdbc-to-orc-1.0.0-SNAPSHOT.jar \
 --jdbc-url=jdbc:oracle:thin:@HOST:1521/SERVICENAME \
 --jdbc-login=YOUR_DB_ACCOUNT \
 --jdbc-password=YOUR_PASSWORD_HERE \
 --jdbc-driver=oracle.jdbc.OracleDriver \
 --output-dir=/tmp/your/directory/here \
 --schema=YOUR_SCHEMA_HERE
 ```