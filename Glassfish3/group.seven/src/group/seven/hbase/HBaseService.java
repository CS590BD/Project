package group.seven.hbase;

import java.io.FileNotFoundException;
import java.io.IOException;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.MasterNotRunningException;
import org.apache.hadoop.hbase.ZooKeeperConnectionException;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.HBaseAdmin;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.util.Bytes;

import java.util.List;

@Path("/hbase")
public class HBaseService {

	public final int MAX_VERSIONS = 10; //max versions of an hbase record to return 

	private final String HBASE_ZOOKEEPER_QUORUM_IP = "localhost.localdomain";
	private final String HBASE_ZOOKEEPER_PROPERTY_CLIENTPORT = "2181";
	private final String HBASE_MASTER = HBASE_ZOOKEEPER_QUORUM_IP + ":60010";

	/**
	 * CREATE TABLE
	 * http://localhost:8080/group.seven/rest/hbase/create-table/characters/capital:lowercase:numeric:punctuation
	 * http://localhost:8080/group.seven/rest/hbase/create-table/foodgroups/fruit:vegetable:grain:meat:dairy
	 * 
	 * @param tablename
	 * @param columnFamilies
	 * @return
	 */
	@PUT
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/create-table/{tablename:.+}/{columnFamilies:.+}")
	public String createTable(
			@PathParam("tablename") String tablename,
			@PathParam("columnFamilies") String columnFamilies) {
		String line = "{'status':'init'}";
		HBaseAdmin hba = null;
		Configuration config = getHBaseConfiguration();
		try {
			// create a table
			HTableDescriptor ht = new HTableDescriptor(tablename);
			// add columns
			for (String columnFamily : columnFamilies.split(":")) {
				HColumnDescriptor cd = new HColumnDescriptor(columnFamily);
				cd.setMaxVersions(MAX_VERSIONS);
				ht.addFamily(cd);
			}
			try { // save the table
				hba = new HBaseAdmin(config);
				hba.createTable(ht);
				line = "{'status':'ok'}";
			} catch (Exception ex) {
				line = exceptionToJson(ex);
			}
		} finally { // close the connection
			try {
				hba.close();
			} catch (IOException e) {
				// do nothing
			}
		}
		return line;
	}
	
	/**
	 * GET SINGLE ROW
	 * http://localhost:8080/group.seven/rest/hbase/get/characters/james
	 * 
	 * @param table
	 * @param row
	 * @return
	 */
	@GET
	@Produces(MediaType.TEXT_PLAIN)
	@Path("/get/{tablename:.+}/{row:.+}")
	public String getRow(
			@PathParam("tablename") String table,
			@PathParam("row") String row) {
		String line = "{'status':'init'}";
		Configuration config = getHBaseConfiguration();
		try {
			HTable ht = new HTable(config, table);
			Get get = new Get(Bytes.toBytes(row));
			Result result = ht.get(get);
			line = "";
		    for(KeyValue keyValue : result.list()) {
		    	//keyValue.toString() == james/capital:M/1406091985672/Put/vlen=1836/ts=0
		    	String qualifier = keyValue.toString().split(":")[1].split("/")[0];
		    	String value = Bytes.toString(keyValue.getValue());
		        line += (qualifier + ":" + value + "===");
		    }
		} catch (IOException ex) {
			line = exceptionToJson(ex);
		}
		return line;
	}
	
	/**
	 * GET CELL LATEST VERSION ONLY
	 * http://localhost:8080/group.seven/rest/hbase/get/characters/james/capital/A
	 * 
	 * @param table
	 * @param row
	 * @param family
	 * @param qualifier
	 * @return
	 */
	@GET
	@Produces(MediaType.TEXT_PLAIN)
	@Path("/get/{tablename:.+}/{row:.+}/{family:.+}/{qualifier:.+}")
	public String getCell(
			@PathParam("tablename") String table,
			@PathParam("row") String row,
			@PathParam("family") String family,
			@PathParam("qualifier") String qualifier) {
		String line = "{'status':'init'}";
		Configuration config = getHBaseConfiguration();
		try {
			HTable ht = new HTable(config, table);
			Get get = new Get(Bytes.toBytes(row));
			Result result = ht.get(get);
			byte[] value = result.getValue(Bytes.toBytes(family), Bytes.toBytes(qualifier));
			line = Bytes.toString(value);
		} catch (IOException ex) {
			line = exceptionToJson(ex);
		}
		return line;
	}
	
	/**
	 * GET CELL AND ALL ITS VERSIONS
	 * http://localhost:8080/group.seven/rest/hbase/get-versions/characters/james/capital/A
	 * 
	 * @param table
	 * @param row
	 * @param family
	 * @param qualifier
	 * @return all versions of record, up to quantity MAX_VERSIONS
	 */
	@GET
	@Produces(MediaType.TEXT_PLAIN)
	@Path("/get-versions/{tablename:.+}/{row:.+}/{family:.+}/{qualifier:.+}")
	public String getCellAndVersions(
			@PathParam("tablename") String table,
			@PathParam("row") String row,
			@PathParam("family") String family,
			@PathParam("qualifier") String qualifier) {
		String line = "{'status':'init'}";
		Configuration config = getHBaseConfiguration();
		try {
			HTable ht = new HTable(config, table);
			Get get = new Get(Bytes.toBytes(row));
			get.setMaxVersions(MAX_VERSIONS);
			Result result = ht.get(get);
			List<KeyValue> kvpairs = result.getColumn(Bytes.toBytes(family), Bytes.toBytes(qualifier));
			line = "";
			for(KeyValue kv : kvpairs) {
				line += Bytes.toString(kv.getValue()) + "\n";
			}
		} catch (IOException ex) {
			line = exceptionToJson(ex);
		}
		return line;
	}

