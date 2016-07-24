/*
 * ao-dbc - Simplified JDBC access for simplified code.
 * Copyright (C) 2001, 2002, 2003, 2004, 2005, 2006, 2007, 2008, 2009, 2010, 2011, 2013, 2014, 2015, 2016  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of ao-dbc.
 *
 * ao-dbc is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ao-dbc is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with ao-dbc.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.aoindustries.dbc;

import com.aoindustries.sql.SQLUtility;
import com.aoindustries.sql.WrappedSQLException;
import com.aoindustries.util.IntArrayList;
import com.aoindustries.util.IntList;
import com.aoindustries.util.LongArrayList;
import com.aoindustries.util.LongList;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.Date;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLData;
import java.sql.SQLException;
import java.sql.SQLXML;
import java.sql.Struct;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Collection;
import java.util.logging.Level;

/**
 * A <code>DatabaseConnection</code> is used to only get actual database connections when needed.
 *
 * @see  Database
 *
 * @author  AO Industries, Inc.
 */
public class DatabaseConnection extends AbstractDatabaseAccess {

	private static final int FETCH_SIZE = 1000;

	/**
	 * Gets a user-friendly description of the provided result in a string formatted like
	 * <code>('value', 'value', int_value, ...)</code>.  This must not be used generate
	 * SQL statements - it is just to provider user display.
	 */
	@SuppressWarnings("deprecation")
	private static String getRow(ResultSet result) throws SQLException {
		StringBuilder sb = new StringBuilder();
		sb.append('(');
		ResultSetMetaData metaData = result.getMetaData();
		int colCount = metaData.getColumnCount();
		for(int c=1; c<=colCount; c++) {
			if(c>1) sb.append(", ");
			int colType = metaData.getColumnType(c);
			switch(colType) {
				case Types.BIGINT :
				case Types.BIT :
				case Types.BOOLEAN :
				case Types.DECIMAL :
				case Types.DOUBLE :
				case Types.FLOAT :
				case Types.INTEGER :
				case Types.NUMERIC :
				case Types.REAL :
				case Types.SMALLINT :
				case Types.TINYINT :
					sb.append(result.getObject(c));
					break;
				case Types.CHAR :
				case Types.DATE :
				case Types.LONGNVARCHAR :
				case Types.LONGVARCHAR :
				case Types.NCHAR :
				case Types.NVARCHAR :
				case Types.TIME :
				case Types.TIMESTAMP :
				case Types.VARCHAR :
				default :
					sb.append('\'');
					SQLUtility.escapeSQL(result.getString(c), sb);
					sb.append('\'');
					break;
				//default :
				//    throw new SQLException("Unexpected column type: "+colType);
			}
		}
		sb.append(')');
		return sb.toString();
	}

	private final Database database;

	Connection _conn;

	protected DatabaseConnection(Database database) {
	   this.database=database;
	}

	public Database getDatabase() {
		return database;
	}

	public Connection getConnection(int isolationLevel, boolean readOnly) throws SQLException {
		return getConnection(isolationLevel, readOnly, 1);
	}

	public Connection getConnection(int isolationLevel, boolean readOnly, int maxConnections) throws SQLException {
		Connection c=_conn;
		if(c==null) {
			c=database.getConnection(isolationLevel, readOnly, maxConnections);
			if(!readOnly || isolationLevel>=Connection.TRANSACTION_REPEATABLE_READ) c.setAutoCommit(false);
			_conn=c;
		} else if(c.getTransactionIsolation()<isolationLevel) {
			if(!c.getAutoCommit()) {
				// c.commit();
				c.setAutoCommit(true);
			}
			c.setTransactionIsolation(isolationLevel);
			if(!readOnly && c.isReadOnly()) c.setReadOnly(false);
			if(!readOnly || isolationLevel>=Connection.TRANSACTION_REPEATABLE_READ) c.setAutoCommit(false);
		} else if(!readOnly && c.isReadOnly()) {
			if(!c.getAutoCommit()) {
				// May be able to get rid of the commit - setAutoCommit should commit according to the documentation
				// c.commit();
				c.setAutoCommit(true);
			}
			c.setReadOnly(false);
			c.setAutoCommit(false);
		}
		return c;
	}

