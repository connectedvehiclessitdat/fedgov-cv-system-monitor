package gov.usdot.cv.system.monitor.uptime.database;

import gov.usdot.cv.system.monitor.constants.IpType;
import gov.usdot.cv.system.monitor.constants.MonitorDialogId;
import gov.usdot.cv.system.monitor.constants.Warehouse;
import gov.usdot.cv.system.monitor.uptime.Asset;
import gov.usdot.cv.system.monitor.uptime.Asset.AssetState;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

/**
 * The DAO for manipulating Assets
 */
public class AssetDAO {

	private static Logger logger = Logger.getLogger(AssetDAO.class);
	
	private static final String CREATE_TABLE_STMT = 
			"CREATE TABLE IF NOT EXISTS ASSET(" +
					"IP_TYPE VARCHAR(7), " +
					"SECURE BOOLEAN, " +
					"DIALOG_ID VARCHAR(12), " +
					"TARGET VARCHAR(12), " +
					"STATE_TIME TIMESTAMP, " +
					"STATE VARCHAR(7), " +
				"PRIMARY KEY (IP_TYPE, SECURE, DIALOG_ID, TARGET, STATE_TIME))";

	private static final String INSERT = 
			"INSERT INTO ASSET (IP_TYPE, SECURE, DIALOG_ID, TARGET, STATE_TIME, STATE) VALUES (?, ?, ?, ?, ?, ?)";
	
	private static final String SELECT_ALL_UNIQUE_ASSETS = 
			"SELECT DISTINCT " +
						"IP_TYPE, " +
						"SECURE, " +
						"DIALOG_ID, " +
						"TARGET " +
			"FROM ASSET "+
					"ORDER BY " +
						"IP_TYPE ASC, " +
						"SECURE ASC, " +
						"DIALOG_ID ASC, " +
						"TARGET ASC";
	
	private static final String SELECT_ALL_FOR_ASSET_IN_TIME_RANGE_ORDER_BY_TIME = 
			"SELECT * FROM ASSET " +
					"WHERE " +
						"IP_TYPE = ? AND " + 
						"SECURE = ? AND " + 
						"DIALOG_ID = ? AND " + 
						"TARGET = ? AND " +
						"STATE_TIME >= ? AND " +
						"STATE_TIME <= ? " +
					"ORDER BY " + 
						"STATE_TIME ASC";
	
	private Connection connection;
	// H2 driver doesn't appear to support statement caching, so doing it here
	private Map<String, PreparedStatement> cachedStatements;
	
	public AssetDAO(Connection connection) throws SQLException {
		this.connection = connection;
		createTable();
		cachedStatements = new HashMap<String, PreparedStatement>();
		cachedStatements.put(INSERT, this.connection.prepareStatement(INSERT));
		cachedStatements.put(SELECT_ALL_UNIQUE_ASSETS, this.connection.prepareStatement(SELECT_ALL_UNIQUE_ASSETS));
		cachedStatements.put(SELECT_ALL_FOR_ASSET_IN_TIME_RANGE_ORDER_BY_TIME,
								this.connection.prepareStatement(SELECT_ALL_FOR_ASSET_IN_TIME_RANGE_ORDER_BY_TIME));
		
	}
	
	public void insertAsset(Asset asset) throws SQLException {
		PreparedStatement stmt = cachedStatements.get(INSERT);
		if (stmt != null) {
			stmt.setString(1, asset.getIpType().toString());
			stmt.setBoolean(2, asset.isSecure());
			stmt.setString(3, asset.getDialogId().getType());
			stmt.setString(4, asset.getTarget().toString());
			stmt.setTimestamp(5, new Timestamp(asset.getTimestamp()));
			stmt.setString(6, asset.getState().toString());
			
			stmt.execute();
		} else {
			logger.error("No cached statement for insert: " + INSERT);
		}
	}
	
	public List<Asset> getAllUniqueAssets() throws SQLException {
		List<Asset> assets = new ArrayList<Asset>();
		
		PreparedStatement stmt = cachedStatements.get(SELECT_ALL_UNIQUE_ASSETS);
		if (stmt != null) {
			ResultSet rs = null;
			try {				
	   			rs = stmt.executeQuery();
	   			if (rs != null) {
	   				while (rs.next()) {
	   					Asset asset = new Asset();
	   					
	   					asset.setIpType(IpType.getByValue(rs.getString(1)));
	   					asset.setSecure(rs.getBoolean(2));
	   					asset.setDialogId(MonitorDialogId.getByType(rs.getString(3)));
	   					asset.setTarget(Warehouse.getByValue(rs.getString(4)));
	   					
	   					// Timestamp and state do not define a unique asset, set to invalid values
	   					asset.setTimestamp(-1);
	   					asset.setState(null);

	   					assets.add(asset);
	   				}
	   			}
			} finally {
				if (rs != null) rs.close();
			}
		} else {
			logger.error("No cached statement for query: " + SELECT_ALL_UNIQUE_ASSETS);
		}
		
		return assets;
	}
	
	public List<Asset> getAllForAssetInTimeRange(Asset keyAsset, Date startDate, Date endDate) throws SQLException {
		List<Asset> assets = new ArrayList<Asset>();
		
		PreparedStatement stmt = cachedStatements.get(SELECT_ALL_FOR_ASSET_IN_TIME_RANGE_ORDER_BY_TIME);
		if (stmt != null) {
			ResultSet rs = null;
			try {
				stmt.setString(1, keyAsset.getIpType().toString());
				stmt.setBoolean(2, keyAsset.isSecure());
				stmt.setString(3, keyAsset.getDialogId().getType());
				stmt.setString(4, keyAsset.getTarget().toString());
				stmt.setTimestamp(5, new Timestamp(startDate.getTime()));
				stmt.setTimestamp(6, new Timestamp(endDate.getTime()));
				
	   			rs = stmt.executeQuery();
	   			if (rs != null) {
	   				while (rs.next()) {
	   					Asset asset = new Asset();
	   					
	   					asset.setIpType(IpType.getByValue(rs.getString(1)));
	   					asset.setSecure(rs.getBoolean(2));
	   					asset.setDialogId(MonitorDialogId.getByType(rs.getString(3)));
	   					asset.setTarget(Warehouse.getByValue(rs.getString(4)));
	   					asset.setTimestamp(rs.getTimestamp(5).getTime());
	   					asset.setState(AssetState.valueOf(rs.getString(6)));

	   					assets.add(asset);
	   				}
	   			}
			} finally {
				if (rs != null) rs.close();
			}
		} else {
			logger.error("No cached statement for query: " + SELECT_ALL_FOR_ASSET_IN_TIME_RANGE_ORDER_BY_TIME);
		}
		
		return assets;
	}
	
	private void createTable() throws SQLException {
		Statement stmt = null;
		try {
			stmt = connection.createStatement();
			stmt.execute(CREATE_TABLE_STMT);
		} finally {
			if (stmt != null) { stmt.close(); }
		}
	}

}