	/**
	 * READ ALL FROM TABLE
	 * http://localhost:8080/group.seven/rest/hbase/fetch/tablename
	 * 
	 * @param table
	 * @return
	 */
	@GET
	@Produces(MediaType.TEXT_PLAIN)
	@Path("/fetch/{tablename:.+}")
	public String readAll(@PathParam("tablename") String table) {
		String line = "";
		Configuration config = getHBaseConfiguration();
		try {
			HTable ht = new HTable(config, table);
			Scan s = new Scan();
			ResultScanner ss = ht.getScanner(s);
			for (Result r : ss) {
				for (KeyValue kv : r.raw()) {
					line = line + new String(kv.getRow()) + " ";
					line = line + new String(kv.getFamily()) + ":";
					line = line + new String(kv.getQualifier()) + " ";
					line = line + kv.getTimestamp() + " ";
					line = line + new String(kv.getValue());
					line = line + "\n\n";
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return line;
	}

	/**
	 * UPDATE AT QUALIFIER
	 * http://localhost:8080/group.seven/rest/hbase/post/tablename/row/family/qualifier
	 * http://localhost:8080/group.seven/rest/hbase/post/characters/james/capital/A
	 * 
	 * @param value - passed in the header message
	 * @param table
	 * @param row
	 * @param family
	 * @param qualifier
	 * @return
	 */
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/post/{table:.+}/{row:.+}/{family:.+}/{qualifier:.+}")
	public String insertSingle(String value, 
			@PathParam("table") String table,
			@PathParam("row") String row, 
			@PathParam("family") String family,
			@PathParam("qualifier") String qualifier) {

		String line = "{'status':'init'}";
		Configuration config = getHBaseConfiguration();
		HTable ht = null;
		try {
			ht = new HTable(config, table);
			Put put = new Put(Bytes.toBytes(row));
			put.add(Bytes.toBytes(family), Bytes.toBytes(qualifier), System.currentTimeMillis(), Bytes.toBytes(value));
			ht.put(put);
			line = "{'status':'ok'}";
		} catch (Exception ex) {
			line = exceptionToJson(ex);
		}
		return line;
	}

	/**
	 * DELETE TABLE
	 * http://localhost:8080/group.seven/rest/hbase/delete-table/tablename
	 * 
	 * @param table
	 * @return
	 */
	@DELETE
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/delete-table/{table:.+}")
	public String deleteTable(@PathParam("table") String table) {
		String line = "{'status':'init}";
		try {
			Configuration config = getHBaseConfiguration();
			HBaseAdmin admin = new HBaseAdmin(config);
			admin.disableTable(table);
			admin.deleteTable(table);
			line = "{'status':'ok'}";
		} catch (Exception ex) {
			line = exceptionToJson(ex);
		}
		return line;
	}

	/**
	 * INSERT FILE
	 * http://localhost:8080/group.seven/rest/hbase/insert-sensor-txt/ttjj_sensor_txt//path
	 * 
	 * @param tablename
	 * @param filepath
	 * @param columnFamilies
	 * @return
	 */
	@GET
	@Produces("application/json")
	@Path("/insert-sensor-txt/{tablename:.+}/{filepath:.+}")
	public String insertFile(@PathParam("tablename") String table,
			@PathParam("filepath") String filePath) {

		String line = "{'status':'init'}";
		try {
			HBase.insertSensorsTxt(table, filePath);
			line = "{'status':'ok'}";
		} catch (Exception ex) {
			line = exceptionToJson(ex);
		}
		return line;
	}

	/**
	 * RETURN THE HBASE CONFIGURATION
	 * 
	 * @return
	 */
	private Configuration getHBaseConfiguration() {
		Configuration config = HBaseConfiguration.create();
		config.clear();
		config.set("hbase.zookeeper.quorum", HBASE_ZOOKEEPER_QUORUM_IP);
		config.set("hbase.zookeeper.property.clientPort",
				HBASE_ZOOKEEPER_PROPERTY_CLIENTPORT);
		config.set("hbase.master", HBASE_MASTER);
		return config;
	}

	/**
	 * HANDLE ALL EXCEPTIONS
	 * 
	 * @param ex
	 * @return
	 */
	private String exceptionToJson(Exception ex) {
		String json = "{'status':'fail','exception':'";
		String exception = "";
		String message = "";
		if (ex instanceof MasterNotRunningException) {
			exception = MasterNotRunningException.class.toString();
			message = ex.getMessage();
		} else if (ex instanceof ZooKeeperConnectionException) {
			exception = ZooKeeperConnectionException.class.toString();
			message = ex.getMessage();
		} else if (ex instanceof FileNotFoundException) {
			exception = FileNotFoundException.class.toString();
			message = ex.getMessage();
		} else if (ex instanceof IOException) {
			exception = IOException.class.toString();
			message = ex.getMessage();
		} else if (ex instanceof NullPointerException) {
			exception = NullPointerException.class.toString();
			message = ex.getMessage();
		} else {
			exception = Exception.class.toString();
			message = ex.getMessage();
		}
		json += exception + "','msg':'" + message + "'}";
		return json;
	}
}