	protected static void setParam(Connection conn, PreparedStatement pstmt, int pos, Object param) throws SQLException {
		if(param==null) pstmt.setNull(pos, Types.VARCHAR);
		else if(param instanceof Null) pstmt.setNull(pos, ((Null)param).getType());
		else if(param instanceof Array) pstmt.setArray(pos, (Array)param);
		else if(param instanceof BigDecimal) pstmt.setBigDecimal(pos, (BigDecimal)param);
		else if(param instanceof BigInteger) pstmt.setBigDecimal(pos, new BigDecimal((BigInteger)param));
		else if(param instanceof Blob) pstmt.setBlob(pos, (Blob)param);
		else if(param instanceof Boolean) pstmt.setBoolean(pos, (Boolean)param);
		else if(param instanceof Byte) pstmt.setByte(pos, (Byte)param);
		else if(param instanceof byte[]) pstmt.setBytes(pos, (byte[])param);
		else if(param instanceof Clob) pstmt.setClob(pos, (Clob)param);
		else if(param instanceof Date) pstmt.setDate(pos, (Date)param);
		else if(param instanceof Double) pstmt.setDouble(pos, (Double)param);
		else if(param instanceof Float) pstmt.setFloat(pos, (Float)param);
		else if(param instanceof Integer) pstmt.setInt(pos, (Integer)param);
		else if(param instanceof InputStream) pstmt.setBinaryStream(pos, (InputStream)param);
		else if(param instanceof Long) pstmt.setLong(pos, (Long)param);
		else if(param instanceof NClob) pstmt.setNClob(pos, (NClob)param);
		else if(param instanceof Reader) pstmt.setCharacterStream(pos, (Reader)param);
		else if(param instanceof Ref) pstmt.setRef(pos, (Ref)param);
		else if(param instanceof RowId) pstmt.setRowId(pos, (RowId)param);
		else if(param instanceof Short) pstmt.setShort(pos, (Short)param);
		else if(param instanceof SQLXML) pstmt.setSQLXML(pos, (SQLXML)param);
		else if(param instanceof String) pstmt.setString(pos, (String)param);
		else if(param instanceof Time) pstmt.setTime(pos, (Time)param);
		else if(param instanceof Timestamp) pstmt.setTimestamp(pos, (Timestamp)param);
		else if(param instanceof URL) pstmt.setURL(pos, (URL)param);
		else if(param instanceof Enum) pstmt.setString(pos, ((Enum)param).name());
		else if(
			(param instanceof SQLData)
			|| (param instanceof Struct)
		) pstmt.setObject(pos, param);
		else if(param instanceof String[]) {
			pstmt.setArray(pos, conn.createArrayOf("text", (Object[])param));
		} else {
			// Defaults to string with object.toString only when the class has a valueOf(String) method that will reconstitute it in AutoObjectFactory
			Class<?> clazz = param.getClass();
			if(AutoObjectFactory.getValueOfStringMethod(clazz)!=null) pstmt.setString(pos, param.toString());
			else throw new SQLException("Unexpected parameter class: "+clazz.getName());
		}
	}

	public static void setParams(Connection conn, PreparedStatement pstmt, Object ... params) throws SQLException {
		int pos=1;
		for(Object param : params) setParam(conn, pstmt, pos++, param);
	}

	@Override
	public boolean executeBooleanQuery(int isolationLevel, boolean readOnly, boolean rowRequired, String sql, Object ... params) throws NoRowException, SQLException {
		Connection conn = getConnection(isolationLevel, readOnly);
		try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
			try {
				setParams(conn, pstmt, params);
				try (ResultSet results = pstmt.executeQuery()) {
					if(results.next()) {
						boolean b=results.getBoolean(1);
						if(results.next()) throw new ExtraRowException();
						return b;
					}
					if(rowRequired) throw new NoRowException();
					return false;
				}
			} catch(NoRowException err) {
				throw err;
			} catch(SQLException err) {
				throw new WrappedSQLException(err, pstmt);
			}
		}
	}

	@Override
	public IntList executeIntListQuery(int isolationLevel, boolean readOnly, String sql, Object ... params) throws SQLException {
		Connection conn = getConnection(isolationLevel, readOnly);
		conn.setAutoCommit(false);
		try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
			try {
				pstmt.setFetchSize(FETCH_SIZE);
				setParams(conn, pstmt, params);
				try (ResultSet results = pstmt.executeQuery()) {
					IntList V=new IntArrayList();
					while(results.next()) V.add(results.getInt(1));
					return V;
				}
			} catch(SQLException err) {
				throw new WrappedSQLException(err, pstmt);
			}
		}
	}

	@Override
	public int executeIntQuery(int isolationLevel, boolean readOnly, boolean rowRequired, String sql, Object ... params) throws NoRowException, SQLException {
		Connection conn = getConnection(isolationLevel, readOnly);
		try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
			try {
				setParams(conn, pstmt, params);
				try (ResultSet results = pstmt.executeQuery()) {
					if(results.next()) {
						int i=results.getInt(1);
						if(results.next()) throw new ExtraRowException();
						return i;
					}
					if(rowRequired) throw new NoRowException();
					return 0;
				}
			} catch(NoRowException err) {
				throw err;
			} catch(SQLException err) {
				throw new WrappedSQLException(err, pstmt);
			}
		}
	}

	@Override
	public LongList executeLongListQuery(int isolationLevel, boolean readOnly, String sql, Object ... params) throws SQLException {
		Connection conn = getConnection(isolationLevel, readOnly);
		conn.setAutoCommit(false);
		try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
			try {
				pstmt.setFetchSize(FETCH_SIZE);
				setParams(conn, pstmt, params);
				try (ResultSet results = pstmt.executeQuery()) {
					LongList V=new LongArrayList();
					while(results.next()) V.add(results.getLong(1));
					return V;
				}
			} catch(SQLException err) {
				throw new WrappedSQLException(err, pstmt);
			}
		}
	}

	@Override
	public long executeLongQuery(int isolationLevel, boolean readOnly, boolean rowRequired, String sql, Object ... params) throws NoRowException, SQLException {
		Connection conn = getConnection(isolationLevel, readOnly);
		try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
			try {
				setParams(conn, pstmt, params);
				try (ResultSet results = pstmt.executeQuery()) {
					if(results.next()) {
						long l=results.getLong(1);
						if(results.next()) throw new ExtraRowException();
						return l;
					}
					if(rowRequired) throw new NoRowException();
					return 0;
				}
			} catch(NoRowException err) {
				throw err;
			} catch(SQLException err) {
				throw new WrappedSQLException(err, pstmt);
			}
		}
	}

	@Override
	public <T,E extends Exception> T executeObjectQuery(int isolationLevel, boolean readOnly, boolean rowRequired, Class<E> eClass, ObjectFactoryE<T,E> objectFactory, String sql, Object ... params) throws NoRowException, SQLException, E {
		Connection conn = getConnection(isolationLevel, readOnly);
		try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
			try {
				setParams(conn, pstmt, params);
				try (ResultSet results = pstmt.executeQuery()) {
					if(results.next()) {
						T object = objectFactory.createObject(results);
						if(results.next()) throw new ExtraRowException();
						return object;
					}
					if(rowRequired) throw new NoRowException();
					return null;
				}
			} catch(NoRowException err) {
				throw err;
			} catch(SQLException err) {
				throw new WrappedSQLException(err, pstmt);
			}
		}
	}

	@Override
	public <T,C extends Collection<? super T>,E extends Exception> C executeObjectCollectionQuery(int isolationLevel, boolean readOnly, C collection, Class<E> eClass, ObjectFactoryE<T,E> objectFactory, String sql, Object ... params) throws SQLException, E {
		Connection conn = getConnection(isolationLevel, readOnly);
		conn.setAutoCommit(false);
		try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
			try {
				pstmt.setFetchSize(FETCH_SIZE);
				setParams(conn, pstmt, params);
				try (ResultSet results = pstmt.executeQuery()) {
					while(results.next()) {
						T newObj = objectFactory.createObject(results);
						if(!collection.add(newObj)) throw new SQLException("Duplicate row in results: "+getRow(results));
					}
					return collection;
				}
			} catch(SQLException err) {
				throw new WrappedSQLException(err, pstmt);
			}
		}
	}

	@Override
	public <T,E extends Exception> T executeQuery(int isolationLevel, boolean readOnly, Class<E> eClass, ResultSetHandlerE<T,E> resultSetHandler, String sql, Object ... params) throws SQLException, E {
		Connection conn = getConnection(isolationLevel, readOnly);
		conn.setAutoCommit(false);
		try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
			try {
				pstmt.setFetchSize(FETCH_SIZE);
				setParams(conn, pstmt, params);
				try (ResultSet results = pstmt.executeQuery()) {
					return resultSetHandler.handleResultSet(results);
				}
			} catch(SQLException err) {
				throw new WrappedSQLException(err, pstmt);
			}
		}
	}

	@Override
	public short executeShortQuery(int isolationLevel, boolean readOnly, boolean rowRequired, String sql, Object ... params) throws NoRowException, SQLException {
		Connection conn = getConnection(isolationLevel, readOnly);
		try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
			try {
				setParams(conn, pstmt, params);
				try (ResultSet results = pstmt.executeQuery()) {
					if(results.next()) {
						short s=results.getShort(1);
						if(results.next()) throw new ExtraRowException();
						return s;
					}
					if(rowRequired) throw new NoRowException();
					return 0;
				}
			} catch(NoRowException err) {
				throw err;
			} catch(SQLException err) {
				throw new WrappedSQLException(err, pstmt);
			}
		}
	}

	@Override
	public int executeUpdate(String sql, Object ... params) throws SQLException {
		Connection conn = getConnection(Connection.TRANSACTION_READ_COMMITTED, false);
		try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
			try {
				setParams(conn, pstmt, params);
				return pstmt.executeUpdate();
			} catch(SQLException err) {
				throw new WrappedSQLException(err, pstmt);
			}
		}
	}

	public void commit() throws SQLException {
		Connection c=_conn;
		if(c!=null && !c.getAutoCommit()) c.commit();
	}

	public boolean isClosed() throws SQLException {
		Connection c=_conn;
		return c==null || c.isClosed();
	}

	public void releaseConnection() throws SQLException {
		Connection c=_conn;
		if(c!=null) {
			_conn=null;
			database.releaseConnection(c);
		}
	}

	public boolean rollback() {
		boolean rolledBack=false;
		try {
			if(_conn!=null && !_conn.isClosed()) {
				rolledBack=true;
				if(!_conn.getAutoCommit()) _conn.rollback();
			}
		} catch(SQLException err) {
			database.getLogger().logp(Level.SEVERE, DatabaseConnection.class.getName(), "rollback", null, err);
		}
		return rolledBack;
	}

	public boolean rollbackAndClose() {
		boolean rolledBack=false;
		try {
			if(_conn!=null && !_conn.isClosed()) {
				rolledBack=true;
				if(!_conn.getAutoCommit()) _conn.rollback();
				_conn.close();
			}
		} catch(SQLException err) {
			database.getLogger().logp(Level.SEVERE, DatabaseConnection.class.getName(), "rollbackAndClose", null, err);
		}
		return rolledBack;
	}
}
